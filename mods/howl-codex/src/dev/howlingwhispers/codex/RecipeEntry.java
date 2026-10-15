package dev.howlingwhispers.codex;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * A normalized recipe. Each ingredient slot contains alternative item IDs.
 * Tags and dynamic ingredient predicates must be resolved by the Minecraft
 * recipe adapter; never invent unseen recipes or ingredient possibilities.
 */
public record RecipeEntry(String id, String type, String outputItemId,
                          int outputCount, List<Set<String>> ingredients) {
    public RecipeEntry {
        id = ItemEntry.normalizeId(id);
        type = ItemEntry.normalizeId(type);
        outputItemId = ItemEntry.normalizeId(outputItemId);
        if (outputCount < 1) throw new IllegalArgumentException("Output count must be positive");
        Objects.requireNonNull(ingredients, "ingredients");
        List<Set<String>> safe = new ArrayList<>();
        for (Set<String> choices : ingredients) {
            Objects.requireNonNull(choices, "ingredient choices");
            if (choices.isEmpty()) throw new IllegalArgumentException("An ingredient slot has no options");
            Set<String> normalized = new LinkedHashSet<>();
            for (String choice : choices) normalized.add(ItemEntry.normalizeId(choice));
            safe.add(Set.copyOf(normalized));
        }
        ingredients = List.copyOf(safe);
    }
}
