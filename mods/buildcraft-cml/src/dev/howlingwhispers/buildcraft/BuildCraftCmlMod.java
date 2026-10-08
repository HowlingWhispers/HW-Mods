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
        // ORIGINAL BuildCraft 8.0.0 item identities and art; H.O.W.L. only
        // adapts the Minecraft 26.4 Snapshot 3 registry lifecycle.
        context.registerBlock("buildcrafttransport:wood_item", 0.7f);
        context.registerBlock("buildcrafttransport:cobblestone_item", 1.4f);
        context.registerBlock("buildcraftcore:engine_redstone", 1.5f);
        context.registerItem("buildcraftcore:wrench");
        context.registerCreativeTab("buildcraftcore:buildcraft", "BuildCraft",
                "buildcrafttransport:wood_item", java.util.List.of(
                    "buildcrafttransport:wood_item",
                    "buildcrafttransport:cobblestone_item",
                    "buildcraftcore:engine_redstone",
                    "buildcraftcore:wrench"));
        context.registerServerTick("pipe_transport", TRANSPORT::onServerTick);
        // Actual native-chest test, available only when this development mod
        // is installed alongside the experimental H.O.W.L. world command bridge.
        context.registerCommand("buildcraft", "BuildCraft chest/pipe single-player test",
                BuildCraftGlassPipeDemo::execute);
        System.out.println("[BuildCraft CML] Server-tick transport bridge registered. "
                + "Snapshot 3 native blocks, items and Creative tab queued.");
    }
}
