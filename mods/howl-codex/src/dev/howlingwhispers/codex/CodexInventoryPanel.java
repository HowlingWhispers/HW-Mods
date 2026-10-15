package dev.howlingwhispers.codex;

import dev.howlingwhispers.codaloader.api.CodaInventoryPanels;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * NEI/TMI-style icon grid in the inventory's free side margin.
 * All slots display real registered Minecraft items. The grid is read-only.
 */
public final class CodexInventoryPanel implements CodaInventoryPanels.Panel {
    private static final int CELL = 20;
    private static final int GRID_Y = 70;

    private final Map<Object, State> screens =
            Collections.synchronizedMap(new WeakHashMap<>());
    private ItemIndex catalog;
    private boolean warned;

    private static final class State {
        Object root;
        Object search;
        final List<Object> rows = new ArrayList<>();
        final List<Object> chrome = new ArrayList<>();
        boolean open;
        int page;
        int visibleRows;
        int columns;
        int pageSize;
        int x;
        int width;
        String query = "";
        ItemEntry selected;
        String note = "";
        WeakReference<Object> client = new WeakReference<>(null);
    }

    @Override public void refresh(Object minecraftClient, Object screen) {
        if (!screen.getClass().getName()
                .equals("net.minecraft.client.gui.screens.inventory.InventoryScreen")) return;
        try {
            State state = screens.computeIfAbsent(screen, key -> new State());
            state.client = new WeakReference<>(minecraftClient);
            if (NativeGui.attached(screen, state.root)) {
                if (state.open && (state.search == null ||
                        !NativeGui.attached(screen, state.search)))
                    show(minecraftClient, screen, state);
                return;
            }
            state.root = null;
            state.search = null;
            state.chrome.clear();
            state.rows.clear();
            int w = NativeGui.dimension(screen, "width");
            var weak = new WeakReference<>(screen);
            state.root = NativeGui.button(screen, "Codex", Math.max(2, w - 77),
                    4, 72, () -> {
                        Object current = weak.get();
                        if (current == null) return;
                        state.open = !state.open;
                        if (state.open) show(minecraftClient, current, state);
                        else close(current, state);
                    });
            if (state.open) show(minecraftClient, screen, state);
        } catch (ReflectiveOperationException | RuntimeException error) {
            if (!warned) {
                warned = true;
                System.err.println("[H.O.W.L. Codex] Native inventory UI unavailable: " + error);
            }
        }
    }

    private ItemIndex getCatalog() throws ReflectiveOperationException {
        if (catalog == null) catalog = NativeItemCatalog.load();
        return catalog;
    }

    private void show(Object client, Object screen, State state) {
        close(screen, state);
        state.open = true;
        try {
            ItemIndex items = getCatalog();
            // Close Minecraft's own left-side recipe book first so inventory
            // slots recenter and no vanilla controls sit under the Codex grid.
            NativeGui.closeRecipeBook(screen);
            int[] layout = NativeGui.sidePanel(screen);
            int w = NativeGui.dimension(screen, "width");
            int h = NativeGui.dimension(screen, "height");
            if (layout[0] < 0 || layout[1] < 130 || h < 170) {
                state.note = "Reduce GUI scale for Codex";
                state.chrome.add(NativeGui.button(screen, state.note,
                        Math.max(3, w - 161), 26, Math.min(155, w - 6), () -> {}));
                return;
            }
            state.x = layout[0];
            state.width = layout[1];
            state.columns = Math.min(10, Math.max(1, (state.width - 4) / CELL));
            state.visibleRows = Math.min(12, Math.max(2, (h - 130) / CELL));
            state.pageSize = state.columns * state.visibleRows;
            final int x = state.x;
            final int width = state.width;
            var weak = new WeakReference<>(screen);

            state.chrome.add(NativeGui.button(screen,
                    "H.O.W.L. CODEX (" + items.size() + ")", x, 26, width, () -> {
                        Object current = weak.get();
                        if (current == null) return;
                        state.selected = null;
                        state.page = 0;
                        drawRows(current, state);
                    }));
            state.search = NativeGui.textBox(client, screen, state.query, x, 47,
                    width, query -> {
                        state.query = query;
                        state.page = 0;
                        state.selected = null;
                        Object current = weak.get();
                        if (current != null) drawRows(current, state);
                    });
            drawRows(screen, state);
            System.out.println("[H.O.W.L. Codex] Icon grid ready: " + state.columns
                    + " columns, " + state.visibleRows + " rows; " + items.size()
                    + " registered items.");
        } catch (ReflectiveOperationException | RuntimeException error) {
            state.note = "Codex grid unavailable: " + error.getClass().getSimpleName();
            if (!warned) {
                warned = true;
                System.err.println("[H.O.W.L. Codex] Grid mapping failed: " + error);
            }
            close(screen, state);
        }
    }

