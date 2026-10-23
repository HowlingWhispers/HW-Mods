package dev.howlingwhispers.frontier;

import dev.howlingwhispers.codaloader.api.*;
import java.util.*;
import static dev.howlingwhispers.frontier.Fixtures.*;
import static dev.howlingwhispers.frontier.Settler.Profession.*;
import static dev.howlingwhispers.frontier.Settler.State.*;

/**
 * Jobs in a fake world whose items follow CodaLoader's rules: 3-block reach to the stockpile,
 * 6-block reach to break, tool in hand, an 8-slot bag, real seeds and saplings to plant.
 */
public final class JobsTest {
    static final CodaBlockPos STOCK = new CodaBlockPos(1, 64, 3);

    public static void main(String[] args) throws Exception {
        lumberjackPicksAxeCutsAndReplants();
        logHousesAndTallTreesAreSafe();
        treesOutsideTheLandAreNotCut();
        farmerHarvestsReplantsAndDelivers();
        minerDigsTunnelAndStopsSafely();
        minersShareOneStairwayTheyChoseThemselves();
        playerMineIsReachedThroughItsMouth();
        unsafeStairwayIsClosedAndAnotherChosen();
        minerKeepsDiggingWithTheRealPathfinder();
        fullStockpileIsReported();
        noToolsMeansNoJob();
        olderSavesStillLoad();
        finish("job checks");
    }

    /** A settlement with a stockpile and one resident labourer standing by the campfire. */
    static Settler camp(Camp t) throws Exception {
        t.found();
        t.autoBuild = false; // these tests are about jobs; BuilderTest covers building
        t.sim.builder.autoStart = false;
        t.c().pioneerCommitted = true;
        t.world.blocks.put(STOCK, block("minecraft:chest", Map.of("type", "single"), true));
        t.c().stockpile = STOCK;
        var s = t.add("Worker", RESIDENT, null, true);
        t.c().recordResidents();
        t.world.put(s.id, new CodaBlockPos(2, 64, 2), 20);
        return s;
    }

    static void until(Camp t, int seconds, java.util.function.BooleanSupplier done) {
        for (int i = 0; i < seconds && !done.getAsBoolean(); i++) t.seconds(1);
    }

    static void lumberjackPicksAxeCutsAndReplants() throws Exception {
        var t = new Camp();
        var s = camp(t);
        t.world.stock(STOCK, "minecraft:stone_axe", 1);
        t.world.stock(STOCK, "minecraft:oak_sapling", 2);
        var base = new CodaBlockPos(12, 64, 8);
        t.world.tree(base, 5, "minecraft:oak", true);
        until(t, 200, () -> t.world.stocked(STOCK, "minecraft:oak_log") == 5);
        check(s.profession == LUMBERJACK && !s.jobChosen, "A labourer picks the lumberjack job from an axe in the stockpile");
        check(t.world.announcements.stream().anyMatch(a -> a.contains("found an axe")), "Players are told who took which job");
        check("minecraft:stone_axe".equals(t.world.hands.get(s.id)) && t.world.stocked(STOCK, "minecraft:stone_axe") == 0, "The axe moved from the stockpile into the settler's hand");
        check(t.world.stocked(STOCK, "minecraft:oak_log") == 5, "All five logs reach the stockpile");
        check(t.world.block(base).orElseThrow().id().equals("minecraft:oak_sapling"), "A real sapling is planted where the tree stood");
        check(t.world.stocked(STOCK, "minecraft:oak_sapling") == 1, "The sapling came from the stockpile");
        check(t.world.harvested.indexOf("minecraft:oak_log") == 0 && t.world.harvested.stream().allMatch(h -> h.equals("minecraft:oak_log")), "Only logs were cut");
    }

    static void logHousesAndTallTreesAreSafe() throws Exception {
        var t = new Camp();
        var s = camp(t);
        t.world.stock(STOCK, "minecraft:stone_axe", 1);
        var wall = new CodaBlockPos(10, 64, 10);
        for (int y = 0; y < 3; y++) t.world.blocks.put(new CodaBlockPos(wall.x(), 64 + y, wall.z()), block("minecraft:spruce_log", Map.of("axis", "y"), true));
        t.world.tree(new CodaBlockPos(14, 64, -6), 4, "minecraft:birch", false); // player-placed (persistent) leaves
        t.world.tree(new CodaBlockPos(-8, 64, 12), 9, "minecraft:spruce", true);  // too tall to reach the top
        t.seconds(200);
        check(t.world.harvested.isEmpty(), "Log walls, trees with player-placed leaves and too-tall trees are never cut");
        check(s.profession == LUMBERJACK && s.activity.contains("No trees to cut"), "The lumberjack explains there is nothing to cut: " + s.activity);
    }

