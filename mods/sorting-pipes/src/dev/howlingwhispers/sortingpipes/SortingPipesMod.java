package dev.howlingwhispers.sortingpipes;

import dev.howlingwhispers.codaloader.api.*;
import dev.howlingwhispers.sortingpipes.PipeSettings.*;
import java.nio.file.*;
import java.util.*;
import java.io.*;

public final class SortingPipesMod implements CodaMod {
    private final PipeSettings settings=new PipeSettings();
    private final SortingTransport transport=new SortingTransport(settings);
    @Override public void onInitialize(CodaContext context) throws Exception {
        // An old loader must reject this mod explicitly instead of registering inert gameplay.
        try { CodaWorldView.class.getMethod("transferFiltered",String.class,CodaBlockPos.class,CodaBlockPos.class,int.class,CodaItemFilter.class); }
        catch (NoSuchMethodException e) { throw new IllegalStateException("Install the included Sorting Pipes patched H.O.W.L. loader",e); }
        List<String> blocks=List.of(SortingTransport.EXTRACT,SortingTransport.PIPE,SortingTransport.SORT,SortingTransport.ROUND,SortingTransport.EQUAL);
        for (String id:blocks) context.registerBlock(id,0.8f);
        context.registerBlockEntityType(SortingTransport.HOLDER,blocks.subList(0,3));
        context.registerBlockEntityTick(SortingTransport.HOLDER,transport::pipeTick);
        context.registerCreativeTab(SortingTransport.NS+"pipes","H.O.W.L. Sorting Pipes",SortingTransport.SORT,blocks);
        context.registerServerTick("sorting_pipes",transport::serverTick);
        context.registerCommand("howlpipes","Configure item filters and pipe inputs",this::command);
        System.out.println("[Sorting Pipes] 1.0.1 for Minecraft 26.4 Snapshot 3 initialized");
    }
    private void command(CodaCommandContext player,List<String> arguments) throws Exception {
        String[] args=arguments.toArray(String[]::new);
        if (args.length==0 || args[0].equals("help")) {
            player.reply("/howlpipes status | show x y z | filter x y z whitelist|blacklist item_id[,item_id] | clear x y z | input x y z auto|north|south|east|west|up|down. Attach one round-robin OR equalizer block beside the extractor."); return;
        }
        if (args[0].equals("status") && args.length==1) { player.reply(transport.status()); return; }
        if (args.length<4) { player.reply("Use /howlpipes help for syntax."); return; }
        try {
            CodaBlockPos pos=new CodaBlockPos(Integer.parseInt(args[1]),Integer.parseInt(args[2]),Integer.parseInt(args[3]));
            CodaPosition playerPos=player.position();
            double distance=Math.pow(pos.x()+0.5-playerPos.x(),2)+Math.pow(pos.y()+0.5-playerPos.y(),2)+Math.pow(pos.z()+0.5-playerPos.z(),2);
            if (distance>4096) throw new IllegalArgumentException("Stand within 64 blocks of the pipe");
            CodaSingleplayerWorld w=player.singleplayerWorld();
            if (!w.isLoaded(pos)) throw new IllegalArgumentException("That pipe is not in a loaded chunk");
            if (!(w.isBlock(pos,SortingTransport.EXTRACT)||w.isBlock(pos,SortingTransport.SORT)||w.isBlock(pos,SortingTransport.PIPE)))
                throw new IllegalArgumentException("No Sorting Pipes pipe at those coordinates");
            settings.load(player.worldDirectory()); Key key=new Key(playerPos.dimension(),pos); Setting current=settings.get(key);
            switch (args[0]) {
                case "show" -> {
                    if (args.length!=4) throw new IllegalArgumentException("show x y z");
                    player.reply("Pipe "+pos+": "+current.filter().mode()+" "+new TreeSet<>(current.filter().items())+", input="+current.input()); return;
                }
                case "filter" -> {
                    if (args.length!=6) throw new IllegalArgumentException("filter x y z whitelist|blacklist item_id[,item_id]");
                    var mode=CodaItemFilter.Mode.valueOf(args[4].toUpperCase(Locale.ROOT));
                    var items=new HashSet<>(Arrays.asList(args[5].split(",",-1)));
                    settings.update(key,new Setting(new CodaItemFilter(mode,items),current.input()));
                }
                case "clear" -> {
                    if (args.length!=4) throw new IllegalArgumentException("clear x y z");
                    settings.update(key,new Setting(CodaItemFilter.all(),current.input()));
                }
                case "input" -> {
                    if (args.length!=5 || !w.isBlock(pos,SortingTransport.EXTRACT)) throw new IllegalArgumentException("input x y z direction requires an extractor");
                    settings.update(key,new Setting(current.filter(),args[4].toLowerCase(Locale.ROOT)));
                }
                default -> throw new IllegalArgumentException("Unknown operation; use /howlpipes help");
            }
            player.reply("Pipe settings saved.");
        } catch (IllegalArgumentException e) { player.reply("Pipe settings unchanged: "+e.getMessage()); }
    }
}
