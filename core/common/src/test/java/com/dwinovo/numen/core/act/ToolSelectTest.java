package com.dwinovo.numen.core.act;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ToolSelectTest {

    @BeforeAll
    static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test
    void axeWinsForLogsEvenWhenSwordWasSelected() {
        SimpleContainer inventory = inventory(
                new ItemStack(Items.DIAMOND_SWORD),
                new ItemStack(Items.WOODEN_AXE),
                ItemStack.EMPTY);

        assertEquals(1, ToolSelect.chooseSlot(inventory, Blocks.OAK_LOG.defaultBlockState(), 0));
    }

    @Test
    void emptyHandReplacesSwordWhenNoUsefulToolExists() {
        SimpleContainer inventory = inventory(
                new ItemStack(Items.DIAMOND_SWORD),
                new ItemStack(Items.OAK_LOG),
                ItemStack.EMPTY);

        assertEquals(2, ToolSelect.chooseSlot(inventory, Blocks.OAK_LOG.defaultBlockState(), 0));
    }

    @Test
    void fullInventoryUsesANonDamageableItemInsteadOfSword() {
        SimpleContainer inventory = inventory(
                new ItemStack(Items.DIAMOND_SWORD),
                new ItemStack(Items.COBBLESTONE),
                new ItemStack(Items.DIRT));

        assertEquals(1, ToolSelect.chooseSlot(inventory, Blocks.OAK_LOG.defaultBlockState(), 0));
    }

    private static SimpleContainer inventory(ItemStack... stacks) {
        SimpleContainer out = new SimpleContainer(stacks.length);
        for (int i = 0; i < stacks.length; i++) out.setItem(i, stacks[i]);
        return out;
    }
}
