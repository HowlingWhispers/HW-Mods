package dev.howlingwhispers.frontier;

import dev.howlingwhispers.codaloader.api.*;
import java.nio.file.*;
import java.util.*;
import static dev.howlingwhispers.frontier.FrontierTest.*;
import static dev.howlingwhispers.frontier.Settlement.*;

/** Failure containment and the explanations players see in /frontier status and settlers. */
public final class FrontierDiagnosticsTest {
    record Camp(Path root,World world,View view,FrontierSimulation sim){}
    static Camp camp()throws Exception{
        Path root=Files.createTempDirectory("frontier-diagnostics-");var world=new World();var view=new View(root,world);
        var sim=new FrontierSimulation();sim.open(view,"session");sim.establish(world,CAMP);
        return new Camp(root,world,view,sim);
    }
    static Settler resident(Camp c,String name,CodaBlockPos bed,boolean withActor){
        var s=new Settler(UUID.randomUUID(),name);s.fate=Fate.RESIDENT;s.bed=bed;s.spawn=add(CAMP,24,0,0);
        c.sim().camp.settlers.add(s);
        if(withActor)c.world().actors.put(s.id,new CodaLivingWorld.Actor(s.id,CAMP.x()+2.5,CAMP.y(),CAMP.z()+.5,20,false));
        return s;
    }

    public static void main(String[]args)throws Exception{
        failureIsolation();
        arrivalExplanations();
        missingStockpile();
        unreadableSaveIsNeverOverwritten();
        commandDiagnostics();
        System.out.println("PASS: "+checks+" Frontier failure containment and diagnostics checks");
    }

    static void failureIsolation()throws Exception{
        var c=camp();
        var broken=resident(c,"Broken",null,true);var healthy=resident(c,"Healthy",null,true);
        c.world().broken=broken.id;
        // The loader disables a listener after three consecutive failures; 15 bad seconds must not escape.
        advance(c.sim(),c.view(),15);
        check(healthy.hunger<80,"A failing settler does not stop hunger for the others");
        check(broken.activity.contains("Update failed"),"The failing settler is marked in /frontier settlers");
        check(c.sim().lastError!=null&&c.sim().lastError.contains("Broken")&&c.sim().errorCount>=15,"The failure is recorded for /frontier status");
        check(!c.sim().arrivalStatus.startsWith("Waiting for the first"),"Arrivals are still evaluated after a settler failure");
        check(c.sim().camp.elapsed==15*20,"Camp time keeps advancing every second");
    }

    static void arrivalExplanations()throws Exception{
        var c=camp();var sim=c.sim();var world=c.world();
        world.blocks.remove(BED);advance(sim,c.view(),2);
        check(sim.arrivalStatus.contains("no sheltered beds found yet"),"No beds is explained");
        world.blocks.put(BED,World.b("minecraft:red_bed",Map.of("part","head","occupied","false"),false));sim.camp.beds.add(BED);
        var claimant=resident(c,"Claimant",BED,false);advance(sim,c.view(),1);
        check(sim.arrivalStatus.contains("all 1 sheltered beds are claimed"),"Claimed beds are explained");
        sim.camp.settlers.remove(claimant);
        sim.camp.arrival=1200;advance(sim,c.view(),1);
        check(sim.arrivalStatus.contains("Next traveller in about 59s"),"The countdown is shown");
        world.blocks.put(BED,World.b("minecraft:red_bed",Map.of("part","head","occupied","true"),false));
        sim.camp.arrival=20;advance(sim,c.view(),1);
        check(sim.arrivalStatus.contains("occupied")&&sim.camp.settlers.isEmpty(),"An occupied bed is explained and admits nobody");
        world.blocks.put(BED,World.b("minecraft:red_bed",Map.of("part","head","occupied","false"),false));
        world.flooded=p->Math.abs(p.x())>=20||Math.abs(p.z())>=20;advance(sim,c.view(),1);
        check(sim.arrivalStatus.contains("no safe arrival point")&&sim.camp.settlers.isEmpty(),"A missing arrival point is explained and admits nobody");
        world.flooded=p->false;
        world.blocks.put(CAMP,World.b("minecraft:campfire",Map.of("lit","false"),false));advance(sim,c.view(),1);
        check(sim.arrivalStatus.contains("is not lit"),"An unlit campfire is explained");
        world.blocks.remove(CAMP);advance(sim,c.view(),1);
        check(sim.arrivalStatus.contains("is gone"),"A removed campfire is explained");
        world.blocks.put(CAMP,World.b("minecraft:campfire",Map.of("lit","true"),false));
        world.loaded=false;advance(sim,c.view(),1);world.loaded=true;
        check(sim.arrivalStatus.contains("not loaded"),"An unloaded camp is explained");
        for(int i=0;i<CAPACITY;i++)resident(c,"Resident "+i,null,false);advance(sim,c.view(),1);
        check(sim.arrivalStatus.contains("full ("+CAPACITY+"/"+CAPACITY+")"),"A full camp is explained");
        sim.camp.settlers.clear();
        for(int i=0;i<512;i++){var dead=new Settler(UUID.randomUUID(),"Past "+i);dead.fate=Fate.DEAD;dead.spawn=add(CAMP,24,0,0);sim.camp.settlers.add(dead);}
        advance(sim,c.view(),1);
        check(sim.arrivalStatus.contains("512"),"The lifetime record limit is explained");
        check(sim.lastError==null,"Explanations are not errors");
    }

