package com.dwinovo.numen.core;

import com.dwinovo.numen.core.debug.DebugCommands;
import com.dwinovo.numen.core.debug.PathDebugRenderer;
import com.dwinovo.numen.core.pathing.cache.PathCaches;
import com.dwinovo.numen.core.scan.BlockSearch;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;

/**
 * Fabric entry point for the numen-core tool pack. Registers the tools and task
 * runners into the numen-api engine, then wires the server-tick work its tools
 * need (budget-sliced block scans, the off-thread pathfinder's chunk snapshots).
 * The engine itself (entity, agent loop, UI, network) is brought up by the
 * separate numen-api mod, which core depends on.
 */
public class NumenCoreFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        NumenCore.init();

        // 排程机器的心跳随机器归了 numen-api;core 只 tick 自己的工具配套。
        // Advance budget-sliced long-range block scans each tick.
        ServerTickEvents.END_SERVER_TICK.register(BlockSearch::tick);
        // Snapshot loaded chunks near companions for the off-thread planner to read live.
        ServerTickEvents.END_SERVER_TICK.register(PathCaches::serverTick);
        // Target-block index: periodic eviction of unloaded-chunk entries.
        ServerTickEvents.END_SERVER_TICK.register(com.dwinovo.numen.core.scan.TargetIndex::serverTick);
        // Debug particles for pathing state, sent only to players with debug on.
        ServerTickEvents.END_SERVER_TICK.register(PathDebugRenderer::serverTick);
        // Debug verbs merged into the /numen root registered by the engine mod.
        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess, environment) -> DebugCommands.register(dispatcher));

        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (world instanceof ServerLevel && player instanceof ServerPlayer serverPlayer) {
                com.dwinovo.numen.core.assist.OwnerActionTracker.recordBreak(serverPlayer, pos, state);
            }
        });
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (world instanceof ServerLevel && player instanceof ServerPlayer serverPlayer) {
                com.dwinovo.numen.core.assist.OwnerActionTracker.recordAttack(serverPlayer, entity);
            }
            return InteractionResult.PASS;
        });
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (world instanceof ServerLevel && player instanceof ServerPlayer serverPlayer
                    && player.getItemInHand(hand).getItem() instanceof BlockItem) {
                com.dwinovo.numen.core.assist.OwnerActionTracker.recordPlace(
                        serverPlayer, hit.getBlockPos().relative(hit.getDirection()));
            }
            return InteractionResult.PASS;
        });

        Constants.LOG.info("numen-core initialised on Fabric.");
    }
}
