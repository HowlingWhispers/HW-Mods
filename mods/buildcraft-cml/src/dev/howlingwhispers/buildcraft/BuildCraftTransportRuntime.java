package dev.howlingwhispers.buildcraft;

import dev.howlingwhispers.codaloader.api.CodaServerTickContext;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Minimal glue between H.O.W.L.'s native server ticks and BuildCraft transport.
 * The future Snapshot 3 block/chunk adapter must bind each world's pipe network
 * and its real loaded-chunk predicate before anything can move.
 */
public final class BuildCraftTransportRuntime {
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

    private final Map<String, Binding> worlds = new HashMap<>();

    /**
     * Bind an existing world network. The future Minecraft adapter must call
     * this only on the authoritative server thread, not during a GUI callback.
     * A loaded-chunk predicate is mandatory: fail closed if none is available.
     */
    public synchronized void bind(String sessionId, PipeNetwork network,
                                  Predicate<PipeNetwork.Pos> isChunkLoaded) {
        if (sessionId == null || sessionId.isBlank())
            throw new IllegalArgumentException("Missing server session ID");
        Objects.requireNonNull(network, "network");
        Objects.requireNonNull(isChunkLoaded, "isChunkLoaded");
        if (worlds.putIfAbsent(sessionId, new Binding(network, isChunkLoaded)) != null)
            throw new IllegalStateException("World session already bound: " + sessionId);
    }

    /**
     * Returns the last in-flight state to the future world persistence adapter.
     * A detached world is never allowed to silently lose the network.
     */
    public synchronized java.util.List<PipeNetwork.NodeState> unbind(String sessionId) {
        Binding binding = worlds.get(sessionId);
        if (binding == null) throw new IllegalStateException("Unknown BuildCraft world session");
        // Take the snapshot successfully BEFORE dropping the authoritative network.
        var preserved = binding.network.snapshot();
        worlds.remove(sessionId);
        return preserved;
    }

    /** Called from CodaContext.registerServerTick, once per native server tick. */
    public synchronized void onServerTick(CodaServerTickContext tick) {
        Objects.requireNonNull(tick, "tick");
        Binding binding = worlds.get(tick.sessionId());
        if (binding == null) return; // World adapter has not attached a pipe network.
        Thread current = Thread.currentThread();
        if (binding.serverThread == null) binding.serverThread = current;
        if (binding.serverThread != current)
            throw new IllegalStateException("BuildCraft transport would run off its world server thread");
        if (tick.tick() <= binding.lastTick)
            throw new IllegalStateException("Out-of-order or repeated BuildCraft server tick");
        // No Minecraft world objects are accessed by this pure transport layer.
        binding.network.tick(binding.loaded);
        binding.lastTick = tick.tick();
    }

    public synchronized int activeSessions() {
        return worlds.size();
    }
}
