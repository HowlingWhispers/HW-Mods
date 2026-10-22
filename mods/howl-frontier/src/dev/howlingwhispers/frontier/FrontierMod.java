package dev.howlingwhispers.frontier;

import dev.howlingwhispers.codaloader.api.*;
import java.nio.file.*;
import java.util.*;
import static dev.howlingwhispers.frontier.Settlement.*;

/** Frontier is an isolated launcher play mode, not an automatic conversion of existing worlds. */
public final class FrontierMod implements CodaMod {
    private final FrontierSimulation sim=new FrontierSimulation();
    @Override public void onInitialize(CodaContext context)throws Exception{
        CodaWorldView.class.getMethod("living",String.class);
        Path marker=context.gameDirectory().resolve("config/howl-play-mode.txt");
        boolean enabled=Files.isRegularFile(marker)&&!Files.isSymbolicLink(marker)&&Files.size(marker)<64
                &&Files.readString(marker).trim().equals("frontier");
        context.registerCommand("frontier","Frontier camp, settlers and worker assignments",(player,args)->{
            if(!enabled){player.reply("Choose Frontier in CodaLauncher Nightly before starting this world.");return;}
            player.withWorld(view->{
                sim.open(view,null);
                if(!player.position().dimension().equals("minecraft:overworld"))throw new IllegalArgumentException("Frontier camps currently belong in the Overworld");
                var world=view.living("minecraft:overworld").orElseThrow(()->new IllegalStateException("H.O.W.L. 0.0.43 single-player living-world API is required"));
                try{command(player,world,args);}catch(IllegalArgumentException e){player.reply("Frontier: "+e.getMessage());}
            });
        });
        if(enabled){context.registerServerTick("frontier",sim::tick);context.registerBlockPlacement(sim::placed);}
        System.out.println("[Frontier] 0.1.0-dev.1, mode="+(enabled?"Frontier":"inactive")+", H.O.W.L. "+context.loaderVersion());
    }
    private void command(CodaCommandContext player,CodaLivingWorld world,List<String> args)throws Exception{
        String op=args.isEmpty()?"help":args.getFirst();
        if(op.equals("help")){
            player.reply("FRONTIER: place a lit campfire to establish camp. Build sheltered beds within 16 blocks. Travellers arrive gradually when beds are free.");
            player.reply("/frontier stockpile x y z | bed x y z | ignorebed x y z | status | settlers");
            player.reply("/frontier job <id> idle|lumberjack|miner|farmer | work <id> x y z [north|south|east|west]");
            player.reply("Stock food and tools in a single chest/barrel. Lumberjack: mark a small tree's lowest log and supply an axe + sapling. Miner: mark the first feet-height tunnel block, supply a pickaxe. Farmer: mark the centre crop of a planted 9x9 field.");
            player.reply("Food shortages cause weaker loyalty to leave. Loyal settlers can stay until they starve. No progression while their entities are unloaded.");return;
        }
        if(op.equals("camp")&&args.size()==4){var p=position(args,1);near(player,p,32);sim.establish(world,p);return;}
        if(sim.camp.camp==null)throw new IllegalArgumentException("Place a lit campfire, or use /frontier camp x y z at an existing one");
        near(player,sim.camp.camp,64);
        switch(op){
            case "status" -> player.reply("Camp "+sim.camp.camp+" | settlers "+sim.camp.population()+"/"+CAPACITY+" | known sheltered beds "+sim.camp.beds.size()+" | free claims "+sim.camp.freeBeds().size()+" | stockpile "+sim.camp.stockpile+" | next arrival needs "+(sim.camp.arrival/20)+"s of loaded camp time and a free bed.");
            case "settlers" -> {
                if(sim.camp.settlers.isEmpty())player.reply("No settlers yet. Build beds under roofs near your campfire. The first bed scan can take about one minute.");
                for(var s:sim.camp.settlers)if(!s.terminal())player.reply(s.id.toString().substring(0,8)+" | "+s.name+" | "+s.fate+" | "+s.job+" | food "+(int)s.hunger+" | loyalty "+(int)s.loyalty+" | "+s.activity);
            }
            case "stockpile","bed","ignorebed" -> {
                if(args.size()!=4)throw new IllegalArgumentException("Use /frontier "+op+" x y z");
                CodaBlockPos p=position(args,1);near(player,p,32);
                if(!within(p,sim.camp.camp,op.equals("stockpile")?24:32))throw new IllegalArgumentException("Keep camp facilities close to the campfire");
                if(op.equals("stockpile")){
                    String id=world.block(p).orElseThrow(()->new IllegalArgumentException("Stockpile must be loaded")).id();
                    if(!Set.of("minecraft:chest","minecraft:barrel").contains(id))throw new IllegalArgumentException("Use an ordinary single chest or barrel");
                    sim.camp.stockpile=p;
                }else if(op.equals("ignorebed")){sim.camp.ignoredBeds.add(p);sim.camp.beds.remove(p);for(var s:sim.camp.settlers)if(p.equals(s.bed))s.bed=null;}
                else{
                    if(!FrontierSimulation.shelteredBed(world,p))throw new IllegalArgumentException("Use the HEAD of a bed with headroom and a solid roof 2-6 blocks above it");
                    if(sim.camp.beds.size()>=256)throw new IllegalArgumentException("Too many registered beds");
                    sim.camp.ignoredBeds.remove(p);sim.camp.beds.add(p);
                }
                sim.save();player.reply("Camp "+op+" saved at "+p);
            }
            case "job","work" -> {
                if(args.size()<3)throw new IllegalArgumentException("Use /frontier "+op+" <settler-id> ...");
                List<Settler> matches=sim.camp.settlers.stream().filter(s->s.resident()&&s.id.toString().startsWith(args.get(1))).toList();
                if(args.get(1).length()<4||matches.size()!=1)throw new IllegalArgumentException("Use a unique ID prefix from /frontier settlers");
                Settler s=matches.getFirst();
                if(op.equals("job")){
                    if(args.size()!=3)throw new IllegalArgumentException("job <id> idle|lumberjack|miner|farmer");
                    Job next=Job.valueOf(args.get(2).toUpperCase(Locale.ROOT));
                    if(next!=s.job&&world.actor(s.id).isPresent()&&world.carried(s.id)>0)throw new IllegalArgumentException("Let this settler deliver their current supplies before changing jobs");
                    s.job=next;s.work=null;s.workIndex=0;s.replant=null;s.seed="";s.prepared=false;
                }else{
                    if(args.size()!=5&&args.size()!=6)throw new IllegalArgumentException("work <id> x y z [north|south|east|west]");
                    var p=position(args,2);near(player,p,32);
                    if(!within(p,sim.camp.camp,32))throw new IllegalArgumentException("Starter work areas must be within 32 blocks of camp");
                    if(world.block(p).isEmpty())throw new IllegalArgumentException("Work area must be loaded");
                    String direction=args.size()==6?args.get(5):"north";
                    if(!Set.of("north","south","east","west").contains(direction))throw new IllegalArgumentException("Choose north, south, east or west");
                    if(s.job==Job.IDLE)throw new IllegalArgumentException("Assign a job first");
                    s.work=p;s.direction=direction;s.workIndex=0;s.replant=null;s.seed="";s.prepared=false;
                }
                sim.save();player.reply(s.name+": "+s.job+", work "+s.work);
            }
            default -> throw new IllegalArgumentException("Use /frontier help");
        }
    }
    private static CodaBlockPos position(List<String>a,int i){return new CodaBlockPos(Integer.parseInt(a.get(i)),Integer.parseInt(a.get(i+1)),Integer.parseInt(a.get(i+2)));}
    private static void near(CodaCommandContext player,CodaBlockPos p,int radius)throws Exception{
        var at=player.position();double d=Math.pow(at.x()-p.x()-.5,2)+Math.pow(at.y()-p.y(),2)+Math.pow(at.z()-p.z()-.5,2);
        if(d>(double)radius*radius)throw new IllegalArgumentException("Stand within "+radius+" blocks of the target");
    }
}
