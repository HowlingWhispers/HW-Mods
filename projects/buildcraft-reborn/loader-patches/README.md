# H.O.W.L. integration required for original pipes

The loader checkout is read-only under the current project-only development
scope. This directory contains a concrete proposed change, not a replacement
pipe implementation or an applied loader modification.

`0001-native-keyed-items.patch` targets the exact commit in `BASE.json`. It adds
`CodaContext.registerNativeKeyedItemFactory` so a mod can return its original
Minecraft Item using the loader's already registry-keyed Properties. Native
registration checks the resulting type and, for a block declaration, verifies
that the BlockItem points to the declared block. Existing owner, duplicate and
pre-freeze guards remain active. `git apply --check` passes against that commit.

BuildCraft's staged `ItemPipeHolder` now accepts those native Properties while
retaining its original definition, placement, colour, pick/drop and tooltip
behaviour. A generic Item cannot install a pipe because original holder
placement checks the original `IItemPipe` interface.

The first world test also requires native payload codec/handler registration,
block-entity renderer registration, a contextual terrain-model hook consuming
the immutable original PipeModelKey, block tint registration, and client
tick/join callbacks for the original item cache. Those hooks are not implemented
by this patch. Vanilla outgoing packets alone do not register incoming codecs.

After extending the loader scope, apply and test the item patch, implement the
remaining native hooks, bind original module initialization and item factories,
then run the real source chest → powered wooden pipe → stone pipes → destination
chest scenario. No Nightly until visible travel and item conservation pass.
