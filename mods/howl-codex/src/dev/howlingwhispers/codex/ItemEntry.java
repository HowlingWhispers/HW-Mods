package dev.howlingwhispers.codex;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** Immutable item data supplied by a future native Minecraft registry adapter. */
public record ItemEntry(String id, String name, Set<String> tags) {
    public ItemEntry {
        id = normalizeId(id);
        name = Objects.requireNonNull(name, "name").strip();
        if (name.isEmpty()) throw new IllegalArgumentException("Item name must not be empty");
        Objects.requireNonNull(tags, "tags");
        Set<String> clean = new LinkedHashSet<>();
        for (String tag : tags) clean.add(normalizeId(tag));
        tags = Set.copyOf(clean);
    }

    public String modId() { return id.substring(0, id.indexOf(':')); }

    static String normalizeId(String id) {
        Objects.requireNonNull(id, "id");
        String value = id.strip().toLowerCase(Locale.ROOT);
        if (!value.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))
            throw new IllegalArgumentException("Expected namespaced registry ID, got: " + id);
        return value;
    }
}
