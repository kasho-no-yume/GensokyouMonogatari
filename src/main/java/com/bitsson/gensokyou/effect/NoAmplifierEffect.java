package com.bitsson.gensokyou.effect;

/**
 * 标记接口：本效果没有「强效档」概念，其品质（amplifier）不承载任何机制差异。
 *
 * <p>典型如灵视（显形半径固定）与彼岸花毒（免伤比例固定）——加高品质只会白给一个
 * 「II」且时长反而被强效档缩短，是纯负面。凡实现本接口的效果，
 * {@link com.bitsson.gensokyou.ritual.potion.PotionTierTransform} 在阶级变换时
 * <b>只延长时长、绝不拔高品质</b>，从而与「夜视没有强效档」的原版口径一致。
 *
 * <p>注意：这只是仪式变换侧的兜底。炼药台侧的强效档应由 {@code ModPotions}
 * 直接不注册 {@code strong_<id>} 条目。
 */
public interface NoAmplifierEffect {
}
