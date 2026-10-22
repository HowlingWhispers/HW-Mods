package dev.howlingwhispers.frontier;

import dev.howlingwhispers.codaloader.api.*;
import java.nio.file.*;
import java.util.*;
import static dev.howlingwhispers.frontier.Fixtures.*;
import static dev.howlingwhispers.frontier.Settler.State.*;

/** The once-a-second update: founding, the pioneer, beds, arrivals, growth, death, absence and failures. */
public final class SimulationTest {
    public static void main(String[] args) throws Exception {
        foundingByCampfire();
        pioneer();
        bedsAndArrivals();
        growth();
        absenceDeathAndRecovery();
        foodAndDeparture();
        dismissedSettlersAreRemoved();
        failuresAreContained();
        unreadableStatePausesEverything();
        negativeCoordinates();
        finish("simulation checks");
    }

    static CodaBlockPlacements.Placement placed(CodaBlockPos p, String id) {
        return new CodaBlockPlacements.Placement("minecraft:overworld", p, id);
    }

    static void foundingByCampfire() throws Exception {
        var t = new Camp();
        t.world.blocks.put(CAMP, block("minecraft:campfire", Map.of("lit", "false"), false));
        t.sim.placed(placed(CAMP, "minecraft:campfire"));
        t.seconds(1);
        check(t.c() == null, "An unlit campfire founds nothing");
        t.world.blocks.put(CAMP, block("minecraft:campfire", Map.of("lit", "true"), false));
        t.sim.placed(placed(CAMP, "minecraft:campfire"));
        t.seconds(1);
        check(t.c() != null && t.c().campfire.equals(CAMP) && t.c().owner == null, "The first lit campfire founds an unclaimed settlement");
        check(t.c().territory().side() == 3, "A new settlement has 3x3 chunks");
        check(Files.exists(t.root.resolve("howl_frontier/frontier.properties")), "Founding is saved immediately");
        var second = new CodaBlockPos(5, 64, 5);
        t.world.blocks.put(second, block("minecraft:campfire", Map.of("lit", "true"), false));
        t.sim.placed(placed(second, "minecraft:campfire"));
        t.seconds(1);
        check(t.c().campfire.equals(CAMP) && t.sim.state.settlements.size() == 1, "Later campfires never move or replace the settlement");
    }

    static void pioneer() throws Exception {
        var t = new Camp();
        t.found();
        var stock = new CodaBlockPos(1, 64, 2);
        t.world.blocks.put(stock, block("minecraft:chest", Map.of("type", "single"), true));
        t.c().stockpile = stock; // fed, so the pioneer stays for the whole test
        t.seconds(59);
        check(t.world.spawned == 0 && t.sim.arrivalStatus.contains("pioneer arrives"), "No pioneer before 60 seconds, and status says it is coming");
        t.seconds(61);
        check(t.world.spawned == 1, "The pioneer arrives within 120 seconds");
        var p = t.c().settlers.values().iterator().next();
        check(p.pioneer && p.bed == null, "The pioneer needs no bed");
        int fromCampfire = Math.max(Math.abs(p.arrivalPoint.x() - CAMP.x()), Math.abs(p.arrivalPoint.z() - CAMP.z()));
        check(t.c().territory().contains(p.arrivalPoint) && fromCampfire >= 12, "The pioneer appears near the edge of the territory");
        t.seconds(2);
        check(p.state == RESIDENT && t.c().peakResidents() == 1, "The pioneer walks to the campfire and becomes resident 1");
        t.seconds(400);
        check(t.world.spawned == 1, "Without beds, no one else arrives");
        check(t.sim.arrivalStatus.contains("no sheltered beds"), "Status explains that a bed is needed");
        t.reopen();
        t.seconds(300);
        check(t.world.spawned == 1 && t.c().settlers.size() == 1, "Reopening the world never sends a second pioneer");
        check(t.c().settler(p.id).orElseThrow().state == RESIDENT, "The pioneer is still resident after reopening");
        check(t.world.food < 5, "The pioneer ate real food from the stockpile");
        // Losing everyone must never leave the settlement unable to recruit.
        t.world.damage(p.id, 20);
        t.seconds(1);
        check(t.c().settler(p.id).orElseThrow().state == DEAD && t.sim.arrivalStatus.contains("new pioneer"), "An empty camp calls a new pioneer");
        t.seconds(300);
        check(t.world.spawned == 2 && t.c().settlers.values().stream().filter(s -> s.pioneer && !s.terminal()).count() == 1, "A new pioneer arrives without a bed");
    }

