package buildcraft.lib.compat.howl;

import java.util.Objects;

/**
 * Loader-neutral replacement for NeoForge's actively scoped mod identifier.
 * H.O.W.L. must open a scope while registering original BuildCraft pipe
 * definitions. There is deliberately no silent fallback to another mod ID.
 */
public final class ActiveModNamespace {
    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private ActiveModNamespace() {}

    public static String get() {
        String id = CURRENT.get();
        if (id == null) {
            throw new IllegalStateException(
                "Cannot register original BCCE pipes outside an active H.O.W.L. mod scope");
        }
        return id;
    }

    public static Scope enter(String modId) {
        Objects.requireNonNull(modId, "modId");
        if (!modId.matches("[a-z][a-z0-9_]*")) {
            throw new IllegalArgumentException("Invalid mod namespace: " + modId);
        }
        String previous = CURRENT.get();
        CURRENT.set(modId);
        return new Scope(previous);
    }

    public static final class Scope implements AutoCloseable {
        private final String previous;
        private boolean closed;

        private Scope(String previous) {
            this.previous = previous;
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            if (previous == null) CURRENT.remove();
            else CURRENT.set(previous);
        }
    }
}
