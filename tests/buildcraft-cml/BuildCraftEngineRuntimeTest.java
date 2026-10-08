import dev.howlingwhispers.buildcraft.BuildCraftEngineRuntime;
import dev.howlingwhispers.codaloader.api.CodaBlockPlacements;
import dev.howlingwhispers.codaloader.api.CodaBlockPos;
import dev.howlingwhispers.codaloader.api.CodaInventoryView;
import dev.howlingwhispers.codaloader.api.CodaServerTickContext;
import dev.howlingwhispers.codaloader.api.CodaWorldView;
import java.util.*;

public final class BuildCraftEngineRuntimeTest {
    private static final String DIM = "minecraft:overworld";
    private static int checks;
    private static CodaBlockPos at(int x, int y, int z) { return new CodaBlockPos(x,y,z); }
    private static void check(boolean yes, String description) {
        checks++;
        if (!yes) throw new AssertionError(description);
    }

    static final class NativeWorld implements CodaWorldView {
        final Map<CodaBlockPos,String> blocks = new HashMap<>();
        final Map<CodaBlockPos,Integer> containers = new HashMap<>();
        boolean powered = true;
        boolean loaded = true;
        int transfers;
        @Override public List<String> dimensions() { return List.of(DIM); }
        @Override public boolean isChunkLoaded(String dimension, CodaBlockPos position) {
            return loaded && DIM.equals(dimension);
        }
        @Override public Optional<CodaInventoryView> inventory(String dimension, CodaBlockPos pos) {
            if (!isChunkLoaded(dimension, pos) || !containers.containsKey(pos))
                return Optional.empty();
            int count = containers.get(pos);
            return Optional.of(new CodaInventoryView(pos,
                    List.of(new CodaInventoryView.Slot(0, count, 64))));
        }
        @Override public boolean isBlock(String dimension, CodaBlockPos pos, String id) {
            return loaded && DIM.equals(dimension) && id.equals(blocks.get(pos));
        }
        @Override public boolean hasNeighborSignal(String dimension, CodaBlockPos pos) {
            return loaded && powered && DIM.equals(dimension);
        }
        @Override public int transfer(String dimension, CodaBlockPos from, CodaBlockPos to, int max) {
            if (!isChunkLoaded(dimension, from) || !isChunkLoaded(dimension, to))
                throw new AssertionError("Never transfer unloaded inventories");
            int source = containers.get(from), destination = containers.get(to);
            int n = Math.min(max, Math.min(source, 64-destination));
            if (n > 0) {
                containers.put(from, source-n);
                containers.put(to, destination+n);
                transfers++;
            }
            return n;
        }
    }
    static NativeWorld setup() {
        NativeWorld world = new NativeWorld();
        world.containers.put(at(0,64,0), 19);
        world.containers.put(at(4,64,0), 0);
        world.blocks.put(at(1,65,0), "buildcraftcore:engine_redstone");
        world.blocks.put(at(1,64,0), "buildcrafttransport:wood_item");
        world.blocks.put(at(2,64,0), "buildcrafttransport:cobblestone_item");
        world.blocks.put(at(3,64,0), "buildcrafttransport:cobblestone_item");
        return world;
    }
    static void tick(BuildCraftEngineRuntime runtime, NativeWorld world, String session, long t)
            throws Exception {
        runtime.onServerTick(new CodaServerTickContext(session,t,Optional.of(world)));
    }
    static void place(BuildCraftEngineRuntime runtime) {
        runtime.onPlacement(new CodaBlockPlacements.Placement(
                DIM, at(1,65,0), "buildcraftcore:engine_redstone"));
    }
    public static void main(String[] args) throws Exception {
        BuildCraftEngineRuntime runtime = new BuildCraftEngineRuntime();
        NativeWorld world = setup();
        tick(runtime, world, "one", 1);
        place(runtime);
        world.powered = false;
        tick(runtime, world, "one", 20);
        check(world.transfers == 0, "Unpowered engine must not extract");
        world.powered = true;
        tick(runtime, world, "one", 40);
        check(world.transfers == 1, "Powered engine transfers native chest items");
        check(world.containers.get(at(0,64,0)) == 3 && world.containers.get(at(4,64,0)) == 16,
                "First engine pulse transfers up to 16 while conserving inventory");
        tick(runtime, world, "one", 60);
        check(world.containers.get(at(0,64,0)) == 0 && world.containers.get(at(4,64,0)) == 19,
                "Next engine pulse transfers remaining 3 real items");
        tick(runtime, world, "one", 61);
        check(world.transfers == 2, "No duplicate transfers between 20-tick pulses");

        BuildCraftEngineRuntime missing = new BuildCraftEngineRuntime();
        NativeWorld gap = setup();
        gap.blocks.remove(at(2,64,0));
        tick(missing,gap,"missing",1); place(missing); tick(missing,gap,"missing",20);
        check(gap.transfers == 0, "Disconnected pipe segment stalls instead of teleporting items");
        check(gap.containers.get(at(0,64,0)) == 19, "Source is untouched on broken pipe");

        BuildCraftEngineRuntime unloaded = new BuildCraftEngineRuntime();
        NativeWorld unsafe = setup();
        tick(unloaded,unsafe,"unloaded",1);place(unloaded);
        unsafe.loaded = false;
        tick(unloaded,unsafe,"unloaded",20);
        check(unsafe.transfers == 0, "Unloaded chunks never initiate transfers");

        BuildCraftEngineRuntime worldChange = new BuildCraftEngineRuntime();
        NativeWorld changed = setup();
        tick(worldChange,changed,"first",1);place(worldChange);
        tick(worldChange,changed,"second",20);
        check(changed.transfers == 0, "Previous world engine position not reused in a new session");
        System.out.println("PASS: " + checks + " native BuildCraft wooden/cobble powered engine transport checks");
    }
}
