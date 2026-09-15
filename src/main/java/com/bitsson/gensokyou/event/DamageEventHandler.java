package com.bitsson.gensokyou.event;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.config.GensokyouConfig;
import com.bitsson.gensokyou.registry.ModDamageTypes;
import com.bitsson.gensokyou.registry.ModMobEffects;
import com.bitsson.gensokyou.spirit.attr.PlayerAttributes;
import com.bitsson.gensokyou.spirit.attr.AttributeKey;
import com.bitsson.gensokyou.spirit.attr.AttributeMath;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

@EventBusSubscriber(modid = Gensokyou.MODID)
public final class DamageEventHandler {

    private DamageEventHandler() {
    }

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!event.getSource().is(ModDamageTypes.DANMAKU)) {
            return;
        }
        float amount = event.getAmount();

        // mu_power 乘区：顺序保持不变（先于玩家受弹管线）
        MobEffectInstance muPower =
                event.getEntity().getEffect(ModMobEffects.MU_POWER);
        if (muPower != null) {
            int level = muPower.getAmplifier() + 1;
            double numerator = GensokyouConfig.MU_POWER_NUMERATOR.get();
            double denominator = GensokyouConfig.MU_POWER_DENOMINATOR.get();
            amount *= (float) ((numerator + level) / denominator);
        }

        // danmaku_protect 效果系数（既有语义，非玩家行为回归不变）
        float protectFactor = 1F;
        MobEffectInstance protect =
                event.getEntity().getEffect(ModMobEffects.DANMAKU_PROTECT);
        if (protect != null) {
            int level = protect.getAmplifier() + 1;
            if (level >= 10) {
                protectFactor = 0F;    // 完全免疫：短路一切减免/封顶计算
            } else {
                double numerator = GensokyouConfig.PROTECT_REDUCTION_NUMERATOR.get();
                double denominator = GensokyouConfig.PROTECT_REDUCTION_DENOMINATOR.get();
                protectFactor = (float) ((numerator - level) / denominator);
            }
        }

        if (event.getEntity() instanceof ServerPlayer player && protectFactor > 0F) {
            // 玩家受弹属性管线（danmaku-combat 增量）：擦弹 → (减免×护盾)封顶 → 抵抗
            float graze = AttributeMath.clampPercent(
                    PlayerAttributes.finalValue(player, AttributeKey.GRAZE_CHANCE));
            if (AttributeMath.grazeHit(graze, player.getRandom().nextFloat())) {
                event.setAmount(0F);
                return;
            }
            float reduce = Math.max(0F,
                    PlayerAttributes.finalValue(player, AttributeKey.DANMAKU_REDUCE));
            float resist = Math.max(0F,
                    PlayerAttributes.finalValue(player, AttributeKey.DANMAKU_RESIST));
            amount = AttributeMath.resolveIncoming(amount, reduce, protectFactor, resist,
                    GensokyouConfig.DANMAKU_REDUCTION_GLOBAL_CAP.get().floatValue());
        } else {
            amount *= protectFactor;
        }
        event.setAmount(amount);
    }
}
