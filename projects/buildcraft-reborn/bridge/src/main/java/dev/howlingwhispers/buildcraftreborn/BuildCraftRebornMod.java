package dev.howlingwhispers.buildcraftreborn;

import buildcraft.lib.compat.howl.ActiveModNamespace;
import buildcraft.transport.block.BlockPipeHolder;
import buildcraft.transport.tile.TilePipeHolder;
import dev.howlingwhispers.codaloader.api.CodaContext;
import dev.howlingwhispers.codaloader.api.CodaMod;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * Source-only bridge between REAL upstream BCCE BlockPipeHolder/TilePipeHolder
 * and H.O.W.L.'s native registry factories. No synthetic network, no custom
 * item transport, no placeholder blocks, no JSON mod metadata or JAR yet.
 *
 * DO NOT PACKAGE until this bridge and all upstream dependencies compile
 * against the actual Minecraft 26.4 Snapshot 3 and live lifecycle tests pass.
 */
public final class BuildCraftRebornMod implements CodaMod {
    private static final String HOLDER_ID = "hw_buildcraft_reborn:pipe_holder";

    @Override
    public void onInitialize(CodaContext context) {
        if (!"hw_buildcraft_reborn".equals(context.modId()))
            throw new IllegalStateException("Unexpected mod ID; retired BuildCraft must stay isolated");
        if (!"26.4-snapshot-3".equals(context.minecraftVersion()))
            throw new IllegalStateException("BuildCraft Reborn requires exact Minecraft 26.4 Snapshot 3");

        // The upstream BuildCraft implementation uses ONE BlockPipeHolder
        // for many pipe variants. Never invent cobblestone/wood replacement
        // blocks here: BCCE's own ItemPipeHolder supplies actual pipe variants.
        try (var scope = ActiveModNamespace.enter(context.modId())) {
            context.registerBlock(HOLDER_ID, 0.25f);
            context.registerBlockEntityType(HOLDER_ID, List.of(HOLDER_ID));
    
            // Stage script preserves BlockPipeHolder's original geometry,
            // waterlogging, interactions and EntityBlock.getTicker().
            // H.O.W.L. supplies only Mojang's required keyed Properties.
            context.registerNativeKeyedBlockFactory(HOLDER_ID,
                    nativeProperties -> new BlockPipeHolder(
                            (BlockBehaviour.Properties) nativeProperties));
    
            // This constructs the ORIGINAL BCCE TilePipeHolder. Its original
            // writeData/readData, Pipe.onTick, pluggables and wire manager are
            // retained. The NeoForge dependency closure must be ported before
            // this source is legal to ship.
            context.registerNativeBlockEntityFactory(HOLDER_ID,
                    (pos, state) -> new TilePipeHolder(
                            (BlockPos) pos, (BlockState) state));
        }
        // No H.O.W.L. custom registerBlockEntityTick: original
        // BlockPipeHolder.getTicker calls original TilePipeHolder.update().
    }
}
