import dev.howlingwhispers.codaloader.api.*;
import dev.howlingwhispers.buildcraft.BuildCraftNativePipeTickObserver;

public final class BuildCraftNativePipeTickObserverTest {
    public static void main(String[] args) {
        String type=BuildCraftNativePipeTickObserver.TYPE;
        CodaBlockEntityTick wooden=new CodaBlockEntityTick(
                type,"minecraft:overworld",new CodaBlockPos(1,64,0));
        CodaBlockEntityTick cobble=new CodaBlockEntityTick(
                type,"minecraft:overworld",new CodaBlockPos(2,64,0));
        BuildCraftNativePipeTickObserver.observe(wooden);
        BuildCraftNativePipeTickObserver.observe(cobble);
        if (BuildCraftNativePipeTickObserver.ticksObserved()!=2 ||
                !cobble.equals(BuildCraftNativePipeTickObserver.lastObserved()))
            throw new AssertionError("Native Minecraft pipe tick signals not observed");
        try {
            BuildCraftNativePipeTickObserver.observe(new CodaBlockEntityTick(
                    "other:machine","minecraft:overworld",new CodaBlockPos(0,64,0)));
            throw new AssertionError("Non-BCCE native tile accepted");
        } catch (IllegalArgumentException expected) { /* correct */ }
        if (BuildCraftNativePipeTickObserver.ticksObserved()!=2)
            throw new AssertionError("Rejected native tick must not change runtime state");
        System.out.println("PASS: BuildCraft native pipe-holder callback forwards Minecraft ticks");
    }
}
