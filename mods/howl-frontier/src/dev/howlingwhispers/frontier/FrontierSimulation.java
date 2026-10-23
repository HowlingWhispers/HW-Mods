package dev.howlingwhispers.frontier;

import dev.howlingwhispers.codaloader.api.*;
import java.nio.file.Path;
import java.util.*;
import static dev.howlingwhispers.frontier.Settler.State.*;

/**
 * Once-a-second settlement decisions. Native Minecraft owns movement, health and
 * items; Frontier decides intent. See DESIGN.md for the rules this keeps.
 */
final class FrontierSimulation {
    static final Set<String> FOOD = Set.of("minecraft:bread", "minecraft:baked_potato", "minecraft:carrot",
            "minecraft:cooked_beef", "minecraft:cooked_porkchop", "minecraft:cooked_chicken", "minecraft:cooked_mutton",
            "minecraft:cooked_rabbit", "minecraft:cooked_cod", "minecraft:cooked_salmon", "minecraft:beetroot", "minecraft:apple");
    static final Set<String> CONTAINERS = Set.of("minecraft:chest", "minecraft:barrel");
    /** The pioneer arrives after 60-120 seconds of loaded camp time. */
    static final int PIONEER_MIN = 1200, PIONEER_SPREAD = 1200;
    static final int BED_SCAN_BUDGET = 512, BED_SCAN_DEPTH = 10, SPAWN_RETRY = 200;

    FrontierState state;
    private FrontierStore store;
    private Path root;
    private String session;
    /** Why this world's state could not be read. While set, nothing is simulated or saved. */
    String loadError;
    String lastError;
    long errorCount, lastErrorTick, now;
    String arrivalStatus = "Waiting for the first camp update";
    boolean foodAvailable;
    private int scanCursor;
    /** Spots where the loader refused a spawn this session; never offered again until the world reopens. */
    private final Set<CodaBlockPos> refusedArrivals = new HashSet<>();
    private final ArrayDeque<CodaBlockPlacements.Placement> placements = new ArrayDeque<>();
    private final Set<String> logged = new HashSet<>();
    final FrontierJobs jobs = new FrontierJobs(this);
    final FrontierPlanner planner = new FrontierPlanner();
    final FrontierBuilder builder = new FrontierBuilder(this, jobs);
    /** The current update's world view, for reading stockpile contents. Valid only during the update. */
    private CodaWorldView view;

    Settlement camp() { return state == null ? null : state.primary(); }

    void placed(CodaBlockPlacements.Placement p) {
        String id = p.blockId();
        if (placements.size() < 64 && (id.equals("minecraft:campfire") || id.endsWith("_bed"))) placements.add(p);
    }

    // ---- State and failure containment ------------------------------------------------

    void open(CodaWorldView view, String session) throws Exception {
        Path next = view.worldDirectory().orElseThrow(() -> new IllegalStateException("World save directory unavailable")).toAbsolutePath().normalize();
        // A failed load is retried only when the world or session changes, never every second.
        if ((state == null && loadError == null) || !next.equals(root) || (session != null && !session.equals(this.session))) {
            if (root != null) placements.clear();
            root = next;
            this.session = session;
            lastError = null;
            errorCount = 0;
            scanCursor = 0;
            refusedArrivals.clear();
            jobs.clear();
            planner.clear();
            builder.clear();
            logged.clear();
            try {
                var opened = new FrontierStore(next);
                state = opened.load();
                store = opened;
                loadError = null;
            } catch (java.io.IOException unreadable) {
                // Never replace unreadable state with a fresh settlement: that would erase the camp.
                state = null;
                store = null;
                loadError = unreadable.getMessage();
                log("load", "[Frontier] World state unreadable; camp paused and the file left unchanged: " + loadError);
            }
        }
    }

    void requireReadable() {
        if (loadError != null) throw new IllegalArgumentException("This world's Frontier file could not be read and has not been changed: "
                + loadError + ". Close the world, then fix or restore howl_frontier/" + FrontierStore.FILE
                + " (the previous version is kept as " + FrontierStore.PREVIOUS + ") and reopen it.");
    }

    void save() throws Exception {
        if (store == null) throw new IllegalStateException("Frontier storage is unavailable");
        store.save(state);
    }

    @FunctionalInterface interface Part { void run() throws Exception; }

