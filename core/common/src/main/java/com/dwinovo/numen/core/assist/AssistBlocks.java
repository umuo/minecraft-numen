package com.dwinovo.numen.core.assist;

import com.dwinovo.numen.core.init.InitTag;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** 协助模式的保守方块分类。 */
public final class AssistBlocks {

    private static final TagKey<Block> COMMON_ORES = TagKey.create(
            Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("c", "ores"));

    private AssistBlocks() {}

    public static boolean isClearable(BlockState state) {
        Block block = state.getBlock();
        return state.is(InitTag.ASSIST_CLEARABLE)
                || block == Blocks.SHORT_GRASS
                || block == Blocks.TALL_GRASS
                || block == Blocks.FERN
                || block == Blocks.LARGE_FERN
                || block == Blocks.DEAD_BUSH;
    }

    public static boolean isOre(BlockState state) {
        return state.is(COMMON_ORES)
                || state.is(BlockTags.COAL_ORES)
                || state.is(BlockTags.COPPER_ORES)
                || state.is(BlockTags.DIAMOND_ORES)
                || state.is(BlockTags.EMERALD_ORES)
                || state.is(BlockTags.GOLD_ORES)
                || state.is(BlockTags.IRON_ORES)
                || state.is(BlockTags.LAPIS_ORES)
                || state.is(BlockTags.REDSTONE_ORES);
    }

    public static boolean isExcavation(BlockState state) {
        return isOre(state)
                || state.is(BlockTags.BASE_STONE_OVERWORLD)
                || state.is(BlockTags.BASE_STONE_NETHER)
                || state.is(InitTag.ASSIST_MINEABLE)
                || state.is(Blocks.END_STONE);
    }
}
