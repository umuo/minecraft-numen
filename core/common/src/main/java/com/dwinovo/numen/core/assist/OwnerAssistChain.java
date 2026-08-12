package com.dwinovo.numen.core.assist;

import com.dwinovo.numen.core.pathing.calc.NavGoal;
import com.dwinovo.numen.core.pathing.execute.PlayerNav;
import com.dwinovo.numen.core.pathing.goal.GoalCompiler;
import com.dwinovo.numen.core.task.combat.AttackCompanionTask;
import com.dwinovo.numen.core.task.combat.AttackTaskRecord;
import com.dwinovo.numen.core.task.move.FollowCompanionTask;
import com.dwinovo.numen.core.task.move.FollowTaskRecord;
import com.dwinovo.numen.entity.InputDriver;
import com.dwinovo.numen.entity.NumenPlayer;
import com.dwinovo.numen.task.CompanionTickDispatcher;
import com.dwinovo.numen.task.Task;
import com.dwinovo.numen.task.TaskState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Enemy;

import java.util.List;

/**
 * 闲时协助层:显式工作为空时才醒,所以主人临时派的任何任务天然压过它；任务腾槽后
 * 它又回来。内部只消费服务器已经观察到的高置信度行为,其余时间就是跟随。
 */
public final class OwnerAssistChain implements Task {

    private static final long COMBAT_SLICE_TICKS = 600L;
    private static final double BUILD_INNER_DISTANCE = 4.0;
    private static final double BUILD_OUTER_DISTANCE = 6.0;

    private FollowCompanionTask follow;
    private double followDistance = -1.0;
    private Task worker;
    private long workerExpiresAt;
    private long consumedSequence;
    private PlayerNav buildAvoidNav;

    @Override
    public boolean canRun(NumenPlayer companion) {
        MinecraftServer server = companion.level().getServer();
        if (server == null || CompanionTickDispatcher.hasExplicitWork(companion.getUUID())) {
            return false;
        }
        AssistConfig config = AssistRegistry.get(server).get(companion.getUUID());
        ServerPlayer owner = companion.resolveOwnerPlayer();
        return config.enabled() && owner != null && owner.level() == companion.level();
    }

    @Override
    public TaskState tick(NumenPlayer companion) {
        AssistConfig config = AssistRegistry.get(companion.level().getServer()).get(companion.getUUID());
        ServerPlayer owner = companion.resolveOwnerPlayer();
        if (owner == null || owner.level() != companion.level()) {
            stopBodies(companion);
            return TaskState.RUNNING;
        }

        long now = companion.level().getGameTime();
        AssistIntentClassifier.Intent intent = AssistIntentClassifier.classify(
                OwnerActionTracker.recent(owner.getUUID(), now), owner.level().dimension(), now, config);

        if (intent.throughSequence() > consumedSequence) {
            consume(companion, owner, config, intent, now);
            consumedSequence = intent.throughSequence();
        }

        if (intent.type() == AssistIntentClassifier.Type.BUILD_WAIT) {
            if (worker instanceof AssistBreakTask) finishWorker(companion, TaskState.CANCELLED);
            tickBuildAvoid(companion, owner);
            return TaskState.RUNNING;
        }
        stopBuildAvoid();

        if (worker != null) {
            if (now >= workerExpiresAt) {
                finishWorker(companion, TaskState.TIMEOUT);
            } else {
                TaskState state = worker.tick(companion);
                if (state.isTerminal()) {
                    finishWorker(companion, state);
                }
            }
            return TaskState.RUNNING;
        }

        tickFollow(companion, config.keepDistance());
        return TaskState.RUNNING;
    }

    private void consume(NumenPlayer companion, ServerPlayer owner, AssistConfig config,
                         AssistIntentClassifier.Intent intent, long now) {
        switch (intent.type()) {
            case COMBAT -> beginCombat(companion, owner, intent, now);
            case MINE_ORE -> beginBreak(companion, owner, config, intent,
                    AssistTargetFinder.oreVein((ServerLevel) companion.level(), intent.anchor(),
                            intent.blockId(), 12), AssistBreakTask.Mode.MINING, now + 240L);
            case EXCAVATE -> beginBreak(companion, owner, config, intent,
                    AssistTargetFinder.workFace((ServerLevel) companion.level(), intent.anchor(),
                            intent.blockId(), 6), AssistBreakTask.Mode.MINING, now + 200L);
            case CLEAR -> beginBreak(companion, owner, config, intent,
                    AssistTargetFinder.clearable((ServerLevel) companion.level(),
                            owner.blockPosition(), Math.min(config.radius(), 8), 20),
                    AssistBreakTask.Mode.CLEARING, now + 240L);
            case BUILD_WAIT -> {
                if (worker instanceof AssistBreakTask) finishWorker(companion, TaskState.CANCELLED);
            }
            case FOLLOW -> { }
        }
    }

