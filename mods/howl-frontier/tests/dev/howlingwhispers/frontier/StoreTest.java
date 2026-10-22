package dev.howlingwhispers.frontier;

import dev.howlingwhispers.codaloader.api.CodaBlockPos;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import static dev.howlingwhispers.frontier.Fixtures.*;
import static dev.howlingwhispers.frontier.Settler.State.*;

/** Schema 2 persistence: round trips, validation and never overwriting what cannot be read. */
public final class StoreTest {
    public static void main(String[] args) throws Exception {
        roundTrip();
        validation();
        unreadableIsLeftAlone();
        oldStateIgnored();
        finish("persistence checks");
    }

    static FrontierState sample() {
        var state = new FrontierState();
        var c = new Settlement(UUID.randomUUID(), UUID.randomUUID(), new CodaBlockPos(-5, 70, -5));
        c.stockpile = new CodaBlockPos(-3, 70, -4);
        c.elapsed = 12_340;
        c.arrivalTimer = 400;
        c.pioneerCommitted = true;
        c.beds.add(new CodaBlockPos(-8, 70, -2));
        c.keptBeds.add(new CodaBlockPos(-9, 70, -6));
        var s = new Settler(UUID.randomUUID(), c.nextName());
        s.state = RESIDENT;
        s.pioneer = true;
        s.profession = Settler.Profession.LUMBERJACK;
        s.bed = new CodaBlockPos(-8, 70, -2);
        s.arrivalPoint = new CodaBlockPos(10, 70, -5);
        s.hunger = 61.5;
        s.loyalty = 72.25;
        s.hungerTicks = 140;
        c.settlers.put(s.id, s);
        var dead = new Settler(UUID.randomUUID(), c.nextName());
        dead.state = DEAD;
        dead.arrivalPoint = new CodaBlockPos(-20, 70, -5);
        c.settlers.put(dead.id, dead);
        c.recordResidents();
        state.settlements.add(c);
        state.dismissed.add(UUID.randomUUID());
        return state;
    }

    static void roundTrip() throws Exception {
        Path world = Files.createTempDirectory("frontier-store-");
        var store = new FrontierStore(world);
        check(store.load().settlements.isEmpty(), "A world without a file starts empty");
        var state = sample();
        store.save(state);
        var back = new FrontierStore(world).load();
        var a = state.primary();
        var b = back.primary();
        check(b.id.equals(a.id) && b.owner.equals(a.owner) && b.campfire.equals(a.campfire), "Identity, owner and campfire survive");
        check(b.peakResidents() == 1 && b.territory().equals(a.territory()), "Peak and territory survive");
        check(b.stockpile.equals(a.stockpile) && b.elapsed == a.elapsed && b.arrivalTimer == a.arrivalTimer && b.pioneerCommitted, "Camp progress survives");
        check(b.beds.equals(a.beds) && b.keptBeds.equals(a.keptBeds) && b.namesIssued == a.namesIssued, "Beds and name counter survive");
        check(back.dismissed.equals(state.dismissed), "Dismissed settler IDs survive");
        var s = a.settlers.values().iterator().next();
        var t = b.settler(s.id).orElseThrow();
        check(t.name.equals(s.name) && t.state == RESIDENT && t.pioneer && t.profession == s.profession, "Settler identity and role survive");
        check(t.bed.equals(s.bed) && t.arrivalPoint.equals(s.arrivalPoint) && t.hunger == s.hunger && t.loyalty == s.loyalty && t.hungerTicks == s.hungerTicks, "Settler needs survive exactly");
        check(new ArrayList<>(b.settlers.keySet()).equals(new ArrayList<>(a.settlers.keySet())), "Settler order survives");
        store.save(back);
        check(Files.exists(world.resolve("howl_frontier/" + FrontierStore.PREVIOUS)), "Saving keeps the previous version");
    }

    static void validation() throws Exception {
        rejects("a bed outside the territory", p -> p.setProperty("c0.beds.0", "500,70,500"));
        rejects("a stockpile outside the territory", p -> p.setProperty("c0.stockpile", "-500,70,0"));
        rejects("two settlers in one bed", p -> {
            p.setProperty("c0.s1.state", "RESIDENT");
            p.setProperty("c0.s1.bed", p.getProperty("c0.s0.bed"));
            p.setProperty("c0.peakResidents", "2");
        });
        rejects("a dead settler holding a bed", p -> p.setProperty("c0.s1.bed", "-7,70,-2"));
        rejects("a duplicate settler ID", p -> p.setProperty("c0.s1.id", p.getProperty("c0.s0.id")));
        rejects("a duplicate name", p -> p.setProperty("c0.s1.name", p.getProperty("c0.s0.name").toUpperCase(Locale.ROOT)));
        rejects("more residents than the stored peak", p -> p.setProperty("c0.peakResidents", "0"));
        rejects("an unknown schema", p -> p.setProperty("schema", "3"));
        rejects("hunger above 100", p -> p.setProperty("c0.s0.hunger", "101"));
        rejects("a missing field", p -> p.remove("c0.s0.loyalty"));
        rejects("a position below the world", p -> p.setProperty("c0.stockpile", "-3,-65,-4"));
        rejects("an unknown state", p -> p.setProperty("c0.s0.state", "ZOMBIE"));
        var bad = sample();
        bad.primary().settlers.values().iterator().next().bed = new CodaBlockPos(900, 70, 900);
        try {
            new FrontierStore(Files.createTempDirectory("frontier-store-bad-")).save(bad);
            throw new AssertionError("Invalid state was written");
        } catch (IllegalArgumentException expected) { checks++; }
    }

    interface Edit { void apply(Properties p); }

    static void rejects(String what, Edit edit) {
        var p = FrontierStore.encode(sample());
        edit.apply(p);
        try {
            FrontierStore.decode(p);
            throw new AssertionError("Accepted " + what);
        } catch (RuntimeException expected) {
            checks++;
        }
    }

    static void unreadableIsLeftAlone() throws Exception {
        Path world = Files.createTempDirectory("frontier-store-corrupt-");
        var store = new FrontierStore(world);
        store.save(sample());
        Path file = world.resolve("howl_frontier/" + FrontierStore.FILE);
        Files.writeString(file, Files.readString(file).replace("schema=2", "schema=9"));
        byte[] corrupt = Files.readAllBytes(file);
        try {
            store.load();
            throw new AssertionError("Corrupt state loaded");
        } catch (IOException expected) {
            check(expected.getMessage().contains("left unchanged"), "The error says the file was not changed");
        }
        check(Arrays.equals(Files.readAllBytes(file), corrupt), "Loading never rewrites a corrupt file");
    }

    static void oldStateIgnored() throws Exception {
        Path world = Files.createTempDirectory("frontier-store-old-");
        Path old = world.resolve("howl_frontier/settlement.properties");
        Files.createDirectories(old.getParent());
        Files.writeString(old, "schema=1\ncamp=0,64,0\n");
        var store = new FrontierStore(world);
        check(store.load().settlements.isEmpty(), "0.1.x state is ignored, not migrated");
        store.save(sample());
        check(Files.readString(old).equals("schema=1\ncamp=0,64,0\n"), "0.1.x state is never changed or deleted");
    }
}
