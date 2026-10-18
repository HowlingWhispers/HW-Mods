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
        // Older loader builds reject valid mod IDs in the recipe-category registry.
        // Keep recipes and transport usable until H.O.W.L. fixes that validator.
        try {
            context.registerRecipeBookCategory("pipes", "Sorting Pipes", SortingTransport.SORT, blocks);
        } catch (IllegalArgumentException e) {
            if (!"Invalid recipe-book category".equals(e.getMessage())) throw e;
            System.err.println("[Sorting Pipes] Recipe-book subcategory disabled by H.O.W.L. validator bug; crafting recipes remain available.");
        }
        context.registerServerTick("sorting_pipes",transport::serverTick);
        context.registerCommand("howlpipes","Configure item filters and pipe inputs",this::command);
        System.out.println("[Sorting Pipes] 1.0.5 for Minecraft 26.4 Snapshot 3 initialized");
    }
    private static void help(CodaCommandContext player,String topic) throws Exception {
        switch(topic.toLowerCase(Locale.ROOT)) {
            case "filters" -> {
                player.reply("FILTERS: whitelist lets ONLY listed items through; blacklist lets everything EXCEPT listed items through. Configure the pipe on the branch you want to control.");
                player.reply("Example, for a pipe at 3 64 1: /howlpipes filter 3 64 1 whitelist minecraft:iron_ingot");
                player.reply("To reject dirt and cobblestone: /howlpipes filter 3 64 1 blacklist minecraft:dirt,minecraft:cobblestone");
                player.reply("Replace the coordinates with your pipe's block coordinates (F3). Exact item IDs only. /howlpipes clear 3 64 1 allows all items; /howlpipes show 3 64 1 shows the saved filter.");
            }
            case "attachments", "modes" -> {
                player.reply("ROUTING: place one attachment touching a FACE of the EXTRACTOR (not a transport pipe). Attachments are separate blocks, not covers. No command is needed to activate them.");
                player.reply("ROUND ROBIN (copper): sends one item to output A, then B, then C, repeating. Full or incompatible outputs are skipped.");
                player.reply("EQUALIZER (gold): sends each incoming item to the output holding the fewest of that item. With 20 iron in A and 0 in B, incoming iron goes to B first. It does not move old items between outputs.");
                player.reply("Use either Round Robin OR Equalizer; both together pause the extractor. Remove the attachment to restore the default first available output. Filters still apply in every mode.");
            }
            case "setup" -> {
                player.reply("SETUP: source chest/barrel -> Extractor Pipe (oak) -> Transport Pipe (iron) -> output chest/barrel. Touching block faces connect; diagonals do not. No engine is needed.");
                player.reply("With exactly one chest/barrel beside the extractor, input auto selects it. For several, /howlpipes input x y z west selects the chest on the WEST side of that extractor; also north/south/east/up/down.");
                player.reply("Put Sorting Pipes (diamond) on branches and use /howlpipes help filters. Add one attachment beside the extractor for routing; see /howlpipes help attachments.");
                player.reply("Items stay in the source until an allowed output has room. Transfers are one item per pulse, not a stack. /howlpipes status helps check idle networks.");
            }
            case "overview" -> {
                player.reply("SORTING PIPES: /howlpipes help setup | /howlpipes help filters | /howlpipes help attachments (or /howlpipes modes).");
                player.reply("Filters and input sides use commands. Round Robin and Equalizer activate by placing one beside the extractor. There is currently no right-click settings GUI.");
                player.reply("Commands: /howlpipes status | show x y z | filter x y z whitelist|blacklist item_id[,item_id] | clear x y z | input x y z auto|north|south|east|west|up|down.");
            }
            default -> player.reply("Help topics: /howlpipes help setup | /howlpipes help filters | /howlpipes help attachments.");
        }
    }
    private void command(CodaCommandContext player,List<String> arguments) throws Exception {
        String[] args=arguments.toArray(String[]::new);
        if (args.length==0 || args[0].equals("help") || args[0].equals("modes")) {
            String topic=args.length>1?args[1]:args.length>0&&args[0].equals("modes")?"attachments":"overview";
            help(player,topic); return;
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
            player.reply(switch(args[0]) {
                case "filter" -> "Filter saved: "+args[4].toLowerCase(Locale.ROOT)+" "+args[5]+". Use /howlpipes show "+args[1]+" "+args[2]+" "+args[3]+" to check it.";
                case "clear" -> "Filter cleared: this pipe now passes all items. Input direction is unchanged.";
                case "input" -> "Input saved: "+args[4].toLowerCase(Locale.ROOT)+" (the side of the extractor containing the source chest/barrel).";
                default -> "Pipe settings saved.";
            });
        } catch (IllegalArgumentException e) { player.reply("Pipe settings unchanged: "+e.getMessage()); }
    }
}
