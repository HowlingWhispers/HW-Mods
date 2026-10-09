import dev.howlingwhispers.buildcraft.*;
import dev.howlingwhispers.codaloader.api.*;
import java.util.*;

public final class BuildCraftEngineRuntimeTest {
    static final String DIM="minecraft:overworld";
    static int checks;
    static CodaBlockPos p(int x,int y,int z){return new CodaBlockPos(x,y,z);}
    static void check(boolean v,String m){checks++;if(!v)throw new AssertionError(m);}
    static final class World implements CodaWorldView {
        final Map<CodaBlockPos,String> blocks=new HashMap<>();
        final Map<CodaBlockPos,Integer> containers=new HashMap<>();
        boolean powered=true,loaded=true;
        int transfers;
        World(){
            blocks.put(p(1,65,0),"buildcraftcore:engine_redstone");
            blocks.put(p(1,64,0),"buildcrafttransport:wood_item");
            blocks.put(p(2,64,0),"buildcrafttransport:cobblestone_item");
            blocks.put(p(3,64,0),"buildcrafttransport:cobblestone_item");
            containers.put(p(0,64,0),19);
            containers.put(p(4,64,0),0);
        }
        @Override public List<String> dimensions(){return List.of(DIM);}
        @Override public boolean isChunkLoaded(String d,CodaBlockPos pos){return DIM.equals(d)&&loaded;}
        @Override public Optional<CodaInventoryView> inventory(String d,CodaBlockPos pos){
            if(!isChunkLoaded(d,pos)||!containers.containsKey(pos))return Optional.empty();
            return Optional.of(new CodaInventoryView(pos,List.of(
                    new CodaInventoryView.Slot(0,containers.get(pos),64))));
        }
        @Override public boolean isBlock(String d,CodaBlockPos pos,String id){
            return isChunkLoaded(d,pos)&&id.equals(blocks.get(pos));
        }
        @Override public boolean hasNeighborSignal(String d,CodaBlockPos pos){
            return isChunkLoaded(d,pos)&&powered;
        }
        @Override public int transfer(String d,CodaBlockPos a,CodaBlockPos b,int n){
            transfers++;
            throw new AssertionError("Fake instantaneous chest-to-chest movement was removed");
        }
    }
    static void tick(BuildCraftEngineRuntime runtime,World w,String session,long t)throws Exception{
        runtime.onServerTick(new CodaServerTickContext(session,t,Optional.of(w)));
    }
    static void place(BuildCraftEngineRuntime runtime){
        runtime.onPlacement(new CodaBlockPlacements.Placement(DIM,p(1,65,0),
                "buildcraftcore:engine_redstone"));
    }
    public static void main(String[] args)throws Exception{
        BuildCraftEngineRuntime runtime=new BuildCraftEngineRuntime();
        World world=new World();
        tick(runtime,world,"A",1);place(runtime);
        for(int t=2;t<=40;t++)tick(runtime,world,"A",t);
        var state=runtime.engineState(DIM,p(1,65,0)).orElseThrow();
        check(state.powerMicroMj()==1_000_000L,"Original BCCE engine caps at 1 MJ");
        check(state.heat()>20.0,"BCCE heat ticked for every server tick");
        check(world.transfers==0,"No virtual 20-tick pipe chest teleportation");
        check(world.containers.get(p(0,64,0))==19 && world.containers.get(p(4,64,0))==0,
                "Unported pipes never silently move real player inventory");
        world.powered=false;
        tick(runtime,world,"A",41);
        check(runtime.engineState(DIM,p(1,65,0)).orElseThrow().powerMicroMj()==0,
                "Engine stops producing MJ without redstone");
        world.loaded=false;
        tick(runtime,world,"A",42);
        check(runtime.engineState(DIM,p(1,65,0)).isPresent(),
                "Unloaded engines remain known but are never ticked");
        world.loaded=true;
        world.blocks.remove(p(1,65,0));
        tick(runtime,world,"A",43);
        check(runtime.engineState(DIM,p(1,65,0)).isEmpty(),
                "Breaking a real engine removes its MJ state");
        tick(new BuildCraftEngineRuntime(),new World(),"B",1);
        check(world.transfers==0,"Session state is not globally shared");
        System.out.println("PASS: "+checks+" native BCCE MJ engine integration assertions");
    }
}
