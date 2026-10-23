package dev.howlingwhispers.frontier;

import dev.howlingwhispers.codaloader.api.*;
import java.util.*;
import static dev.howlingwhispers.frontier.Fixtures.*;
import static dev.howlingwhispers.frontier.FrontierPlanner.Role.*;

/**
 * The planner against made-up landscapes. The campfire at 0,64,0 sits in chunk 0,0, so the
 * square is cells (0,0),(1,0),(0,1),(1,1); a 3x3-chunk land covers cells -2..3 on each axis.
 */
public final class PlannerTest {
    static FrontierPlanner.CellPos cell(int x, int z) { return new FrontierPlanner.CellPos(x, z); }

    public static void main(String[] args) throws Exception {
        grid();
        flatMeadow();
        hazards();
        yourBuildsAreLeftAlone();
        roadsGoAroundObstacles();
        palettes();
        blueprints();
        planCommandShowsMap();
        finish("planner checks");
    }

    static Camp camp() throws Exception {
        var t = new Camp();
        t.found();
        return t;
    }

    static FrontierPlanner.Role role(Camp t, int x, int z) throws Exception {
        t.sim.planner.surveyAll(t.world, t.c());
        return t.sim.planner.survey(cell(x, z)).orElseThrow().role();
    }

    static String reason(Camp t, int x, int z) { return t.sim.planner.survey(cell(x, z)).orElseThrow().reason(); }

    static void grid() throws Exception {
        var t = camp();
        var cells = FrontierPlanner.cells(t.c().territory());
        check(cells.size() == 36 && cells.getFirst().equals(cell(-2, -2)) && cells.getLast().equals(cell(3, 3)), "A 3x3-chunk land is 6x6 cells");
        check(FrontierPlanner.CellPos.of(new CodaBlockPos(-1, 64, -1)).equals(cell(-1, -1)), "Cells use floor division too");
        for (var c : List.of(cell(0, 0), cell(1, 0), cell(0, 1), cell(1, 1))) check(FrontierPlanner.square(t.c(), c), "The campfire's chunk is the square: " + c);
        check(!FrontierPlanner.square(t.c(), cell(-1, 0)), "Cells outside the campfire chunk are not the square");
    }

    static void flatMeadow() throws Exception {
        var t = camp();
        check(role(t, 0, 0) == SQUARE && role(t, -2, -2) == OPEN && role(t, 3, 3) == OPEN, "A flat empty meadow is open everywhere but the square");
        var plot = t.sim.planner.nextPlot(t.c());
        check(plot != null && plot.road().isEmpty(), "On flat land the first home goes next to the square, with no road needed");
        check(Math.abs(plot.cell().x() - 0.5) <= 1.5 && Math.abs(plot.cell().z() - 0.5) <= 1.5, "The plot touches the square: " + plot.cell());
        check(t.sim.planner.palette().name().equals("cobblestone"), "No trees on plain grass: build in cobblestone");
    }

    static void hazards() throws Exception {
        // Water filling cell (-2,-2).
        var t = camp();
        t.world.flooded = p -> p.x() >= -16 && p.x() < -8 && p.z() >= -16 && p.z() < -8 && p.y() <= 63;
        check(role(t, -2, -2) == WATER, "Water is detected: " + reason(t, -2, -2));
        // A cliff: the ground east of x=24 falls away by 10 blocks, beside cell (2,-2).
        t = camp();
        t.world.ground = (x, z) -> x >= 24 ? 53 : 63;
        check(role(t, 2, -2) == HAZARD && reason(t, 2, -2).contains("drop of 10"), "A cliff edge is a hazard: " + reason(t, 2, -2));
        check(role(t, 3, -2) == OPEN, "The low ground at the cliff's foot is fine");
        // A cave two blocks under cell (-1,2).
        t = camp();
        for (int x = -6; x <= -4; x++) for (int z = 18; z <= 20; z++) t.world.blocks.put(new CodaBlockPos(x, 61, z), block("minecraft:cave_air", Map.of(), false));
        check(role(t, -1, 2) == HAZARD && reason(t, -1, 2).contains("hollow"), "A cave under the floor is a hazard: " + reason(t, -1, 2));
        // Sand on top of cell (2,2).
        t = camp();
        for (int x = 16; x < 24; x++) for (int z = 16; z < 24; z++) t.world.blocks.put(new CodaBlockPos(x, 63, z), block("minecraft:sand", Map.of(), true));
        check(role(t, 2, 2) == HAZARD && reason(t, 2, 2).contains("loose ground"), "Sand under the floor is a hazard");
        // Lava just beside cell (-2,0).
        t = camp();
        t.world.blocks.put(new CodaBlockPos(-17, 63, 3), new CodaLivingWorld.Block("minecraft:lava", Map.of(), false, true));
        check(role(t, -2, 0) == HAZARD && reason(t, -2, 0).contains("lava"), "Lava next to a cell is a hazard");
        // A hill: cell (3,0) rises 5 blocks.
        t = camp();
        t.world.ground = (x, z) -> x >= 24 && z >= 0 && z < 8 ? 63 + Math.min(5, x - 24) : 63;
        check(role(t, 3, 0) == STEEP, "Uneven ground is steep: " + reason(t, 3, 0));
        // Unloaded land is never guessed.
        t = camp();
        t.world.loaded = false;
        t.sim.planner.surveyAll(t.world, t.c());
        t.world.loaded = true;
        check(t.sim.planner.survey(cell(-2, -2)).orElseThrow().role() == UNKNOWN, "Unloaded cells stay unknown");
        check(t.sim.planner.nextPlot(t.c()) == null, "No plot is chosen without a survey");
    }