    private void beginCombat(NumenPlayer companion, ServerPlayer owner,
                             AssistIntentClassifier.Intent intent, long now) {
        Entity target = intent.targetUuid() == null ? null
                : ((ServerLevel) companion.level()).getEntity(intent.targetUuid());
        if (!(target instanceof Enemy) || !target.isAlive()
                || target.distanceToSqr(owner) > 18.0 * 18.0) {
            return;
        }
        finishWorker(companion, TaskState.CANCELLED);
        stopFollow(companion);
        AttackTaskRecord record = new AttackTaskRecord(
                "assist-combat-" + intent.throughSequence(), now + COMBAT_SLICE_TICKS,
                List.of(target.getId()), false);
        worker = new AttackCompanionTask(companion, record);
        workerExpiresAt = now + COMBAT_SLICE_TICKS;
        worker.start(companion);
    }

    private void beginBreak(NumenPlayer companion, ServerPlayer owner, AssistConfig config,
                            AssistIntentClassifier.Intent intent, List<BlockPos> targets,
                            AssistBreakTask.Mode mode, long expiresAt) {
        if (targets.isEmpty()) {
            return;
        }
        finishWorker(companion, TaskState.CANCELLED);
        stopFollow(companion);
        worker = new AssistBreakTask(companion, mode,
                intent.blockId(), config.radius(), targets);
        workerExpiresAt = expiresAt;
        worker.start(companion);
    }

    private void tickFollow(NumenPlayer companion, int keepDistance) {
        stopBuildAvoid();
        if (follow == null || followDistance != keepDistance) {
            stopFollow(companion);
            followDistance = keepDistance;
            follow = new FollowCompanionTask(companion,
                    new FollowTaskRecord("assist-follow", keepDistance, null, null));
            follow.start(companion);
        }
        if (follow.canRun(companion)) {
            follow.tick(companion);
        } else {
            InputDriver.halt(companion);
        }
    }

    /** 施工时站进 4～6 格环带,太近会主动退开,而不是只“不帮忙”。 */
    private void tickBuildAvoid(NumenPlayer companion, ServerPlayer owner) {
        stopFollow(companion);
        double distance = horizontalDistance(companion, owner);
        if (distance >= BUILD_INNER_DISTANCE && distance <= BUILD_OUTER_DISTANCE) {
            stopBuildAvoid();
            InputDriver.halt(companion);
            return;
        }
        if (buildAvoidNav == null) {
            buildAvoidNav = PlayerNav.toRevalidating(companion,
                    () -> new GoalCompiler.Compiled(
                            NavGoal.ring(owner.blockPosition(), BUILD_INNER_DISTANCE,
                                    BUILD_OUTER_DISTANCE),
                            it.unimi.dsi.fastutil.longs.LongSets.emptySet()),
                    1.0, () -> {
                        double d = horizontalDistance(companion, owner);
                        return d >= BUILD_INNER_DISTANCE && d <= BUILD_OUTER_DISTANCE;
                    }, AssistNavContexts.NON_DESTRUCTIVE);
        }
        if (buildAvoidNav.tick() == PlayerNav.Status.FAILED) {
            stopBuildAvoid();
            InputDriver.halt(companion);
        }
    }

    private static double horizontalDistance(NumenPlayer companion, ServerPlayer owner) {
        double dx = companion.getX() - owner.getX();
        double dz = companion.getZ() - owner.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    private void finishWorker(NumenPlayer companion, TaskState state) {
        if (worker == null) return;
        worker.result(state); // cleanup belongs to the child even though no LLM result is emitted
        worker = null;
        workerExpiresAt = 0L;
        InputDriver.halt(companion);
    }

    private void stopFollow(NumenPlayer companion) {
        if (follow != null) {
            // 这里不是抢占后还要恢复,而是永久丢掉这一个跟随实例;result 才会真正
            // stopNav/清路径覆盖。只调 PREEMPTED 会把导航计划连同异步搜索一起泄漏。
            follow.stop(companion, StopReason.REPLACED);
            follow.result(TaskState.CANCELLED);
            follow = null;
            followDistance = -1.0;
        }
    }

    private void stopBuildAvoid() {
        if (buildAvoidNav != null) {
            buildAvoidNav.stop();
            buildAvoidNav = null;
        }
    }

    private void stopBodies(NumenPlayer companion) {
        if (worker != null) worker.stop(companion, StopReason.PREEMPTED);
        if (follow != null) follow.stop(companion, StopReason.PREEMPTED);
        if (buildAvoidNav != null) buildAvoidNav.pause();
        InputDriver.halt(companion);
    }

    @Override
    public void stop(NumenPlayer companion, StopReason why) {
        stopBodies(companion);
        MinecraftServer server = companion.level().getServer();
        if (server == null || !AssistRegistry.get(server).get(companion.getUUID()).enabled()) {
            finishWorker(companion, TaskState.CANCELLED);
            stopFollow(companion);
            stopBuildAvoid();
        }
    }

    @Override
    public String name() {
        return "owner_assist";
    }
}
