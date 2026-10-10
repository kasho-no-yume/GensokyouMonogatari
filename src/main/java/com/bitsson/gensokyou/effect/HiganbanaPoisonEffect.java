package com.bitsson.gensokyou.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * 彼岸花毒：契约型免伤。
 *
 * <p><b>行为全部在事件侧</b>（{@code ModPotionEvents}）：本类只提供分类与配色。
 * <ul>
 *   <li>免伤 80%（等值抗性提升 IV）——刻意<b>不用</b>全免：Resistance V 全免是公认超标杆，
 *       且用户明确否决"抗性超标"。</li>
 *   <li>不挡虚空、不挡 {@code /kill}。</li>
 *   <li>自然结束或被牛奶洗掉 → 立即死亡。</li>
 *   <li>死亡走 {@code setHealth(0) + die()} 而不走 {@code hurt()}，
 *       因此不死图腾不会触发（图腾判定挂在 {@code hurt()} 内）。</li>
 * </ul>
 *
 * <p>本类实现 {@link NoAmplifierEffect}：免伤是固定 80%，品质毫无意义，
 * 故既无强效档，仪式变换也不会给它拔品质。
 */
public class HiganbanaPoisonEffect extends MobEffect implements NoAmplifierEffect {

    public HiganbanaPoisonEffect() {
        super(MobEffectCategory.HARMFUL, 0xE86A6A);
    }
}
