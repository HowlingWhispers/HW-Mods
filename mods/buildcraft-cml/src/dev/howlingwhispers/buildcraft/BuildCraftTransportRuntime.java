package dev.howlingwhispers.buildcraft;

import dev.howlingwhispers.codaloader.api.CodaServerTickContext;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Single-player first: one authoritative pipe network per (server, dimension).
 * The overworld and Nether must never share packet positions or inventories.
 */
public final class BuildCraftTransportRuntime {
    public static final String OVERWORLD = "minecraft:overworld";

    private record WorldKey(String session, String dimension) {
        WorldKey {
            if (session == null || session.isBlank())
                throw new IllegalArgumentException("Missing server session ID");
            if (dimension == null
                    || !dimension.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))
                throw new IllegalArgumentException("Invalid namespaced dimension");
        }
    }

    private static final class Binding {
        final PipeNetwork network;
        final Predicate<PipeNetwork.Pos> loaded;
        Thread serverThread;
        long lastTick;

        Binding(PipeNetwork network, Predicate<PipeNetwork.Pos> loaded) {
            this.network = network;
            this.loaded = loaded;
        }
    }

    private final Map<WorldKey, Binding> worlds = new HashMap<>();

    /**
     * Explicit dimension-aware binding for Minecraft integration. A future
     * block adapter supplies its per-dimension network and an existing-chunk
     * predicate, on the actual integrated server thread.
     */
    public synchronized void bind(String sessionId, String dimensionId, PipeNetwork network,
                                  Predicate<PipeNetwork.Pos> isChunkLoaded) {
        WorldKey key = new WorldKey(sessionId, dimensionId);
        Objects.requireNonNull(network, "network");
        Objects.requireNonNull(isChunkLoaded, "isChunkLoaded");
        if (worlds.putIfAbsent(key, new Binding(network, isChunkLoaded)) != null)
            throw new IllegalStateException("BuildCraft world already bound: " + key);
    }

    /** Legacy test helper, which always binds the overworld. */
    public void bind(String sessionId, PipeNetwork network,
                     Predicate<PipeNetwork.Pos> isChunkLoaded) {
        bind(sessionId, OVERWORLD, network, isChunkLoaded);
    }

    /**
     * Take a fully validated snapshot BEFORE detaching an individual
     * dimension, including all packets and inventories. Do not silently merge
     * inventories between dimensions or create the snapshot after detach.
     */
    public synchronized java.util.List<PipeNetwork.NodeState> unbind(String sessionId,
                                                                      String dimensionId) {
        WorldKey key = new WorldKey(sessionId, dimensionId);
        Binding binding = worlds.get(key);
        if (binding == null)
            throw new IllegalStateException("Unknown BuildCraft world: " + key);
        var preserved = binding.network.snapshot();
        worlds.remove(key);
        return preserved;
    }

    /** Legacy test helper, which detaches only the overworld. */
    public java.util.List<PipeNetwork.NodeState> unbind(String sessionId) {
        return unbind(sessionId, OVERWORLD);
    }

    /** Called from CodaContext.registerServerTick on the server thread. */
    public synchronized void onServerTick(CodaServerTickContext tick) {
        Objects.requireNonNull(tick, "tick");
        Thread current = Thread.currentThread();
        for (Map.Entry<WorldKey, Binding> entry : worlds.entrySet()) {
            if (!entry.getKey().session.equals(tick.sessionId())) continue;
            Binding binding = entry.getValue();
            if (binding.serverThread == null) binding.serverThread = current;
            if (binding.serverThread != current)
                throw new IllegalStateException("BuildCraft transport would run off its world server thread");
            if (tick.tick() <= binding.lastTick)
                throw new IllegalStateException("Repeated or out-of-order BuildCraft server tick");
            // No mutable Minecraft world objects are read or written by this
            // Minecraft-independent pipe simulation.
            binding.network.tick(binding.loaded);
            binding.lastTick = tick.tick();
        }
    }

    /** Number of attached dimension networks across all server sessions. */
    public synchronized int activeSessions() {
        return worlds.size();
    }
}
