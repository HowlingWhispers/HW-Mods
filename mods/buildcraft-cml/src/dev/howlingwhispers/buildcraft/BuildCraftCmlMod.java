package dev.howlingwhispers.buildcraft;

import dev.howlingwhispers.codaloader.api.CodaContext;
import dev.howlingwhispers.codaloader.api.CodaMod;

/**
 * Unofficial BuildCraft Refabricated-inspired port for native H.O.W.L.
 * Development-only: the actual block and inventory adapter is not yet shipped.
 */
public final class BuildCraftCmlMod implements CodaMod {
    private static final BuildCraftTransportRuntime TRANSPORT = new BuildCraftTransportRuntime();

    /** Reserved for the future Minecraft block/chunk lifecycle adapter. */
    public static BuildCraftTransportRuntime transportRuntime() {
        return TRANSPORT;
    }

    @Override
    public void onInitialize(CodaContext context) {
        context.registerServerTick("pipe_transport", TRANSPORT::onServerTick);
        context.registerCommand("buildcraft", "BuildCraft CML port status", (source, args) -> {
            if (!args.isEmpty()) throw new IllegalArgumentException("Usage: /buildcraft");
            source.reply("BuildCraft CML 0.1.0-dev: server-tick transport bridge is ready; "
                    + "pipe blocks, chest inventory adapters and recipes are not installed yet.");
        });
        System.out.println("[BuildCraft CML] Server-tick transport bridge registered. "
                + "Minecraft block/item integration pending.");
    }
}
