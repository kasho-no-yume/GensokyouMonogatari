package com.bitsson.gensokyou.danmaku;

import com.bitsson.gensokyou.danmaku.track.Geometry;
import com.bitsson.gensokyou.danmaku.track.Shape;
import com.bitsson.gensokyou.entity.AbstractDanmakuProjectile;
import com.bitsson.gensokyou.entity.DanmakuWhitelists;
import com.bitsson.gensokyou.entity.SphereDanmaku;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.Set;

/**
 * 发射指令 → 弹幕实体的翻译层。
 *
 * <p>形状决定的是<b>行为开关</b>而非弹种：所有 BOSS 弹幕统一走球弹，靠
 * 「色相 / 尺寸 / 行为」三项承担视觉辨识（见 {@code danmaku-track-composition}
 * 的轨道视觉独占规则）。新增弹种是一个独立的、可以后置的旋钮。
 *
 * <p>行为映射：
 * <pre>
 *   HOVER_BURST   → configureHover(hoverTicks)
 *   MINE_RING     → configureMine(mineRadius)
 *   GAP_SPLIT     → configureSplit(splitTick, splitCount)
 *   CURVE_RING    → configureCurve(axis, rateDegPerSec)
 *   GROUND_BAND   → 贴地：初速极低并让 Y 分量归零，靠重力外的"贴地"近似
 *   其余          → 无额外行为
 * </pre>
 */
public final class DanmakuEmitter {

    /** BOSS 弹幕不伤同类：白名单留空，靠 owner 判定即可。 */
    private static final Set<net.minecraft.world.entity.EntityType<?>> NO_WHITELIST = Set.of();

    private DanmakuEmitter() {
    }

    public static void emit(LivingEntity boss, Geometry.Shot shot, int color, float damage, Shape shape) {
        if (!(boss.level() instanceof ServerLevel server)) {
            return;
        }
        Shape.Params params = shot.params();
        SphereDanmaku bullet = new SphereDanmaku(
                server, boss, damage, color, (float) params.size(), NO_WHITELIST);
        bullet.moveTo(shot.origin().x, shot.origin().y, shot.origin().z, 0F, 0F);
        if (shot.direction().lengthSqr() > 1.0E-9D) {
            bullet.setDirection(shot.direction(), params.speed());
        } else {
            // 溜め：零方向弹，靠 configureMine 变成静止触发器。
            bullet.setDeltaMovement(Vec3.ZERO);
        }
        switch (shape) {
            case HOVER_BURST -> bullet.configureHover(params.hoverTicks());
            case MINE_RING -> bullet.configureMine(params.mineRadius());
            case GAP_SPLIT -> bullet.configureSplit(params.splitTick(), params.splitCount());
            case CURVE_RING -> bullet.configureCurve(
                    com.bitsson.gensokyou.danmaku.track.Geometry.axisFrom(
                            params.curveYawDeg(), params.curvePitchDeg()),
                    params.curveRateDegPerSec());
            case GROUND_BAND -> bullet.setDeltaMovement(
                    new Vec3(shot.direction().x, 0.0D, shot.direction().z)
                            .normalize().scale(params.speed()));
            default -> {
            }
        }
        if (params.hoverTicks() > 0 && shape != Shape.HOVER_BURST) {
            bullet.configureHover(params.hoverTicks());
        }
        server.addFreshEntity(bullet);
        DanmakuBudget.recordEmit();
    }

    /** BOSS 自用弹幕的白名单工厂（留作差异化旋钮）。 */
    public static Set<net.minecraft.world.entity.EntityType<?>> bossWhitelist() {
        return DanmakuWhitelists.FAIRY;
    }
}
