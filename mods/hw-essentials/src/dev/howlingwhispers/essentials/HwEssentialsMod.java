package dev.howlingwhispers.essentials;

import dev.howlingwhispers.codaloader.api.CodaContext;
import dev.howlingwhispers.codaloader.api.CodaMod;
import dev.howlingwhispers.codaloader.api.CodaCommandContext;
import dev.howlingwhispers.codaloader.api.CodaPosition;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class HwEssentialsMod implements CodaMod {
    private HomeStore store;
    private record PlayerWorld(Path world, UUID player) {}
    private final Map<PlayerWorld, CodaPosition> returnPoints = new ConcurrentHashMap<>();

    private PlayerWorld playerWorld(CodaCommandContext source) throws Exception {
        return new PlayerWorld(source.worldDirectory().toAbsolutePath().normalize(), source.playerId());
    }

    @Override public void onInitialize(CodaContext context) throws Exception {
        Path config = context.configDirectory().resolve("essentials.properties");
        Properties settings = new Properties();
        if (Files.exists(config)) {
            try (InputStream input = Files.newInputStream(config)) { settings.load(input); }
        } else {
            settings.setProperty("max-homes", "10");
            try (OutputStream output = Files.newOutputStream(config)) { settings.store(output, "HW Essentials"); }
        }
        store = new HomeStore(Integer.parseInt(settings.getProperty("max-homes", "10").trim()));
        context.registerCommand("sethome", "Save a home in this world", this::setHome);
        context.registerCommand("home", "Return to a saved home", this::goHome);
        context.registerCommand("back", "Return to where your last home trip started", this::goBack);
        context.registerCommand("homes", "List your homes in this world", this::listHomes);
        context.registerCommand("delhome", "Delete one of your homes", this::deleteHome);
        context.registerCommand("hwessentials", "HW Essentials version and help", (source, args) -> {
            if (!args.isEmpty()) throw new IllegalArgumentException("Usage: /hwessentials");
            source.reply("HW Essentials 0.2.0 • Minecraft 26.4 Snapshot 3 • /sethome [name], /home [name], /back, /homes, /delhome <name>");
        });
        System.out.println("[HW Essentials] Homes filed. 6 commands registered for Minecraft 26.4 Snapshot 3.");
    }

    private String optionalName(List<String> args, String command) {
        if (args.size() > 1) throw new IllegalArgumentException("Usage: /" + command + " [name]");
        return HomeStore.name(args.isEmpty() ? "home" : args.get(0));
    }

    private void setHome(CodaCommandContext source, List<String> args) throws Exception {
        String name = optionalName(args, "sethome");
        store.set(source.worldDirectory(), source.playerId(), name, source.position());
        source.reply("Coda: Home '" + name + "' filed. /home " + name + " will bring you back.");
    }

    private void goHome(CodaCommandContext source, List<String> args) throws Exception {
        String name = optionalName(args, "home");
        CodaPosition home = store.homes(source.worldDirectory(), source.playerId()).get(name);
        if (home == null) throw new IllegalArgumentException("No home named '" + name + "'. Use /sethome " + name + " first.");
        if (!home.dimension().equals(source.position().dimension()))
            throw new IllegalArgumentException("That home is in another dimension. Return there before using /home.");
        CodaPosition departure = source.position();
        PlayerWorld key = playerWorld(source);
        source.teleport(home);
        returnPoints.put(key, departure);
        source.reply("Coda: Welcome home. Clipboard checked.");
    }

    private void goBack(CodaCommandContext source, List<String> args) throws Exception {
        if (!args.isEmpty()) throw new IllegalArgumentException("Usage: /back");
        PlayerWorld key = playerWorld(source);
        CodaPosition destination = returnPoints.get(key);
        if (destination == null)
            throw new IllegalArgumentException("No return point yet. Use /home first. Return points reset when Minecraft restarts.");
        CodaPosition departure = source.position();
        if (!destination.dimension().equals(departure.dimension()))
            throw new IllegalArgumentException("Your return point is in another dimension. Return there before using /back.");
        source.teleport(destination);
        returnPoints.put(key, departure);
        source.reply("Coda: Back where you left off. Return trip filed.");
    }

    private void listHomes(CodaCommandContext source, List<String> args) throws Exception {
        if (!args.isEmpty()) throw new IllegalArgumentException("Usage: /homes");
        Map<String, CodaPosition> homes = store.homes(source.worldDirectory(), source.playerId());
        source.reply(homes.isEmpty() ? "Coda: No homes filed in this world yet. Stand somewhere safe and use /sethome."
                : "Coda: Your homes in this world: " + String.join(", ", homes.keySet()));
    }

    private void deleteHome(CodaCommandContext source, List<String> args) throws Exception {
        if (args.size() != 1) throw new IllegalArgumentException("Usage: /delhome <name>");
        String name = HomeStore.name(args.get(0));
        if (!store.delete(source.worldDirectory(), source.playerId(), name))
            throw new IllegalArgumentException("No home named '" + name + "'.");
        source.reply("Coda: Home '" + name + "' removed from the ledger.");
    }
}
