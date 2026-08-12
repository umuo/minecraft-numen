package com.dwinovo.numen.core.assist;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AssistIntentClassifierTest {

    private static final AssistConfig ON = new AssistConfig(true, true, true, true, 10, 4);
    private static final ResourceLocation STONE = ResourceLocation.withDefaultNamespace("stone");

    @Test
    void oneOrdinaryBreakIsAmbiguousAndDoesNotStartMining() {
        var intent = classify(List.of(action(1, 100, OwnerActionTracker.Kind.EXCAVATION_BREAK,
                new BlockPos(0, 64, 0), STONE, null)), 100);

        assertEquals(AssistIntentClassifier.Type.FOLLOW, intent.type());
    }

    @Test
    void threeNearbySameBlockBreaksBecomeExcavation() {
        var intent = classify(List.of(
                action(1, 90, OwnerActionTracker.Kind.EXCAVATION_BREAK, new BlockPos(0, 64, 0), STONE, null),
                action(2, 95, OwnerActionTracker.Kind.EXCAVATION_BREAK, new BlockPos(1, 64, 0), STONE, null),
                action(3, 100, OwnerActionTracker.Kind.EXCAVATION_BREAK, new BlockPos(2, 64, 0), STONE, null)), 100);

        assertEquals(AssistIntentClassifier.Type.EXCAVATE, intent.type());
        assertEquals(3L, intent.throughSequence());
    }

    @Test
    void oneOreBreakIsAlreadyHighConfidence() {
        var intent = classify(List.of(action(7, 100, OwnerActionTracker.Kind.ORE_BREAK,
                new BlockPos(2, 12, 4), ResourceLocation.withDefaultNamespace("diamond_ore"), null)), 100);

        assertEquals(AssistIntentClassifier.Type.MINE_ORE, intent.type());
    }

    @Test
    void weedingNeedsRepeatedClearableBreaks() {
        var one = action(1, 100, OwnerActionTracker.Kind.CLEAR_BREAK,
                new BlockPos(0, 64, 0), ResourceLocation.withDefaultNamespace("short_grass"), null);
        assertEquals(AssistIntentClassifier.Type.FOLLOW, classify(List.of(one), 100).type());

        var two = action(2, 105, OwnerActionTracker.Kind.CLEAR_BREAK,
                new BlockPos(2, 64, 0), ResourceLocation.withDefaultNamespace("short_grass"), null);
        assertEquals(AssistIntentClassifier.Type.CLEAR, classify(List.of(one, two), 105).type());
    }

    @Test
    void laterPlacementSuppressesWorkAndMeansStepAside() {
        var intent = classify(List.of(
                action(1, 90, OwnerActionTracker.Kind.ORE_BREAK, new BlockPos(0, 20, 0),
                        ResourceLocation.withDefaultNamespace("iron_ore"), null),
                action(2, 100, OwnerActionTracker.Kind.PLACE, new BlockPos(1, 64, 1), null, null)), 100);

        assertEquals(AssistIntentClassifier.Type.BUILD_WAIT, intent.type());
    }

    @Test
    void hostileTargetAssistHasPriority() {
        UUID zombie = UUID.randomUUID();
        var intent = classify(List.of(
                action(1, 99, OwnerActionTracker.Kind.PLACE, new BlockPos(1, 64, 1), null, null),
                action(2, 100, OwnerActionTracker.Kind.ATTACK_HOSTILE, new BlockPos(2, 64, 2), null, zombie)), 100);

        assertEquals(AssistIntentClassifier.Type.COMBAT, intent.type());
        assertEquals(zombie, intent.targetUuid());
    }

    @Test
    void aLaterBuildActionEndsTheOldCombatSignal() {
        UUID zombie = UUID.randomUUID();
        var intent = classify(List.of(
                action(1, 95, OwnerActionTracker.Kind.ATTACK_HOSTILE,
                        new BlockPos(2, 64, 2), null, zombie),
                action(2, 100, OwnerActionTracker.Kind.PLACE,
                        new BlockPos(1, 64, 1), null, null)), 100);

        assertEquals(AssistIntentClassifier.Type.BUILD_WAIT, intent.type());
    }

    private static AssistIntentClassifier.Intent classify(List<OwnerActionTracker.Action> actions,
                                                           long now) {
        return AssistIntentClassifier.classify(actions, Level.OVERWORLD, now, ON);
    }

    private static OwnerActionTracker.Action action(long seq, long time, OwnerActionTracker.Kind kind,
                                                     BlockPos pos, ResourceLocation block,
                                                     UUID target) {
        return new OwnerActionTracker.Action(seq, time, Level.OVERWORLD, kind, pos, block, target);
    }
}
