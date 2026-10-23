package dev.howlingwhispers.frontier;

import dev.howlingwhispers.codaloader.api.*;
import java.util.*;
import static dev.howlingwhispers.frontier.Fixtures.*;
import static dev.howlingwhispers.frontier.Settler.State.*;

/**
 * 0.5.0: settlers craft with H.O.W.L. 0.0.47 (L8), in a fake world whose craft() follows the loader:
 * a real crafting table within 3 blocks, 2x2 recipes without one, bag first, all-or-nothing.
 */
public final class CraftingTest {
    static final CodaBlockPos STOCK = new CodaBlockPos(2, 64, 2);

    public static void main(String[] args) throws Exception {
        recipesMatchTheGame();
        plankHouseFromLogsAlone();
        bedFromWool();
        olderLoaderKeepsLogCabins();
        finish("crafting checks");
    }

    /** Frontier's ingredient table must agree with the vanilla recipes the loader uses (counts from the real client's JSON). */
    static void recipesMatchTheGame() {
        var planks = FrontierCrafting.recipe("minecraft:oak_planks");
        check(planks.yield() == 4 && planks.ingredients().equals(Map.of("minecraft:oak_log", 1)) && !planks.table(), "1 log makes 4 planks without a table");
        var door = FrontierCrafting.recipe("minecraft:spruce_door");
        check(door.yield() == 3 && door.ingredients().equals(Map.of("minecraft:spruce_planks", 6)) && door.table(), "6 planks make 3 doors at a table");
        var bed = FrontierCrafting.recipe("minecraft:red_bed");
        check(bed.yield() == 1 && bed.ingredients().equals(Map.of("minecraft:red_wool", 3, FrontierCrafting.ANY_PLANKS, 3)) && bed.table(), "3 wool and 3 planks make a bed");
        var ladder = FrontierCrafting.recipe("minecraft:ladder");
        check(ladder.yield() == 3 && ladder.ingredients().equals(Map.of("minecraft:stick", 7)) && ladder.table(), "7 sticks make 3 ladders");
        var stick = FrontierCrafting.recipe("minecraft:stick");
        check(stick.yield() == 4 && stick.ingredients().equals(Map.of(FrontierCrafting.ANY_PLANKS, 2)) && !stick.table(), "2 planks make 4 sticks");
        check(FrontierCrafting.recipe("minecraft:iron_door") == null && FrontierCrafting.recipe("minecraft:diamond") == null, "Nothing else is crafted");
        check(FrontierCrafting.bedFromWool(Map.of("minecraft:blue_wool", 3)).equals("minecraft:blue_bed")
                && FrontierCrafting.bedFromWool(Map.of("minecraft:blue_wool", 2)) == null, "A bed needs 3 wool of one colour");
    }

    static Camp camp(int trees) throws Exception {
        var t = new Camp();
        t.crafting = true;
        t.sim.crafting.enabled = true;
        t.found();
        t.world.blocks.put(STOCK, block("minecraft:chest", Map.of("type", "single"), true));
        t.c().stockpile = STOCK;
        t.world.food = 60;
        for (int i = 0; i < trees; i++) t.world.tree(new CodaBlockPos(-14 + 4 * (i % 4), 64, -14 + 4 * (i / 4)), 5, "minecraft:oak", true);
        return t;
    }

    static Settler pioneer(Camp t) {
        return t.c().settlers.values().stream().filter(s -> s.pioneer && !s.terminal()).findFirst().orElse(null);
    }

    static void until(Camp t, int seconds, java.util.function.BooleanSupplier done) {
        for (int i = 0; i < seconds && !done.getAsBoolean(); i++) t.seconds(1);
    }

