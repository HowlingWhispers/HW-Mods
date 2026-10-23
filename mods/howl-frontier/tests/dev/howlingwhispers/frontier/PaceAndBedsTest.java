package dev.howlingwhispers.frontier;

import dev.howlingwhispers.codaloader.api.*;
import java.nio.file.Files;
import java.util.*;
import static dev.howlingwhispers.frontier.Fixtures.*;

/** 0.4.3: the place/break speed settings file, and beds that do not count being explained. */
public final class PaceAndBedsTest {
    static final CodaBlockPos STOCK = new CodaBlockPos(2, 64, 2);

    public static void main(String[] args) throws Exception {
        configFile();
        paceFollowsTheSettings();
        uncountedBedIsExplained();
        finish("pace and bed checks");
    }

    static void configFile() throws Exception {
        var dir = Files.createTempDirectory("frontier-config-");
        var first = FrontierConfig.load(dir);
        check(first.equals(FrontierConfig.DEFAULTS), "With no file, the defaults are used");
        var file = dir.resolve(FrontierConfig.FILE);
        check(Files.isRegularFile(file) && Files.readString(file).contains("place_per_second=1") && Files.readString(file).contains("break_per_second=1"),
                "A commented settings file is created");
        Files.writeString(file, "place_per_second=4\nbreak_per_second=0,5\n");
        var custom = FrontierConfig.load(dir);
        check(custom.placePerSecond() == 4 && custom.breakPerSecond() == .5, "Settings are read, with a comma decimal accepted: " + custom);
        Files.writeString(file, "place_per_second=100\nbreak_per_second=fast\n");
        check(FrontierConfig.load(dir).equals(FrontierConfig.DEFAULTS), "Out-of-range or unreadable values fall back to 1");
        check(Files.readString(file).contains("=100"), "The player's file is never overwritten");
        check(FrontierConfig.load(null).equals(FrontierConfig.DEFAULTS), "No config folder: defaults");
    }

    static Camp meadow() throws Exception {
        var t = new Camp();
        t.found();
        t.world.blocks.put(STOCK, block("minecraft:chest", Map.of("type", "single"), true));
        t.c().stockpile = STOCK;
        t.world.food = 60;
        t.world.stock(STOCK, "minecraft:cobblestone", 78);
        t.world.stock(STOCK, "minecraft:oak_door", 1);
        t.world.stock(STOCK, "minecraft:red_bed", 1);
        return t;
    }

    /** Seconds from the start of BUILD to the finished cobblestone hut at a given place rate. */
    static int buildSeconds(double place) throws Exception {
        var t = meadow();
        t.sim.pace.configure(new FrontierConfig(place, 1));
        for (int i = 0; i < 1500 && (t.c().project == null || t.c().project.phase != Settlement.Project.Phase.BUILD); i++) t.seconds(1);
        check(t.c().project != null && t.c().project.phase == Settlement.Project.Phase.BUILD, "The site is cleared at rate " + place);
        int seconds = 0;
        while (t.c().project != null && seconds < 3000) { t.seconds(1); seconds++; }
        check(t.c().homesBuilt == 1, "The hut is finished at rate " + place);
        check(t.world.placedBlocks.size() == 80, "Every block placed exactly once at rate " + place + " (" + t.world.placedBlocks.size() + ")");
        return seconds;
    }

    static void paceFollowsTheSettings() throws Exception {
        int normal = buildSeconds(1), fast = buildSeconds(4), slow = buildSeconds(.5);
        check(fast < normal * 0.6, "4 blocks a second builds much faster (" + fast + "s vs " + normal + "s)");
        check(slow > normal * 1.5, "Half a block a second builds slower (" + slow + "s vs " + normal + "s)");
        var pace = new FrontierPace();
        var id = UUID.randomUUID();
        pace.configure(new FrontierConfig(.5, 1));
        pace.second(id);
        check(!pace.take(id, true), "At 0.5 nothing is placed in the first second");
        pace.second(id);
        check(pace.take(id, true) && !pace.take(id, true), "...and one block in the second");
        pace.configure(new FrontierConfig(3, 1));
        for (int i = 0; i < 10; i++) pace.second(id);
        int burst = 0;
        while (pace.take(id, true)) burst++;
        check(burst == 3, "Waiting never saves up a burst: at most one second's worth (" + burst + ")");
    }

    /** The play test: two beds placed, one settler, and status said every bed was claimed. */
    static void uncountedBedIsExplained() throws Exception {
        var t = new Camp();
        t.found();
        t.seconds(123);
        var sheltered = t.world.bed(new CodaBlockPos(4, 64, 3));
        t.sim.placed(new CodaBlockPlacements.Placement("minecraft:overworld", new CodaBlockPos(3, 64, 3), "minecraft:red_bed"));
        // A second bed under open sky.
        var open = new CodaBlockPos(9, 64, 3);
        t.world.blocks.put(open, block("minecraft:red_bed", Map.of("part", "head", "occupied", "false"), false));
        t.world.blocks.put(new CodaBlockPos(8, 64, 3), block("minecraft:red_bed", Map.of("part", "foot", "occupied", "false"), false));
        t.sim.placed(new CodaBlockPlacements.Placement("minecraft:overworld", new CodaBlockPos(8, 64, 3), "minecraft:red_bed"));
        t.seconds(2);
        check(t.c().beds.contains(sheltered) && !t.c().beds.contains(open), "Only the bed with a roof counts");
        check(t.world.announcements.stream().anyMatch(a -> a.contains("does not count for settlers yet") && a.contains("no roof")),
                "Placing a bed without a roof says so at once");
        check(t.sim.uncountedBeds.containsKey(open) && t.sim.uncountedBeds.get(open).contains("no roof 2-6 blocks above"), "The uncounted bed is remembered with its reason");
        t.seconds(5);
        check(t.sim.arrivalStatus.contains("give a roof to the bed at 9 64 3"), "Arrivals point at the bed to fix: " + t.sim.arrivalStatus);
        // The player adds a roof: the bed counts on the next re-check, and the note goes away.
        t.world.blocks.put(new CodaBlockPos(9, 67, 3), block("minecraft:oak_planks", Map.of(), true));
        t.sim.placed(new CodaBlockPlacements.Placement("minecraft:overworld", new CodaBlockPos(8, 64, 3), "minecraft:red_bed"));
        t.seconds(2);
        check(t.c().beds.contains(open) && !t.sim.uncountedBeds.containsKey(open), "With a roof the bed counts and is no longer listed");
    }
}
