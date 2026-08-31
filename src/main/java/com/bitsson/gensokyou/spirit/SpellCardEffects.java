package com.bitsson.gensokyou.spirit;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.config.GensokyouConfig;
import com.bitsson.gensokyou.entity.OrbitYinYangOrb;
import com.bitsson.gensokyou.entity.SphereDanmaku;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 符卡效果注册表：物品符卡与已学技能槽共用同一执行器。
 * 槽位顺序固定，客户端 HUD 与服务端校验均依赖该顺序。
 */
public final class SpellCardEffects {

    public static final String MUSOU_FUUIN = "musou_fuuin";
    public static final String ICICLE_FALL = "icicle_fall";
    public static final String LIGHT_REFLECT = "light_reflect";

    /** 技能槽 1~3 对应的卡（与 HUD、键位一一对应）。 */
    public static final String[] SLOT_ORDER = {MUSOU_FUUIN, ICICLE_FALL, LIGHT_REFLECT};

    /**
     * @param themeColor 卡面主题色（ARGB）——物品图标纹章层 tint 与未来 HUD 强调色共用；
     *                   与品阶色环（TierPalette）无关的独立卡面参数。
     */
    public record Entry(java.util.function.BiConsumer<Level, Player> effect,
                        Supplier<Integer> spCost, Supplier<Integer> cooldownTicks,
                        int themeColor) {
    }

    private static final Map<String, Entry> REGISTRY = new HashMap<>();

    static {
        register(MUSOU_FUUIN, SpellCardEffects::performMusouFuuin,
                () -> GensokyouConfig.SKILL_MUSOU_SP_COST.get(),
                () -> GensokyouConfig.SKILL_MUSOU_COOLDOWN.get(),
                0xFFF44336);    // 无想封印：红（幻想封印主题）
        register(ICICLE_FALL, SpellCardEffects::performIcicleFall,
                () -> GensokyouConfig.SKILL_ICICLE_SP_COST.get(),
                () -> GensokyouConfig.SKILL_ICICLE_COOLDOWN.get(),
                0xFF4FC3F7);    // 冰符：冰蓝
        register(LIGHT_REFLECT, (level, player) -> {
        }, () -> 5, () -> 60,
                0xFFFFD54F);    // 光反（占位）：金
    }

    private SpellCardEffects() {
    }

    private static void register(String id, java.util.function.BiConsumer<Level, Player> effect,
                                 Supplier<Integer> spCost, Supplier<Integer> cooldownTicks,
                                 int themeColor) {
        REGISTRY.put(id, new Entry(effect, spCost, cooldownTicks, themeColor));
    }

    public static Entry get(String cardId) {
        return REGISTRY.get(cardId);
    }

    public static void perform(String cardId, Level level, Player player) {
        Entry entry = REGISTRY.get(cardId);
        if (entry != null) {
            entry.effect().accept(level, player);
        }
    }

    public static ResourceLocation iconFor(String cardId) {
        return Gensokyou.id(cardId);
    }

    private static void performMusouFuuin(Level level, Player player) {
        double radius = GensokyouConfig.MUSOU_RADIUS.get();
        double angularSpeed = GensokyouConfig.MUSOU_ANGULAR_SPEED.get();
        int duration = GensokyouConfig.MUSOU_DURATION_TICKS.get();
        float damageCap = GensokyouConfig.MUSOU_DAMAGE_CAP.get().floatValue();
        for (int i = 0; i < 6; i++) {
            OrbitYinYangOrb orb = new OrbitYinYangOrb(level, player, i, radius, angularSpeed, duration, damageCap);
            level.addFreshEntity(orb);
        }
    }

    private static void performIcicleFall(Level level, Player player) {
        int count = GensokyouConfig.ICICLE_COUNT.get();
        double speed = GensokyouConfig.ICICLE_SPEED.get();
        float damage = GensokyouConfig.ICICLE_DAMAGE.get().floatValue();
        Vec3 look = player.getLookAngle().normalize();
        for (int i = 0; i < count; i++) {
            double offset = Math.toRadians((i - (count - 1) / 2.0D) * 12D);
            Vec3 dir = com.bitsson.gensokyou.entity.goal.FanDanmakuGoal.rotateAroundY(look, offset);
            SphereDanmaku projectile = new SphereDanmaku(
                    level, player, damage,
                    0x8FD8FF,       // 冰锥主题色
                    0.4F,
                    java.util.Set.of() // 空白名单：仅 owner 免疫
            );
            projectile.moveTo(player.getX(), player.getEyeY(), player.getZ(),
                    player.getYRot(), player.getXRot());
            projectile.shoot(dir.x, dir.y, dir.z, (float) speed, 0F);
            level.addFreshEntity(projectile);
        }
    }
}
