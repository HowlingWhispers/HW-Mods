package dev.howlingwhispers.sortingpipes;

import dev.howlingwhispers.codaloader.api.*;
import dev.howlingwhispers.sortingpipes.PipeSettings.*;
import java.nio.file.*;
import java.util.*;

public final class SortingCommandTest {
    private static int checks;
    private static void check(boolean b,String message) { checks++; if(!b)throw new AssertionError(message); }
    static final class Player implements CodaCommandContext {
        final Path dir; String reply; final List<String> messages=new ArrayList<>();
        Player(Path dir) { this.dir=dir; }
        public UUID playerId() { return new UUID(0,1); }
        public Path worldDirectory() { return dir; }
        public CodaPosition position() { return new CodaPosition("minecraft:overworld",0,64,0,0,0); }
        public void reply(String message) { reply=message; messages.add(message); }
        public void teleport(CodaPosition p) { throw new AssertionError("No teleport"); }
        public CodaSingleplayerWorld singleplayerWorld() { return new CodaSingleplayerWorld() {
            public boolean isLoaded(CodaBlockPos p) { return p.x()<10; }
            public boolean isBlock(CodaBlockPos p,String id) { return p.x()==0&&p.y()==64&&p.z()==0&&id.equals(SortingTransport.EXTRACT); }
            public int transfer(CodaBlockPos from,CodaBlockPos to,int max) { throw new AssertionError("Commands never move items"); }
        }; }
    }
    public static void main(String[] args) throws Exception {
        Path game=Files.createTempDirectory("sorting-command-test-");
        new SortingPipesMod().onInitialize(new CodaContext("0.0.38","26.4-snapshot-3",game,game.resolve("config"),"howl_sorting_pipes",List.of("howl_sorting_pipes")));
        var command=CodaCommands.registrations().stream().filter(r->r.name().equals("howlpipes")).findFirst().orElseThrow().command();
        Player player=new Player(Files.createTempDirectory("sorting-save-test-"));
        command.execute(player,List.of("filter","0","64","0","whitelist","minecraft:iron_ingot,minecraft:gold_ingot"));
        check(player.reply.startsWith("Filter saved: whitelist"),"Filter command accepted");
        PipeSettings s=new PipeSettings();s.load(player.dir);Key key=new Key("minecraft:overworld",new CodaBlockPos(0,64,0));
        check(s.get(key).filter().items().size()==2,"Both filter IDs persisted");
        command.execute(player,List.of("filter","0","64","0","whitelist","BAD:ID"));
        check(player.reply.startsWith("Pipe settings unchanged:"),"Invalid ID reports unchanged");
        s.reset();s.load(player.dir);check(s.get(key).filter().items().size()==2,"Invalid filter did not replace saved settings");
        command.execute(player,List.of("input","0","64","0","west"));
        s.reset();s.load(player.dir);check(s.get(key).input().equals("west"),"Explicit input persisted");
        command.execute(player,List.of("show","0","64","0"));check(player.reply.contains("input=west"),"Show command reports saved setting");
        command.execute(player,List.of("filter","20","64","0","whitelist","minecraft:diamond"));check(player.reply.contains("loaded chunk"),"Unloaded edit refused");
        command.execute(player,List.of("filter","1000","64","0","whitelist","minecraft:diamond"));check(player.reply.contains("64 blocks"),"Remote edit refused");
        command.execute(player,List.of("clear","0","64","0"));s.reset();s.load(player.dir);check(s.get(key).filter().allows("minecraft:stone"),"Clear restores pass-all filter");
        for(String topic:List.of("setup","filters","attachments")) {
            player.messages.clear(); command.execute(player,List.of("help",topic));
            check(player.messages.size()==4,"Dedicated readable help page: "+topic);
        }
        player.messages.clear();command.execute(player,List.of("modes"));
        check(player.messages.stream().anyMatch(m->m.contains("both together pause")),"Modes explains conflicting attachments");
        check(player.messages.stream().anyMatch(m->m.contains("fewest of that item")),"Equalizer help describes per-item stock");
        check(player.messages.stream().anyMatch(m->m.contains("FACE of the EXTRACTOR")),"Attachment placement specified");
        player.messages.clear();command.execute(player,List.of("help","filters"));
        check(player.messages.stream().anyMatch(m->m.contains("ONLY listed")&&m.contains("EXCEPT listed")),"Filter meanings explained");
        s.reset();s.load(player.dir);check(s.get(key).filter().allows("minecraft:stone")&&s.get(key).input().equals("west"),"Help commands never change settings");
        check(!Files.exists(game.resolve("resourcepacks")),"Loader owns automatic declared-resource activation before first reload");
        System.out.println("PASS: "+checks+" configuration command checks");
    }
}
