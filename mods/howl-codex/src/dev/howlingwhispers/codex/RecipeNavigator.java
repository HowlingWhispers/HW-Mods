package dev.howlingwhispers.codex;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Navigable native-widget viewer for real synced, unlocked Minecraft recipes. */
final class RecipeNavigator {
    private final WeakReference<Object> screen;
    private final List<Object> widgets;
    private final List<RecipeEntry> recipes;
    private final ItemEntry selected;
    private final ItemIndex items;
    private final Runnable onBack;
    private final boolean usage;
    private final int x, width, rowsPerPage;
    private int page;
    private RecipeEntry detail;

    private RecipeNavigator(Object screen, List<Object> widgets, List<RecipeEntry> recipes,
                            ItemEntry selected, ItemIndex items, Runnable onBack,
                            boolean usage, int x, int width, int visibleRows) {
        this.screen = new WeakReference<>(screen);
        this.widgets = widgets;
        this.recipes = recipes;
        this.selected = selected;
        this.items = items;
        this.onBack = onBack;
        this.usage = usage;
        this.x = x;
        this.width = width;
        this.rowsPerPage = Math.max(1, visibleRows - 3);
    }

    static void open(Object client, Object screen, ItemEntry item, ItemIndex items,
                     boolean usage, List<Object> widgets, Runnable onBack,
                     int x, int width, int visibleRows) {
        try {
            if (client == null) throw new IllegalStateException("No active client");
            RecipeIndex index = NativeRecipeCatalog.load(client, screen);
            var found = usage ? index.usagesOf(item.id()) : index.recipesFor(item.id());
            var view = new RecipeNavigator(screen, widgets, found, item, items,
                    onBack, usage, x, width, visibleRows);
            view.draw();
        } catch (ReflectiveOperationException | RuntimeException error) {
            System.err.println("[H.O.W.L. Codex] Recipe sync unavailable: " + error);
            NativeGui.remove(screen, widgets);
            widgets.clear();
            try {
                widgets.add(NativeGui.button(screen, "< Back to item", x, 70, width,
                        onBack));
                widgets.add(NativeGui.button(screen, "Recipe data unavailable", x, 93,
                        width, () -> {}));
            } catch (ReflectiveOperationException ignored) {}
        }
    }

    private void draw() {
        Object current = screen.get();
        if (current == null) return;
        NativeGui.remove(current, widgets);
        widgets.clear();
        try {
            if (detail != null) {
                detail(current);
            } else {
                list(current);
            }
        } catch (ReflectiveOperationException | RuntimeException failure) {
            System.err.println("[H.O.W.L. Codex] Recipe view failed: " + failure);
        }
    }

    private void row(Object current, String text, int y, Runnable click)
            throws ReflectiveOperationException {
        widgets.add(NativeGui.button(current, NativeGui.clip(text, 34),
                x, y, width, click));
    }

    private void list(Object current) throws ReflectiveOperationException {
        row(current, "< Back to " + selected.name(), 70, onBack);
        row(current, "Known " + (usage ? "uses" : "recipes") + ": " + recipes.size(),
                91, () -> {});
        if (recipes.isEmpty()) {
            row(current, "No unlocked recipes found", 116, () -> {});
            return;
        }
        int first = page * rowsPerPage;
        for (int i = first; i < Math.min(recipes.size(), first + rowsPerPage); i++) {
            RecipeEntry recipe = recipes.get(i);
            int yy = 116 + (i - first) * 20;
            Object icon = NativeItemIcons.add(current, recipe.outputItemId(), x + 3, yy + 1);
            if (icon != null) widgets.add(icon);
            widgets.add(NativeGui.button(current, NativeGui.clip(
                    label(recipe.outputItemId()) + " x" + recipe.outputCount(), 30),
                    x + 22, yy, width - 22,
                    () -> { detail = recipe; page = 0; draw(); }));
        }
        int y = 120 + rowsPerPage * 20;
        widgets.add(NativeGui.button(current, "< Previous", x, y,
                (width - 6) / 2, () -> { page = Math.max(0, page - 1); draw(); }));
        widgets.add(NativeGui.button(current, "Next >", x + (width + 6) / 2, y,
                (width - 6) / 2, () -> {
                    if ((page + 1) * rowsPerPage < recipes.size()) page++;
                    draw();
                }));
        row(current, "Only unlocked, synced recipes", y + 20, () -> {});
    }

    private void detail(Object current) throws ReflectiveOperationException {
        RecipeEntry selectedRecipe = detail;
        row(current, "< Recipe results", 70, () -> { detail = null; page = 0; draw(); });
        row(current, "Output: " + label(selectedRecipe.outputItemId()) +
                " x" + selectedRecipe.outputCount(), 92, () -> {});
        row(current, "Type: " + selectedRecipe.type(), 114, () -> {});
        row(current, "Ingredients (one per slot):", 136, () -> {});
        List<Set<String>> inputs = selectedRecipe.ingredients();
        int perPage = Math.max(1, rowsPerPage - 2);
        for (int i = page * perPage; i < Math.min(inputs.size(), (page + 1) * perPage); i++) {
            var slot = inputs.get(i);
            String summary = slot.stream().sorted().limit(2).map(this::label)
                    .collect(java.util.stream.Collectors.joining(" / "));
            if (slot.size() > 2) summary += " / ...";
            row(current, (i + 1) + ". " + summary,
                    159 + (i - page * perPage) * 20, () -> {});
        }
        if (inputs.isEmpty()) row(current, "No resolved ingredients", 159, () -> {});
        int y = 164 + perPage * 20;
        row(current, "Next ingredient page >", y, () -> {
            page = (page + 1) % Math.max(1, (inputs.size() + perPage - 1) / perPage);
            draw();
        });
    }

    private String label(String id) {
        for (ItemEntry item : items.all())
            if (item.id().equals(id)) return item.name();
        return id.contains(":") ? id.substring(id.indexOf(':') + 1).replace('_', ' ') : id;
    }
}
