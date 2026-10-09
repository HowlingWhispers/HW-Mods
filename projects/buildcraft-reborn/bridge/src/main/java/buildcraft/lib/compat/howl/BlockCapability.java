package buildcraft.lib.compat.howl;

import net.minecraft.resources.Identifier;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Objects;

/**
 * H.O.W.L. descriptor for BCCE's existing sided capability contracts.
 * This only IDENTIFIES an original capability; actual values are obtained
 * from real Minecraft block entities through NativeCapabilityAccess.
 *
 * No fake inventory, fluid store, energy or pipe connection is created.
 */
public final class BlockCapability<T,C> {
    private static final ConcurrentHashMap<Identifier, BlockCapability<?,?>> SIDED =
            new ConcurrentHashMap<>();
    private final Identifier id;
    private final Class<T> type;

    private BlockCapability(Identifier id, Class<T> type) {
        this.id = id;
        this.type = type;
    }

    @SuppressWarnings("unchecked")
    public static <T,C> BlockCapability<T,C> createSided(Identifier id, Class<T> type) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        BlockCapability<?,?> original = SIDED.compute(id, (key, existing) -> {
            if (existing != null && existing.type != type)
                throw new IllegalArgumentException(
                    "Conflicting BuildCraft capability type for " + key);
            return existing != null ? existing : new BlockCapability<>(id, type);
        });
        return (BlockCapability<T,C>) original;
    }

    public Identifier id() { return id; }
    public Class<T> type() { return type; }

    /** Reject misregistered implementations instead of corrupting game state. */
    public T checkedValue(Object object) {
        return object == null ? null : type.cast(object);
    }

    @Override public String toString() { return "H.O.W.L. BCCE capability " + id; }
}
