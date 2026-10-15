package dev.howlingwhispers.codex;

import java.util.List;
import java.util.Set;

public final class CodexIndexTest {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
    private static void invalid(Runnable call) {
        try { call.run(); }
        catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Expected IllegalArgumentException");
    }

    public static void main(String[] args) {
        ItemEntry pipe = new ItemEntry("howl_sorting_pipes:sorting_pipe",
                "Sorting Pipe", Set.of("howl_sorting_pipes:pipes"));
        ItemEntry iron = new ItemEntry("minecraft:iron_ingot", "Iron Ingot",
                Set.of("minecraft:ingots/iron"));
        ItemEntry copper = new ItemEntry("minecraft:copper_ingot", "Copper Ingot",
                Set.of("minecraft:ingots/copper"));
        ItemIndex index = new ItemIndex(List.of(iron, pipe, copper));
        check(index.size() == 3, "Item count");
        check(index.search("IRon", 0, 12).equals(List.of(iron)), "Case-insensitive names");
        check(index.search("@howl_sorting_pipes pipe", 0, 12).equals(List.of(pipe)), "Mod filter");
        check(index.search("#minecraft:ingots", 0, 12).equals(List.of(copper, iron)),
                "Tag filter and deterministic registry-ID order");
        check(index.search("", 0, 2).size() == 2 && index.search("", 1, 2).size() == 1,
                "Paginated results");
        check(index.search("missing", 0, 10).isEmpty(), "Missing item");
        invalid(() -> index.search("", -1, 10));
        invalid(() -> index.search("", 0, 300));
        invalid(() -> new ItemIndex(List.of(iron, iron)));
        invalid(() -> new ItemEntry("no-namespace", "Invalid", Set.of()));
        try { index.all().clear(); throw new AssertionError("Index must be immutable"); }
        catch (UnsupportedOperationException expected) {}

        RecipeEntry shaped = new RecipeEntry("howl_sorting_pipes:sorting_pipe",
                "minecraft:crafting_shaped", pipe.id(), 4,
                List.of(Set.of(iron.id(), copper.id()), Set.of("minecraft:glass")));
        RecipeEntry alternate = new RecipeEntry("howl_sorting_pipes:sorting_pipe_alt",
                "minecraft:crafting_shapeless", pipe.id(), 1, List.of(Set.of(iron.id())));
        RecipeEntry smelt = new RecipeEntry("minecraft:iron_ingot_from_smelting",
                "minecraft:smelting", iron.id(), 1, List.of(Set.of("minecraft:raw_iron")));
        RecipeIndex recipes = new RecipeIndex(List.of(smelt, alternate, shaped));
        check(recipes.size() == 3, "Recipe count");
        check(recipes.recipesFor(pipe.id()).equals(List.of(shaped, alternate)), "Multiple recipes");
        check(recipes.usagesOf(iron.id()).equals(List.of(shaped, alternate)), "Reverse usages");
        check(recipes.usagesOf(copper.id()).equals(List.of(shaped)), "Alternatives indexed");
        check(recipes.recipesFor("minecraft:missing").isEmpty(), "No invented recipe");
        check(recipes.usagesOf("minecraft:missing").isEmpty(), "No invented usage");
        invalid(() -> new RecipeIndex(List.of(shaped, shaped)));
        invalid(() -> new RecipeEntry("test:broken", "minecraft:crafting_shaped",
                pipe.id(), 1, List.of(Set.of())));
        try { recipes.usagesOf(iron.id()).clear(); throw new AssertionError("Recipes must be immutable"); }
        catch (UnsupportedOperationException expected) {}
        System.out.println("CodexIndexTest passed");
    }
}
