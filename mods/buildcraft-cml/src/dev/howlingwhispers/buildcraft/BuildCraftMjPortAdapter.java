package dev.howlingwhispers.buildcraft;

import buildcraft.api.v2.OperationMode;
import buildcraft.api.v2.energy.MjAmount;
import buildcraft.api.v2.energy.MjPort;
import buildcraft.api.v2.energy.MjPortRole;
import buildcraft.api.v2.energy.MjTransferResult;
import java.util.Objects;
import java.util.Set;

/**
 * Thin H.O.W.L. compatibility seam between the ported BCCE 8.0.23 engine
 * and BCCE's original, unmodified API2 MJ port contract.
 *
 * This does not create a substitute Minecraft pipe. Native TilePipeHolder
 * ownership/lifecycle still must be ported before runtime hookups are enabled.
 */
public final class BuildCraftMjPortAdapter implements BuildCraftRedstoneEngine.MjEndpoint {
    private final MjPort port;
    private final Set<MjPortRole> roles;

    public BuildCraftMjPortAdapter(MjPort port, Set<MjPortRole> roles) {
        this.port = Objects.requireNonNull(port, "BCCE port");
        this.roles = Set.copyOf(Objects.requireNonNull(roles, "BCCE port roles"));
        if (!this.roles.contains(MjPortRole.CONSUMER)
                && !this.roles.contains(MjPortRole.REDSTONE_RECEIVER))
            throw new IllegalArgumentException("MJ target is not a consumer or redstone receiver");
    }

    @Override
    public boolean redstoneReceiver() {
        return roles.contains(MjPortRole.REDSTONE_RECEIVER);
    }

    private long transferred(long offeredMicroMj, OperationMode mode) {
        if (offeredMicroMj < 0)
            throw new IllegalArgumentException("Negative MJ offered");
        if (offeredMicroMj == 0 || !port.canInsert()) return 0;
        MjAmount offered = MjAmount.ofMicro(offeredMicroMj);
        MjTransferResult result = Objects.requireNonNull(
                port.insert(offered, mode), "BCCE port transfer result");
        if (!result.requested().equals(offered))
            throw new IllegalStateException("BCCE MJ port changed the requested amount");
        return result.transferred().microMj();
    }

    @Override public long simulateInsert(long offered) {
        return transferred(offered, OperationMode.SIMULATE);
    }
    @Override public long executeInsert(long offered) {
        return transferred(offered, OperationMode.EXECUTE);
    }
}
