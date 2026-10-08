# HW Mods

First-party CML mods for **Minecraft Java 26.4 Snapshot 3**.

Each mod lives in its own folder under `mods/` with its own version, JAR, tests, and release pipeline.

## Mods

- `mods/hw-quiet-underground/` — HW Quiet Underground 1.0.0 — optional Snapshot 3 world-generation data pack; rare mega caves and five-times-rarer ravines. Enable per world via Data Packs.
- `mods/hw-essentials/` — HW Essentials 0.2.0 — `/sethome`, `/home`, `/back`, `/homes`, `/delhome`, `/hwessentials`
- `mods/buildcraft-cml/` — BuildCraft CML 0.1.0-dev — **experimental source-only port foundation** (item transport engine and tests, no in-game blocks or launcher installation yet). See its README for attribution and roadmap.

## Building

```bash
# Requires HW-CodaLoader checked out alongside HW-Mods (for API sources)
export HW_CODALOADER_API_DIR=../HW-CodaLoader/src/main/java
./scripts/build-hw-essentials.sh
```

Output: `dist/hw-essentials-0.2.0.jar` + `.sha256`

## Testing

```bash
./scripts/test-hw-essentials.sh
./scripts/test-buildcraft-cml.sh
```

## Distribution

Existing released mod JARs are published to GitHub Releases. CodaLoader installs **released, bundled** mods into the active game profile (including CodaLauncher-managed profiles). BuildCraft CML is not published or bundled.

The shared command API (`CodaMod`, `CodaContext`, `CodaCommand`, `CodaCommandContext`, `CodaPosition`, `CodaCommands`) and Minecraft hooks remain in **HW-CodaLoader**.

Target: **Minecraft Java 26.4 Snapshot 3**.