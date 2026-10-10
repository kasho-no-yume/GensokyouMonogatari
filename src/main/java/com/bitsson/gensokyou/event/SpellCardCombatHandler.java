package com.bitsson.gensokyou.event;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.config.GensokyouConfig;
import com.bitsson.gensokyou.entity.spell.DarknessFieldEntity;
import com.bitsson.gensokyou.spirit.ModAttachments;
import com.bitsson.gensokyou.spirit.SpellBuffData;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;

import java.util.List;

/**
 * 玩家符卡战斗钩子（add-player-spellcards 3.1-3.4）：
 * <ul>
 *   <li>鲜花之铠：弹射物伤害抵消 + 花瓣反馈</li>
 *   <li>黑暗结界：玩家主动造成伤害即终止</li>
 *   <li>疫符：免疫新负面 + 回敬伤害者随机负面</li>
 * </ul>
 */
@EventBusSubscriber(modid = Gensokyou.MODID)
public final class SpellCardCombatHandler {

    /** 疫符回敬候选负面池。 */
    private static final List<net.minecraft.core.Holder<MobEffect>> PLAGUE_POOL = List.of(
            MobEffects.POISON, MobEffects.WITHER, MobEffects.WEAKNESS,
            MobEffects.MOVEMENT_SLOWDOWN, MobEffects.DIG_SLOWDOWN, MobEffects.BLINDNESS,
            MobEffects.HUNGER, MobEffects.CONFUSION, MobEffects.DARKNESS);

    private SpellCardCombatHandler() {
    }

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SpellBuffData buffs = player.getData(ModAttachments.SPELL_BUFFS.get());
            long now = player.level().getGameTime();
            Entity direct = event.getSource().getDirectEntity();
            // 鲜花之铠：只挡弹射物来源
            if (direct instanceof Projectile && buffs.hasFlowerArmor(now)) {
                event.setCanceled(true);
                SpellBuffData next = buffs.consumePetal(now);
                ModAttachments.setSpellBuffs(player, next);
                flowerArmorFeedback(player, next.flowerArmorPetals());
                return;
            }
            // 疫符回敬：对造成伤害的实体
            if (buffs.hasPlagueRepay(now)) {
                Entity attacker = event.getSource().getEntity();
                if (attacker instanceof LivingEntity living && living != player) {
                    repayPlague(player, living);
                }
            }
        }
        // 黑暗结界：玩家主动造成伤害即终止
        Entity attacker = event.getSource().getEntity();
        if (attacker instanceof ServerPlayer caster && event.getEntity() != caster) {
            DarknessFieldEntity.terminateFor(caster);
        }
    }

    @SubscribeEvent
    public static void onEffectApplicable(MobEffectEvent.Applicable event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        MobEffectInstance instance = event.getEffectInstance();
        if (instance == null
                || instance.getEffect().value().getCategory() != MobEffectCategory.HARMFUL) {
            return;
        }
        SpellBuffData buffs = player.getData(ModAttachments.SPELL_BUFFS.get());
        if (buffs.hasPlagueRepay(player.level().getGameTime())) {
            event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
            player.displayClientMessage(Component.translatable("msg.gensokyou.plague_repay_resist"), true);
        }
    }

    private static void flowerArmorFeedback(ServerPlayer player, int petalsLeft) {
        if (player.level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                    player.getX(), player.getY() + 1D, player.getZ(),
                    10, 0.4D, 0.5D, 0.4D, 0.02D);
        }
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.7F, 1.5F);
        player.displayClientMessage(
                Component.translatable("msg.gensokyou.flower_armor_block", petalsLeft), true);
    }

    private static void repayPlague(ServerPlayer player, LivingEntity attacker) {
        int existing = 0;
        for (MobEffectInstance inst : attacker.getActiveEffects()) {
            if (inst.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) {
                existing++;
            }
        }
        int count = Math.max(0, GensokyouConfig.PLAGUE_REPAY_DEBUFF_COUNT.get() - existing);
        if (count == 0) {
            return;
        }
        int durationTicks = GensokyouConfig.PLAGUE_REPAY_DEBUFF_DURATION_SECONDS.get() * 20;
        for (int i = 0; i < count; i++) {
            net.minecraft.core.Holder<MobEffect> effect =
                    PLAGUE_POOL.get(player.getRandom().nextInt(PLAGUE_POOL.size()));
            attacker.addEffect(new MobEffectInstance(effect, durationTicks, 0));
        }
        if (player.level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.ITEM_SLIME,
                    attacker.getX(), attacker.getY() + 1D, attacker.getZ(),
                    12, 0.4D, 0.5D, 0.4D, 0.02D);
        }
    }
}
