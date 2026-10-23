package dev.howlingwhispers.frontier;

import dev.howlingwhispers.codaloader.api.*;
import java.util.*;
import static dev.howlingwhispers.frontier.FrontierSimulation.add;
import static dev.howlingwhispers.frontier.FrontierSimulation.text;
import static dev.howlingwhispers.frontier.Settler.Profession.*;

/**
 * What residents do in daylight. Everything uses real items and the loader's native actions,
 * whose rules this code respects exactly (CodaLoader 0.0.45 MinecraftLivingWorld):
 * <ul>
 *   <li>eat, deliver, collect and fetching a tool need the settler within 3 blocks of the stockpile;</li>
 *   <li>harvest needs the right tool already in hand and the block within 6 blocks, and fails when the 8-slot bag is full;</li>
 *   <li>plant needs the settler within 3 blocks, air at the spot, and the real seed or sapling.</li>
 * </ul>
 * All work stays inside the settlement's territory. Lumberjacks only cut natural trees
 * (natural leaves are not "persistent"), so log houses and player builds are never touched.
 */
final class FrontierJobs {
    static final Set<String> AXES = tools("axe"), PICKS = tools("pickaxe"), HOES = tools("hoe");
    /** Unload once carrying this many items, well before the 8-slot bag fills. */
    static final int CARRY_LIMIT = 48;
    /** Logs a lumberjack can reach from the ground: the top one must be within 6 blocks. */
    static final int MAX_TRUNK = 6;
    static final int MAX_TUNNEL = 32;
    /** The loader breaks blocks within 6 blocks; keep a small margin for movement. */
    static final double REACH = 5.5;
    static final Set<String> GROUND_FOR_TREES = Set.of("minecraft:dirt", "minecraft:grass_block", "minecraft:podzol",
            "minecraft:coarse_dirt", "minecraft:rooted_dirt", "minecraft:mycelium", "minecraft:moss_block", "minecraft:mud");
    /** Crop block, ripe age, and the item that replants it. */
    record Crop(String ripeAge, String seed) {}
    static final Map<String, Crop> CROPS = Map.of(
            "minecraft:wheat", new Crop("7", "minecraft:wheat_seeds"),
            "minecraft:carrots", new Crop("7", "minecraft:carrot"),
            "minecraft:potatoes", new Crop("7", "minecraft:potato"),
            "minecraft:beetroots", new Crop("3", "minecraft:beetroot_seeds"));
    static final Set<String> MINEABLE = Set.of("minecraft:stone", "minecraft:deepslate", "minecraft:andesite", "minecraft:diorite",
            "minecraft:granite", "minecraft:tuff", "minecraft:dirt", "minecraft:coarse_dirt", "minecraft:cobblestone",
            "minecraft:coal_ore", "minecraft:deepslate_coal_ore", "minecraft:iron_ore", "minecraft:deepslate_iron_ore",
            "minecraft:copper_ore", "minecraft:deepslate_copper_ore", "minecraft:gold_ore", "minecraft:deepslate_gold_ore",
            "minecraft:redstone_ore", "minecraft:deepslate_redstone_ore", "minecraft:lapis_ore", "minecraft:deepslate_lapis_ore",
            "minecraft:diamond_ore", "minecraft:deepslate_diamond_ore", "minecraft:emerald_ore", "minecraft:deepslate_emerald_ore",
            "minecraft:sandstone", "minecraft:red_sandstone", "minecraft:terracotta", "minecraft:white_terracotta", "minecraft:orange_terracotta",
            "minecraft:yellow_terracotta", "minecraft:brown_terracotta", "minecraft:red_terracotta", "minecraft:light_gray_terracotta");
    static final Set<String> FALLING = Set.of("minecraft:sand", "minecraft:red_sand", "minecraft:gravel", "minecraft:suspicious_sand", "minecraft:suspicious_gravel");

