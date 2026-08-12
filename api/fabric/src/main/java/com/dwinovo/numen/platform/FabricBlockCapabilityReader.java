package com.dwinovo.numen.platform;

import com.dwinovo.numen.platform.services.IBlockCapabilityReader;
import net.fabricmc.fabric.api.lookup.v1.block.BlockApiLookup;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fabric Transfer API implementation of the loader-neutral block-storage reader.
 * Item and fluid storage are provided by Fabric API; energy remains false because
 * Fabric has no built-in universal energy API dependency in this project.
 */
public final class FabricBlockCapabilityReader implements IBlockCapabilityReader {

    private static final int MAX_ITEM_LINES = 64;

    @Override
    public StorageKinds storageKinds(Level level, BlockPos pos) {
        boolean items = level.getBlockEntity(pos) instanceof Container
                || !collect(ItemStorage.SIDED, level, pos).isEmpty();
        boolean fluids = !collect(FluidStorage.SIDED, level, pos).isEmpty();
        return new StorageKinds(items, fluids, false);
    }

    @Override
    public String describe(Level level, BlockPos pos) {
        StringBuilder result = new StringBuilder();
        appendItems(collect(ItemStorage.SIDED, level, pos), result);
        appendFluids(collect(FluidStorage.SIDED, level, pos), result);
        return result.length() == 0 ? null : result.toString();
    }

    private static void appendItems(Map<Storage<ItemVariant>, List<String>> storages,
                                    StringBuilder out) {
        int index = 0;
        for (Map.Entry<Storage<ItemVariant>, List<String>> entry : storages.entrySet()) {
            out.append("items").append(storages.size() > 1 ? " #" + index : "")
                    .append(" (sides: ").append(String.join(",", entry.getValue())).append("):\n");
            int shown = 0;
            int nonEmpty = 0;
            int viewIndex = 0;
            for (StorageView<ItemVariant> view : entry.getKey()) {
                if (!view.isResourceBlank() && view.getAmount() > 0) {
                    nonEmpty++;
                    if (shown++ < MAX_ITEM_LINES) {
                        out.append("  view ").append(viewIndex).append(": ")
                                .append(BuiltInRegistries.ITEM.getKey(
                                        view.getResource().getItem()))
                                .append(" x").append(view.getAmount()).append("\n");
                    }
                }
                viewIndex++;
            }
            if (nonEmpty == 0) {
                out.append("  (all views empty)\n");
            } else if (nonEmpty > MAX_ITEM_LINES) {
                out.append("  … and ").append(nonEmpty - MAX_ITEM_LINES)
                        .append(" more non-empty views\n");
            }
            index++;
        }
    }

    private static void appendFluids(Map<Storage<FluidVariant>, List<String>> storages,
                                     StringBuilder out) {
        int index = 0;
        for (Map.Entry<Storage<FluidVariant>, List<String>> entry : storages.entrySet()) {
            out.append("fluids").append(storages.size() > 1 ? " #" + index : "")
                    .append(" (sides: ").append(String.join(",", entry.getValue())).append("):\n");
            int viewIndex = 0;
            boolean any = false;
            for (StorageView<FluidVariant> view : entry.getKey()) {
                if (!view.isResourceBlank() && view.getAmount() > 0) {
                    any = true;
                    out.append("  tank ").append(viewIndex).append(": ")
                            .append(BuiltInRegistries.FLUID.getKey(
                                    view.getResource().getFluid())).append(" ")
                            .append(toMilliBuckets(view.getAmount())).append("/")
                            .append(toMilliBuckets(view.getCapacity())).append(" mB\n");
                }
                viewIndex++;
            }
            if (!any) out.append("  (all views empty)\n");
            index++;
        }
    }

    private static long toMilliBuckets(long droplets) {
        return Math.round((double) droplets * 1000.0 / FluidConstants.BUCKET);
    }

    /** Query the unsided view and every face, de-duplicating providers by identity. */
    private static <T> Map<Storage<T>, List<String>> collect(
            BlockApiLookup<Storage<T>, Direction> lookup, Level level, BlockPos pos) {
        Map<Storage<T>, List<String>> found = new IdentityHashMap<>();
        add(found, lookup.find(level, pos, null), "all");
        for (Direction direction : Direction.values()) {
            add(found, lookup.find(level, pos, direction), direction.getName());
        }
        return found;
    }

    private static <T> void add(Map<Storage<T>, List<String>> found,
                                Storage<T> storage, String side) {
        if (storage != null) {
            found.computeIfAbsent(storage, ignored -> new ArrayList<>()).add(side);
        }
    }
}
