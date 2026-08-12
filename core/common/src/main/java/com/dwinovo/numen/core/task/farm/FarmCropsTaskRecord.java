package com.dwinovo.numen.core.task.farm;

import com.dwinovo.numen.task.TaskRecord;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/** Typed descriptor and live progress for a bounded harvest/replant pass. */
public final class FarmCropsTaskRecord extends TaskRecord {

    public static final String TOOL_NAME = "farm_crops";

    public final Block crop;
    public final IntegerProperty age;
    public final BlockState seedling;
    public final int matureAge;
    public final Item seed;
    public final BlockPos min;
    public final BlockPos max;
    public final String label;

    private int harvested;
    private int planted;
    private int immature;
    private int obstructed;
    private int noSeed;

    public FarmCropsTaskRecord(String toolCallId, long deadlineGameTime,
                               Block crop, IntegerProperty age, BlockState seedling,
                               int matureAge, Item seed, BlockPos min, BlockPos max,
                               String label) {
        super(TOOL_NAME, toolCallId, deadlineGameTime);
        this.crop = crop;
        this.age = age;
        this.seedling = seedling;
        this.matureAge = matureAge;
        this.seed = seed;
        this.min = min.immutable();
        this.max = max.immutable();
        this.label = label;
    }

    public void harvestedOne() { harvested++; }
    public void plantedOne() { planted++; }
    public void skippedImmature() { immature++; }
    public void skippedObstructed() { obstructed++; }
    public void skippedNoSeed() { noSeed++; }
    public int harvested() { return harvested; }
    public int planted() { return planted; }
    public int immature() { return immature; }
    public int obstructed() { return obstructed; }
    public int noSeed() { return noSeed; }

    @Override
    public String describe() {
        return "收种 " + label + " 收" + harvested + " 种" + planted;
    }
}