    private final FrontierSimulation sim;
    /** Natural tree bases and crops found by the land scan. Runtime only: found again after reopening. */
    final LinkedHashSet<CodaBlockPos> trees = new LinkedHashSet<>(), crops = new LinkedHashSet<>();
    /** Trees a lumberjack tried and could not cut (too tall, unreachable). Runtime only. */
    final Set<CodaBlockPos> skipped = new HashSet<>();

    FrontierJobs(FrontierSimulation sim) { this.sim = sim; }

    static Set<String> tools(String type) {
        Set<String> s = new HashSet<>();
        for (String m : List.of("wooden", "stone", "copper", "iron", "golden", "diamond", "netherite")) s.add("minecraft:" + m + "_" + type);
        return Set.copyOf(s);
    }

    void clear() {
        trees.clear();
        crops.clear();
        skipped.clear();
    }

    // ---- Land scan: called for every scanned block ------------------------------------------

    void scanned(CodaLivingWorld world, CodaBlockPos p, CodaLivingWorld.Block b) throws Exception {
        String id = b.id();
        if (isLog(id)) {
            var below = world.block(add(p, 0, -1, 0));
            if (below.isPresent() && GROUND_FOR_TREES.contains(below.get().id())) {
                if (trees.size() < 1024) trees.add(p);
                return;
            }
        }
        trees.remove(p);
        if (CROPS.containsKey(id)) {
            var below = world.block(add(p, 0, -1, 0));
            if (below.isPresent() && below.get().id().equals("minecraft:farmland")) {
                if (crops.size() < 2048) crops.add(p);
                return;
            }
        }
        crops.remove(p);
    }

    static boolean isLog(String id) { return id.endsWith("_log") && !id.startsWith("minecraft:stripped_"); }

    // ---- Daylight work -----------------------------------------------------------------------

    void work(CodaLivingWorld world, CodaWorldView view, Settlement c, Settler s, CodaLivingWorld.Actor actor) throws Exception {
        if (FrontierSimulation.stockpileProblem(world, c) != null) {
            idle(world, c, s, actor, "Waiting at the campfire. Set a stockpile with /frontier stockpile");
            return;
        }
        // Builders carry their building materials on purpose; everyone else unloads when the bag fills.
        if (s.delivering || (s.profession != BUILDER && world.carried(s.id) >= CARRY_LIMIT)) {
            deliver(world, c, s, actor);
            return;
        }
        if (s.profession == LABOURER && !s.jobChosen) choose(world, view, c, s);
        switch (s.profession) {
            case LUMBERJACK -> lumberjack(world, c, s, actor);
            case FARMER -> farmer(world, c, s, actor);
            case MINER -> miner(world, c, s, actor);
            case BUILDER -> {
                if (!sim.builder.work(world, view, c, s, actor)) {
                    s.profession = LABOURER;
                    s.jobChosen = false;
                    idle(world, c, s, actor, "Nothing to build right now");
                }
            }
            case LABOURER -> idle(world, c, s, actor, "No job yet. Put an axe, a hoe (with crops planted) or a pickaxe in the stockpile");
        }
    }

    /** A labourer takes the first job whose tool is in the stockpile and which has work to do. */
    private void choose(CodaLivingWorld world, CodaWorldView view, Settlement c, Settler s) throws Exception {
        Set<String> stocked = new HashSet<>();
        view.itemInventory(c.dimension, c.stockpile).ifPresent(inv -> inv.slots().forEach(slot -> { if (slot.count() > 0) stocked.add(slot.itemId()); }));
        Settler.Profession next = null;
        String tool = null;
        if (!Collections.disjoint(stocked, HOES) && !crops.isEmpty()) { next = FARMER; tool = "a hoe"; }
        else if (!Collections.disjoint(stocked, AXES)) { next = LUMBERJACK; tool = "an axe"; }
        else if (!Collections.disjoint(stocked, PICKS)) { next = MINER; tool = "a pickaxe"; }
        if (next == null) return;
        s.profession = next;
        s.target = null;
        sim.save();
        world.announce(Chat.good(s.name + " found " + tool + " in the stockpile and will work as a " + next.name().toLowerCase(Locale.ROOT) + "."));
    }

