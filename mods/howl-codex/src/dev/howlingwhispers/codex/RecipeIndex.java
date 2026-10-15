package dev.howlingwhispers.codex;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Immutable forward and reverse recipe lookup shared by all UI adapters. */
public final class RecipeIndex {
    private final List<RecipeEntry> entries;
    private final Map<String, List<RecipeEntry>> byOutput;
    private final Map<String, List<RecipeEntry>> byIngredient;

    public RecipeIndex(Collection<RecipeEntry> recipes) {
        Objects.requireNonNull(recipes, "recipes");
        Map<String, RecipeEntry> ids = new HashMap<>();
        for (RecipeEntry recipe : recipes) {
            Objects.requireNonNull(recipe, "recipe");
            if (ids.putIfAbsent(recipe.id(), recipe) != null)
                throw new IllegalArgumentException("Duplicate recipe ID: " + recipe.id());
        }
        entries = ids.values().stream().sorted(Comparator.comparing(RecipeEntry::id)).toList();
        Map<String, List<RecipeEntry>> outputs = new HashMap<>();
        Map<String, List<RecipeEntry>> usages = new HashMap<>();
        for (RecipeEntry recipe : entries) {
            outputs.computeIfAbsent(recipe.outputItemId(), ignored -> new ArrayList<>()).add(recipe);
            Set<String> uniqueInputs = recipe.ingredients().stream().flatMap(Set::stream)
                    .collect(java.util.stream.Collectors.toSet());
            for (String itemId : uniqueInputs)
                usages.computeIfAbsent(itemId, ignored -> new ArrayList<>()).add(recipe);
        }
        byOutput = frozen(outputs);
        byIngredient = frozen(usages);
    }

    private static Map<String, List<RecipeEntry>> frozen(Map<String, List<RecipeEntry>> source) {
        Map<String, List<RecipeEntry>> safe = new HashMap<>();
        source.forEach((key, value) -> safe.put(key, List.copyOf(value)));
        return Map.copyOf(safe);
    }

    public int size() { return entries.size(); }
    public List<RecipeEntry> all() { return entries; }
    public List<RecipeEntry> recipesFor(String itemId) {
        return byOutput.getOrDefault(ItemEntry.normalizeId(itemId), List.of());
    }
    public List<RecipeEntry> usagesOf(String itemId) {
        return byIngredient.getOrDefault(ItemEntry.normalizeId(itemId), List.of());
    }
}