    private void drawRows(Object screen, State state) {
        NativeGui.remove(screen, state.rows);
        state.rows.clear();
        try {
            final int x = state.x;
            final int width = state.width;
            final int y = GRID_Y;
            var weak = new WeakReference<>(screen);
            if (state.selected != null) {
                ItemEntry item = state.selected;
                addRow(screen, state, "< Back to item grid", x, y, width, () -> {
                    state.selected = null;
                    Object current = weak.get();
                    if (current != null) drawRows(current, state);
                });
                Object icon = NativeItemIcons.add(screen, item.id(),
                        x + 3, y + 23, item.name());
                if (icon != null) state.rows.add(icon);
                addRow(screen, state, NativeGui.clip(item.name(), 26),
                        x + 22, y + 22, width - 22, () -> {});
                addRow(screen, state, NativeGui.clip(item.id(), 29),
                        x, y + 44, width, () -> {});
                addRow(screen, state, "Mod: " + NativeGui.clip(item.modId(), 22),
                        x, y + 66, width, () -> {});
                addRow(screen, state, "R  Known recipes", x, y + 92, width, () -> {
                    Object current = weak.get();
                    if (current != null)
                        RecipeNavigator.open(state.client.get(), current, item, catalog,
                                false, state.rows, () -> drawRows(current, state),
                                x, width, state.visibleRows);
                });
                addRow(screen, state, "U  Known uses", x, y + 115, width, () -> {
                    Object current = weak.get();
                    if (current != null)
                        RecipeNavigator.open(state.client.get(), current, item, catalog,
                                true, state.rows, () -> drawRows(current, state),
                                x, width, state.visibleRows);
                });
                addRow(screen, state, NativeGui.clip(state.note.isBlank()
                        ? "Read only | No item spawning" : state.note, 30),
                        x, y + 139, width, () -> {});
                return;
            }

            List<ItemEntry> results = getCatalog().search(state.query, state.page,
                    state.pageSize);
            for (int i = 0; i < results.size(); i++) {
                ItemEntry item = results.get(i);
                int cellX = x + 2 + (i % state.columns) * CELL;
                int cellY = y + (i / state.columns) * CELL;
                state.rows.add(NativeGui.itemSlot(screen, cellX, cellY, () -> {
                    state.selected = item;
                    state.note = "";
                    Object current = weak.get();
                    if (current != null) drawRows(current, state);
                }));
                Object sprite = NativeItemIcons.add(screen, item.id(),
                        cellX + 1, cellY + 1, item.name());
                if (sprite != null) state.rows.add(sprite);
            }
            if (results.isEmpty())
                addRow(screen, state, "No matching items", x, y, width, () -> {});

            int navY = y + state.visibleRows * CELL + 3;
            addRow(screen, state, "< Previous", x, navY, (width - 4) / 2, () -> {
                if (state.page > 0) state.page--;
                Object current = weak.get();
                if (current != null) drawRows(current, state);
            });
            addRow(screen, state, "Next >", x + (width + 4) / 2, navY,
                    (width - 4) / 2, () -> {
                        if (!getPagePreview(state).isEmpty()) state.page++;
                        Object current = weak.get();
                        if (current != null) drawRows(current, state);
                    });
            addRow(screen, state, "Page " + (state.page + 1)
                    + " | Hover for item names", x, navY + 21, width, () -> {});
        } catch (ReflectiveOperationException | RuntimeException failure) {
            if (!warned) {
                warned = true;
                System.err.println("[H.O.W.L. Codex] Item grid failed: " + failure);
            }
        }
    }

    private List<ItemEntry> getPagePreview(State state) {
        if (catalog == null) return List.of();
        return catalog.search(state.query, state.page + 1, state.pageSize);
    }

    private void addRow(Object screen, State state, String label, int x, int y,
                        int width, Runnable action) throws ReflectiveOperationException {
        state.rows.add(NativeGui.button(screen, label, x, y,
                Math.max(30, width), action));
    }

    private static void close(Object screen, State state) {
        NativeGui.remove(screen, state.rows);
        NativeGui.remove(screen, state.chrome);
        if (state.search != null) NativeGui.remove(screen, List.of(state.search));
        state.rows.clear();
        state.chrome.clear();
        state.search = null;
    }
}
