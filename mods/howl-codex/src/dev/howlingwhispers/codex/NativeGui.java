package dev.howlingwhispers.codex;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

/**
 * Version-pinned native widget adapter. Game classes are resolved only from
 * Minecraft's client GUI thread, never during mod initialization.
 */
final class NativeGui {
    private NativeGui() {}

    static int dimension(Object instance, String field) throws ReflectiveOperationException {
        return ((Number) field(instance, field)).intValue();
    }

    static Object field(Object instance, String name) throws ReflectiveOperationException {
        for (Class<?> type = instance.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field f = type.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(instance);
            } catch (NoSuchFieldException ignored) {}
        }
        throw new NoSuchFieldException(instance.getClass().getName() + "." + name);
    }

    static Object button(Object screen, String title, int x, int y, int width,
                         Runnable callback) throws ReflectiveOperationException {
        Class<?> component = Class.forName("net.minecraft.network.chat.Component");
        Class<?> listener = Class.forName("net.minecraft.client.gui.components.Button$OnPress");
        Class<?> type = Class.forName("net.minecraft.client.gui.components.Button");
        Object handler = Proxy.newProxyInstance(listener.getClassLoader(),
                new Class<?>[]{listener}, (proxy, method, args) -> {
            return switch (method.getName()) {
                case "onPress" -> { callback.run(); yield null; }
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                case "toString" -> "Codex button";
                default -> null;
            };
        });
        Object text = component.getMethod("literal", String.class).invoke(null, title);
        Object builder = type.getMethod("builder", component, listener)
                .invoke(null, text, handler);
        builder = builder.getClass().getMethod("bounds",
                int.class, int.class, int.class, int.class).invoke(builder, x, y, width, 18);
        Object widget = builder.getClass().getMethod("build").invoke(builder);
        add(screen, widget);
        return widget;
    }

    static Object textBox(Object client, Object screen, String value,
                          int x, int y, int width, Consumer<String> onEdit)
            throws ReflectiveOperationException {
        Object font = field(client, "font");
        Class<?> boxType = Class.forName("net.minecraft.client.gui.components.EditBox");
        Class<?> component = Class.forName("net.minecraft.network.chat.Component");
        Object label = component.getMethod("literal", String.class).invoke(null, "Codex search");
        Object box = null;
        for (Constructor<?> ctor : boxType.getConstructors()) {
            Class<?>[] p = ctor.getParameterTypes();
            if (p.length == 6 && p[0].isInstance(font) && p[1] == int.class &&
                    p[2] == int.class && p[3] == int.class && p[4] == int.class &&
                    p[5].isInstance(label)) {
                box = ctor.newInstance(font, x, y, width, 18, label);
                break;
            }
        }
        if (box == null) throw new NoSuchMethodException("EditBox(Font,int,int,int,int,Component)");
        boxType.getMethod("setMaxLength", int.class).invoke(box, 90);
        boxType.getMethod("setValue", String.class).invoke(box, value);
        boxType.getMethod("setResponder", Consumer.class).invoke(box, onEdit);
        add(screen, box);
        return box;
    }

    static void add(Object screen, Object widget) throws ReflectiveOperationException {
        Class<?> listener = Class.forName("net.minecraft.client.gui.components.events.GuiEventListener");
        Method method = Class.forName("net.minecraft.client.gui.screens.Screen")
                .getDeclaredMethod("addRenderableWidget", listener);
        method.setAccessible(true);
        method.invoke(screen, widget);
    }

    static boolean attached(Object screen, Object widget) {
        if (widget == null) return false;
        try {
            Object children = screen.getClass().getMethod("children").invoke(screen);
            return children instanceof Collection<?> entries && entries.contains(widget);
        } catch (ReflectiveOperationException ex) { return false; }
    }

    static void remove(Object screen, List<Object> widgets) {
        for (Object widget : widgets) {
            for (String field : List.of("children", "renderables", "narratables")) {
                try {
                    Object list = field.equals("children")
                            ? screen.getClass().getMethod("children").invoke(screen)
                            : field(screen, field);
                    if (list instanceof Collection<?> entries) entries.remove(widget);
                } catch (ReflectiveOperationException | UnsupportedOperationException ignored) {}
            }
        }
    }

    /**
     * When Codex opens beside the inventory, close the vanilla recipe book
     * through the inventory screen's own handler. This also recenters its
     * slots instead of leaving the crafting GUI shifted beneath the grid.
     */
    static boolean closeRecipeBook(Object screen) {
        try {
            Object book = field(screen, "recipeBookComponent");
            if (!Boolean.TRUE.equals(book.getClass().getMethod("isVisible").invoke(book)))
                return true;
            for (Class<?> c = screen.getClass(); c != null; c = c.getSuperclass()) {
                try {
                    Method click = c.getDeclaredMethod("onRecipeBookButtonClick");
                    click.setAccessible(true);
                    click.invoke(screen);
                    return !Boolean.TRUE.equals(book.getClass().getMethod("isVisible").invoke(book));
                } catch (NoSuchMethodException ignored) {}
            }
        } catch (ReflectiveOperationException | RuntimeException error) {
            System.err.println("[H.O.W.L. Codex] Recipe book layout not changed: " + error);
        }
        return false;
    }

    /** Use the actual inventory bounds, not a fixed overlay over the slots. */
    static int[] sidePanel(Object screen) throws ReflectiveOperationException {
        int width = dimension(screen, "width");
        int left;
        int image;
        try {
            left = dimension(screen, "leftPos");
            image = dimension(screen, "imageWidth");
        } catch (NoSuchFieldException ignored) {
            image = 176;
            left = (width - image) / 2;
        }
        int rightGap = width - (left + image);
        int leftGap = left;
        // NEI/JEI arrangement: grid in the free right margin by default.
        if (rightGap >= 130) {
            int x = left + image + 8;
            return new int[]{x, Math.min(205, width - x - 6)};
        }
        if (leftGap >= 130) {
            int size = Math.min(205, leftGap - 14);
            return new int[]{left - 8 - size, size};
        }
        return new int[]{-1, 0}; // Never cover vanilla inventory or slots.
    }

    /** Small Minecraft-native clickable item slot, with 18x18 hitbox. */
    static Object itemSlot(Object screen, int x, int y, Runnable action)
            throws ReflectiveOperationException {
        return button(screen, "", x, y, 18, action);
    }

    /** Hand selected output name to Minecraft's ordinary unlocked recipe UI. */
    static boolean searchVanillaRecipes(Object screen, String itemName) {
        try {
            Object book = null;
            for (Class<?> c = screen.getClass(); c != null && book == null; c = c.getSuperclass()) {
                for (Field f : c.getDeclaredFields()) {
                    if (!f.getType().getSimpleName().contains("RecipeBookComponent")) continue;
                    f.setAccessible(true);
                    book = f.get(screen);
                    break;
                }
            }
            if (book == null) return false;
            if (!Boolean.TRUE.equals(book.getClass().getMethod("isVisible").invoke(book)))
                book.getClass().getMethod("toggleVisibility").invoke(book);
            Object search = field(book, "searchBox");
            if (search == null) return false;
            search.getClass().getMethod("setValue", String.class).invoke(search, itemName);
            return true;
        } catch (ReflectiveOperationException | RuntimeException error) {
            System.err.println("[H.O.W.L. Codex] Recipe-book shortcut failed: " + error);
            return false;
        }
    }

    static String clip(String value, int max) {
        return value.length() <= max ? value : value.substring(0, Math.max(0, max - 1)) + "…";
    }
}
