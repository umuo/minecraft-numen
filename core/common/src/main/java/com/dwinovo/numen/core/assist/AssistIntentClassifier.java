package com.dwinovo.numen.core.assist;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** 把短时间行为折成“帮忙 / 只等着”的确定性判据。 */
public final class AssistIntentClassifier {

    public enum Type { FOLLOW, BUILD_WAIT, COMBAT, MINE_ORE, EXCAVATE, CLEAR }

    public record Intent(Type type, long throughSequence, long observedAt,
                         BlockPos anchor, ResourceLocation blockId, UUID targetUuid) {
        private static Intent follow() {
            return new Intent(Type.FOLLOW, 0L, Long.MIN_VALUE, null, null, null);
        }
    }

    static final long COMBAT_WINDOW = 40L;
    static final long BUILD_WINDOW = 60L;
    static final long WORK_WINDOW = 40L;

    private AssistIntentClassifier() {}

    public static Intent classify(List<OwnerActionTracker.Action> actions,
                                  ResourceKey<Level> dimension, long now,
                                  AssistConfig config) {
        List<OwnerActionTracker.Action> here = actions.stream()
                .filter(a -> a.dimension().equals(dimension))
                .toList();

        OwnerActionTracker.Action attack = latest(here, OwnerActionTracker.Kind.ATTACK_HOSTILE,
                now, COMBAT_WINDOW);
        OwnerActionTracker.Action place = latest(here, OwnerActionTracker.Kind.PLACE,
                now, BUILD_WINDOW);
        OwnerActionTracker.Action newestWork = here.stream()
                .filter(a -> now - a.gameTime() <= WORK_WINDOW)
                .filter(a -> a.kind() != OwnerActionTracker.Kind.PLACE
                        && a.kind() != OwnerActionTracker.Kind.ATTACK_HOSTILE)
                .max(Comparator.comparingLong(OwnerActionTracker.Action::sequence))
                .orElse(null);

        OwnerActionTracker.Action newest = newest(attack, place, newestWork);
        if (newest == null) {
            return Intent.follow();
        }
        if (newest == attack) {
            return config.combat() ? from(Type.COMBAT, attack) : Intent.follow();
        }
        if (newest == place) {
            return from(Type.BUILD_WAIT, place);
        }

        return switch (newestWork.kind()) {
            case ORE_BREAK -> config.mining() ? from(Type.MINE_ORE, newestWork) : Intent.follow();
            case CLEAR_BREAK -> config.clearing()
                    && countNear(here, newestWork, OwnerActionTracker.Kind.CLEAR_BREAK, null) >= 2
                    ? from(Type.CLEAR, newestWork) : Intent.follow();
            case EXCAVATION_BREAK -> config.mining()
                    && countNear(here, newestWork, OwnerActionTracker.Kind.EXCAVATION_BREAK,
                    newestWork.blockId()) >= 3
                    ? from(Type.EXCAVATE, newestWork) : Intent.follow();
            default -> Intent.follow();
        };
    }

    private static OwnerActionTracker.Action newest(OwnerActionTracker.Action... actions) {
        OwnerActionTracker.Action out = null;
        for (OwnerActionTracker.Action action : actions) {
            if (action != null && (out == null || action.sequence() > out.sequence())) {
                out = action;
            }
        }
        return out;
    }

    private static int countNear(List<OwnerActionTracker.Action> actions,
                                 OwnerActionTracker.Action latest,
                                 OwnerActionTracker.Kind kind, ResourceLocation sameBlock) {
        int count = 0;
        for (OwnerActionTracker.Action action : actions) {
            if (action.kind() != kind
                    || latest.gameTime() - action.gameTime() > WORK_WINDOW
                    || latest.gameTime() < action.gameTime()) {
                continue;
            }
            if (sameBlock != null && !sameBlock.equals(action.blockId())) {
                continue;
            }
            if (action.pos() != null && latest.pos() != null
                    && action.pos().distSqr(latest.pos()) <= 36.0) {
                count++;
            }
        }
        return count;
    }

    private static OwnerActionTracker.Action latest(List<OwnerActionTracker.Action> actions,
                                                     OwnerActionTracker.Kind kind,
                                                     long now, long window) {
        return actions.stream()
                .filter(a -> a.kind() == kind && now - a.gameTime() <= window)
                .max(Comparator.comparingLong(OwnerActionTracker.Action::sequence))
                .orElse(null);
    }

    private static Intent from(Type type, OwnerActionTracker.Action action) {
        return new Intent(type, action.sequence(), action.gameTime(), action.pos(),
                action.blockId(), action.targetUuid());
    }
}
