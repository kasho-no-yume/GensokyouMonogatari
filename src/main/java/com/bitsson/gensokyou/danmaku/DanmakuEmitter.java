package com.bitsson.gensokyou.danmaku;

import com.bitsson.gensokyou.danmaku.track.Behaviour;
import com.bitsson.gensokyou.danmaku.track.Geometry;
import com.bitsson.gensokyou.entity.AbstractDanmakuProjectile;
import com.bitsson.gensokyou.entity.DanmakuRig;
import com.bitsson.gensokyou.entity.DanmakuWhitelists;
import com.bitsson.gensokyou.entity.SphereDanmaku;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.Set;

/**
 * 发射指令 → 弹幕实体的翻译层。
 *
 * <p><b>几何与行为在这里分开施加</b>：{@code Geometry} 产出的是「纯几何」，
 * 本类 MUST NOT 依据<b>形状名</b>改写弹的运动；运动/分裂/显隐一律由
 * {@link Behaviour} 决定，按<b>行为种类</b>分派。改前这里是
 * {@code switch (shape)}，于是「让某几何带某行为」必须新造一个几何。
 *
 * <p>分派按行为种类而非形状名，是「几何与行为正交」这条约束的落点：若哪天
 * 出现了「按 {@code shape} 分派」的分支，lint 侧的新增判据会立刻失去意义。
 *
 * <p>行为到实体的映射（全部走既有弹幕实体的行为开关，MUST NOT 为任一行为新增
 * 独立弹幕类型）：
 * <pre>
 *   Motion.NONE        → 无额外处理（匀速直线）
 *   Motion.CURVE       → configureCurve(axis, rate)
 *   Motion.HOVER       → configureHover(hoverTick)      定住后仍判伤
 *   Motion.MINE        → configureMine(radius)          埋设期不接触判伤，近身才结算
 *   Motion.GROUND_HUG  → 抹掉竖直分量、保留水平分量
 *   Split              → configureSplit(tick, count)
 *   Visibility         → configurePhaseHide(period, duty, offset)
 * </pre>
 *
 * <p>新弹种是一个独立且可后置的旋钮：凡是改变「能命中什么 / 如何判定命中」的能力
 * 才走新弹种（如激光的射线判伤），运动与显隐一律留在行为层。
 */
public final class DanmakuEmitter {

    /** BOSS 弹幕不伤同类：白名单留空，靠 owner 判定即可。 */
    private static final Set<net.minecraft.world.entity.EntityType<?>> NO_WHITELIST = Set.of();

    private DanmakuEmitter() {
    }

    public static void emit(LivingEntity boss, Geometry.Shot shot, Behaviour behaviour,
                            int color, float damage) {
        emit(boss, shot, behaviour, color, damage, null, 0);
    }

    /**
     * 发射一枚弹，可选地挂到编队装置上。
     *
     * @param rig   本轨的装置；为 {@code null} 表示该轨不编队（绝大多数情况）
     * @param phase 本弹在该编队中的相位角（弧度）。<b>只在 rig 非 null 时有意义</b>
     */
    public static void emit(LivingEntity boss, Geometry.Shot shot, Behaviour behaviour,
                            int color, float damage, DanmakuRig rig, double phase) {
        if (!(boss.level() instanceof ServerLevel server)) {
            return;
        }
        double speed = shot.params().speed();
        SphereDanmaku bullet = new SphereDanmaku(
                server, boss, damage, color, (float) speed, NO_WHITELIST);
        bullet.moveTo(shot.origin().x, shot.origin().y, shot.origin().z, 0F, 0F);
        applyMotion(bullet, shot, behaviour.motion(), speed);
        applySplit(bullet, behaviour.split());
        applyVisibility(bullet, behaviour.visibility());
        // MUST 在 setDirection 之后：装置会接管位置，几何给的初速随即失效。
        if (rig != null) {
            bullet.bindToRig(rig, phase);
        }
        server.addFreshEntity(bullet);
        DanmakuBudget.recordEmit();
    }

    /**
     * 几何给出的初速，再按行为运动改写。
     *
     * <p>顺序有意义：行为在几何<b>之后</b>施加，故「贴地」这类运动能把几何给的
     * 竖直分量抹掉，而几何不必知道自己被改写了。
     */
    private static void applyMotion(SphereDanmaku bullet, Geometry.Shot shot,
                                    Behaviour.Motion motion, double speed) {
        Behaviour.Motion.Kind kind = motion.kind();
        if (kind == Behaviour.Motion.Kind.GROUND_HUG) {
            Vec3 flat = new Vec3(shot.direction().x, 0.0D, shot.direction().z);
            if (flat.lengthSqr() > 1.0E-9D) {
                bullet.setDirection(flat.normalize(), speed);
            } else {
                bullet.setDeltaMovement(Vec3.ZERO);
            }
            return;
        }
        if (shot.direction().lengthSqr() > 1.0E-9D) {
            bullet.setDirection(shot.direction(), speed);
        } else {
            // 几何给了零方向（静止待发类）：由行为决定其语义。
            bullet.setDeltaMovement(Vec3.ZERO);
        }
        switch (kind) {
            case CURVE -> bullet.configureCurve(
                    Geometry.axisFrom(motion.curveYawDeg(), motion.curvePitchDeg()),
                    motion.curveRateDegPerSec());
            // 速率曲线 MUST 在 setDirection 之后挂：它要趁速度非零时记下方向轴
            // （曲线会把速率降到 0，零速时方向不可恢复）。
            case SPEED_PROFILE -> bullet.configureSpeedProfile(motion.speedProfile());
            case HOVER -> bullet.configureHover(motion.hoverTick());
            case MINE -> bullet.configureMine(motion.mineRadius());
            case NONE, GROUND_HUG -> {
                // 上面已处理
            }
        }
    }

    private static void applySplit(SphereDanmaku bullet, Behaviour.Split split) {
        if (split.active()) {
            bullet.configureSplit(split.tick(), split.count());
        }
    }

    private static void applyVisibility(SphereDanmaku bullet, Behaviour.Visibility visibility) {
        if (visibility.active()) {
            bullet.configurePhaseHide(
                    visibility.periodTicks(), visibility.duty(), visibility.phaseOffset());
        }
    }

    /** BOSS 自用弹幕的白名单工厂（留作差异化旋钮）。 */
    public static Set<net.minecraft.world.entity.EntityType<?>> bossWhitelist() {
        return DanmakuWhitelists.FAIRY;
    }
}