    static void treesOutsideTheLandAreNotCut() throws Exception {
        var t = new Camp();
        camp(t);
        t.world.stock(STOCK, "minecraft:stone_axe", 1);
        t.world.tree(new CodaBlockPos(40, 64, 0), 4, "minecraft:oak", true); // chunk 2: outside a 3x3 territory
        t.seconds(200);
        check(t.world.harvested.isEmpty(), "Trees outside the territory are never cut");
    }

    static void farmerHarvestsReplantsAndDelivers() throws Exception {
        var t = new Camp();
        var s = camp(t);
        t.world.stock(STOCK, "minecraft:iron_hoe", 1);
        t.world.stock(STOCK, "minecraft:oak_sapling", 1);
        var field = List.of(new CodaBlockPos(6, 64, -6), new CodaBlockPos(7, 64, -6), new CodaBlockPos(8, 64, -6));
        for (var p : field) t.world.crop(p, "minecraft:wheat", "7");
        var green = new CodaBlockPos(9, 64, -6);
        t.world.crop(green, "minecraft:carrots", "2");
        until(t, 200, () -> t.world.stocked(STOCK, "minecraft:wheat") == 3 && t.world.carried(s.id) == 0);
        check(s.profession == FARMER, "With a hoe in the stockpile and crops planted, a labourer becomes a farmer");
        check(t.world.stocked(STOCK, "minecraft:wheat") == 3, "Three ripe wheat were harvested and delivered");
        for (var p : field) check(t.world.block(p).orElseThrow().id().equals("minecraft:wheat") && "0".equals(t.world.block(p).orElseThrow().properties().get("age")), "Wheat was replanted with real seeds at " + p);
        check(t.world.stocked(STOCK, "minecraft:wheat_seeds") == 3, "Spare seeds went to the stockpile (6 dropped, 3 replanted)");
        check(t.world.block(green).orElseThrow().properties().get("age").equals("2"), "Unripe carrots are left to grow");
    }

    static void minerDigsTunnelAndStopsSafely() throws Exception {
        var t = new Camp();
        var s = camp(t);
        t.world.stock(STOCK, "minecraft:stone_pickaxe", 1);
        // A stone hill east of camp: the tunnel starts at x=6, feet at y=64, heading east.
        for (int x = 6; x <= 20; x++) for (int y = 64; y <= 66; y++) for (int z = -1; z <= 1; z++)
            t.world.blocks.put(new CodaBlockPos(x, y, z), block("minecraft:stone", Map.of(), true));
        t.world.blocks.put(new CodaBlockPos(10, 66, 0), block("minecraft:gravel", Map.of(), true)); // above the 5th step's head block
        t.c().mineEntrance = new CodaBlockPos(6, 64, 0);
        t.c().mineDirection = "east";
        until(t, 300, () -> s.activity.contains("Mining paused"));
        check(s.profession == MINER, "With a pickaxe and a mine set, a labourer becomes a miner");
        check(t.c().mineProgress == 8, "Four steps of tunnel were dug before reaching gravel (progress " + t.c().mineProgress + ")");
        check(t.world.block(new CodaBlockPos(9, 65, 0)).orElseThrow().air() && t.world.block(new CodaBlockPos(9, 64, 0)).orElseThrow().air(), "The tunnel is two blocks high");
        check(!t.world.block(new CodaBlockPos(10, 65, 0)).orElseThrow().air(), "Mining stops before the block under the gravel");
        until(t, 60, () -> t.world.carried(s.id) == 0 && t.world.stocked(STOCK, "minecraft:cobblestone") > 0);
        check(t.world.stocked(STOCK, "minecraft:cobblestone") + t.world.carried(s.id) == 8, "Every mined block produced real cobblestone");
        t.reopen();
        check(t.c().mineProgress == 8 && t.c().mineEntrance != null && t.c().mineDirection.equals("east"), "Mine progress survives reopening the world");
    }

    static void fullStockpileIsReported() throws Exception {
        var t = new Camp();
        var s = camp(t);
        t.world.stock(STOCK, "minecraft:stone_axe", 1);
        t.world.tree(new CodaBlockPos(12, 64, 8), 3, "minecraft:oak", true);
        t.world.chestFull = true;
        until(t, 200, () -> s.activity.contains("stockpile is full"));
        check(s.activity.contains("stockpile is full") && t.world.carried(s.id) == 3, "A full stockpile is reported and the logs are kept, not lost");
    }