    static void yourBuildsAreLeftAlone() throws Exception {
        var t = camp();
        t.world.blocks.put(new CodaBlockPos(-12, 64, 4), block("minecraft:oak_planks", Map.of(), true));
        t.world.blocks.put(new CodaBlockPos(20, 65, -12), block("minecraft:glass", Map.of(), true));
        t.world.blocks.put(new CodaBlockPos(-12, 63, -12), block("minecraft:dirt_path", Map.of(), true));
        check(role(t, -2, 0) == YOURS && role(t, 2, -2) == YOURS && role(t, -2, -2) == YOURS, "Planks, glass and paths mark a cell as your builds");
        t.world.tree(new CodaBlockPos(-12, 64, 20), 5, "minecraft:oak", true);
        check(role(t, -2, 2) == OPEN, "A single natural tree is not a build: " + reason(t, -2, 2));
        t = camp();
        t.world.tree(new CodaBlockPos(-12, 64, -12), 5, "minecraft:birch", false); // player-placed leaves
        check(role(t, -2, -2) == YOURS, "Leaves the player placed count as a build");
        t = camp();
        for (int i = 0; i < 3; i++) t.world.tree(new CodaBlockPos(-15 + 3 * i, 64, -15 + 3 * i), 4, "minecraft:spruce", true);
        check(role(t, -2, -2) == FOREST, "Three natural trees make a forest");
    }

    static void roadsGoAroundObstacles() throws Exception {
        var t = camp();
        // Surround the square with water except a corridor to the east along row z=0.
        Set<FrontierPlanner.CellPos> dry = Set.of(cell(2, 0), cell(3, 0), cell(3, -1), cell(0, 0), cell(1, 0), cell(0, 1), cell(1, 1));
        t.world.flooded = p -> p.y() <= 63 && !dry.contains(FrontierPlanner.CellPos.of(p))
                && Math.max(Math.abs(Math.floorDiv(p.x(), 8) - .5), Math.abs(Math.floorDiv(p.z(), 8) - .5)) < 3;
        // The corridor's first cell is forest: a road may cross it, a house may not.
        for (int i = 0; i < 3; i++) t.world.tree(new CodaBlockPos(17 + 3 * i, 64, 1 + 2 * i), 4, "minecraft:oak", true);
        t.sim.planner.surveyAll(t.world, t.c());
        check(t.sim.planner.survey(cell(2, 0)).orElseThrow().role() == FOREST, "The corridor starts in forest");
        var plot = t.sim.planner.nextPlot(t.c());
        check(plot != null, "A plot is found past the water");
        check(plot.cell().equals(cell(3, 0)) || plot.cell().equals(cell(3, -1)), "The plot is at the end of the dry corridor: " + plot.cell());
        check(plot.road().contains(cell(2, 0)), "The road runs along the corridor: " + plot.road());
        for (var r : plot.road()) check(t.sim.planner.survey(r).orElseThrow().role() != WATER, "No road through water");
        check(plot.cell().equals(cell(3, 0)) ? plot.doorFacing().equals("west") || plot.doorFacing().equals("north") : plot.doorFacing().equals("south") || plot.doorFacing().equals("west"),
                "The door faces the road: " + plot.doorFacing());
    }

    static void palettes() throws Exception {
        var t = camp();
        for (int i = 0; i < 5; i++) t.world.tree(new CodaBlockPos(-14 + 6 * i, 64, -14), 5, "minecraft:spruce", true);
        t.sim.planner.surveyAll(t.world, t.c());
        var p = t.sim.planner.palette();
        check(p.name().equals("spruce wood") && p.frame().equals("minecraft:spruce_log") && p.door().equals("minecraft:spruce_door"), "Trees nearby: build in the local wood");
        t = camp();
        t.world.ground = (x, z) -> 62;
        for (int x = -16; x < 32; x++) for (int z = -16; z < 32; z++) t.world.blocks.put(new CodaBlockPos(x, 62, z), block("minecraft:sandstone", Map.of(), true));
        t.world.blocks.put(new CodaBlockPos(0, 63, 0), block("minecraft:campfire", Map.of("lit", "true"), false));
        t.sim.planner.surveyAll(t.world, t.c());
        check(t.sim.planner.palette().name().equals("sandstone"), "Desert: build in sandstone");
        var mixed = Palette.choose(Map.of(), Map.of("minecraft:orange_terracotta", 30, "minecraft:terracotta", 10, "minecraft:grass_block", 50));
        check(mixed.name().equals("terracotta") && mixed.wall().equals("minecraft:orange_terracotta"), "Badlands: terracotta, walls in the common local colour");
        check(Palette.choose(Map.of("minecraft:oak_log", 2), Map.of("minecraft:grass_block", 60)).reason().contains("only 2 trees"), "Too few trees: says why it is not wood");
    }

