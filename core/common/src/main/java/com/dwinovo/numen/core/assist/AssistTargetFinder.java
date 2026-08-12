package com.dwinovo.numen.core.assist;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 从玩家刚做完的那一小块工作里挑一个有硬边界的协助批次。 */
final class AssistTargetFinder {

    private static final int[][] NEIGHBOURS_26 = neighbours26();

    private AssistTargetFinder() {}

    static List<BlockPos> oreVein(ServerLevel level, BlockPos broken,
                                  ResourceLocation blockId, int limit) {
        if (broken == null || blockId == null) {
            return List.of();
        }
        ArrayDeque<BlockPos> open = new ArrayDeque<>();
        Set<BlockPos> seen = new HashSet<>();
        List<BlockPos> found = new ArrayList<>();
        open.add(broken.immutable());
        seen.add(broken.immutable());
        while (!open.isEmpty() && found.size() < limit) {
            BlockPos at = open.removeFirst();
            for (int[] d : NEIGHBOURS_26) {
                BlockPos next = at.offset(d[0], d[1], d[2]).immutable();
                if (!seen.add(next) || next.distSqr(broken) > 36.0) {
                    continue;
                }
                if (sameBlock(level, next, blockId)) {
                    found.add(next);
                    open.addLast(next);
                    if (found.size() >= limit) break;
                }
            }
        }
        return found;
    }

    static List<BlockPos> workFace(ServerLevel level, BlockPos broken,
                                   ResourceLocation blockId, int limit) {
        if (broken == null || blockId == null) {
            return List.of();
        }
        List<BlockPos> out = new ArrayList<>();
        for (BlockPos p : BlockPos.betweenClosed(broken.offset(-2, -1, -2),
                broken.offset(2, 1, 2))) {
            if (sameBlock(level, p, blockId)) {
                out.add(p.immutable());
            }
        }
        out.sort(Comparator.comparingDouble(p -> p.distSqr(broken)));
        return out.stream().limit(limit).toList();
    }

    static List<BlockPos> clearable(ServerLevel level, BlockPos ownerFeet, int radius, int limit) {
        List<BlockPos> out = new ArrayList<>();
        for (BlockPos p : BlockPos.betweenClosed(
                ownerFeet.offset(-radius, -2, -radius),
                ownerFeet.offset(radius, 2, radius))) {
            if (AssistBlocks.isClearable(level.getBlockState(p))) {
                out.add(p.immutable());
            }
        }
        out.sort(Comparator.comparingDouble(p -> p.distSqr(ownerFeet)));
        return new ArrayList<>(new LinkedHashSet<>(out.stream().limit(limit).toList()));
    }

    private static boolean sameBlock(ServerLevel level, BlockPos pos, ResourceLocation id) {
        return id.equals(BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()));
    }

    private static int[][] neighbours26() {
        int[][] out = new int[26][3];
        int i = 0;
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    if (x == 0 && y == 0 && z == 0) continue;
                    out[i++] = new int[]{x, y, z};
                }
            }
        }
        return out;
    }
}
