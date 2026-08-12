package com.dwinovo.numen.core.task.farm;

import com.dwinovo.numen.core.PlayerInv;
import com.dwinovo.numen.core.pathing.execute.PlayerNav;
import com.dwinovo.numen.core.pathing.goal.GoalCompiler;
import com.dwinovo.numen.core.task.base.AbstractCompanionTask;
import com.dwinovo.numen.entity.InputDriver;
import com.dwinovo.numen.entity.NumenPlayer;
import com.dwinovo.numen.task.TaskState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** A harvest-first farm pass; each cell is independent and material availability stays dynamic. */
public final class FarmCropsCompanionTask extends AbstractCompanionTask<FarmCropsTaskRecord> {

    private static final int TRAVEL_BUDGET_TICKS = 30 * 20;
    private static final int CELLS_PER_TICK = 4;
    private static final int PLACE_FLAGS = Block.UPDATE_ALL;

    private enum Phase { TRAVEL, HARVEST, PLANT }

    private final List<BlockPos> cells = new ArrayList<>();
    private Phase phase = Phase.TRAVEL;
    private int cursor;
    private int travelTicks;

    public FarmCropsCompanionTask(NumenPlayer player, FarmCropsTaskRecord record) {
        super(player, record);
    }

    @Override
    protected void onStart() {
        for (BlockPos pos : BlockPos.betweenClosed(r.min, r.max)) {
            cells.add(pos.immutable());
        }
    }

    @Override
    protected TaskState onTick() {
        if (player.isDeadOrDying()) {
            return TaskState.CANCELLED;
        }
        return switch (phase) {
            case TRAVEL -> tickTravel();
            case HARVEST -> tickHarvest();
            case PLANT -> tickPlant();
        };
    }

    private TaskState tickTravel() {
        if (nav == null) {
            BlockPos center = new BlockPos(
                    (r.min.getX() + r.max.getX()) / 2,
                    r.min.getY(),
                    (r.min.getZ() + r.max.getZ()) / 2);
            nav = PlayerNav.to(player, () -> GoalCompiler.near(center, 3.0), 1.0,
                    () -> player.blockPosition().distSqr(center) <= 25);
        }
        PlayerNav.Status status = nav.tick();
        if (status == PlayerNav.Status.ARRIVED || status == PlayerNav.Status.FAILED
                || (!nav.planningInFlight() && ++travelTicks >= TRAVEL_BUDGET_TICKS)) {
            stopNav();
            InputDriver.halt(player);
            phase = Phase.HARVEST;
            cursor = 0;
        }
        return TaskState.RUNNING;
    }

    private TaskState tickHarvest() {
        int budget = CELLS_PER_TICK;
        while (cursor < cells.size() && budget-- > 0) {
            harvest(cells.get(cursor++));
        }
        if (cursor >= cells.size()) {
            phase = Phase.PLANT;
            cursor = 0;
        }
        return TaskState.RUNNING;
    }

    private void harvest(BlockPos pos) {
        if (!player.level().isLoaded(pos)) return;
        BlockState state = player.level().getBlockState(pos);
        if (!state.is(r.crop)) return;
        if (state.getValue(r.age) < r.matureAge) {
            r.skippedImmature();
            return;
        }

        List<ItemStack> drops = harvestDrops(pos, state);
        boolean canReplant = player.hasInfiniteMaterials()
                || reserveOne(drops)
                || PlayerInv.remove(player.getInventory(), r.seed, 1) == 1;
        if (!canReplant) {
            r.skippedNoSeed();
            return;
        }
        if (!player.level().setBlock(pos, r.seedling, PLACE_FLAGS)) {
            if (!player.hasInfiniteMaterials()) {
                give(new ItemStack(r.seed));
                for (ItemStack drop : drops) {
                    if (!drop.isEmpty()) give(drop);
                }
            }
            r.skippedObstructed();
            return;
        }
        player.level().levelEvent(2001, pos, Block.getId(state));
        player.swing(InteractionHand.MAIN_HAND, true);
        for (ItemStack drop : drops) {
            if (!drop.isEmpty()) give(drop);
        }
        r.harvestedOne();
    }

    private List<ItemStack> harvestDrops(BlockPos pos, BlockState state) {
        if (player.hasInfiniteMaterials() || !(player.level() instanceof ServerLevel level)) {
            return new ArrayList<>();
        }
        try {
            return new ArrayList<>(Block.getDrops(state, level, pos,
                    level.getBlockEntity(pos), player, player.getMainHandItem()));
        } catch (RuntimeException brokenLootTable) {
            com.dwinovo.numen.core.Constants.LOG.warn(
                    "[numen-farm] failed to roll drops for {} at {}", state, pos, brokenLootTable);
            return new ArrayList<>();
        }
    }

    /** Reserve one planting item from the harvest before giving the surplus to the player. */
    private boolean reserveOne(List<ItemStack> drops) {
        for (ItemStack stack : drops) {
            if (!stack.isEmpty() && stack.is(r.seed)) {
                stack.shrink(1);
                return true;
            }
        }
        return false;
    }

    private void give(ItemStack stack) {
        ItemStack leftover = PlayerInv.add(player.getInventory(), stack);
        if (!leftover.isEmpty()) {
            Block.popResource(player.level(), player.blockPosition(), leftover);
        }
    }

    private TaskState tickPlant() {
        int budget = CELLS_PER_TICK;
        while (cursor < cells.size() && budget-- > 0) {
            plant(cells.get(cursor++));
        }
        return cursor >= cells.size() ? TaskState.SUCCESS : TaskState.RUNNING;
    }

    private void plant(BlockPos pos) {
        if (!player.level().isLoaded(pos)) return;
        BlockState current = player.level().getBlockState(pos);
        if (current.is(r.crop)) return;
        if (!current.isAir()) {
            r.skippedObstructed();
            return;
        }
        if (!r.seedling.canSurvive(player.level(), pos)) return;
        if (!player.hasInfiniteMaterials()
                && PlayerInv.remove(player.getInventory(), r.seed, 1) != 1) {
            r.skippedNoSeed();
            return;
        }
        if (!player.level().setBlock(pos, r.seedling, PLACE_FLAGS)) {
            if (!player.hasInfiniteMaterials()) give(new ItemStack(r.seed));
            r.skippedObstructed();
            return;
        }
        player.swing(InteractionHand.MAIN_HAND, true);
        r.plantedOne();
    }

    @Override
    protected Map<String, Object> resultData() {
        Map<String, Object> data = new HashMap<>();
        data.put("crop", r.label);
        data.put("harvested", r.harvested());
        data.put("planted", r.planted());
        data.put("immature_skipped", r.immature());
        data.put("obstructed_skipped", r.obstructed());
        data.put("missing_seed_skipped", r.noSeed());
        return data;
    }

    @Override
    protected String successMessage() {
        return "farmed " + r.label + ": harvested and replanted " + r.harvested()
                + ", planted " + r.planted() + " empty cell(s), left " + r.immature()
                + " immature, " + r.obstructed() + " obstructed, and " + r.noSeed()
                + " without seed";
    }

    @Override
    protected String timeoutMessage() {
        return "farm pass timed out after harvesting " + r.harvested()
                + " and planting " + r.planted();
    }

    @Override
    protected String cancelledMessage() {
        return "farm pass interrupted after harvesting " + r.harvested()
                + " and planting " + r.planted();
    }
}
