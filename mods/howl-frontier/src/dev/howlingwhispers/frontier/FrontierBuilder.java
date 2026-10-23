package dev.howlingwhispers.frontier;

import dev.howlingwhispers.codaloader.api.*;
import java.util.*;
import static dev.howlingwhispers.frontier.FrontierSimulation.add;
import static dev.howlingwhispers.frontier.FrontierSimulation.text;
import static dev.howlingwhispers.frontier.Settlement.Project.Phase.*;

/**
 * Builds what the planner chose, with real items and the loader's native placement (H.O.W.L. 0.0.46+).
 * Order: lay the road (a shovel makes dirt paths), clear the plot of natural ground and plants,
 * then place the blueprint layer by layer from the bottom. Progress is saved after every block,
 * and every step is checked against the world, so a reload or an interruption resumes cleanly.
 * Builders never break or replace anything that is not natural.
 */
final class FrontierBuilder {
    static final String STARTER = "starter_hut";
    static final Set<String> SHOVELS = FrontierJobs.tools("shovel");
    /** The loader places within 6 blocks; stay a little inside that. */
    static final double REACH = 5.5;
    /** Blocks a builder may clear from a plot: ground, stone, sand and plants. Never anything built. */
    static final Set<String> CLEARABLE_GROUND = Set.of("minecraft:grass_block", "minecraft:dirt", "minecraft:coarse_dirt", "minecraft:podzol",
            "minecraft:rooted_dirt", "minecraft:mycelium", "minecraft:moss_block", "minecraft:sand", "minecraft:red_sand", "minecraft:gravel",
            "minecraft:clay", "minecraft:snow", "minecraft:snow_block", "minecraft:mud");

    /** One block of the building, in world space, with the state to place and the item it needs. */
    record Step(CodaBlockPos pos, String role, String state, String item) {}

    private final FrontierSimulation sim;
    private final FrontierJobs jobs;
    /** Runtime cache of the current project's world-space steps. */
    private Settlement.Project cachedFor;
    private List<Step> steps = List.of();
    private Set<CodaBlockPos> footprintVolume = Set.of(), footprint = Set.of();
    /**
     * What each builder collected for the build and has not placed yet. The loader reports only a
     * total, so this ledger is the builder's own record; it is dropped whenever the bag is empty and
     * distrusted after a reload (the bag is then emptied at the stockpile before collecting again).
     */
    private final Map<UUID, Map<String, Integer>> ledger = new HashMap<>();

    FrontierBuilder(FrontierSimulation sim, FrontierJobs jobs) {
        this.sim = sim;
        this.jobs = jobs;
    }

    // ---- Starting a project ---------------------------------------------------------------------

    /**
     * Start the next home when everyone has a bed (so the next settler needs a new one), the
     * survey is complete and the planner has a plot. Called once a second.
     */
    /** Tests of other systems switch automatic building off. */
    boolean autoStart = true;

    void maybeStart(CodaLivingWorld world, Settlement c) throws Exception {
        if (c.project != null || !autoStart) return;
        if (c.population() == 0 || !c.freeBeds().isEmpty()) return;
        var planner = sim.planner;
        if (planner.surveyed(c) < FrontierPlanner.cells(c.territory()).size()) return;
        var plot = planner.nextPlot(c);
        if (plot == null) return;
        var palette = planner.palette();
        c.project = new Settlement.Project(STARTER, plot.cell(), plot.doorFacing(), plot.level(), palette, plot.road());
        c.project.phase = plot.road().isEmpty() ? CLEAR : ROAD;
        sim.save();
        world.announce(Chat.good("A new home is planned") + Chat.label(": " + ("aeiou".indexOf(palette.name().charAt(0)) >= 0 ? "an " : "a ") + palette.name() + " at ") + Chat.value(plot.cell().minX() + " " + plot.cell().minZ())
                + Chat.label(plot.road().isEmpty() ? "." : ", with " + plot.road().size() + " cell" + (plot.road().size() == 1 ? "" : "s") + " of road first."));
    }

