package net.minecraft.core.registries;

import java.util.List;
import java.util.stream.Stream;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

public final class BuiltInRegistries {
    public static final ItemRegistry ITEM = new ItemRegistry();
    public static final class ItemRegistry {
        private final List<Item> items = List.of(
            new Item("minecraft:air", "Air"),
            new Item("minecraft:iron_ingot", "Iron Ingot"),
            new Item("minecraft:copper_ingot", "Copper Ingot"),
            new Item("howl_sorting_pipes:sorting_pipe", "Sorting Pipe"),
            new Item("minecraft:diamond", "Diamond"),
            new Item("minecraft:glass", "Glass"),
            new Item("minecraft:raw_iron", "Raw Iron"));
        public Stream<Item> stream() { return items.stream(); }
        public String getKey(Object item) { return ((Item) item).id; }
        public Item getValue(Identifier id) {
            return items.stream().filter(item -> item.id.equals(id.value()))
                    .findFirst().orElse(null);
        }
    }
}