package com.dwinovo.numen.core.task.mine;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MiningToolAdvisorTest {

    @BeforeAll
    static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test
    void swordIsNotAnEfficientLoggingToolButAxeIs() {
        var sword = inventory(new ItemStack(Items.DIAMOND_SWORD));
        var axe = inventory(new ItemStack(Items.WOODEN_AXE));

        assertFalse(MiningToolAdvisor.hasEfficientTool(
                sword, Blocks.OAK_LOG.defaultBlockState()));
        assertTrue(MiningToolAdvisor.hasEfficientTool(
                axe, Blocks.OAK_LOG.defaultBlockState()));
    }

    @Test
    void largeWoodJobReturnsBootstrapPlanWithoutAxe() {
        var assessment = MiningToolAdvisor.decide(
                64, true, true, Set.of("an axe"), Set.of());

        assertFalse(assessment.proceed());
        assertTrue(assessment.message().contains("mine exactly 2"));
        assertTrue(assessment.message().contains("wooden axe"));
        assertTrue(assessment.message().contains("remaining 62"));
    }

    @Test
    void realLogAssessmentRecognizesTheBootstrapCase() {
        var assessment = MiningToolAdvisor.assess(
                inventory(new ItemStack(Items.DIAMOND_SWORD)),
                Set.of(Blocks.OAK_LOG), 64);

        assertFalse(assessment.proceed());
        assertTrue(assessment.message().contains("mine exactly 2"));
        assertTrue(assessment.message().contains("wooden axe"));
    }

    @Test
    void twoLogsAreAllowedAsTheBootstrapBatch() {
        var assessment = MiningToolAdvisor.decide(
                2, true, true, Set.of("an axe"), Set.of());

        assertTrue(assessment.proceed());
    }

    @Test
    void unharvestableOreIsRejectedBeforeBreakingAnything() {
        var assessment = MiningToolAdvisor.decide(
                1, false, false, Set.of(), Set.of("a pickaxe"));

        assertFalse(assessment.proceed());
        assertTrue(assessment.message().contains("before any block was destroyed"));
        assertTrue(assessment.message().contains("pickaxe"));
    }

    private static SimpleContainer inventory(ItemStack... stacks) {
        SimpleContainer out = new SimpleContainer(stacks.length);
        for (int i = 0; i < stacks.length; i++) out.setItem(i, stacks[i]);
        return out;
    }
}
