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

    private GensokyouTextures() {
    }

    private static ResourceLocation entity(String name) {
        return ResourceLocation.fromNamespaceAndPath(Gensokyou.MODID, "textures/entity/" + name + ".png");
    }
}
