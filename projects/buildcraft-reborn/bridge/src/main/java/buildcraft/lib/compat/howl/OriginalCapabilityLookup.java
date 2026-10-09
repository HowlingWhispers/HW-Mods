package buildcraft.lib.compat.howl;

import buildcraft.lib.internal.capabilities.IBCCapabilityProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

/**
 * Preserve BCCE's actual sided provider dispatch for *loaded* pipe holders.
 *
 * Equivalent source-layer replacement for NeoForge's positional
 * Level.getCapability(CAP_PIPE/CAP_PLUG/...).
 *
 * BCCE's own TilePipeHolder/pipe/pluggables still decide which values exist
 * and which sides are blocked. No synthesized pipes, neighbours or items.
 * Non-BCCE inventory/fluid capability providers remain a separate port task.
 */
public final class OriginalCapabilityLookup {
    private OriginalCapabilityLookup() {}

    public static <T> T get(Level level, BlockPos position, Direction side,
                            BlockCapability<T,Direction> capability) {
        return NativeCapabilityAccess.get(level, position, side, capability,
                (entity, key, requestedSide) ->
                        entity instanceof IBCCapabilityProvider provider
                        ? provider.getCapability(key, requestedSide) : null);
    }
}
