package dev.howlingwhispers.buildcraft;

import dev.howlingwhispers.codaloader.api.CodaContext;
import dev.howlingwhispers.codaloader.api.CodaMod;

/**
 * Native H.O.W.L. BuildCraft port, using BCCE modern source as reference.
 * Development-only: initial native transport adapter, not full BCCE parity.
 */
public final class BuildCraftCmlMod implements CodaMod {
    private static final BuildCraftTransportRuntime TRANSPORT = new BuildCraftTransportRuntime();
    private static final BuildCraftEngineRuntime ENGINES = new BuildCraftEngineRuntime();

    /** Reserved for the future Minecraft block/chunk lifecycle adapter. */
    public static BuildCraftTransportRuntime transportRuntime() {
        return TRANSPORT;
    }

    @Override
    public void onInitialize(CodaContext context) {
        // BCCE item identities and original BuildCraft art; H.O.W.L. only
        // adapts the Minecraft 26.4 Snapshot 3 registry lifecycle.
        context.registerBlock("buildcrafttransport:wood_item", 0.7f);
        context.registerBlock("buildcrafttransport:cobblestone_item", 1.4f);
        // BCCE's original BlockPipeHolder/TilePipeHolder model shares one
        // native Minecraft block-entity type across the item pipe variants.
        // H.O.W.L. supplies the entity registration seam only; no invented
        // pipe inventory, moving packet, or MJ receiver is enabled yet.
        context.registerBlockEntityType("buildcrafttransport:pipe_holder", java.util.List.of(
                "buildcrafttransport:wood_item", "buildcrafttransport:cobblestone_item"));
        context.registerBlockEntityTick("buildcrafttransport:pipe_holder",
                BuildCraftNativePipeTickObserver::observe);
        context.registerBlock("buildcraftcore:engine_redstone", 1.5f);
        context.registerItem("buildcraftcore:wrench");
        context.registerCreativeTab("buildcraftcore:buildcraft", "BuildCraft",
                "buildcrafttransport:wood_item", java.util.List.of(
                    "buildcrafttransport:wood_item",
                    "buildcrafttransport:cobblestone_item",
                    "buildcraftcore:engine_redstone",
                    "buildcraftcore:wrench"));
        context.registerBlockPlacement(ENGINES::onPlacement);
        context.registerServerTick("real_engine_transport", ENGINES::onServerTick);
        context.registerServerTick("pipe_transport", TRANSPORT::onServerTick);
        context.registerCommand("buildcraft", "Inspect nearby native BuildCraft pipes and engines",
                (command, arguments) -> {
                    if (!arguments.isEmpty() && !arguments.equals(java.util.List.of("status"))) {
                        command.reply("Usage: /buildcraft [status]");
                        return;
                    }
                    command.reply("BuildCraft 0.0.1-dev.1: "
                            + ENGINES.inspect(command.position(), command.worldDirectory()));
                });
        // Do not expose the obsolete /buildcraft pulse chest teleportation
        // command. Its JVM fixture remains separately testable, but genuine
        // player transport requires BCCE pipe block entities and MJ ports.
        System.out.println("[BuildCraft H.O.W.L.] Native BCCE redstone-engine MJ and piston "
                + "state enabled; real pipe route inspection available via /buildcraft status; " +
                "item-stack transport is not installed.");
    }
}
