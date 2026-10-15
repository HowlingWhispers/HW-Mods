package dev.howlingwhispers.codex;

import dev.howlingwhispers.codaloader.api.CodaContext;
import dev.howlingwhispers.codaloader.api.CodaMod;

/** A GUI-thread, inventory-only native widget prototype for Minecraft 26.4. */
public final class HowlCodexMod implements CodaMod {
    private final CodexInventoryPanel inventoryPanel = new CodexInventoryPanel();

    @Override public void onInitialize(CodaContext context) {
        context.registerInventoryPanel("howl_codex:item_browser", 10, inventoryPanel);
        context.registerCommand("howlcodex", "Help for the H.O.W.L. item browser",
                (player, arguments) -> player.reply(
                        "H.O.W.L. Codex: open your player inventory and click Codex "
                        + "in the top-right. Search by name, @mod or #tag. "
                        + "Select an item to see its ID and known vanilla recipes. "
                        + "Development prototype: read-only, no item-give cheats."));
        System.out.println("[H.O.W.L. Codex] Native inventory prototype registered.");
    }
}
