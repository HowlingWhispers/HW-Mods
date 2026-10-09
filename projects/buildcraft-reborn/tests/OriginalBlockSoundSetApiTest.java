import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.sounds.BlockSoundSet;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * Compile-only EXACT Mojang 26.4 Snapshot 3 source ABI check.
 * Confirms we mapped BCCE's original stone/metal sounds onto real
 * minecraft:block_sound_set registry keys, without deleting sound.
 */
public final class OriginalBlockSoundSetApiTest {
    static ResourceKey<BlockSoundSet> stone() {
        return ResourceKey.create(Registries.BLOCK_SOUND_SET,
                Identifier.parse("minecraft:stone"));
    }
    static ResourceKey<BlockSoundSet> metal() {
        return ResourceKey.create(Registries.BLOCK_SOUND_SET,
                Identifier.parse("minecraft:metal"));
    }
    static BlockBehaviour.Properties originalStoneProperties(
            BlockBehaviour.Properties preparedWithKey) {
        return preparedWithKey.sound(stone());
    }
    static BlockBehaviour.Properties originalMetalProperties(
            BlockBehaviour.Properties preparedWithKey) {
        return preparedWithKey.sound(metal());
    }
}
