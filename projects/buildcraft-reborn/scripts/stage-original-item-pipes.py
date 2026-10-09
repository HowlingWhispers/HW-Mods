#!/usr/bin/env python3
"""Select original BCCE item-pipe features after applying native API changes.

The first world target is wooden extraction, stone transport and a redstone
engine. Diamond item sorting is retained for the next world scenario. Fluid
pipes, power pipes, FE conversion and gate actions are outside this build.
No substitute routing, extraction, engine or travelling-item implementation.
"""
from pathlib import Path
import hashlib
import json
import re
import runpy
import sys

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
BASE = ROOT / "dist/buildcraft-reborn"
SOURCE = BASE / "effective-1.21.11-neoforge/src/main/java/buildcraft"
DEST = BASE / "staged-snapshot3/src/main/java/buildcraft"
WITH_SORTING = '--with-diamond' in sys.argv


def code_mask(text):
    """Preserve positions while excluding comments and Java string literals."""
    pattern = r'//[^\n]*|/\*[\s\S]*?\*/|"""[\s\S]*?"""|"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\''
    return re.sub(pattern, lambda m: re.sub(r'[^\n]', ' ', m.group()), text)


def span(text, anchor):
    assert text.count(anchor) == 1, (anchor, text.count(anchor))
    start = text.index(anchor)
    masked = code_mask(text)
    opening = masked.index('{', start)
    depth = 1
    end = opening + 1
    while depth:
        depth += (masked[end] == '{') - (masked[end] == '}')
        end += 1
    return start, end


def body(text, anchor):
    start, end = span(text, anchor)
    opening = code_mask(text).index('{', start)
    return text[opening + 1:end - 1]


def replace_body(text, anchor, replacement):
    start, end = span(text, anchor)
    opening = code_mask(text).index('{', start)
    return text[:opening + 1] + replacement + text[end - 1:]


def remove_method(text, anchor):
    start, end = span(text, anchor)
    # Include annotations immediately preceding the declaration.
    start = text.rfind('\n', 0, start) + 1
    while True:
        previous = text.rfind('\n', 0, start - 1) + 1
        if not text[previous:start].strip().startswith('@'):
            break
        start = previous
    return text[:start] + text[end:]


def remove_member(text, anchor):
    """Remove a braced class/branch, preserving all surrounding source."""
    start, end = span(text, anchor)
    return text[:start] + text[end:]


def exact(text, before, after='', count=1):
    assert text.count(before) == count, (before, count, text.count(before))
    return text.replace(before, after, count)


def unused_imports(text):
    imports = list(re.finditer(r'^import ([\w.*]+);\n', text, re.M))
    masked = code_mask(text)
    for match in reversed(imports):
        masked = masked[:match.start()] + ' ' * len(match.group()) + masked[match.end():]
    for match in reversed(imports):
        name = match.group(1).rsplit('.', 1)[-1]
        if name != '*' and not re.search(r'\b' + re.escape(name) + r'\b', masked):
            text = text[:match.start()] + text[match.end():]
    return text


