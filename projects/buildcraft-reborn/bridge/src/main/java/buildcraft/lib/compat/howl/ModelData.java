package buildcraft.lib.compat.howl;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable envelope for the ORIGINAL BCCE model payload, including its
 * PipeRenderData/geometry. This does NOT render geometry or refresh chunks.
 * The future native chunk-renderer bridge must consume that same payload.
 */
public final class ModelData {
    public static final ModelData EMPTY = new ModelData(new IdentityHashMap<>());
    private final Map<ModelProperty<?>, Object> values;

    private ModelData(IdentityHashMap<ModelProperty<?>, Object> copy) {
        this.values = java.util.Collections.unmodifiableMap(copy);
    }

    public static Builder builder() { return new Builder(); }

    @SuppressWarnings("unchecked")
    public <T> T get(ModelProperty<T> key) {
        Objects.requireNonNull(key, "model property");
        return (T) values.get(key);
    }

    public boolean has(ModelProperty<?> key) { return values.containsKey(key); }

    public static final class Builder {
        private final IdentityHashMap<ModelProperty<?>,Object> entries =
                new IdentityHashMap<>();

        public <T> Builder with(ModelProperty<T> key, T originalBakedValue) {
            Objects.requireNonNull(key, "model property");
            Objects.requireNonNull(originalBakedValue, "original model data");
            entries.put(key, originalBakedValue);
            return this;
        }

        public ModelData build() { return new ModelData(new IdentityHashMap<>(entries)); }
    }
}
