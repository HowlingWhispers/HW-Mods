package dev.howlingwhispers.buildcraft;

import dev.howlingwhispers.codaloader.api.CodaBlockPlacements;
import dev.howlingwhispers.codaloader.api.CodaBlockPos;
import dev.howlingwhispers.codaloader.api.CodaInventoryView;
import dev.howlingwhispers.codaloader.api.CodaServerTickContext;
import dev.howlingwhispers.codaloader.api.CodaWorldView;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * First server-authoritative BuildCraft 8.0 transport gameplay slice.
 *
 * Redstone-powered engine next to wooden extraction pipe, followed by
 * connected cobblestone transport pipes. Original art and real native blocks,
 * never virtual/glass placeholders. ItemStacks are moved by the loaded vanilla
 * chest/barrel adapter. Bounded to 16 pipes and 16 items per engine pulse.
 *
 * Next ports still needed: source-compatible pipe TileEntity packet motion,
 * engine temperature/energy states, persistence of powered engine tracking,
 * wrench interactions and in-pipe animations. Nothing is replicated to a
 * separate virtual chest.
 */
public final class BuildCraftEngineRuntime {
    private record Engine(String dimension, CodaBlockPos position) {}
    private static final String WOOD = "buildcrafttransport:wood_item";
    private static final String COBBLE = "buildcrafttransport:cobblestone_item";
    private static final String ENGINE = "buildcraftcore:engine_redstone";
    private static final int MAX_HOPS = 16;
    private static final int ITEMS_PER_PULSE = 16;
    private static final int[][] NEIGHBORS = {
        {1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}
    };

    private final Set<Engine> engines = new HashSet<>();
    private String activeSession = "";
    private int lastMoved;

    public synchronized void onPlacement(CodaBlockPlacements.Placement placed) {
        if (ENGINE.equals(placed.blockId()))
            engines.add(new Engine(placed.dimension(), placed.position()));
    }

    /**
     * Server callback only. Never loads chunks, never generates terrain, never
     * writes unless ALL pipe positions and endpoints are checked first.
     */
    public synchronized void onServerTick(CodaServerTickContext tick) throws Exception {
        if (!activeSession.equals(tick.sessionId())) {
            // Never let a previous world's engine list affect a new world.
            engines.clear();
            activeSession = tick.sessionId();
        }
        if (tick.tick() % 20 != 0 || tick.world().isEmpty()) return;
        CodaWorldView world = tick.world().get();
        List<Engine> snapshots = List.copyOf(engines);
        int moved = 0;
        for (Engine engine : snapshots) {
            if (!world.isChunkLoaded(engine.dimension(), engine.position())) continue;
            if (!world.isBlock(engine.dimension(), engine.position(), ENGINE)) {
                engines.remove(engine);
                continue;
            }
            if (!world.hasNeighborSignal(engine.dimension(), engine.position())) continue;
            moved += pulseEngine(world, engine);
        }
        lastMoved = moved;
        if (moved > 0)
            System.out.println("[BuildCraft] Original redstone engine pulsed " + moved
                    + " native Minecraft item(s) through placed BuildCraft pipes.");
    }

    public synchronized int lastMoved() { return lastMoved; }

    private int pulseEngine(CodaWorldView world, Engine engine) throws Exception {
        String dim = engine.dimension();
        for (CodaBlockPos wood : adjacent(engine.position())) {
            if (!world.isChunkLoaded(dim, wood) || !world.isBlock(dim, wood, WOOD)) continue;
            // Extraction requires a nonempty source container *next to wood*.
            for (CodaBlockPos source : adjacent(wood)) {
                var found = world.inventory(dim, source);
                if (found.isEmpty() || !hasItems(found.get())) continue;
                CodaBlockPos destination = findTarget(world, dim, wood, source);
                if (destination != null) {
                    return world.transfer(dim, source, destination, ITEMS_PER_PULSE);
                }
            }
        }
        return 0;
    }

    private CodaBlockPos findTarget(CodaWorldView world, String dim, CodaBlockPos wood,
                                    CodaBlockPos source) throws Exception {
        record Step(CodaBlockPos pos, int pipes) {}
        Set<CodaBlockPos> visited = new HashSet<>();
        ArrayDeque<Step> queue = new ArrayDeque<>();
        visited.add(wood);
        queue.add(new Step(wood, 1));
        while (!queue.isEmpty()) {
            Step step = queue.removeFirst();
            for (CodaBlockPos next : adjacent(step.pos())) {
                if (next.equals(source) || next.equals(wood)) continue;
                if (!world.isChunkLoaded(dim, next)) continue;
                // Only the full checked path of actual BuildCraft blocks is
                // allowed. A full destination simply stalls, never voids cargo.
                var possible = world.inventory(dim, next);
                if (possible.isPresent()) return next;
                if (step.pipes() >= MAX_HOPS || !visited.add(next)) continue;
                if (world.isBlock(dim, next, COBBLE))
                    queue.addLast(new Step(next, step.pipes() + 1));
            }
        }
        return null;
    }

    private static boolean hasItems(CodaInventoryView view) {
        return view.slots().stream().anyMatch(slot -> slot.count() > 0);
    }
    private static List<CodaBlockPos> adjacent(CodaBlockPos point) {
        List<CodaBlockPos> points = new ArrayList<>(6);
        for (int[] dir : NEIGHBORS)
            points.add(new CodaBlockPos(point.x() + dir[0], point.y() + dir[1],
                    point.z() + dir[2]));
        return points;
    }
}
