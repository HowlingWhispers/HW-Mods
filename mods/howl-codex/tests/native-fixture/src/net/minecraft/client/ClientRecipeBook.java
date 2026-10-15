package net.minecraft.client;
import java.util.List;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
public final class ClientRecipeBook {
    private List<RecipeCollection> collections;
    public void setCollections(List<RecipeCollection> collections) { this.collections = collections; }
    public List<RecipeCollection> getCollections() { return collections; }
}