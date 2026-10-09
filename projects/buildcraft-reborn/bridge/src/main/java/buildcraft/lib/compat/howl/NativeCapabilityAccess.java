package buildcraft.lib.compat.howl;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import java.util.Objects;

/**
 * Real, loaded Minecraft block-entity lookup for BCCE pipe capability routing.
 *
 * Callers supply the original BCCE IBCCapabilityProvider lookup implementation.
 * The adapter NEVER creates a tile, loads a missing chunk, synthesizes an
 * inventory, bypasses a pluggable, or transfers items.
 */
public final class NativeCapabilityAccess {
    private NativeCapabilityAccess() {}

    @FunctionalInterface
    public interface OriginalProvider {
        Object lookup(BlockEntity nativeBlockEntity, BlockCapability<?,Direction> capability,
                      Direction requestedSide);
    }

    public static <T> T get(Level level, BlockPos pos, Direction targetSide,
                            BlockCapability<T,Direction> capability,
                            OriginalProvider originalProvider) {
        Objects.requireNonNull(capability, "capability");
        Objects.requireNonNull(originalProvider, "originalProvider");
        if (level == null || pos == null || !level.isLoaded(pos)) return null;
        BlockEntity entity = level.getBlockEntity(pos);
        if (entity == null || entity.isRemoved()) return null;
        Object returned = originalProvider.lookup(entity, capability, targetSide);
        return capability.checkedValue(returned);
    }
}