    static void noToolsMeansNoJob() throws Exception {
        var t = new Camp();
        var s = camp(t);
        t.world.tree(new CodaBlockPos(12, 64, 8), 4, "minecraft:oak", true);
        t.seconds(150);
        check(s.profession == LABOURER && t.world.harvested.isEmpty(), "Without tools nobody works: no free resources");
        check(s.activity.contains("Put an axe"), "The labourer says which tools would give them a job");
    }

    static void olderSavesStillLoad() throws Exception {
        var state = StoreTest.sample();
        Properties p = FrontierStore.encode(state);
        for (var key : new ArrayList<>(p.stringPropertyNames()))
            if (key.endsWith(".mineEntrance") || key.endsWith(".mineDirection") || key.endsWith(".mineProgress") || key.endsWith(".jobChosen")) p.remove(key);
        var back = FrontierStore.decode(p);
        check(back.primary().mineEntrance == null && back.primary().mineProgress == 0, "A 0.2.2 world without job fields still loads");
    }

    /** Every miner, not one each: with pickaxes and no mine set, miners choose one stairway and take turns at its face. */
    static void minersShareOneStairwayTheyChoseThemselves() throws Exception {
        var t = new Camp();
        var a = camp(t);
        var b = t.add("Second", RESIDENT, null, true);
        t.c().recordResidents();
        t.world.put(b.id, new CodaBlockPos(3, 64, 2), 20);
        t.world.stock(STOCK, "minecraft:stone_pickaxe", 2);
        t.world.food = 60; // a long test: keep everyone fed
        t.sim.planner.surveyAll(t.world, t.c());
        // Underground the fake pathfinder only finds short ways, like the real one from far away.
        t.world.reachable = (actor, p) -> p.y() >= 64 || actor.distanceSquared(p) <= 3.5 * 3.5;
        t.world.vanillaAccuracy = true;
        Set<String> together = new HashSet<>();
        int[] maxProgress = {0};
        CodaBlockPos[] firstMine = {null};
        String[] firstDirection = {null};
        for (int i = 0; i < 300; i++) {
            t.seconds(1);
            if (firstMine[0] == null && t.c().mineEntrance != null) { firstMine[0] = t.c().mineEntrance; firstDirection[0] = t.c().mineDirection; }
            if (a.target != null && a.target.equals(b.target)) together.add("both at " + a.target);
            maxProgress[0] = Math.max(maxProgress[0], t.c().mineProgress);
        }
        check(a.profession == MINER && b.profession == MINER, "A pickaxe alone makes a miner: no /frontier mine needed: " + a.profession + "/" + b.profession + " " + a.activity + " / " + b.activity);
        check(t.c().mineEntrance != null && FrontierJobs.autoMine(t.c()), "The miners chose a stairway mine themselves");
        long chosen = t.world.announcements.stream().filter(x -> x.contains("chose a place for the settlement's mine")).count();
        long closed = t.world.announcements.stream().filter(x -> x.contains("Miners will choose a new place")).count();
        check(chosen >= 1 && chosen == closed + 1, "One shared mine at a time, not one each (" + chosen + " chosen, " + closed + " closed)");
        check(together.isEmpty(), "Never two miners at the same face: " + together);
        var e = firstMine[0];
        var col4 = FrontierJobs.mineFeet(e, firstDirection[0], "stairs", 4);
        check(col4.y() == e.y() - 4, "The stairway goes one block down per step");
        check(maxProgress[0] > 20 && t.world.block(col4).orElseThrow().air() && t.world.block(FrontierSimulation.add(col4, 0, 1, 0)).orElseThrow().air()
                && t.world.block(FrontierSimulation.add(col4, 0, 2, 0)).orElseThrow().air(), "Steps are dug three high so settlers can walk down (progress " + maxProgress[0] + ") " + a.activity + " / " + b.activity + " / " + t.world.announcements.stream().filter(x -> x.contains("mine")).toList());
        var plot = t.sim.planner.nextPlot(t.c());
        check(plot == null || !FrontierPlanner.CellPos.of(e).equals(plot.cell()), "No home is planned on top of the mine");
        t.reopen();
        check(t.c().mineEntrance != null && FrontierJobs.autoMine(t.c()), "The shared mine survives reopening the world");
        // Dug out to the end, it is closed and the next one chosen: still one mine at a time.
        var mineNow = t.c().mineEntrance;
        until(t, 1500, () -> t.c().mineEntrance != null && !t.c().mineEntrance.equals(mineNow));
        check(t.world.announcements.stream().noneMatch(x -> x.contains("not enough food")), "Miners deep in the mine walk back out the way they came to eat");
        check(t.c().mineEntrance != null && !t.c().mineEntrance.equals(mineNow) && t.world.announcements.stream().anyMatch(x -> x.contains("is finished")),
                "A finished mine is followed by a new one elsewhere: " + mineNow + " " + t.c().mineEntrance + " " + t.world.announcements.stream().filter(x -> !x.contains("mine")).skip(Math.max(0, t.world.announcements.size() - 12)).toList() + " food " + t.world.food);
    }