    /** True when the right tool is in hand; otherwise walks to the stockpile for one. */
    private boolean tool(CodaLivingWorld world, Settlement c, Settler s, CodaLivingWorld.Actor actor, Set<String> kinds, String name) throws Exception {
        if (world.equip(s.id, c.stockpile, kinds)) return true;
        if (actor.distanceSquared(c.stockpile) > 9) {
            s.activity = "Fetching " + name + " from the stockpile";
            sim.moveNear(world, s, c.stockpile);
            return false;
        }
        if (!s.jobChosen) {
            // Picked automatically and the tool is gone: free to pick another job.
            s.profession = LABOURER;
            s.target = null;
            sim.save();
        }
        s.activity = "Needs " + name + ": put one in the stockpile";
        return false;
    }

    private void deliver(CodaLivingWorld world, Settlement c, Settler s, CodaLivingWorld.Actor actor) throws Exception {
        s.delivering = true;
        if (actor.distanceSquared(c.stockpile) > 9) {
            s.activity = "Carrying supplies to the stockpile";
            sim.moveNear(world, s, c.stockpile);
            return;
        }
        int moved = world.deliver(s.id, c.stockpile);
        if (world.carried(s.id) == 0) {
            s.delivering = false;
            s.activity = "Delivered " + moved + " items";
        } else {
            s.activity = moved == 0 ? "The stockpile is full: make room in it" : "Unloading";
        }
    }

    private void idle(CodaLivingWorld world, Settlement c, Settler s, CodaLivingWorld.Actor actor, String why) throws Exception {
        s.activity = why;
        if (actor.distanceSquared(c.campfire) > 36) sim.moveNear(world, s, c.campfire);
    }

    /** Someone else's current target. */
    private boolean claimedByOther(Settlement c, Settler s, CodaBlockPos p) {
        for (var other : c.settlers.values()) if (other != s && other.resident() && p.equals(other.target)) return true;
        return false;
    }

    // ---- Lumberjack ----------------------------------------------------------------------------

    /** For builders who fell trees for their own building. */
    void lumberjackFor(CodaLivingWorld world, Settlement c, Settler s, CodaLivingWorld.Actor actor) throws Exception {
        lumberjack(world, c, s, actor);
    }

    private void lumberjack(CodaLivingWorld world, Settlement c, Settler s, CodaLivingWorld.Actor actor) throws Exception {
        if (!tool(world, c, s, actor, AXES, "an axe")) return;
        if (s.target != null && !isTreeBase(world, s.target)) s.target = null;
        if (s.target == null) {
            // Choose a tree at the stockpile, so a matching sapling can be taken for replanting.
            if (actor.distanceSquared(c.stockpile) > 9) {
                s.activity = "Going to the stockpile before the next tree";
                sim.moveNear(world, s, c.stockpile);
                return;
            }
            s.target = nearestTree(world, c, s);
            if (s.target == null) {
                idle(world, c, s, actor, "No trees to cut in your land (natural trees up to " + MAX_TRUNK + " logs tall)");
                return;
            }
            String sapling = saplingFor(world.block(s.target).orElseThrow().id());
            if (sapling != null) world.collect(s.id, c.stockpile, CodaItemFilter.only(sapling), 1);
        }
        CodaBlockPos base = s.target;
        int height = trunk(world, base);
        CodaBlockPos top = add(base, 0, height - 1, 0);
        if (actor.distanceSquared(top) > REACH * REACH) {
            // Low leaves can block the trunk's sides, so stand anywhere within 4 blocks that can reach the top log.
            CodaBlockPos stand = FrontierSimulation.standing(world, base, 4);
            if (stand == null || distanceSquared(stand, top) > REACH * REACH || !sim.navigate(world, s, stand, .65)) {
                skipped.add(base);
                s.target = null;
                s.activity = "Cannot reach the tree at " + text(base) + "; looking for another";
                return;
            }
            s.activity = "Walking to a tree";
            return;
        }
        String log = world.block(top).orElseThrow().id();
        if (!sim.pace.take(s.id, false)) { s.activity = "Felling a tree"; return; }
        if (!world.harvest(s.id, top, log, c.stockpile, AXES)) {
            if (world.carried(s.id) > 0) s.delivering = true;
            else {
                skipped.add(base);
                s.target = null;
                s.activity = "Could not cut the tree at " + text(base);
            }
            return;
        }
        s.activity = "Chopping a tree";
        if (height > 1) return;
        // The last log is gone: replant, then take the wood home.
        String sapling = saplingFor(log);
        boolean replanted = sapling != null && world.plant(s.id, base, sapling, c.stockpile);
        s.activity = replanted ? "Felled a tree and planted a sapling" : "Felled a tree (no sapling in the stockpile to replant)";
        trees.remove(base);
        s.target = null;
        s.delivering = true;
    }

