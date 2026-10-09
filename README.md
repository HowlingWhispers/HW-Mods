# HW Mods

First-party CML mods for **Minecraft Java 26.4 Snapshot 3**.

Each mod lives in its own folder under `mods/` with its own version, JAR, tests, and release pipeline.

## Official content policy

There are **no optional first-party player mods**. Every officially released H.O.W.L. gameplay mod must be installed and verified automatically. If a required component is missing or damaged, repair it or report a blocked launch; do not quietly leave the player with an incomplete install.

Unreleased prototypes are **development-only**, not optional player mods. World-generation features must become part of the required content for **new worlds** after live tests; never silently rewrite existing saves.

## Mods

- `mods/hw-quiet-underground/` — HW Quiet Underground 1.0.0 — **development-only** Snapshot 3 world generation. A guarded pre-generation provisioner exists, but the Minecraft new-world hook remains to be implemented and tested.
- `mods/hw-essentials/` — HW Essentials 0.2.0 — `/sethome`, `/home`, `/back`, `/homes`, `/delhome`, `/hwessentials`
- `mods/buildcraft-cml/` — BuildCraft CML **0.0.1-dev.1** — **development-only** genuine BCCE 8.0.23 redstone engine MJ/heat/piston port, real Native Creative block registration and live pipe-route surveying. Native pipe BlockEntities, moving ItemStacks, wrench actions and machine parity remain to be implemented. The older JAR filename is kept temporarily for CodaLauncher compatibility.

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
python3 scripts/build-hw-quiet-underground.py
python3 tests/test-provision-quiet-underground.py
```

## Distribution

Official mod JAR releases are installed as **required, automatically managed** first-party content. BuildCraft CML and Quiet Underground remain development-only and are tested by CI but are not yet published in the player bundle.

The shared command API (`CodaMod`, `CodaContext`, `CodaCommand`, `CodaCommandContext`, `CodaPosition`, `CodaCommands`) and Minecraft hooks remain in **HW-CodaLoader**.

Target: **Minecraft Java 26.4 Snapshot 3**.