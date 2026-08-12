package com.dwinovo.numen.core.assist;

import com.dwinovo.numen.core.pathing.bridge.ContextFactory;
import com.dwinovo.numen.core.pathing.execute.PlayerNav;
import com.dwinovo.numen.core.pathing.moves.CalculationContext;
import com.dwinovo.numen.core.pathing.moves.ChunkLoadedTest;
import com.dwinovo.numen.entity.NumenPlayer;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

/** 协助行为里只准走现成道路的导航上下文。 */
final class AssistNavContexts {

    static final PlayerNav.ContextProvider NON_DESTRUCTIVE = new PlayerNav.ContextProvider() {
        @Override
        public CalculationContext forSearch(NumenPlayer player, LongSet sacred, LongSet deniedPlace) {
            return ContextFactory.forSearch(player, sacred, deniedPlace, AssistNavContexts::safeContext);
        }

        @Override
        public CalculationContext forExecution(NumenPlayer player, LongSet sacred, LongSet deniedPlace) {
            return ContextFactory.forExecution(player, sacred, deniedPlace, AssistNavContexts::safeContext);
        }
    };

    private AssistNavContexts() {}

    private static CalculationContext safeContext(ServerPlayer player, BlockGetter view,
                                                  ChunkLoadedTest loaded, boolean threadSafe,
                                                  LongSet sacred, LongSet deniedPlace) {
        return new CalculationContext(player, view, loaded, threadSafe, sacred, deniedPlace) {
            @Override
            public double breakCostMultiplierAt(int x, int y, int z, BlockState current) {
                return com.dwinovo.numen.core.pathing.moves.ActionCosts.COST_INF;
            }

            @Override
            public double costOfPlacingAt(int x, int y, int z, BlockState current) {
                return com.dwinovo.numen.core.pathing.moves.ActionCosts.COST_INF;
            }
        };
    }
}
