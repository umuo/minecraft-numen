package com.dwinovo.numen.core.tools.work;

import com.dwinovo.numen.agent.tool.NumenTool;
import com.dwinovo.numen.agent.tool.Schema;
import com.dwinovo.numen.core.assist.AssistConfig;
import com.dwinovo.numen.core.assist.AssistRegistry;
import com.dwinovo.numen.core.task.move.FollowTaskRecord;
import com.dwinovo.numen.entity.NumenPlayer;
import com.dwinovo.numen.task.CompanionTickDispatcher;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraft.network.chat.Component;

import java.util.Map;
import java.util.function.Consumer;

/** 开关和配置长期协助模式;不占身体。 */
public final class AssistOwnerTool implements NumenTool {

    private static final Gson GSON = new Gson();

    private record Args(Boolean enabled, Boolean mining, Boolean combat, Boolean clearing,
                        Integer radius, Integer keep_distance) {}

    @Override
    public String name() {
        return "assist_owner";
    }

    @Override
    public String description() {
        return "Configure your persistent owner-assist mode. When enabled and you have no explicit "
                + "job, you follow your owner and help only in high-confidence situations: attack the "
                + "hostile mob they attack, mine a small bounded vein/work face after observing mining, "
                + "and clear nearby grass after observing repeated weeding. Placing blocks is treated as "
                + "building: you step aside and DO NOT copy it. Ambiguous actions are ignored. Explicit "
                + "tools/jobs always take priority; this mode resumes when they finish. Settings survive "
                + "server restarts. Set enabled=false to pause all following/help, or keep enabled=true "
                + "and turn mining/combat/clearing off for follow-only mode. Omit every argument to read "
                + "the current settings.";
    }

    @Override
    public Map<String, Object> parameterSchema() {
        return Schema.object()
                .optionalBool("enabled", "Turn the persistent assist mode on or off.")
                .optionalBool("mining", "Help with ore veins and repeated natural-rock excavation.")
                .optionalBool("combat", "Help attack hostile mobs the owner attacks.")
                .optionalBool("clearing", "Help clear whitelisted grass/fern-like plants after repeated weeding.")
                .optionalInteger("radius", "Maximum work radius around the owner.",
                        AssistConfig.MIN_RADIUS, AssistConfig.MAX_RADIUS)
                .optionalInteger("keep_distance", "Normal following distance in blocks.",
                        AssistConfig.MIN_KEEP_DISTANCE, AssistConfig.MAX_KEEP_DISTANCE)
                .build();
    }

    @Override
    public void onServerCall(String toolCallId, JsonObject args, NumenPlayer self,
                             Consumer<String> reply) {
        Args a = GSON.fromJson(args, Args.class);
        AssistRegistry registry = AssistRegistry.get(self.level().getServer());
        AssistConfig old = registry.get(self.getUUID());
        boolean readOnly = a == null || (a.enabled() == null && a.mining() == null
                && a.combat() == null && a.clearing() == null
                && a.radius() == null && a.keep_distance() == null);
        AssistConfig now = readOnly ? old : new AssistConfig(
                a.enabled() == null ? old.enabled() : a.enabled(),
                a.mining() == null ? old.mining() : a.mining(),
                a.combat() == null ? old.combat() : a.combat(),
                a.clearing() == null ? old.clearing() : a.clearing(),
                a.radius() == null ? old.radius() : a.radius(),
                a.keep_distance() == null ? old.keepDistance() : a.keep_distance());
        if (!readOnly && !now.equals(old)) {
            registry.put(self.getUUID(), now);
            // 普通 follow 是永不腾槽的显式常驻任务。开启协助时它与新模式语义重叠,
            // 不撤掉就会让“已开启”永远没有机会运行；其他显式工作则照常做完再恢复协助。
            if (now.enabled()
                    && CompanionTickDispatcher.currentTaskFor(self.getUUID()) instanceof FollowTaskRecord) {
                CompanionTickDispatcher.stopActive(self, "assist mode replaces plain follow");
            }
            var owner = self.resolveOwnerPlayer();
            if (owner != null) {
                owner.sendSystemMessage(Component.literal("🤝 " + self.getName().getString()
                        + " 协助模式:" + mode(now)));
            }
        }

        JsonObject out = new JsonObject();
        out.addProperty("success", true);
        out.addProperty("enabled", now.enabled());
        out.addProperty("mode", mode(now));
        out.addProperty("mining", now.mining());
        out.addProperty("combat", now.combat());
        out.addProperty("clearing", now.clearing());
        out.addProperty("building", false);
        out.addProperty("radius", now.radius());
        out.addProperty("keep_distance", now.keepDistance());
        out.addProperty("message", now.enabled()
                ? "assist mode is active whenever no explicit job owns the body"
                : "assist mode is paused");
        reply.accept(out.toString());
    }

    private static String mode(AssistConfig config) {
        if (!config.enabled()) return "已暂停";
        return config.mining() || config.combat() || config.clearing()
                ? "自动协助" : "仅跟随";
    }
}