    static void bedsAndArrivals() throws Exception {
        var t = new Camp();
        t.found();
        t.seconds(121); // pioneer committed and arriving
        t.seconds(2);
        var head = t.world.bed(new CodaBlockPos(4, 64, 3));
        t.sim.placed(placed(new CodaBlockPos(3, 64, 3), "minecraft:red_bed")); // placement reports the foot
        t.seconds(1);
        check(t.c().beds.contains(head), "A placed bed is registered at once, from its foot");
        t.seconds(1);
        var pioneer = t.c().settlers.values().iterator().next();
        check(head.equals(pioneer.bed), "The pioneer takes the first free bed");
        check(t.sim.arrivalStatus.contains("every sheltered bed is claimed"), "Status explains that all beds are taken");
        var second = t.world.bed(new CodaBlockPos(8, 64, 3));
        t.sim.placed(placed(new CodaBlockPos(7, 64, 3), "minecraft:red_bed"));
        t.seconds(299);
        check(t.world.spawned == 1, "The next settler waits for the arrival interval");
        t.seconds(2);
        check(t.world.spawned == 2, "A free sheltered bed brings the next settler");
        var newcomer = t.c().settlers.values().stream().filter(s -> !s.pioneer).findFirst().orElseThrow();
        check(second.equals(newcomer.bed), "The newcomer's bed is reserved before they appear");
        var outside = t.world.bed(new CodaBlockPos(60, 64, 3));
        t.sim.placed(placed(new CodaBlockPos(59, 64, 3), "minecraft:red_bed"));
        t.seconds(1);
        check(!t.c().beds.contains(outside), "Beds outside the territory are ignored");
        t.world.blocks.remove(new CodaBlockPos(8, 67, 3)); // remove the roof
        t.seconds(5);
        check(!t.c().beds.contains(second), "An unroofed bed stops counting");
    }

    static void growth() throws Exception {
        var t = new Camp();
        t.found();
        for (int i = 0; i < 4; i++) t.add("Resident " + i, RESIDENT, t.world.bed(new CodaBlockPos(2 + 2 * i, 64, 6)), true);
        t.c().recordResidents();
        var fifthBed = t.world.bed(new CodaBlockPos(10, 64, 6));
        var fifth = t.add("Fifth", ARRIVING, fifthBed, true);
        t.c().pioneerCommitted = true;
        t.seconds(1);
        check(fifth.state == RESIDENT && t.c().territory().side() == 5, "The fifth resident unlocks 5x5");
        check(t.world.announcements.stream().anyMatch(a -> a.contains("5x5")), "Players are told the land grew");
        t.world.damage(fifth.id, 20);
        var another = t.c().settlers.values().iterator().next();
        t.world.damage(another.id, 20);
        t.seconds(1);
        check(fifth.state == DEAD && another.state == DEAD && t.c().territory().side() == 5, "Deaths never shrink the land");
        t.reopen();
        check(t.c().territory().side() == 5 && t.c().peakResidents() == 5, "Unlocked land survives reopening");
    }

    static void absenceDeathAndRecovery() throws Exception {
        var t = new Camp();
        t.found();
        t.c().pioneerCommitted = true;
        var bed = t.world.bed(new CodaBlockPos(4, 64, 3));
        t.c().beds.add(bed);
        var absent = t.add("Away", RESIDENT, bed, false);
        t.c().recordResidents();
        t.seconds(400);
        check(absent.state == RESIDENT && absent.hunger == 80 && bed.equals(absent.bed), "An absent settler keeps their bed and does not get hungry");
        check(t.world.spawned == 0, "Absence never brings a replacement");
        t.world.put(absent.id, bed, 20);
        t.world.damage(absent.id, 20);
        t.seconds(1);
        check(absent.state == DEAD && absent.bed == null, "An observed death is recorded and frees the bed");
        t.c().arrivalTimer = 20;
        t.seconds(2);
        check(t.world.spawned == 1, "Arrivals resume after a death");
    }

