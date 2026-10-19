package com.bitsson.gensokyou.spirit;

import com.bitsson.gensokyou.balance.BalanceNumbers;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.entity.OrbitYinYangOrb;
import com.bitsson.gensokyou.entity.SphereDanmaku;
import com.bitsson.gensokyou.entity.spell.DarknessFieldEntity;
import com.bitsson.gensokyou.entity.spell.FlowerGardenEntity;
import com.bitsson.gensokyou.entity.spell.FoxServantEntity;
import com.bitsson.gensokyou.entity.spell.SilkProjectileEntity;
import com.bitsson.gensokyou.spirit.grace.GraceService;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 符卡效果注册表：物品符卡与已学技能槽共用同一执行器。
 * 槽位顺序固定，客户端 HUD 与服务端校验均依赖该顺序。
 *
 * <p>品质 × 灵力强度缩放（player-spellcard-quality）：每条 {@link Entry} 携带
 * {@code Base}/{@code α_card}，{@link #perform}（已学，动态读灵力强度）与
 * {@link #performItem}（道具，固定取 S_std(品)）统一经 {@link SpellCardScaling} 求值，
 * 把结果放进 {@link CastContext} 交给效果。
 */
public final class SpellCardEffects {

    public static final String MUSOU_FUUIN = "musou_fuuin";
    public static final String ICICLE_FALL = "icicle_fall";
    public static final String LIGHT_REFLECT = "light_reflect";

    // 前期 BOSS 主题玩家符卡（add-player-spellcards）
    public static final String HEALING_GARDEN = "healing_garden";
    public static final String FLOWER_ARMOR = "flower_armor";
    public static final String DEMARCATION = "demarcation";
    public static final String SPIDER_WEB = "spider_web";
    public static final String PLAGUE_REPAY = "plague_repay";
    public static final String FOX_SERVANT = "fox_servant";

    /**
     * 卡注册表顺序（旧"槽位=固定卡"时代的槽序，v2 起仅作旧档迁移映射；
     * 槽位本体已与卡牌解绑，见 {@link SkillStateData}）。
     */
    public static final String[] SLOT_ORDER = {MUSOU_FUUIN, ICICLE_FALL, LIGHT_REFLECT};

    /** 符卡效果：一次性投放，持续型落宿主实体/附件（见各效果实现）。 */
    @FunctionalInterface
    public interface SpellEffect {
        void cast(CastContext ctx);
    }

    /**
     * 一次施放的上下文。
     *
     * @param magnitude 经缩放求值后的效果量（伤害/回血/挡弹数等；固定参数卡可忽略）
     * @param quality   品阶 1..5（道具=物品品；已学=施放者当前阶级）
     * @param itemForm  道具形态（固定数值）还是已学形态（动态）
     */
    public record CastContext(Level level, Player player, double magnitude, int quality, boolean itemForm) {

        /** 服务端施放者（效果实现只在服务端跑，正常非 null）。 */
        public ServerPlayer serverPlayer() {
            return player instanceof ServerPlayer sp ? sp : null;
        }
    }

    /**
     * @param themeColor 卡面主题色（ARGB）——物品图标纹章层 tint 与 HUD 强调色共用。
     * @param base       品 1 / S=1 基准值。
     * @param alpha      该卡自己的缩放指数（逐卡独立）。
     */
    public record Entry(SpellEffect effect,
                        Supplier<Integer> spCost, Supplier<Integer> cooldownTicks,
                        int themeColor, Supplier<Double> base, Supplier<Double> alpha) {
    }

    private static final Map<String, Entry> REGISTRY = new HashMap<>();

    static {
        // 既有三卡：α=0 保持行为不变（视为品 1 道具卡，迁移条款）
        register(MUSOU_FUUIN, SpellCardEffects::performMusouFuuin,
                () -> BalanceNumbers.SKILL_MUSOU_SP_COST,
                () -> BalanceNumbers.SKILL_MUSOU_COOLDOWN,
                0xFFF44336, () -> 0D, () -> 0D);   // 无想封印：红
        register(ICICLE_FALL, SpellCardEffects::performIcicleFall,
                () -> BalanceNumbers.SKILL_ICICLE_SP_COST,
                () -> BalanceNumbers.SKILL_ICICLE_COOLDOWN,
                0xFF4FC3F7, () -> 0D, () -> 0D);   // 冰符：冰蓝
        register(LIGHT_REFLECT, SpellCardEffects::performNoop,
                () -> 5, () -> 60,
                0xFFFFD54F, () -> 0D, () -> 0D);   // 光反（占位）：金

        // 前期 BOSS 主题玩家符卡（add-player-spellcards）
        register(HEALING_GARDEN, SpellCardEffects::performHealingGarden,
                () -> BalanceNumbers.HEALING_GARDEN_SP_COST,
                () -> BalanceNumbers.HEALING_GARDEN_COOLDOWN,
                0xFFF8BBD0, () -> BalanceNumbers.HEALING_GARDEN_BASE,
                () -> BalanceNumbers.HEALING_GARDEN_ALPHA);
        register(FLOWER_ARMOR, SpellCardEffects::performFlowerArmor,
                () -> BalanceNumbers.FLOWER_ARMOR_SP_COST,
                () -> BalanceNumbers.FLOWER_ARMOR_COOLDOWN,
                0xFFF48FB1, () -> 0D, () -> 0D);
        register(DEMARCATION, SpellCardEffects::performDemarcation,
                () -> BalanceNumbers.DEMARCATION_SP_COST,
                () -> BalanceNumbers.DEMARCATION_COOLDOWN,
                0xFF311B92, () -> 0D, () -> BalanceNumbers.DEMARCATION_ALPHA);
        register(SPIDER_WEB, SpellCardEffects::performSpiderWeb,
                () -> BalanceNumbers.SPIDER_WEB_SP_COST,
                () -> BalanceNumbers.SPIDER_WEB_COOLDOWN,
                0xFFB0BEC5, () -> 0D, () -> BalanceNumbers.SPIDER_WEB_ALPHA);
        register(PLAGUE_REPAY, SpellCardEffects::performPlagueRepay,
                () -> BalanceNumbers.PLAGUE_REPAY_SP_COST,
                () -> BalanceNumbers.PLAGUE_REPAY_COOLDOWN,
                0xFF7CB342, () -> 0D, () -> BalanceNumbers.PLAGUE_REPAY_ALPHA);
        register(FOX_SERVANT, SpellCardEffects::performFoxServant,
                () -> BalanceNumbers.FOX_SERVANT_SP_COST,
                () -> BalanceNumbers.FOX_SERVANT_COOLDOWN,
                0xFF7E57C2, () -> BalanceNumbers.FOX_SERVANT_BASE,
                () -> BalanceNumbers.FOX_SERVANT_ALPHA);
    }

    private SpellCardEffects() {
    }

    private static void register(String id, SpellEffect effect,
                                 Supplier<Integer> spCost, Supplier<Integer> cooldownTicks,
                                 int themeColor, Supplier<Double> base, Supplier<Double> alpha) {
        REGISTRY.put(id, new Entry(effect, spCost, cooldownTicks, themeColor, base, alpha));
    }

    public static Entry get(String cardId) {
        return REGISTRY.get(cardId);
    }

    /** 已学形态施放：动态读玩家灵力强度（品 = 玩家当前阶级）。 */
    public static void perform(String cardId, Level level, Player player) {
        int quality = player instanceof ServerPlayer sp ? Math.max(1, GraceService.tierOf(sp)) : 1;
        dispatch(cardId, quality, level, player, false);
    }

    /** 道具形态施放：固定数值（品 = 物品上的品质组件，缺省 1）。 */
    public static void performItem(String cardId, int quality, Level level, Player player) {
        dispatch(cardId, SpellCardScaling.clampQuality(quality), level, player, true);
    }

    private static void dispatch(String cardId, int quality, Level level, Player player, boolean itemForm) {
        Entry entry = REGISTRY.get(cardId);
        if (entry == null) {
            return;
        }
        double magnitude;
        if (itemForm) {
            magnitude = SpellCardScaling.itemValue(entry.base().get(), entry.alpha().get(), quality);
        } else if (player instanceof ServerPlayer sp) {
            magnitude = SpellCardScaling.learnedValue(entry.base().get(), entry.alpha().get(), sp);
        } else {
            magnitude = entry.base().get();
        }
        entry.effect().cast(new CastContext(level, player, magnitude, quality, itemForm));
    }

    public static ResourceLocation iconFor(String cardId) {
        return Gensokyou.id(cardId);
    }

    private static void performNoop(CastContext ctx) {
    }

    private static void performMusouFuuin(CastContext ctx) {
        Level level = ctx.level();
        Player player = ctx.player();
        double radius = BalanceNumbers.MUSOU_RADIUS;
        double angularSpeed = BalanceNumbers.MUSOU_ANGULAR_SPEED;
        int duration = BalanceNumbers.MUSOU_DURATION_TICKS;
        float damageCap = BalanceNumbers.MUSOU_DAMAGE_CAP.floatValue();
        for (int i = 0; i < 6; i++) {
            OrbitYinYangOrb orb = new OrbitYinYangOrb(level, player, i, radius, angularSpeed, duration, damageCap);
            level.addFreshEntity(orb);
        }
    }

    private static void performIcicleFall(CastContext ctx) {
        Level level = ctx.level();
        Player player = ctx.player();
        int count = BalanceNumbers.ICICLE_COUNT;
        double speed = BalanceNumbers.ICICLE_SPEED;
        float damage = BalanceNumbers.ICICLE_DAMAGE.floatValue();
        Vec3 look = player.getLookAngle().normalize();
        for (int i = 0; i < count; i++) {
            double offset = Math.toRadians((i - (count - 1) / 2.0D) * 12D);
            Vec3 dir = com.bitsson.gensokyou.entity.goal.FanDanmakuGoal.rotateAroundY(look, offset);
            SphereDanmaku projectile = new SphereDanmaku(
                    level, player, damage,
                    0x8FD8FF,
                    0.4F,
                    java.util.Set.of()
            );
            projectile.moveTo(player.getX(), player.getEyeY(), player.getZ(),
                    player.getYRot(), player.getXRot());
            projectile.shoot(dir.x, dir.y, dir.z, (float) speed, 0F);
            level.addFreshEntity(projectile);
        }
    }

    // ------------------------------------------------------------------
    // 前期 BOSS 主题玩家符卡（add-player-spellcards 4.x）
    // ------------------------------------------------------------------

    /** 已学形态读 spell_amp 增幅；道具形态固定不加（spec item-spellcards）。 */
    private static double amplified(CastContext ctx) {
        if (ctx.itemForm() || !(ctx.player() instanceof ServerPlayer sp)) {
            return ctx.magnitude();
        }
        float amp = com.bitsson.gensokyou.spirit.attr.PlayerAttributes
                .finalValue(sp, com.bitsson.gensokyou.spirit.attr.AttributeKey.SPELL_AMP);
        return ctx.magnitude() * (1D + Math.max(0F, amp));
    }

    private static int secondsToTicks(double seconds) {
        return (int) Math.round(seconds * 20D);
    }

    private static int durationTicks(CastContext ctx,
                                     java.util.List<? extends Double> table) {
        return secondsToTicks(SpellCardScaling.tableEntry(table, ctx.quality()));
    }

    /** 花符『癒しの花園』：施放点花圃场，圈内玩家每秒回血（不回灵）。 */
    private static void performHealingGarden(CastContext ctx) {
        if (!(ctx.level() instanceof ServerLevel level)) {
            return;
        }
        double perSecond = amplified(ctx);
        float radius = (float) SpellCardScaling.tableEntry(
                BalanceNumbers.HEALING_GARDEN_RADIUS, ctx.quality());
        int duration = durationTicks(ctx, BalanceNumbers.HEALING_GARDEN_DURATION_SECONDS);
        level.addFreshEntity(new FlowerGardenEntity(level,
                ctx.player().getX(), ctx.player().getY(), ctx.player().getZ(),
                radius, duration, perSecond));
    }

    /** 花符『鮮花之鎧』：花瓣护盾，挡弹消耗（事件层拦截）。 */
    private static void performFlowerArmor(CastContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player)) {
            return;
        }
        int petals = (int) Math.round(SpellCardScaling.tableEntry(
                BalanceNumbers.FLOWER_ARMOR_BLOCKS, ctx.quality()));
        int duration = secondsToTicks(BalanceNumbers.FLOWER_ARMOR_DURATION_SECONDS);
        ModAttachments.setSpellBuffs(player, ModAttachments.spellBuffs(player)
                .withFlowerArmor(petals, player.level().getGameTime() + duration));
        player.displayClientMessage(
                Component.translatable("msg.gensokyou.flower_armor_cast", petals), true);
    }

    /** 闇符『ディマーケイション』：黑暗结界（对 BOSS 生效，主动攻击即终止）。 */
    private static void performDemarcation(CastContext ctx) {
        if (!(ctx.level() instanceof ServerLevel level)) {
            return;
        }
        float radius = (float) SpellCardScaling.tableEntry(
                BalanceNumbers.DEMARCATION_RADIUS, ctx.quality());
        int duration = secondsToTicks(BalanceNumbers.DEMARCATION_DURATION_SECONDS);
        level.addFreshEntity(new DarknessFieldEntity(level, ctx.player(), radius, duration));
    }

    /** 網符『蜘蛛の巣』：丝弹到位炸开立方体减速网域。 */
    private static void performSpiderWeb(CastContext ctx) {
        if (!(ctx.level() instanceof ServerLevel level)) {
            return;
        }
        float edge = (float) SpellCardScaling.tableEntry(
                BalanceNumbers.SPIDER_WEB_EDGE, ctx.quality());
        int duration = durationTicks(ctx, BalanceNumbers.SPIDER_WEB_DURATION_SECONDS);
        double mult = BalanceNumbers.SPIDER_WEB_SPEED_MULT;
        double distance = edge; // 生成距离 = 边长
        Vec3 dir = ctx.player().getLookAngle().normalize();
        level.addFreshEntity(new SilkProjectileEntity(level, ctx.player(), dir,
                distance, edge, duration, mult));
    }

    /** 疫符『病の返し』：免疫新负面 + 回敬伤害者。 */
    private static void performPlagueRepay(CastContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player)) {
            return;
        }
        int duration = durationTicks(ctx, BalanceNumbers.PLAGUE_REPAY_DURATION_SECONDS);
        ModAttachments.setSpellBuffs(player, ModAttachments.spellBuffs(player)
                .withPlagueRepay(player.level().getGameTime() + duration));
    }

    /** 式神『狐の従者』：召唤狐火式神，限距限伤自动索敌。 */
    private static void performFoxServant(CastContext ctx) {
        if (!(ctx.level() instanceof ServerLevel level)) {
            return;
        }
        double damage = Math.min(BalanceNumbers.FOX_SERVANT_DAMAGE_CAP, amplified(ctx));
        double range = SpellCardScaling.tableEntry(
                BalanceNumbers.FOX_SERVANT_RANGE, ctx.quality());
        int duration = durationTicks(ctx, BalanceNumbers.FOX_SERVANT_DURATION_SECONDS);
        int interval = BalanceNumbers.FOX_SERVANT_INTERVAL_TICKS;
        level.addFreshEntity(new FoxServantEntity(level, ctx.player(), range, duration, damage, interval));
    }
}