    private CodaBlockPos nearestTree(CodaLivingWorld world, Settlement c, Settler s) throws Exception {
        Territory t = c.territory();
        List<CodaBlockPos> candidates = new ArrayList<>();
        for (var p : trees) if (t.contains(p) && !skipped.contains(p) && !claimedByOther(c, s, p)) candidates.add(p);
        candidates.sort(Comparator.comparingLong(p -> distance(p, c.stockpile)));
        for (var p : candidates.subList(0, Math.min(16, candidates.size()))) {
            if (!isTreeBase(world, p)) { trees.remove(p); continue; }
            if (trunk(world, p) > MAX_TRUNK || !naturalCanopy(world, p, trunk(world, p))) { skipped.add(p); continue; }
            return p;
        }
        return null;
    }

    private boolean isTreeBase(CodaLivingWorld world, CodaBlockPos p) throws Exception {
        var b = world.block(p);
        var below = world.block(add(p, 0, -1, 0));
        return b.isPresent() && below.isPresent() && isLog(b.get().id()) && GROUND_FOR_TREES.contains(below.get().id());
    }

    /** Logs of the same kind stacked from the base upwards. */
    static int trunk(CodaLivingWorld world, CodaBlockPos base) throws Exception {
        String id = world.block(base).map(CodaLivingWorld.Block::id).orElse("");
        int h = 0;
        while (h < 32) {
            var b = world.block(add(base, 0, h, 0));
            if (b.isEmpty() || !b.get().id().equals(id)) break;
            h++;
        }
        return h;
    }

    /** At least four natural (non-persistent) leaves around the top of the trunk: a real tree, not a build. */
    static boolean naturalCanopy(CodaLivingWorld world, CodaBlockPos base, int height) throws Exception {
        int natural = 0;
        for (int y = Math.max(1, height - 2); y <= height + 2; y++)
            for (int x = -2; x <= 2; x++)
                for (int z = -2; z <= 2; z++) {
                    var b = world.block(add(base, x, y, z));
                    if (b.isPresent() && b.get().id().endsWith("_leaves") && "false".equals(b.get().properties().get("persistent")) && ++natural >= 4) return true;
                }
        return false;
    }

    static String saplingFor(String log) {
        if (!log.endsWith("_log")) return null;
        String wood = log.substring(0, log.length() - 4);
        if (wood.equals("minecraft:mangrove")) return "minecraft:mangrove_propagule";
        if (wood.equals("minecraft:crimson") || wood.equals("minecraft:warped")) return null;
        return wood + "_sapling";
    }

    // ---- Farmer ----------------------------------------------------------------------------------

    private void farmer(CodaLivingWorld world, Settlement c, Settler s, CodaLivingWorld.Actor actor) throws Exception {
        if (!tool(world, c, s, actor, HOES, "a hoe")) return;
        if (s.target != null && !ripe(world, s.target)) s.target = null;
        if (s.target == null) s.target = nearestRipe(world, c, s, actor);
        if (s.target == null) {
            if (world.carried(s.id) > 0) s.delivering = true;
            else idle(world, c, s, actor, crops.isEmpty() ? "No crops planted on farmland in your land yet" : "Waiting for crops to ripen");
            return;
        }
        CodaBlockPos crop = s.target;
        if (actor.distanceSquared(crop) > 4) {
            s.activity = "Walking to ripe crops";
            if (!sim.moveNear(world, s, crop)) s.target = null;
            return;
        }
        String id = world.block(crop).orElseThrow().id();
        if (!world.harvest(s.id, crop, id, c.stockpile, Set.of())) {
            if (world.carried(s.id) > 0) s.delivering = true;
            else s.target = null;
            return;
        }
        boolean replanted = world.plant(s.id, crop, CROPS.get(id).seed(), c.stockpile);
        s.activity = replanted ? "Harvesting and replanting" : "Harvested, but had nothing to replant with";
        s.target = null;
    }

