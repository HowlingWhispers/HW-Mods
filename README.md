# HW Mods

First-party CML mods for **Minecraft Java 26.4 Snapshot 3**.

Each mod lives in its own folder under `mods/` with its own version, JAR, tests, and release pipeline.

## Official content policy

There are **no optional first-party player mods**. Every officially released H.O.W.L. gameplay mod must be installed and verified automatically. If a required component is missing or damaged, repair it or report a blocked launch; do not quietly leave the player with an incomplete install.

Unreleased prototypes are **development-only**, not optional player mods. World-generation features must become part of the required content for **new worlds** after live tests; never silently rewrite existing saves.

## Mods

- `mods/hw-quiet-underground/` — HW Quiet Underground 1.0.0 — **development-only** Snapshot 3 world-generation prototype; rare mega caves and five-times-rarer ravines. Not included in player releases until automatic required-world integration is tested.
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