package com.bitsson.gensokyou.danmaku.program.gameplay;

import com.bitsson.gensokyou.danmaku.program.ops.Pattern;
import com.bitsson.gensokyou.danmaku.visual.DanmakuVisualProfile;

/** Resolves presentation only. Selecting a model never changes authority collision geometry. */
public final class ProgramVisuals {
    private static final java.util.Map<String,DanmakuVisualProfile.Profile> MODELS=new java.util.concurrent.ConcurrentHashMap<>(java.util.Map.of(
            "sphere",DanmakuVisualProfile.DEFAULT,"star_prism",DanmakuVisualProfile.STAR_PRISM,"laser",DanmakuVisualProfile.DEFAULT));
    private ProgramVisuals() {}
    public static DanmakuVisualProfile.Profile profile(Pattern.Style style) {
        validate(style);
        return MODELS.get(style.projectile());
    }
    /** Register identically on both sides during setup, before constructing content definitions. */
    public static void register(String id,DanmakuVisualProfile.Profile profile) {
        if (id==null || !id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || MODELS.putIfAbsent(id,java.util.Objects.requireNonNull(profile))!=null)
            throw new IllegalArgumentException("invalid or duplicate visual style: "+id);
    }
    public static int tint(Pattern.Style style) {
        if (style.visual().matches("#[0-9a-fA-F]{6}")) return Integer.parseInt(style.visual().substring(1),16);
        return switch (style.visual()) {
            case "blue" -> 0x4080FF;
            case "gold" -> 0xFFCC40;
            case "red" -> 0xFF4060;
            case "green" -> 0x50DD88;
            case "purple" -> 0xB070FF;
            case "cyan" -> 0x40DDFF;
            case "pink" -> 0xFF80CC;
            case "orange" -> 0xFF9933;
            default -> 0xFFFFFF;
        };
    }
    /** Existing renderer models. Adding a model is an extension; selecting colors is content. */
    public static void validate(Pattern.Style style) {
        if (!MODELS.containsKey(style.projectile()))
            throw new IllegalArgumentException("unknown projectile style: "+style.projectile());
        if (!java.util.Set.of("blue","gold","red","white","green","purple","cyan","pink","orange","default").contains(style.visual())
                && !style.visual().matches("#[0-9a-fA-F]{6}"))
            throw new IllegalArgumentException("unknown color: "+style.visual()+" (use #RRGGBB)");
    }
}
