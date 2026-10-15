package net.minecraft.client.gui.screens.recipebook;
import java.util.List;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
public final class RecipeCollection {
    private final List<RecipeDisplayEntry> recipes;
    public RecipeCollection(List<RecipeDisplayEntry> recipes) { this.recipes = recipes; }
    public List<RecipeDisplayEntry> getRecipes() { return recipes; }
}