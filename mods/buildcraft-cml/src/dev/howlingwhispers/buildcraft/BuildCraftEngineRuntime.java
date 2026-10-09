package dev.howlingwhispers.buildcraft;

import dev.howlingwhispers.codaloader.api.CodaBlockPlacements;
import dev.howlingwhispers.codaloader.api.CodaBlockPos;
import dev.howlingwhispers.codaloader.api.CodaServerTickContext;
import dev.howlingwhispers.codaloader.api.CodaWorldView;
import dev.howlingwhispers.buildcraft.BuildCraftEngineStore.Engine;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Authoritative H.O.W.L. bridge for BCCE redstone engine state and tick timing.
 *
 * The old instant chest-to-chest transport implementation was deliberately
 * removed from normal gameplay. Pipes need actual BCCE MjPort receivers,
 * travelling ItemStacks and Minecraft block-entity rendering before we enable
 * items moving in-game; no player will be given a fake transfer in the interim.
 *
 * This tracks real native engine positions against loaded Minecraft blocks and
 * persists precise MJ, heat, stroke and redstone state under the world's save.
 * Original TileEngineRedstone_BC8/TileEngineBase_BC8 are the rules reference.
 */
public final class BuildCraftEngineRuntime {
    private static final String ENGINE = "buildcraftcore:engine_redstone";

    private final Set<Engine> engines = new HashSet<>();
    private final Map<Engine,BuildCraftRedstoneEngine> machines = new HashMap<>();
    private String activeSession = "";
    private Path activeWorld;
    private boolean dirty;
    private int lastMoved;

    public synchronized void onPlacement(CodaBlockPlacements.Placement placed) {
        if (ENGINE.equals(placed.blockId())) {
            Engine engine = new Engine(placed.dimension(), placed.position());
            if (engines.add(engine)) {
                machines.put(engine,new BuildCraftRedstoneEngine());
                dirty = true;
            }
        }
    }

    private void persist() throws Exception {
        if(activeWorld==null) return;
        // Source of truth is still the physical Minecraft block. Position index
        // and engine snapshots are both stored with checksum + atomic rename.
        Map<Engine,BuildCraftRedstoneEngine.Snapshot> snapshots=new HashMap<>();
        for(Engine engine:engines) {
            BuildCraftRedstoneEngine machine=machines.get(engine);
            if(machine==null) throw new IllegalStateException("Missing MJ state for native engine");
            snapshots.put(engine,machine.snapshot());
        }
        BuildCraftEngineMjStore.save(activeWorld,snapshots);
        BuildCraftEngineStore.save(activeWorld,engines);
        dirty=false;
    }

    public synchronized void onServerTick(CodaServerTickContext tick) throws Exception {
        if (tick.world().isEmpty()) return;
        CodaWorldView world=tick.world().get();
        if (!activeSession.equals(tick.sessionId())) {
            Path root=world.worldDirectory().orElse(null);
            Set<Engine> loaded=root==null ? Set.of() : BuildCraftEngineStore.load(root);
            Map<Engine,BuildCraftRedstoneEngine.Snapshot> saved=root==null
                    ? Map.of() : BuildCraftEngineMjStore.load(root);
            engines.clear();
            engines.addAll(loaded);
            machines.clear();
            for(Engine engine:loaded)
                machines.put(engine,new BuildCraftRedstoneEngine(saved.getOrDefault(engine,
                        new BuildCraftRedstoneEngine().snapshot())));
            activeWorld=root;
            activeSession=tick.sessionId();
            dirty=false;
            lastMoved=0;
        }
        if (dirty) persist(); // New placements are made durable before ticking.

        for(Engine engine:List.copyOf(engines)) {
            if(!world.isChunkLoaded(engine.dimension(),engine.position())) continue;
            if(!world.isBlock(engine.dimension(),engine.position(),ENGINE)) {
                engines.remove(engine);
                machines.remove(engine);
                dirty=true;
                continue;
            }
            BuildCraftRedstoneEngine machine=machines.computeIfAbsent(
                    engine,unused->new BuildCraftRedstoneEngine());
            // Receiver NONE is deliberate: H.O.W.L. cannot safely expose
            // BCCE's redstone MJ port through native Minecraft block entities
            // yet. Engine simulation is exact, but cargo does not teleport.
            boolean powered=world.hasNeighborSignal(engine.dimension(),engine.position());
            machine.tick(tick.tick(),powered,BuildCraftRedstoneEngine.MjEndpoint.NONE);
        }

        // Save updated MJ and piston states at most once per 20 server ticks;
        // a clean server shutdown / Snapshot 3 block-entity API will eventually
        // provide immediate flushes. Never run on render thread.
        if (dirty || tick.tick()%20 == 0) persist();
        lastMoved=0; // No artificial instant chest transfers.
    }

    public synchronized Optional<BuildCraftRedstoneEngine.Snapshot> engineState(
            String dimension,CodaBlockPos position) {
        BuildCraftRedstoneEngine value=machines.get(new Engine(dimension,position));
        return value==null?Optional.empty():Optional.of(value.snapshot());
    }
    /** Compat diagnostic; stays zero until actual native pipe item movement exists. */
    public synchronized int lastMoved() { return lastMoved; }
}
