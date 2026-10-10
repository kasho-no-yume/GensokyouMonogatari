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
    public static final ResourceLocation KITSUNEBI = entity("kitsunebi");
    public static final ResourceLocation NOMEN_MASK = entity("nomen_mask");
public static final ResourceLocation CRYSTAL = entity("crystal");
    /** 式神狐火 / 丝弹的通用发光贴图（add-player-spellcards）。 */
    public static final ResourceLocation FOX_FIRE = entity("fox_fire");

    /**
     * 「结界崩解」光斑粒子贴图：柔和的径向白色光斑。
     *
     * <p>与 {@link #DUST} 用的尘埃点不同——本图中心近实、边缘平滑归零，
     * 半透明混合后大量叠加才能积成「一团光」而非「一堆小点」。
     */
    public static final ResourceLocation SHATTER_GLOW = particle("shatter_glow");

    /** 玩家符卡 FX（add-player-spellcards）。 */
    public static final ResourceLocation MAGIC_CIRCLE = fx("magic_circle");
    public static final ResourceLocation SPIDER_WEB = fx("spider_web");
    /** 灰度火焰条带（与水晶体共用），着蓝紫色即式神狐火 / 粉色即疗愈光墙。 */
    public static final ResourceLocation AURA_FLAME = fx("aura_flame");

    private GensokyouTextures() {
    }

    private static ResourceLocation fx(String name) {
        return ResourceLocation.fromNamespaceAndPath(Gensokyou.MODID, "textures/fx/" + name + ".png");
    }

    private static ResourceLocation particle(String name) {
        return ResourceLocation.fromNamespaceAndPath(Gensokyou.MODID, "textures/particle/" + name + ".png");
    }

    private static ResourceLocation entity(String name) {
        return ResourceLocation.fromNamespaceAndPath(Gensokyou.MODID, "textures/entity/" + name + ".png");
    }
}
