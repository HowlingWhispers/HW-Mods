import dev.howlingwhispers.buildcraft.BuildCraftGlassPipeDemo;
import dev.howlingwhispers.codaloader.api.CodaBlockPos;
import dev.howlingwhispers.codaloader.api.CodaCommandContext;
import dev.howlingwhispers.codaloader.api.CodaPosition;
import dev.howlingwhispers.codaloader.api.CodaSingleplayerWorld;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.UUID;

/** Validate real in-game command geometry before it touches any chest. */
public final class BuildCraftGlassPipeDemoTest {
    private static int checks;
    private static void check(boolean yes, String why) {
        checks++;
        if (!yes) throw new AssertionError(why);
    }
    private static void rejected(Action action, String why) throws Exception {
        try { action.run(); }
        catch (IllegalArgumentException expected) {
            checks++;
            return;
        }
        throw new AssertionError(why);
    }
    @FunctionalInterface interface Action { void run() throws Exception; }

    static final class World implements CodaSingleplayerWorld {
        final Set<CodaBlockPos> wooden = new HashSet<>();
        final Set<CodaBlockPos> cobble = new HashSet<>();
        boolean loaded = true;
        int transfers;
        int available = 19;
        int output;
        @Override public boolean isLoaded(CodaBlockPos pos) {
            return loaded;
        }
        @Override public boolean isBlock(CodaBlockPos pos, String blockId) {
            return (blockId.equals("buildcrafttransport:wood_item") && wooden.contains(pos))
                    || (blockId.equals("buildcrafttransport:cobblestone_item") && cobble.contains(pos));
        }
        @Override public int transfer(CodaBlockPos from, CodaBlockPos to, int maximum) {
            transfers++;
            int count = Math.min(available, maximum);
            available -= count;
            output += count;
            return count;
        }
    }
    static final class Player implements CodaCommandContext {
        final World world = new World();
        String lastReply;
        @Override public UUID playerId() { return UUID.randomUUID(); }
        @Override public Path worldDirectory() { return Path.of("fixture"); }
        @Override public CodaPosition position() {
            return new CodaPosition("minecraft:overworld", 0,64,0,0,0);
        }
        @Override public void reply(String message) { lastReply=message; }
        @Override public void teleport(CodaPosition to) {
            throw new AssertionError("BuildCraft must not teleport players");
        }
        @Override public CodaSingleplayerWorld singleplayerWorld() { return world; }
    }

    public static void main(String[] args) throws Exception {
        Player user = new Player();
        user.world.wooden.add(new CodaBlockPos(1,64,0));
        for(int x=2;x<=3;x++) user.world.cobble.add(new CodaBlockPos(x,64,0));
        List<String> command = List.of("pulse","0","64","0","4","64","0");
        BuildCraftGlassPipeDemo.execute(user, List.of());
        check(user.lastReply.contains("WOODEN PIPE"), "Command gives player setup instructions");
        check(user.world.transfers == 0, "Help cannot mutate world");

        BuildCraftGlassPipeDemo.execute(user, command);
        check(user.world.transfers == 1, "Native BuildCraft blocks trigger exactly one chest transaction");
        check(user.world.output == 16 && user.world.available == 3,
                "Pulse respects 16 item cap and item conservation");
        check(user.lastReply.contains("16 real items"), "Readable successful player feedback");

        BuildCraftGlassPipeDemo.execute(user, command);
        check(user.world.output == 19 && user.world.available == 0,
                "Second command only transfers remaining items");
        BuildCraftGlassPipeDemo.execute(user, command);
        check(user.lastReply.contains("Nothing moved."), "Empty chest gives safe feedback");

        Player incomplete = new Player();
        incomplete.world.wooden.add(new CodaBlockPos(1,64,0));
        rejected(() -> BuildCraftGlassPipeDemo.execute(incomplete, command),
                "Missing original BuildCraft pipe must prevent transfer");
        check(incomplete.world.transfers == 0, "Invalid route cannot mutate chests");
        incomplete.world.loaded = false;
        rejected(() -> BuildCraftGlassPipeDemo.execute(incomplete, command),
                "Unloaded route must be refused");
        check(incomplete.world.transfers == 0, "Unloaded chests never transfer");

        Player bent = new Player();
        rejected(() -> BuildCraftGlassPipeDemo.execute(bent,
                List.of("pulse","0","64","0","3","64","3")),
                "Diagonal/angled routes refused");
        check(bent.world.transfers == 0, "Bad geometry never touches inventory");
        rejected(() -> BuildCraftGlassPipeDemo.execute(bent,
                List.of("pulse","0","64","0","22","64","0")),
                "Overlong pipe arrays refused");
        rejected(() -> BuildCraftGlassPipeDemo.execute(bent,
                List.of("pulse","0","64","0","0","64","0")),
                "Same chest source/target refused");
        rejected(() -> BuildCraftGlassPipeDemo.execute(bent,
                List.of("pulse","0","64","x","4","64","0")),
                "Invalid numeric coordinates refused");
        check(bent.world.transfers == 0, "No invalid pulse changes items");

        System.out.println("PASS: " + checks + " authentic native BuildCraft block transfer checks");
    }
}