def main():
    runpy.run_path(str(HERE / 'stage-original-pipe-holders.py'), run_name='__main__')
    report = {'scope': ['wood_item', 'stone_item', 'engine_redstone'] + (['diamond_item'] if WITH_SORTING else []),
              'status': 'STAGED_NOT_COMPILED', 'files': []}

    def stage(relative, transform):
        path = DEST / relative
        before = (path if path.exists() else SOURCE / relative).read_text()
        after = unused_imports(transform(before))
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(after)
        report['files'].append({'source': relative,
                               'before_sha256': hashlib.sha256(before.encode()).hexdigest(),
                               'after_sha256': hashlib.sha256(after.encode()).hexdigest()})

    def registries(text):
        pre = body(text, 'public static void preInit()')
        retained = [line for line in pre.splitlines(True)
                    if 'PipeApi.pipeRegistry =' in line or 'PipeApi.flowItems =' in line]
        assert len(retained) == 2
        text = replace_body(text, 'public static void preInit()', '\n' + ''.join(retained) + '    ')
        init = body(text, 'public static void init()')
        start = init.index('        PipeConnectionAPI.registerConnection(')
        end = init.index(';', start) + 1
        text = replace_body(text, 'public static void init()', '\n' + init[start:end] + '\n    ')
        return remove_method(text, 'private static void registerStripes(')

    stage('transport/BCTransportRegistries.java', registries)

    def definitions(text):
        selected = {'woodItem', 'stoneItem'} | ({'diamondItem'} if WITH_SORTING else set())
        text = re.sub(r'^    public static PipeDefinition (\w+);\n',
                      lambda m: m.group() if m.group(1) in selected else '', text, flags=re.M)
        original = body(text, 'public static void preInit()')
        wood = original[original.index('        builder.logic(PipeBehaviourWood::new'):
                        original.index('        woodFluid =')]
        stone = original[original.index('        builder.logic(PipeBehaviourStone::new'):
                         original.index('        stoneFluid =')]
        diamond = original[original.index('        String[] diamondTextureSuffixes ='):
                           original.index('        builder.logic(PipeBehaviourDiamondFluid::new')]
        retained = '\n        DefinitionBuilder builder = new DefinitionBuilder();\n' \
                   '        builder.builder.enableColouring();\n\n' + wood + '\n' + stone + '\n' + diamond + '    '
        if not WITH_SORTING:
            retained = exact(retained, diamond)
        text = replace_body(text, 'public static void preInit()', retained)
        text = remove_method(text, 'private static void registerLegacyFeAliases()')
        for name in ('flowFluid', 'flowPower', 'flowForgeEnergy'):
            text = remove_method(text, 'public DefinitionBuilder ' + name + '()')
        assert re.findall(r'\b(\w+) = builder\.idTex(?:Prefix)?\(', retained) == \
            ['woodItem', 'stoneItem'] + (['diamondItem'] if WITH_SORTING else [])
        return text

    stage('transport/BCTransportPipes.java', definitions)

    def items(text):
        start = text.index('    public static Item waterproof;')
        end = text.index('    static {', start)
        declarations = text[start:end]
        selected = []
        for line in declarations.splitlines(True):
            if any(token in line for token in ('BCDeferredRegister<Item> ITEMS =',
                                               'LinkedHashMap<PipeDefinition, BCRegistryEntry<ItemPipeHolder>> PIPE_MAP =',
                                               'BCRegistryEntry<ItemPipeHolder> PIPE_ITEM_WOOD;',
                                               'BCRegistryEntry<ItemPipeHolder> PIPE_ITEM_STONE;',
                                               'BCRegistryEntry<ItemPipeHolder> PIPE_ITEM_DIAMOND;')):
                selected.append(line)
        assert len(selected) == 5
        if not WITH_SORTING:
            selected = [line for line in selected if 'PIPE_ITEM_DIAMOND;' not in line]
        text = text[:start] + ''.join(selected) + \
            '\n    // Wire items are outside this build; holder pick/drop paths retain the empty catalog.\n' \
            '    public static final EnumMap<DyeColor, Item> wires = new EnumMap<>(DyeColor.class);\n\n' + text[end:]
        static_start, static_end = span(text, '    static {')
        static_body = body(text, '    static {')
        selected = [line for line in static_body.splitlines(True)
                    if re.search(r'PIPE_ITEM_(WOOD|STONE|DIAMOND) = makePipeItem\(', line)]
        assert len(selected) == 3
        if not WITH_SORTING:
            selected = [line for line in selected if 'PIPE_ITEM_DIAMOND =' not in line]
        text = text[:static_start] + '    static {\n' + ''.join(selected) + '    }' + text[static_end:]
        text = exact(text, '        items.add(WATER_PROOF.get().getDefaultInstance());\n')
        text = remove_method(text, 'public static List<ItemStack> getPlugTabItems()')
        return exact(text, 'BCTransport.MODID', '"buildcrafttransport"')

    stage('transport/BCTransportItems.java', items)

    def config(text):
        # Keep the original item energy cost and its range guard. No fluid/FE
        # config handlers or references to unselected pipe definitions.
        start = text.index('    private static final long MJ_REQ_MILLIBUCKET_MIN')
        end = text.index('    public static void preInit()', start)
        fields = text[start:end]
        selected = [line for line in fields.splitlines(True)
                    if any(token in line for token in ('MJ_REQ_ITEM_MIN =', 'long mjPerItem =', 'IntValue propMjPerItem;'))]
        assert len(selected) == 3
        text = text[:start] + ''.join(selected) + '\n' + text[end:]
        pre = body(text, 'public static void preInit()')
        setting_start = pre.index('        propMjPerItem =')
        setting_end = pre.index(';', setting_start) + 1
        replacement = '\n        BCConfigSpec.Builder builder = new BCConfigSpec.Builder();\n' \
                      '        builder.push("general");\n' + pre[setting_start:setting_end] + \
                      '\n        builder.pop();\n        config = builder.build();\n    '
        text = replace_body(text, 'public static void preInit()', replacement)
        reload = body(text, 'public static void reloadConfig()')
        item = [line for line in reload.splitlines(True) if 'mjPerItem = Math.max(' in line]
        assert len(item) == 1
        text = replace_body(text, 'public static void reloadConfig()', '\n' + item[0] + '    ')
        for method in ('private static void fluidTransfer(', 'private static void powerTransfer(',
                       'private static void forgeEnergyTransfer('):
            text = remove_method(text, method)
        return exact(text, 'BCTransport.MODID', '"buildcrafttransport"', 2)

    stage('transport/BCTransportConfig.java', config)

    stage('transport/item/ItemPipeHolder.java',
          lambda text: exact(text, 'BCTransportBlocks.pipeHolder.get()',
                             '(net.minecraft.world.level.block.Block) '
                             'net.minecraft.core.registries.BuiltInRegistries.BLOCK'
                             '.getValue(net.minecraft.resources.Identifier.parse("hw_buildcraft_reborn:pipe_holder"))'))

    def menus(text):
        # The original diamond container remains; other transport screens do
        # not belong to the first item-pipe build.
        return re.sub(r'^    public static final BCRegistryEntry<MenuType<[^\n]+\n',
                      lambda m: m.group() if 'MENU_PIPE_DIAMOND =' in m.group() else '', text, flags=re.M)

    stage('transport/BCTransportGuis.java', menus)

    def chest_utility(text):
        # Mining, fluid draining and block-placement utilities are unrelated to
        # transport. Keep BCCE's actual double-chest ordering implementation.
        methods = []
        for anchor in ('public static @Nullable ChestBlockEntity getOtherDoubleChest(',
                       'public static @Nullable Container getCombinedDoubleChestContainer('):
            start, end = span(text, anchor)
            methods.append(text[start:end])
        start = text.index('public final class BlockUtil {')
        return text[:start] + 'public final class BlockUtil {\n\n    ' + \
            '\n\n    '.join(methods) + '\n}\n'

    stage('lib/misc/BlockUtil.java', chest_utility)

    def item_storage_adapters(text):
        for anchor in ('public static EnergyStorage fromNativeEnergy(',
                       'public static IEnergyStorage toNativeEnergy(',
                       'public static FluidStorage<FluidStack> fromNativeFluids(',
                       'public static IFluidHandler toNativeFluids(',
                       'private static FluidAction action('):
            text = remove_method(text, anchor)
        for anchor in ('private static final class Energy ', 'private static final class NativeEnergy ',
                       'private static class Fluids ', 'private static final class FilteredFluids ',
                       'private static class NativeFluids ', 'private static final class NativeFilteredFluids '):
            text = remove_member(text, anchor)
        return text

    stage('lib/platform/storage/StorageAdapters.java', item_storage_adapters)

    def caps(text):
        for anchor in ('public void addFluidStorage(', 'public void addEnergyStorage('):
            # Each has a storage and a supplier overload.
            assert text.count(anchor) == 2
            for _ in range(2):
                start = text.index(anchor)
                tail = text[start:]
                next_start = tail.find(anchor, len(anchor))
                part = tail if next_start < 0 else tail[:next_start]
                _, end = span(part, anchor)
                text = text[:start] + text[start + end:]
        text = remove_method(text, 'private static net.neoforged.neoforge.fluids.capability.IFluidHandler nativeFluids(')
        for anchor in ('if (capability == CapUtil.CAP_FLUIDS)', 'if (capability == CapUtil.CAP_FE)'):
            text = remove_member(text, anchor)
        return text

    stage('lib/cap/CapabilityHelper.java', caps)

    def platform_storage(text):
        for anchor in ('public static ItemStorage localInventory(',
                       'public static FluidStorage<FluidStack> fluids(',
                       'public static EnergyStorage energy(Level ',
                       'public static EnergyStorage energy(ItemStack ',
                       'public static ItemStorage items(Entity '):
            text = remove_method(text, anchor)
        return text

    stage('lib/platform/storage/PlatformStorage.java', platform_storage)

    def cap_util(text):
        for anchor in ('public static IFluidHandler getFluidHandler(',
                       'public static IEnergyStorage getEnergyStorage(',
                       'public static IItemHandler getItemHandler(@Nullable Entity '):
            text = remove_method(text, anchor)
        # Remove unused capability tokens, with their original annotations.
        for declaration in ('    @Nonnull\n    public static final BlockCapability<IFluidHandler, Direction> CAP_FLUIDS = BlockCapability.createSided(id("fluids"), IFluidHandler.class);',
                            '    @Nonnull\n    public static final EntityCapability<IItemTransactor, Direction> CAP_ITEM_TRANSACTOR_ENTITY =\n        EntityCapability.createSided(id("item_transactor"), IItemTransactor.class);',
                            '    @Nonnull\n    public static final BlockCapability<IEnergyStorage, Direction> CAP_FE = BlockCapability.createSided(id("fe"), IEnergyStorage.class);'):
            text = exact(text, declaration)
        anchor = 'public static IItemHandler getItemHandler(@Nullable Level '
        return_start = body(text, anchor).index('        ResourceHandler<ItemResource> modern =')
        previous = body(text, anchor)[:return_start]
        text = replace_body(text, anchor, previous +
                            '        return buildcraft.lib.compat.howl.OriginalCapabilityLookup.get(level, pos, side, CAP_ITEMS);\n    ')
        # Only the block/sided capability API belongs to this build.
        entity_anchor = 'public static <T, C> T getCapability(\n        @Nullable Entity entity,'
        text = remove_method(text, entity_anchor)
        for line in ('            if (capability == CAP_FLUIDS) return (T) getFluidHandler(level, pos, side);\n',
                     '            if (capability == CAP_FE) return (T) getEnergyStorage(level, pos, side);\n'):
            text = exact(text, line)
        return exact(text, 'return level.getCapability(capability, pos, context);',
                     'return buildcraft.lib.compat.howl.OriginalCapabilityLookup.get(level, pos, (Direction) context, capability);')

    stage('lib/misc/CapUtil.java', cap_util)

    def transactors(text):
        text = remove_method(text, 'public static IItemTransactor getTransactorForEntity(')
        text = exact(text, 'level.getCapability(CapUtil.CAP_ITEM_TRANSACTOR, pos, face)',
                     'buildcraft.lib.compat.howl.OriginalCapabilityLookup.get(level, pos, face, CapUtil.CAP_ITEM_TRANSACTOR)')
        return exact(text, 'level.getCapability(PipeApi.CAP_INJECTABLE, pos, face)',
                     'buildcraft.lib.compat.howl.OriginalCapabilityLookup.get(level, pos, face, PipeApi.CAP_INJECTABLE)')

    stage('lib/inventory/ItemTransactorHelper.java', transactors)

    def wood(text):
        item_before = body(text, 'protected int extractItems(')
        # Keep the item branch exactly; omit only its unused fluid alternative.
        start, end = span(text, 'else if (pipe.getFlow() instanceof IFlowFluid)')
        text = text[:start] + text[end:]
        text = remove_method(text, 'protected FluidStack extractFluid(')
        text = remove_method(text, 'public void fluidSideCheck(')
        assert body(text, 'protected int extractItems(') == item_before
        assert 'int maxItems = (int) (power / BCTransportConfig.mjPerItem);' in text
        assert 'return power - extracted * BCTransportConfig.mjPerItem;' in text
        return text

    stage('transport/pipe/behaviour/PipeBehaviourWood.java', wood)

    def directional(text):
        text = remove_method(text, 'public void addActions(')
        text = remove_method(text, 'public void onActionActivate(')
        return exact(text, 'if(Thread.currentThread().getThreadGroup() == SidedThreadGroups.SERVER)',
                     'if (!pipe.getHolder().getPipeWorld().isClientSide())')

    stage('transport/pipe/behaviour/PipeBehaviourDirectional.java', directional)
    stage('transport/pipe/flow/PipeFlowItems.java',
          lambda text: remove_method(text, 'public static void addTriggers('))

    def mj(text):
        text = exact(text, '    @Nullable\n    private final IEnergyStorage feReceiver;')
        text = exact(text, '        this.feReceiver = mj instanceof IMjReceiver value ? new MjReceiverEnergyStorage(value) : null;')
        start, end = span(text, 'if (capability == CapUtil.CAP_FE &&')
        return text[:start] + text[end:]

    stage('lib/internal/mj/MjCapabilityHelper.java', mj)

    def tile_base(text):
        for line in ('    public final TankManager tankManager = new TankManager();\n',
                     '        tankManager.addDrops(toDrop);\n',
                     '        input.findCompound("tanks").ifPresent(tag -> tankManager.deserializeNBT(input.registries(), tag));\n',
                     '        CompoundTag tanks = tankManager.serializeNBT(output.registries());\n',
                     '        if (!tanks.isEmpty()) output.put("tanks", tanks);\n'):
            text = exact(text, line)
        return exact(text, 'return tankManager.onActivated(player, worldPosition, hand);',
                     'return InteractionResult.PASS; // No tanks in the selected item-pipe/redstone-engine scope.')

    stage('lib/tile/TileBC_Neptune.java', tile_base)

    stage('lib/tile/TileBC_Neptune.java',
          lambda text: remove_method(text, 'public IDetachedRenderer getDebugRenderer()'))
    stage('lib/debug/IAdvDebugTarget.java',
          lambda text: exact(text, '    IDetachedRenderer getDebugRenderer();\n'))

    def item_tooltip(text):
        start, end = span(text, 'if (definition.flowType == PipeApi.flowFluids)')
        text = text[:start] + text[end:]
        for anchor in ('else if (definition.flowType == PipeApi.flowPower)',
                       'else if (definition.flowType == PipeApi.flowForgeEnergy)'):
            text = remove_member(text, anchor)
        return text

    stage('transport/item/ItemPipeHolder.java', item_tooltip)

    def item_codecs(text):
        start = text.index('    private static final ItemAbility AXE_DIG')
        end = text.index('    /** Serialize an ItemStack ', start)
        return text[:start] + '    private ItemCompat() {}\n\n' + text[end:]

    stage('lib/compat/ItemCompat.java', item_codecs)
    stage('lib/net/cache/BuildCraftObjectCaches.java',
          lambda text: exact(exact(text,
                                  '    public static final NetworkedFluidStackCache CACHE_FLUIDS = new NetworkedFluidStackCache();\n'),
                             '        registerCache(CACHE_FLUIDS);\n'))

    def flow_without_interop_transaction(text):
        # The NeoForge transaction bridge is not exposed in this native build.
        # Keep BCCE's real SIMULATE/EXECUTE branches and transfer results; only
        # its optional outer-journal participation is omitted.
        start = text.index('    private final buildcraft.lib.compat.transfer.TransferJournal<ItemTransferState>')
        end = text.index('    private ItemTransportProfile requireItemProfile()', start)
        text = text[:start] + text[end:]
        text = exact(text, '        if (pendingItems != null) transferJournal.record();\n')
        text = exact(text, '        transferJournal.record();\n')
        return exact(text, '        if (buildcraft.lib.compat.transfer.TransferJournal.defer(() -> sendItemDataToClient(item))) return;\n')

    stage('transport/pipe/flow/PipeFlowItems.java', flow_without_interop_transaction)

    def handler_without_interop_transaction(text):
        start = text.index('    private final buildcraft.lib.compat.transfer.TransferJournal<TransferState>')
        end = text.index('    public ItemHandlerSimple(int size)', start)
        text = text[:start] + text[end:]
        text = exact(text, ' && !buildcraft.lib.compat.transfer.TransferJournal.active()', '', 5)
        return exact(text, '        transferJournal.record();\n', '', 2)

    stage('lib/tile/item/ItemHandlerSimple.java', handler_without_interop_transaction)
    stage('lib/tile/TileBC_Neptune.java',
          lambda text: exact(text, '        if (buildcraft.lib.compat.transfer.TransferJournal.defer(this::markChunkDirty)) return;\n'))

    def transactors_without_interop_transaction(text):
        text = exact(text, ' && !buildcraft.lib.compat.transfer.TransferJournal.active()', '', 2)
        return exact(text, '        if (buildcraft.lib.compat.transfer.TransferJournal.active()) return NoSpaceTransactor.INSTANCE;\n')

    stage('lib/inventory/ItemTransactorHelper.java', transactors_without_interop_transaction)

    def legacy_api(text):
        text = replace_body(text, 'public static String getVersion()',
                            '\n        return buildcraft.lib.net.BuildCraftTarget.MOD_VERSION;\n    ')
        return replace_body(text, 'public static Identifier nameToResourceLocation(',
                            '\n        if (name.indexOf(\':\') > 0) return Identifier.parse(name);\n'
                            '        return Identifier.fromNamespaceAndPath('
                            'buildcraft.lib.compat.howl.ActiveModNamespace.get(), name);\n    ')

    stage('lib/internal/core/BuildCraftAPI.java', legacy_api)

    def base_without_container_slots(text):
        # Item pipes own travelling stacks in PipeFlowItems; redstone engines
        # have no fuel/storage slots. Their shared machine inventory is empty.
        for line in ('    protected final ItemHandlerManager itemManager = new ItemHandlerManager(this::onSlotChange);\n',
                     '        caps.addProvider(itemManager);\n',
                     '        caps.addItemStorage(itemManager::getItemStorage, EnumPipePart.VALUES);\n',
                     '        itemManager.addDrops(toDrop);\n',
                     '        input.findCompound("items").ifPresent(tag -> itemManager.deserializeNBT(input.registries(), tag));\n',
                     '        CompoundTag items = itemManager.serializeNBT(output.registries());\n',
                     '        if (!items.isEmpty()) output.put("items", items);\n'):
            text = exact(text, line)
        return remove_method(text, 'protected void onSlotChange(')

    stage('lib/tile/TileBC_Neptune.java', base_without_container_slots)

    def fallback_profile(text):
        # Ownership attribution is retained. This build does not invoke robot,
        # builder or mining automation and needs no fake-player implementation.
        start = text.index('    public static final GameProfile NULL_PROFILE =')
        return text[:start] + \
            '    public static final GameProfile NULL_PROFILE = new GameProfile(\n' \
            '        java.util.UUID.nameUUIDFromBytes("buildcraft.core".getBytes(java.nio.charset.StandardCharsets.UTF_8)), "[BuildCraft]");\n}\n'

    stage('lib/misc/FakePlayerProvider.java', fallback_profile)
    stage('transport/internal/pluggable/PluggableModelKey.java',
          lambda text: text.replace('RenderCompat.cutout()', 'RenderTypes.cutoutMovingBlock()')
                           .replace('RenderCompat.translucent()', 'RenderTypes.translucentMovingBlock()'))

    def cache_without_fluid_debug(text):
        for variable in ('copy', 'read'):
            start, end = span(text, 'if (' + variable + ' instanceof FluidStack)')
            tail = text[end:]
            assert re.match(r'\s*else\s*\{', tail)
            else_start = end + re.match(r'\s*else\s*\{', tail).start()  # replace the whole if/else
            opening = code_mask(text).index('{', else_start)
            ending = opening + 1
            depth = 1
            masked = code_mask(text)
            while depth:
                depth += (masked[ending] == '{') - (masked[ending] == '}')
                ending += 1
            text = text[:start] + text[opening + 1:ending - 1] + text[ending:]
        return text

    stage('lib/net/cache/NetworkedObjectCache.java', cache_without_fluid_debug)

    def stack_utility(text):
        for anchor in ('public static boolean contains(@Nonnull IngredientStack ingredientStack, @Nonnull ItemStack stack)',
                       'public static boolean contains(@Nonnull IngredientStack ingredientStack, @Nonnull NonNullList<ItemStack> stacks)'):
            text = remove_method(text, anchor)
        return exact(text, 'base.getTags().anyMatch(base::is)', 'base.typeHolder().tags().anyMatch(base::is)')

    stage('lib/misc/StackUtil.java', stack_utility)
    stage('lib/inventory/filter/StackFilter.java',
          lambda text: exact(text, '    },\n    FUEL {\n        public boolean matches(@Nonnull ItemStack stack) {\n            return ItemCompat.getBurnTime(stack) > 0;\n        }\n    };',
                             '    };'))
    stage('transport/item/ItemPipeHolder.java',
          lambda text: exact(text, 'I18n.exists(tipName)', 'net.minecraft.locale.Language.getInstance().has(tipName)'))
    stage('lib/engine/TileEngineBase_BC8.java',
          lambda text: exact(text, 'level.invalidateCapabilities(worldPosition);',
                             '// Native BCCE capability reads are uncached; original orientation update follows.'))
    stage('lib/misc/InventoryUtil.java',
          lambda text: exact(text, 'player.drop(stack, false, false);',
                             'player.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY);'))
    stage('transport/wire/WireSystem.java',
          lambda text: exact(text, 'new ChunkPos(element.blockPos)', 'ChunkPos.containing(element.blockPos)', 2))
    stage('transport/wire/WorldSavedDataWireSystems.java',
          lambda text: exact(text, 'new SavedDataType<>(DATA_NAME,',
                             'new SavedDataType<>(net.minecraft.resources.Identifier.withDefaultNamespace(DATA_NAME),'))
    stage('lib/misc/CapUtil.java',
          lambda text: exact(text, 'return buildcraft.lib.compat.howl.OriginalCapabilityLookup.get(level, pos, (Direction) context, capability);',
                             'if (context != null && !(context instanceof Direction)) return null;\n'
                             '        return buildcraft.lib.compat.howl.OriginalCapabilityLookup.get(level, pos, (Direction) context, '
                             '(BlockCapability<T, Direction>) (BlockCapability<?, ?>) capability);'))
    stage('lib/compat/minecraft/persistence/BCBlockEntity.java',
          lambda text: exact(text, '    private boolean nativeLoaded;',
                             '    @Override\n    public void setLevel(net.minecraft.world.level.Level level) {\n'
                             '        super.setLevel(level);\n'
                             '        buildcraft.lib.compat.howl.NativeLifecycleAccess.bind(level);\n'
                             '    }\n\n    private boolean nativeLoaded;'))

    def holder_scope(text):
        # Silicon lenses are an optional gate feature, not diamond-pipe filters.
        for anchor in ('if (pipe.flow instanceof IFlowItems && BCModules.SILICON.isLoaded())',):
            assert text.count(anchor) == 3
            for _ in range(3):
                start = text.index(anchor)
                # span() is deliberately unique-anchor-only; select each full
                # exact block and retain the original item handler registrations.
                tail = text[start:]
                first_next = tail.find(anchor, len(anchor))
                part = tail if first_next < 0 else tail[:first_next]
                _, end = span(part, anchor)
                text = text[:start] + text[start + end:]
        for anchor in ('if (capability == CapUtil.CAP_FLUIDS)',
                       'if (capability == CapUtil.CAP_FE)'):
            start, end = span(text, anchor)
            text = text[:start] + text[end:]
        start = text.index('            if (neighbour != null) {', text.index('public <T> T getCapabilityFromPipe('))
        block = text[start:text.index('            return buildcraft.lib.compat.howl.OriginalCapabilityLookup.get(', start)]
        assert 'CompatCapTransfromer.INSTANCE' in block
        return exact(text, block)

    stage('transport/tile/TilePipeHolder.java', holder_scope)

    def block_scope(text):
        # Ordinary pipe painting is retained. Facades belong to Silicon and
        # cannot be installed by this item-only catalog.
        text = exact(text, '\t\tDirection facadeSide = getFacadeSideAt(tile, pos, hitPos);\n')
        text = remove_member(text, 'if (facadeSide != null)')
        return remove_method(text, 'private static Direction getFacadeSideAt(')

    stage('transport/block/BlockPipeHolder.java', block_scope)

    def pipe_scope(text):
        # PipeView's existing defaults expose absent fluid/power/FE ports.
        # Wooden behaviour's MJ receiver and the redstone engine remain active.
        for anchor in ('public Optional<FluidPort> fluidPort(',
                       'public Optional<MjPort> mjPort(',
                       'public Optional<ExternalEnergyPort> externalEnergyPort(',
                       'public List<Direction> applyFluidRouting(',
                       'private static buildcraft.lib.compat.howl.FluidAction fluidAction('):
            text = remove_method(text, anchor)
        return text

    stage('transport/pipe/Pipe.java', pipe_scope)
    stage('lib/internal/module/BCModules.java',
          lambda text: exact(text, 'module.loaded = ModList.get().isLoaded(module.modId);',
                             'module.loaded = module == LIB || module == CORE || module == TRANSPORT;'))

    def model_snapshot(text):
        # Keep the original immutable pipe key as the snapshot boundary.
        # Native terrain baking belongs on the client side of that boundary;
        # server-side holders must not load facade models or client renderers.
        text = exact(text, 'import buildcraft.transport.client.model.ModelPipeNative121111;',
                     'import buildcraft.transport.client.model.key.PipeModelKey;\n'
                     'import buildcraft.transport.pipe.Pipe;\n'
                     'import buildcraft.lib.compat.howl.ModelProperty;')
        text = exact(text, 'public class TilePipeHolderModelData {',
                     'public class TilePipeHolderModelData {\n'
                     '    public static final ModelProperty<PipeModelKey> MODEL_DATA = new ModelProperty<>();')
        text = exact(text, 'ModelPipeNative121111.PipeRenderData data = ModelPipeNative121111.buildModelData(tile);',
                     'PipeModelKey data = tile.getPipe() == Pipe.EMPTY ? null : tile.getPipe().getModel();')
        return exact(text, 'ModelPipeNative121111.MODEL_DATA', 'MODEL_DATA')

    stage('transport/tile/TilePipeHolderModelData.java', model_snapshot)

    def engine(text):
        return exact(text, 'BCCoreBlocks.ENGINE_REDSTONE_TILE_BC8.get()',
                     'net.minecraft.core.registries.BuiltInRegistries.BLOCK_ENTITY_TYPE'
                     '.getValue(net.minecraft.resources.Identifier.parse("hw_buildcraft_reborn:engine_redstone"))')

    stage('core/blockEntity/TileEngineRedstone_BC8.java', engine)

    def atlas_only(text):
        start, end = span(text, 'public static Function<Identifier, TextureAtlasSprite> blockSprites()')
        return text[:text.index('public final class RenderCompat')] + \
            'public final class RenderCompat {\n    private RenderCompat() {}\n' + text[start:end] + '\n}\n'

    stage('lib/compat/RenderCompat.java', atlas_only)

    def pipe_renderer(text):
        # The chosen flows have no wires, gates, fluid geometry or dynamic
        # behaviour renderer. Keep the original native item submit path.
        text = remove_member(text, 'private static final VertexConsumer DISCARDING_VERTEX_CONSUMER =')
        for anchor in ('private static void submitLegacyGeometry(',
                       'private static PoseStack copyPose(',
                       'private static boolean sameLayer(',
                       'private static void renderPluggables(',
                       'private static <P extends PipePluggable> void renderPlug(',
                       'private static void renderFlow(',
                       'private static void renderBehaviour('):
            text = remove_method(text, anchor)
        start = text.index('        // Wires are generated dynamically')
        end = text.index('        if (pipe.flow instanceof PipeFlowItems itemFlow)', start)
        text = text[:start] + text[end:]
        start, end = span(text, '} else if (pipe.flow instanceof PipeFlowFluids fluidFlow)')
        # Remove the rest of the alternative flow/behaviour submissions.
        closing = text.index('\n    }', end)
        text = text[:start] + '}\n' + text[closing:]
        return text.replace('renderer.state.CameraRenderState', 'renderer.state.level.CameraRenderState')

    stage('transport/client/render/RenderPipeHolder.java', pipe_renderer)
    stage('transport/client/render/PipeFlowRendererItems.java',
          lambda text: remove_method(exact(text, ' implements IPipeFlowRenderer<PipeFlowItems>', ''),
                                     'public void render(').replace('RenderCompat.cutout()',
                                     'net.minecraft.client.renderer.rendertype.RenderTypes.cutoutMovingBlock()'))
    stage('core/client/render/RenderEngine_BC8.java',
          lambda text: text.replace('renderer.state.CameraRenderState', 'renderer.state.level.CameraRenderState')
          .replace('.mulPose(Axis.', '.rotate(Axis.')
          .replace('RenderCompat.entityCutout(',
                   'net.minecraft.client.renderer.rendertype.RenderTypes.entityCutoutCull('))

    def sprites(text):
        text = exact(text, '    public static final EnumMap<SlotIndex, SpriteHolder> ACTION_EXTRACTION_PRESET;\n')
        text = exact(text, '        ACTION_EXTRACTION_PRESET = new EnumMap<>(SlotIndex.class);\n')
        return remove_member(text, 'for (SlotIndex index : SlotIndex.VALUES)')

    stage('transport/BCTransportSprites.java', sprites)
    stage('lib/misc/ColourUtil.java',
          lambda text: exact(text, 'in.isColor()',
                             'java.util.EnumSet.range(ChatFormatting.BLACK, ChatFormatting.WHITE).contains(in)', 2))

    def locale_scope(text):
        text = remove_method(text, 'public static MutableComponent localizeFluidStaticAmount(IFluidTank tank)')
        text = remove_method(text, 'public static Component localizeColourComponent(')
        return exact(text, 'I18n.exists(key)', 'net.minecraft.locale.Language.getInstance().has(key)')

    stage('lib/misc/LocaleUtil.java', locale_scope)

    def native_pipe_model(text):
        # Keep original pipe-body baking, UVs, winding and tint recovery.
        # The selected scope has no static pluggables. A native chunk hook
        # must supply the immutable PipeModelKey, never a live tile to a baker.
        text = exact(text, 'implements DynamicBlockStateModel', 'implements BlockStateModel')
        text = exact(text, 'import net.minecraft.client.renderer.block.model.BakedQuad;',
                     'import net.minecraft.client.resources.model.geometry.BakedQuad;\n'
                     'import net.minecraft.client.resources.model.sprite.Material;\n'
                     'import net.minecraft.client.renderer.block.dispatch.BlockStateModel;\n'
                     'import com.mojang.blaze3d.platform.Transparency;')
        text = exact(text, 'import net.minecraft.client.renderer.block.model.BlockModelPart;',
                     'import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;')
        text = re.sub(r'\bBlockModelPart\b', 'BlockStateModelPart', text)
        text = exact(text, '    public static final ModelProperty<PipeRenderData> MODEL_DATA = new ModelProperty<>();',
                     '    private final PipeRenderData data;')
        text = replace_body(text, 'private ModelPipeNative121111()', '\n        this.data = null;\n    ')
        insertion = '''
    private ModelPipeNative121111(PipeRenderData data) { this.data = data; }

    public static ModelPipeNative121111 forKey(PipeModelKey key) {
        return new ModelPipeNative121111(buildModelData(key));
    }
'''
        text = text[:text.index('    public static boolean isPipeTint(')] + insertion + text[text.index('    public static boolean isPipeTint('):]
        text = exact(text, 'public static PipeRenderData buildModelData(TilePipeHolder tile)',
                     'public static PipeRenderData buildModelData(PipeModelKey key)')
        start = text.index('        Pipe pipe = tile.getPipe();')
        end = text.index('        if (key == null || key.definition == null)', start)
        text = text[:start] + text[end:]
        start = text.index('        List<buildcraft.lib.compat.mc121111.client.renderer.block.model.BakedQuad> legacyPlugCutout;')
        end = text.index('        // PipeBaseModelGenStandard temporarily', start)
        text = text[:start] + text[end:]
        for line in ('            legacyPlugCutout = PipeModelCachePluggable.cacheCutoutAll.bake(plugCutoutKey);\n',
                     '            legacyPlugTranslucent = PipeModelCachePluggable.cacheTranslucentAll.bake(plugTranslucentKey);\n',
                     '        cutout.addAll(convertPluggables(legacyPlugCutout));\n',
                     '        translucent.addAll(convertPluggables(legacyPlugTranslucent));\n'):
            text = exact(text, line)
        text = exact(text, 'new ArrayList<>(legacyBaseCutout.size() + legacyPlugCutout.size())',
                     'new ArrayList<>(legacyBaseCutout.size())')
        text = exact(text, 'new ArrayList<>(legacyBaseTranslucent.size() + legacyPlugTranslucent.size())',
                     'new ArrayList<>(legacyBaseTranslucent.size())')
        text = exact(text, 'PipeGeometryKey geometryKey = new PipeGeometryKey(key, plugCutoutKey, plugTranslucentKey);',
                     'PipeModelKey geometryKey = key;')
        text = remove_method(text, 'private static boolean isNativeStaticPluggable(')
        text = remove_method(text, 'private static List<BakedQuad> convertPluggables(')
        start, end = span(text, 'if (pipeBody)')
        quad_start = text.index('                result.add(new BakedQuad(', start)
        tint_start = text.index('                    tint,', quad_start)
        prefix = text[quad_start:tint_start]
        # Geometry and UV arguments above are copied verbatim from BCCE.
        native_quad = prefix + '''                    face,
                    BakedQuad.MaterialInfo.of(new Material.Baked(sprite, translucentLayer),
                        translucentLayer ? Transparency.TRANSLUCENT : Transparency.TRANSPARENT,
                        tint, face, 0)
                ));'''
        else_start = text.index(' else {', end)
        assert else_start == end
        _, else_end = span(text, '} else {\n                result.add(new BakedQuad(')
        text = text[:start] + 'int tint = encodedTint(quad, translucentLayer);\n' + native_quad + text[else_end:]
        text = remove_method(text, 'private static PipeRenderData modelData(')
        text = exact(text, 'quads.get(0).sprite()', 'quads.get(0).materialInfo().sprite()')
        text = remove_method(text, 'public Object createGeometryKey(')
        text = exact(text, 'public void collectParts(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random,',
                     'public void collectParts(RandomSource random,')
        text = exact(text, '        PipeRenderData data = modelData(level, pos);\n', '', 2)
        text = remove_method(text, 'public TextureAtlasSprite particleIcon(BlockAndTintGetter')
        text = text[:text.index('    private static TextureAtlasSprite fallbackParticle()')]
        text = remove_method(text, 'public TextureAtlasSprite particleIcon()')
        text = text + '''
    public Material.Baked particleMaterial() {
        return new Material.Baked(data == null ? fallbackParticle() : data.particle(), false);
    }

    public int materialFlags() {
        return data != null && data.translucent() != null ? BakedQuad.FLAG_TRANSLUCENT : 0;
    }

    private static TextureAtlasSprite fallbackParticle() {
        return SpriteUtil.missingSprite();
    }

    public record PipeRenderData(PipeModelKey geometryKey, @Nullable BlockStateModelPart cutout,
        @Nullable BlockStateModelPart translucent, TextureAtlasSprite particle) {}

    private record PipeModelPart(List<BakedQuad> quads, TextureAtlasSprite particle,
        ChunkSectionLayer layer) implements BlockStateModelPart {
        public List<BakedQuad> getQuads(@Nullable Direction side) {
            return side == null ? quads : List.of();
        }
        public boolean useAmbientOcclusion() { return true; }
        public Material.Baked particleMaterial() {
            return new Material.Baked(particle, layer == ChunkSectionLayer.TRANSLUCENT);
        }
        public int materialFlags() {
            return layer == ChunkSectionLayer.TRANSLUCENT ? BakedQuad.FLAG_TRANSLUCENT : 0;
        }
    }
}
'''
        return text

    stage('transport/client/model/ModelPipeNative121111.java', native_pipe_model)

    # Native render passes select their texture explicitly. Omit the removed
    # global GL binding API from sprite data rather than silently ignoring it.
    stage('lib/internal/core/render/ISprite.java',
          lambda text: exact(text, '    void bindTexture();\n'))
    for relative in ('lib/client/sprite/SpriteHolderRegistry.java',
                     'lib/client/sprite/SpriteRaw.java'):
        stage(relative, lambda text: remove_method(text, 'public void bindTexture()'))

    def sprite_data(text):
        declarations = []
        for anchor in ('public static Identifier transformLocation(',
                       'public static TextureAtlasSprite missingSprite()'):
            start, end = span(text, anchor)
            declarations.append(text[start:end])
        return text[:text.index('public class SpriteUtil')] + \
            'public class SpriteUtil {\n    protected static TextureAtlasSprite MISSING_TEX;\n' + \
            '\n'.join(declarations) + '\n}\n'

    stage('lib/misc/SpriteUtil.java', sprite_data)

    for relative in ('transport/net/MessageMultiPipeItem.java',
                     'transport/wire/MessageWireSystemsPowered.java'):
        stage(relative, lambda text: exact(text, 'FMLEnvironment.getDist() == Dist.CLIENT',
                                          'ctx.get().side().isClient()'))

    def networking(text):
        text = exact(text, 'import net.neoforged.api.distmarker.Dist;',
                     'import buildcraft.lib.compat.howl.NativeNetworkAccess;')
        text = re.sub(r'\bDist\b', 'BCNetworkSide', text)
        text = text.replace('BCNetworkSide.DEDICATED_SERVER', 'BCNetworkSide.SERVER')
        text = exact(text, 'RegisterPayloadHandlersEvent event', 'NativeNetworkAccess.Registrar registrar')
        text = replace_body(text, 'public static void registerPayloads(',
                            '\n        NativeNetworkAccess.register(registrar, PROTOCOL_VERSION, TYPE, '
                            'STREAM_CODEC, MessageManager::handlePayload);\n    ')
        text = exact(text, 'IPayloadContext context', 'BCPacketContext context')
        text = exact(text, 'context.flow() == PacketFlow.CLIENTBOUND', 'context.side().isClient()')
        text = exact(text, 'new NeoForgePacketContext(context)', 'context')
        text = text.replace('PacketDistributor.', 'NativeNetworkAccess.')
        text = text.replace('ClientNativeNetworkAccess.', 'NativeNetworkAccess.')
        return text

    stage('lib/net/MessageManager.java', networking)

    def runtime_scope(text):
        services = {'energy', 'wrenches', 'itemLists'}
        text = re.sub(r'^    private final (\w+) (\w+) = .*;\n',
                      lambda m: m.group() if m.group(2) in services else '', text, flags=re.M)
        text = re.sub(r'^        services\.put\(BuildCraftServices\.\w+, (\w+)\);\n',
                      lambda m: m.group() if m.group(1) in services else '', text, flags=re.M)
        keep_features = {'REGISTRIES', 'TRANSFER', 'ENERGY', 'PIPES'}
        text = re.sub(r'^        new ApiFeature\(BuildCraftFeatures\.(\w+), 1\),?\n',
                      lambda m: m.group() if m.group(1) in keep_features else '', text, flags=re.M)
        text = exact(text, 'new ApiFeature(BuildCraftFeatures.PIPES, 1),',
                     'new ApiFeature(BuildCraftFeatures.PIPES, 1)')
        keep_registries = {'MJ_CONNECTION_RULES', 'PIPE_TYPES', 'PIPE_COMPONENT_TYPES',
                           'PIPE_ATTACHMENT_TYPES', 'PIPE_CONNECTION_RULES',
                           'PIPE_SYNC_CHANNELS', 'SIGNAL_CHANNEL_TYPES'}
        text = re.sub(r'^        registerRegistry\(BuildCraftRegistries\.(\w+)\);\n',
                      lambda m: m.group() if m.group(1) in keep_registries else '', text, flags=re.M)
        text = exact(text, '        registerBuiltInMachineProperties();\n')
        text = remove_method(text, 'private void registerBuiltInMachineProperties()')
        for field in ('energyFluids', 'machineRecipes', 'crops', 'templates', 'facadeRules'):
            text = re.sub(r'^    public \w+ ' + field + r'\(\) \{ return ' + field + r'; \}\n', '', text, flags=re.M)
        return text

    stage('lib/internal/api/v2/BuildCraftApiRuntime.java', runtime_scope)
    stage('lib/internal/api/v2/WrenchServiceImpl.java',
          lambda text: exact(text, 'stack.getTags()', 'stack.typeHolder().tags()'))
    stage('lib/chunkload/IChunkLoadingTile.java',
          lambda text: exact(text, 'new ChunkPos(pos.offset(face.getUnitVec3i()))',
                             'ChunkPos.containing(pos.offset(face.getUnitVec3i()))'))

    def mj_boundary(text):
        assert 'import buildcraft.lib.compat.howl.FluidAction;' in text
        text = remove_method(text, 'private static FeEndpoint feEndpoint(')
        text = remove_member(text, 'private static final class FeEndpoint ')
        text = exact(text, '            FeEndpoint fe = feEndpoint(level, pos, side);\n'
                          '            return fe == null ? Optional.empty() : Optional.of(fe);',
                     '            return Optional.empty();')
        text = exact(text, '            FeEndpoint fe = feEndpoint(level, pos, side);\n'
                          '            return fe == null ? Optional.empty() : Optional.of(fe.descriptor());',
                     '            return Optional.empty();')
        for cap in ('CONNECTOR', 'RECEIVER', 'REDSTONE_RECEIVER', 'READABLE', 'PASSIVE_PROVIDER'):
            text = exact(text, 'level.getCapability(MjCapabilities.CAP_' + cap + ', pos, side)',
                         'buildcraft.lib.compat.howl.OriginalCapabilityLookup.get(level, pos, side, '
                         'MjCapabilities.CAP_' + cap + ')')
        for position, side in (('context.position()', 'context.side()'),
                               ('remotePos', 'context.side().getOpposite()')):
            text = exact(text, 'context.level().getCapability(MjCapabilities.CAP_CONNECTOR, ' + position + ', ' + side + ')',
                         'buildcraft.lib.compat.howl.OriginalCapabilityLookup.get(context.level(), ' +
                         position + ', ' + side + ', MjCapabilities.CAP_CONNECTOR)')
        return text

    stage('lib/internal/mj/MjApi2PlatformBridge.java', mj_boundary)

    def keyed_item(text):
        text = exact(text, ', RegistryCompat.itemProperties(new Item.Properties()));', ', properties);')
        text = exact(text, 'public ItemPipeHolder(PipeDefinition definition) {',
                     'public ItemPipeHolder(PipeDefinition definition) {\n'
                     '        this(definition, RegistryCompat.itemProperties(new Item.Properties()));\n'
                     '    }\n\n'
                     '    public ItemPipeHolder(PipeDefinition definition, Item.Properties properties) {')
        return text

    stage('transport/item/ItemPipeHolder.java', keyed_item)
    stage('core/block/BlockEngine_BC8.java',
          lambda text: remove_method(exact(text, 'BCCoreItems.ENGINE_ITEM_MAP.get(state.getValue(getEngineProperty()))',
                             'net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue('
                             'net.minecraft.resources.Identifier.parse("hw_buildcraft_reborn:engine_redstone"))'),
                                     'public boolean canHarvestBlock('))

    # Use the pinned upstream generator for metadata, with this port's target
    # properties. This generated class is not present in the source layers.
    upstream = BASE / 'bcce-8.0.23-original'
    generator = runpy.run_path(str(upstream / 'scripts/materialize_project.py'))
    lock = json.loads((HERE.parent / 'UPSTREAM.lock.json').read_text())
    target = lock['port_target']['minecraft'] + '-howl'
    generator['_write_buildcraft_target'](DEST.parents[3], {
        'common.deps.minecraft': lock['port_target']['minecraft'],
        'common.source.platform': 'H.O.W.L.',
        'common.network.protocol': lock['project_id'] + ':original-items-1',
        'common.mod.version': lock['project_version'],
    }, target)

    # Behaviour texture coordinates are original constant data. Referencing
    # the full renderer solely for these constants drags client machinery into
    # server-side behaviour compilation. Generate one shared data-only class.
    renderer = (SOURCE / 'transport/client/render/RenderPipeHolder.java').read_text()
    declarations = re.findall(r'^    public static final int\[\] \w+_UV = .*;', renderer, re.M)
    assert len(declarations) == 7
    uv_path = DEST / 'transport/client/render/PipeTextureUVs.java'
    uv_path.parent.mkdir(parents=True, exist_ok=True)
    uv_path.write_text(renderer[:renderer.index('package ')] +
                       'package buildcraft.transport.client.render;\n\n'
                       '/** Original RenderPipeHolder UV constants, shared without loading its renderer. */\n'
                       'public final class PipeTextureUVs {\n' + '\n'.join(declarations) +
                       '\n    private PipeTextureUVs() {}\n}\n')
    report['generated_constant_source'] = 'transport/client/render/RenderPipeHolder.java'
    for relative in ('transport/internal/pipe/PipeBehaviour.java',
                     'transport/pipe/behaviour/PipeBehaviourWood.java',
                     'transport/pipe/behaviour/PipeBehaviourDiamond.java'):
        stage(relative, lambda text: text.replace('RenderPipeHolder', 'PipeTextureUVs'))

    # javac otherwise resolves entire NeoForge mod entrypoints merely to read
    # an ASCII namespace constant. Inline only the pinned original constants.
    namespaces = {}
    for module, entry in (('lib', 'BCLib'), ('core', 'BCCore'),
                          ('transport', 'BCTransport'), ('energy', 'BCEnergy'),
                          ('builders', 'BCBuilders'), ('factory', 'BCFactory'),
                          ('silicon', 'BCSilicon'), ('robotics', 'BCRobotics')):
        entry_source = (SOURCE / module / (entry + '.java')).read_text()
        value = re.search(r'public static final String MODID = "([a-z]+)";', entry_source)
        assert value and value.group(1) == 'buildcraft' + module
        namespaces[(module, entry)] = value.group(1)
    # Strip only imports whose simple names are absent from executable source.
    # javac resolves unused imports too, including deleted module entrypoints.
    for source_path in sorted(SOURCE.rglob('*.java')):
        path = DEST / source_path.relative_to(SOURCE)
        text = (path if path.exists() else source_path).read_text()
        revised = text
        for (module, entry), namespace in namespaces.items():
            pattern = r'(?<![\w.])(?:buildcraft\.' + module + r'\.)?' + entry + r'\.MODID\b'
            revised = re.sub(pattern, '"' + namespace + '"', revised)
        revised = revised.replace('net.neoforged.neoforge.server.ServerLifecycleHooks',
                                  'buildcraft.lib.compat.howl.NativeLifecycleAccess')
        revised = revised.replace('ServerLifecycleHooks.getCurrentServer()',
                                  'NativeLifecycleAccess.getCurrentServer()')
        revised = unused_imports(revised)
        if revised != text:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(revised)
    # Record the final files, including global import/namespace changes, so
    # intermediate hashes cannot hide an additional source adaptation.
    report['final_files'] = []
    for path in sorted(DEST.rglob('*.java')):
        relative = path.relative_to(DEST)
        original = SOURCE / relative
        report['final_files'].append({
            'source': relative.as_posix(),
            'original_sha256': hashlib.sha256(original.read_bytes()).hexdigest() if original.exists() else None,
            'staged_sha256': hashlib.sha256(path.read_bytes()).hexdigest(),
        })
    # Guard the actual mechanics, rather than trusting a successful javac.
    protected = {
        'transport/pipe/behaviour/PipeBehaviourWood.java': ['protected int extractItems('],
        'transport/pipe/flow/TravellingItem.java': ['public boolean canMerge(', 'public boolean mergeWith(',
                                                  'public Vec3 getRenderPosition(', 'public boolean shouldRender('],
        'core/blockEntity/TileEngineRedstone_BC8.java': ['protected void engineUpdate()', 'public double getPistonSpeed()',
                                                      'public void updateHeatLevel()'],
        'transport/client/render/PipeFlowRendererItems.java': ['public void submit('],
    }
    report['preserved_methods'] = []
    for relative, anchors in protected.items():
        before = (SOURCE / relative).read_text()
        after = (DEST / relative).read_text() if (DEST / relative).exists() else before
        for anchor in anchors:
            original_body = body(before, anchor)
            assert body(after, anchor) == original_body, ('Gameplay changed', relative, anchor)
            report['preserved_methods'].append({'source': relative, 'method': anchor,
                'body_sha256': hashlib.sha256(original_body.encode()).hexdigest()})
    (BASE / 'staged-snapshot3/ITEM_PIPE_SCOPE.json').write_text(json.dumps(report, indent=2) + '\n')
    print('Selected original wooden/stone item definitions and MJ redstone engine' +
          (' with diamond sorting' if WITH_SORTING else '') + '; compilation still required.')


if __name__ == '__main__':
    main()
