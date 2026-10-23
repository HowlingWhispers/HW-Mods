package dev.howlingwhispers.frontier;

import dev.howlingwhispers.codaloader.api.*;
import java.util.*;
import static dev.howlingwhispers.frontier.Fixtures.*;
import static dev.howlingwhispers.frontier.Settler.State.*;

/** Getting around when native pathfinding is short-sighted, refuses routes or leaves a settler wedged. */
public final class WalkingTest {
    public static void main(String[] args) throws Exception {
        shortSightedPathfindingStillReachesCamp();
        wedgedSettlerStepsAside();
        hopelessArrivalIsBroughtIn();
        plantsAreWalkable();
        finish("walking checks");
    }

    static Settler pioneer(Camp t) {
        return t.c().settlers.values().stream().filter(s -> s.pioneer && !s.terminal()).findFirst().orElse(null);
    }

    static void until(Camp t, int seconds, java.util.function.BooleanSupplier done) {
        for (int i = 0; i < seconds && !done.getAsBoolean(); i++) t.seconds(1);
    }

    static void shortSightedPathfindingStillReachesCamp() throws Exception {
        var t = new Camp();
        t.found();
        t.world.walkRange = 9; // routes longer than 9 blocks are refused
        until(t, 400, () -> pioneer(t) != null && pioneer(t).state == RESIDENT);
        check(pioneer(t) != null && pioneer(t).state == RESIDENT, "The pioneer reaches the campfire in short hops: " + (pioneer(t) == null ? "none" : pioneer(t).activity));
        check(t.world.announcements.stream().noneMatch(a -> a.contains("found a way through")), "No rescue was needed");
    }

    static void wedgedSettlerStepsAside() throws Exception {
        var t = new Camp();
        t.found();
        until(t, 400, () -> pioneer(t) != null && pioneer(t).state == ARRIVING);
        var p = pioneer(t);
        var a = t.world.actor(p.id).orElseThrow();
        t.world.wedged.add(new CodaBlockPos((int) Math.floor(a.x()), (int) Math.floor(a.y()), (int) Math.floor(a.z())));
        until(t, 60, () -> p.state == RESIDENT);
        check(p.state == RESIDENT, "A wedged arrival steps aside by itself and walks in: " + p.activity);
        check(t.world.wedgedWalks >= FrontierSimulation.STUCK && t.world.wedgedWalks < 20, "It noticed within seconds (" + t.world.wedgedWalks + " wasted paths)");
    }

    static void hopelessArrivalIsBroughtIn() throws Exception {
        var t = new Camp();
        t.found();
        until(t, 400, () -> pioneer(t) != null && pioneer(t).state == ARRIVING);
        var p = pioneer(t);
        var a = t.world.actor(p.id).orElseThrow();
        // Every long route is refused from here and nothing short helps: completely wedged.
        t.world.walkRange = 0;
        until(t, FrontierSimulation.RESCUE + 20, () -> p.state == RESIDENT);
        check(p.state == RESIDENT, "After " + FrontierSimulation.RESCUE + "s without any way in, the arrival is brought to the campfire: " + p.activity);
        check(t.world.announcements.stream().anyMatch(x -> x.contains("found a way through to the campfire")), "Players are told");
        check(t.world.actor(p.id).isPresent(), "The settler exists after being brought in");
    }

    static void plantsAreWalkable() throws Exception {
        var t = new Camp();
        t.found();
        var c = t.c().campfire;
        for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++)
            if (Math.abs(x) + Math.abs(z) > 0) t.world.blocks.put(new CodaBlockPos(c.x() + x, c.y(), c.z() + z), block(x % 2 == 0 ? "minecraft:fern" : "minecraft:poppy", Map.of(), false));
        check(FrontierSimulation.standing(t.world, c, 2) != null, "Settlers can stand among ferns and flowers by the campfire");
        check(FrontierSimulation.standing(t.world, new CodaBlockPos(c.x() + 30, c.y(), c.z()), 0) != null, "Open ground is standable");
        t.world.blocks.put(new CodaBlockPos(c.x() + 30, c.y(), c.z()), block("minecraft:sweet_berry_bush", Map.of(), false));
        check(FrontierSimulation.standing(t.world, new CodaBlockPos(c.x() + 30, c.y(), c.z()), 0) == null, "Berry bushes are avoided");
        until(t, 400, () -> pioneer(t) != null && pioneer(t).state == RESIDENT);
        check(pioneer(t) != null && pioneer(t).state == RESIDENT, "The pioneer reaches a campfire ringed with flowers");
    }
}
