package com.bitsson.gensokyou.danmaku;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * 弹幕的一 tick 运动扫掠命中。
 *
 * <p>与原版 {@code ProjectileUtil.getHitResultOnMoveVector} 同用途，但**实体查询带类型过滤**。
 * 这不是性能微调，是弹量级的差别：
 *
 * <p><b>为什么</b>——实体的世界存储按 {@code EntityType} 分子表组织。带类型过滤的查询传一个
 * 类型测试，其 {@code testSubList} 在进入子表<b>之前</b>即可整体跳过整个子表；不带过滤的
 * 查询则必须逐个访问查询盒所在分区内的每一个实体。全部弹幕共享同一个 {@code EntityType}，
 * 故一次子表级判断即可跳过全部弹幕；不带过滤时，单枚弹的判定开销与区域内存活弹数<b>成正比</b>。
 *
 * <p>原版被迫使用不带过滤的重载——投射物必须能命中任何东西（雪球打船、标枪打船是原版行为）。
 * 本项目的弹幕没有这类需求。
 *
 * <p><b>与原版的另一处差异</b>：原版只要扫到方块就立即返回方块命中，<b>不与实体命中比距离</b>；
 * 本实现取<b>距离更近者</b>。弹幕每 tick 只前进零点几格，两者几乎总是一致，但在
 * 「玩家贴着墙、弹正要掠过玩家」时会分道扬镳——原版会吞掉那发本该命中的弹。取更近者才与观感一致。
 *
 * <p>判定语义（见 {@code danmaku-pipeline-capacity}，改动时 MUST 保持）：
 * <ol>
 *   <li>方块碰撞仍然生效</li>
 *   <li>方块命中与实体命中取距离更近者</li>
 *   <li>定住的弹（悬停）由调用方另走碰撞箱相交</li>
 * </ol>
 */
public final class DanmakuHitScan {

    /** 查询盒相对运动矢量的外扩（格）。与原版一致。 */
    private static final double QUERY_INFLATE = 1.0D;

    /** 视作零位移的长度平方阈值。 */
    private static final double ZERO_MOTION_SQR = 1.0E-7D;

    /** 轴向近乎平行的判据：此时该轴不构成出入条件。 */
    private static final double AXIS_PARALLEL = 1.0E-9D;

    private DanmakuHitScan() {
    }

    /**
     * 弹沿本 tick 的位移扫掠，返回最先遇到的东西。
     *
     * @param bullet  扫掠发起者（用它的碰撞箱与位移）
     * @param hitType 参与实体判定的类型。传入 {@link LivingEntity} 可让弹幕所在的实体类型
     *                子表被整体跳过——这是本方法存在的全部理由
     * @param canHit  谓词（白名单等额外规则）
     * @return 方块命中 / 实体命中；<b>未命中返回 {@code null}</b>（本映射下无 MISS 实例）
     */
    public static <T extends Entity> HitResult sweep(Entity bullet, Class<T> hitType,
                                                      Predicate<? super T> canHit) {
        Vec3 delta = bullet.getDeltaMovement();
        if (delta.lengthSqr() < ZERO_MOTION_SQR) {
            // 零位移下原版亦返回 MISS，与原版一致。定住弹由调用方另走 AABB 相交。
            return null;
        }
        Level level = bullet.level();
        Vec3 start = bullet.position();
        Vec3 end = start.add(delta);
        AABB query = queryBox(bullet.getBoundingBox(), delta);

        BlockHitResult blockHit = level.clip(new ClipContext(
                start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, bullet));
        boolean hitBlock = blockHit.getType() == HitResult.Type.BLOCK;
        double blockDistSqr = hitBlock ? blockHit.getLocation().distanceToSqr(start)
                : Double.MAX_VALUE;

        // getEntitiesOfClass 自带 NO_SPECTATORS，故此处只需业务谓词。
        List<T> candidates = level.getEntitiesOfClass(hitType, query, canHit);

        T nearest = null;
        Vec3 nearestPoint = null;
        double nearestDistSqr = Double.MAX_VALUE;
        for (T candidate : candidates) {
            // 不外扩目标碰撞箱：原版的 getPickRadius() 只对可拾取物非零，对活体恒为 0，
            // 故不外扩才与既有行为逐位一致。
            Optional<Vec3> clipped = sweepAabb(start, delta, candidate.getBoundingBox());
            if (clipped.isEmpty()) {
                continue;
            }
            Vec3 point = clipped.get();
            double distSqr = point.distanceToSqr(start);
            if (distSqr < nearestDistSqr) {
                nearestDistSqr = distSqr;
                nearest = candidate;
                nearestPoint = point;
            }
        }

        if (nearest == null || blockDistSqr < nearestDistSqr) {
            return hitBlock ? blockHit : null;
        }
        return new EntityHitResult(nearest, nearestPoint.subtract(nearest.position()));
    }

