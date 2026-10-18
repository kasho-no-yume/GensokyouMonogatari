package com.bitsson.gensokyou.danmaku.visual;

/** Shared entity/program hidden-state policy. Alpha-blended bodies preserve their RGB. */
public final class DanmakuHiddenStyle {
    public static final float LASER_DIM=.18F;
    private DanmakuHiddenStyle() {}
    public static double bodyAlpha(DanmakuVisualProfile.Profile profile) { return profile.hiddenAlpha()/255.0; }
}
