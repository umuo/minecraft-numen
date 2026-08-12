package com.dwinovo.numen.core.tools.perception;

import com.dwinovo.numen.agent.tool.NumenTool;
import com.dwinovo.numen.agent.tool.Schema;
import com.dwinovo.numen.core.tools.StorageScanOps;
import com.dwinovo.numen.entity.NumenPlayer;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.util.Map;
import java.util.function.Consumer;

/** Query tool: discover vanilla or modded storage without already knowing its block id. */
public final class ScanStorageTool implements NumenTool {

    private static final Gson GSON = new Gson();
    private final StorageScanOps impl = new StorageScanOps();

    private record Args(int radius, String storage_type) {}

    @Override
    public String name() {
        return "scan_storage";
    }

    @Override
    public String description() {
        return "Find nearby storage blocks by what they actually expose, without knowing their block ids. "
                + "Use items to find vanilla chests/barrels AND modded crates, vaults, drawers, Create or "
                + "other machines with inventories; fluids for tanks; energy for batteries; all for any. "
                + "Results include exact block id, coordinates, distance and storage_types, nearest first. "
                + "After finding one, use inspect_block_storage to read it, or goto + interact_at to open "
                + "its GUI. Only loaded chunks are searched; the note identifies unknown unloaded coverage.";
    }

    @Override
    public Map<String, Object> parameterSchema() {
        return Schema.object()
                .integer("radius", "Search radius in blocks. Range [1, 64].", 1, 64)
                .enumStr("storage_type", "Storage capability to find.",
                        "items", "fluids", "energy", "all")
                .build();
    }

    @Override
    public void onServerCall(String toolCallId, JsonObject args, NumenPlayer self,
                             Consumer<String> reply) {
        Args parsed = GSON.fromJson(args, Args.class);
        reply.accept(impl.scanNearby(parsed.radius(), parsed.storage_type(), self));
    }
}