    private boolean ripe(CodaLivingWorld world, CodaBlockPos p) throws Exception {
        var b = world.block(p);
        if (b.isEmpty()) return false;
        var crop = CROPS.get(b.get().id());
        if (crop == null) { crops.remove(p); return false; }
        return crop.ripeAge().equals(b.get().properties().get("age"));
    }

    private CodaBlockPos nearestRipe(CodaLivingWorld world, Settlement c, Settler s, CodaLivingWorld.Actor actor) throws Exception {
        Territory t = c.territory();
        CodaBlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (var p : new ArrayList<>(crops)) {
            if (!t.contains(p) || claimedByOther(c, s, p) || !ripe(world, p)) continue;
            double d = actor.distanceSquared(p);
            if (d < bestDistance) { best = p; bestDistance = d; }
        }
        return best;
    }

    // ---- Miner -------------------------------------------------------------------------------------

    /** For builders who dig rock for their own building. */
    void minerFor(CodaLivingWorld world, Settlement c, Settler s, CodaLivingWorld.Actor actor) throws Exception {
        miner(world, c, s, actor);
    }

    /**
     * The settlement's one shared mine. Miners (and builders who need rock) all work in it, one at
     * the face at a time. Without a mine the first miner chooses one: a stairway dug down from the
     * surface, which settlers can always walk in and out of. A mine the player set with
     * /frontier mine is a straight tunnel from where they looked.
     */
    private void miner(CodaLivingWorld world, Settlement c, Settler s, CodaLivingWorld.Actor actor) throws Exception {
        if (c.mineEntrance == null && !chooseMine(world, c, s)) {
            idle(world, c, s, actor, s.activity.isEmpty() ? "Looking for a place for the mine" : s.activity);
            return;
        }
        if (!tool(world, c, s, actor, PICKS, "a pickaxe")) return;
        var blocks = mineBlocks(c.mineEntrance, c.mineDirection, c.mineShape);
        if (c.mineProgress >= blocks.size() || !c.territory().contains(blocks.get(c.mineProgress))) {
            if (world.carried(s.id) > 0) { s.target = null; s.delivering = true; return; }
            finishMine(world, c, "is finished");
            return;
        }
        var face = blocks.get(c.mineProgress);
        var b = world.block(face);
        if (b.isEmpty()) { s.activity = "Mining paused: the mine is in an unloaded area"; return; }
        if (b.get().air() || (!b.get().solid() && !b.get().fluid())) {
            // Already open (a cave, or grass and flowers at the top of the stairs): plants are cleared by hand.
            if (!b.get().air() && !world.harvest(s.id, face, b.get().id(), c.stockpile, Set.of())) {
                if (!b.get().air() && walkToFace(world, c, s, actor, face)) s.activity = "Mining paused: cannot clear the " + b.get().id().replace("minecraft:", "") + " at " + text(face);
                return;
            }
            c.mineProgress++;
            sim.save();
            return;
        }
        String id = b.get().id();
        if (!MINEABLE.contains(id) && !CLEAR_BY_HAND.contains(id)) {
            if (autoMine(c)) { finishMine(world, c, "reached " + id.replace("minecraft:", "") + ", which miners do not dig, and is closed"); return; }
            s.activity = "Mining paused at " + id.replace("minecraft:", "") + ": miners only dig stone, dirt, ore, sandstone and terracotta";
            return;
        }
        for (int[] n : new int[][]{{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, 0, 1}, {0, 0, -1}}) {
            var near = world.block(add(face, n[0], n[1], n[2]));
            if (near.isEmpty() || near.get().fluid() || FALLING.contains(near.get().id())) {
                if (autoMine(c)) { finishMine(world, c, "reached water, lava, sand or gravel and is closed"); return; }
                s.activity = "Mining paused: water, lava, sand or gravel next to the tunnel";
                return;
            }
        }
        if (claimedByOther(c, s, face)) {
            s.target = null;
            idle(world, c, s, actor, "Waiting for a turn at the face of the mine");
            return;
        }
        s.target = face;
        if (!walkToFace(world, c, s, actor, face)) return;
        Set<String> tools = CLEAR_BY_HAND.contains(id) ? Set.of() : PICKS;
        if (!sim.pace.take(s.id, false)) { s.activity = "Mining"; return; }
        if (world.harvest(s.id, face, id, c.stockpile, tools)) {
            c.mineProgress++;
            sim.save();
            s.activity = "Mining";
            if (sim.pace.more(s.id, false)) miner(world, c, s, world.actor(s.id).orElse(actor));
        } else if (world.carried(s.id) > 0) {
            s.target = null;
            s.delivering = true;
        } else {
            s.activity = "Needs a better pickaxe for " + id.replace("minecraft:", "");
        }
    }

