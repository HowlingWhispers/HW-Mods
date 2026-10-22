package dev.howlingwhispers.frontier;

import dev.howlingwhispers.codaloader.api.CodaBlockPos;
import static dev.howlingwhispers.frontier.Fixtures.*;
import static dev.howlingwhispers.frontier.Settler.State.*;

/**
 * Arrivals on realistic ground. The fake world refuses spawns exactly as CodaLoader does
 * (air at feet and head, solid dry floor), which is what 0.2.0's tests did not model:
 * in Nightly, the pioneer never appeared on grassy ground.
 */
public final class ArrivalTest {
    public static void main(String[] args) throws Exception {
        grassyMeadow();
        hillsAtTheEdge();
        refusedSpotIsNotRetriedForever();
        noGroundAtAll();
        stuckWorldFrom020Recovers();
        finish("arrival checks on realistic ground");
    }

    static Settler pioneer(Camp t) {
        return t.c().settlers.values().stream().filter(s -> s.pioneer).findFirst().orElse(null);
    }

    /** Most of the ground has tall grass, which a settler can walk through but the loader will not spawn into. */
    static void grassyMeadow() throws Exception {
        var t = new Camp();
        t.world.grass = (x, z) -> Math.floorMod(x + z, 5) != 0; // about 80% grass
        t.found();
        t.seconds(140);
        check(t.world.spawned == 1, "The pioneer appears on grassy ground (refused " + t.world.refusedSpawns.size() + " times)");
        var p = pioneer(t);
        check(p != null && p.state != COMMITTED, "The pioneer is no longer stuck waiting to appear");
        check(t.world.block(p.arrivalPoint).orElseThrow().air(), "The arrival spot is clear air, not grass");
    }

    /** The edge of the land is six blocks higher than the campfire. */
    static void hillsAtTheEdge() throws Exception {
        var t = new Camp();
        t.world.ground = (x, z) -> Math.max(Math.abs(x), Math.abs(z)) >= 12 ? 69 : 63;
        t.found();
        t.seconds(140);
        check(t.world.spawned == 1, "The pioneer appears on a hill at the edge of the land");
    }

    /** The game refuses the first spot for a reason the rule cannot see (say, an entity standing there). */
    static void refusedSpotIsNotRetriedForever() throws Exception {
        var t = new Camp();
        t.world.collides = p -> t.world.refusedSpawns.isEmpty() || t.world.refusedSpawns.contains(p);
        t.found();
        for (int i = 0; i < 130 && t.world.refusedSpawns.isEmpty(); i++) t.seconds(1); // the pioneer's time is random within 60-120 s
        check(t.world.refusedSpawns.size() == 1 && t.world.spawned == 0, "The first spawn attempt was refused");
        check(t.sim.arrivalStatus.contains("Could not appear"), "Status explains the refused spot: " + t.sim.arrivalStatus);
        t.seconds(15);
        check(t.world.spawned == 1, "The pioneer appears at another spot after a refusal");
        check(t.world.refusedSpawns.size() == 1, "The refused spot is never tried again");
    }

    /** Water everywhere near the edge: nobody can arrive, and the player is told why. */
    static void noGroundAtAll() throws Exception {
        var t = new Camp();
        t.world.flooded = p -> Math.max(Math.abs(p.x()), Math.abs(p.z())) >= 8 && p.y() <= 63;
        t.found();
        t.seconds(140);
        check(t.world.spawned == 0 && t.c().settlers.isEmpty(), "Nobody is committed without a place to appear");
        check(t.sim.arrivalStatus.contains("flat, dry"), "Status says to clear flat, dry ground: " + t.sim.arrivalStatus);
    }

    /** A 0.2.0 world: the pioneer was committed at a grassy spot and saved there. 0.2.1 must recover it. */
    static void stuckWorldFrom020Recovers() throws Exception {
        var t = new Camp();
        t.world.grass = (x, z) -> Math.floorMod(x + z, 5) != 0;
        t.found();
        var stuck = new Settler(java.util.UUID.randomUUID(), t.c().nextName());
        stuck.pioneer = true;
        stuck.arrivalPoint = new CodaBlockPos(28, 64, 1); // tall grass at the feet: refused by the game
        check(!t.world.loaderStandable(stuck.arrivalPoint), "The saved 0.2.0 spot is one the game refuses");
        t.c().settlers.put(stuck.id, stuck);
        t.c().pioneerCommitted = true;
        t.sim.save();
        t.reopen();
        t.seconds(20);
        check(t.world.spawned == 1 && t.c().settler(stuck.id).orElseThrow().state != COMMITTED, "The stuck pioneer appears within about 20 seconds");
        check(t.c().settlers.size() == 1, "No second pioneer is created");
    }
}
