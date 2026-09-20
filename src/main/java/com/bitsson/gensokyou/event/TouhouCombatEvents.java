package com.bitsson.gensokyou.event;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.config.GensokyouConfig;
import com.bitsson.gensokyou.entity.TouhouMonster;
import com.bitsson.gensokyou.registry.ModDamageTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;

/**
 * 东方怪的战斗交互收口：
 * <ul>
 *   <li>非弹幕伤害的天然减免（{@link TouhouMonster}）</li>
 *   <li>弹幕命中盾牌后的破盾</li>
 * </ul>
 */
@EventBusSubscriber(modid = Gensokyou.MODID)
public final class TouhouCombatEvents {

    private TouhouCombatEvents() {
    }

    /**
     * 东方系怪物对非 danmaku 伤害减免；{@code /kill} 与虚空豁免。
     */
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof TouhouMonster)) {
            return;
        }
        DamageSource source = event.getSource();
        if (source.is(ModDamageTypes.DANMAKU)) {
            return;
        }
        if (source.is(DamageTypes.GENERIC_KILL) || source.is(DamageTypes.FELL_OUT_OF_WORLD)) {
            return;
        }
        float resist = GensokyouConfig.TOUHOU_NON_DANMAKU_RESIST.get().floatValue();
        if (resist > 0.0F) {
            event.setAmount(event.getAmount() * (1.0F - resist));
        }
    }

    /**
     * 弹幕命中盾牌：该击照常挡下，随后禁用盾牌一段时长。
     */
    @SubscribeEvent
    public static void onShieldBlock(LivingShieldBlockEvent event) {
        if (!event.getBlocked() || !event.getDamageSource().is(ModDamageTypes.DANMAKU)) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack shield = player.getUseItem();
        if (shield.isEmpty()) {
            return;
        }
        int ticks = GensokyouConfig.DANMAKU_SHIELD_DISABLE_TICKS.get();
        if (ticks > 0) {
            player.getCooldowns().addCooldown(shield.getItem(), ticks);
        }
        player.stopUsingItem();
    }
}
