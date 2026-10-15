package dev.howlingwhispers.codex;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Native Snapshot 3 item sprites. All rendering stays in Minecraft's
 * GuiGraphicsExtractor, with actual registered ItemStack objects.
 */
final class NativeItemIcons {
    private static final AtomicBoolean WARNED = new AtomicBoolean();
    private NativeItemIcons() {}

    /** Attach a render-only sprite, never an interactive inventory slot. */
    static Object add(Object screen, String itemId, int x, int y) {
        try {
            Class<?> identifierType = Class.forName("net.minecraft.resources.Identifier");
            Object identifier = identifierType.getMethod("parse", String.class).invoke(null, itemId);
            Object registry = Class.forName("net.minecraft.core.registries.BuiltInRegistries")
                    .getField("ITEM").get(null);
            Object nativeItem = registry.getClass().getMethod("getValue", identifierType)
                    .invoke(registry, identifier);
            if (nativeItem == null) return null;
            Object key = registry.getClass().getMethod("getKey", Object.class)
                    .invoke(registry, nativeItem);
            if (key == null || !itemId.equals(key.toString())) return null;

            Class<?> itemStackType = Class.forName("net.minecraft.world.item.ItemStack");
            Object stack = null;
            for (Constructor<?> constructor : itemStackType.getConstructors()) {
                Class<?>[] params = constructor.getParameterTypes();
                if (params.length == 2 && params[0].isInstance(nativeItem)
                        && params[1] == int.class) {
                    stack = constructor.newInstance(nativeItem, 1);
                    break;
                }
                if (params.length == 1 && params[0].isInstance(nativeItem)) {
                    stack = constructor.newInstance(nativeItem);
                    break;
                }
            }
            if (stack == null) throw new NoSuchMethodException("ItemStack(Item[, count])");

            final Object displayStack = stack;
            Class<?> renderable = Class.forName("net.minecraft.client.gui.components.Renderable");
            Object icon = Proxy.newProxyInstance(renderable.getClassLoader(),
                    new Class<?>[]{renderable}, (proxy, method, args) -> {
                if (method.getName().equals("extractRenderState")) {
                    Object graphics = args[0];
                    graphics.getClass().getMethod("item",
                            itemStackType, int.class, int.class)
                            .invoke(graphics, displayStack, x, y);
                    return null;
                }
                return switch (method.getName()) {
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "toString" -> "Codex item sprite " + itemId;
                    default -> null;
                };
            });
            Method add = Class.forName("net.minecraft.client.gui.screens.Screen")
                    .getDeclaredMethod("addRenderableOnly", renderable);
            add.setAccessible(true);
            add.invoke(screen, icon);
            return icon;
        } catch (ReflectiveOperationException | RuntimeException failure) {
            if (WARNED.compareAndSet(false, true))
                System.err.println("[H.O.W.L. Codex] Native item sprites unavailable: "
                        + failure);
            return null;
        }
    }
}