    /**
     * 一 tick 运动所需的实体查询盒。
     *
     * <p>盒 = 弹自身的碰撞盒，<b>按本 tick 的位移 {@code delta}</b> 沿运动方向延长，
     * 再外扩 {@link #QUERY_INFLATE} 格。结果即从「起始碰撞盒」扫到「终点碰撞盒」的全域。
     *
     * <p>两处 MUST NOT 写错，都是本文件已发生过的错误：
     * <ul>
     *   <li>传入的 {@code bulletBox} <b>已是世界坐标</b>，MUST NOT 再对它
     *       {@code move(...)}——{@code AABB.move} 是平移，会把查询盒挪到 {@code 2 × 位置}，
     *       使弹幕仅在世界原点附近才碰巧命中。
     *   <li>{@code AABB.expandTowards} 是<b>按增量</b>扩展的，MUST NOT 传绝对终点坐标——
     *       那会按「距原点多远」去扩展。在 x=1000 处传绝对终点会多扩 1000 格。
     * </ul>
     *
     * <p>提成静态函数正是为了能脱离世界单测：以上两处都需要实体与世界才能暴露。
     */
    public static AABB queryBox(AABB bulletBox, Vec3 delta) {
        return bulletBox.expandTowards(delta).inflate(QUERY_INFLATE);
    }

    /**
     * 线段（{@code start → start+delta}）与 AABB 的首次相交点。
     *
     * <p>标准 slab 裁剪：逐轴求进出参数区间并取交，交集非空则区间下界即首次入射点。
     * {@code delta} 该轴近零时该轴不设约束，退化为「在盒内 / 在盒外」判定。
     *
     * <p>纯静态、无世界，故可被单测直接覆盖。
     */
    public static Optional<Vec3> sweepAabb(Vec3 start, Vec3 delta, AABB box) {
        if (delta.lengthSqr() < ZERO_MOTION_SQR) {
            return box.contains(start) ? Optional.of(start) : Optional.empty();
        }
        double[] span = new double[]{0.0D, 1.0D};
        if (clipAxis(start.x, delta.x, box.minX, box.maxX, span)
                || clipAxis(start.y, delta.y, box.minY, box.maxY, span)
                || clipAxis(start.z, delta.z, box.minZ, box.maxZ, span)) {
            return Optional.empty();
        }
        return Optional.of(start.add(delta.scale(span[0])));
    }

    /**
     * 单轴 slab 裁剪，就地更新 {@code span[0]=进入参数, span[1]=离开参数}。
     *
     * @return 该轴使区间为空（必然不相交）时为 true
     */
    private static boolean clipAxis(double origin, double delta, double min, double max, double[] span) {
        if (Math.abs(delta) < AXIS_PARALLEL) {
            return origin < min || origin > max;
        }
        double inverse = 1.0D / delta;
        double enter = (min - origin) * inverse;
        double leave = (max - origin) * inverse;
        if (enter > leave) {
            double swap = enter;
            enter = leave;
            leave = swap;
        }
        if (enter > span[0]) {
            span[0] = enter;
        }
        if (leave < span[1]) {
            span[1] = leave;
        }
        return span[0] > span[1];
    }
}
