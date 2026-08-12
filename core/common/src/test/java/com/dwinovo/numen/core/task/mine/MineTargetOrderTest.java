package com.dwinovo.numen.core.task.mine;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MineTargetOrderTest {

    @Test
    void nearbyHittableOrePreemptsTheWiderField() {
        BlockPos feet = new BlockPos(0, 2, 0);
        BlockPos overhead = new BlockPos(0, 9, 0);
        BlockPos far = new BlockPos(14, 2, 14);

        assertEquals(List.of(overhead), MineTargetOrder.preferred(
                List.of(far, overhead), feet, 8, overhead::equals));
    }

    @Test
    void fallsBackToAllKnownTargetsWhenNothingLocalIsHittable() {
        BlockPos feet = new BlockPos(0, 2, 0);
        List<BlockPos> known = List.of(new BlockPos(2, 2, 0), new BlockPos(14, 2, 14));

        assertEquals(known, MineTargetOrder.preferred(known, feet, 8, pos -> false));
    }
}
