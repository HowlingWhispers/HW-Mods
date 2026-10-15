package dev.howlingwhispers.codex;

import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.crafting.display.SlotDisplay;

/** Fixture classes deliberately mirror Snapshot 3's public record accessors. */
public final class FakeRecipe {
    private FakeRecipe() {}
    public record Ingredient(List<ItemStack> stacks) implements SlotDisplay {
        public List<ItemStack> resolveForStacks(ContextMap context) { return stacks; }
    }
    public record Shaped(List<SlotDisplay> ingredients, SlotDisplay result,
                         SlotDisplay craftingStation) {}
    public record Furnace(SlotDisplay ingredient, SlotDisplay fuel,
                          SlotDisplay result, SlotDisplay craftingStation) {}
}