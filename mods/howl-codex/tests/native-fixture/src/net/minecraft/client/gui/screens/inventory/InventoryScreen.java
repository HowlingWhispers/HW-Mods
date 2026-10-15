package net.minecraft.client.gui.screens.inventory;
import net.minecraft.client.gui.screens.Screen;

public final class InventoryScreen extends Screen {
    private final RecipeBookComponent recipeBookComponent = new RecipeBookComponent();
    public RecipeBookComponent book() { return recipeBookComponent; }
}