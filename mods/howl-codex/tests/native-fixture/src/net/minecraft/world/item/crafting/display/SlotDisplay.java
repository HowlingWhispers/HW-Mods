package net.minecraft.world.item.crafting.display;
import java.util.List;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.ItemStack;
public interface SlotDisplay {
    List<ItemStack> resolveForStacks(ContextMap context);
}