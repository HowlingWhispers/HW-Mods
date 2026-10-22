package dev.howlingwhispers.frontier;

import dev.howlingwhispers.codaloader.api.CodaBlockPos;
import java.util.*;
import static dev.howlingwhispers.frontier.Fixtures.*;
import static dev.howlingwhispers.frontier.Settler.State.*;

/** Territory maths, settler lifecycle and settlement rules. No world needed. */
public final class CoreTest {
    public static void main(String[] args) {
        chunks();
        territoryTable();
        territoryEdges();
        lifecycle();
        hungerAndLoyalty();
        settlementRules();
        facing();
        finish("core territory, lifecycle and settlement checks");
    }

    static void chunks() {
        check(ChunkPos.of(new CodaBlockPos(0, 64, 0)).equals(new ChunkPos(0, 0)), "Block 0 is chunk 0");
        check(ChunkPos.of(new CodaBlockPos(15, 64, 15)).equals(new ChunkPos(0, 0)), "Block 15 is still chunk 0");
        check(ChunkPos.of(new CodaBlockPos(16, 64, 16)).equals(new ChunkPos(1, 1)), "Block 16 is chunk 1");
        check(ChunkPos.of(new CodaBlockPos(-1, 64, -1)).equals(new ChunkPos(-1, -1)), "Block -1 is chunk -1, not 0 (floor division)");
        check(ChunkPos.of(new CodaBlockPos(-16, 64, -16)).equals(new ChunkPos(-1, -1)), "Block -16 is chunk -1");
        check(ChunkPos.of(new CodaBlockPos(-17, 64, -17)).equals(new ChunkPos(-2, -2)), "Block -17 is chunk -2");
    }

    static void territoryTable() {
        int[][] expected = {{0, 3}, {4, 3}, {5, 5}, {9, 5}, {10, 7}, {14, 7}, {15, 9}, {19, 9}, {20, 11}, {24, 11}, {25, 13}, {29, 13}, {30, 15}, {34, 15}};
        for (int[] row : expected) {
            var t = Territory.around(CAMP, row[0]);
            check(t.side() == row[1], row[0] + " residents give " + row[1] + "x" + row[1] + " (got " + t.side() + ")");
            check(t.chunkCount() == row[1] * row[1], "Chunk count for " + row[0] + " residents");
            check(t.sideBlocks() == row[1] * 16, "Block width for " + row[0] + " residents");
        }
        check(Territory.nextRingAt(0) == 5 && Territory.nextRingAt(4) == 5 && Territory.nextRingAt(5) == 10, "Next ring thresholds");
        try {
            Territory.milestoneFor(-1);
            throw new AssertionError("Negative residents accepted");
        } catch (IllegalArgumentException expectedFailure) { checks++; }
    }

    static void territoryEdges() {
        var t = Territory.around(new CodaBlockPos(-1, 64, -1), 0); // centre chunk (-1,-1), so chunks -2..0
        check(t.contains(new CodaBlockPos(15, 64, 15)), "Edge block 15 (chunk 0) is inside");
        check(!t.contains(new CodaBlockPos(16, 64, 0)), "Block 16 (chunk 1) is outside");
        check(t.contains(new CodaBlockPos(-32, 64, -32)), "Block -32 (chunk -2) is inside");
        check(!t.contains(new CodaBlockPos(-33, 64, 0)), "Block -33 (chunk -3) is outside");
        check(t.minBlockX() == -32 && t.maxBlockX() == 15, "Block bounds of a negative territory");
        var grown = t.atLeast(1);
        check(grown.side() == 5 && grown.centre().equals(t.centre()), "Growth keeps the campfire chunk as centre");
        check(grown.atLeast(0) == grown, "Territory never shrinks");
        check(grown.contains(new CodaBlockPos(31, 64, 31)) && !grown.contains(new CodaBlockPos(32, 64, 0)), "The new ring is exactly one chunk wider on every side");
    }

