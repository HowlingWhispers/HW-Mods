package net.minecraft.client.gui.screens.inventory;

import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

public final class RecipeBookComponent {
    private boolean visible;
    private final EditBox searchBox = new EditBox(new Object(), 0,0,120,18,Component.literal("search"));
    public boolean isVisible() { return visible; }
    public void toggleVisibility() { visible = !visible; }
    public String searchText() { return searchBox.getValue(); }
}