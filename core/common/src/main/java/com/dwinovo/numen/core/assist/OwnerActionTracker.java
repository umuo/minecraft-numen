package com.dwinovo.numen.core.assist;

import com.dwinovo.numen.entity.NumenPlayer;
import com.dwinovo.numen.entity.CompanionRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 真实玩家最近做过什么。加载器事件只负责把事实送进来,意图判断统一留在 common。
 */
public final class OwnerActionTracker {

    public enum Kind { ORE_BREAK, EXCAVATION_BREAK, CLEAR_BREAK, PLACE, ATTACK_HOSTILE }

    public record Action(long sequence, long gameTime, ResourceKey<Level> dimension,
                         Kind kind, BlockPos pos, ResourceLocation blockId, UUID targetUuid) {
        public Action {
            pos = pos == null ? null : pos.immutable();
        }
    }

    private static final int MAX_ACTIONS = 32;
    private static final long MAX_AGE_TICKS = 200L;

    private static final class Timeline {
        private final ArrayDeque<Action> actions = new ArrayDeque<>();
        private long nextSequence = 1L;
    }

    private static final Map<UUID, Timeline> BY_OWNER = new HashMap<>();

    static {
        com.dwinovo.numen.platform.ServerLifecycle.onStopped(BY_OWNER::clear);
    }

    private OwnerActionTracker() {}

    public static void recordBreak(ServerPlayer player, BlockPos pos, BlockState oldState) {
        if (player instanceof NumenPlayer || player.isSpectator() || !hasEnabledCompanion(player)) {
            return;
        }
        Kind kind;
        if (AssistBlocks.isClearable(oldState)) {
            kind = Kind.CLEAR_BREAK;
        } else if (AssistBlocks.isOre(oldState)) {
            kind = Kind.ORE_BREAK;
        } else if (AssistBlocks.isExcavation(oldState)) {
            kind = Kind.EXCAVATION_BREAK;
        } else {
            return; // 拆建筑、机器与其他含糊动作不构成协助授权
        }
        append(player, kind, pos, BuiltInRegistries.BLOCK.getKey(oldState.getBlock()), null);
    }

    /** 放置尝试本身就是“正在施工”的安全信号;误报只会让同伴安静两秒。 */
    public static void recordPlace(ServerPlayer player, BlockPos pos) {
        if (!(player instanceof NumenPlayer) && !player.isSpectator()
                && hasEnabledCompanion(player)) {
            append(player, Kind.PLACE, pos, null, null);
        }
    }

    public static void recordAttack(ServerPlayer player, Entity target) {
        if (player instanceof NumenPlayer || player.isSpectator() || !(target instanceof Enemy)
                || !hasEnabledCompanion(player)) {
            return; // 默认绝不复制 PvP、杀牲畜或误伤村民
        }
        append(player, Kind.ATTACK_HOSTILE, target.blockPosition(), null, target.getUUID());
    }

    private static void append(ServerPlayer player, Kind kind, BlockPos pos,
                               ResourceLocation blockId, UUID targetUuid) {
        Timeline timeline = BY_OWNER.computeIfAbsent(player.getUUID(), ignored -> new Timeline());
        long now = player.level().getGameTime();
        timeline.actions.addLast(new Action(timeline.nextSequence++, now,
                player.level().dimension(), kind, pos, blockId, targetUuid));
        prune(timeline, now);
    }

    /** 没有启用协助的主人不留行为历史,大型服务器的表大小因此只随实际使用者增长。 */
    private static boolean hasEnabledCompanion(ServerPlayer player) {
        var server = player.level().getServer();
        if (server == null) return false;
        var ownedCompanions = CompanionRegistry.get(server).ownedBy(player.getUUID());
        if (ownedCompanions.isEmpty()) return false;
        AssistRegistry assist = AssistRegistry.get(server);
        for (var owned : ownedCompanions) {
            if (assist.get(owned.getKey()).enabled()) {
                return true;
            }
        }
        return false;
    }

    public static List<Action> recent(UUID owner, long now) {
        Timeline timeline = BY_OWNER.get(owner);
        if (timeline == null) {
            return List.of();
        }
        prune(timeline, now);
        return List.copyOf(new ArrayList<>(timeline.actions));
    }

    private static void prune(Timeline timeline, long now) {
        while (!timeline.actions.isEmpty()
                && (timeline.actions.size() > MAX_ACTIONS
                || now - timeline.actions.peekFirst().gameTime() > MAX_AGE_TICKS)) {
            timeline.actions.removeFirst();
        }
    }
}
