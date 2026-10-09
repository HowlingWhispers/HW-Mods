import dev.howlingwhispers.buildcraft.*;
import dev.howlingwhispers.codaloader.api.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public final class BuildCraftEnginePersistenceTest {
    static final String DIM="minecraft:overworld";
    static final CodaBlockPos POS=new CodaBlockPos(1,65,0);
    static final BuildCraftEngineStore.Engine KEY=new BuildCraftEngineStore.Engine(DIM,POS);
    static int checks;
    static void check(boolean yes,String description){
        checks++;if(!yes)throw new AssertionError(description);
    }
    static final class World implements CodaWorldView {
        Path root;
        boolean powered=true,loaded=true;
        boolean exists=true;
        int transfers;
        World(Path root){this.root=root;}
        @Override public Optional<Path> worldDirectory(){return Optional.of(root);}
        @Override public List<String> dimensions(){return List.of(DIM);}
        @Override public boolean isChunkLoaded(String dim,CodaBlockPos pos){return DIM.equals(dim)&&loaded;}
        @Override public Optional<CodaInventoryView> inventory(String dim,CodaBlockPos pos){return Optional.empty();}
        @Override public boolean isBlock(String dim,CodaBlockPos pos,String id){
            return isChunkLoaded(dim,pos)&&exists && id.equals("buildcraftcore:engine_redstone");
        }
        @Override public boolean hasNeighborSignal(String dim,CodaBlockPos pos){return powered&&loaded;}
        @Override public int transfer(String dim,CodaBlockPos from,CodaBlockPos to,int max){
            transfers++;throw new AssertionError("Unported pipes may not teleport cargo");
        }
    }
    static void tick(BuildCraftEngineRuntime e,World w,String session,long time)throws Exception{
        e.onServerTick(new CodaServerTickContext(session,time,Optional.of(w)));
    }
    static void place(BuildCraftEngineRuntime e){
        e.onPlacement(new CodaBlockPlacements.Placement(DIM,POS,"buildcraftcore:engine_redstone"));
    }
    public static void main(String[] args)throws Exception{
        Path root=Files.createTempDirectory("howl-bc-mj-world-");
        Path another=Files.createTempDirectory("howl-bc-mj-other-");
        World first=new World(root);
        BuildCraftEngineRuntime a=new BuildCraftEngineRuntime();
        tick(a,first,"a",1);
        place(a);
        for(int t=2;t<=40;t++)tick(a,first,"a",t);
        var before=a.engineState(DIM,POS).orElseThrow();
        check(BuildCraftEngineStore.load(root).contains(KEY),"Original engine location persists in this world");
        var saved=BuildCraftEngineMjStore.load(root).get(KEY);
        check(before.equals(saved),"BCCE heat/MJ/piston state was checkpointed intact");
        check(before.powerMicroMj()==1_000_000,"BCCE stored energy persisted accurately");
        BuildCraftEngineRuntime b=new BuildCraftEngineRuntime();
        World reopened=new World(root);
        tick(b,reopened,"b",41);
        var next=b.engineState(DIM,POS).orElseThrow();
        check(next.heat()>before.heat()-0.21,"Reopen restored warm engine, not cold reset");
        check(next.powerMicroMj()==1_000_000,"Reopen retained 1 MJ buffer");
        check(reopened.transfers==0,"No fake cargo extraction after engine reload");

        BuildCraftEngineRuntime c=new BuildCraftEngineRuntime();
        tick(c,new World(another),"c",1);
        check(c.engineState(DIM,POS).isEmpty(),"Other save has no phantom engines");
        reopened.loaded=false;
        tick(b,reopened,"b",42);
        check(b.engineState(DIM,POS).isPresent(),"Unloaded chunk leaves engine state intact");
        reopened.loaded=true;
        reopened.exists=false;
        tick(b,reopened,"b",43);
        check(b.engineState(DIM,POS).isEmpty(),"Removed Minecraft block is pruned from MJ state");
        check(BuildCraftEngineMjStore.load(root).isEmpty(),"Removed engine atomically purged from MJ save");

        // Store round-trip preserves mid-stroke state without forcing cold
        // restart and without losing original 1.0-dev position-only saves.
        var custom=new BuildCraftRedstoneEngine.Snapshot(56.75,400_000L,0.51f,2,true,true);
        BuildCraftEngineMjStore.save(root,Map.of(KEY,custom));
        check(custom.equals(BuildCraftEngineMjStore.load(root).get(KEY)),
                "All piston/heat/MJ fields roundtrip exactly");
        Path target=root.resolve("cml/buildcraft/engine-mj.v1.dat");
        byte[] damaged=Files.readAllBytes(target);
        damaged[12]^=0x11;
        Files.write(target,damaged);
        try{
            BuildCraftEngineMjStore.load(root);
            throw new AssertionError("Corrupt MJ file accepted");
        }catch(IOException expected){checks++;}
        try{
            BuildCraftEngineMjStore.save(root,Map.of());
            throw new AssertionError("Corrupt MJ file overwritten");
        }catch(IOException expected){checks++;}
        check(Arrays.equals(damaged,Files.readAllBytes(target)),
                "Corrupt MJ save remains available for recovery");
        try{
            tick(new BuildCraftEngineRuntime(),new World(root),"invalid",1);
            throw new AssertionError("Corrupt MJ file permitted ticking");
        }catch(IOException expected){checks++;}
        check(reopened.transfers==0,"World never mutates user inventories during MJ state tests");
        System.out.println("PASS: "+checks+" BCCE per-world MJ/piston persistence assertions");
    }
}
