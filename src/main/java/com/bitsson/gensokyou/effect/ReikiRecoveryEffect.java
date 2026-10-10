package com.bitsson.gensokyou.effect;

import com.bitsson.gensokyou.config.GensokyouConfig;
import com.bitsson.gensokyou.spirit.ModAttachments;
import com.bitsson.gensokyou.spirit.SpiritPowerData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

/**
 * 回灵汤：<b>瞬发</b>效果，饮用/喷溅的瞬间一次性注入灵力，不持续回复、无持续时间。
 *
 * <p>每阶注入 {@code 当前最大灵力 × reikiRecoveryPercent}：I 阶 = 10%、II 阶 = 20%。
 * 因为是瞬发效果（{@link #isInstantenous()}），炼药台与炼药仪式都不提供长效档——
 * 时长对瞬发毫无意义。
 *
 * <p><b>只动 {@code current}，绝不动 {@code max} / {@code spiritDamage}</b>：这两个字段是
 * 「阶级台账求和」的单写事实来源（{@code SpiritPowerData.withRecomputedPool}），
 * 任何旁路写入都会让玩家属性永久漂移。故本效果只在 {@link SpiritPowerData#withAddedCurrent}
 * 的口径内工作，由 {@code max} 自动钳住上限；池满时是 no-op。
 */
public class ReikiRecoveryEffect extends MobEffect {

    public ReikiRecoveryEffect() {
        super(MobEffectCategory.BENEFICIAL, 0x7FE8C8);
    }

    @Override
    public boolean isInstantenous() {
        return true;
    }

    @Override
    public void applyInstantenousEffect(@Nullable Entity source, @Nullable Entity indirectSource,
                                        LivingEntity entity, int amplifier, double health) {
        restore(entity, amplifier);
    }

    /**
     * {@code /effect give} 与其它 {@code addEffect} 路径不会走
     * {@link #applyInstantenousEffect}（那只在饮用/投掷药水时触发），这里补一条，
     * 使指令调试与药水口径一致。两条路径互斥，不会重复结算。
     */
    @Override
    public void onEffectStarted(LivingEntity entity, int amplifier) {
        restore(entity, amplifier);
    }

    private static void restore(LivingEntity entity, int amplifier) {
        if (!(entity instanceof ServerPlayer player)) {
            return;
        }
        SpiritPowerData data = ModAttachments.get(player);
        if (data.max() <= 0F || data.current() >= data.max()) {
            return;
        }
        double percent = GensokyouConfig.REIKI_RECOVERY_PERCENT.get();
        float amount = (float) (data.max() * percent * (amplifier + 1));
        if (amount <= 0F) {
            return;
        }
        ModAttachments.set(player, data.withAddedCurrent(amount));
    }
}