    /** The reported bug: a mine set at a cave below a hill. Miners go to its mouth, then inside, never over the hill. */
    static void playerMineIsReachedThroughItsMouth() throws Exception {
        var t = new Camp();
        var s = camp(t);
        t.world.stock(STOCK, "minecraft:stone_pickaxe", 1);
        // A hill east of the camp: solid stone from x=8 to 24 up to y=72, with the cave mouth at its foot.
        for (int x = 8; x <= 24; x++) for (int y = 64; y <= 72; y++) for (int z = -3; z <= 3; z++)
            t.world.blocks.put(new CodaBlockPos(x, y, z), block("minecraft:stone", Map.of(), true));
        t.world.reachable = (actor, p) -> p.x() < 8 || actor.distanceSquared(p) <= 3.5 * 3.5; // inside the hill only short steps work
        t.world.vanillaAccuracy = true;
        t.c().mineEntrance = new CodaBlockPos(8, 64, 0);
        t.c().mineDirection = "east";
        until(t, 400, () -> t.c().mineProgress >= 12);
        check(t.c().mineProgress >= 12, "The miner walks in through the mouth and digs (" + s.activity + ")");
        var at = t.world.actor(s.id).orElseThrow();
        check(at.y() < 66, "The miner is inside the tunnel, not on top of the hill (y " + at.y() + ")");
        check(!s.activity.contains("Path blocked"), "No path-blocked complaint: " + s.activity);
    }

    /** A stairway that reaches water is closed and the miners choose another place; nothing is dug next to the water. */
    static void unsafeStairwayIsClosedAndAnotherChosen() throws Exception {
        var t = new Camp();
        var s = camp(t);
        t.world.stock(STOCK, "minecraft:stone_pickaxe", 1);
        t.sim.planner.surveyAll(t.world, t.c());
        until(t, 60, () -> t.c().mineEntrance != null);
        var first = t.c().mineEntrance;
        check(first != null, "A mine was chosen");
        var col3 = FrontierJobs.mineFeet(first, t.c().mineDirection, "stairs", 3);
        var under = FrontierSimulation.add(col3, 0, -1, 0);
        t.world.blocks.put(under, new CodaLivingWorld.Block("minecraft:water", Map.of(), false, true));
        until(t, 600, () -> t.c().mineEntrance != null && !t.c().mineEntrance.equals(first));
        check(t.world.announcements.stream().anyMatch(x -> x.contains("reached water")), "Players are told the mine was closed and why");
        check(t.c().mineEntrance != null && !t.c().mineEntrance.equals(first), "Another place was chosen");
        check(t.world.block(under).orElseThrow().fluid(), "The water was not dug into");
    }

    /**
     * The 0.4.1 play test: Asta dug one block, then stood still until nudged. The loader's pathfinder
     * ignores a target within a block, and she was asked to step exactly one column forward.
     */
    static void minerKeepsDiggingWithTheRealPathfinder() throws Exception {
        var t = new Camp();
        var s = camp(t);
        t.world.vanillaAccuracy = true;
        t.world.food = 60;
        t.world.stock(STOCK, "minecraft:stone_pickaxe", 1);
        t.sim.planner.surveyAll(t.world, t.c());
        until(t, 60, () -> t.c().mineEntrance != null);
        int[] stuck = {0, 0};
        int last = t.c().mineProgress;
        for (int i = 0; i < 600; i++) {
            t.seconds(1);
            if (t.c().mineProgress == last && s.profession == MINER && !s.delivering && s.activity.startsWith("Walking down")) stuck[0]++; else stuck[0] = 0;
            stuck[1] = Math.max(stuck[1], stuck[0]);
            last = t.c().mineProgress;
        }
        check(t.c().mineProgress >= 40, "With the real pathfinder's rules the miner keeps digging on her own (progress " + t.c().mineProgress + ", " + s.activity + ")");
        check(stuck[1] < 10, "She never stands walking on the spot for long (" + stuck[1] + " seconds)");
    }
}