    /**
     * The play-test complaint: settlers only used logs and asked the player for a door. With crafting,
     * they fell trees, make planks, set up a workbench, make their own door and build a plank house.
     */
    static void plankHouseFromLogsAlone() throws Exception {
        var t = camp(16);
        t.world.stock(STOCK, "minecraft:stone_axe", 1);
        t.world.stock(STOCK, "minecraft:oak_sapling", 16);
        t.world.stock(STOCK, "minecraft:cobblestone", 25);
        t.world.stock(STOCK, "minecraft:red_bed", 1);
        until(t, 400, () -> t.c().project != null);
        var project = t.c().project;
        check(project != null && project.palette.name().equals("oak plank house"), "With crafting, the home is planned as a plank house: " + (project == null ? "none" : project.palette.name()));
        check(project.palette.frame().equals("minecraft:oak_log") && project.palette.wall().equals("minecraft:oak_planks") && project.palette.roof().equals("minecraft:oak_planks"),
                "Log corner posts, plank walls and roof");
        until(t, 6000, () -> t.c().project == null);
        var p = pioneer(t);
        check(t.c().homesBuilt == 1, "The plank house is finished with no door supplied: " + (p == null ? "" : p.activity));
        var table = FrontierCrafting.workbench(t.world, t.c());
        check(table != null, "A workbench was set up beside the stockpile");
        check(t.world.announcements.stream().anyMatch(a -> a.contains("set up a workbench")), "Players are told about the workbench");
        check(t.world.crafted.contains("minecraft:crafting_table") && t.world.crafted.contains("minecraft:oak_door") && t.world.crafted.contains("minecraft:oak_planks"),
                "Planks, a crafting table and the door were really crafted: " + new TreeSet<>(t.world.crafted));
        long planksPlaced = t.world.placedBlocks.stream().filter(b -> t.world.block(b).map(x -> x.id().equals("minecraft:oak_planks")).orElse(false)).count();
        long logsPlaced = t.world.placedBlocks.stream().filter(b -> t.world.block(b).map(x -> x.id().equals("minecraft:oak_log")).orElse(false)).count();
        check(planksPlaced > 30 && logsPlaced <= 12, "Walls and roof are planks, only the posts are logs (" + planksPlaced + " planks, " + logsPlaced + " logs)");
        long logsCut = t.world.harvested.stream().filter(h -> h.equals("minecraft:oak_log")).count();
        check(logsCut < 40, "Far fewer logs than a 53-log cabin: 4 planks from every log (" + logsCut + " cut)");
        var door = t.world.blocks.entrySet().stream().filter(e -> e.getValue().id().equals("minecraft:oak_door") && "lower".equals(e.getValue().properties().get("half"))).findFirst();
        check(door.isPresent(), "The crafted oak door is in the house");
    }

    static void bedFromWool() throws Exception {
        var t = camp(0);
        t.world.stock(STOCK, "minecraft:cobblestone", 78);
        t.world.stock(STOCK, "minecraft:oak_door", 1);
        t.world.stock(STOCK, "minecraft:oak_planks", 8);
        t.world.stock(STOCK, "minecraft:blue_wool", 3);
        until(t, 3000, () -> t.c().project == null && t.c().homesBuilt == 1);
        var p = pioneer(t);
        check(t.c().homesBuilt == 1, "The house is finished with a bed made from the wool: " + (p == null ? "" : p.activity));
        check(t.world.crafted.contains("minecraft:blue_bed"), "A blue bed was crafted from blue wool");
        check(t.world.blocks.values().stream().anyMatch(b -> b.id().equals("minecraft:blue_bed")), "...and placed in the house");
        check(t.world.stocked(STOCK, "minecraft:blue_wool") == 0, "The wool was used");
    }

    /** On H.O.W.L. 0.0.46 there is no craft(): log cabins and asking for doors, exactly as before. */
    static void olderLoaderKeepsLogCabins() throws Exception {
        var t = camp(16);
        t.crafting = false;
        t.sim.crafting.enabled = false;
        t.world.stock(STOCK, "minecraft:stone_axe", 1);
        t.world.stock(STOCK, "minecraft:oak_sapling", 16);
        t.world.stock(STOCK, "minecraft:cobblestone", 25);
        t.world.stock(STOCK, "minecraft:red_bed", 1);
        until(t, 400, () -> t.c().project != null);
        check(t.c().project != null && t.c().project.palette.name().equals("oak log cabin"), "Without crafting the home stays a log cabin");
        until(t, 3000, () -> pioneer(t) != null && pioneer(t).activity.contains("needs a door"));
        check(pioneer(t).activity.contains("needs a door"), "...and the builder asks for a door: " + pioneer(t).activity);
        check(t.world.crafted.isEmpty() && FrontierCrafting.workbench(t.world, t.c()) == null, "Nothing is crafted and no workbench is set up");
    }
}
