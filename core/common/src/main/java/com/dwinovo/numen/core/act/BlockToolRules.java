package com.dwinovo.numen.core.act;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.level.block.state.BlockState;

/** Shared block-to-tool classification used by planning and actual hand selection. */
public final class BlockToolRules {

    public enum Kind {
        AXE("an axe"),
        PICKAXE("a pickaxe"),
        SHOVEL("a shovel"),
        HOE("a hoe"),
        SUITABLE("a suitable tool");

        private final String label;

        Kind(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    private BlockToolRules() {}

    public static Kind preferred(BlockState state) {
        if (state.is(BlockTags.MINEABLE_WITH_AXE) || isLog(state)) {
            return Kind.AXE;
        }
        if (state.is(BlockTags.MINEABLE_WITH_PICKAXE)) return Kind.PICKAXE;
        if (state.is(BlockTags.MINEABLE_WITH_SHOVEL)) return Kind.SHOVEL;
        if (state.is(BlockTags.MINEABLE_WITH_HOE)) return Kind.HOE;
        return Kind.SUITABLE;
    }

    public static boolean isLog(BlockState state) {
        if (state.is(BlockTags.LOGS)) return true;
        String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        return path.endsWith("_log") || path.endsWith("_wood")
                || path.endsWith("_stem") || path.endsWith("_hyphae");
    }

    /**
     * Vanilla and modded tool components normally provide the exact speed. The type fallback
     * keeps selection correct while registries are only lightly bootstrapped (notably tests),
     * and for conventional tool subclasses whose component data is populated later.
     */
    public static float effectiveDestroySpeed(ItemStack stack, BlockState state) {
        float declared = stack.getDestroySpeed(state);
        if (declared > 1.0f || !matches(stack.getItem(), preferred(state))) {
            return declared;
        }
        return 2.0f;
    }

    private static boolean matches(Item item, Kind kind) {
        return switch (kind) {
            case AXE -> item instanceof AxeItem;
            case PICKAXE -> item instanceof PickaxeItem;
            case SHOVEL -> item instanceof ShovelItem;
            case HOE -> item instanceof HoeItem;
            case SUITABLE -> false;
        };
    }
}
