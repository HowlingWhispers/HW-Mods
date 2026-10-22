package dev.howlingwhispers.frontier;

import dev.howlingwhispers.codaloader.api.*;
import java.nio.file.*;
import java.util.*;
import static dev.howlingwhispers.frontier.FrontierTest.*;

/** Exercises the registered command, including saved selection and failed replacement. */
public final class FrontierCommandTest {
    static final class Player implements CodaCommandContext {
        final View view; final List<String> replies=new ArrayList<>(); Optional<CodaBlockTarget> target=Optional.empty();
        Player(View view){this.view=view;}
        public void withWorld(WorldAction action)throws Exception{action.run(view);}
        public UUID playerId(){return new UUID(0,1);}
        public Path worldDirectory(){return view.root;}
        public CodaPosition position(){return new CodaPosition("minecraft:overworld",.5,64,.5,0,0);}
        public Optional<CodaBlockTarget> targetBlock(){return target;}
        public void reply(String s){replies.add(s);}
        public void teleport(CodaPosition p){throw new AssertionError("No teleport expected");}
    }
    public static void main(String[]args)throws Exception{
        Path profile=Files.createTempDirectory("frontier-target-command-");
        Files.createDirectories(profile.resolve("config"));Files.writeString(profile.resolve("config/howl-play-mode.txt"),"frontier\n");
        Files.createDirectories(profile.resolve("save"));
        var world=new World();var view=new View(profile.resolve("save"),world);var player=new Player(view);
        new FrontierMod().onInitialize(new CodaContext("0.0.44","26.4-snapshot-3",profile,profile.resolve("config"),"howl_frontier",List.of("howl_frontier")));
        var command=CodaCommands.registrations().stream().filter(r->r.name().equals("frontier")).findFirst().orElseThrow().command();
        command.execute(player,List.of("camp","0","64","0"));
        player.target=Optional.of(new CodaBlockTarget("minecraft:overworld",STOCK));
        command.execute(player,List.of("stockpile"));
        check(new SettlementStore(view.root).load().stockpile.equals(STOCK),"Crosshair chest saved without coordinates");
        check(player.replies.getLast().contains("Camp stockpile set"),"Selection confirms success");
        var barrel=new CodaBlockPos(2,64,2);world.blocks.put(barrel,World.b("minecraft:barrel",Map.of(),true));
        player.target=Optional.of(new CodaBlockTarget("minecraft:overworld",barrel));command.execute(player,List.of("stockpile"));
        check(new SettlementStore(view.root).load().stockpile.equals(barrel),"Crosshair barrel replaces chest");
        player.target=Optional.empty();command.execute(player,List.of("stockpile"));
        check(player.replies.getLast().contains("within reach"),"Miss gives actionable targeting help");
        check(new SettlementStore(view.root).load().stockpile.equals(barrel),"Miss preserves saved stockpile");
        player.target=Optional.of(new CodaBlockTarget("minecraft:overworld",CAMP));command.execute(player,List.of("stockpile"));
        check(player.replies.getLast().contains("single chest or barrel"),"Wrong block is refused");
        check(new SettlementStore(view.root).load().stockpile.equals(barrel),"Wrong block preserves stockpile");
        var far=new CodaBlockPos(30,64,0);player.target=Optional.of(new CodaBlockTarget("minecraft:overworld",far));command.execute(player,List.of("stockpile"));
        check(player.replies.getLast().contains("24 blocks"),"Camp distance remains enforced");
        world.blocks.put(STOCK,World.b("minecraft:chest",Map.of("type","left"),true));
        player.target=Optional.of(new CodaBlockTarget("minecraft:overworld",STOCK));command.execute(player,List.of("stockpile"));
        check(player.replies.getLast().contains("Use a single chest"),"Double chest is refused before persisting");
        world.blocks.put(STOCK,World.b("minecraft:chest",Map.of("type","single"),true));
        player.target=Optional.of(new CodaBlockTarget("minecraft:the_nether",STOCK));command.execute(player,List.of("stockpile"));
        check(player.replies.getLast().contains("in this world"),"Cross-dimension hit is refused");
        player.target=Optional.of(new CodaBlockTarget("minecraft:overworld",STOCK));world.loaded=false;
        command.execute(player,List.of("stockpile"));world.loaded=true;
        check(player.replies.getLast().contains("loaded"),"Unloaded target is refused");
        check(new SettlementStore(view.root).load().stockpile.equals(barrel),"All refusals preserve old stockpile");
        command.execute(player,List.of("stockpile","1","64","2"));
        check(new SettlementStore(view.root).load().stockpile.equals(STOCK),"Advanced coordinate command remains compatible");
        command.execute(player,List.of("stockpile","1"));check(player.replies.getLast().contains("Look at"),"Incomplete coordinates teach default command");
        System.out.println("PASS: "+checks+" Frontier stockpile command checks");
    }
}
