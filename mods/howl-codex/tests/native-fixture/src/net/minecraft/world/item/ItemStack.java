package net.minecraft.world.item;

import net.minecraft.network.chat.Component;

public final class ItemStack {
    private final Item item;
    public ItemStack(Item item) { this.item = item; }
    public Component getHoverName() { return Component.literal(item.name); }
}