    /** Contain one failing part of the update so the loader never disables Frontier for the session. */
    private boolean guard(String part, Part action) {
        try {
            action.run();
            return true;
        } catch (Exception | LinkageError failure) {
            errorCount++;
            lastError = part + ": " + failure;
            lastErrorTick = now;
            log(part + failure.getClass().getName() + failure.getMessage(), "[Frontier] " + part + " failed and was skipped this second: " + failure);
            return false;
        }
    }

    private void log(String key, String message) {
        if (logged.size() < 64 && logged.add(key)) System.err.println(message);
    }

    // ---- The update -------------------------------------------------------------------

    /** Never throws: three consecutive failures would make the loader disable Frontier. */
    void tick(CodaServerTickContext tick) {
        now = tick.tick();
        if (tick.tick() % 20 != 0 || tick.world().isEmpty()) return;
        CodaWorldView view = tick.world().orElseThrow();
        this.view = view;
        if (!guard("Opening the world state", () -> open(view, tick.sessionId())) || loadError != null) {
            placements.clear();
            return;
        }
        CodaLivingWorld world;
        try {
            var access = view.living("minecraft:overworld");
            if (access.isEmpty()) return;
            world = access.get();
        } catch (Exception failure) {
            guard("Reaching the Overworld", () -> { throw failure; });
            return;
        }
        guard("Removing dismissed settlers", () -> dismissals(world));
        guard("Handling placed blocks", () -> placements(world));
        Settlement c = camp();
        if (c == null) {
            arrivalStatus = "No settlement yet: place and light a campfire";
            return;
        }
        // A camp whose campfire chunk is unloaded is paused, including hunger and arrivals. No offline catch-up.
        boolean[] loaded = {false};
        if (!guard("Checking the campfire", () -> loaded[0] = world.block(c.campfire).isPresent()) || !loaded[0]) {
            arrivalStatus = "Paused: the campfire's chunk is not loaded";
            return;
        }
        c.elapsed += 20;
        guard("Scanning the land", () -> scanLand(world, c));
        guard("Surveying the land", () -> planner.tick(world, c));
        guard("Planning a home", () -> builder.maybeStart(world, c));
        guard("Choosing a builder", () -> assignBuilder(world, c));
        if (c.elapsed % 100 == 0) guard("Re-checking beds", () -> recheckBeds(world, c));
        if (!guard("Checking stockpile food", () -> foodAvailable = hasFood(view, c))) foodAvailable = false;
        for (var s : new ArrayList<>(c.settlers.values())) {
            if (s.terminal()) continue;
            if (!guard("Settler " + s.name + " (" + s.shortId() + ")", () -> step(world, c, s))) s.activity = "Update failed: see /frontier status";
        }
        guard("Arrivals", () -> arrivals(world, c));
        if (c.elapsed % 100 == 0) guard("Saving", this::save);
    }

    private void dismissals(CodaLivingWorld world) throws Exception {
        boolean changed = false;
        for (var it = state.dismissed.iterator(); it.hasNext(); ) {
            UUID id = it.next();
            try {
                if (world.actor(id).isEmpty()) continue; // absent: removed when their chunk next loads
                world.discard(id);
            } catch (IllegalArgumentException notASettler) {
                log("dismiss" + id, "[Frontier] Dismissed ID " + id + " is not a settler entity; it was left alone");
            }
            it.remove();
            changed = true;
        }
        if (changed) save();
    }

    private void placements(CodaLivingWorld world) throws Exception {
        while (!placements.isEmpty()) {
            var p = placements.removeFirst();
            if (!p.dimension().equals("minecraft:overworld")) continue;
            Settlement c = camp();
            if (p.blockId().equals("minecraft:campfire")) {
                if (c != null) continue; // later campfires never move or replace the settlement
                var b = world.block(p.position());
                if (b.isPresent() && b.get().id().equals("minecraft:campfire") && "true".equals(b.get().properties().get("lit")))
                    establish(world, p.position(), null);
            } else if (c != null) {
                CodaBlockPos head = bedHead(world, p.position());
                if (head != null && c.territory().contains(head) && !c.keptBeds.contains(head)
                        && c.beds.size() < Settlement.MAX_BEDS && shelteredBed(world, head)) c.beds.add(head);
            }
        }
    }