    /** Dirt, grass and the like at the top of a stairway: they come away by hand, no pickaxe needed. */
    static final Set<String> CLEAR_BY_HAND = Set.of("minecraft:grass_block", "minecraft:dirt", "minecraft:coarse_dirt", "minecraft:podzol",
            "minecraft:rooted_dirt", "minecraft:mycelium", "minecraft:moss_block", "minecraft:clay", "minecraft:mud", "minecraft:snow_block");
    /** Stairway mines go this many steps down before levelling out. */
    static final int STAIR_DEPTH = 8;

    static boolean autoMine(Settlement c) { return "stairs".equals(c.mineShape); }

    /** The blocks of a mine in digging order. A tunnel: head then feet of each step. Stairs: three high while going down. */
    static List<CodaBlockPos> mineBlocks(CodaBlockPos entrance, String direction, String shape) {
        int[] d = direction(direction);
        List<CodaBlockPos> list = new ArrayList<>();
        boolean stairs = "stairs".equals(shape);
        for (int i = 0; i < MAX_TUNNEL; i++) {
            var feet = mineFeet(entrance, direction, shape, i);
            if (stairs && i > 0 && i <= STAIR_DEPTH) list.add(add(feet, 0, 2, 0));
            list.add(add(feet, 0, 1, 0));
            list.add(feet);
        }
        return list;
    }

    /** Where a settler's feet are in column {@code i} of the mine. */
    static CodaBlockPos mineFeet(CodaBlockPos entrance, String direction, String shape, int i) {
        int[] d = direction(direction);
        int down = "stairs".equals(shape) ? Math.min(i, STAIR_DEPTH) : 0;
        return new CodaBlockPos(entrance.x() + d[0] * i, entrance.y() - down, entrance.z() + d[1] * i);
    }

    private static int column(CodaBlockPos entrance, String direction, CodaBlockPos p) {
        int[] d = direction(direction);
        return d[0] != 0 ? (p.x() - entrance.x()) * d[0] : (p.z() - entrance.z()) * d[1];
    }

