package com.dwinovo.numen.core.tools.work;

import com.dwinovo.numen.agent.tool.NumenTool;
import com.dwinovo.numen.agent.tool.Schema;
import com.dwinovo.numen.core.task.farm.FarmCropsTaskRecord;
import com.dwinovo.numen.core.tools.FarmOps;
import com.dwinovo.numen.entity.NumenPlayer;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.util.Map;
import java.util.function.Consumer;

import static com.dwinovo.numen.task.TaskDispatch.ctx;
import static com.dwinovo.numen.task.TaskDispatch.setTask;

/** Harvest mature crops and replant a bounded field without one bad cell stopping the rest. */
public final class FarmCropsTool implements NumenTool {

    private static final Gson GSON = new Gson();
    private final FarmOps impl = new FarmOps();

    private record Args(String crop_id,
                        int x1, int y1, int z1,
                        int x2, int y2, int z2) {}

    @Override
    public String name() {
        return FarmCropsTaskRecord.TOOL_NAME;
    }

    @Override
    public String description() {
        return "Harvest and replant one bounded crop field as ONE background task. Mature crops are "
                + "harvested and immediately replanted first; their surplus seeds are then available for "
                + "empty valid farmland. Immature crops and obstructed cells are skipped independently, so "
                + "one occupied cell never stops the rest of the field. crop_id is the planted BLOCK id "
                + "(minecraft:wheat, minecraft:carrots, minecraft:potatoes, etc.), not the seed item. Give "
                + "the two opposite corners of the crop layer or volume. BACKGROUND: after acceptance wait "
                + "for task_finished; do not resend while it is running.";
    }

    @Override
    public Map<String, Object> parameterSchema() {
        return Schema.object()
                .string("crop_id", "Namespaced planted crop block id, e.g. minecraft:wheat or minecraft:carrots.")
                .integer("x1", "First corner X of the field.")
                .integer("y1", "First corner Y of the crop layer.")
                .integer("z1", "First corner Z of the field.")
                .integer("x2", "Opposite corner X of the field.")
                .integer("y2", "Opposite corner Y of the crop layer.")
                .integer("z2", "Opposite corner Z of the field.")
                .build();
    }

    @Override
    public void onServerCall(String toolCallId, JsonObject args, NumenPlayer companion,
                             Consumer<String> reply) {
        Args a = GSON.fromJson(args, Args.class);
        setTask(companion, impl.farmCrops(a.crop_id(), a.x1(), a.y1(), a.z1(),
                a.x2(), a.y2(), a.z2(), companion, ctx(toolCallId, companion)), args, reply);
    }
}
