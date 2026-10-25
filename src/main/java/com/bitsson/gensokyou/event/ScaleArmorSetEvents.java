package com.bitsson.gensokyou.event;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.item.equipment.DragonScaleSetBonus;
import com.bitsson.gensokyou.item.equipment.MermaidScaleSetBonus;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * 人鱼鳞 / 龙鳞功能甲的套装行为侧。
 *
 * <p><b>人鱼鳞（全套）</b>：水下呼吸常驻续期；夜视只在 {@code isUnderWater()} 时续期
 * 一份 5 秒的效果——出水后要么下一轮复算就清掉（短效果），要么自然在 5 秒内消退
 * （design 允许的降级上限）。
 *
 * <p><b>龙鳞（全套）</b>：{@code #minecraft:is_fire} 伤害直接取消（Fire Resistance 语义，
 * 含岩浆伤害、不含岩浆的物理困住/窒息）；{@code KNOCKBACK_RESISTANCE = 1.0} 由
 * {@link DragonScaleSetBonus#syncKnockback} 每 tick 幂等同步。
 *
 * <p><b>为什么不区分「自己给的效果」</b>：原版 {@code addEffect} 只在"更好"时才覆盖，
 * 所以玩家自己喝的药水永远不会被这里的短效果截断；清理侧只动时长不超过本类续期值的条目，
 * 即只可能是我们挂的那一份。
 */
@EventBusSubscriber(modid = Gensokyou.MODID)
public final class ScaleArmorSetEvents {

    private ScaleArmorSetEvents() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        // 抗击退是属性修饰，幂等同步，每 tick 都复算（死亡/换维度后能自愈）
        DragonScaleSetBonus.syncKnockback(player, DragonScaleSetBonus.hasFullSet(player));

        // 套装判定每 tick 做（4 件物品检查，代价可忽略）：缺件时当 tick 就撤掉效果，
        // 满足「任一件脱下或替换立即移除」。
        if (!MermaidScaleSetBonus.hasFullSet(player)) {
            clearShort(player, MobEffects.WATER_BREATHING, MermaidScaleSetBonus.WATER_BREATHING_TICKS);
            clearShort(player, MobEffects.NIGHT_VISION, MermaidScaleSetBonus.NIGHT_VISION_TICKS);
            return;
        }
        if (player.tickCount % MermaidScaleSetBonus.TICK_INTERVAL != 0) {
            return;
        }
        player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING,
                MermaidScaleSetBonus.WATER_BREATHING_TICKS, 0, false, false, true));
        if (player.isUnderWater()) {
            player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION,
                    MermaidScaleSetBonus.NIGHT_VISION_TICKS, 0, false, false, true));
        } else {
            // 出水即撤（只撤我们那份短效果）
            clearShort(player, MobEffects.NIGHT_VISION, MermaidScaleSetBonus.NIGHT_VISION_TICKS);
        }
    }

    /** 龙鳞全套：火焰类伤害全免（Fire Resistance 语义）。 */
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!event.getSource().is(DamageTypeTags.IS_FIRE)) {
            return;
        }
        if (DragonScaleSetBonus.hasFullSet(player)) {
            event.setCanceled(true);
        }
    }

    /** 移除时长不超过 {@code maxTicks} 的同效果实例（即本套装挂的那一份）。 */
    private static void clearShort(ServerPlayer player, Holder<MobEffect> effect, int maxTicks) {
        MobEffectInstance current = player.getEffect(effect);
        if (current == null) {
            return;
        }
        if (current.getDuration() > 0 && current.getDuration() <= maxTicks) {
            player.removeEffect(effect);
        }
    }
}
