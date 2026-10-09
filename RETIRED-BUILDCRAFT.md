# BuildCraft prototype retired

The former `mods/buildcraft-cml/` prototype was removed deliberately after
it was found to be the old experimental implementation under a new version label.

**DO NOT restore, package, import, distribute, or resume these files as the
new BuildCraft port.** The removed custom `PipeNetwork`, `BuildCraftGlassPipeDemo`,
`BuildCraftRedstoneEngine`, simplified block registrations, stand-in engine
model, and placeholder resource-pack workflow are not the BCCE gameplay port.

The source remains retrievable in immutable Git history, which is normal
for a source-control deletion; **the active `main` tree no longer contains it**.
Old release assets such as `nightly-buildcraft-20261009-02f3e79f5d` may still
exist on GitHub. They are **retired, unsafe as a source of future updates**,
and must never be promoted or reinstalled by the launcher.

A future port must use an entirely separate new directory or repository
populated from **upstream BuildCraft Community Edition 8.0.23** at
`BCCE-team/BuildCraft`, commit
`23c6af379676ce5262c5c0cb6f1f331edc9b12c6`, with original pipe
holders, item flow, energy/engine mechanics and rendering retained rather than
reimplemented using the deleted prototype.

Do not load the old test world after removing its BuildCraft mod. Modded
blocks may be replaced or disappear in a save opened without their mod.
Keep a copy of that world strictly for reference.
