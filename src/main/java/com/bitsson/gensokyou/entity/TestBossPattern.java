package com.bitsson.gensokyou.entity;

import com.bitsson.gensokyou.config.GensokyouConfig;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.Set;

/**
 * 测试 BOSS 的十种东方美学弹幕模式（add-balance-test-harness）。
 *
 * <p>每种模式在"一个模式槽"（默认 60 tick）内按自身节奏发射，复用既有球/飞刀/灵符/激光实体。
 * 几何（角度/数量）在代码中，标量（间隔/弹速/伤害系数/阶段）走 config。
 */
public enum TestBossPattern {

    /** ① 环弹「八方圆舞」：16 向水平等角环，每槽 3 环。 */
    RING,
    /** ② 螺旋「回旋涡」：双流反向，每 3 tick 各发 1 发，角度递增。 */
    SPIRAL,
    /** ③ 狙弹「三途刺」：自机狙 3 向飞刀，每 15 tick 一组。 */
    AIM3,
    /** ④ 扇弹「孔雀开屏」：9 向扇形（张角 60°），每 25 tick。 */
    FAN,
    /** ⑤ 星芒「十字星辉」：固定 8 向 + 旋转 22.5° 的次环，每 30 tick。 */
    STAR,
    /** ⑥ 花弹「彼岸花开」：5 花瓣 × 5 子弹，速度正弦调制，每 40 tick。 */
    FLOWER,
    /** ⑦ 迟弹「缓速散华」：12 向慢速大弹，每 30 tick。 */
    SLOW,
    /** ⑧ 激光「贯穿之矢」：自机狙激光（延迟 + 持续），每槽一次。 */
    LASER,
    /** ⑨ 追符「执念灵符」：6 张追踪符，每槽一次。 */
    HOMING,
    /** ⑩ 弹幕雨「无尽散华」：每 tick 2 发随机方向/速度。 */
    RAIN;

    /** 弹幕白名单：仅免疫发射者自身（owner）。 */
    public static final Set<EntityType<?>> NO_WHITELIST = Set.of();

    public static final int COUNT = 10;

    public void fire(BalanceTestBossEntity boss, int slotTick, int slotLength, double speedMult) {
        if (boss.getTarget() == null || !boss.getTarget().isAlive()) {
            return;
        }
        double base = GensokyouConfig.TEST_BULLET_SPEED.get() * speedMult;
        float damage = boss.testDanmakuDamage() * damageFactor(ordinal());
        Vec3 origin = boss.getEyePosition();
        switch (this) {
            case RING -> {
                int every = Math.max(1, slotLength / 3);
                if (slotTick % every == 0) {
                    for (int i = 0; i < 16; i++) {
                        sphere(boss, origin, horiz(i * 22.5D), base * 0.8D, damage);
                    }
                }
            }
            case SPIRAL -> {
                if (slotTick % 3 == 0) {
                    double angle = slotTick * 14.0D;
                    sphere(boss, origin, horiz(angle), base, damage);
                    sphere(boss, origin, horiz(angle + 180.0D), base, damage);
                }
            }
            case AIM3 -> {
                if (slotTick % 15 == 0) {
                    Vec3 aim = aim(boss);
                    for (int i = -1; i <= 1; i++) {
                        knife(boss, origin, rotY(aim, i * 8.0D), base * 1.3D, damage);
                    }
                }
            }
            case FAN -> {
                if (slotTick % 25 == 0) {
                    Vec3 aim = aim(boss);
                    for (int i = 0; i < 9; i++) {
                        double offset = (i - 4) * 7.5D;
                        sphere(boss, origin, rotY(aim, offset), base * 0.9D, damage);
                    }
                }
            }
            case STAR -> {
                if (slotTick % 30 == 0) {
                    for (int i = 0; i < 8; i++) {
                        sphere(boss, origin, horiz(i * 45.0D), base * 0.85D, damage);
                    }
                    for (int i = 0; i < 8; i++) {
                        sphere(boss, origin, horiz(i * 45.0D + 22.5D), base * 0.7D, damage);
                    }
                }
            }
            case FLOWER -> {
                if (slotTick % 40 == 0) {
                    for (int petal = 0; petal < 5; petal++) {
                        double petalAngle = petal * 72.0D;
                        for (int j = 0; j < 5; j++) {
                            double sine = Math.sin(Math.toRadians(j * 45.0D + petal * 18.0D));
                            double speed = base * (0.7D + 0.35D * sine);
                            sphere(boss, origin, horiz(petalAngle + j * 6.0D), speed, damage);
                        }
                    }
                }
            }
            case SLOW -> {
                if (slotTick % 30 == 0) {
                    for (int i = 0; i < 12; i++) {
                        sphere(boss, origin, horiz(i * 30.0D), base * 0.5D, damage);
                    }
                }
            }
            case LASER -> {
                if (slotTick == 0) {
                    laser(boss, origin, aim(boss), damage, speedMult);
                }
            }
            case HOMING -> {
                if (slotTick == 0) {
                    Vec3 aim = aim(boss);
                    for (int i = 0; i < 6; i++) {
                        talisman(boss, origin, rotY(aim, (i - 2.5D) * 10.0D), base * 0.8D, damage);
                    }
                }
            }
            case RAIN -> {
                for (int k = 0; k < 2; k++) {
                    double yaw = boss.getRandom().nextDouble() * 360.0D;
                    double speed = base * (0.6D + boss.getRandom().nextDouble() * 1.0D);
                    sphere(boss, origin, horiz(yaw), speed, damage);
                }
            }
        }
    }

