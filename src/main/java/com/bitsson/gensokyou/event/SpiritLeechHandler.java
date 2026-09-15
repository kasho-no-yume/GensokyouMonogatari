package com.bitsson.gensokyou.event;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.config.GensokyouConfig;
import com.bitsson.gensokyou.registry.ModDamageTypes;
import com.bitsson.gensokyou.spirit.ModAttachments;
import com.bitsson.gensokyou.spirit.SpiritPowerData;
import com.bitsson.gensokyou.spirit.attr.AttributeKey;
import com.bitsson.gensokyou.spirit.attr.AttributeMath;
import com.bitsson.gensokyou.spirit.attr.LedgerData;
import com.bitsson.gensokyou.spirit.attr.PlayerAttributes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

/**
 * 灵力汲取（实验件）：玩家弹幕命中后，按实付伤害×转化率回灵，
 * 每结算周期额度封顶（账本），满池自然截断；转化率配置为 0 即全链路失效、零残迹。
 */
@EventBusSubscriber(modid = Gensokyou.MODID)
public final class SpiritLeechHandler {

    private SpiritLeechHandler() {
    }

    @SubscribeEvent
    public static void onDamage(LivingDamageEvent.Post event) {
        if (!event.getSource().is(ModDamageTypes.DANMAKU)) {
            return;
        }
        // 仅主武器弹幕汲取：直接实体须为标记 fromWeapon 的弹（排除符卡/冰锥等其他玩家弹幕来源）
        if (!(event.getSource().getDirectEntity()
                instanceof com.bitsson.gensokyou.entity.AbstractDanmakuProjectile proj
                && proj.isFromWeapon())) {
            return;
        }
        Entity attacker = event.getSource().getEntity();
        if (!(attacker instanceof ServerPlayer player) || player == event.getEntity()) {
            return;
        }
        float rate = AttributeMath.clampPercent(
                PlayerAttributes.finalValue(player, AttributeKey.SPIRIT_LEECH_RATE));
        if (rate <= 0F) {
            return;
        }
        float dealt = event.getNewDamage();
        SpiritPowerData power = ModAttachments.get(player);
        if (dealt <= 0F || power.current() >= power.max()) {
            return;
        }
        LedgerData ledger = player.getData(ModAttachments.SPIRIT_LEECH_LEDGER.get())
                .advance(player.level().getGameTime(),
                        Math.max(1, GensokyouConfig.SETTLE_PERIOD_TICKS.get()),
                        GensokyouConfig.SPIRIT_LEECH_MAX_PER_SECOND.get().floatValue()
                                * Math.max(1, GensokyouConfig.SETTLE_PERIOD_TICKS.get()) / 20F);
        float gain = AttributeMath.leechGain(dealt, rate, ledger.remaining());
        if (gain > 0F) {
            player.setData(ModAttachments.SPIRIT_LEECH_LEDGER.get(), ledger.consume(gain));
            ModAttachments.set(player, power.withAddedCurrent(gain));
        } else {
            player.setData(ModAttachments.SPIRIT_LEECH_LEDGER.get(), ledger);
        }
    }
}