    // ---- Geometry ---------------------------------------------------------------------------------

    /** Blueprint (front = south) to world, rotated so the door faces the road. */
    static int[] rotate(int x, int z, int w, int d, String facing) {
        return switch (facing) {
            case "north" -> new int[]{w - 1 - x, d - 1 - z};
            case "east" -> new int[]{z, w - 1 - x};
            case "west" -> new int[]{d - 1 - z, x};
            default -> new int[]{x, z};
        };
    }

    static String rotateDirection(String dir, String facing) {
        List<String> order = List.of("south", "west", "north", "east"); // clockwise seen from above
        int turns = switch (facing) { case "west" -> 1; case "north" -> 2; case "east" -> 3; default -> 0; };
        int i = order.indexOf(dir);
        return i < 0 ? dir : order.get((i + turns) % 4);
    }

    static String rotateProperties(String properties, String facing) {
        if (properties.isEmpty()) return properties;
        StringJoiner out = new StringJoiner(",");
        for (String pair : properties.split(",")) {
            String[] kv = pair.split("=", 2);
            out.add(kv[0].equals("facing") ? "facing=" + rotateDirection(kv[1], facing) : pair);
        }
        return out.toString();
    }

    /** World position of the footprint's north-west corner: 1 block from the cell edge on the road side. */
    static int[] origin(Settlement.Project p, int fw, int fd) {
        int x0 = p.cell.minX(), z0 = p.cell.minZ(), cx = x0 + (FrontierPlanner.CELL - fw) / 2, cz = z0 + (FrontierPlanner.CELL - fd) / 2;
        return switch (p.facing) {
            case "north" -> new int[]{cx, z0 + 1};
            case "south" -> new int[]{cx, z0 + FrontierPlanner.CELL - 1 - fd};
            case "east" -> new int[]{x0 + FrontierPlanner.CELL - 1 - fw, cz};
            default -> new int[]{x0 + 1, cz};
        };
    }

    private void prepare(Settlement.Project p) throws Exception {
        if (cachedFor == p) return;
        var bp = Blueprint.resource(p.blueprint);
        boolean turned = p.facing.equals("east") || p.facing.equals("west");
        int fw = turned ? bp.depth : bp.width, fd = turned ? bp.width : bp.depth;
        int[] o = origin(p, fw, fd);
        List<Step> list = new ArrayList<>();
        Set<CodaBlockPos> volume = new HashSet<>(), base = new HashSet<>();
        for (var cell : bp.cells) {
            int[] r = rotate(cell.x(), cell.z(), bp.width, bp.depth, p.facing);
            var pos = new CodaBlockPos(o[0] + r[0], p.level + 1 + cell.y(), o[1] + r[1]);
            volume.add(pos);
            base.add(new CodaBlockPos(pos.x(), p.level + 1, pos.z()));
            if (!cell.solid() || cell.secondHalf()) continue;
            String state = p.palette.resolve(cell.role(), rotateProperties(cell.properties(), p.facing));
            int bracket = state.indexOf('[');
            list.add(new Step(pos, cell.role(), state, bracket < 0 ? state : state.substring(0, bracket)));
        }
        steps = List.copyOf(list);
        footprintVolume = Set.copyOf(volume);
        footprint = Set.copyOf(base);
        cachedFor = p;
    }

    List<Step> steps(Settlement.Project p) throws Exception {
        prepare(p);
        return steps;
    }