    Settlement establish(CodaLivingWorld world, CodaBlockPos campfire, UUID owner) throws Exception {
        requireReadable();
        if (camp() != null) throw new IllegalArgumentException("This world already has a Frontier settlement at " + text(camp().campfire));
        var block = world.block(campfire).orElseThrow(() -> new IllegalArgumentException("The campfire must be in a loaded area"));
        if (!block.id().equals("minecraft:campfire") || !"true".equals(block.properties().get("lit")))
            throw new IllegalArgumentException("That is not a lit campfire. Place and light an ordinary campfire first");
        var c = new Settlement(UUID.randomUUID(), owner, campfire);
        c.arrivalTimer = PIONEER_MIN + new Random(c.id.getLeastSignificantBits()).nextInt(PIONEER_SPREAD / 20 + 1) * 20;
        state.settlements.add(c);
        try {
            save();
        } catch (Exception failure) {
            state.settlements.remove(c);
            throw failure;
        }
        world.announce(Chat.good("Settlement founded.") + Chat.label(" Your land is ") + Chat.value(c.territory().describe()) + Chat.label(" around this campfire. A pioneer will arrive soon. ") + Chat.warn("/frontier help"));
        return c;
    }

    // ---- Beds ---------------------------------------------------------------------------

    /** Slow background sweep of the territory: beds, natural trees and crops. */
    private void scanLand(CodaLivingWorld world, Settlement c) throws Exception {
        Territory t = c.territory();
        int width = t.sideBlocks(), height = BED_SCAN_DEPTH * 2 + 1;
        long total = (long) width * width * height;
        for (int i = 0; i < BED_SCAN_BUDGET; i++) {
            long n = Math.floorMod(scanCursor++, total);
            if (scanCursor < 0) scanCursor = 0;
            var p = new CodaBlockPos(t.minBlockX() + (int) (n % width), c.campfire.y() - BED_SCAN_DEPTH + (int) (n / ((long) width * width)),
                    t.minBlockZ() + (int) ((n / width) % width));
            var b = world.block(p);
            if (b.isEmpty()) continue;
            jobs.scanned(world, p, b.get());
            boolean head = b.get().id().endsWith("_bed") && "head".equals(b.get().properties().get("part"));
            if (head && !c.keptBeds.contains(p) && shelteredBed(world, p)) {
                if (c.beds.size() < Settlement.MAX_BEDS) c.beds.add(p);
            } else c.beds.remove(p);
        }
    }

    private void recheckBeds(CodaLivingWorld world, Settlement c) throws Exception {
        for (var bed : new ArrayList<>(c.beds)) {
            var b = world.block(bed);
            if (b.isPresent() && !shelteredBed(world, bed)) c.beds.remove(bed);
        }
    }

