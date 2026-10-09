# BuildCraft Reborn for H.O.W.L.

Port the original **BCCE 8.0.23** to **Minecraft 26.4 Snapshot 3** on
**H.O.W.L.** The exact upstream commit is pinned in `UPSTREAM.lock.json`.
NeoForge is a source dependency being migrated away from, not the target loader.

**Not playable. The complete original pipe build still fails. No Nightly or
installable JAR is produced.**

## Source and build

Work belongs only in `projects/buildcraft-reborn/` on `main`. Keep the retired
prototype deleted. The vendor files remain byte-identical to upstream;
`stage-original-pipe-holders.py` applies audited, reversible API changes to
BCCE's own layered source. It clears old staged files before each run.
The actual `BlockPipeHolder`, `TilePipeHolder`, `PipeFlowItems`, `PipeBehaviourWood`,
travelling items, engines, models and textures remain the implementations to port.

With Java 25 and the current H.O.W.L. SDK checkout alongside HW-Mods:

```bash
bash projects/buildcraft-reborn/scripts/prepare-source.sh
bash projects/buildcraft-reborn/scripts/compile-original-pipes.sh
```

`HW_CODALOADER_API_DIR` can point to the SDK's `src/main/java` directory.
The compiler includes both original holders, their entire dependency closure,
and the H.O.W.L. entrypoint. Mojang metadata, client and libraries are verified
against their published hashes. There is no NeoForge JAR on the classpath.
A failed compilation returns a nonzero exit code. The existing CI probe now
uses the same strict build gate; diagnostic collection cannot make CI green.
Full errors are written to `dist/buildcraft-reborn/original-pipes-javac-errors.txt`.

## Implemented compatibility changes

- Native keyed holder constructors, the original block sound IDs and native ticker.
- Snapshot 3 particle, pick-stack, player-destroy, explosion and chunk-coordinate signatures.
- Native first-tick dispatch to the original `onLoad()` before `update()`;
  the original pipe still calls its own `pipe.onTick()`.
- Original handler signatures use BCCE's existing `ItemStorage` and
  `MutableItemStorage` contracts. Original simulation semantics retain the
  `EXECUTE`/`SIMULATE` values. No new inventory discovery or transport network.
- Existing stateless capability reads retain original providers and sided lookups;
  original topology notifications remain after pipe installation and plug changes.

## Validation and remaining work

```bash
bash projects/buildcraft-reborn/scripts/test-original-persistence.sh
```

The original `BCBlockEntity`, ValueIO helpers and `ChunkUtil` compile against
Snapshot 3. Native Mojang registry bootstrap and save/load/save tests pass for
the original flat pipe-holder layout and nested `bc_legacy` machine layout.
This tests the persistence superclass, not pipe placement, extraction or cargo.

The full build remains blocked by the original dependency closure: fluid
carriers/handlers used by `Tank` and `TankManager`, native transaction and
capability seams, networking, module registration and client rendering.
Native chunk-unload dispatch and cancellable player breaking also remain
unwired; inherited compatibility method declarations are not evidence that
Minecraft calls those hooks. Do not package until those are connected.

The acceptance test remains real BuildCraft pipes between two vanilla chests,
original powered extraction, visible travelling stacks, and no duplication or
loss. Compile the original mechanics and run that test before publishing.
