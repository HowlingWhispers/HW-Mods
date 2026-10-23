package dev.howlingwhispers.frontier;

import dev.howlingwhispers.codaloader.api.*;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;

/** Frontier is an isolated launcher play mode. It only runs when CodaLauncher selected it. */
public final class FrontierMod implements CodaMod {
    private final FrontierSimulation sim = new FrontierSimulation();
    private final FrontierCommands commands = new FrontierCommands(sim);

    @Override
    public void onInitialize(CodaContext context) throws Exception {
        // Fail at startup, not mid-game, if the loader lacks what Frontier needs.
        CodaWorldView.class.getMethod("living", String.class);
        CodaCommandContext.class.getMethod("targetBlock");
        CodaLivingWorld.class.getMethod("rename", UUID.class, String.class);
        CodaLivingWorld.class.getMethod("place", UUID.class, CodaBlockPos.class, String.class, CodaBlockPos.class); // H.O.W.L. 0.0.46
        CodaLivingWorld.class.getMethod("shapePath", UUID.class, CodaBlockPos.class);
        Path marker = context.gameDirectory().resolve("config/howl-play-mode.txt");
        boolean enabled = Files.isRegularFile(marker) && !Files.isSymbolicLink(marker) && Files.size(marker) < 64
                && Files.readString(marker).trim().equals("frontier");
        context.registerCommand("frontier", "Frontier settlement, settlers and land", (player, args) -> {
            if (!enabled) {
                player.reply(Chat.refused("Choose Frontier in CodaLauncher Nightly before starting this world."));
                return;
            }
            player.withWorld(view -> {
                sim.open(view, null);
                try {
                    sim.requireReadable();
                    if (!player.position().dimension().equals("minecraft:overworld"))
                        throw new IllegalArgumentException("Frontier settlements belong in the Overworld");
                    var world = view.living("minecraft:overworld")
                            .orElseThrow(() -> new IllegalStateException("H.O.W.L. 0.0.46 single-player living-world support is required"));
                    commands.run(player, view, world, args);
                } catch (IllegalArgumentException refused) {
                    player.reply(Chat.refused(refused.getMessage()));
                }
            });
        });
        if (enabled) {
            context.registerServerTick("frontier", sim::tick);
            context.registerBlockPlacement(sim::placed);
        }
        System.out.println("[Frontier] " + version() + ", mode=" + (enabled ? "Frontier" : "inactive") + ", H.O.W.L. " + context.loaderVersion());
    }

    /** For tests: the simulation behind the registered command. */
    FrontierSimulation simulation() { return sim; }

    /** The version in coda.mod.json, so the log, JAR and release can never disagree. */
    static String version() {
        try (InputStream in = FrontierMod.class.getResourceAsStream("/coda.mod.json")) {
            if (in == null) return "unknown";
            var m = Pattern.compile("\"version\"\\s*:\\s*\"([0-9A-Za-z.+-]{1,32})\"").matcher(new String(in.readNBytes(8192), StandardCharsets.UTF_8));
            return m.find() ? m.group(1) : "unknown";
        } catch (java.io.IOException e) {
            return "unknown";
        }
    }
}
