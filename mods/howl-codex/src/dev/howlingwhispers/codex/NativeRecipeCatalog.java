package dev.howlingwhispers.codex;

import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Snapshot 3's client recipe book contains server-synchronized, unlocked
 * RecipeDisplayEntry values. No server-only RecipeManager is assumed.
 *
 * Recipes are indexed on an explicit R/U action, not on every GUI frame.
 * An ingredient whose SlotDisplay cannot resolve is never fabricated.
 */
final class NativeRecipeCatalog {
    private NativeRecipeCatalog() {}

    static RecipeIndex load(Object client, Object inventoryScreen) throws ReflectiveOperationException {
        Object component = NativeGui.field(inventoryScreen, "recipeBookComponent");
        Object book = NativeGui.field(component, "book");
        if (book == null) throw new IllegalStateException("Recipe book has not synchronized");
        Object level = NativeGui.field(client, "level");
        if (level == null) throw new IllegalStateException("No active client world");
        Class<?> nativeLevel = Class.forName("net.minecraft.world.level.Level");
        Object context = Class.forName("net.minecraft.world.item.crafting.display.SlotDisplayContext")
                .getMethod("fromLevel", nativeLevel).invoke(null, level);
        Object raw = book.getClass().getMethod("getCollections").invoke(book);
        if (!(raw instanceof Collection<?> collections))
            throw new IllegalStateException("Recipe collections are not available");
        var unique = new LinkedHashMap<String, RecipeEntry>();
        for (Object collection : collections) {
            Object all = collection.getClass().getMethod("getRecipes").invoke(collection);
            if (!(all instanceof Collection<?> entries)) continue;
            for (Object entry : entries) {
                Object nativeId = entry.getClass().getMethod("id").invoke(entry);
                Object display = entry.getClass().getMethod("display").invoke(entry);
                String key = String.valueOf(nativeId.getClass().getMethod("index").invoke(nativeId));
                List<Object> products = resolve(display.getClass().getMethod("result").invoke(display), context);
                List<Set<String>> ingredients = ingredients(display, context);
                String type = "minecraft:" + display.getClass().getSimpleName()
                        .replace("RecipeDisplay", "").toLowerCase(Locale.ROOT);
                for (int variant = 0; variant < products.size(); variant++) {
                    Object stack = products.get(variant);
                    String outputId = itemId(stack);
                    if (outputId == null || outputId.equals("minecraft:air")) continue;
                    int count = (Integer) stack.getClass().getMethod("getCount").invoke(stack);
                    if (count <= 0) continue;
                    String id = "howl_codex:known_" + key + "_" + variant;
                    unique.put(id, new RecipeEntry(id, type, outputId, count, ingredients));
                }
            }
        }
        return new RecipeIndex(unique.values());
    }

    private static List<Set<String>> ingredients(Object display, Object context)
            throws ReflectiveOperationException {
        var result = new ArrayList<Set<String>>();
        if (display.getClass().isRecord()) {
            for (RecordComponent part : display.getClass().getRecordComponents()) {
                String name = part.getName();
                if (name.equals("result") || name.equals("craftingStation") ||
                        name.equals("fuel") || name.equals("experience")) continue;
                appendSlots(part.getAccessor().invoke(display), context, result, 0);
            }
        } else {
            // Unknown third-party display shape: don't invent ingredient edges.
            return List.of();
        }
        return List.copyOf(result);
    }

    private static void appendSlots(Object node, Object context,
                                    List<Set<String>> out, int depth)
            throws ReflectiveOperationException {
        if (node == null || depth > 8) return;
        if (node instanceof Optional<?> option) {
            if (option.isPresent()) appendSlots(option.get(), context, out, depth + 1);
            return;
        }
        if (node instanceof Iterable<?> list) {
            for (Object child : list) appendSlots(child, context, out, depth + 1);
            return;
        }
        Class<?> slotType = Class.forName("net.minecraft.world.item.crafting.display.SlotDisplay");
        if (slotType.isInstance(node)) {
            var alternatives = new LinkedHashSet<String>();
            for (Object stack : resolve(node, context)) {
                String id = itemId(stack);
                if (id != null && !id.equals("minecraft:air")) alternatives.add(id);
            }
            if (!alternatives.isEmpty()) out.add(Set.copyOf(alternatives));
        }
    }

    private static List<Object> resolve(Object slot, Object context)
            throws ReflectiveOperationException {
        Class<?> slotType = Class.forName("net.minecraft.world.item.crafting.display.SlotDisplay");
        Class<?> contextType = Class.forName("net.minecraft.util.context.ContextMap");
        if (!slotType.isInstance(slot)) return List.of();
        Object stacks = slotType.getMethod("resolveForStacks", contextType)
                .invoke(slot, context);
        if (!(stacks instanceof Collection<?> list)) return List.of();
        return list.stream().filter(java.util.Objects::nonNull).toList();
    }

    private static String itemId(Object stack) throws ReflectiveOperationException {
        Object item = stack.getClass().getMethod("getItem").invoke(stack);
        Object itemRegistry = Class.forName("net.minecraft.core.registries.BuiltInRegistries")
                .getField("ITEM").get(null);
        Method getKey = itemRegistry.getClass().getMethod("getKey", Object.class);
        Object key = getKey.invoke(itemRegistry, item);
        return key == null ? null : key.toString();
    }
}
