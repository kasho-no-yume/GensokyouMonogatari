package com.bitsson.gensokyou.event;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.config.GensokyouConfig;
import com.bitsson.gensokyou.effect.SpiritualSightEffect;
import com.bitsson.gensokyou.registry.ModMobEffects;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * mod 药水效果的行为侧：灵视显形 + 彼岸花毒契约。
 *
 * <p>回灵汤与灵触都是纯 {@link net.minecraft.world.effect.MobEffect} 自足实现
 * （分别写灵力池 / 加属性），不需要事件；这里只处理两件自足实现不了的事：
 * <ol>
 *   <li><b>灵视</b>：给范围内实体挂原版 {@code glowing}——需要实体遍历，属世界侧逻辑；</li>
 *   <li><b>彼岸花毒</b>：免伤要拦 {@link LivingDamageEvent.Pre}、契约死亡要接
 *       {@link MobEffectEvent.Remove}，都是跨实体的事件。</li>
 * </ol>
 */
@EventBusSubscriber(modid = Gensokyou.MODID)
public final class ModPotionEvents {

    /** 灵视描边用的专用队伍：只用来承载 teamColor（染绿）。 */
    private static final String SIGHT_TEAM = "gensokyou_spirit_sight";
    /** 每 N tick 扫一次显形，避免每个持效果玩家每 tick 全场遍历。 */
    private static final int SIGHT_SCAN_INTERVAL = 10;

    private ModPotionEvents() {
    }

    // ------------------------------------------------------------------ 灵视

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !sightActive(player)) {
            return;
        }
        if (player.level().getGameTime() % SIGHT_SCAN_INTERVAL != 0) {
            return;
        }
        double radius = SpiritualSightEffect.radius();
        if (radius <= 0.0D || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        PlayerTeam team = ensureSightTeam(level);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(radius))) {
            if (target == player || !target.isAlive()) {
                continue;
            }
            // 玩家不进队伍（会破坏计分板语义与名字隐藏），只给非玩家实体染绿
            if (!(target instanceof Player)) {
                level.getScoreboard().addPlayerToTeam(target.getStringUUID(), team);
            }
            target.addEffect(new MobEffectInstance(MobEffects.GLOWING,
                    SIGHT_SCAN_INTERVAL + 20, 0, false, false, false));
        }
    }

    private static PlayerTeam ensureSightTeam(ServerLevel level) {
        Scoreboard board = level.getScoreboard();
        PlayerTeam team = board.getPlayerTeam(SIGHT_TEAM);
        if (team == null) {
            team = board.addPlayerTeam(SIGHT_TEAM);
        }
        team.setColor(ChatFormatting.GREEN);
        return team;
    }

    // ------------------------------------------------------------------ 彼岸花毒免伤

    /**
     * 80% 免伤 = 等值抗性提升 IV。
     *
     * <p><b>为什么不用 {@code ResistanceEffect} 而直接改伤害</b>：给一个自定义 effect 挂
     * {@code MobEffects.DAMAGE_RESISTANCE} 会同时引入原版抗性的图标与本地化，且品质阶跃
     * 是 20% 硬分档；直接乘算落在 {@code Pre}（护甲/附魔计算之前）可精确控制成
     * 配置值，也便于日后独立调整。
     */
    @SubscribeEvent
    public static void onLivingDamagePre(LivingDamageEvent.Pre event) {
        if (event.getEntity().getEffect(ModMobEffects.HIGANBANA_POISON) == null) {
            return;
        }
        if (isBypassed(event.getSource())) {
            return;
        }
        event.setNewDamage((float) (event.getNewDamage()
                * GensokyouConfig.HIGANBANA_POISON_DAMAGE_SCALE.get()));
    }

    /** 虚空与 {@code /kill}（genericKill）刻意不纳入契约。 */
    private static boolean isBypassed(net.minecraft.world.damagesource.DamageSource source) {
        if (source == null) {
            return true;
        }
        return source.is(net.minecraft.world.damagesource.DamageTypes.FELL_OUT_OF_WORLD)
                || source.is(net.minecraft.world.damagesource.DamageTypes.GENERIC_KILL);
    }

    // ------------------------------------------------------------------ 彼岸花毒契约死亡

    /**
     * 效果被移除（自然到期 / 牛奶 / 指令）即执行契约。
     *
     * <p><b>为什么不用 {@code hurt()}</b>：不死图腾的判定挂在 {@code LivingEntity#hurt} 内部，
     * 走伤害路径会被图腾救回。这里直接 {@code setHealth(0) + die()}，
     * 完全不进 {@code hurt()}，图腾无从触发。{@code setHealth(0)} 会让 {@code isAlive()}
     * 转假，因此效果清理触发的第二次 {@code Remove} 会被上面的存活判重挡掉，不会递归。
     */
    @SubscribeEvent
    public static void onEffectRemoved(MobEffectEvent.Remove event) {
        if (!event.getEffect().is(ModMobEffects.HIGANBANA_POISON)) {
            return;
        }
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide || !entity.isAlive()) {
            return;
        }
        entity.setHealth(0.0F);
        entity.die(entity.damageSources().starve());
    }

    /** 供调试读取：当前是否应显形。 */
    public static boolean sightActive(Player player) {
        MobEffectInstance sight = player.getEffect(ModMobEffects.SPIRITUAL_SIGHT);
        return sight != null && !sight.endsWithin(1);
    }
}
