package dev.howlingwhispers.codex;

import java.util.List;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.world.level.Level;

public final class CodexNativeGuiTest {
    private static final class Client {
        public final Object font = new Object();
        public final Level level = new Level();
    }
    private static Button find(InventoryScreen screen, String prefix) {
        return screen.children().stream().filter(w -> w instanceof Button)
                .map(w -> (Button) w).filter(b -> b.label.startsWith(prefix))
                .findFirst().orElseThrow(() -> new AssertionError("No button: " + prefix));
    }
    private static List<Button> slots(InventoryScreen screen) {
        return screen.children().stream().filter(w -> w instanceof Button)
                .map(w -> (Button) w).filter(b -> b.label.isEmpty() &&
                        b.width == 18 && b.height == 18).toList();
    }
    private static EditBox search(InventoryScreen screen) {
        return screen.children().stream().filter(w -> w instanceof EditBox)
                .map(w -> (EditBox) w).findFirst()
                .orElseThrow(() -> new AssertionError("No Codex search field"));
    }
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        var screen = new InventoryScreen();
        screen.width = 550; screen.height = 300;
        screen.leftPos = (screen.width - screen.imageWidth) / 2;
        // The screenshot's problematic state: vanilla recipe book open,
        // inventory shifted to the right, old Codex text buttons overlapping.
        screen.book().toggleVisibility();
        screen.leftPos += 60;

        var client = new Client();
        var panel = new CodexInventoryPanel();
        panel.refresh(client, screen);
        check(screen.children().size() == 1, "Codex button not attached");
        panel.refresh(client, screen);
        check(screen.children().size() == 1, "Repeated refresh duplicated Codex");
        find(screen, "Codex").click();
        check(find(screen, "Codex v0.1.0-dev.4") != null,
                "Codex version must be visible inside the inventory");
        check(!screen.book().isVisible(), "Codex should close vanilla recipe book");
        check(screen.leftPos == (screen.width - screen.imageWidth) / 2,
                "Closing recipe book failed to recenter inventory");
        check(search(screen).getValue().isEmpty(), "Search should start blank");
        List<Button> visible = slots(screen);
        check(visible.size() > 35, "Expected a multi-column NEI item grid, got " +
                visible.size());
        int inventoryRight = screen.leftPos + screen.imageWidth;
        for (Button slot : visible) {
            check(slot.x >= inventoryRight + 8,
                    "Icon slot overlaps vanilla inventory: " + slot.x);
            check(slot.x + slot.width <= screen.width, "Slot extends past screen");
        }
        Button initial = visible.get(0);
        check(screen.testIconRenderCount() >= visible.size(),
                "All slots should render real Minecraft item sprites");
        check(screen.testTooltipCount(initial.x + 3, initial.y + 3) > 0,
                "Hovered item must show its readable tooltip");

        // Preserve existing filters and recipe/usage navigation.
        search(screen).setValue("@minecraft iron");
        check(!slots(screen).isEmpty(), "Search produced no clickable icon");
        slots(screen).get(0).click();
        find(screen, "R  Known recipes").click();
        find(screen, "Iron Ingot x1").click();
        find(screen, "Type: minecraft:furnace").click();
        find(screen, "< Recipe results").click();
        find(screen, "< Back to Iron Ingot").click();
        find(screen, "U  Known uses").click();
        find(screen, "Sorting Pipe").click();
        find(screen, "Ingredients (one per slot)").click();
        find(screen, "< Recipe results").click();
        find(screen, "< Back to Iron Ingot").click();
        find(screen, "< Back to item grid").click();
        check(slots(screen).size() > 0, "Going back failed to restore icon grid");

        search(screen).setValue("");
        find(screen, "Next >").click();
        check(find(screen, "Page 2") != null, "Next page is not available");
        check(slots(screen).size() > 0, "Page 2 did not show any icons");
        find(screen, "< Previous").click();
        check(find(screen, "Page 1") != null, "Previous page not restored");

        screen.rebuildWidgets();
        panel.refresh(client, screen);
        check(slots(screen).size() > 35, "Rebuild failed to restore icon grid");
        find(screen, "Codex").click();
        check(screen.children().size() == 1, "Closing left stale icon targets");
        check(screen.testIconRenderCount() == 0, "Closing left sprites behind");

        System.out.println("CodexNativeGuiTest passed: NEI grid, hover, " +
                "side margin, vanilla recipe book reflow and R/U navigation");
    }
}
