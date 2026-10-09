package dev.howlingwhispers.buildcraft;

import dev.howlingwhispers.codaloader.api.CodaBlockPlacements;
import dev.howlingwhispers.codaloader.api.CodaBlockPos;
import dev.howlingwhispers.codaloader.api.CodaInventoryView;
import dev.howlingwhispers.codaloader.api.CodaServerTickContext;
import dev.howlingwhispers.codaloader.api.CodaWorldView;
import dev.howlingwhispers.buildcraft.BuildCraftEngineStore.Engine;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Experimental native Snapshot 3 engine-driven chest transport adapter.
 *
 * Placeable blocks and original textures exist, but instant chest transfer is
 * NOT equivalent to BCCE's MJ engine, travelling-item tile entities or pipe
 * routing. Keep this adapter development-only until those originals are ported.
 *
 * Installed engine positions are now indexed per Minecraft WORLD SAVE. This
 * index is not an alternative source of truth: each loaded engine is checked
 * against the real block before acting; missing/unloaded positions never tick.
 */
public final class BuildCraftEngineRuntime {
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
    private Path activeWorld;
    private boolean dirty;
    private int lastMoved;

    public synchronized void onPlacement(CodaBlockPlacements.Placement placed) {
        // Native placement notifications are server-authoritative and only
        // emitted after Minecraft verifies the placed block identity.
        if (ENGINE.equals(placed.blockId()) &&
                engines.add(new Engine(placed.dimension(), placed.position())))
            dirty = true;
    }

    /**
     * Each server is a separate session. Load the index BEFORE transferring,
     * and atomically save pending placements/removals before the first pulse.
     * An unreadable/corrupt index raises an error; no world is mutated.
     */
    public synchronized void onServerTick(CodaServerTickContext tick) throws Exception {
        if (tick.world().isEmpty()) return;
        CodaWorldView world = tick.world().get();
        if (!activeSession.equals(tick.sessionId())) {
            // Read before replacing state. Failure leaves the prior save intact
            // and prevents this session from pulsing an unverified engine.
            Path root = world.worldDirectory().orElse(null);
            Set<Engine> loaded = root == null
                    ? Set.of() : BuildCraftEngineStore.load(root);
            engines.clear();
            engines.addAll(loaded);
            activeWorld = root;
            activeSession = tick.sessionId();
            lastMoved = 0;
            dirty = false;
        }

        // No transfer proceeds if a placement cannot be made durable.
        if (dirty && activeWorld != null) {
            BuildCraftEngineStore.save(activeWorld, engines);
            dirty = false;
        }
        if (tick.tick() % 20 != 0) return;
        int moved = 0;
        for (Engine engine : List.copyOf(engines)) {
            if (!world.isChunkLoaded(engine.dimension(), engine.position())) continue;
            if (!world.isBlock(engine.dimension(), engine.position(), ENGINE)) {
                engines.remove(engine);
                dirty = true;
                continue;
            }
            if (!world.hasNeighborSignal(engine.dimension(), engine.position())) continue;
            moved += pulseEngine(world, engine);
        }
        if (dirty && activeWorld != null) {
            BuildCraftEngineStore.save(activeWorld, engines);
            dirty = false;
        }
        lastMoved = moved;
        if (moved > 0)
            System.out.println("[BuildCraft H.O.W.L. preview] Moved " + moved
                    + " native item(s) between loaded chests (not BCCE pipe packets).");
    }

    public synchronized int lastMoved() { return lastMoved; }

    private int pulseEngine(CodaWorldView world, Engine engine) throws Exception {
        String dim = engine.dimension();
        for (CodaBlockPos wood : adjacent(engine.position())) {
            if (!world.isChunkLoaded(dim, wood) || !world.isBlock(dim, wood, WOOD)) continue;
            for (CodaBlockPos source : adjacent(wood)) {
                var found = world.inventory(dim, source);
                if (found.isEmpty() || !hasItems(found.get())) continue;
                CodaBlockPos destination = findTarget(world, dim, wood, source);
                if (destination != null)
                    return world.transfer(dim, source, destination, ITEMS_PER_PULSE);
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
