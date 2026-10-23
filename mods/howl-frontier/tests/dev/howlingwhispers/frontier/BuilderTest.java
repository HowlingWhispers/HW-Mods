package dev.howlingwhispers.frontier;

import dev.howlingwhispers.codaloader.api.*;
import java.util.*;
import static dev.howlingwhispers.frontier.Fixtures.*;
import static dev.howlingwhispers.frontier.Settler.State.*;

/** Building, end to end, in a fake world whose placement follows CodaLoader 0.0.46's rules. */
public final class BuilderTest {
    static final CodaBlockPos STOCK = new CodaBlockPos(2, 64, 2);

    public static void main(String[] args) throws Exception {
        geometry();
        pioneerBuildsOwnCabin();
        roadComesFirst();
        roadThroughForestFellsTrees();
        playerBlocksAreNeverTouched();
        waitsForDoorAndResumesAfterReopen();
        statusShowsBuilding();
        finish("builder checks");
    }

    static void geometry() {
        check(Arrays.equals(FrontierBuilder.rotate(2, 4, 5, 5, "south"), new int[]{2, 4}), "South keeps the blueprint as drawn");
        check(Arrays.equals(FrontierBuilder.rotate(2, 4, 5, 5, "north"), new int[]{2, 0}), "North turns the front row to the north edge");
        check(Arrays.equals(FrontierBuilder.rotate(2, 4, 5, 5, "east"), new int[]{4, 2}), "East turns the front row to the east edge");
        check(Arrays.equals(FrontierBuilder.rotate(2, 4, 5, 5, "west"), new int[]{0, 2}), "West turns the front row to the west edge");
        check(FrontierBuilder.rotateDirection("south", "east").equals("east") && FrontierBuilder.rotateDirection("north", "east").equals("west")
                && FrontierBuilder.rotateDirection("south", "north").equals("north") && FrontierBuilder.rotateDirection("west", "west").equals("north"), "Facing properties turn with the building");
        check(FrontierBuilder.rotateProperties("half=lower,facing=south,hinge=left", "west").equals("half=lower,facing=west,hinge=left"), "Only the facing property turns");
    }

