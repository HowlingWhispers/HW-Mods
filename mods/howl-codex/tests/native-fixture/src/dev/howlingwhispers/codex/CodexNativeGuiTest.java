package dev.howlingwhispers.codex;

import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;

public final class CodexNativeGuiTest {
    private static final class Client {
        public final Object font = new Object();
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
    private static void assertTrue(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        var screen = new InventoryScreen();
        var client = new Client();
        var panel = new CodexInventoryPanel();
        panel.refresh(client, screen);
        assertTrue(screen.children().size() == 1, "Codex entry button not attached");
        panel.refresh(client, screen);
        assertTrue(screen.children().size() == 1, "Repeated refresh duplicated entry");
        find(screen, "Codex").click();
        assertTrue(search(screen).getValue().isEmpty(), "Search should begin blank");
        assertTrue(screen.children().size() > 4, "Opening did not attach result widgets");
        search(screen).setValue("@minecraft iron");
        find(screen, "Iron Ingot").click();
        find(screen, "R  Show known recipes").click();
        assertTrue(screen.book().isVisible(), "Vanilla recipe book not opened");
        assertTrue(screen.book().searchText().equals("Iron Ingot"),
                "Vanilla recipes were not filtered");
        panel.refresh(client, screen);
        assertTrue(screen.children().size() > 1, "Panel disappeared on repeated refresh");
        screen.rebuildWidgets();
        panel.refresh(client, screen);
        assertTrue(screen.children().size() > 4, "Inventory rebuild did not restore overlay");
        find(screen, "Codex").click();
        assertTrue(screen.children().size() == 1, "Codex close left widget debris");
        System.out.println("CodexNativeGuiTest passed");
    }
}