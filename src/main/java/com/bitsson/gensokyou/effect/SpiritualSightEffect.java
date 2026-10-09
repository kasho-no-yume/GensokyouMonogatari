package com.bitsson.gensokyou.effect;

import com.bitsson.gensokyou.config.GensokyouConfig;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * 灵视：范围内的实体以绿色发光轮廓渲染，可穿墙看见。
 *
 * <p><b>为什么用原版 {@code glowing} + 队伍色，而不是自己写渲染器</b>：原版发光的描边
 * 本来就穿墙可见，颜色由实体所属队伍的 {@code teamColor} 决定。给实体挂一个短时效的
 * {@code MobEffects.GLOWING}，再通过一个专用队伍把描边染绿，即可零渲染成本拿到
 * 「绿色灵气缭绕」的效果。真正的显形扫描由 {@link com.bitsson.gensokyou.event.ModPotionEvents} 驱动。
 */
public class SpiritualSightEffect extends MobEffect {

    public SpiritualSightEffect() {
        super(MobEffectCategory.BENEFICIAL, 0x7FE86A);
    }

    /** 显形半径（格），供扫描器读取；0 或负数表示关闭。 */
    public static double radius() {
        return Math.max(0.0D, GensokyouConfig.SPIRITUAL_SIGHT_RADIUS.get());
    }
}