    static void blueprints() throws Exception {
        var hut = Blueprint.resource("starter_hut");
        check(hut.width == 5 && hut.height == 4 && hut.depth == 5 && hut.anchorZ == 4, "The starter hut loads from the mod");
        var oak = hut.materials(Palette.wood("oak", "test"));
        check(oak.equals(new TreeMap<>(Map.of("minecraft:oak_log", 12, "minecraft:oak_planks", 66, "minecraft:oak_door", 1, "minecraft:red_bed", 1))), "Oak hut materials: " + oak);
        var sand = hut.materials(Palette.sandstone("test"));
        check(sand.equals(new TreeMap<>(Map.of("minecraft:sandstone", 78, "minecraft:oak_door", 1, "minecraft:red_bed", 1))), "The same hut in sandstone: " + sand);
        check(Palette.wood("oak", "").resolve("frame", "axis=y").equals("minecraft:oak_log[axis=y]") && Palette.stone("").resolve("frame", "axis=y").equals("minecraft:cobblestone"),
                "Properties only apply to blocks that have them");
        for (String bad : List.of("name: x\nsize: 1 1 1\nanchor: 0 0 0\nlegend:\n  A wall\nlayer 0:\n  B\n",
                "name: x\nsize: 2 1 1\nanchor: 0 0 0\nlegend:\n  A wall\nlayer 0:\n  A\n",
                "name: x\nsize: 1 1 1\nanchor: 0 0 0\nlegend:\n  A brick\nlayer 0:\n  A\n",
                "name: x\nsize: 1 2 1\nanchor: 0 0 0\nlegend:\n  A wall\nlayer 0:\n  A\n",
                "name: x\nsize: 1 1 1\nanchor: 3 0 0\nlegend:\n  A wall\nlayer 0:\n  A\n")) {
            try {
                Blueprint.parse(bad);
                throw new AssertionError("Accepted a broken blueprint:\n" + bad);
            } catch (IllegalArgumentException expected) { checks++; }
        }
    }

    static void planCommandShowsMap() throws Exception {
        var profile = java.nio.file.Files.createTempDirectory("frontier-plan-profile-");
        java.nio.file.Files.createDirectories(profile.resolve("config"));
        java.nio.file.Files.writeString(profile.resolve("config/howl-play-mode.txt"), "frontier\n");
        var mod = new FrontierMod();
        mod.onInitialize(new CodaContext("0.0.45", "26.4-snapshot-3", profile, profile.resolve("config"), "howl_frontier", List.of("howl_frontier")));
        var command = CodaCommands.registrations().stream().filter(r -> r.name().equals("frontier")).reduce((a, b) -> b).orElseThrow().command();
        var t = new Camp();
        CodaServerTicks.dispatch(new CodaServerTickContext("plan-" + t.root.getFileName(), 20, Optional.of(t.view)));
        var me = new Player(t.view);
        me.look(CAMP);
        command.execute(me, List.of("camp"));
        var sim = mod.simulation();
        sim.planner.surveyAll(t.world, sim.camp());
        me.replies.clear();
        me.raw.clear();
        command.execute(me, List.of("plan"));
        check(me.said("surveyed 36/36 cells") && me.said("Materials: cobblestone"), "Plan reports the survey and materials");
        check(me.said("Next home: plot at") && me.said("no road needed"), "Plan names the next home plot");
        check(me.said("Starter hut needs: 78 cobblestone, 1 oak door, 1 red bed"), "Plan lists the hut's materials in the land's palette");
        var mapRows = me.replies.stream().filter(r -> r.matches("(" + FrontierPlanner.GLYPH + ")+")).toList();
        check(mapRows.size() == 6 && mapRows.stream().allMatch(r -> r.length() == 6), "The map is 6 rows of 6 cells: " + mapRows);
        check(me.raw.stream().anyMatch(r -> r.contains(Chat.S + "b" + FrontierPlanner.GLYPH)), "The map marks where you stand");
        me.look(new CodaBlockPos(-12, 63, -12));
        command.execute(me, List.of("plan", "here"));
        check(me.said("Open") && me.said("flat, safe and empty"), "plan here explains a cell: " + me.replies.getLast());
        me.look(new CodaBlockPos(90, 63, 0));
        command.execute(me, List.of("plan", "here"));
        check(me.last().contains("outside your land"), "plan here refuses spots outside the land");
    }
}
