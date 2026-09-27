package com.bitsson.gensokyou.item.weapon;

import com.bitsson.gensokyou.entity.AbstractDanmakuProjectile;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.function.DoubleSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * 弹幕核的发射行为（核的独立维度：同弹幕类型可有多种 firePattern）。
 * 数值全部为 Supplier（注册期早于配置加载，使用时才读取）。
 */
public record FirePattern(
        DanmakuKind kind,
        DanmakuFactory factory,
        IntSupplier count,
        DoubleSupplier spreadAngleDeg,
        DoubleSupplier projectileSpeed,
        DoubleSupplier lifetimeSeconds,
        DoubleSupplier laserMaxLength,
        DoubleSupplier laserRadius,
        DoubleSupplier laserDelaySeconds,
        DoubleSupplier laserDurationSeconds,
        DoubleSupplier talismanSensitivity) {

    @FunctionalInterface
    public interface DanmakuFactory {
        AbstractDanmakuProjectile create(Level level, LivingEntity owner, float damage);
    }

    /** 球/飞刀类：单发或散弹。 */
    public static FirePattern ofBullet(DanmakuKind kind, DanmakuFactory factory, IntSupplier count,
                                       DoubleSupplier spreadAngleDeg, DoubleSupplier speed,
                                       DoubleSupplier lifetimeSeconds) {
        return new FirePattern(kind, factory, count, spreadAngleDeg, speed, lifetimeSeconds,
                () -> 0D, () -> 0D, () -> 0D, () -> 0D, () -> 0D);
    }

    /** 激光类：机枪=短延迟短持续，炮=长延迟长持续。 */
    public static FirePattern ofLaser(DanmakuKind kind, DanmakuFactory factory, DoubleSupplier maxLength,
                                      DoubleSupplier radius, DoubleSupplier delaySeconds,
                                      DoubleSupplier durationSeconds) {
        return new FirePattern(kind, factory, () -> 1, () -> 0D, () -> 0D, () -> 0D,
                maxLength, radius, delaySeconds, durationSeconds, () -> 0D);
    }

    /** 灵符类：发射时射线拾取目标，落空直线飞。 */
    public static FirePattern ofTalisman(DanmakuKind kind, DanmakuFactory factory, DoubleSupplier speed,
                                         DoubleSupplier sensitivity) {
        return new FirePattern(kind, factory, () -> 1, () -> 0D, speed, () -> 0D,
                () -> 0D, () -> 0D, () -> 0D, () -> 0D, sensitivity);
    }

    public boolean isLaser() {
        return laserMaxLength.getAsDouble() > 0D;
    }

    public boolean isTalisman() {
        return talismanSensitivity.getAsDouble() > 0D;
    }
}
