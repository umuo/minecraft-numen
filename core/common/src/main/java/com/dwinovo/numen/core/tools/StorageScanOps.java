package com.dwinovo.numen.core.tools;

import com.dwinovo.numen.entity.NumenPlayer;
import com.dwinovo.numen.platform.Services;
import com.dwinovo.numen.platform.services.IBlockCapabilityReader.StorageKinds;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Finds loaded block-entity-backed storage by capability instead of by a hard-coded block id. */
public final class StorageScanOps {

    private static final int MIN_RADIUS = 1;
    private static final int MAX_RADIUS = 64;
    private static final int MAX_RESULTS = 32;

    public String scanNearby(int radius, String filter, NumenPlayer self) {
        int boundedRadius = Math.clamp(radius, MIN_RADIUS, MAX_RADIUS);
        String kind = readFilter(filter);
        if (!(self.level() instanceof ServerLevel level)) {
            throw new IllegalArgumentException("storage scan needs a server level");
        }

        BlockPos center = self.blockPosition();
        int minCx = (center.getX() - boundedRadius) >> 4;
        int maxCx = (center.getX() + boundedRadius) >> 4;
        int minCz = (center.getZ() - boundedRadius) >> 4;
        int maxCz = (center.getZ() + boundedRadius) >> 4;
        double radiusSqr = (double) boundedRadius * boundedRadius;
        int unloaded = 0;
        List<Hit> hits = new ArrayList<>();

        // Containers and machines are block entities in normal mod APIs. Walking
        // each loaded chunk's BE map avoids a multi-million-block cube scan and
        // never loads or generates a chunk merely to answer a perception query.
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    unloaded++;
                    continue;
                }
                for (BlockPos raw : chunk.getBlockEntities().keySet()) {
                    BlockPos pos = raw.immutable();
                    double distanceSqr = pos.distSqr(center);
                    if (distanceSqr > radiusSqr) continue;
                    try {
                        StorageKinds kinds = Services.CAPS.storageKinds(level, pos);
                        if (kinds.matches(kind)) {
                            hits.add(new Hit(pos, kinds, Math.sqrt(distanceSqr)));
                        }
                    } catch (RuntimeException brokenProvider) {
                        com.dwinovo.numen.core.Constants.LOG.debug(
                                "[numen-storage] capability probe failed at {}: {}",
                                pos.toShortString(), brokenProvider.toString());
                    }
                }
            }
        }

        hits.sort(Comparator.comparingDouble(Hit::distance));
        JsonArray found = new JsonArray();
        int limit = Math.min(hits.size(), MAX_RESULTS);
        for (int i = 0; i < limit; i++) {
            Hit hit = hits.get(i);
            JsonObject item = new JsonObject();
            item.addProperty("x", hit.pos.getX());
            item.addProperty("y", hit.pos.getY());
            item.addProperty("z", hit.pos.getZ());
            item.addProperty("block", BuiltInRegistries.BLOCK.getKey(
                    level.getBlockState(hit.pos).getBlock()).toString());
            item.addProperty("distance", hit.distance);
            JsonArray kinds = new JsonArray();
            if (hit.kinds.items()) kinds.add("items");
            if (hit.kinds.fluids()) kinds.add("fluids");
            if (hit.kinds.energy()) kinds.add("energy");
            item.add("storage_types", kinds);
            found.add(item);
        }

        JsonObject root = new JsonObject();
        root.add("storages", found);
        root.addProperty("total_found", hits.size());
        root.addProperty("truncated", hits.size() > MAX_RESULTS || unloaded > 0);
        root.addProperty("radius_searched", boundedRadius);
        root.addProperty("filter", kind);
        if (unloaded > 0) {
            root.addProperty("note", unloaded + " chunk columns in the radius were not loaded and were"
                    + " not searched; storage there is UNKNOWN, not absent");
        }
        return root.toString();
    }

    private static String readFilter(String filter) {
        if (List.of("items", "fluids", "energy", "all").contains(filter)) return filter;
        throw new IllegalArgumentException(
                "storage_type must be one of [items, fluids, energy, all], got: " + filter);
    }

    private record Hit(BlockPos pos, StorageKinds kinds, double distance) {}
}
