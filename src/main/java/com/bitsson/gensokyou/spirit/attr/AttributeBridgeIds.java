package com.bitsson.gensokyou.spirit.attr;

import com.bitsson.gensokyou.Gensokyou;
import net.minecraft.resources.ResourceLocation;

/** 原版属性桥的固定 modifier id（幂等重算的锚点）。 */
public final class AttributeBridgeIds {

    public static final ResourceLocation HEALTH_BONUS = Gensokyou.id("attribute.health_bonus");
    public static final ResourceLocation MOVE_SPEED = Gensokyou.id("attribute.move_speed_bonus");
    public static final ResourceLocation JUMP = Gensokyou.id("attribute.jump");

    private AttributeBridgeIds() {
    }
}
