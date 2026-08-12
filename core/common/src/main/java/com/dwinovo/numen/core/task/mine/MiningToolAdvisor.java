package com.dwinovo.numen.core.task.mine;

import com.dwinovo.numen.core.act.BlockToolRules;
import com.dwinovo.numen.core.pathing.util.BlockHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * {@code mine} 受理前的工具计划。硬门槛回答“能不能拿到掉落”,软门槛回答
 * “大量干这件事是否该先做工具”。小批两格保留给木头启动链。
 */
public final class MiningToolAdvisor {

    public static final int HAND_BOOTSTRAP_COUNT = 2;

    public record Assessment(boolean proceed, String message) {
        private static final Assessment READY = new Assessment(true, "");
    }

    private MiningToolAdvisor() {}

    public static Assessment assess(Container inventory, Set<Block> targets, int count) {
        boolean anyHarvestable = false;
        boolean allHarvestableTargetsAreLogs = true;
        Set<String> missingEfficient = new LinkedHashSet<>();
        Set<String> missingHarvest = new LinkedHashSet<>();

        for (Block block : targets) {
            BlockState state = block.defaultBlockState();
            String tool = preferredTool(state);
            if (!BlockHelper.canHarvest(inventory, state)) {
                missingHarvest.add(tool);
                continue;
            }
            anyHarvestable = true;
            allHarvestableTargetsAreLogs &= BlockToolRules.isLog(state);
            if (!hasEfficientTool(inventory, state)) {
                missingEfficient.add(tool);
            }
        }

        return decide(count, anyHarvestable, allHarvestableTargetsAreLogs,
                missingEfficient, missingHarvest);
    }

    /** 方块/背包事实折好之后的纯策略核。 */
    static Assessment decide(int count, boolean anyHarvestable,
                             boolean allHarvestableTargetsAreLogs,
                             Set<String> missingEfficient, Set<String> missingHarvest) {
        if (!anyHarvestable) {
            return new Assessment(false,
                    "Tool check stopped mining before any block was destroyed: none of the requested "
                            + "blocks can drop with the tools you carry. Bring "
                            + joined(missingHarvest) + " of the required tier, then retry.");
        }
        if (missingEfficient.isEmpty() || count <= HAND_BOOTSTRAP_COUNT) {
            return Assessment.READY;
        }

        String need = joined(missingEfficient);
        if (allHarvestableTargetsAreLogs && missingEfficient.equals(Set.of("an axe"))) {
            int remaining = Math.max(0, count - HAND_BOOTSTRAP_COUNT);
            return new Assessment(false,
                    "Tool check: this is a large wood job but no axe is carried. Do not spend sword "
                            + "durability chopping by hand. Bootstrap deliberately: mine exactly "
                            + HAND_BOOTSTRAP_COUNT + " of the requested logs first (the miner will put "
                            + "the sword away and use a harmless hand item), turn them into planks and "
                            + "sticks, craft/place a crafting table if none is within reach, craft a "
                            + "wooden axe, then retry the remaining " + remaining + " items.");
        }
        return new Assessment(false,
                "Tool check: gathering " + count + " items without " + need
                        + " would be needlessly slow or waste durability. Craft or equip " + need
                        + " first. If you are deliberately bootstrapping, mine at most "
                        + HAND_BOOTSTRAP_COUNT + " items by hand, make the tool, then continue.");
    }

    static boolean hasEfficientTool(Container inventory, BlockState state) {
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (BlockToolRules.effectiveDestroySpeed(stack, state) > 1.0f
                    && (!state.requiresCorrectToolForDrops() || stack.isCorrectToolForDrops(state))) {
                return true;
            }
        }
        return false;
    }

    static String preferredTool(BlockState state) {
        return BlockToolRules.preferred(state).label();
    }

    private static String joined(Set<String> tools) {
        return tools.isEmpty() ? "a suitable tool" : String.join(" or ", tools);
    }
}
