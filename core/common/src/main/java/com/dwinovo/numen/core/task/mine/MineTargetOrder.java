package com.dwinovo.numen.core.task.mine;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/** Selection policy kept separate from path cost: local hittable ore preempts the wider field. */
final class MineTargetOrder {

    private MineTargetOrder() {}

    static List<BlockPos> preferred(List<BlockPos> known, BlockPos feet, int radius,
                                    Predicate<BlockPos> hittable) {
        double radiusSqr = radius * radius;
        List<BlockPos> local = known.stream()
                .filter(pos -> pos.distSqr(feet) <= radiusSqr)
                .filter(hittable)
                .sorted(Comparator.comparingDouble(feet::distSqr))
                .toList();
        return local.isEmpty() ? new ArrayList<>(known) : new ArrayList<>(local);
    }
}
