import dev.howlingwhispers.buildcraft.BuildCraftEngineRuntime;
import dev.howlingwhispers.buildcraft.BuildCraftEngineStore;
import dev.howlingwhispers.codaloader.api.CodaBlockPlacements;
import dev.howlingwhispers.codaloader.api.CodaBlockPos;
import dev.howlingwhispers.codaloader.api.CodaInventoryView;
import dev.howlingwhispers.codaloader.api.CodaServerTickContext;
import dev.howlingwhispers.codaloader.api.CodaWorldView;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public final class BuildCraftEnginePersistenceTest {
    static final String DIM = "minecraft:overworld";
    static final CodaBlockPos ENGINE_POS = new CodaBlockPos(1,65,0);
    static int checks;
    static void check(boolean good, String message) {
        checks++;
        if (!good) throw new AssertionError(message);
    }

    static final class World implements CodaWorldView {
        Path root;
        boolean powered = true;
        boolean loaded = true;
        Map<CodaBlockPos,String> blocks = new HashMap<>();
        Map<CodaBlockPos,Integer> items = new HashMap<>();
        int transfers;
        World(Path root) {
            this.root = root;
            blocks.put(ENGINE_POS,"buildcraftcore:engine_redstone");
            blocks.put(new CodaBlockPos(1,64,0),"buildcrafttransport:wood_item");
            blocks.put(new CodaBlockPos(2,64,0),"buildcrafttransport:cobblestone_item");
            blocks.put(new CodaBlockPos(3,64,0),"buildcrafttransport:cobblestone_item");
            items.put(new CodaBlockPos(0,64,0),32);
            items.put(new CodaBlockPos(4,64,0),0);
        }
        @Override public Optional<Path> worldDirectory() { return Optional.of(root); }
        @Override public List<String> dimensions() { return List.of(DIM); }
        @Override public boolean isChunkLoaded(String dim, CodaBlockPos pos) {
            return DIM.equals(dim) && loaded;
        }
        @Override public Optional<CodaInventoryView> inventory(String dim,CodaBlockPos pos) {
            if (!isChunkLoaded(dim,pos) || !items.containsKey(pos)) return Optional.empty();
            return Optional.of(new CodaInventoryView(pos,
                    List.of(new CodaInventoryView.Slot(0, items.get(pos),64))));
        }
        @Override public boolean isBlock(String dim,CodaBlockPos pos,String id) {
            return isChunkLoaded(dim,pos) && id.equals(blocks.get(pos));
        }
        @Override public boolean hasNeighborSignal(String dim,CodaBlockPos pos) {
            return isChunkLoaded(dim,pos) && powered;
        }
        @Override public int transfer(String dim,CodaBlockPos src,CodaBlockPos dst,int max) {
            if (!isChunkLoaded(dim,src) || !isChunkLoaded(dim,dst)) throw new AssertionError("Unloaded transfer");
            int n = Math.min(max,Math.min(items.get(src),64-items.get(dst)));
            if (n>0) {
                items.put(src,items.get(src)-n);
                items.put(dst,items.get(dst)+n);
                transfers++;
            }
            return n;
        }
    }
    static void tick(BuildCraftEngineRuntime runtime, World world, String session,long number)
            throws Exception {
        runtime.onServerTick(new CodaServerTickContext(session,number,Optional.of(world)));
    }
    static void place(BuildCraftEngineRuntime runtime) {
        runtime.onPlacement(new CodaBlockPlacements.Placement(DIM,ENGINE_POS,"buildcraftcore:engine_redstone"));
    }
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("howl-bc-world-");
        Path other = Files.createTempDirectory("howl-bc-other-");
        World first = new World(root);
        BuildCraftEngineRuntime runtime = new BuildCraftEngineRuntime();
        tick(runtime, first, "a",1);
        place(runtime);
        tick(runtime, first, "a",2);
        check(BuildCraftEngineStore.load(root).size()==1, "Placement indexed in world save");
        tick(runtime, first, "a",20);
        check(first.transfers==1, "A native powered engine can use the indexed position");

        // New MinecraftServer: reloaded engine location must be discovered
        // without requiring the player to break and replace the block.
        BuildCraftEngineRuntime reopened = new BuildCraftEngineRuntime();
        World second = new World(root);
        tick(reopened,second,"b",1);
        tick(reopened,second,"b",20);
        check(second.transfers==1,"Reopened world retains engine location");
        check(second.items.get(new CodaBlockPos(0,64,0))==16,
                "Reloaded engine moved actual source inventory");

        // One player's world cannot influence a second world's instance.
        BuildCraftEngineRuntime separate = new BuildCraftEngineRuntime();
        World isolated = new World(other);
        tick(separate,isolated,"c",1);
        tick(separate,isolated,"c",20);
        check(isolated.transfers==0,"No shared engine registry across different saves");
        check(BuildCraftEngineStore.load(other).isEmpty(),"No phantom engine save in other world");

        // Chunk unloading must not erase the world index.
        second.loaded=false;
        tick(reopened,second,"b",40);
        check(BuildCraftEngineStore.load(root).size()==1,
                "Unloaded engine remains indexed, never orphaned");
        second.loaded=true;
        second.blocks.remove(ENGINE_POS);
        tick(reopened,second,"b",60);
        check(BuildCraftEngineStore.load(root).isEmpty(),
                "Removed engine pruned from world index");

        // A corrupt save must not silently reset and overwrite coordinates.
        BuildCraftEngineStore.save(root,Set.of(new BuildCraftEngineStore.Engine(DIM,ENGINE_POS)));
        Path save = root.resolve("cml/buildcraft/engines.v1.dat");
        byte[] damaged = Files.readAllBytes(save);
        damaged[12] ^= 0x31;
        Files.write(save,damaged);
        try {
            BuildCraftEngineStore.load(root);
            throw new AssertionError("Corrupt engine file accepted");
        } catch (IOException expected) { checks++; }
        try {
            BuildCraftEngineStore.save(root,Set.of());
            throw new AssertionError("Corrupt engine file overwritten");
        } catch (IOException expected) { checks++; }
        check(Arrays.equals(damaged,Files.readAllBytes(save)),"Corrupt save preserved for repair");
        try {
            tick(new BuildCraftEngineRuntime(),new World(root),"bad",1);
            throw new AssertionError("Corrupt save allowed engine runtime to start");
        } catch (IOException expected) { checks++; }

        Path dir = Files.createTempDirectory("howl-bc-symlink-");
        try {
            Files.createSymbolicLink(dir.resolve("cml"),root);
            try {
                BuildCraftEngineStore.save(dir,Set.of());
                throw new AssertionError("Symlink save path accepted");
            } catch (IOException expected) { checks++; }
        } catch (UnsupportedOperationException | java.nio.file.FileSystemException excluded) {
            // Not every CI filesystem permits symlinks; behavior tested when allowed.
        }
        System.out.println("PASS: "+checks+" per-world engine persistence, reload, isolation and corrupt-save checks");
    }
}
