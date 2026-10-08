package dev.howlingwhispers.buildcraft;

import dev.howlingwhispers.codaloader.api.CodaContext;
import dev.howlingwhispers.codaloader.api.CodaMod;

/**
 * Native H.O.W.L. BuildCraft port, using original BuildCraft as reference.
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
        // Actual native-chest test, available only when this development mod
        // is installed alongside the experimental H.O.W.L. world command bridge.
        context.registerCommand("buildcraft", "BuildCraft chest/pipe single-player test",
                BuildCraftGlassPipeDemo::execute);
        System.out.println("[BuildCraft CML] Server-tick transport bridge registered. "
                + "Minecraft block/item integration pending.");
    }
}
