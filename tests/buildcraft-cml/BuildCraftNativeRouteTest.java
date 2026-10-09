import dev.howlingwhispers.buildcraft.BuildCraftNativeRoute;
import dev.howlingwhispers.codaloader.api.*;
import java.util.*;

public final class BuildCraftNativeRouteTest {
    static final String DIM="minecraft:overworld";
    static int checks;
    static CodaBlockPos p(int x,int y,int z){return new CodaBlockPos(x,y,z);}
    static void check(boolean condition, String message){
        checks++;
        if(!condition)throw new AssertionError(message);
    }
    static final class World implements CodaWorldView {
        final Map<CodaBlockPos,String> blocks=new HashMap<>();
        final Set<CodaBlockPos> chests=new HashSet<>();
        boolean powered=true;
        CodaBlockPos unloaded;
        int transfers=0;
        World(){
            blocks.put(p(1,65,0),"buildcraftcore:engine_redstone");
            blocks.put(p(1,64,0),"buildcrafttransport:wood_item");
            blocks.put(p(2,64,0),"buildcrafttransport:cobblestone_item");
            blocks.put(p(3,64,0),"buildcrafttransport:cobblestone_item");
            chests.add(p(0,64,0));
            chests.add(p(4,64,0));
        }
        public List<String> dimensions(){return List.of(DIM);}
        public boolean isChunkLoaded(String d,CodaBlockPos pos){
            return DIM.equals(d) && !pos.equals(unloaded);
        }
        public boolean isBlock(String d,CodaBlockPos pos,String id){
            return isChunkLoaded(d,pos) && id.equals(blocks.get(pos));
        }
        public Optional<CodaInventoryView> inventory(String d,CodaBlockPos pos){
            if(!isChunkLoaded(d,pos) || !chests.contains(pos))return Optional.empty();
            return Optional.of(new CodaInventoryView(pos, List.of(new CodaInventoryView.Slot(0,5,64))));
        }
        public boolean hasNeighborSignal(String d,CodaBlockPos pos){return powered;}
        public int transfer(String d,CodaBlockPos a,CodaBlockPos b,int n){
            transfers++;
            throw new AssertionError("A survey must never move inventory");
        }
    }
    static Optional<BuildCraftNativeRoute.Route> route(World w)throws Exception {
        return BuildCraftNativeRoute.inspect(w,DIM,p(1,65,0));
    }
    public static void main(String[] args)throws Exception {
        World world=new World();
        var result=route(world).orElseThrow();
        check(result.engine().equals(p(1,65,0)),"Actual engine anchor");
        check(result.source().equals(p(0,64,0)),"Source is adjacent to wooden pipe");
        check(result.destination().equals(p(4,64,0)),"Destination reached by real cobblestone");
        check(result.pipes().equals(List.of(p(1,64,0),p(2,64,0),p(3,64,0))),
                "Route lists actual placed blocks, not glass or abstract nodes");
        check(result.powered(),"Reads real redstone power");
        world.powered=false;
        check(!route(world).orElseThrow().powered(),"Detects unpowered engine");
        world.powered=true;
        world.blocks.remove(p(2,64,0));
        check(route(world).isEmpty(),"Broken pipe prevents a route");
        world.blocks.put(p(2,64,0),"buildcrafttransport:cobblestone_item");
        world.unloaded=p(3,64,0);
        check(route(world).isEmpty(),"Does not traverse unloaded pipe chunks");
        world.unloaded=null;
        world.chests.remove(p(4,64,0));
        check(route(world).isEmpty(),"Missing destination prevents false readiness");
        world.chests.add(p(4,64,0));
        world.blocks.put(p(3,64,0),"minecraft:glass");
        check(route(world).isEmpty(),"Vanilla glass cannot masquerade as BuildCraft");
        world.blocks.put(p(3,64,0),"buildcrafttransport:cobblestone_item");
        world.blocks.put(p(1,65,0),"minecraft:stone");
        check(route(world).isEmpty(),"Engine itself must exist as native block");
        check(world.transfers==0,"Never touches player items");
        System.out.println("PASS: "+checks+" real-world BuildCraft pipe-route assertions");
    }
}
