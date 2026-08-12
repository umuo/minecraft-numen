package com.dwinovo.numen.core.act;

import com.dwinovo.numen.entity.NumenPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The two "swap the best implement into the main hand" helpers, unified from
 * the duplicated block-breaking and combat inventory scans.
 *
 * <p>Both scan the WHOLE inventory (not just the hotbar) and swap via
 * {@link NumenPlayer#holdInHand(int)} so the selected item matches the action
 * the task is about to perform.
 */
public final class ToolSelect {

    private ToolSelect() {}

    /**
     * Hold the best implement for breaking {@code state}: among tools that beat
     * the bare hand, one that actually harvests the block (correct tier when the
     * block gates its drops) outranks a merely faster one.
     */
    public static void holdBestTool(NumenPlayer p, BlockState state) {
        Inventory inv = p.getInventory();
        int best = chooseSlot(inv, state, inv.selected);
        if (best >= 0) {
            p.holdInHand(best);
        }
    }

    /**
     * 纯选择核,给执行与单测共用。没有任何工具比徒手快时,主动换到空手/无耐久物品,
     * 而不是把之前战斗留下的剑继续拿来磨木头。
     */
    static int chooseSlot(Container inv, BlockState state, int selected) {
        boolean tierGated = state.requiresCorrectToolForDrops();
        int harvest = -1, any = -1;
        float harvestSpeed = 1.0f, anySpeed = 1.0f;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            float spd = BlockToolRules.effectiveDestroySpeed(s, state);
            if (spd <= 1.0f) continue;
            if (!tierGated || s.isCorrectToolForDrops(state)) {
                if (spd > harvestSpeed) { harvestSpeed = spd; harvest = i; }
            } else if (spd > anySpeed) {
                anySpeed = spd;
                any = i;
            }
        }
        int best = harvest >= 0 ? harvest : any;
        if (best >= 0) return best;

        // 当前手本来就不会掉耐久,不必为了“空手”搅动背包。
        if (selected >= 0 && selected < inv.getContainerSize()
                && harmless(inv.getItem(selected))) {
            return selected;
        }
        // 空槽优先:从背包深处换上来会把当前武器完整收回去,不会丢物品。
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).isEmpty()) return i;
        }
        // 背包塞满时拿任意无耐久物品代替剑/工具;挖掘不会消耗它。
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (harmless(inv.getItem(i))) return i;
        }
        return selected;
    }

    private static boolean harmless(ItemStack stack) {
        return stack.isEmpty() || stack.getMaxDamage() <= 1;
    }
}
