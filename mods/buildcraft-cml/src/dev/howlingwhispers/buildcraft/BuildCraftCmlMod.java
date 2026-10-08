package dev.howlingwhispers.buildcraft;

import dev.howlingwhispers.codaloader.api.CodaContext;
import dev.howlingwhispers.codaloader.api.CodaMod;

/** Unofficial CodaLoader port foundation for BuildCraft-style industry. */
public final class BuildCraftCmlMod implements CodaMod {
    @Override
    public void onInitialize(CodaContext context) {
        context.registerCommand("buildcraft", "BuildCraft CML port status", (source, args) -> {
            if (!args.isEmpty()) throw new IllegalArgumentException("Usage: /buildcraft");
            source.reply("BuildCraft CML 0.1.0-dev: transport simulation ready; blocks, textures and gameplay not installed yet.");
        });
        System.out.println("[BuildCraft CML] Transport core loaded. Game block registry integration pending.");
    }
}
