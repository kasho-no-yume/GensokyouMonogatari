package com.bitsson.gensokyou.event;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.registry.ModDamageTypes;
import com.bitsson.gensokyou.spirit.attr.AttributeKey;
import com.bitsson.gensokyou.spirit.attr.PlayerAttributes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * 修灵馈赠消费点（add-cultivation-gifts）：
 * - 近战附加物理伤害（攻击者为玩家且 {@code player_attack}）；
 * - 物抗：非弹幕伤害的独立乘区 {@code ×(1−物抗)}，豁免斩杀/虚空；弹幕走护壁指数通道不受影响。
 */
@EventBusSubscriber(modid = Gensokyou.MODID)
public final class CultivationGiftEvents {

    private CultivationGiftEvents() {
    }

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        float amount = event.getAmount();

        // 近战附加：先加附加，随后可能被受害者物抗乘算
        if (event.getSource().getEntity() instanceof ServerPlayer attacker
                && event.getSource().is(DamageTypes.PLAYER_ATTACK)) {
            amount += Math.max(0F, PlayerAttributes.finalValue(attacker, AttributeKey.MELEE_DAMAGE));
        }

        // 物抗：非弹幕、非豁免伤害的独立乘区
        if (event.getEntity() instanceof ServerPlayer victim
                && !event.getSource().is(ModDamageTypes.DANMAKU)
                && !event.getSource().is(DamageTypes.GENERIC_KILL)
                && !event.getSource().is(DamageTypes.FELL_OUT_OF_WORLD)) {
            float resist = Math.max(0F, Math.min(1F,
                    PlayerAttributes.finalValue(victim, AttributeKey.PHYS_RESIST)));
            amount *= (1F - resist);
        }

        event.setAmount(amount);
    }
}
