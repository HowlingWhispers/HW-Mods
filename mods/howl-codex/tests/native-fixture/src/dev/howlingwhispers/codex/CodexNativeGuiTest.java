package dev.howlingwhispers.codex;

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
        var client = new Client();
        var panel = new CodexInventoryPanel();
        panel.refresh(client, screen);
        check(screen.children().size() == 1, "Codex entry button not attached");
        panel.refresh(client, screen);
        check(screen.children().size() == 1, "Repeated refresh duplicated entry");
        find(screen, "Codex").click();
        check(search(screen).getValue().isEmpty(), "Search should begin blank");
        search(screen).setValue("@minecraft iron");
        find(screen, "Iron Ingot").click();
        find(screen, "U  Known uses").click();
        find(screen, "Sorting Pipe").click();
        find(screen, "Ingredients (one per slot)").click();
        find(screen, "< Recipe results").click();
        find(screen, "< Back to Iron Ingot").click();
        find(screen, "R  Known recipes").click();
        find(screen, "Iron Ingot x1").click();
        find(screen, "Type: minecraft:furnace").click();
        find(screen, "< Recipe results").click();
        find(screen, "< Back to Iron Ingot").click();
        find(screen, "<  Back to items").click();
        check(screen.children().size() > 4, "Search panel should remain open");
        panel.refresh(client, screen);
        screen.rebuildWidgets();
        panel.refresh(client, screen);
        check(screen.children().size() > 4, "Inventory rebuild did not restore overlay");
        find(screen, "Codex").click();
        check(screen.children().size() == 1, "Closing left native widgets");
        System.out.println("CodexNativeGuiTest passed");
    }
}