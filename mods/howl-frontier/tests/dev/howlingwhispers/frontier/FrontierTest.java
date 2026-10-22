package dev.howlingwhispers.frontier;
import dev.howlingwhispers.codaloader.api.*;
import java.nio.file.*;
import java.util.*;
import static dev.howlingwhispers.frontier.Settlement.*;

/** Behavioural scenarios, not Minecraft compatibility claims. */
public final class FrontierTest {
    static int checks;
    static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    static final CodaBlockPos CAMP=new CodaBlockPos(0,64,0), BED=new CodaBlockPos(3,64,0), STOCK=new CodaBlockPos(1,64,2);
    static final class World implements CodaLivingWorld {
        final Map<CodaBlockPos,Block> blocks=new HashMap<>();final Map<UUID,Actor> actors=new HashMap<>();
        int spawned,damage,food=2,delivered,harvests;boolean loaded=true,spawnAllowed=true,path=true,tool=true;long clock=6000;
        World(){
            blocks.put(CAMP,b("minecraft:campfire",Map.of("lit","true"),false));
            blocks.put(BED,b("minecraft:red_bed",Map.of("part","head","occupied","false"),false));
            blocks.put(Settlement.add(BED,0,3,0),b("minecraft:oak_planks",Map.of(),true));
            blocks.put(STOCK,b("minecraft:chest",Map.of(),true));
        }
        static Block b(String id,Map<String,String>props,boolean solid){return new Block(id,props,solid,false);}
        public long clock(){return clock;}
        public Optional<Block> block(CodaBlockPos p){
            if(!loaded)return Optional.empty();
            return Optional.of(blocks.getOrDefault(p,b(p.y()==63?"minecraft:grass_block":"minecraft:air",Map.of(),p.y()==63)));
        }
        public Optional<Actor> actor(UUID id){return loaded?Optional.ofNullable(actors.get(id)):Optional.empty();}
        public boolean spawn(UUID id,String name,CodaBlockPos p){if(!spawnAllowed||actors.containsKey(id))return false;spawned++;actors.put(id,new Actor(id,p.x()+.5,p.y(),p.z()+.5,20,false));return true;}
        public boolean walk(UUID id,CodaBlockPos p,double speed){if(!path)return false;Actor a=actors.get(id);actors.put(id,new Actor(id,p.x()+.5,p.y(),p.z()+.5,a.health(),false));return true;}
        public boolean sleep(UUID id,CodaBlockPos p){Actor a=actors.get(id);actors.put(id,new Actor(id,a.x(),a.y(),a.z(),a.health(),true));return true;}
        public void wake(UUID id){}
        public void damage(UUID id,float amount){damage++;Actor a=actors.get(id);actors.put(id,new Actor(id,a.x(),a.y(),a.z(),Math.max(0,a.health()-amount),false));}
        public void discard(UUID id){actors.remove(id);}
        public boolean eat(UUID id,CodaBlockPos p,Set<String>allowed){if(food==0)return false;food--;return true;}
        public int deliver(UUID id,CodaBlockPos p){return delivered++;}
        public int carried(UUID id){return 0;}
        public int collect(UUID id,CodaBlockPos p,CodaItemFilter f,int maximum){return 1;}
        public boolean equip(UUID id,CodaBlockPos p,Set<String>tools){return tool;}
        public boolean harvest(UUID id,CodaBlockPos p,String expected,CodaBlockPos supplies,Set<String>tools){if(!tool)return false;harvests++;blocks.remove(p);return true;}
        public boolean plant(UUID id,CodaBlockPos p,String item,CodaBlockPos supplies){blocks.put(p,b("minecraft:oak_sapling",Map.of(),false));return true;}
        public void announce(String text){}
    }
    static final class View implements CodaWorldView {
        final Path root;final World world;View(Path p,World w){root=p;world=w;}
        public Optional<Path> worldDirectory(){return Optional.of(root);}
        public List<String> dimensions(){return List.of("minecraft:overworld");}
        public boolean isChunkLoaded(String d,CodaBlockPos p){return world.loaded;}
        public Optional<CodaInventoryView> inventory(String d,CodaBlockPos p){return Optional.empty();}
        public Optional<CodaItemInventoryView> itemInventory(String d,CodaBlockPos p){return Optional.of(new CodaItemInventoryView(p,List.of(new CodaItemInventoryView.Slot(0,"minecraft:bread",world.food,64))));}
        public Optional<CodaLivingWorld> living(String d){return Optional.of(world);}
    }
    static void advance(FrontierSimulation sim,View view,int seconds)throws Exception{
        for(int i=0;i<seconds;i++)sim.tick(new CodaServerTickContext("session",(i+1)*20L,Optional.of(view)));
    }
    public static void main(String[]args)throws Exception{
        Path temp=Files.createTempDirectory("frontier-behaviour-");World world=new World();View view=new View(temp,world);
        FrontierSimulation sim=new FrontierSimulation();sim.open(view,"session");sim.establish(world,CAMP);
        check(FrontierSimulation.shelteredBed(world,BED),"A roofed bed qualifies");
        world.blocks.remove(add(BED,0,3,0));check(!FrontierSimulation.shelteredBed(world,BED),"An outdoor bed does not qualify");
        world.blocks.put(add(BED,0,3,0),World.b("minecraft:oak_planks",Map.of(),true));
        sim.camp.beds.add(BED);advance(sim,view,59);check(world.spawned==0,"Arrival is gradual, not instant");
        advance(sim,view,3);check(world.spawned==1,"Free bed admits exactly one native settler");
        advance(sim,view,400);check(world.spawned==1,"An allocated bed never admits a second settler");
        var s=sim.camp.settlers.getFirst();check(s.fate==Fate.RESIDENT,"Traveller walks to cabin before becoming resident");
        double hunger=s.hunger;world.loaded=false;advance(sim,view,1000);check(s.hunger==hunger,"Unloaded camp does not starve or advance offline");world.loaded=true;
        sim.camp.stockpile=STOCK;s.hunger=50;world.actors.put(s.id,new CodaLivingWorld.Actor(s.id,STOCK.x()+.5,STOCK.y(),STOCK.z()+.5,20,false));
        advance(sim,view,1);check(s.hunger==90&&world.food==1,"A meal consumes a real food item before hunger improves");
        var low=new Settler(UUID.randomUUID(),"Low loyalty");low.fate=Fate.RESIDENT;low.loyalty=20;low.hunger=30;low.hungerStep();check(low.fate==Fate.LEAVING,"Low loyalty leaves early in shortage");
        var medium=new Settler(UUID.randomUUID(),"Medium loyalty");medium.fate=Fate.RESIDENT;medium.loyalty=55;medium.hunger=30;medium.hungerStep();check(medium.fate==Fate.RESIDENT,"Medium loyalty endures the first shortage");
        for(int i=0;i<20;i++)medium.hungerStep();check(medium.fate==Fate.LEAVING,"Medium loyalty eventually leaves");
        s.loyalty=95;s.hunger=0;s.fate=Fate.RESIDENT;world.food=0;
        advance(sim,view,22);check(s.fate==Fate.DEAD&&world.damage>=20,"High loyalty can stay until actual native starvation kills them");
        check(sim.camp.freeBeds().contains(BED),"Confirmed death frees the bed");
        sim.save();Settlement restored=new SettlementStore(temp).load();
        check(restored.settlers.getFirst().id.equals(s.id)&&restored.settlers.getFirst().fate==Fate.DEAD,"Reload retains identity, fate and bed release");
        check(restored.stockpile.equals(STOCK),"Stockpile belongs to the world save");
        Path other=Files.createTempDirectory("frontier-other-world-");check(new SettlementStore(other).load().camp==null,"Different worlds never share settlement state");
        // A failed native spawn keeps its precommitted identity and reservation; retries do not add another record.
        world.spawnAllowed=false;sim.camp.arrival=0;advance(sim,view,1);int count=sim.camp.settlers.size();UUID pending=sim.camp.settlers.getLast().id;
        advance(sim,view,30);check(sim.camp.settlers.size()==count&&sim.camp.settlers.getLast().id.equals(pending),"Spawn failures retain one pending settler and one bed claim");
        // Invalid persisted state is reported, never silently replaced with a fresh settlement.
        Path file=temp.resolve("howl_frontier/settlement.properties");String original=Files.readString(file);
        Files.writeString(file,original.replace("schema=1","schema=99"));
        try{new SettlementStore(temp).load();throw new AssertionError("Corruption accepted");}catch(java.io.IOException expected){checks++;}
        check(Files.readString(file).contains("schema=99"),"Corrupt save remains untouched for recovery");
        Files.writeString(file,original);check(Files.exists(temp.resolve("howl_frontier/settlement.previous.properties")),"Previous state backup exists");
        // An empty stockpile must not trap hungry farmers in an endless trip home.
        s.fate=Fate.RESIDENT;s.job=Job.FARMER;s.work=new CodaBlockPos(4,64,0);s.hunger=50;s.loyalty=90;s.prepared=true;s.workIndex=40;s.returning=false;
        world.actors.put(s.id,new CodaLivingWorld.Actor(s.id,4.5,64,.5,20,false));world.food=0;
        world.blocks.put(s.work,World.b("minecraft:wheat",Map.of("age","7"),false));int harvests=world.harvests;
        advance(sim,view,1);check(world.harvests==harvests+1,"Hungry farmer can produce food when stockpile is empty");
        check(s.replant.equals(s.work),"Harvest retains the exact crop replant task");
        advance(sim,view,1);check(s.replant==null&&s.returning,"Farmer replants before delivering the remaining harvest");
        // Explicit work areas must not turn cabins or storage into free materials.
        s.returning=false;s.prepared=true;s.job=Job.LUMBERJACK;s.work=new CodaBlockPos(5,64,0);s.workIndex=0;
        world.blocks.put(s.work,World.b("minecraft:oak_log",Map.of(),true));harvests=world.harvests;
        advance(sim,view,1);check(world.harvests==harvests&&s.activity.contains("canopy"),"Lumberjack refuses cabin-like logs without a tree canopy");
        s.job=Job.MINER;s.direction="north";world.blocks.put(add(s.work,0,1,0),World.b("minecraft:stone",Map.of(),true));
        world.blocks.put(add(s.work,1,1,0),World.b("minecraft:gravel",Map.of(),true));
        advance(sim,view,1);check(world.harvests==harvests&&s.activity.contains("falling"),"Miner pauses at falling material before breaking a block");
        s.prepared=false;world.tool=false;world.actors.put(s.id,new CodaLivingWorld.Actor(s.id,STOCK.x()+.5,STOCK.y(),STOCK.z()+.5,20,false));
        advance(sim,view,1);check(world.harvests==harvests&&s.activity.contains("tool"),"Missing supplied tool pauses work without producing resources");world.tool=true;
        // Native absence is deliberately not interpreted as death or a reason to duplicate a settler.
        sim.camp.settlers.clear();var absent=new Settler(UUID.randomUUID(),"Unloaded");absent.fate=Fate.RESIDENT;absent.bed=BED;absent.spawn=add(CAMP,24,0,0);sim.camp.settlers.add(absent);sim.camp.arrival=6000;
        advance(sim,view,30);check(absent.fate==Fate.RESIDENT&&absent.hunger==80,"Absent actor is paused, not killed or respawned");
        check(sim.camp.freeBeds().isEmpty(),"Unloaded resident retains their bed claim");
        System.out.println("PASS: "+checks+" Frontier camp, survival, arrival, persistence and isolation scenarios");
    }
}
