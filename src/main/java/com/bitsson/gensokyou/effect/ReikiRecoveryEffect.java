package com.bitsson.gensokyou.effect;

import com.bitsson.gensokyou.config.GensokyouConfig;
import com.bitsson.gensokyou.spirit.ModAttachments;
import com.bitsson.gensokyou.spirit.SpiritPowerData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * 回灵汤：持续期间按配置速率向玩家灵力池额外注灵。
 *
 * <p><b>只动 {@code current}，绝不动 {@code max} / {@code spiritDamage}</b>：这两个字段是
 * 「阶级台账求和」的单写事实来源（{@code SpiritPowerData.withRecomputedPool}），
 * 任何旁路写入都会让玩家属性永久漂移。故本效果只在 {@link SpiritPowerData#withAddedCurrent}
 * 的口径内工作，由 {@code max} 自动钳住上限。
 */
public class ReikiRecoveryEffect extends MobEffect {

    public ReikiRecoveryEffect() {
        super(MobEffectCategory.BENEFICIAL, 0x7FE8C8);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        // 每 10 tick 结算一次，与「每秒若干点」的口径对齐（配置以每秒计）。
        return duration % 10 == 0;
    }

    @Override
    public boolean applyEffectTick(net.minecraft.world.entity.LivingEntity entity, int amplifier) {
        if (!(entity instanceof ServerPlayer player)) {
            return true;
        }
        float perSecond = (float) (double) GensokyouConfig.REIKI_RECOVERY_PER_SECOND.get();
        if (perSecond <= 0F) {
            return true;
        }
        // 每 10 tick 结算 => 每秒的 1/2；品质每 +1 加成 +50%
        float amount = perSecond * 0.5F * (1.0F + amplifier * 0.5F);
        SpiritPowerData data = ModAttachments.get(player);
        if (data.current() >= data.max()) {
            return true;
        }
        ModAttachments.set(player, data.withAddedCurrent(amount));
        return true;
    }
}
