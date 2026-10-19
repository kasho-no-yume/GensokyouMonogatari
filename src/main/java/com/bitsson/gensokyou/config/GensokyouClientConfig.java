package com.bitsson.gensokyou.config;

import com.bitsson.gensokyou.balance.BalanceNumbers;
import com.bitsson.gensokyou.config.GensokyouServerConfig;
import com.bitsson.gensokyou.config.GensokyouClientConfig;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 客户端设置（config-surface 三分法）：只承载**表现偏好**——画面、HUD、操作便利。
 * 战力数值见 {@code balance/BalanceNumbers}；非战力经济见 {@code GensokyouServerConfig}。
 *
 * <p>本文件按玩家本地生效，MUST NOT 影响服务端判定。逐特效的几何/颜色是代码常量，
 * 只通过画质档整体收口。新增可调项 MUST 先经用户确认。
 */
public final class GensokyouClientConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    /** 镜头抖动倍率（原 fxShatterShakeScale，0 = 关闭，无障碍）。 */
    public static final ModConfigSpec.DoubleValue CAMERA_SHAKE;
    /** HUD 咒符条宽度（原 talismanBarWidth）。 */
    public static final ModConfigSpec.IntValue BOSS_BAR_WIDTH;
    /** 仪式建造器冲突线框的停留秒数（原 ritualBuilderOutlineSeconds）。 */
    public static final ModConfigSpec.IntValue BUILDER_OUTLINE_SECONDS;
    /** 全局特效画质档：off / low / medium / high。 */
    public static final ModConfigSpec.ConfigValue<String> FX_QUALITY;
    /** 超出该距离（格）只绘制低 LOD 特效。 */
    public static final ModConfigSpec.DoubleValue FX_DRAW_DISTANCE;

    public static final ModConfigSpec SPEC;

    static {
        CAMERA_SHAKE = BUILDER.comment(
                        "Camera roll/pitch shake multiplier for barrier shatter (peak roll 15.6 deg at 1.0).",
                        "Set to 0 to disable shake entirely (accessibility). Client-side only.")
                .defineInRange("cameraShake", 1.0D, 0.0D, 3.0D);

        BOSS_BAR_WIDTH = BUILDER.comment("Talisman boss bar: total width in pixels.")
                .defineInRange("bossBarWidth", 182, 120, 400);

        BUILDER_OUTLINE_SECONDS = BUILDER.comment("How long the red conflict outline lingers after a blocked ritual build (seconds).")
                .defineInRange("builderOutlineSeconds", 15, 1, 60);

        FX_QUALITY = BUILDER.comment(
                        "Global ritual/entity FX quality: off | low | medium | high.",
                        "Maps to density / layer counts / particle budgets of every effect; individual",
                        "effect geometry is a code constant and is not exposed.")
                .define("fxQuality", "high");

        FX_DRAW_DISTANCE = BUILDER.comment("Distance in blocks beyond which FX drop to low LOD.")
                .defineInRange("fxDrawDistance", 64.0D, 8.0D, 256.0D);

        SPEC = BUILDER.build();
    }

    private GensokyouClientConfig() {
    }
}
