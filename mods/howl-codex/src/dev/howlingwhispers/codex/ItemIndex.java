package dev.howlingwhispers.codex;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Read-only, deterministic registry-item search. Build on registry/reload
 * events, then query on user input; never rebuild this on every render frame.
 * Query syntax: text terms, @modnamespace, #namespaced_tag.
 */
public final class ItemIndex {
    private final List<ItemEntry> entries;

    public ItemIndex(Collection<ItemEntry> values) {
        Objects.requireNonNull(values, "values");
        Map<String, ItemEntry> byId = new TreeMap<>();
        for (ItemEntry item : values) {
            Objects.requireNonNull(item, "item");
            if (byId.putIfAbsent(item.id(), item) != null)
                throw new IllegalArgumentException("Duplicate item ID: " + item.id());
        }
        this.entries = List.copyOf(byId.values());
    }

    public int size() { return entries.size(); }
    public List<ItemEntry> all() { return entries; }

    /** Zero-based page. The bound prevents accidental unbounded UI allocations. */
    public List<ItemEntry> search(String query, int page, int pageSize) {
        if (page < 0 || pageSize < 1 || pageSize > 256)
            throw new IllegalArgumentException("Page must be >= 0 and pageSize 1..256");
        Query parsed = Query.parse(query);
        List<ItemEntry> matches = new ArrayList<>();
        for (ItemEntry item : entries) if (parsed.matches(item)) matches.add(item);
        matches.sort(Comparator.comparingInt((ItemEntry item) -> parsed.score(item))
                .reversed().thenComparing(ItemEntry::id));
        return matches.stream().skip((long) page * pageSize).limit(pageSize).toList();
    }

    private record Query(List<String> words, List<String> mods, List<String> tags) {
        static Query parse(String text) {
            List<String> words = new ArrayList<>(), mods = new ArrayList<>(), tags = new ArrayList<>();
            if (text != null && !text.isBlank()) {
                for (String part : text.strip().toLowerCase(Locale.ROOT).split("\\s+")) {
                    if (part.startsWith("@") && part.length() > 1) mods.add(part.substring(1));
                    else if (part.startsWith("#") && part.length() > 1) tags.add(part.substring(1));
                    else if (!part.equals("@") && !part.equals("#")) words.add(part);
                }
            }
            return new Query(List.copyOf(words), List.copyOf(mods), List.copyOf(tags));
        }

        boolean matches(ItemEntry entry) {
            String label = entry.name().toLowerCase(Locale.ROOT);
            String id = entry.id();
            String readableId = id.replace('_', ' ');
            for (String term : words)
                if (!label.contains(term) && !id.contains(term) && !readableId.contains(term)) return false;
            for (String mod : mods)
                if (!entry.modId().contains(mod)) return false;
            for (String tag : tags)
                if (entry.tags().stream().noneMatch(t -> t.contains(tag))) return false;
            return true;
        }

        int score(ItemEntry entry) {
            if (words.isEmpty()) return 0;
            String phrase = String.join(" ", words);
            String label = entry.name().toLowerCase(Locale.ROOT);
            String id = entry.id();
            if (id.equals(phrase)) return 100;
            if (label.equals(phrase)) return 95;
            if (label.startsWith(phrase)) return 75;
            if (id.substring(id.indexOf(':') + 1).startsWith(phrase.replace(' ', '_'))) return 60;
            return 10;
        }
    }
}
