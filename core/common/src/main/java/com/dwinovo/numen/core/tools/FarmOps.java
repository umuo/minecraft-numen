package com.dwinovo.numen.core.tools;

import com.dwinovo.numen.agent.tool.api.ToolContext;
import com.dwinovo.numen.core.build.BuildStates;
import com.dwinovo.numen.core.task.farm.FarmCropsTaskRecord;
import com.dwinovo.numen.entity.NumenPlayer;
import com.dwinovo.numen.task.TaskRecord;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.Collections;

/** Argument validation and task construction for {@code farm_crops}. */
public final class FarmOps {

    private static final int MAX_CELLS = 4096;
    private static final long MIN_TIMEOUT_TICKS = 60 * 20;
    private static final long TICKS_PER_CELL = 4;

    public TaskRecord farmCrops(String cropId,
                                int x1, int y1, int z1,
                                int x2, int y2, int z2,
                                NumenPlayer player,
                                ToolContext ctx) {
        ResourceLocation id = ResourceLocation.tryParse(cropId);
        if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) {
            throw new IllegalArgumentException("unknown crop block id: " + cropId);
        }
        Block crop = BuiltInRegistries.BLOCK.get(id);
        if (crop == Blocks.AIR) {
            throw new IllegalArgumentException("crop_id must name a crop block, not air");
        }
        Property<?> property = crop.getStateDefinition().getProperty("age");
        if (!(property instanceof IntegerProperty age)) {
            throw new IllegalArgumentException(cropId
                    + " is not an age-based crop (it has no integer 'age' property)");
        }

        BlockPos min = new BlockPos(Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2));
        BlockPos max = new BlockPos(Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2));
        long cells = (long) (max.getX() - min.getX() + 1)
                * (max.getY() - min.getY() + 1)
                * (max.getZ() - min.getZ() + 1);
        if (cells > MAX_CELLS) {
            throw new IllegalArgumentException("farm area is too large: " + cells
                    + " cells (maximum " + MAX_CELLS + ")");
        }

        BlockState seedling = crop.defaultBlockState().setValue(
                age, Collections.min(age.getPossibleValues()));
        // Crops generally have no BlockItem: wheat's planting item is wheat_seeds,
        // carrots' is carrot, and mod crops can define their own clone-stack answer.
        Item seed = BuildStates.materialItem(seedling, player.level(), min);
        if (seed == Items.AIR) {
            throw new IllegalArgumentException(cropId + " does not expose a planting item");
        }
        int matureAge = Collections.max(age.getPossibleValues());
        long timeout = Math.max(MIN_TIMEOUT_TICKS, cells * TICKS_PER_CELL);
        return new FarmCropsTaskRecord(ctx.toolCallId(), ctx.deadline(timeout), crop, age,
                seedling, matureAge, seed, min, max, id.toString());
    }
}
