package dev.howlingwhispers.frontier;

import dev.howlingwhispers.codaloader.api.*;
import java.nio.file.*;
import java.util.*;
import static dev.howlingwhispers.frontier.Settlement.*;

/** One-second decisions; native Minecraft owns entity movement and the carried ItemStacks. */
final class FrontierSimulation {
    static final Set<String> FOOD=Set.of("minecraft:bread","minecraft:baked_potato","minecraft:carrot","minecraft:cooked_beef","minecraft:cooked_porkchop","minecraft:cooked_chicken","minecraft:cooked_mutton","minecraft:cooked_rabbit","minecraft:cooked_cod","minecraft:cooked_salmon","minecraft:beetroot","minecraft:apple");
    static final Set<String> AXES=tools("axe"), PICKS=tools("pickaxe");
    private static Set<String> tools(String type){Set<String>s=new HashSet<>();for(String material:List.of("wooden","stone","copper","iron","golden","diamond","netherite"))s.add("minecraft:"+material+"_"+type);return Set.copyOf(s);}
    Settlement camp;
    SettlementStore store;
    private Path root;
    private String session;
    boolean foodAvailable;
    private final ArrayDeque<CodaBlockPlacements.Placement> placements=new ArrayDeque<>();
    /** Why this world's settlement file could not be read. While set, nothing is simulated or saved. */
    String loadError;
    /** Latest contained failure, shown by /frontier status. Not persisted. */
    String lastError; long errorCount, lastErrorTick, now;
    /** Plain-language reason the next traveller has not arrived. Not persisted. */
    String arrivalStatus="Waiting for the first camp update";
    private final Set<String> logged=new HashSet<>();
    void placed(CodaBlockPlacements.Placement p){if(p.blockId().equals("minecraft:campfire")&&placements.size()<16)placements.add(p);}
    void open(CodaWorldView view,String session)throws Exception{
        Path next=view.worldDirectory().orElseThrow(()->new IllegalStateException("World save directory unavailable")).toAbsolutePath().normalize();
        // A failed load is retried only when the world or session changes, never every second.
        if((camp==null&&loadError==null)||!next.equals(root)||(session!=null&&!session.equals(this.session))){
            if(root!=null)placements.clear();
            root=next;this.session=session;lastError=null;errorCount=0;logged.clear();
            try{
                var loadedStore=new SettlementStore(next);Settlement loaded=loadedStore.load();
                store=loadedStore;camp=loaded;loadError=null;
            }catch(java.io.IOException unreadable){
                // Never replace an unreadable settlement with a new one: that would overwrite the player's camp.
                store=null;camp=null;
                loadError=unreadable.getMessage()+(unreadable.getCause()!=null?" ("+unreadable.getCause().getMessage()+")":"");
                log("load","[Frontier] Settlement file unreadable, camp paused and file left unchanged: "+loadError);
            }
        }
    }
    /** Thrown to commands while the settlement file is unreadable; FrontierMod turns it into guidance. */
    void requireReadable(){
        if(loadError!=null)throw new IllegalArgumentException("This world's settlement file could not be read and has not been changed: "+loadError
                +". Close the world, then fix or restore howl_frontier/settlement.properties (a copy of the previous version is in settlement.previous.properties) and reopen it.");
    }
    void save()throws Exception{if(store==null)throw new IllegalStateException("Settlement storage is unavailable");store.save(camp);}
    @FunctionalInterface interface Part{void run()throws Exception;}
    /** Contain one failing part of the update so the loader never disables Frontier for the session. */
    private boolean guard(String part,Part action){
        try{action.run();return true;}
        catch(Exception|LinkageError failure){
            errorCount++;lastError=part+": "+failure;lastErrorTick=now;
            log(part+failure.getClass().getName()+failure.getMessage(),"[Frontier] "+part+" failed and was skipped this second: "+failure);
            return false;
        }
    }
    private void log(String key,String message){if(logged.size()<64&&logged.add(key))System.err.println(message);}
    void establish(CodaLivingWorld world,CodaBlockPos p)throws Exception{
        requireReadable();
        if(camp.camp!=null)throw new IllegalArgumentException("This world already has a Frontier camp at "+camp.camp);
        var block=world.block(p).orElseThrow(()->new IllegalArgumentException("Campfire must be loaded"));
        if(!block.id().equals("minecraft:campfire")||!"true".equals(block.properties().get("lit")))throw new IllegalArgumentException("Place and light an ordinary campfire first");
        camp.camp=p;camp.arrival=1200;save();
        world.announce("Camp established. Build sheltered beds nearby, then look at a chest and use /frontier stockpile. /frontier help");
    }
    /**
     * Never lets an exception reach the loader: three consecutive failures would disable
     * Frontier for the rest of the session. Each part is contained separately, so one
     * broken settler or bed cannot stop hunger, jobs, arrivals or saving for the others.
     */
    void tick(CodaServerTickContext tick){
        now=tick.tick();
        if(tick.tick()%20!=0||tick.world().isEmpty())return;
        CodaWorldView view=tick.world().orElseThrow();
        if(!guard("Opening the settlement",()->open(view,tick.sessionId()))||loadError!=null){placements.clear();return;}
        CodaLivingWorld world;
        try{var access=view.living("minecraft:overworld");if(access.isEmpty())return;world=access.get();}
        catch(Exception failure){guard("Reaching the Overworld",()->{throw failure;});return;}
        guard("Establishing a camp",()->{
            while(!placements.isEmpty()){
                var p=placements.removeFirst();if(camp.camp==null&&p.dimension().equals(camp.dimension)){
                    var b=world.block(p.position());if(b.isPresent()&&b.get().id().equals("minecraft:campfire")&&"true".equals(b.get().properties().get("lit")))establish(world,p.position());
                }
            }
        });
        if(camp.camp==null){arrivalStatus="No camp yet: place and light a campfire";return;}
        // A camp outside loaded chunks is paused, including hunger and immigration. No offline catch-up.
        boolean[] loaded={false};
        if(!guard("Checking the campfire",()->loaded[0]=world.block(camp.camp).isPresent())||!loaded[0]){
            arrivalStatus="Paused: the campfire's chunk is not loaded";return;
        }
        camp.elapsed+=20;
        guard("Scanning for beds",()->scanBeds(world,256));
        if(!guard("Checking stockpile food",()->foodAvailable=hasFood(view)))foodAvailable=false;
        for(var s:camp.settlers){
            if(!s.terminal()){
                if(!guard("Settler "+s.name+" ("+s.id.toString().substring(0,8)+")",()->step(world,s)))s.activity="Update failed: see /frontier status";
            }
            else if(s.fate==Fate.LEFT)guard("Removing departed "+s.name,()->world.discard(s.id)); // finish an interrupted, already-persisted departure
        }
        guard("Arrivals",()->arrivals(world));
        if(camp.elapsed%100==0)guard("Saving the settlement",this::save);
    }
    private void arrivals(CodaLivingWorld world)throws Exception{
        var fire=world.block(camp.camp).orElseThrow();
        if(!fire.id().equals("minecraft:campfire")){arrivalStatus="Stopped: the campfire at "+text(camp.camp)+" is gone. Place a campfire on that exact spot";return;}
        if(!"true".equals(fire.properties().get("lit"))){arrivalStatus="Stopped: the campfire at "+text(camp.camp)+" is not lit";return;}
        if(camp.population()>=CAPACITY){arrivalStatus="Camp is full ("+camp.population()+"/"+CAPACITY+")";return;}
        if(camp.settlers.size()>=512){arrivalStatus="Stopped: the camp has reached its limit of 512 settler records, including dead and departed settlers";return;}
        List<CodaBlockPos> free=new ArrayList<>();
        for(var b:camp.freeBeds())if(shelteredBed(world,b))free.add(b);
        if(free.isEmpty()){
            arrivalStatus=camp.beds.isEmpty()
                ?"Waiting for a bed: no sheltered beds found yet. Place a bed within 16 blocks with a solid roof 2-6 blocks above its head (a full scan takes about a minute)"
                :"Waiting for a bed: all "+camp.beds.size()+" sheltered beds are claimed. Build another one";
            return;
        }
        camp.arrival=Math.max(0,camp.arrival-20);
        if(camp.arrival>0){arrivalStatus="Next traveller in about "+(camp.arrival/20)+"s of loaded camp time";return;}
        String problem=arrive(world,free.getFirst());
        arrivalStatus=problem==null?"A traveller is on the way":problem;
    }
    static String text(CodaBlockPos p){return p.x()+" "+p.y()+" "+p.z();}
    void scanBeds(CodaLivingWorld world,int budget)throws Exception{
        int width=RADIUS*2+1,height=13,total=width*width*height;
        for(int i=0;i<budget;i++){
            int n=camp.scanCursor++%total;
            CodaBlockPos p=add(camp.camp,n%width-RADIUS,(n/(width*width))-6,(n/width)%width-RADIUS);
            var b=world.block(p);if(b.isEmpty())continue;
            if(b.get().id().endsWith("_bed")&&"head".equals(b.get().properties().get("part"))&&!camp.ignoredBeds.contains(p)){
                if(shelteredBed(world,p)&&camp.beds.size()<256)camp.beds.add(p);
            }else camp.beds.remove(p);
        }
        if(camp.scanCursor>=total)camp.scanCursor%=total;
    }
    static boolean shelteredBed(CodaLivingWorld world,CodaBlockPos p)throws Exception{
        var b=world.block(p);if(b.isEmpty()||!b.get().id().endsWith("_bed")||!"head".equals(b.get().properties().get("part")))return false;
        var above=world.block(add(p,0,1,0));if(above.isEmpty()||!above.get().air())return false;
        boolean roof=false;for(int y=2;y<=6;y++){var sample=world.block(add(p,0,y,0));if(sample.isEmpty())return false;if(sample.get().solid()){roof=true;break;}}
        return roof;
    }
    private CodaBlockPos standing(CodaLivingWorld world,CodaBlockPos origin,int radius)throws Exception{
        for(int dy=0;dy<=3;dy++)for(int y:dy==0?new int[]{0}:new int[]{dy,-dy})
            for(int r=0;r<=radius;r++)for(int x=-r;x<=r;x++)for(int z=-r;z<=r;z++){
                if(Math.max(Math.abs(x),Math.abs(z))!=r)continue;
                var p=add(origin,x,y,z);var f=world.block(p);var h=world.block(add(p,0,1,0));var b=world.block(add(p,0,-1,0));
                if(f.isPresent()&&h.isPresent()&&b.isPresent()&&passable(f.get())&&h.get().air()&&(b.get().solid()||b.get().id().equals("minecraft:farmland"))&&!b.get().fluid()
                        &&!Set.of("minecraft:campfire","minecraft:soul_campfire","minecraft:magma_block","minecraft:cactus").contains(b.get().id()))return p;
            }
        return null;
    }
    private static boolean passable(CodaLivingWorld.Block b){
        return b.air()||Set.of("minecraft:wheat","minecraft:carrots","minecraft:potatoes","minecraft:beetroots","minecraft:short_grass").contains(b.id())||b.id().endsWith("_sapling");
    }
    /** Returns null when a traveller was committed, otherwise why not. */
    private String arrive(CodaLivingWorld world,CodaBlockPos bed)throws Exception{
        // Only unoccupied native beds can attract a new resident. Existing claims survive sleeping/unloading.
        if("true".equals(world.block(bed).orElseThrow().properties().get("occupied")))return "Waiting: the free bed at "+text(bed)+" is occupied by someone sleeping in it";
        CodaBlockPos spawn=null;
        int turn=(int)((camp.elapsed/20)%4);
        for(int i=0;i<4&&spawn==null;i++){
            int side=(turn+i)%4;
            spawn=standing(world,add(camp.camp,side==0?24:side==1?-24:0,0,side==2?24:side==3?-24:0),2);
        }
        if(spawn==null)return "Waiting: no safe arrival point about 24 blocks north, south, east or west of the campfire. Clear some flat, dry ground there";
        UUID id=UUID.randomUUID();String[] names={"Asta","Bryn","Edda","Finn","Ivar","Liv","Mika","Nils","Runa","Siv","Tora","Vidar"};
        var s=new Settler(id,names[camp.settlers.size()%names.length]);
        s.bed=bed;s.spawn=spawn;s.loyalty=35+Math.floorMod(id.hashCode(),41);
        camp.settlers.add(s);camp.arrival=ARRIVAL_INTERVAL;
        save(); // reserve bed and immutable identity BEFORE touching the native entity registry
        spawnPending(world,s);
        return null;
    }
    private void spawnPending(CodaLivingWorld world,Settler s)throws Exception{
        var existing=world.actor(s.id);
        if(existing.isPresent()||world.spawn(s.id,s.name,s.spawn)){
            s.fate=Fate.ARRIVING;save();world.announce(s.name+" is approaching your camp.");
        }
    }
    private void step(CodaLivingWorld world,Settler s)throws Exception{
        if(s.fate==Fate.PENDING){
            // Native entity chunks may finish loading after the first server tick.
            s.absentTicks+=20;if(s.absentTicks>=200){s.absentTicks=0;spawnPending(world,s);}return;
        }
        var entity=world.actor(s.id);
        if(entity.isEmpty()){s.activity="Away or in an unloaded chunk (paused)";return;}
        var actor=entity.get();
        if(actor.health()<=0){s.fate=Fate.DEAD;s.bed=null;save();world.announce(s.name+" has died.");return;}
        s.hungerTicks+=20;
        if(s.hungerTicks>=200){s.hungerTicks-=200;var previous=s.fate;s.hungerStep();if(previous!=s.fate){save();world.announce(s.name+" is leaving because there is not enough food.");}}
        if(s.hunger==0){world.damage(s.id,1);s.activity="Starving";}
        if(s.fate==Fate.LEAVING){
            world.wake(s.id);s.travelTicks+=20;
            if(actor.distanceSquared(s.spawn)<=9){
                // Departing settlers take their carried supplies with them.
                s.fate=Fate.LEFT;save();world.discard(s.id);world.announce(s.name+" has left the settlement.");return;
            }
            navigate(world,s,s.spawn,.7);return;
        }
        if(s.fate==Fate.ARRIVING){
            s.travelTicks+=20;s.activity="Walking into camp";
            if(s.bed!=null&&actor.distanceSquared(s.bed)<=9){s.fate=Fate.RESIDENT;s.travelTicks=0;save();world.announce(s.name+" has joined your settlement.");}
            else if(s.bed!=null&&!navigate(world,s,s.bed,.65))s.activity="Arrival path blocked: clear a route to the cabin";
            return;
        }
        if(s.bed!=null){
            var b=world.block(s.bed);
            if(b.isPresent()&&!shelteredBed(world,s.bed)){camp.beds.remove(s.bed);s.bed=null;save();}
        }
        if(s.bed==null){for(var bed:camp.freeBeds())if(shelteredBed(world,bed)){s.bed=bed;save();break;}}
        if(s.hunger<=60&&camp.stockpile!=null&&foodAvailable){
            world.wake(s.id);
            if(actor.distanceSquared(camp.stockpile)<=9){if(world.eat(s.id,camp.stockpile,FOOD)){s.meal();s.activity="Eating";}}
            else {s.activity="Going to the stockpile for food";moveNear(world,s,camp.stockpile);return;}
        }
        if(s.hunger<20){s.activity="Too hungry to work";return;}
        long time=Math.floorMod(world.clock(),24000);
        if(time>=12500&&time<23500){
            s.activity=s.bed==null?"No sheltered bed":"Returning to bed";
            if(s.bed!=null){if(actor.distanceSquared(s.bed)<=6.25){if(world.sleep(s.id,s.bed))s.activity="Sleeping";}else navigate(world,s,s.bed,.6);}return;
        }
        world.wake(s.id);
        if(camp.stockpile==null){s.activity="Needs a camp stockpile";return;}
        work(world,s,actor);
    }
    private boolean navigate(CodaLivingWorld world,Settler s,CodaBlockPos target,double speed)throws Exception{
        var actor=world.actor(s.id);if(actor.isEmpty())return false;
        var at=actor.get();CodaBlockPos next=target;
        if(at.distanceSquared(target)>28*28){
            double dx=target.x()+.5-at.x(),dz=target.z()+.5-at.z(),length=Math.sqrt(dx*dx+dz*dz);
            if(length<1)return false;
            var midpoint=new CodaBlockPos((int)Math.floor(at.x()+dx*22/length),(int)Math.floor(at.y()),(int)Math.floor(at.z()+dz*22/length));
            next=standing(world,midpoint,2);if(next==null)return false;
        }
        return world.walk(s.id,next,speed);
    }
    private void moveNear(CodaLivingWorld world,Settler s,CodaBlockPos target)throws Exception{
        CodaBlockPos stand=standing(world,target,2);
        if(stand==null||!navigate(world,s,stand,s.hunger<30?.4:.65))s.activity="Path blocked: "+s.activity;
    }
    /** Reads the stockpile now, so status is correct before the first update. */
    boolean hasFood(CodaWorldView view)throws Exception{
        return camp.stockpile!=null && view.itemInventory(camp.dimension,camp.stockpile)
                .map(inventory->inventory.slots().stream().anyMatch(slot->slot.count()>0&&FOOD.contains(slot.itemId()))).orElse(false);
    }
    static final Set<String> CONTAINERS=Set.of("minecraft:chest","minecraft:barrel");
    /** Null when the stockpile is usable; otherwise a plain explanation. Never loads chunks. */
    static String stockpileProblem(CodaLivingWorld world,CodaBlockPos stockpile)throws Exception{
        if(stockpile==null)return "No stockpile yet: look at a chest or barrel and use /frontier stockpile";
        var b=world.block(stockpile);
        if(b.isEmpty())return "The stockpile at "+text(stockpile)+" is in an unloaded area";
        if(!CONTAINERS.contains(b.get().id()))return "The stockpile at "+text(stockpile)+" is missing: there is no chest or barrel there any more. Look at one and use /frontier stockpile";
        return null;
    }
    private void work(CodaLivingWorld world,Settler s,CodaLivingWorld.Actor actor)throws Exception{
        // A removed chest previously surfaced as "Stockpile full"; say what is actually wrong.
        var container=world.block(camp.stockpile);
        if(container.isPresent()&&!CONTAINERS.contains(container.get().id())){s.activity="Stockpile missing: look at a chest or barrel and use /frontier stockpile";return;}
        if(s.returning){
            s.activity="Delivering supplies";
            if(actor.distanceSquared(camp.stockpile)>9){moveNear(world,s,camp.stockpile);return;}
            int moved=world.deliver(s.id,camp.stockpile);
            if(world.carried(s.id)>0){s.activity=moved==0?"Stockpile full: work paused":"Unloading";return;}
            s.returning=false;s.prepared=false;
        }
        if(s.job==Job.IDLE||s.work==null){s.activity="Waiting for a job and work area";return;}
        if(!s.prepared){
            if(actor.distanceSquared(camp.stockpile)>9){s.activity="Collecting work supplies";moveNear(world,s,camp.stockpile);return;}
            Set<String> tools=s.job==Job.LUMBERJACK?AXES:s.job==Job.MINER?PICKS:Set.of();
            if(!world.equip(s.id,camp.stockpile,tools)){s.activity="Stockpile needs a suitable tool and space for the old tool";return;}
            if(s.replant!=null){
                if(world.collect(s.id,camp.stockpile,CodaItemFilter.only(s.seed),1)==0){s.activity="Stockpile needs "+s.seed+" for replanting";return;}
            }else if(s.job==Job.LUMBERJACK){
                String sapling=sapling(world,s.work);
                if(sapling==null||world.collect(s.id,camp.stockpile,CodaItemFilter.only(sapling),1)==0){s.activity="Stockpile needs a sapling for the marked tree";return;}
            }
            s.prepared=true;
        }
        if(s.replant!=null){
            s.activity="Replanting";
            if(actor.distanceSquared(s.replant)>9){moveNear(world,s,s.replant);return;}
            if(!world.plant(s.id,s.replant,s.seed,camp.stockpile)){s.activity="Needs seed/sapling or suitable soil to replant";s.prepared=false;return;}
            s.replant=null;s.seed="";s.returning=true;if(s.job==Job.LUMBERJACK)s.work=null;return;
        }
        switch(s.job){
            case LUMBERJACK -> lumber(world,s,actor);
            case MINER -> mine(world,s,actor);
            case FARMER -> farm(world,s,actor);
            default -> {}
        }
    }
    private String sapling(CodaLivingWorld world,CodaBlockPos p)throws Exception{
        var b=world.block(p);if(b.isEmpty()||!b.get().id().endsWith("_log"))return null;
        return b.get().id().substring(0,b.get().id().length()-4)+"_sapling";
    }
    private void lumber(CodaLivingWorld world,Settler s,CodaLivingWorld.Actor actor)throws Exception{
        s.activity="Cutting the marked tree";
        CodaBlockPos target=add(s.work,0,s.workIndex,0);var b=world.block(target);if(b.isEmpty())return;
        if(!b.get().id().endsWith("_log")){
            if(s.workIndex==0){s.activity="Mark another small tree with /frontier work";return;}
            s.replant=s.work; // seed recorded on first harvested log
            if(s.seed.isEmpty()){s.activity="No supported sapling for this tree";s.replant=null;s.returning=true;return;}
            return;
        }
        if(s.workIndex>=6){s.activity="Tree exceeds starter lumberjack reach; delivering cut logs";s.work=null;s.returning=true;return;}
        if(s.workIndex==0){
            boolean leaves=false;for(int y=2;y<=7;y++)for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++){
                var leaf=world.block(add(s.work,x,y,z));if(leaf.isPresent()&&leaf.get().id().endsWith("_leaves"))leaves=true;
            }
            if(!leaves){s.activity="Refusing logs without a nearby tree canopy";return;}
            s.seed=sapling(world,s.work);
        }
        if(actor.distanceSquared(s.work)>9){
            moveNear(world,s,s.work);return;
        }
        if(world.harvest(s.id,target,b.get().id(),camp.stockpile,AXES))s.workIndex++;
        else s.activity="Needs an axe, free carrying space or a reachable log";
    }
    private void mine(CodaLivingWorld world,Settler s,CodaLivingWorld.Actor actor)throws Exception{
        if(s.workIndex>=16){s.activity="Starter tunnel complete (8 blocks). Mark the next section.";s.returning=world.carried(s.id)>0;return;}
        int dx=s.direction.equals("east")?1:s.direction.equals("west")?-1:0;
        int dz=s.direction.equals("south")?1:s.direction.equals("north")?-1:0;
        int step=s.workIndex/2;CodaBlockPos target=add(s.work,dx*step,1-s.workIndex%2,dz*step);
        var b=world.block(target);if(b.isEmpty())return;
        if(b.get().air()){s.workIndex++;return;}
        Set<String> safe=Set.of("minecraft:stone","minecraft:deepslate","minecraft:andesite","minecraft:diorite","minecraft:granite","minecraft:tuff","minecraft:dirt","minecraft:coal_ore","minecraft:deepslate_coal_ore","minecraft:iron_ore","minecraft:deepslate_iron_ore","minecraft:copper_ore","minecraft:deepslate_copper_ore","minecraft:gold_ore","minecraft:deepslate_gold_ore","minecraft:redstone_ore","minecraft:deepslate_redstone_ore","minecraft:lapis_ore","minecraft:deepslate_lapis_ore","minecraft:diamond_ore","minecraft:deepslate_diamond_ore","minecraft:emerald_ore","minecraft:deepslate_emerald_ore");
        if(!safe.contains(b.get().id())){s.activity="Mining paused at an unsupported block";return;}
        for(int[] d:new int[][]{{1,0,0},{-1,0,0},{0,1,0},{0,0,1},{0,0,-1},{0,-1,0}}){
            var n=world.block(add(target,d[0],d[1],d[2]));
            if(n.isEmpty()||n.get().fluid()||Set.of("minecraft:sand","minecraft:red_sand","minecraft:gravel").contains(n.get().id())){s.activity="Mining paused: liquid, falling material or unloaded boundary";return;}
        }
        s.activity="Mining the marked tunnel";
        if(actor.distanceSquared(target)>9){moveNear(world,s,add(s.work,dx*(step-1),0,dz*(step-1)));return;}
        if(world.harvest(s.id,target,b.get().id(),camp.stockpile,PICKS)){s.workIndex++;if(s.workIndex%4==0)s.returning=true;}
        else s.activity="Needs the correct pickaxe or free carrying space";
    }
    private void farm(CodaLivingWorld world,Settler s,CodaLivingWorld.Actor actor)throws Exception{
        // One prepared 9x9 field, max nine inspections per decision. Minecraft grows the crops normally.
        for(int i=0;i<9;i++){
            int n=s.workIndex++%81;CodaBlockPos target=add(s.work,n%9-4,0,n/9-4);var b=world.block(target);if(b.isEmpty())continue;
            String seed;String age;
            switch(b.get().id()){
                case "minecraft:wheat" -> {seed="minecraft:wheat_seeds";age="7";}
                case "minecraft:carrots" -> {seed="minecraft:carrot";age="7";}
                case "minecraft:potatoes" -> {seed="minecraft:potato";age="7";}
                case "minecraft:beetroots" -> {seed="minecraft:beetroot_seeds";age="3";}
                default -> {continue;}
            }
            if(!age.equals(b.get().properties().get("age")))continue;
            if(actor.distanceSquared(target)>9){s.workIndex--;s.activity="Walking to ripe crops";moveNear(world,s,target);return;}
            if(world.harvest(s.id,target,b.get().id(),camp.stockpile,Set.of())){s.replant=target;s.seed=seed;s.activity="Harvesting";return;}
        }
        s.workIndex%=81;s.activity="Waiting for crops to ripen";
    }
}