    /** A settlement with a stocked chest, food, and natural oak trees in the north-west of the land. */
    static Camp meadow(int trees) throws Exception {
        var t = new Camp();
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

    static void pioneerBuildsOwnCabin() throws Exception {
        var t = meadow(16);
        t.world.stock(STOCK, "minecraft:stone_axe", 1);
        t.world.stock(STOCK, "minecraft:oak_sapling", 16);
        t.world.stock(STOCK, "minecraft:spruce_door", 1);
        t.world.stock(STOCK, "minecraft:blue_bed", 1);
        until(t, 200, () -> pioneer(t) != null && pioneer(t).state == RESIDENT);
        var p = pioneer(t);
        check(p != null && p.state == RESIDENT, "The pioneer arrives and settles");
        until(t, 120, () -> t.c().project != null);
        check(t.c().project != null && t.c().project.palette.name().equals("oak log cabin"), "With no free bed, a home is planned in the land's wood");
        until(t, 5, () -> p.profession == Settler.Profession.BUILDER);
        check(p.profession == Settler.Profession.BUILDER, "The pioneer becomes the builder");
        check(t.world.announcements.stream().anyMatch(a -> a.contains("will build the new home")), "Players are told who builds");
        var project = t.c().project;
        until(t, 4000, () -> t.c().project == null);
        check(t.c().project == null && t.c().homesBuilt == 1, "The cabin is finished (" + p.activity + ")");
        check(t.world.announcements.stream().anyMatch(a -> a.contains("A new home is finished")), "Players are told the home is finished");
        long logs = t.world.blocks.values().stream().filter(b -> b.id().equals("minecraft:oak_log")).count();
        check(t.world.placedBlocks.size() == 80, "78 logs, a door and a bed were placed (" + t.world.placedBlocks.size() + ")");
        check(t.world.harvested.stream().filter(h -> h.equals("minecraft:oak_log")).count() >= 78, "Every log came from felling real trees");
        check(t.world.stocked(STOCK, "minecraft:spruce_door") == 0 && t.world.stocked(STOCK, "minecraft:blue_bed") == 0, "The door and bed from the stockpile were used");
        // The cabin's door faces the square side, and its bed counts as a sheltered home.
        var door = t.world.blocks.entrySet().stream().filter(e -> e.getKey().y() > 64 && e.getValue().id().equals("minecraft:spruce_door")
                && "lower".equals(e.getValue().properties().get("half"))).findFirst().orElseThrow();
        check(door.getValue().properties().get("facing").equals(project.facing), "The door faces the road side: " + project.facing);
        var bed = t.world.blocks.entrySet().stream().filter(e -> e.getValue().id().equals("minecraft:blue_bed") && "head".equals(e.getValue().properties().get("part"))).findFirst().orElseThrow();
        check(FrontierSimulation.shelteredBed(t.world, bed.getKey()), "The bed is under the cabin's roof, 2 blocks above");
        until(t, 5, () -> bed.getKey().equals(p.bed));
        check(bed.getKey().equals(p.bed), "The pioneer moves into the cabin's bed");
        until(t, 120, () -> t.c().project != null);
        check(t.c().project != null && !t.c().project.cell.equals(project.cell), "With every bed taken, the next home is planned on another plot");
        check(FrontierPlanner.CellPos.of(door.getKey()).equals(project.cell), "The cabin stands on its planned plot");
    }

    static void roadComesFirst() throws Exception {
        var t = meadow(0);
        // Water everywhere near the square except a gently sloping corridor east, so the plot needs a road.
        Set<FrontierPlanner.CellPos> dry = Set.of(new FrontierPlanner.CellPos(2, 0), new FrontierPlanner.CellPos(3, 0), new FrontierPlanner.CellPos(3, -1),
                new FrontierPlanner.CellPos(0, 0), new FrontierPlanner.CellPos(1, 0), new FrontierPlanner.CellPos(0, 1), new FrontierPlanner.CellPos(1, 1));
        t.world.flooded = p -> p.y() <= 63 && !dry.contains(FrontierPlanner.CellPos.of(p))
                && Math.max(Math.abs(Math.floorDiv(p.x(), 8) - .5), Math.abs(Math.floorDiv(p.z(), 8) - .5)) < 3;
        t.world.ground = (x, z) -> x >= 16 && x < 24 && z >= 0 && z < 8 ? 63 + (x - 16) / 2 : 63; // cell (2,0) rises 3
        t.world.stock(STOCK, "minecraft:stone_shovel", 1);
        t.world.stock(STOCK, "minecraft:cobblestone", 78);
        t.world.stock(STOCK, "minecraft:oak_door", 1);
        t.world.stock(STOCK, "minecraft:red_bed", 1);
        until(t, 400, () -> t.c().project != null);
        var project = t.c().project;
        check(project != null && project.road.contains(new FrontierPlanner.CellPos(2, 0)) && project.palette.name().equals("cobblestone"), "A plot past the corridor is planned, with a road");
        until(t, 600, () -> t.c().project == null || t.c().project.phase != Settlement.Project.Phase.ROAD);
        long paths = t.world.blocks.entrySet().stream().filter(e -> e.getValue().id().equals("minecraft:dirt_path")
                && FrontierPlanner.CellPos.of(e.getKey()).equals(new FrontierPlanner.CellPos(2, 0))).count();
        check(paths >= 8, "A dirt path was laid through the corridor first (" + paths + " blocks)");
        check(t.c().roads.contains(new FrontierPlanner.CellPos(2, 0)), "The road is remembered");
        until(t, 2000, () -> t.c().project == null);
        check(t.c().project == null && t.c().homesBuilt == 1, "Then the cobblestone cabin is built");
        check(t.world.stocked(STOCK, "minecraft:cobblestone") == 0, "Exactly 78 cobblestone were used");
        t.sim.planner.surveyAll(t.world, t.c());
        check(t.sim.planner.survey(new FrontierPlanner.CellPos(2, 0)).orElseThrow().role() == FrontierPlanner.Role.ROAD, "The planner sees the laid road as a road, not as a build");
    }

    static void playerBlocksAreNeverTouched() throws Exception {
        var t = meadow(0);
        t.world.stock(STOCK, "minecraft:cobblestone", 78);
        t.world.stock(STOCK, "minecraft:oak_door", 1);
        t.world.stock(STOCK, "minecraft:red_bed", 1);
        until(t, 400, () -> t.c().project != null);
        var project = t.c().project;
        var steps = t.sim.builder.steps(project);
        var mine = steps.get(steps.size() - 1).pos(); // a roof block position
        t.world.blocks.put(mine, block("minecraft:glass", Map.of(), true));
        until(t, 2000, () -> pioneer(t) != null && pioneer(t).activity.contains("cannot be cleared"));
        check(pioneer(t).activity.contains("glass"), "The builder stops at a block you placed: " + pioneer(t).activity);
        check(t.world.block(mine).orElseThrow().id().equals("minecraft:glass"), "...and never breaks it");
        check(t.world.harvested.stream().noneMatch(h -> h.equals("minecraft:glass")), "Your block was not harvested");
    }

    static void waitsForDoorAndResumesAfterReopen() throws Exception {
        var t = meadow(0);
        t.world.stock(STOCK, "minecraft:cobblestone", 78);
        t.world.stock(STOCK, "minecraft:red_bed", 1);
        until(t, 400, () -> t.c().project != null);
        until(t, 800, () -> pioneer(t) != null && pioneer(t).activity.contains("needs a door"));
        check(pioneer(t).activity.contains("needs a door"), "Without a door the builder waits and says so: " + pioneer(t).activity);
        int placedBefore = t.world.placedBlocks.size();
        check(placedBefore > 0 && placedBefore < 80, "Everything up to the door was built (" + placedBefore + ")");
        t.reopen();
        check(t.c().project != null && t.c().project.step > 0, "Progress survives reopening the world");
        t.world.stock(STOCK, "minecraft:birch_door", 1);
        until(t, 1500, () -> t.c().project == null);
        check(t.c().homesBuilt == 1, "With a door supplied, building resumes and finishes");
        check(t.world.placedBlocks.size() == 80, "Nothing was placed twice after reopening (" + t.world.placedBlocks.size() + ")");
        check(t.world.stocked(STOCK, "minecraft:cobblestone") == 0 && t.world.stocked(STOCK, "minecraft:birch_door") == 0, "Every material was used exactly once");
    }

    static void roadThroughForestFellsTrees() throws Exception {
        var t = meadow(0);
        Set<FrontierPlanner.CellPos> dry = Set.of(new FrontierPlanner.CellPos(2, 0), new FrontierPlanner.CellPos(3, 0), new FrontierPlanner.CellPos(3, -1),
                new FrontierPlanner.CellPos(0, 0), new FrontierPlanner.CellPos(1, 0), new FrontierPlanner.CellPos(0, 1), new FrontierPlanner.CellPos(1, 1));
        t.world.flooded = p -> p.y() <= 63 && !dry.contains(FrontierPlanner.CellPos.of(p))
                && Math.max(Math.abs(Math.floorDiv(p.x(), 8) - .5), Math.abs(Math.floorDiv(p.z(), 8) - .5)) < 3;
        // A forest corridor: one tree right on the road's centre line, two beside it.
        t.world.tree(new CodaBlockPos(19, 64, 4), 4, "minecraft:oak", true);
        t.world.tree(new CodaBlockPos(17, 64, 0), 4, "minecraft:oak", true);
        t.world.tree(new CodaBlockPos(22, 64, 7), 4, "minecraft:oak", true);
        t.world.stock(STOCK, "minecraft:stone_shovel", 1);
        t.world.stock(STOCK, "minecraft:stone_axe", 1);
        until(t, 400, () -> t.c().project != null);
        check(t.c().project != null && t.c().project.road.contains(new FrontierPlanner.CellPos(2, 0)), "The road is planned through the forest corridor");
        until(t, 900, () -> t.c().project == null || t.c().project.phase != Settlement.Project.Phase.ROAD);
        check(t.c().project != null && t.c().project.phase != Settlement.Project.Phase.ROAD, "The road was finished: " + (pioneer(t) == null ? "" : pioneer(t).activity));
        check(t.world.block(new CodaBlockPos(19, 64, 4)).orElseThrow().air() && t.world.block(new CodaBlockPos(19, 63, 4)).orElseThrow().id().equals("minecraft:dirt_path"),
                "The tree on the line was felled and the path laid where it stood");
        check(t.world.harvested.stream().filter(h -> h.equals("minecraft:oak_log")).count() >= 4, "The whole trunk was cut, top first, with the axe");
    }

    static void statusShowsBuilding() throws Exception {
        var profile = java.nio.file.Files.createTempDirectory("frontier-build-profile-");
        java.nio.file.Files.createDirectories(profile.resolve("config"));
        java.nio.file.Files.writeString(profile.resolve("config/howl-play-mode.txt"), "frontier\n");
        var mod = new FrontierMod();
        mod.onInitialize(new CodaContext("0.0.46", "26.4-snapshot-3", profile, profile.resolve("config"), "howl_frontier", List.of("howl_frontier")));
        var command = CodaCommands.registrations().stream().filter(r -> r.name().equals("frontier")).reduce((a, b) -> b).orElseThrow().command();
        var t = new Camp();
        CodaServerTicks.dispatch(new CodaServerTickContext("build-" + t.root.getFileName(), 20, Optional.of(t.view)));
        var me = new Player(t.view);
        me.look(CAMP);
        command.execute(me, List.of("camp"));
        var sim = mod.simulation();
        sim.camp().stockpile = STOCK;
        t.world.blocks.put(STOCK, block("minecraft:chest", Map.of("type", "single"), true));
        me.replies.clear();
        command.execute(me, List.of("status"));
        check(me.said("Building: nothing yet"), "Before any project, status says nothing is being built");
        sim.planner.surveyAll(t.world, sim.camp());
        var s = new Settler(UUID.randomUUID(), "Asta");
        s.state = RESIDENT;
        s.arrivalPoint = new CodaBlockPos(20, 64, 0);
        sim.camp().settlers.put(s.id, s);
        sim.camp().recordResidents();
        sim.builder.maybeStart(t.world, sim.camp());
        check(sim.camp().project != null, "A project starts when nobody has a free bed");
        me.replies.clear();
        command.execute(me, List.of("status"));
        check(me.said("cobblestone at") && me.said("still needs 1 bed, 78 cobblestone, 1 door") && me.said("no builder free"), "Status shows the project, its needs and its builder: " + me.replies);
        command.execute(me, List.of("plan"));
        check(me.said("Building: cobblestone at") && me.said("door facing"), "Plan shows the project in place of the next plot");
    }
}