    /**
     * Get within reach of the face the way a player would: first to the mouth of the mine on the
     * surface (or at the cave the player chose), then down the dug columns. Digging starts as soon
     * as the face is within reach, wherever in the mine the settler stands. Waypoints are always at
     * least two columns ahead: the loader's pathfinder treats anything within a block as already
     * reached and would not move the settler at all (0.4.1 left miners standing still).
     */
    private boolean walkToFace(CodaLivingWorld world, Settlement c, Settler s, CodaLivingWorld.Actor actor, CodaBlockPos face) throws Exception {
        int faceColumn = column(c.mineEntrance, c.mineDirection, face);
        int[] d = direction(c.mineDirection);
        int inside = mineColumnAt(c, actor.x(), actor.y(), actor.z());
        var mouthBlock = new CodaBlockPos(c.mineEntrance.x() - d[0], c.mineEntrance.y(), c.mineEntrance.z() - d[1]);
        var mouth = FrontierSimulation.standing(world, mouthBlock, 0);
        boolean atMouth = mouth != null && Math.abs(actor.x() - mouth.x() - .5) <= 1.5 && Math.abs(actor.z() - mouth.z() - .5) <= 1.5 && Math.abs(actor.y() - mouth.y()) <= 1.5;
        boolean inReach = actor.distanceSquared(face) <= REACH * REACH;
        if (inside < 0 && !atMouth) {
            if (inReach && faceColumn <= 1) return true;
            s.activity = "Walking to the mine";
            if (mouth == null || !sim.navigate(world, s, mouth, .65)) {
                s.activity = "Path blocked: cannot reach the mouth of the mine at " + text(c.mineEntrance);
                if (autoMine(c) && mouth == null) finishMine(world, c, "has no place to stand at its mouth and is closed");
            }
            return false;
        }
        if (inReach) return true;
        int from = Math.max(inside, -1);
        int last = faceColumn - 1;
        if (last <= from) return true; // already as close as the dug columns allow
        s.activity = "Walking down into the mine";
        // Three columns ahead, else two: never one, which the pathfinder counts as already there.
        for (int to = Math.min(last, from + 3); to >= Math.min(last, from + 2); to--)
            if (world.walk(s.id, mineFeet(c.mineEntrance, c.mineDirection, c.mineShape, to), .65)) return false;
        var next = mineFeet(c.mineEntrance, c.mineDirection, c.mineShape, Math.min(last, from + 2));
        if (!sim.navigate(world, s, next, .65)) s.activity = "Path blocked inside the mine at " + text(next);
        return false;
    }

    /** The mine column a settler stands in, or -1. */
    static int mineColumnAt(Settlement c, double x, double y, double z) {
        for (int i = 0; i < MAX_TUNNEL; i++) {
            var feet = mineFeet(c.mineEntrance, c.mineDirection, c.mineShape, i);
            if (Math.abs(x - feet.x() - .5) <= .9 && Math.abs(z - feet.z() - .5) <= .9 && Math.abs(y - feet.y()) <= 1.2) return i;
        }
        return -1;
    }

    /**
     * For a settler inside the mine heading somewhere outside it: the next place to walk to on
     * the way out (two columns back, then the mouth). Null when not inside or already heading in.
     */
    static CodaBlockPos wayOutOfMine(CodaLivingWorld world, Settlement c, CodaLivingWorld.Actor actor, CodaBlockPos target) throws Exception {
        int at = mineColumnAt(c, actor.x(), actor.y(), actor.z());
        if (at <= 0) return null;
        if (mineColumnAt(c, target.x() + .5, target.y(), target.z() + .5) >= 0) return null;
        if (at >= 2) return mineFeet(c.mineEntrance, c.mineDirection, c.mineShape, at - 2);
        int[] d = direction(c.mineDirection);
        return FrontierSimulation.standing(world, new CodaBlockPos(c.mineEntrance.x() - d[0], c.mineEntrance.y(), c.mineEntrance.z() - d[1]), 0);
    }

