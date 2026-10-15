package dev.howlingwhispers.codex;

import dev.howlingwhispers.codaloader.api.CodaContext;
import dev.howlingwhispers.codaloader.api.CodaMod;

/**
 * Development-only entry point. This milestone intentionally does not modify
 * any Minecraft inventory screen or spawn items; the native GUI bridge is a
 * separate loader prerequisite.
 */
public final class HowlCodexMod implements CodaMod {
    @Override
    public void onInitialize(CodaContext context) {
        context.registerCommand("howlcodex", "Show H.O.W.L. Codex development status",
                (player, arguments) -> player.reply(
                        "H.O.W.L. Codex 0.1.0-dev.1: search/recipe index foundation loaded. "
                        + "Inventory overlay is not yet available in this development build."));
        System.out.println("[H.O.W.L. Codex] 0.1.0-dev.1 core initialized; native inventory UI pending.");
    }
}