    private static float damageFactor(int ordinal) {
        var list = GensokyouConfig.TEST_PATTERN_DAMAGE_FACTOR.get();
        if (list.isEmpty()) {
            return 1F;
        }
        int index = Math.min(ordinal, list.size() - 1);
        return list.get(index).floatValue();
    }

    // ---- 几何与生成 ----

    /** 水平单位向量（yaw 角度制，绕 Y 轴）。 */
    public static Vec3 horiz(double degrees) {
        double radians = Math.toRadians(degrees);
        return new Vec3(Math.cos(radians), 0D, Math.sin(radians));
    }

    /** 绕 Y 轴旋转（角度制）。 */
    public static Vec3 rotY(Vec3 vec, double degrees) {
        double radians = Math.toRadians(degrees);
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        return new Vec3(vec.x * cos + vec.z * sin, vec.y, -vec.x * sin + vec.z * cos);
    }

    /** 自机狙：BOSS 眼位指向目标眼位。 */
    public static Vec3 aim(BalanceTestBossEntity boss) {
        LivingEntity target = boss.getTarget();
        if (target == null) {
            return boss.getLookAngle();
        }
        Vec3 to = target.getEyePosition().subtract(boss.getEyePosition());
        return to.lengthSqr() < 1.0E-8D ? boss.getLookAngle() : to.normalize();
    }

    private static void sphere(BalanceTestBossEntity boss, Vec3 origin, Vec3 dir, double speed, float damage) {
        SphereDanmaku p = new SphereDanmaku(boss.level(), boss, damage, 0, 0.4F, NO_WHITELIST);
        shoot(boss, p, origin, dir, speed);
    }

    private static void knife(BalanceTestBossEntity boss, Vec3 origin, Vec3 dir, double speed, float damage) {
        KnifeDanmaku p = new KnifeDanmaku(boss.level(), boss, damage, NO_WHITELIST);
        shoot(boss, p, origin, dir, speed);
    }

    private static void talisman(BalanceTestBossEntity boss, Vec3 origin, Vec3 dir, double speed, float damage) {
        TalismanDanmaku p = new TalismanDanmaku(boss.level(), boss, damage, 0,
                boss.getTarget(), 90.0D, NO_WHITELIST);
        shoot(boss, p, origin, dir, speed);
    }

    private static void laser(BalanceTestBossEntity boss, Vec3 origin, Vec3 dir, float damage, double speedMult) {
        LaserDanmaku p = new LaserDanmaku(boss.level(), origin, dir, damage, 0,
                32.0D, 0.25D, 0.7D, 1.2D, boss, NO_WHITELIST);
        boss.level().addFreshEntity(p);
    }

    private static void shoot(BalanceTestBossEntity boss, AbstractDanmakuProjectile p,
                              Vec3 origin, Vec3 dir, double speed) {
        p.moveTo(origin.x, origin.y, origin.z, boss.getYRot(), boss.getXRot());
        p.shoot(dir.x, dir.y, dir.z, (float) speed, 0F);
        boss.level().addFreshEntity(p);
    }
}
