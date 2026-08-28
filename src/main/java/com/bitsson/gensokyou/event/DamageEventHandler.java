package com.bitsson.gensokyou.event;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.config.GensokyouConfig;
import com.bitsson.gensokyou.registry.ModDamageTypes;
import com.bitsson.gensokyou.registry.ModMobEffects;
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

        MobEffectInstance muPower =
                event.getEntity().getEffect(ModMobEffects.MU_POWER);
        if (muPower != null) {
            int level = muPower.getAmplifier() + 1;
            double numerator = GensokyouConfig.MU_POWER_NUMERATOR.get();
            double denominator = GensokyouConfig.MU_POWER_DENOMINATOR.get();
            amount *= (float) ((numerator + level) / denominator);
        }

        MobEffectInstance protect =
                event.getEntity().getEffect(ModMobEffects.DANMAKU_PROTECT);
        if (protect != null) {
            int level = protect.getAmplifier() + 1;
            if (level >= 10) {
                amount = 0F;
            } else {
                double numerator = GensokyouConfig.PROTECT_REDUCTION_NUMERATOR.get();
                double denominator = GensokyouConfig.PROTECT_REDUCTION_DENOMINATOR.get();
                amount *= (float) ((numerator - level) / denominator);
            }
        }
        event.setAmount(amount);
    }
}
