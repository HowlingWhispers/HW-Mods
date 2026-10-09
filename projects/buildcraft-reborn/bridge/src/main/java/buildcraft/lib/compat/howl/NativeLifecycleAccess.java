package buildcraft.lib.compat.howl;

import java.lang.ref.WeakReference;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;

/** The actual server attached to original BCCE entities, without NeoForge globals. */
public final class NativeLifecycleAccess {
    private static volatile WeakReference<MinecraftServer> server = new WeakReference<>(null);

    private NativeLifecycleAccess() {}

    public static void bind(Level level) {
        MinecraftServer attached = level.getServer();
        if (attached != null && !attached.isStopped()) server = new WeakReference<>(attached);
    }

    public static MinecraftServer getCurrentServer() {
        MinecraftServer attached = server.get();
        return attached == null || attached.isStopped() ? null : attached;
    }
}
