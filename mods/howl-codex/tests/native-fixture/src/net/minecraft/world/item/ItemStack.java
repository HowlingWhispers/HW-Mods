package net.minecraft.world.item;
import net.minecraft.network.chat.Component;

public final class ItemStack {
    private final Item item;
    private final int count;
    public ItemStack(Item item) { this(item, 1); }
    public ItemStack(Item item, int count) { this.item=item; this.count=count; }
    public Item getItem() { return item; }
    public int getCount() { return count; }
    public Component getHoverName() { return Component.literal(item.name); }
}