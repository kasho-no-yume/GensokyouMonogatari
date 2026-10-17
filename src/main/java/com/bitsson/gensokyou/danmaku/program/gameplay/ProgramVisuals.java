package com.bitsson.gensokyou.danmaku.program.gameplay;

import com.bitsson.gensokyou.danmaku.program.ops.Pattern;
import com.bitsson.gensokyou.danmaku.visual.DanmakuVisualProfile;

/** Resolves presentation only. Selecting a model never changes authority collision geometry. */
public final class ProgramVisuals {
    private ProgramVisuals() {}
    public static DanmakuVisualProfile.Profile profile(Pattern.Style style) {
        return "star_prism".equals(style.projectile()) ? DanmakuVisualProfile.STAR_PRISM : DanmakuVisualProfile.DEFAULT;
    }
    public static int tint(Pattern.Style style) {
        return switch (style.visual()) {
            case "blue" -> 0x4080FF;
            case "gold" -> 0xFFCC40;
            case "red" -> 0xFF4060;
            default -> 0xFFFFFF;
        };
    }
}