    /**
     * Choose the shared mine: a stairway starting on open or steep land inside the territory, away
     * from planned homes and roads, nearest the stockpile; hillsides first, since rock is closer there.
     */
    private boolean chooseMine(CodaLivingWorld world, Settlement c, Settler s) throws Exception {
        var planner = sim.planner;
        if (planner.surveyed(c) < FrontierPlanner.cells(c.territory()).size()) { s.activity = "Looking for a place for the mine: surveying the land"; return false; }
        Set<FrontierPlanner.CellPos> reserved = new HashSet<>(c.roads);
        if (c.project != null) { reserved.add(c.project.cell); reserved.addAll(c.project.road); }
        CodaBlockPos best = null;
        String bestDirection = null;
        double bestScore = Double.MAX_VALUE;
        for (var cell : FrontierPlanner.cells(c.territory())) {
            var survey = planner.survey(cell).orElse(null);
            if (survey == null || reserved.contains(cell)) continue;
            if (survey.role() != FrontierPlanner.Role.OPEN && survey.role() != FrontierPlanner.Role.STEEP) continue;
            int cx = cell.minX() + FrontierPlanner.CELL / 2, cz = cell.minZ() + FrontierPlanner.CELL / 2;
            var entrance = FrontierSimulation.standing(world, new CodaBlockPos(cx, survey.level() + 1, cz), 0);
            if (entrance == null || abandonedMines.contains(entrance)) continue;
            for (String direction : List.of("north", "east", "south", "west")) {
                int[] d = direction(direction);
                boolean room = c.territory().contains(new CodaBlockPos(entrance.x() + d[0] * (STAIR_DEPTH + 8), entrance.y(), entrance.z() + d[1] * (STAIR_DEPTH + 8)))
                        && c.territory().contains(new CodaBlockPos(entrance.x() - d[0], entrance.y(), entrance.z() - d[1]));
                if (!room) continue;
                double score = Math.sqrt(distanceSquared(entrance, c.stockpile)) + (survey.role() == FrontierPlanner.Role.STEEP ? 0 : 12);
                if (score < bestScore) { bestScore = score; best = entrance; bestDirection = direction; }
            }
        }
        if (best == null) { s.activity = "No good place for a mine in your land yet: miners need open or hilly ground away from homes and roads"; return false; }
        c.mineEntrance = best;
        c.mineDirection = bestDirection;
        c.mineShape = "stairs";
        c.mineProgress = 0;
        sim.save();
        world.announce(Chat.good(s.name + " chose a place for the settlement's mine") + Chat.label(" at ") + Chat.value(text(best))
                + Chat.label(": a stairway down, heading " + bestDirection + ". Every miner works there."));
        return true;
    }

    /** Places tried and closed this session, so a closed stairway is not chosen again straight away. */
    private final Set<CodaBlockPos> abandonedMines = new HashSet<>();

    private void finishMine(CodaLivingWorld world, Settlement c, String why) throws Exception {
        var entrance = c.mineEntrance;
        boolean auto = autoMine(c);
        if (auto) {
            abandonedMines.add(entrance);
            c.mineEntrance = null;
            c.mineProgress = 0;
            for (var other : c.settlers.values()) if (other.target != null && other.profession == MINER) other.target = null;
            sim.save();
            world.announce(Chat.warn("The mine at " + text(entrance) + " " + why + ".") + Chat.label(" Miners will choose a new place."));
        } else {
            for (var other : c.settlers.values()) if (other.profession == MINER && other.resident()) other.activity = "The mine " + why + ". Use /frontier mine auto to let miners choose a new place";
        }
    }

    static int[] direction(String facing) {
        return switch (facing) {
            case "east" -> new int[]{1, 0};
            case "west" -> new int[]{-1, 0};
            case "south" -> new int[]{0, 1};
            default -> new int[]{0, -1};
        };
    }

    /** Minecraft yaw: 0 faces south (+z), 90 west, 180 north, 270 east. */
    static String facing(float yaw) {
        float y = ((yaw % 360) + 360) % 360;
        return y < 45 || y >= 315 ? "south" : y < 135 ? "west" : y < 225 ? "north" : "east";
    }

    /** Same measure as the loader: from a settler standing at {@code feet} to the centre of {@code block}. */
    static double distanceSquared(CodaBlockPos feet, CodaBlockPos block) {
        double x = feet.x() - block.x(), y = feet.y() - block.y(), z = feet.z() - block.z();
        return x * x + y * y + z * z;
    }

    private static long distance(CodaBlockPos a, CodaBlockPos b) {
        long x = (long) a.x() - b.x(), y = (long) a.y() - b.y(), z = (long) a.z() - b.z();
        return x * x + y * y + z * z;
    }
}
