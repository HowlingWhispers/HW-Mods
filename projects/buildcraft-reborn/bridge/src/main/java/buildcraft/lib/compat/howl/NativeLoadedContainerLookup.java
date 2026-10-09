package buildcraft.lib.compat.howl;

import buildcraft.api.v2.item.ItemPort;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Read-only discovery of real, already-loaded vanilla inventory endpoints.
 *
 * This is an endpoint lookup, NOT a replacement pipe transport or a generic
 * replacement for BCCE's own pipe/pluggable capability resolution.
 * PipeFlowItems must remain responsible for when an endpoint is used.
 */
public final class NativeLoadedContainerLookup {
    private NativeLoadedContainerLookup() {}

    /**
     * @return a sided BCCE ItemPort for the genuine native block inventory,
     *         or null when the chunk or inventory is unavailable.
     */
    public static ItemPort get(Level level, BlockPos pos, Direction insertionFace) {
        if (level == null || pos == null || level.isClientSide() || !level.isLoaded(pos)) return null;
        BlockEntity entity = level.getBlockEntity(pos);
        if (entity == null || entity.isRemoved()) return null;
        if (!(entity instanceof Container container)) return null;
        return new NativeContainerItemPort(container, insertionFace);
    }
}
