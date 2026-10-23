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
        fullStockpileIsReported();
        noToolsMeansNoJob();
        olderSavesStillLoad();
        finish("job checks");
    }

    /** A settlement with a stockpile and one resident labourer standing by the campfire. */
    static Settler camp(Camp t) throws Exception {
        t.found();
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
}