    static void foodAndDeparture() throws Exception {
        var t = new Camp();
        t.found();
        t.c().pioneerCommitted = true;
        var stock = new CodaBlockPos(1, 64, 2);
        t.world.blocks.put(stock, block("minecraft:chest", Map.of("type", "single"), true));
        t.c().stockpile = stock;
        var bed = t.world.bed(new CodaBlockPos(4, 64, 3));
        t.c().beds.add(bed);
        var s = t.add("Hungry", RESIDENT, bed, true);
        t.c().recordResidents();
        s.hunger = 50;
        t.world.put(s.id, stock, 20);
        int food = t.world.food;
        t.seconds(1);
        check(s.hunger == 90 && t.world.food == food - 1, "A hungry resident eats one real item from the stockpile");
        t.world.food = 0;
        s.loyalty = 20;
        s.hunger = 31;
        for (int i = 0; i < 15 && s.state == RESIDENT; i++) t.seconds(1);
        check(s.state == LEAVING && s.bed == null, "A disloyal resident leaves in a shortage and frees the bed");
        t.seconds(2);
        check(s.state == DEPARTED && t.world.actors.get(s.id) == null, "They walk out of the territory and are removed");
    }

    static void dismissedSettlersAreRemoved() throws Exception {
        var t = new Camp();
        t.found();
        UUID loaded = UUID.randomUUID(), away = UUID.randomUUID();
        t.world.put(loaded, new CodaBlockPos(3, 64, 3), 20);
        t.sim.state.dismissed.addAll(List.of(loaded, away));
        t.seconds(1);
        check(!t.world.actors.containsKey(loaded) && !t.sim.state.dismissed.contains(loaded), "A dismissed settler who is loaded is removed");
        check(t.sim.state.dismissed.contains(away), "A dismissed settler who is away is removed when next seen");
        t.world.put(away, new CodaBlockPos(3, 64, 3), 20);
        t.seconds(1);
        check(!t.world.actors.containsKey(away) && t.sim.state.dismissed.isEmpty(), "...and is removed as soon as they load");
    }

    static void failuresAreContained() throws Exception {
        var t = new Camp();
        t.found();
        t.c().pioneerCommitted = true;
        var broken = t.add("Broken", RESIDENT, null, true);
        var healthy = t.add("Healthy", RESIDENT, null, true);
        t.c().recordResidents();
        t.world.broken = broken.id;
        t.seconds(15); // three consecutive failures would make the loader disable Frontier
        check(healthy.hunger < 80, "One failing settler never stops the others");
        check(broken.activity.contains("Update failed"), "The failing settler is marked");
        check(t.sim.lastError.contains("Broken") && t.sim.errorCount >= 15, "The failure is recorded for status");
        check(t.c().elapsed == 15 * 20, "Camp time keeps advancing");
    }

    static void unreadableStatePausesEverything() throws Exception {
        var t = new Camp();
        t.found();
        Path file = t.root.resolve("howl_frontier/frontier.properties");
        Files.writeString(file, Files.readString(file).replace("schema=2", "schema=7"));
        byte[] corrupt = Files.readAllBytes(file);
        t.reopen();
        check(t.sim.loadError != null && t.c() == null, "Unreadable state pauses the settlement");
        var fire = new CodaBlockPos(4, 64, 4);
        t.world.blocks.put(fire, block("minecraft:campfire", Map.of("lit", "true"), false));
        t.sim.placed(placed(fire, "minecraft:campfire"));
        t.seconds(5);
        check(Arrays.equals(Files.readAllBytes(file), corrupt) && t.c() == null, "A new campfire never replaces unreadable state");
    }

    static void negativeCoordinates() throws Exception {
        var t = new Camp();
        var fire = new CodaBlockPos(-1, 64, -1);
        t.world.blocks.remove(CAMP);
        t.world.blocks.put(fire, block("minecraft:campfire", Map.of("lit", "true"), false));
        t.sim.open(t.view, t.session);
        t.sim.establish(t.world, fire, null);
        check(t.c().territory().centre().equals(new ChunkPos(-1, -1)), "A campfire at -1,-1 is centred on chunk -1,-1");
        t.seconds(121);
        var p = t.c().settlers.values().iterator().next();
        check(t.c().territory().contains(p.arrivalPoint), "The pioneer arrives inside a negative-coordinate territory");
    }
}