    static void missingStockpile()throws Exception{
        var c=camp();var s=resident(c,"Courier",null,true);
        c.sim().camp.stockpile=STOCK;s.job=Job.LUMBERJACK;s.work=add(CAMP,5,0,0);s.prepared=true;s.returning=true;
        c.world().actors.put(s.id,new CodaLivingWorld.Actor(s.id,STOCK.x()+.5,STOCK.y(),STOCK.z()+.5,20,false));
        c.world().blocks.remove(STOCK);int deliveries=c.world().delivered;
        advance(c.sim(),c.view(),1);
        check(s.activity.contains("Stockpile missing")&&!s.activity.contains("full"),"A removed stockpile is reported as missing, not full");
        check(c.world().delivered==deliveries,"Nothing is delivered into a missing stockpile");
        check(FrontierSimulation.stockpileProblem(c.world(),STOCK).contains("missing"),"Status explains the missing stockpile");
        check(FrontierSimulation.stockpileProblem(c.world(),null).contains("/frontier stockpile"),"Status explains how to set a stockpile");
    }

    static void unreadableSaveIsNeverOverwritten()throws Exception{
        var c=camp();c.sim().save();
        Path file=c.root().resolve("howl_frontier/settlement.properties");
        Files.writeString(file,Files.readString(file).replace("schema=1","schema=99"));byte[] corrupt=Files.readAllBytes(file);
        Files.deleteIfExists(c.root().resolve("howl_frontier/settlement.previous.properties"));
        var fresh=new FrontierSimulation();var world=new World();var view=new View(c.root(),world);
        world.blocks.put(new CodaBlockPos(9,64,9),World.b("minecraft:campfire",Map.of("lit","true"),false));
        fresh.placed(new CodaBlockPlacements.Placement("minecraft:overworld",new CodaBlockPos(9,64,9),"minecraft:campfire"));
        advance(fresh,view,5);
        check(fresh.loadError!=null&&fresh.camp==null,"An unreadable save blocks the simulation instead of resetting it");
        check(Arrays.equals(Files.readAllBytes(file),corrupt),"A new campfire never overwrites the unreadable save");
        check(!Files.exists(c.root().resolve("howl_frontier/settlement.previous.properties")),"No backup rotation happens while blocked");
        try{fresh.requireReadable();throw new AssertionError("Blocked state accepted a command");}
        catch(IllegalArgumentException expected){check(expected.getMessage().contains("settlement.previous.properties"),"Commands explain how to recover");}
    }

    static void commandDiagnostics()throws Exception{
        Path profile=Files.createTempDirectory("frontier-diagnostics-command-");
        Files.createDirectories(profile.resolve("config"));Files.writeString(profile.resolve("config/howl-play-mode.txt"),"frontier\n");
        new FrontierMod().onInitialize(new CodaContext("0.0.45","26.4-snapshot-3",profile,profile.resolve("config"),"howl_frontier",List.of("howl_frontier")));
        var command=CodaCommands.registrations().stream().filter(r->r.name().equals("frontier")).findFirst().orElseThrow().command();

        // A healthy camp with one resident, one dead and one departed settler.
        var c=camp();c.sim().camp.stockpile=STOCK;
        var alive=resident(c,"Asta",BED,false);
        var dead=resident(c,"Bryn",null,false);dead.fate=Fate.DEAD;
        var left=resident(c,"Liv",null,false);left.fate=Fate.LEFT;
        c.sim().save();
        var player=new FrontierCommandTest.Player(c.view());
        command.execute(player,List.of("settlers"));
        check(player.replies.stream().anyMatch(r->r.contains("Asta"))&&player.replies.stream().noneMatch(r->r.contains("| Bryn |")),"Default list shows active settlers only");
        check(player.replies.getLast().contains("1 dead (Bryn)")&&player.replies.getLast().contains("1 departed (Liv)"),"Past settlers are summarised by name");
        player.replies.clear();command.execute(player,List.of("settlers","all"));
        check(player.replies.size()==3&&player.replies.stream().anyMatch(r->r.contains("DEAD"))&&player.replies.stream().anyMatch(r->r.contains("LEFT")),"settlers all lists every record");
        command.execute(player,List.of("settlers","bogus"));check(player.replies.getLast().contains("settlers all"),"Bad argument shows usage");
        player.replies.clear();command.execute(player,List.of("status"));
        check(player.replies.getFirst().contains("1 resident"),"Status counts residents");
        check(player.replies.stream().anyMatch(r->r.startsWith("Arrivals: ")),"Status explains arrivals");
        check(player.replies.stream().anyMatch(r->r.startsWith("Stockpile: ")&&r.contains("has food")),"Status reports stockpile and food");
        check(player.replies.stream().anyMatch(r->r.contains("1 dead, 1 departed")),"Status mentions past settlers");

        // The same command against an unreadable save explains recovery and changes nothing.
        Path file=c.root().resolve("howl_frontier/settlement.properties");
        Files.writeString(file,Files.readString(file).replace("schema=1","schema=99"));byte[] corrupt=Files.readAllBytes(file);
        var other=new FrontierCommandTest.Player(new View(c.root(),c.world()));
        // A new session forces a reload, as reopening the world would.
        CodaServerTicks.dispatch(new CodaServerTickContext("reopened",20,Optional.of(other.view)));
        command.execute(other,List.of("status"));
        check(other.replies.getLast().contains("could not be read"),"Status explains an unreadable save");
        command.execute(other,List.of("camp","0","64","0"));
        check(other.replies.getLast().contains("could not be read")&&Arrays.equals(Files.readAllBytes(file),corrupt),"No command can replace an unreadable save");
    }
}
