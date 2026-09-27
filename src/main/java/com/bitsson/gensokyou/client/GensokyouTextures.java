package com.bitsson.gensokyou.client;

import com.bitsson.gensokyou.Gensokyou;
import net.minecraft.resources.ResourceLocation;

public final class GensokyouTextures {

    public static final ResourceLocation DANMAKU = entity("danmaku");
    public static final ResourceLocation SPHERE_DANMAKU = entity("sphere_danmaku");
    public static final ResourceLocation KNIFE_DANMAKU = entity("knife_danmaku");
    public static final ResourceLocation TALISMAN_DANMAKU = entity("talisman_danmaku");
    public static final ResourceLocation LASER_DANMAKU = entity("laser_danmaku");
    public static final ResourceLocation ORBIT_ORB = entity("orbit_orb");
    public static final ResourceLocation FAIRY = entity("fairy");
    public static final ResourceLocation BIG_FAIRY = entity("big_fairy");
    public static final ResourceLocation FLANDRE = entity("flandre");
    public static final ResourceLocation RINNOSUKE = entity("rinnosuke");
    public static final ResourceLocation SUKIMA = entity("sukima");
    public static final ResourceLocation SUKIMA_PORTAL = entity("sukima_portal");
    public static final ResourceLocation CRYSTAL = entity("crystal");

    /**
     * 「结界崩解」光斑粒子贴图：柔和的径向白色光斑。
     *
     * <p>与 {@link #DUST} 用的尘埃点不同——本图中心近实、边缘平滑归零，
     * 半透明混合后大量叠加才能积成「一团光」而非「一堆小点」。
     */
    public static final ResourceLocation SHATTER_GLOW = particle("shatter_glow");

    private GensokyouTextures() {
    }

    private static ResourceLocation particle(String name) {
        return ResourceLocation.fromNamespaceAndPath(Gensokyou.MODID, "textures/particle/" + name + ".png");
    }

    private static ResourceLocation entity(String name) {
        return ResourceLocation.fromNamespaceAndPath(Gensokyou.MODID, "textures/entity/" + name + ".png");
    }
}
