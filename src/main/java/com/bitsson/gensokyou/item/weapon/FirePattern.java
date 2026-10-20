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
 *
 * <p><b>单位约定：弹速是「格 / tick」，不是「格 / 秒」。</b>弹丸每 tick 走一格弹速
 * （{@code AbstractDanmakuProjectile.tickDanmaku()} 的 {@code setPos(+ velocity)}），
 * 故<b>射程 = 弹速 × 存活 tick 数</b>，存活秒数须先 ×20 才是 tick 数。
 * 运行距离同理：{@code travelDistance / projectileSpeed} 得到的是 <b>tick</b>。
 * 任何涉及射程/飞行时间的推导 MUST 先确认这个单位（错过它会差 20 倍）。
 */
public record FirePattern(
        DanmakuKind kind,
        DanmakuFactory factory,
        IntSupplier count,
        DoubleSupplier spreadAngleDeg,
        DoubleSupplier projectileSpeed,
        DoubleSupplier lifetimeSeconds,
        DoubleSupplier travelDistance,
        DoubleSupplier laserMaxLength,
        DoubleSupplier laserRadius,
        DoubleSupplier laserDelaySeconds,
        DoubleSupplier laserDurationSeconds,
        DoubleSupplier talismanSensitivity) {

    @FunctionalInterface
    public interface DanmakuFactory {
        AbstractDanmakuProjectile create(Level level, LivingEntity owner, float damage);
    }

    /** 球/飞刀类：单发或散弹（运行距离不限制）。 */
    public static FirePattern ofBullet(DanmakuKind kind, DanmakuFactory factory, IntSupplier count,
                                       DoubleSupplier spreadAngleDeg, DoubleSupplier speed,
                                       DoubleSupplier lifetimeSeconds) {
        return ofBullet(kind, factory, count, spreadAngleDeg, speed, lifetimeSeconds, () -> 0D);
    }

    /**
     * 球/飞刀/锥类：单发或散弹，并可带运行距离上限。
     *
     * @param travelDistance 运行距离上限（<b>格</b>）；{@code 0} 表示不限制。
     *                        与存活时间<b>取先到者</b>，见
     *                        {@link CoreMath#effectiveLifetimeTicks}。
     */
    public static FirePattern ofBullet(DanmakuKind kind, DanmakuFactory factory, IntSupplier count,
                                       DoubleSupplier spreadAngleDeg, DoubleSupplier speed,
                                       DoubleSupplier lifetimeSeconds,
                                       DoubleSupplier travelDistance) {
        return new FirePattern(kind, factory, count, spreadAngleDeg, speed, lifetimeSeconds,
                travelDistance,
                () -> 0D, () -> 0D, () -> 0D, () -> 0D, () -> 0D);
    }

    /** 激光类：机枪=短延迟短持续，炮=长延迟长持续。 */
    public static FirePattern ofLaser(DanmakuKind kind, DanmakuFactory factory, DoubleSupplier maxLength,
                                      DoubleSupplier radius, DoubleSupplier delaySeconds,
                                      DoubleSupplier durationSeconds) {
        return new FirePattern(kind, factory, () -> 1, () -> 0D, () -> 0D, () -> 0D, () -> 0D,
                maxLength, radius, delaySeconds, durationSeconds, () -> 0D);
    }

    /** 灵符类：发射时射线拾取目标，落空直线飞。 */
    public static FirePattern ofTalisman(DanmakuKind kind, DanmakuFactory factory, DoubleSupplier speed,
                                         DoubleSupplier sensitivity) {
        return new FirePattern(kind, factory, () -> 1, () -> 0D, speed, () -> 0D, () -> 0D,
                () -> 0D, () -> 0D, () -> 0D, () -> 0D, sensitivity);
    }

    public boolean isLaser() {
        return laserMaxLength.getAsDouble() > 0D;
    }

    public boolean isTalisman() {
        return talismanSensitivity.getAsDouble() > 0D;
    }
}
