package com.dwinovo.numen.core.assist;

/**
 * 一只同伴的协助偏好。它是长期设置,不是一次任务:显式任务可以暂时盖住它,
 * 任务结束后协助自动回来。
 */
public record AssistConfig(boolean enabled, boolean mining, boolean combat,
                           boolean clearing, int radius, int keepDistance) {

    public static final int MIN_RADIUS = 4;
    public static final int MAX_RADIUS = 16;
    public static final int MIN_KEEP_DISTANCE = 2;
    public static final int MAX_KEEP_DISTANCE = 8;

    public static final AssistConfig DEFAULT = new AssistConfig(
            false, true, true, true, 10, 4);

    public AssistConfig {
        radius = Math.clamp(radius, MIN_RADIUS, MAX_RADIUS);
        keepDistance = Math.clamp(keepDistance, MIN_KEEP_DISTANCE, MAX_KEEP_DISTANCE);
    }
}
