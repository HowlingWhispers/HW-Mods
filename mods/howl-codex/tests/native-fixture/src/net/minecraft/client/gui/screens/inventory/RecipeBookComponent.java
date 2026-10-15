package net.minecraft.client.gui.screens.inventory;

import java.util.List;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import dev.howlingwhispers.codex.FakeRecipe;

public final class RecipeBookComponent {
    private boolean visible;
    private final EditBox searchBox = new EditBox(new Object(), 0,0,120,18,Component.literal("search"));
    private final ClientRecipeBook book = new ClientRecipeBook();
    public RecipeBookComponent() {
        var iron = new FakeRecipe.Ingredient(List.of(new ItemStack(new Item("minecraft:iron_ingot", "Iron Ingot"))));
        var copper = new FakeRecipe.Ingredient(List.of(new ItemStack(new Item("minecraft:copper_ingot", "Copper Ingot"))));
        var glass = new FakeRecipe.Ingredient(List.of(new ItemStack(new Item("minecraft:glass", "Glass"))));
        var pipe = new FakeRecipe.Ingredient(List.of(new ItemStack(new Item("howl_sorting_pipes:sorting_pipe", "Sorting Pipe"),4)));
        var raw = new FakeRecipe.Ingredient(List.of(new ItemStack(new Item("minecraft:raw_iron", "Raw Iron"))));
        var ingot = new FakeRecipe.Ingredient(List.of(new ItemStack(new Item("minecraft:iron_ingot", "Iron Ingot"))));
        var station = new FakeRecipe.Ingredient(List.of(new ItemStack(new Item("minecraft:crafting_table", "Crafting Table"))));
        var fuel = new FakeRecipe.Ingredient(List.of(new ItemStack(new Item("minecraft:coal", "Coal"))));
        book.setCollections(List.of(new RecipeCollection(List.of(
                new RecipeDisplayEntry(new RecipeDisplayId(1),
                        new FakeRecipe.Shaped(List.of(iron, glass), pipe, station)),
                new RecipeDisplayEntry(new RecipeDisplayId(2),
                        new FakeRecipe.Furnace(raw, fuel, ingot, station))
        ))));
    }
    public boolean isVisible() { return visible; }
    public void toggleVisibility() { visible = !visible; }
    public String searchText() { return searchBox.getValue(); }
}