    /** The head of a bed at or next to a placed position (placement reports the foot). */
    static CodaBlockPos bedHead(CodaLivingWorld world, CodaBlockPos p) throws Exception {
        for (int[] d : new int[][]{{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            var q = new CodaBlockPos(p.x() + d[0], p.y(), p.z() + d[1]);
            var b = world.block(q);
            if (b.isPresent() && b.get().id().endsWith("_bed") && "head".equals(b.get().properties().get("part"))) return q;
        }
        return null;
    }

    /** A bed head with headroom and a solid roof two to six blocks above it. */
    static boolean shelteredBed(CodaLivingWorld world, CodaBlockPos p) throws Exception {
        var b = world.block(p);
        if (b.isEmpty() || !b.get().id().endsWith("_bed") || !"head".equals(b.get().properties().get("part"))) return false;
        var above = world.block(add(p, 0, 1, 0));
        if (above.isEmpty() || !above.get().air()) return false;
        for (int y = 2; y <= 6; y++) {
            var sample = world.block(add(p, 0, y, 0));
            if (sample.isEmpty()) return false;
            if (sample.get().solid()) return true;
        }
        return false;
    }

    // ---- Arrivals -----------------------------------------------------------------------

    private void arrivals(CodaLivingWorld world, Settlement c) throws Exception {
        var fire = world.block(c.campfire).orElseThrow();
        if (!fire.id().equals("minecraft:campfire")) {
            arrivalStatus = "Stopped: the campfire at " + text(c.campfire) + " is gone. Place a campfire on that exact spot";
            return;
        }
        if (!"true".equals(fire.properties().get("lit"))) {
            arrivalStatus = "Stopped: the campfire at " + text(c.campfire) + " is not lit";
            return;
        }
        if (c.population() >= Settlement.MAX_POPULATION) {
            arrivalStatus = "The settlement is full (" + c.population() + "/" + Settlement.MAX_POPULATION + ")";
            return;
        }
        if (c.settlers.size() >= Settlement.MAX_RECORDS) {
            arrivalStatus = "Stopped: this settlement has reached " + Settlement.MAX_RECORDS + " settler records";
            return;
        }
        // One arrival at a time: a settler who has not appeared yet is reported, not overtaken.
        for (var s : c.settlers.values())
            if (s.state == COMMITTED) {
                arrivalStatus = (s.pioneer ? "Pioneer " : "") + s.name + " is on the way. " + s.activity;
                return;
            }
        // The first arrival, and anyone arriving at an empty camp, is a pioneer who needs no bed.
        // Losing every settler therefore never leaves a settlement unable to recruit again.
        if (!c.pioneerCommitted || c.population() == 0) {
            c.arrivalTimer = Math.max(0, c.arrivalTimer - 20);
            if (c.arrivalTimer > 0) {
                arrivalStatus = (c.pioneerCommitted ? "Nobody lives here now. A new pioneer" : "The pioneer")
                        + " arrives in about " + (c.arrivalTimer / 20) + "s of loaded camp time. No bed needed";
                return;
            }
            String problem = commit(world, c, null, true);
            arrivalStatus = problem == null ? "The pioneer is on the way" : problem;
            return;
        }
        List<CodaBlockPos> free = new ArrayList<>();
        for (var bed : c.freeBeds()) if (shelteredBed(world, bed)) free.add(bed);
        if (free.isEmpty()) {
            // Kept beds are never in c.beds, so an empty list means no usable bed exists.
            arrivalStatus = c.beds.isEmpty()
                    ?"Waiting for a bed: no sheltered beds in your territory yet. A bed needs a solid roof 2-6 blocks above its head"
                    : "Waiting for a bed: every sheltered bed is claimed. Build another one";
            return;
        }
        c.arrivalTimer = Math.max(0, c.arrivalTimer - 20);
        if (c.arrivalTimer > 0) {
            arrivalStatus = "Next settler in about " + (c.arrivalTimer / 20) + "s of loaded camp time";
            return;
        }
        String problem = commit(world, c, free.getFirst(), false);
        arrivalStatus = problem == null ? "A settler is on the way" : problem;
    }

    /** Save a new settler's identity and bed before touching the entity. Returns why not, or null. */
    private String commit(CodaLivingWorld world, Settlement c, CodaBlockPos bed, boolean pioneer) throws Exception {
        if (bed != null && "true".equals(world.block(bed).orElseThrow().properties().get("occupied")))
            return "Waiting: the free bed at " + text(bed) + " is occupied by someone sleeping in it";
        CodaBlockPos point = arrivalPoint(world, c);
        if (point == null) return "Waiting: no safe place to arrive near the edge of your territory. Clear some flat, dry ground there";
        var s = new Settler(UUID.randomUUID(), c.nextName());
        s.pioneer = pioneer;
        s.bed = bed;
        s.arrivalPoint = point;
        s.loyalty = pioneer ? 70 : 35 + Math.floorMod(s.id.hashCode(), 41);
        c.settlers.put(s.id, s);
        boolean pioneerBefore = c.pioneerCommitted;
        if (pioneer) c.pioneerCommitted = true;
        c.arrivalTimer = Settlement.ARRIVAL_INTERVAL;
        try {
            save();
        } catch (Exception failure) {
            c.settlers.remove(s.id);
            c.pioneerCommitted = pioneerBefore;
            throw failure;
        }
        trySpawn(world, c, s);
        return s.state == COMMITTED ? s.activity : null;
    }

    /**
     * Arrival points sit just inside the territory edge. Several columns along every side
     * are tried, each searched up and down for ground, so hills, trees and water rarely
     * block arrivals. Only spots the loader will accept for spawning are returned.
     */
    CodaBlockPos arrivalPoint(CodaLivingWorld world, Settlement c) throws Exception {
        Territory t = c.territory();
        int turn = (int) ((c.elapsed / 20) % 4);
        int[] along = {0, 4, -4, 8, -8, 12, -12};
        for (int i = 0; i < 4; i++) {
            int side = (turn + i) % 4;
            for (int offset : along) {
                int x = side == 0 ? t.maxBlockX() - 3 : side == 1 ? t.minBlockX() + 3 : clamp(c.campfire.x() + offset, t.minBlockX() + 3, t.maxBlockX() - 3);
                int z = side == 2 ? t.maxBlockZ() - 3 : side == 3 ? t.minBlockZ() + 3 : clamp(c.campfire.z() + offset, t.minBlockZ() + 3, t.maxBlockZ() - 3);
                var p = groundIn(world, x, z, c.campfire.y());
                if (p != null && t.contains(p) && !refusedArrivals.contains(p)) return p;
            }
        }
        return null;
    }

    /** The highest spawnable spot in a column within 8 blocks of {@code nearY}, or null. Never loads chunks. */
    static CodaBlockPos groundIn(CodaLivingWorld world, int x, int z, int nearY) throws Exception {
        for (int y = nearY + 8; y >= nearY - 8; y--) {
            var p = new CodaBlockPos(x, y, z);
            if (spawnable(world, p)) return p;
        }
        return null;
    }

    /**
     * Exactly the loader's spawn rule: air at the feet and head, solid dry ground below
     * that is not a campfire, magma or cactus. Walking targets ({@link #standing}) may be
     * looser, because settlers can walk through grass; spawning cannot.
     */
    static boolean spawnable(CodaLivingWorld world, CodaBlockPos p) throws Exception {
        var feet = world.block(p);
        var head = world.block(add(p, 0, 1, 0));
        var floor = world.block(add(p, 0, -1, 0));
        return feet.isPresent() && head.isPresent() && floor.isPresent() && feet.get().air() && head.get().air()
                && floor.get().solid() && !floor.get().fluid()
                && !Set.of("minecraft:magma_block", "minecraft:campfire", "minecraft:soul_campfire", "minecraft:cactus").contains(floor.get().id());
    }

    private static int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }

    private void trySpawn(CodaLivingWorld world, Settlement c, Settler s) throws Exception {
        if (world.actor(s.id).isPresent() || world.spawn(s.id, s.name, s.arrivalPoint)) {
            s.moveTo(ARRIVING);
            s.activity = s.pioneer ? "Walking to the campfire" : "Walking into the camp";
            save();
            world.announce(Chat.good(s.pioneer ? "A pioneer, " + s.name + ", is approaching your campfire." : s.name + " is approaching your settlement."));
            return;
        }
        // Refused (something in the way, or the spot changed): never retry the same spot forever.
        CodaBlockPos refused = s.arrivalPoint;
        refusedArrivals.add(refused);
        var next = arrivalPoint(world, c);
        if (next == null) {
            s.activity = "Cannot appear: no safe spot near the edge of your land. Clear some flat, dry, open ground there";
            return;
        }
        s.arrivalPoint = next;
        save();
        s.activity = "Could not appear at " + text(refused) + "; trying " + text(next) + " next";
    }

    // ---- One settler, once a second -------------------------------------------------------

    private void step(CodaLivingWorld world, Settlement c, Settler s) throws Exception {
        if (s.state == COMMITTED) {
            s.retryTicks += 20;
            if (s.retryTicks >= SPAWN_RETRY) {
                s.retryTicks = 0;
                trySpawn(world, c, s);
            }
            return;
        }
        if (pendingRescue.contains(s.id) && world.actor(s.id).isEmpty()) {
            var spot = nearCamp(world, c);
            if (spot != null && world.spawn(s.id, s.name, spot)) {
                pendingRescue.remove(s.id);
                progress.remove(s.id);
                s.arrivalPoint = spot;
                save();
            }
            return;
        }
        pendingRescue.remove(s.id);
        var entity = world.actor(s.id);
        if (entity.isEmpty()) {
            // Absent is not dead: keep the bed, pause hunger and work.
            s.activity = "Absent: in an unloaded area (paused)";
            return;
        }
        var actor = entity.get();
        if (actor.health() <= 0) {
            s.moveTo(DEAD);
            s.activity = "Dead";
            save();
            world.announce(Chat.bad(s.name + " has died."));
            return;
        }
        if (s.state == ARRIVING) {
            CodaBlockPos goal = s.bed != null ? s.bed : c.campfire;
            if (actor.distanceSquared(goal) <= (s.bed != null ? 9 : 16)) {
                s.moveTo(RESIDENT);
                s.activity = "Settling in";
                noteResidents(world, c);
                save();
                world.announce(Chat.good(s.name + " has joined your settlement."));
            } else if (stuckSeconds(s) >= RESCUE && rescue(world, c, s)) {
                return;
            } else if (!(s.bed != null ? navigate(world, s, s.bed, .65) : moveNear(world, s, c.campfire))) {
                s.activity = "Arrival path blocked: clear a route to the " + (s.bed != null ? "bed at " + text(s.bed) : "campfire");
            } else {
                s.activity = stuckSeconds(s) >= STUCK ? "Finding a way to the campfire" : s.pioneer ? "Walking to the campfire" : "Walking into the camp";
            }
            return;
        }
        s.hungerTicks += 20;
        if (s.hungerTicks >= Settler.HUNGER_STEP_TICKS) {
            s.hungerTicks -= Settler.HUNGER_STEP_TICKS;
            if (s.hungerStep()) {
                save();
                world.announce(Chat.warn(s.name + " is leaving because there is not enough food."));
            }
        }
        if (s.hunger == 0) {
            world.damage(s.id, 1);
            s.activity = "Starving";
        }
        if (s.state == LEAVING) {
            world.wake(s.id);
            if (actor.distanceSquared(s.arrivalPoint) <= 9) {
                s.moveTo(DEPARTED);
                s.activity = "Left the settlement";
                save();
                world.discard(s.id); // departing settlers take what they carry
                world.announce(Chat.warn(s.name + " has left the settlement."));
            } else if (!navigate(world, s, s.arrivalPoint, .7)) {
                s.activity = "Leaving, but the way out is blocked";
            }
            return;
        }
        resident(world, c, s, actor);
    }

    private void resident(CodaLivingWorld world, Settlement c, Settler s, CodaLivingWorld.Actor actor) throws Exception {
        if (s.bed != null && (c.keptBeds.contains(s.bed) || (world.block(s.bed).isPresent() && !shelteredBed(world, s.bed)))) {
            if (!c.keptBeds.contains(s.bed)) c.beds.remove(s.bed);
            s.bed = null;
            save();
        }
        if (s.bed == null) {
            for (var bed : c.freeBeds()) if (shelteredBed(world, bed)) { s.bed = bed; save(); break; }
        }
        if (s.hunger <= Settler.HUNGRY && foodAvailable && stockpileProblem(world, c) == null) {
            world.wake(s.id);
            if (actor.distanceSquared(c.stockpile) <= 9) {
                if (world.eat(s.id, c.stockpile, FOOD)) {
                    s.meal();
                    s.activity = "Eating";
                }
            } else {
                s.activity = "Going to the stockpile for food";
                moveNear(world, s, c.stockpile);
                return;
            }
        }
        if (s.hunger < Settler.TOO_HUNGRY_TO_WORK) {
            s.activity = "Too hungry to work";
            return;
        }
        long time = Math.floorMod(world.clock(), 24000);
        if (time >= 12500 && time < 23500) {
            if (s.bed == null) {
                s.activity = "Resting by the campfire (no bed)";
                if (actor.distanceSquared(c.campfire) > 36) moveNear(world, s, c.campfire);
            } else if (actor.distanceSquared(s.bed) <= 6.25) {
                s.activity = world.sleep(s.id, s.bed) ? "Sleeping" : "Trying to sleep";
            } else {
                s.activity = "Going to bed";
                navigate(world, s, s.bed, .6);
            }
            return;
        }
        world.wake(s.id);
        work(world, c, s, actor);
    }

    private void work(CodaLivingWorld world, Settlement c, Settler s, CodaLivingWorld.Actor actor) throws Exception {
        jobs.work(world, view, c, s, actor);
    }

    /** A project needs one builder: the pioneer if free, otherwise any resident whose job was not chosen by the owner. */
    private void assignBuilder(CodaLivingWorld world, Settlement c) throws Exception {
        if (c.project == null) return;
        Settler pick = null;
        for (var s : c.settlers.values()) {
            if (!s.resident()) continue;
            if (s.profession == Settler.Profession.BUILDER) return;
            if (s.jobChosen) continue;
            if (pick == null || (s.pioneer && !pick.pioneer)) pick = s;
        }
        if (pick == null) return;
        pick.profession = Settler.Profession.BUILDER;
        pick.target = null;
        save();
        world.announce(Chat.good(pick.name + " will build the new home."));
    }

    /** An arrival that cannot find any way in for a while is brought to the campfire instead. */
    private boolean rescue(CodaLivingWorld world, Settlement c, Settler s) throws Exception {
        var spot = nearCamp(world, c);
        if (spot == null) return false;
        world.discard(s.id);
        if (!world.spawn(s.id, s.name, spot)) {
            pendingRescue.add(s.id);
            s.activity = "Finding a way to the campfire";
            return true;
        }
        progress.remove(s.id);
        s.arrivalPoint = spot;
        save();
        world.announce(Chat.label(s.name + " found a way through to the campfire."));
        return true;
    }
    private final Set<UUID> pendingRescue = new HashSet<>();

    private void noteResidents(CodaLivingWorld world, Settlement c) throws Exception {
        if (c.recordResidents())
            world.announce(Chat.heading("Your land grew!") + Chat.label(" " + c.peakResidents() + " residents unlocked ") + Chat.good(c.territory().describe()) + Chat.label("."));
    }

    // ---- Movement -------------------------------------------------------------------------

    /** Runtime only: where each moving settler stood, to notice when one is stuck. */
    private static final class Progress { double x, y, z; long second = -10; int still; boolean left; }
    private final Map<UUID, Progress> progress = new HashMap<>();
    /** Seconds without moving before a stuck settler steps aside, and (arrivals) before it is brought in. */
    static final int STUCK = 4, RESCUE = 45;
    static final int[] DETOUR_DISTANCES = {22, 14, 8, 4};
    static final int[][] DETOUR_ANGLES = {{0, 30, -30}, {60, -60, 90, -90}};

    /** How many seconds this settler has been trying to move without getting anywhere. */
    int stuckSeconds(Settler s) {
        var p = progress.get(s.id);
        return p == null || (camp() != null && camp().elapsed / 20 - p.second > 2) ? 0 : p.still;
    }

    /**
     * Walk towards a target of any distance. Native pathfinding reaches about 32 blocks and gives up
     * on some routes (trees, slopes, water), so when the direct way is refused the settler walks to
     * the best reachable spot in the target's direction instead, fanning out sideways around
     * obstacles. A settler that has not moved for a few seconds first steps aside, the way a nudge
     * from the player frees it.
     */
    boolean navigate(CodaLivingWorld world, Settler s, CodaBlockPos target, double speed) throws Exception {
        var actor = world.actor(s.id);
        if (actor.isEmpty()) return false;
        var at = actor.get();
        var pr = progress.computeIfAbsent(s.id, k -> new Progress());
        long now = camp() == null ? 0 : camp().elapsed / 20;
        if (now != pr.second) {
            double moved = Math.pow(at.x() - pr.x, 2) + Math.pow(at.y() - pr.y, 2) + Math.pow(at.z() - pr.z, 2);
            if (now - pr.second > 2 || moved > .5 * .5) { pr.x = at.x(); pr.y = at.y(); pr.z = at.z(); pr.still = 0; }
            else pr.still++;
            pr.second = now;
        }
        double dx = target.x() + .5 - at.x(), dz = target.z() + .5 - at.z(), length = Math.sqrt(dx * dx + dz * dz);
        double heading = Math.atan2(dz, dx);
        boolean stuck = pr.still >= STUCK;
        if (stuck && pr.still % STUCK == 0) {
            // Step aside first: a short walk to any free spot re-plans from somewhere else.
            pr.left = !pr.left;
            for (int a : new int[]{pr.left ? 90 : -90, pr.left ? -90 : 90, 180, 45, -45})
                if (tryWalk(world, s, at, heading + Math.toRadians(a), 3, speed)) return true;
        }
        if (!stuck && length <= 28 && world.walk(s.id, target, speed)) return true;
        if (stuck && length <= 28 && pr.still % STUCK != 0 && world.walk(s.id, target, speed)) return true;
        // Mostly forward first, at every distance; then wider around obstacles.
        for (int[] angles : DETOUR_ANGLES)
            for (int distance : DETOUR_DISTANCES) {
                if (distance >= length + 2) continue;
                for (int a : angles)
                    if (tryWalk(world, s, at, heading + Math.toRadians(pr.left ? -a : a), distance, speed)) return true;
            }
        return false;
    }

    private boolean tryWalk(CodaLivingWorld world, Settler s, CodaLivingWorld.Actor at, double angle, int distance, double speed) throws Exception {
        var spot = new CodaBlockPos((int) Math.floor(at.x() + Math.cos(angle) * distance), (int) Math.floor(at.y()), (int) Math.floor(at.z() + Math.sin(angle) * distance));
        var stand = standing(world, spot, 1);
        return stand != null && world.walk(s.id, stand, speed);
    }

    /** A spot near the campfire where a settler can appear, for bringing in a stuck arrival. */
    CodaBlockPos nearCamp(CodaLivingWorld world, Settlement c) throws Exception {
        CodaBlockPos best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (int x = -8; x <= 8; x++)
            for (int z = -8; z <= 8; z++) {
                int d = x * x + z * z;
                if (d < 4 || d >= bestDistance) continue;
                var p = groundIn(world, c.campfire.x() + x, c.campfire.z() + z, c.campfire.y());
                if (p != null && c.territory().contains(p)) { best = p; bestDistance = d; }
            }
        return best;
    }

    boolean moveNear(CodaLivingWorld world, Settler s, CodaBlockPos target) throws Exception {
        CodaBlockPos stand = standing(world, target, 2);
        boolean moving = stand != null && navigate(world, s, stand, s.hunger < 30 ? .4 : .65);
        if (!moving && !s.activity.startsWith("Path blocked")) s.activity = "Path blocked: " + s.activity;
        return moving;
    }

    /** The nearest place a settler can stand: passable feet and head, solid dry floor. */
    static CodaBlockPos standing(CodaLivingWorld world, CodaBlockPos origin, int radius) throws Exception {
        for (int dy = 0; dy <= 3; dy++)
            for (int y : dy == 0 ? new int[]{0} : new int[]{dy, -dy})
                for (int r = 0; r <= radius; r++)
                    for (int x = -r; x <= r; x++)
                        for (int z = -r; z <= r; z++) {
                            if (Math.max(Math.abs(x), Math.abs(z)) != r) continue;
                            var p = add(origin, x, y, z);
                            var feet = world.block(p);
                            var head = world.block(add(p, 0, 1, 0));
                            var floor = world.block(add(p, 0, -1, 0));
                            if (feet.isPresent() && head.isPresent() && floor.isPresent() && passable(feet.get()) && passable(head.get())
                                    && (floor.get().solid() || floor.get().id().equals("minecraft:farmland")) && !floor.get().fluid()
                                    && !Set.of("minecraft:campfire", "minecraft:soul_campfire", "minecraft:magma_block", "minecraft:cactus").contains(floor.get().id()))
                                return p;
                        }
        return null;
    }

    /** Blocks that hurt, trap or burn a settler walking through them. */
    static final Set<String> HARMFUL = Set.of("minecraft:fire", "minecraft:soul_fire", "minecraft:sweet_berry_bush", "minecraft:cobweb",
            "minecraft:powder_snow", "minecraft:wither_rose", "minecraft:campfire", "minecraft:soul_campfire", "minecraft:cactus",
            "minecraft:magma_block", "minecraft:pointed_dripstone", "minecraft:lava", "minecraft:water");

    /** Anything a settler walks through: air, grass, flowers, ferns, bushes, crops, leaf litter. */
    static boolean passable(CodaLivingWorld.Block b) {
        return b.air() || (!b.solid() && !b.fluid() && !HARMFUL.contains(b.id()) && !b.id().endsWith("_door") && !b.id().endsWith("_bed"));
    }

    // ---- Stockpile ------------------------------------------------------------------------

    /** Reads the stockpile now, so status is correct before the first update. */
    boolean hasFood(CodaWorldView view, Settlement c) throws Exception {
        return c.stockpile != null && view.itemInventory(c.dimension, c.stockpile)
                .map(inventory -> inventory.slots().stream().anyMatch(slot -> slot.count() > 0 && FOOD.contains(slot.itemId()))).orElse(false);
    }

    /** Null when the stockpile is usable; otherwise a plain explanation. Never loads chunks. */
    static String stockpileProblem(CodaLivingWorld world, Settlement c) throws Exception {
        if (c.stockpile == null) return "No stockpile yet: look at a chest or barrel inside your territory and use /frontier stockpile";
        var b = world.block(c.stockpile);
        if (b.isEmpty()) return "The stockpile at " + text(c.stockpile) + " is in an unloaded area";
        if (!CONTAINERS.contains(b.get().id()))
            return "The stockpile at " + text(c.stockpile) + " is missing: there is no chest or barrel there any more. Look at one and use /frontier stockpile";
        return null;
    }

    static CodaBlockPos add(CodaBlockPos p, int x, int y, int z) { return new CodaBlockPos(p.x() + x, p.y() + y, p.z() + z); }
    static String text(CodaBlockPos p) { return p.x() + " " + p.y() + " " + p.z(); }
}
