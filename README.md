# HW Mods

First-party CML mods for **Minecraft Java 26.4 Snapshot 3**.

Each mod lives in its own folder under `mods/` with its own version, JAR, tests, and release pipeline.

## Mods

- `mods/hw-essentials/` — HW Essentials 0.1.0 — `/sethome`, `/home`, `/homes`, `/delhome`, `/hwessentials`

## Building

```bash
# Requires HW-CodaLoader checked out alongside HW-Mods (for API sources)
export HW_CODALOADER_API_DIR=../HW-CodaLoader/src/main/java
./scripts/build-hw-essentials.sh
```

Output: `dist/hw-essentials-0.1.0.jar` + `.sha256`

## Testing

```bash
./scripts/test-hw-essentials.sh
```

## Distribution

Mod JARs are published to GitHub Releases. CodaLoader installs them automatically into the active game profile (including CodaLauncher-managed profiles).

The shared command API (`CodaMod`, `CodaContext`, `CodaCommand`, `CodaCommandContext`, `CodaPosition`, `CodaCommands`) and Minecraft hooks remain in **HW-CodaLoader**.

Target: **Minecraft Java 26.4 Snapshot 3**.