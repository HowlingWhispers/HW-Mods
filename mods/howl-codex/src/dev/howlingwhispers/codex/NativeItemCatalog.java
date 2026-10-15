package dev.howlingwhispers.codex;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/** Read-only native item registry discovery, called only on the GUI thread. */
final class NativeItemCatalog {
    private NativeItemCatalog() {}

    static ItemIndex load() throws ReflectiveOperationException {
        Class<?> registries = Class.forName("net.minecraft.core.registries.BuiltInRegistries");
        Object registry = registries.getField("ITEM").get(null);
        Object values = registry.getClass().getMethod("stream").invoke(registry);
        if (!(values instanceof Stream<?> items))
            throw new IllegalStateException("ITEM registry has no stream()");
        Method getKey = registry.getClass().getMethod("getKey", Object.class);
        List<ItemEntry> entries = new ArrayList<>();
        try (items) {
            for (Object item : items.toList()) {
                if (item == null) continue;
                Object nativeId = getKey.invoke(registry, item);
                if (nativeId == null) continue;
                String id = nativeId.toString();
                if (id.equals("minecraft:air")) continue;
                String label = displayName(item, id);
                entries.add(new ItemEntry(id, label, tagIds(item)));
            }
        }
        if (entries.isEmpty())
            throw new IllegalStateException("The native ITEM registry was empty");
        return new ItemIndex(entries);
    }

    private static String displayName(Object item, String id) {
        try {
            Class<?> stackType = Class.forName("net.minecraft.world.item.ItemStack");
            for (Constructor<?> ctor : stackType.getConstructors()) {
                Class<?>[] p = ctor.getParameterTypes();
                if (p.length == 1 && p[0].isInstance(item)) {
                    Object stack = ctor.newInstance(item);
                    Object component = stackType.getMethod("getHoverName").invoke(stack);
                    String label = (String) component.getClass().getMethod("getString").invoke(component);
                    if (!label.isBlank()) return label;
                }
                if (p.length == 2 && p[0].isInstance(item) && p[1] == int.class) {
                    Object stack = ctor.newInstance(item, 1);
                    Object component = stackType.getMethod("getHoverName").invoke(stack);
                    String label = (String) component.getClass().getMethod("getString").invoke(component);
                    if (!label.isBlank()) return label;
                }
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // The browser can still index an item when its display name changes.
        }
        String shortId = id.substring(id.indexOf(':') + 1).replace('_', ' ');
        StringBuilder result = new StringBuilder();
        for (String word : shortId.split(" ")) {
            if (!word.isEmpty()) {
                if (!result.isEmpty()) result.append(' ');
                result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
            }
        }
        return result.toString();
    }

    private static Set<String> tagIds(Object item) {
        try {
            Object holder = item.getClass().getMethod("builtInRegistryHolder").invoke(item);
            Object stream = holder.getClass().getMethod("tags").invoke(holder);
            Set<String> tags = new LinkedHashSet<>();
            if (stream instanceof Stream<?> tagStream) {
                try (tagStream) {
                    for (Object tag : tagStream.toList()) {
                        Object identifier = tag.getClass().getMethod("location").invoke(tag);
                        tags.add(identifier.toString());
                    }
                }
            }
            return Set.copyOf(tags);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return Set.of();
        }
    }
}
