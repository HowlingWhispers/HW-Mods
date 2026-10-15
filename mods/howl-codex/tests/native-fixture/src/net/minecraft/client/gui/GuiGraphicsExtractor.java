package net.minecraft.client.gui;
import net.minecraft.world.item.ItemStack;
public final class GuiGraphicsExtractor {
    public int drawn, tooltips;
    public void item(ItemStack item, int x, int y) { drawn++; }
    public void setTooltipForNextFrame(net.minecraft.network.chat.Component label, int x, int y) { tooltips++; }
}