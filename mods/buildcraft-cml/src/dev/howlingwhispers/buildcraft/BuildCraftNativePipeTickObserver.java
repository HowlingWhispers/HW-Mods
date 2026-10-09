package dev.howlingwhispers.buildcraft;

import dev.howlingwhispers.codaloader.api.CodaBlockEntityTick;
import java.util.Objects;

/**
 * Diagnostic only: proves Minecraft called the native EntityBlock ticker.
 *
 * Original BCCE TilePipeHolder.update() is the source of game behavior to
 * connect after its NBT/ItemStack dependencies are ported. Never substitute
 * this observer for PipeFlowItems or TravellingItem gameplay.
 */
public final class BuildCraftNativePipeTickObserver {
    public static final String TYPE = "buildcrafttransport:pipe_holder";
    private static long ticks;
    private static CodaBlockEntityTick last;

    private BuildCraftNativePipeTickObserver() {}

    public static synchronized void observe(CodaBlockEntityTick event) {
        Objects.requireNonNull(event, "event");
        if (!TYPE.equals(event.typeId()))
            throw new IllegalArgumentException("Refusing non-BCCE pipe-holder tick");
        ticks++;
        last = event;
    }

    /** Isolated development smoke-test counters, not persistent game state. */
    public static synchronized long ticksObserved() { return ticks; }
    public static synchronized CodaBlockEntityTick lastObserved() { return last; }
}
