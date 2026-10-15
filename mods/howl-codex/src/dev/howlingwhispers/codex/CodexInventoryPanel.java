package dev.howlingwhispers.codex;

import dev.howlingwhispers.codaloader.api.CodaInventoryPanels;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Working development panel: native search field, result buttons and pages.
 * No item mutation, save modification or direct client recipe fabrication.
 */
public final class CodexInventoryPanel implements CodaInventoryPanels.Panel {
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
                if (state.open && (state.search == null || !NativeGui.attached(screen, state.search)))
                    show(minecraftClient, screen, state);
                return;
            }
            state.root = null;
            state.search = null;
            state.chrome.clear();
            state.rows.clear();
            int screenWidth = NativeGui.dimension(screen, "width");
            var weak = new WeakReference<>(screen);
            state.root = NativeGui.button(screen, "Codex", Math.max(2, screenWidth - 77),
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
                System.err.println("[H.O.W.L. Codex] Native inventory GUI unavailable: " + error);
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
            int w = NativeGui.dimension(screen, "width");
            int h = NativeGui.dimension(screen, "height");
            final int x = w >= 580 ? Math.max(4, w - 221) : Math.max(4, w - 215);
            final int width = Math.min(205, w - x - 4);
            if (width < 100 || h < 170) {
                state.note = "Increase window size or reduce GUI scale";
                return;
            }
            state.visibleRows = Math.max(2, Math.min(12, (h - 121) / 20));
            var weak = new WeakReference<>(screen);
            state.chrome.add(NativeGui.button(screen, "H.O.W.L. CODEX  (" + items.size() + ")",
                    x, 26, width, () -> {
                        Object current = weak.get();
                        if (current == null) return;
                        state.selected = null;
                        state.page = 0;
                        drawRows(current, state, x, width);
                    }));
            state.search = NativeGui.textBox(client, screen, state.query, x, 47, width,
                    query -> {
                        state.query = query;
                        state.page = 0;
                        state.selected = null;
                        Object current = weak.get();
                        if (current != null) drawRows(current, state, x, width);
                    });
            drawRows(screen, state, x, width);
            System.out.println("[H.O.W.L. Codex] Native searchable inventory panel opened ("
                    + items.size() + " registered items).");
        } catch (ReflectiveOperationException | RuntimeException error) {
            state.note = "Codex GUI mapping missing: " + error.getClass().getSimpleName();
            if (!warned) {
                warned = true;
                System.err.println("[H.O.W.L. Codex] Cannot open panel: " + error);
            }
            close(screen, state);
        }
    }

    private void drawRows(Object screen, State state, int x, int width) {
        NativeGui.remove(screen, state.rows);
        state.rows.clear();
        try {
            final int y = 70;
            var weak = new WeakReference<>(screen);
            if (state.selected != null) {
                ItemEntry item = state.selected;
                addRow(screen, state, "<  Back to items", x, y, width, () -> {
                    state.selected = null;
                    Object current = weak.get();
                    if (current != null) drawRows(current, state, x, width);
                });
                Object selectedIcon = NativeItemIcons.add(screen, item.id(), x + 3, y + 23);
                if (selectedIcon != null) state.rows.add(selectedIcon);
                addRow(screen, state, NativeGui.clip(item.name(), 28),
                        x + 22, y + 22, width - 22, () -> {});
                addRow(screen, state, NativeGui.clip(item.id(), 31),
                        x, y + 44, width, () -> {});
                addRow(screen, state, "Mod: " + NativeGui.clip(item.modId(), 22),
                        x, y + 66, width, () -> {});
                addRow(screen, state, "R  Known recipes", x, y + 92, width, () -> {
                    Object current = weak.get();
                    if (current != null)
                        RecipeNavigator.open(state.client.get(), current, item, catalog,
                                false, state.rows, () -> drawRows(current, state, x, width),
                                x, width, state.visibleRows);
                });
                addRow(screen, state, "U  Known uses", x, y + 115, width, () -> {
                    Object current = weak.get();
                    if (current != null)
                        RecipeNavigator.open(state.client.get(), current, item, catalog,
                                true, state.rows, () -> drawRows(current, state, x, width),
                                x, width, state.visibleRows);
                });
                addRow(screen, state, NativeGui.clip(state.note.isBlank()
                        ? "Survival-safe | Read only" : state.note, 33),
                        x, y + 139, width, () -> {});
                return;
            }

            List<ItemEntry> results = getCatalog().search(state.query, state.page,
                    state.visibleRows);
            if (results.isEmpty()) {
                addRow(screen, state, "No items match this search", x, y, width, () -> {});
            }
            int line = 0;
            for (ItemEntry item : results) {
                int position = line++;
                Object sprite = NativeItemIcons.add(screen, item.id(),
                        x + 3, y + position * 20 + 1);
                if (sprite != null) state.rows.add(sprite);
                addRow(screen, state, NativeGui.clip(item.name(), 28),
                        x + 22, y + position * 20, width - 22, () -> {
                            state.selected = item;
                            state.note = "";
                            Object current = weak.get();
                            if (current != null) drawRows(current, state, x, width);
                        });
            }
            int navY = y + state.visibleRows * 20 + 2;
            addRow(screen, state, "< Previous", x, navY, (width - 6)/2, () -> {
                if (state.page > 0) state.page--;
                Object current = weak.get();
                if (current != null) drawRows(current, state, x, width);
            });
            addRow(screen, state, "Next >", x + (width + 6)/2, navY, (width - 6)/2, () -> {
                if (!getPagePreview(state).isEmpty()) state.page++;
                Object current = weak.get();
                if (current != null) drawRows(current, state, x, width);
            });
            addRow(screen, state, "Page " + (state.page + 1) + "  |  Select item for details",
                    x, navY + 20, width, () -> {});
        } catch (ReflectiveOperationException | RuntimeException failure) {
            if (!warned) {
                warned = true;
                System.err.println("[H.O.W.L. Codex] Inventory row render failed: " + failure);
            }
        }
    }

    private List<ItemEntry> getPagePreview(State state) {
        if (catalog == null) return List.of();
        return catalog.search(state.query, state.page + 1, state.visibleRows);
    }

    private void addRow(Object screen, State state, String label, int x, int y, int width,
                        Runnable action) throws ReflectiveOperationException {
        state.rows.add(NativeGui.button(screen, label, x, y, Math.max(30,width), action));
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
