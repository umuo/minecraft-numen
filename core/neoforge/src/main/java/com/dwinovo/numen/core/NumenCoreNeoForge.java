package com.dwinovo.numen.core;

import com.dwinovo.numen.agent.skill.SkillRegistry;
import com.dwinovo.numen.core.debug.DebugCommands;
import com.dwinovo.numen.core.debug.PathDebugRenderer;
import com.dwinovo.numen.core.pathing.cache.PathCaches;
import com.dwinovo.numen.task.CompanionTickDispatcher;
import com.dwinovo.numen.core.scan.BlockSearch;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;

import java.nio.file.Path;

/**
 * NeoForge entry point for the numen-core tool pack. Registers the tools and
 * task runners into the numen-api engine, then wires the server-tick work its
 * tools need (budget-sliced block scans, the off-thread pathfinder's chunk
 * snapshots). The engine itself is brought up by the separate numen-api mod,
 * which core depends on.
 */
@Mod(Constants.MOD_ID)
public class NumenCoreNeoForge {

    public NumenCoreNeoForge(IEventBus eventBus, ModContainer container) {
        NumenCore.init();

        NeoForge.EVENT_BUS.addListener(NumenCoreNeoForge::onServerTickPost);
        NeoForge.EVENT_BUS.addListener(NumenCoreNeoForge::onPlayerBreakBlock);
        NeoForge.EVENT_BUS.addListener(NumenCoreNeoForge::onPlayerAttack);
        NeoForge.EVENT_BUS.addListener(NumenCoreNeoForge::onPlayerUseBlock);
        // Debug verbs merged into the /numen root registered by the engine mod.
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.RegisterCommandsEvent e) ->
                DebugCommands.register(e.getDispatcher()));

        // Client-only: declare core's built-in skills, read in place from the
        // skills/ dir bundled in this jar. Skills feed the client-side LLM, so
        // this never runs on a dedicated server.
        if (FMLEnvironment.dist == Dist.CLIENT) {
            declareBundledSkills();
        }

        Constants.LOG.info("numen-core initialised on NeoForge.");
    }

    private static void declareBundledSkills() {
        Path root = ModList.get().getModFileById(Constants.MOD_ID).getFile().findResource("skills");
        if (root != null) {
            SkillRegistry.instance().declareBundled(root);
        } else {
            Constants.LOG.warn("[numen-core] no bundled skills/ dir found in jar");
        }
    }

    private static void onServerTickPost(ServerTickEvent.Post event) {
        // 排程机器的心跳随机器归了 numen-api;core 只 tick 自己的工具配套。
        BlockSearch.tick(event.getServer());
        PathCaches.serverTick(event.getServer());
        // Periodic eviction sweep for the target-block index (entries of unloaded chunks).
        com.dwinovo.numen.core.scan.TargetIndex.serverTick(event.getServer());
        // Debug particles for pathing state, sent only to players with debug on.
        PathDebugRenderer.serverTick(event.getServer());
    }

    private static void onPlayerBreakBlock(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player) {
            com.dwinovo.numen.core.assist.OwnerActionTracker.recordBreak(
                    player, event.getPos(), event.getState());
        }
    }

    private static void onPlayerAttack(AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            com.dwinovo.numen.core.assist.OwnerActionTracker.recordAttack(player, event.getTarget());
        }
    }

    private static void onPlayerUseBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player
                && player.getItemInHand(event.getHand()).getItem() instanceof BlockItem) {
            com.dwinovo.numen.core.assist.OwnerActionTracker.recordPlace(
                    player, event.getPos().relative(event.getFace()));
        }
    }
}