    static void lifecycle() {
        var s = new Settler(UUID.randomUUID(), "Asta");
        s.bed = new CodaBlockPos(3, 64, 0);
        check(s.state == COMMITTED && s.present() && !s.resident(), "New settlers are committed, not yet resident");
        s.moveTo(ARRIVING);
        s.moveTo(RESIDENT);
        check(s.resident() && s.bed != null, "Residents keep their bed");
        try {
            s.moveTo(COMMITTED);
            throw new AssertionError("Illegal transition accepted");
        } catch (IllegalStateException expected) { checks++; }
        s.moveTo(DEAD);
        check(s.terminal() && s.bed == null, "Death releases the bed");
        for (var next : Settler.State.values()) check(!Settler.allowed(DEAD, next), "Nothing follows death: " + next);
        var leaver = new Settler(UUID.randomUUID(), "Bryn");
        leaver.bed = new CodaBlockPos(4, 64, 0);
        leaver.moveTo(ARRIVING);
        leaver.moveTo(RESIDENT);
        leaver.moveTo(LEAVING);
        check(leaver.bed == null && !leaver.present(), "Leaving releases the bed immediately");
        check(!Settler.allowed(COMMITTED, DEAD), "An unspawned settler cannot be observed dead");
        check(Settler.allowed(COMMITTED, RETIRED), "An unspawned settler can be retired");
    }

    static void hungerAndLoyalty() {
        var low = resident(20, 30);
        check(low.hungerStep() && low.state == LEAVING, "Low loyalty leaves early in a shortage");
        var medium = resident(55, 30);
        check(!medium.hungerStep(), "Medium loyalty endures the first shortage");
        for (int i = 0; i < 25 && medium.state == RESIDENT; i++) medium.hungerStep();
        check(medium.state == LEAVING, "Medium loyalty eventually leaves");
        var loyal = resident(90, 1);
        for (int i = 0; i < 10; i++) loyal.hungerStep();
        check(loyal.state == RESIDENT && loyal.hunger == 0, "Loyal residents stay, even at zero food");
        loyal.meal();
        check(loyal.hunger == Settler.MEAL, "A meal restores hunger");
    }

    static Settler resident(double loyalty, double hunger) {
        var s = new Settler(UUID.randomUUID(), "S");
        s.state = RESIDENT;
        s.loyalty = loyalty;
        s.hunger = hunger;
        return s;
    }

    static void settlementRules() {
        var c = new Settlement(UUID.randomUUID(), null, CAMP);
        check(c.territory().side() == 3, "A new settlement starts at 3x3");
        for (int i = 0; i < 4; i++) addResident(c);
        check(!c.recordResidents() && c.territory().side() == 3, "Four residents do not unlock a ring");
        addResident(c);
        check(c.recordResidents() && c.territory().side() == 5, "The fifth resident unlocks 5x5");
        c.settlers.values().iterator().next().moveTo(DEAD);
        c.settlers.values().stream().skip(1).findFirst().orElseThrow().moveTo(LEAVING);
        check(!c.recordResidents() && c.territory().side() == 5 && c.peakResidents() == 5, "Losing residents never shrinks the land");
        var arriving = new Settler(UUID.randomUUID(), "Arriving");
        arriving.state = ARRIVING;
        c.settlers.put(arriving.id, arriving);
        c.recordResidents();
        check(c.peakResidents() == 5, "Arriving settlers do not count until they are residents");
        Set<String> names = new HashSet<>();
        var fresh = new Settlement(UUID.randomUUID(), null, CAMP);
        for (int i = 0; i < 100; i++) {
            var s = new Settler(UUID.randomUUID(), fresh.nextName());
            fresh.settlers.put(s.id, s);
            names.add(s.name.toLowerCase(Locale.ROOT));
        }
        check(names.size() == 100, "Settler names never repeat, even past the name list");
        var bedA = new CodaBlockPos(3, 64, 0);
        var outside = new CodaBlockPos(200, 64, 0);
        fresh.beds.add(bedA);
        fresh.beds.add(outside);
        check(fresh.freeBeds().equals(List.of(bedA)), "Beds outside the territory are never offered");
    }

    static void addResident(Settlement c) {
        var s = new Settler(UUID.randomUUID(), c.nextName());
        s.state = RESIDENT;
        c.settlers.put(s.id, s);
    }

    static void facing() {
        check(FrontierJobs.facing(0).equals("south") && FrontierJobs.facing(90).equals("west") && FrontierJobs.facing(180).equals("north")
                && FrontierJobs.facing(270).equals("east") && FrontierJobs.facing(-90).equals("east") && FrontierJobs.facing(-170).equals("north"), "Minecraft yaw maps to compass directions");
        check(FrontierJobs.saplingFor("minecraft:oak_log").equals("minecraft:oak_sapling") && FrontierJobs.saplingFor("minecraft:mangrove_log").equals("minecraft:mangrove_propagule")
                && FrontierJobs.saplingFor("minecraft:warped_stem") == null, "Each log maps to its sapling");
        check(!FrontierJobs.isLog("minecraft:stripped_oak_log") && FrontierJobs.isLog("minecraft:cherry_log"), "Stripped logs are never trees");
    }
}
