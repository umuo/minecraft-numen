package com.dwinovo.numen.core.tools.interact;
import com.dwinovo.numen.core.tools.BlockActionOps;

import static com.dwinovo.numen.task.TaskDispatch.*;

import com.dwinovo.numen.agent.tool.Schema;
import com.dwinovo.numen.agent.tool.NumenTool;
import com.dwinovo.numen.entity.NumenPlayer;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.util.Map;
import java.util.function.Consumer;

/** World-action tool (raw NumenTool): aim at a world point and press a mouse button. */
public final class InteractAtTool implements NumenTool {

    private static final Gson GSON = new Gson();
    private final BlockActionOps impl = new BlockActionOps();

    private record Args(String button, Integer x, Integer y, Integer z, Integer hold_ticks,
                        String item_id, boolean sneak) {}

    @Override
    public String name() {
        return "interact_at";
    }

    @Override
    public String description() {
        return "Aim at a world point and press one mouse button — the native crosshair interaction for "
                + "BLOCKS and the AIR (moving entities use interact_entity). It does NOT travel: you must "
                + "ALREADY be within working reach (~4.5 blocks) of the aim point — goto stops you right "
                + "beside a block, which is in reach. Farther away it fails and tells you to goto first. "
                + "Set sneak=true only when the action needs Shift: placing a held block against a chest, "
                + "crafting table, machine, or other usable block; or invoking a mod interaction that "
                + "explicitly requires Shift+left/right click. Ordinary clicks use sneak=false.";
    }

    @Override
    public Map<String, Object> parameterSchema() {
        return Schema.object()
                .enumStr("button", "right = use/activate/throw, left = attack/break.", "left", "right")
                .nullableInteger("x", "Aim X. Null (with y,z null) = use the held item straight ahead (eat/drink).")
                .nullableInteger("y", "Aim Y. Null when aiming forward.")
                .nullableInteger("z", "Aim Z. Null when aiming forward.")
                .nullableInteger("hold_ticks", "0/null = single press; >0 = hold that many ticks; -1 = hold until done/timeout.")
                .nullableString("item_id", "Optional namespaced item to equip-and-use, e.g. minecraft:bonemeal. Null = use what's in hand.")
                .bool("sneak", "Hold Shift for this mouse action. True for Shift+click; false for an ordinary click.")
                .build();
    }

    @Override
    public void onServerCall(String toolCallId, JsonObject args, NumenPlayer companion, Consumer<String> reply) {
        Args a = GSON.fromJson(args, Args.class);
        runSync(companion, impl.interactAt(a.button(), a.x(), a.y(), a.z(), a.hold_ticks(), a.item_id(), a.sneak(),
                ctx(toolCallId, companion)), reply);
    }
}
