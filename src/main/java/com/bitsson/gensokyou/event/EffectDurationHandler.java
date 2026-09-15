package com.bitsson.gensokyou.event;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.spirit.attr.AttributeKey;
import com.bitsson.gensokyou.spirit.attr.AttributeMath;
import com.bitsson.gensokyou.spirit.attr.PlayerAttributes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;

/**
 * 强效延长 / 韧性（player-attribute-suite D7）：玩家被施加 MobEffect 时统一折算时长。
 * 正面 ×(1+强效延长)（已裁决惠及降神变身 T 秒——变身走同一施加入口），
 * 负面 ×(1−韧性)，只缩时长不免疫。
 *
 * <p>实现：1.21.1 的 MobEffectInstance 无公开 setDuration，故在 Added 处以缩放副本
 * 重走 addEffect——原版 merge 取 max(现值, 缩放值)，净效果即时长被拉长；
 * 防重入旗标避免缩放副本再次触发折算。无限/0 时长不动。
 */
@EventBusSubscriber(modid = Gensokyou.MODID)
public final class EffectDurationHandler {

    private static boolean scaling = false;

    private EffectDurationHandler() {
    }

    @SubscribeEvent
    public static void onEffectAdded(MobEffectEvent.Added event) {
        if (scaling || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        MobEffectInstance instance = event.getEffectInstance();
        if (instance == null || instance.isInfiniteDuration() || instance.getDuration() <= 0) {
            return;
        }
        MobEffectCategory category = instance.getEffect().value().getCategory();
        float multiplier = switch (category) {
            case BENEFICIAL -> 1F + Math.max(0F, PlayerAttributes.finalValue(player, AttributeKey.BUFF_EXTEND));
            case HARMFUL -> Math.max(0.1F, 1F - AttributeMath.clampPercent(
                    PlayerAttributes.finalValue(player, AttributeKey.TENACITY)));
            default -> 1F;
        };
        if (multiplier == 1F) {
            return;
        }
        int original = instance.getDuration();
        int scaled = AttributeMath.scaledDuration(original, multiplier);
        if (scaled == original) {
            return;
        }
        MobEffectInstance copy = new MobEffectInstance(instance.getEffect(), scaled,
                instance.getAmplifier(), instance.isAmbient(), instance.isVisible(), instance.showIcon());
        scaling = true;
        try {
            if (multiplier > 1F) {
                // 延长：重走 addEffect，原版 merge 取 max，净效果=时长被拉长
                player.addEffect(copy, event.getEffectSource());
            } else {
                // 缩减：韧性对负面是"封顶"语义，已存更长时长时移除后以缩放值重挂
                MobEffectInstance stored = player.getEffect(instance.getEffect());
                if (stored != null && stored.getDuration() > scaled) {
                    player.removeEffect(instance.getEffect());
                    player.addEffect(copy, event.getEffectSource());
                }
            }
        } finally {
            scaling = false;
        }
    }
}
