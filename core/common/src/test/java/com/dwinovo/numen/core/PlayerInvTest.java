package com.dwinovo.numen.core;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("mc")
class PlayerInvTest {

    @BeforeAll
    static void bootMinecraft() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test
    void fullMainInventoryCannotAcceptAnUnstackableDrop() {
        Inventory inventory = fullOfCobblestone();
        assertFalse(PlayerInv.canAcceptAny(inventory, Set.of(Items.DIAMOND)));
    }

    @Test
    void aPartialMatchingStackProvidesPickupCapacity() {
        Inventory inventory = fullOfCobblestone();
        inventory.items.set(7, new ItemStack(Items.DIAMOND, 63));
        assertTrue(PlayerInv.canAcceptAny(inventory, Set.of(Items.DIAMOND)));
    }

    @Test
    void anEmptyMainSlotProvidesPickupCapacity() {
        Inventory inventory = fullOfCobblestone();
        inventory.items.set(7, ItemStack.EMPTY);
        assertTrue(PlayerInv.canAcceptAny(inventory, Set.of(Items.DIAMOND)));
    }

    private static Inventory fullOfCobblestone() {
        Inventory inventory = new Inventory(null);
        for (int slot = 0; slot < PlayerInv.BUILDABLE_SLOTS; slot++) {
            inventory.items.set(slot, new ItemStack(Items.COBBLESTONE, 64));
        }
        return inventory;
    }
}