    /** Road blocks for a new road cell: a 2-wide strip from the cell's centre to each connected side. */
    static List<int[]> roadColumns(Settlement c, FrontierPlanner.CellPos cell) {
        Set<FrontierPlanner.CellPos> connects = new HashSet<>(c.roads);
        if (c.project != null) { connects.addAll(c.project.road); connects.add(c.project.cell); }
        List<int[]> cols = new ArrayList<>();
        int x0 = cell.minX(), z0 = cell.minZ();
        for (int dx = 3; dx <= 4; dx++) for (int dz = 3; dz <= 4; dz++) cols.add(new int[]{x0 + dx, z0 + dz});
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] d : dirs) {
            var n = cell.step(d[0], d[1]);
            if (!connects.contains(n) && !FrontierPlanner.square(c, n)) continue;
            for (int i = 0; i < 4; i++) for (int w = 3; w <= 4; w++) {
                int along = d[0] + d[1] > 0 ? 5 + i : 2 - i; // from the centre to the edge on that side
                cols.add(d[0] != 0 ? new int[]{x0 + along, z0 + w} : new int[]{x0 + w, z0 + along});
            }
        }
        return cols;
    }

    // ---- Daylight work ------------------------------------------------------------------------------

    /** One decision for the builder. Returns false when there is nothing to build. */
    boolean work(CodaLivingWorld world, CodaWorldView view, Settlement c, Settler s, CodaLivingWorld.Actor actor) throws Exception {
        var p = c.project;
        if (p == null) return false;
        prepare(p);
        switch (p.phase) {
            case ROAD -> road(world, view, c, s, actor, p);
            case CLEAR -> clear(world, view, c, s, actor, p);
            case BUILD -> build(world, view, c, s, actor, p);
        }
        return true;
    }

    private void road(CodaLivingWorld world, CodaWorldView view, Settlement c, Settler s, CodaLivingWorld.Actor actor, Settlement.Project p) throws Exception {
        for (var cell : p.road) {
            for (int[] col : roadColumns(c, cell)) {
                var ground = groundAt(world, col[0], col[1], p.level);
                if (ground == null) continue;
                var top = world.block(ground).orElseThrow();
                if (top.id().equals("minecraft:dirt_path") || !Set.of("minecraft:grass_block", "minecraft:dirt", "minecraft:coarse_dirt",
                        "minecraft:podzol", "minecraft:mycelium", "minecraft:rooted_dirt").contains(top.id())) continue;
                var above = add(ground, 0, 1, 0);
                var plant = world.block(above).orElseThrow();
                if (FrontierJobs.isLog(plant.id())) {
                    fellForRoad(world, view, c, s, actor, above);
                    return;
                }
                if (!world.equip(s.id, c.stockpile, SHOVELS)) {
                    if (!hasInStockpile(view, c, SHOVELS)) { s.activity = "Laying a road: needs a shovel in the stockpile"; return; }
                    s.activity = "Fetching a shovel for the road";
                    sim.moveNear(world, s, c.stockpile);
                    return;
                }
                if (actor.distanceSquared(ground) > REACH * REACH) {
                    s.activity = "Walking to the road";
                    sim.moveNear(world, s, above);
                    return;
                }
                if (!plant.air()) {
                    if (plant.solid() || !world.harvest(s.id, above, plant.id(), c.stockpile, Set.of())) { s.activity = "Laying a road: something is in the way at " + text(above); return; }
                    return;
                }
                s.activity = world.shapePath(s.id, ground) ? "Laying a road" : "Laying a road: could not shape the path at " + text(ground);
                return;
            }
        }
        c.roads.addAll(p.road);
        p.phase = CLEAR;
        sim.save();
        s.activity = "The road is done";
    }

    /** A natural tree standing on the road's line is felled from the top, with an axe; its logs go to the stockpile. */
    private void fellForRoad(CodaLivingWorld world, CodaWorldView view, Settlement c, Settler s, CodaLivingWorld.Actor actor, CodaBlockPos base) throws Exception {
        int height = FrontierJobs.trunk(world, base);
        if (!felling.contains(base) && !FrontierJobs.naturalCanopy(world, base, height)) { s.activity = "Laying a road: logs that are not a natural tree are in the way at " + text(base); return; }
        felling.add(base);
        if (world.carried(s.id) >= FrontierJobs.CARRY_LIMIT) { s.delivering = true; return; }
        if (!world.equip(s.id, c.stockpile, FrontierJobs.AXES)) {
            if (!hasInStockpile(view, c, FrontierJobs.AXES)) { s.activity = "Laying a road: a tree is in the way and the stockpile has no axe"; return; }
            s.activity = "Fetching an axe to clear the road";
            sim.moveNear(world, s, c.stockpile);
            return;
        }
        var top = add(base, 0, height - 1, 0);
        if (actor.distanceSquared(top) > REACH * REACH) {
            s.activity = "Walking to a tree on the road";
            sim.moveNear(world, s, base);
            return;
        }
        s.activity = world.harvest(s.id, top, world.block(top).orElseThrow().id(), c.stockpile, FrontierJobs.AXES)
                ? "Felling a tree on the road" : "Laying a road: could not fell the tree at " + text(base);
    }

    /** Natural ground, plants, natural trees: a builder may clear these. Anything built never. */
    static boolean clearable(CodaLivingWorld.Block block) {
        if (block.fluid() || FrontierPlanner.made(block.id())) return false;
        if (block.id().endsWith("_leaves")) return !"true".equals(block.properties().get("persistent"));
        boolean plant = !block.solid();
        return plant || CLEARABLE_GROUND.contains(block.id()) || FrontierJobs.isLog(block.id());
    }

    /** Trees whose felling has started: their canopy may decay before the last log is cut. Runtime only. */
    private final Set<CodaBlockPos> felling = new HashSet<>();

    /**
     * A natural tree on the building site is felled whole with an axe, from the highest log in
     * reach downwards, so no trunk is left floating over the new home. Its logs go to the stockpile
     * and count towards a log cabin.
     */
    private void fellOnSite(CodaLivingWorld world, CodaWorldView view, Settlement c, Settler s, CodaLivingWorld.Actor actor, CodaBlockPos log) throws Exception {
        var base = log;
        String id = world.block(log).orElseThrow().id();
        while (base.y() > log.y() - 32 && world.block(add(base, 0, -1, 0)).map(b -> b.id().equals(id)).orElse(false)) base = add(base, 0, -1, 0);
        int height = FrontierJobs.trunk(world, base);
        if (!felling.contains(base) && !FrontierJobs.naturalCanopy(world, base, height)) {
            s.activity = "Building paused: " + id.replace("minecraft:", "") + " at " + text(log) + " is not part of a natural tree and cannot be cleared";
            return;
        }
        if (world.carried(s.id) >= FrontierJobs.CARRY_LIMIT) { s.delivering = true; return; }
        if (!world.equip(s.id, c.stockpile, FrontierJobs.AXES)) {
            if (!hasInStockpile(view, c, FrontierJobs.AXES)) { s.activity = "Clearing the building site: a tree is in the way. Put an axe in the stockpile"; return; }
            s.activity = "Fetching an axe to clear the building site";
            sim.moveNear(world, s, c.stockpile);
            return;
        }
        felling.add(base);
        var top = add(base, 0, height - 1, 0);
        double beside = Math.pow(actor.x() - base.x() - .5, 2) + Math.pow(actor.z() - base.z() - .5, 2);
        if (actor.distanceSquared(top) > REACH * REACH && beside > 1.6 * 1.6) {
            // Stand right beside the trunk, so the top can be cut first and nothing is left hanging.
            for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {-1, -1}, {1, -1}, {-1, 1}}) {
                var spot = FrontierSimulation.standing(world, add(base, d[0], 0, d[1]), 0);
                if (spot != null && !spot.equals(base) && sim.navigate(world, s, spot, .65)) { s.activity = "Walking to a tree on the building site"; return; }
            }
        }
        for (int h = height - 1; h >= 0; h--) {
            var target = add(base, 0, h, 0);
            if (actor.distanceSquared(target) > REACH * REACH) continue;
            if (world.harvest(s.id, target, id, c.stockpile, FrontierJobs.AXES)) s.activity = "Felling a tree on the building site";
            else if (world.carried(s.id) > 0) s.delivering = true;
            else s.activity = "Clearing the building site: could not fell the tree at " + text(base);
            return;
        }
        if (near(world, s, actor, base, "Clearing the building site")) s.activity = "Clearing the building site: the tree at " + text(base) + " is too tall to reach";
    }

    /**
     * Clearing follows a fixed order, shown in the builder's activity:
     * 1. trees: every natural tree with a log on the site or leaves reaching over it is felled whole,
     *    with an axe from the stockpile, nearest first (the axe is fetched before anything else);
     * 2. leaves and plants still on the site, top down, by hand;
     * 3. ground (grass, dirt, sand...) inside the walls, top down, by hand.
     */
    private void clear(CodaLivingWorld world, CodaWorldView view, Settlement c, Settler s, CodaLivingWorld.Actor actor, Settlement.Project p) throws Exception {
        if (world.carried(s.id) >= FrontierJobs.CARRY_LIMIT) { s.delivering = true; return; }
        var trees = treesOverSite(world, c);
        if (!trees.isEmpty()) {
            trees.sort(Comparator.comparingDouble(actor::distanceSquared));
            fellOnSite(world, view, c, s, actor, trees.getFirst());
            s.activity = "Clearing (1/3, " + trees.size() + " tree" + (trees.size() == 1 ? "" : "s") + " left): " + s.activity;
            return;
        }
        for (int stage = 2; stage <= 3; stage++)
            for (var pos : sorted(footprintVolume)) {
                var b = world.block(pos);
                if (b.isEmpty()) { s.activity = "Waiting: the building site is not loaded"; return; }
                var block = b.get();
                if (block.air()) continue;
                boolean soft = !block.solid() || block.id().endsWith("_leaves");
                if (stage == 2 && !soft) continue;
                String label = stage == 2 ? "Clearing (2/3): leaves and plants" : "Clearing (3/3): levelling the ground";
                if (!clearable(block)) {
                    s.activity = "Building paused: " + block.id().replace("minecraft:", "") + " at " + text(pos) + " is in the way and cannot be cleared";
                    return;
                }
                if (!near(world, s, actor, pos, label)) return;
                if (!world.harvest(s.id, pos, block.id(), c.stockpile, Set.of())) {
                    if (world.carried(s.id) > 0) s.delivering = true;
                    else s.activity = "Building paused: cannot clear " + block.id().replace("minecraft:", "") + " by hand at " + text(pos);
                    return;
                }
                s.activity = label;
                return;
            }
        p.phase = BUILD;
        sim.save();
    }

    /**
     * One log of each natural tree that stands on the site or reaches over it with its leaves.
     * Only trees inside the land; logs that are not part of a natural tree are left for the
     * normal checks to report.
     */
    List<CodaBlockPos> treesOverSite(CodaLivingWorld world, Settlement c) throws Exception {
        Map<CodaBlockPos, CodaBlockPos> bases = new LinkedHashMap<>();
        for (var pos : footprintVolume) {
            var b = world.block(pos);
            if (b.isEmpty()) continue;
            if (FrontierJobs.isLog(b.get().id())) { addTree(world, c, pos, bases, true); continue; }
            if (!b.get().id().endsWith("_leaves") || "true".equals(b.get().properties().get("persistent"))) continue;
            // Leaves over the site: find the trunk they grow from, up to 4 blocks away.
            for (int dx = -4; dx <= 4; dx++)
                for (int dz = -4; dz <= 4; dz++)
                    for (int dy = -6; dy <= 0; dy++) {
                        var q = add(pos, dx, dy, dz);
                        var l = world.block(q);
                        if (l.isPresent() && FrontierJobs.isLog(l.get().id())) addTree(world, c, q, bases, false);
                    }
        }
        return new ArrayList<>(bases.values());
    }

    private void addTree(CodaLivingWorld world, Settlement c, CodaBlockPos log, Map<CodaBlockPos, CodaBlockPos> bases, boolean onSite) throws Exception {
        String id = world.block(log).orElseThrow().id();
        var base = log;
        while (base.y() > log.y() - 32 && world.block(add(base, 0, -1, 0)).map(b -> b.id().equals(id)).orElse(false)) base = add(base, 0, -1, 0);
        if (bases.containsKey(base)) return;
        var below = world.block(add(base, 0, -1, 0));
        boolean grounded = below.isPresent() && FrontierJobs.GROUND_FOR_TREES.contains(below.get().id());
        if (!c.territory().contains(base)) return;
        if (!onSite && (!grounded || !(felling.contains(base) || FrontierJobs.naturalCanopy(world, base, FrontierJobs.trunk(world, base))))) return;
        bases.put(base, log);
    }

    private void build(CodaLivingWorld world, CodaWorldView view, Settlement c, Settler s, CodaLivingWorld.Actor actor, Settlement.Project p) throws Exception {
        while (p.step < steps.size() && placed(world, steps.get(p.step))) p.step++;
        if (p.step >= steps.size()) { finish(world, c, s, p); return; }
        var step = steps.get(p.step);
        // Something may have appeared on the site since it was cleared: never remove anything built.
        var there = world.block(step.pos());
        if (there.isEmpty()) { s.activity = "Waiting: the building site is not loaded"; return; }
        if (!there.get().air() && (there.get().solid() || there.get().fluid())) {
            if (!clearable(there.get())) {
                s.activity = "Building paused: " + there.get().id().replace("minecraft:", "") + " at " + text(step.pos()) + " is in the way and cannot be cleared";
                return;
            }
            if (!near(world, s, actor, step.pos(), "Clearing the building site")) return;
            if (!world.harvest(s.id, step.pos(), there.get().id(), c.stockpile, Set.of()) && world.carried(s.id) > 0) s.delivering = true;
            return;
        }
        String item = itemFor(world, view, c, s, step);
        if (item == null) {
            s.activity = "Building: needs " + describe(step) + " in the stockpile";
            gatherIfPossible(world, view, c, s, actor, step);
            return;
        }
        if (!carries(world, s, item)) {
            fetch(world, view, c, s, actor, p, item);
            return;
        }
        if (!near(world, s, actor, step.pos(), "Building the " + c.project.palette.name())) return;
        String state = item.equals(step.item()) ? step.state() : item + step.state().substring(step.item().length());
        if (world.place(s.id, step.pos(), state, c.stockpile)) {
            ledger.getOrDefault(s.id, new HashMap<>()).computeIfPresent(item, (k, n) -> n > 1 ? n - 1 : null);
            p.step++;
            sim.save();
            s.activity = "Building: " + p.step + "/" + steps.size() + " blocks";
        } else {
            ledger.remove(s.id); // the bag may not hold what the ledger says: re-collect next time
            s.activity = "Building paused: could not place " + describe(step) + " at " + text(step.pos());
        }
    }

    private void finish(CodaLivingWorld world, Settlement c, Settler s, Settlement.Project p) throws Exception {
        for (var step : steps) {
            if (!step.role().equals("bed")) continue;
            var head = FrontierSimulation.bedHead(world, step.pos());
            if (head != null && FrontierSimulation.shelteredBed(world, head) && c.beds.size() < Settlement.MAX_BEDS) c.beds.add(head);
        }
        c.roads.addAll(p.road);
        c.homesBuilt++;
        c.project = null;
        cachedFor = null;
        sim.save();
        s.activity = "Finished a home";
        world.announce(Chat.heading("A new home is finished!") + Chat.label(" " + s.name + " built a " + p.palette.name() + ". Its bed is ready for a settler."));
    }

    // ---- Helpers ----------------------------------------------------------------------------------

    private static List<CodaBlockPos> sorted(Collection<CodaBlockPos> positions) {
        List<CodaBlockPos> list = new ArrayList<>(positions);
        // Top down, so nothing is left floating while clearing.
        list.sort(Comparator.comparingInt(CodaBlockPos::y).reversed().thenComparingInt(CodaBlockPos::z).thenComparingInt(CodaBlockPos::x));
        return list;
    }

    private boolean placed(CodaLivingWorld world, Step step) throws Exception {
        var b = world.block(step.pos());
        if (b.isEmpty()) return false;
        String id = b.get().id();
        if (step.role().equals("door")) return id.endsWith("_door");
        if (step.role().equals("bed")) return id.endsWith("_bed");
        return id.equals(step.item());
    }

    /** The real item for a step. Doors and beds may be any colour or wood the player put in the stockpile. */
    private String itemFor(CodaLivingWorld world, CodaWorldView view, Settlement c, Settler s, Step step) throws Exception {
        if (!step.role().equals("door") && !step.role().equals("bed")) return carries(world, s, step.item()) || stocked(view, c, step.item()) > 0 ? step.item() : null;
        String suffix = step.role().equals("door") ? "_door" : "_bed";
        // One already carried comes first, then whatever the player put in the stockpile.
        if (world.carried(s.id) > 0)
            for (var id : ledger.getOrDefault(s.id, Map.of()).keySet()) if (id.endsWith(suffix) && !id.equals("minecraft:iron_door")) return id;
        for (var id : stockpileItems(view, c).keySet()) if (id.endsWith(suffix) && !id.equals("minecraft:iron_door")) return id;
        return null;
    }

    private boolean carries(CodaLivingWorld world, Settler s, String item) throws Exception {
        if (world.carried(s.id) == 0) { ledger.remove(s.id); return false; }
        return ledger.getOrDefault(s.id, Map.of()).getOrDefault(item, 0) > 0;
    }

    private boolean matches(Step step, String item) {
        if (step.role().equals("door")) return item.endsWith("_door");
        if (step.role().equals("bed")) return item.endsWith("_bed");
        return step.item().equals(item);
    }

    private void fetch(CodaLivingWorld world, CodaWorldView view, Settlement c, Settler s, CodaLivingWorld.Actor actor, Settlement.Project p, String item) throws Exception {
        if (actor.distanceSquared(c.stockpile) > 9) {
            s.activity = "Fetching " + item.replace("minecraft:", "").replace('_', ' ') + " from the stockpile";
            sim.moveNear(world, s, c.stockpile);
            return;
        }
        // Empty the bag first: whatever is in it (cleared dirt, an unknown load after a reload) goes to the stockpile.
        if (world.carried(s.id) > 0 && world.deliver(s.id, c.stockpile) == 0) { s.activity = "The stockpile is full: make room in it"; return; }
        ledger.remove(s.id);
        int need = 0;
        for (int i = p.step; i < steps.size(); i++) if (matches(steps.get(i), item)) need++;
        int got = world.collect(s.id, c.stockpile, CodaItemFilter.only(item), Math.max(1, Math.min(need, 64 * 8)));
        if (got > 0) ledger.computeIfAbsent(s.id, k -> new HashMap<>()).merge(item, got, Integer::sum);
        s.activity = got > 0 ? "Carrying " + got + " " + item.replace("minecraft:", "").replace('_', ' ') + " to the site" : "The stockpile has no " + item;
    }

    /** With no logs left, a builder who can get an axe fells trees for the cabin. */
    private void gatherIfPossible(CodaLivingWorld world, CodaWorldView view, Settlement c, Settler s, CodaLivingWorld.Actor actor, Step step) throws Exception {
        if (!FrontierJobs.isLog(step.item())) return;
        // equip() succeeds when an axe is already in hand, or in reach at the stockpile.
        if (!hasInStockpile(view, c, FrontierJobs.AXES) && !world.equip(s.id, c.stockpile, FrontierJobs.AXES)) return;
        jobs.lumberjackFor(world, c, s, actor);
        if (s.profession != Settler.Profession.BUILDER && c.project != null) s.profession = Settler.Profession.BUILDER;
        if (!s.activity.isEmpty()) s.activity = "Gathering logs for the " + c.project.palette.name() + ": " + s.activity.toLowerCase(Locale.ROOT);
    }

    private boolean near(CodaLivingWorld world, Settler s, CodaLivingWorld.Actor actor, CodaBlockPos target, String doing) throws Exception {
        if (actor.distanceSquared(target) <= REACH * REACH) return true;
        CodaBlockPos stand = standingOutside(world, target);
        s.activity = doing + ": walking over";
        if (stand == null || !sim.navigate(world, s, stand, .65)) s.activity = doing + ": cannot reach " + text(target);
        return false;
    }

    /** A standing spot just outside the footprint, within reach of the target. */
    private CodaBlockPos standingOutside(CodaLivingWorld world, CodaBlockPos target) throws Exception {
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE, y = Integer.MAX_VALUE;
        for (var b : footprint) { minX = Math.min(minX, b.x()); maxX = Math.max(maxX, b.x()); minZ = Math.min(minZ, b.z()); maxZ = Math.max(maxZ, b.z()); y = Math.min(y, b.y()); }
        CodaBlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int ring = 1; ring <= 4; ring++)
            for (int x = minX - ring; x <= maxX + ring; x++)
                for (int z = minZ - ring; z <= maxZ + ring; z++) {
                    if (x > minX - ring && x < maxX + ring && z > minZ - ring && z < maxZ + ring) continue;
                    var spot = FrontierSimulation.standing(world, new CodaBlockPos(x, y, z), 0);
                    if (spot == null) continue;
                    double d = FrontierJobs.distanceSquared(spot, target);
                    if (d <= (REACH - .7) * (REACH - .7) && d < bestDistance) { best = spot; bestDistance = d; }
                }
        return best;
    }

    private CodaBlockPos groundAt(CodaLivingWorld world, int x, int z, int level) throws Exception {
        for (int y = level + 3; y >= level - 3; y--) {
            var b = world.block(new CodaBlockPos(x, y, z));
            if (b.isEmpty()) return null;
            String id = b.get().id();
            // Tree trunks and leaves stand on the ground; they are not the ground.
            if (b.get().solid() && !FrontierJobs.isLog(id) && !id.endsWith("_leaves")) return new CodaBlockPos(x, y, z);
        }
        return null;
    }

    static Map<String, Integer> stockpileItems(CodaWorldView view, Settlement c) throws Exception {
        Map<String, Integer> items = new HashMap<>();
        if (c.stockpile == null) return items;
        view.itemInventory(c.dimension, c.stockpile).ifPresent(inv -> inv.slots().forEach(slot -> { if (slot.count() > 0) items.merge(slot.itemId(), slot.count(), Integer::sum); }));
        return items;
    }

    private static int stocked(CodaWorldView view, Settlement c, String item) throws Exception { return stockpileItems(view, c).getOrDefault(item, 0); }

    private static boolean hasInStockpile(CodaWorldView view, Settlement c, Set<String> any) throws Exception {
        return !Collections.disjoint(stockpileItems(view, c).keySet(), any);
    }

    static String describe(Step step) {
        return switch (step.role()) {
            case "door" -> "a door";
            case "bed" -> "a bed";
            default -> step.item().replace("minecraft:", "").replace('_', ' ');
        };
    }

    /** Materials still needed for the current project, by item (doors and beds by kind). */
    Map<String, Integer> remaining(CodaLivingWorld world, Settlement.Project p) throws Exception {
        prepare(p);
        Map<String, Integer> need = new TreeMap<>();
        for (int i = p.step; i < steps.size(); i++) {
            var step = steps.get(i);
            if (placed(world, step)) continue;
            need.merge(step.role().equals("door") ? "door" : step.role().equals("bed") ? "bed" : step.item().replace("minecraft:", "").replace('_', ' '), 1, Integer::sum);
        }
        return need;
    }

    void clear() {
        cachedFor = null;
        steps = List.of();
        ledger.clear();
    }
}
