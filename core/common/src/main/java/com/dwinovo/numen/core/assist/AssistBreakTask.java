package com.dwinovo.numen.core.assist;

import com.dwinovo.numen.core.PlayerInv;
import com.dwinovo.numen.core.act.BlockDigger;
import com.dwinovo.numen.core.act.ToolSelect;
import com.dwinovo.numen.core.pathing.execute.PlayerNav;
import com.dwinovo.numen.core.pathing.goal.GoalCompiler;
import com.dwinovo.numen.core.pathing.moves.AimGeometry;
import com.dwinovo.numen.core.pathing.util.BlockHelper;
import com.dwinovo.numen.entity.InputDriver;
import com.dwinovo.numen.entity.NumenPlayer;
import com.dwinovo.numen.task.Task;
import com.dwinovo.numen.task.TaskResult;
import com.dwinovo.numen.task.TaskState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 协助模式的一小批破坏动作。目标是明确坐标清单,绝不把“玩家挖了石头”扩成
 * “搜索全世界所有石头”。
 */
final class AssistBreakTask implements Task {

    enum Mode { MINING, CLEARING }

    private static final long MAX_WORK_TICKS = 240L;
    private static final int MAX_ARRIVED_DUD_TICKS = 20;

    private final NumenPlayer player;
    private final Mode mode;
    private final ResourceLocation expectedBlock;
    private final int radius;
    private final LinkedHashSet<BlockPos> targets;
    private final BlockDigger digger;

    private PlayerNav nav;
    private long startedAt;
    private int arrivedDudTicks;

    AssistBreakTask(NumenPlayer player, Mode mode,
                    ResourceLocation expectedBlock, int radius, List<BlockPos> targets) {
        this.player = player;
        this.mode = mode;
        this.expectedBlock = expectedBlock;
        this.radius = radius;
        this.targets = new LinkedHashSet<>(targets.stream().map(BlockPos::immutable).toList());
        this.digger = new BlockDigger(player);
    }

    @Override
    public void start(NumenPlayer companion) {
        startedAt = companion.level().getGameTime();
    }

    @Override
    public TaskState tick(NumenPlayer companion) {
        ServerPlayer owner = companion.resolveOwnerPlayer();
        if (owner == null || owner.level() != companion.level()
                || companion.level().getGameTime() - startedAt >= MAX_WORK_TICKS) {
            return TaskState.SUCCESS;
        }
        prune(owner);
        if (targets.isEmpty()) {
            return TaskState.SUCCESS;
        }

        BlockPos reachable = nearestReachable();
        if (reachable != null) {
            arrivedDudTicks = 0;
            if (nav != null) nav.pause();
            if (mode == Mode.MINING && !safeToMine(reachable)) {
                targets.remove(reachable);
                return TaskState.RUNNING;
            }
            switch (digger.digTargetStep(reachable)) {
                case BROKE_TARGET -> targets.remove(reachable);
                case NO_SHOT -> {
                    // 站位判据与真实方块轮廓偶尔会差一线;交给导航换角度,不原地挥空。
                    if (nav != null) nav.stop();
                    nav = null;
                }
                default -> { }
            }
            return targets.isEmpty() ? TaskState.SUCCESS : TaskState.RUNNING;
        }

        digger.cancel();
        if (nav == null) {
            nav = PlayerNav.toRevalidating(player,
                    () -> GoalCompiler.mineField(new ArrayList<>(targets), List.of()),
                    1.0, () -> nearestReachable() != null,
                    mode == Mode.CLEARING
                            ? AssistNavContexts.NON_DESTRUCTIVE
                            : PlayerNav.ContextProvider.DEFAULT);
        }
        return switch (nav.tick()) {
            case RUNNING -> TaskState.RUNNING;
            case FAILED -> TaskState.SUCCESS; // 协助不是硬任务,走不到就安静放弃这一批
            case ARRIVED -> {
                nav.pause();
                if (nearestReachable() == null && ++arrivedDudTicks >= MAX_ARRIVED_DUD_TICKS) {
                    targets.remove(nearestTarget());
                    arrivedDudTicks = 0;
                    nav.stop();
                    nav = null;
                }
                yield targets.isEmpty() ? TaskState.SUCCESS : TaskState.RUNNING;
            }
        };
    }

    private void prune(ServerPlayer owner) {
        ServerLevel level = (ServerLevel) player.level();
        BlockPos ownerFeet = owner.blockPosition();
        BlockPos playerFeet = player.blockPosition();
        targets.removeIf(pos -> !level.hasChunkAt(pos)
                || pos.distSqr(ownerFeet) > (double) radius * radius
                || pos.equals(ownerFeet) || pos.equals(ownerFeet.above()) || pos.equals(ownerFeet.below())
                || pos.equals(playerFeet) || pos.equals(playerFeet.above()) || pos.equals(playerFeet.below())
                || level.getBlockEntity(pos) != null
                || BlockHelper.shouldAvoidBreaking(level, pos)
                || !matches(level.getBlockState(pos)));
    }

    private boolean matches(BlockState state) {
        if (mode == Mode.CLEARING) {
            return AssistBlocks.isClearable(state);
        }
        return expectedBlock != null
                && expectedBlock.equals(BuiltInRegistries.BLOCK.getKey(state.getBlock()));
    }

    private boolean safeToMine(BlockPos pos) {
        ServerLevel level = (ServerLevel) player.level();
        BlockState state = level.getBlockState(pos);
        if (!BlockHelper.canHarvest(player.getInventory(), state)) {
            return false;
        }
        ToolSelect.holdBestTool(player, state);
        Set<net.minecraft.world.item.Item> drops = new LinkedHashSet<>();
        for (var stack : Block.getDrops(state, level, pos, null, player, player.getMainHandItem())) {
            if (!stack.isEmpty()) drops.add(stack.getItem());
        }
        return drops.isEmpty() || PlayerInv.canAcceptAny(player.getInventory(), drops);
    }

    private BlockPos nearestReachable() {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos pos : targets) {
            double distance = pos.distSqr(player.blockPosition());
            if (distance < bestDistance && reachable(pos)) {
                best = pos;
                bestDistance = distance;
            }
        }
        return best;
    }

    private boolean reachable(BlockPos pos) {
        Vec3 eyes = player.getEyePosition();
        Vec3 aim = Vec3.atCenterOf(pos);
        double reach = AimGeometry.blockReachDistance(player);
        if (eyes.distanceToSqr(aim) > reach * reach) {
            return false;
        }
        BlockHitResult hit = player.level().clip(new ClipContext(
                eyes, aim, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(pos);
    }

    private BlockPos nearestTarget() {
        return targets.stream().min(java.util.Comparator.comparingDouble(
                p -> p.distSqr(player.blockPosition()))).orElse(null);
    }

    @Override
    public void stop(NumenPlayer companion, StopReason why) {
        digger.cancel();
        if (nav != null) nav.pause();
        InputDriver.halt(companion);
    }

    @Override
    public TaskResult result(TaskState terminal) {
        digger.cancel();
        if (nav != null) {
            nav.stop();
            nav = null;
        }
        InputDriver.halt(player);
        return TaskResult.ok("协助批次结束");
    }

    @Override
    public String name() {
        return mode == Mode.CLEARING ? "assist_clear" : "assist_mine";
    }
}
