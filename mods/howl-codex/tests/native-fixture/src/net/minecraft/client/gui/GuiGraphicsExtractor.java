package net.minecraft.client.gui;
import net.minecraft.world.item.ItemStack;
public final class GuiGraphicsExtractor {
    public int drawn;
    public void item(ItemStack item, int x, int y) { drawn++; }
}