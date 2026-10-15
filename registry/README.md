# H.O.W.L. Mod Registry (server prototype)

Dedicated read-only mod catalog plus authenticated Nightly publication service for the existing CodaLauncher. The registry is **not deployed yet**. This project does not replace existing Nightly GitHub Releases or authorize manual installs.

## Flow

1. GitHub builds/tests the complete compatible H.O.W.L./mod combination and publishes an immutable versioned GitHub release with SHA-256.
2. The official HW-Mods workflow notifies `POST /v1/publish` using a server-scoped publisher token.
3. The server independently checks the public release, exact approved GitHub asset URL, maximum JAR size, SHA-256, `coda.mod.json`, entrypoint class, mod ID, version, Minecraft compatibility and declared dependencies before approval.
4. PostgreSQL records metadata and the server stores JAR bytes by SHA-256, never by an attacker-provided path.
5. `GET /v1/mods?channel=nightly` lists latest verified Nightly versions; `GET /v1/mods/{mod_id}` gives mod history, and `GET /v1/assets/{sha256}` serves immutable JAR bytes.

**Security:** Only `HowlingWhispers/HW-Mods` public release assets can be imported. This registry verifies provenance and binary identity; it does *not* prove gameplay safety or provide a malware audit. The launcher must still validate downloaded SHA-256 and JAR metadata before installing. Required Nightly dependencies are automatic on PLAY, optional mod installs require consent. Stable promotion is intentionally not exposed by the publisher API.

## Configuration and deployment for Kilo

Run Node.js 22+ behind a TLS reverse proxy on `mods.thehowlingwhispers.com` with a *separate* PostgreSQL database/role. Never expose port 8795 or the DB publicly. Provide `DATABASE_URL`, `HOWL_REGISTRY_PUBLISH_TOKEN`, `STORAGE_ROOT` (e.g. `/var/lib/howl-mod-registry`), `HOST=127.0.0.1`, and `PORT=8795` via systemd environment secret. Use a long random secret and rotate it if leaked. Install dependencies with `npm install`, apply `schema.sql` using psql, then run `npm start`. Restrict storage to the service user and back up DB and objects together. Rate-limit publication through the reverse proxy.

Configure the HW-Mods GitHub secret `HOWL_MOD_REGISTRY_PUBLISH_TOKEN` and repo variable `HOWL_MOD_REGISTRY_URL=https://mods.thehowlingwhispers.com` **only after** the service is healthy. If unset, GitHub continues its existing immutable release channel and logs that the registry isn't configured.

## Status and next integration

The CodaLauncher Mod Browser PR #11 already provides search, status filters and detail panels using the current GitHub discovery; switch its catalog provider to the registry only after the hosted API is verified. Keep the old GitHub-based Nightly updater until an end-to-end staging test proves registry delivery. Do not send prototype GUI builds through Stable. 
