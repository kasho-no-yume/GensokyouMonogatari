package com.bitsson.gensokyou.danmaku;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link DanmakuHitScan#sweepAabb} 的纯几何断言。
 *
 * <p>这是命中判定里唯一不依赖世界状态的部分，也是本次把判定从原版
 * {@code getHitResultOnMoveVector} 挪出来之后唯一可离线覆盖的部分——故必须有测试，
 * 否则扫掠的正确性完全靠实机目视。
 */
class DanmakuHitScanTest {

    private static final double EPS = 1.0E-9D;

    /**
     * 查询盒 MUST 覆盖从起始碰撞盒到终点碰撞盒的全域，且 MUST NOT 相对位置再平移一次。
     *
     * <p><b>本条守的是两处已发生过的严重错误</b>：曾把
     * {@code bulletBox.move(start).expandTowards(end)} 当作 vanilla 的写法照抄，但
     * <ul>
     *   <li>{@code AABB.move} 是<b>平移</b>，而实体碰撞盒已经是世界坐标——查询盒被挪到
     *       {@code 2 × 位置}，弹幕<b>仅在世界原点附近才碰巧命中</b></li>
     *   <li>{@code AABB.expandTowards} 是<b>按增量</b>扩展，传绝对终点会按「距原点多远」
     *       去扩——在 x=1000 处多扩 1000 格</li>
     * </ul>
     * 两处都需要实体与世界才能暴露，原点附近测试时完全看不出来。故用远离原点的坐标断言。
     */
    @Test
    void queryBoxIsNotDoubleTranslated() {
        Vec3 far = new Vec3(1000.5D, 64.0D, -800.25D);
        AABB bulletBox = new AABB(far.x - 0.2D, far.y - 0.2D, far.z - 0.2D,
                far.x + 0.2D, far.y + 0.2D, far.z + 0.2D);
        Vec3 delta = new Vec3(0.5D, 0.0D, 0.0D);
        AABB query = DanmakuHitScan.queryBox(bulletBox, delta);

        // x 向跨度应恰为「弹宽 0.4 + 位移 0.5 + 外扩 2」= 2.9。若按绝对坐标扩展则会宽达上千格。
        // 注意 AABB#getSize() 返回三轴跨度的【最小值】，故此处显式取 x 跨度。
        assertEquals(2.9D, query.maxX - query.minX, 1.0E-6D,
                "查询盒 x 跨度应只含位移与外扩，不应按绝对坐标膨胀");
        assertTrue(bulletBox.intersects(query),
                "查询盒必须仍与弹自身碰撞盒相交（far=" + far + "）");
    }

    /** 查询盒必须覆盖运动终点，否则终点处的目标会在查询阶段就被漏掉。 */
    @Test
    void queryBoxCoversEndOfMotion() {
        Vec3 start = new Vec3(50.0D, 70.0D, 50.0D);
        AABB bulletBox = new AABB(49.8D, 69.8D, 49.8D, 50.2D, 70.2D, 50.2D);
        Vec3 delta = new Vec3(0.5D, 0.0D, 0.0D);
        AABB query = DanmakuHitScan.queryBox(bulletBox, delta);
        assertTrue(query.contains(start.add(delta)),
                "查询盒必须包含本 tick 的运动终点");
    }

    /** 命中点应是<b>首次入射</b>点，不是中心、不是出射点。 */
    @Test
    void entryPointIsFirstContact() {
        AABB target = new AABB(2.0D, -1.0D, -1.0D, 4.0D, 1.0D, 1.0D);
        Optional<Vec3> hit = DanmakuHitScan.sweepAabb(Vec3.ZERO, new Vec3(6.0D, 0.0D, 0.0D), target);
        assertTrue(hit.isPresent());
        assertEquals(2.0D, hit.get().x, EPS, "入射点应是 x=2（近侧），而非远侧 4 或中心 3");
    }

    /** 反向运动同样取近侧——入射点与运动方向无关。 */
    @Test
    void entryPointForNegativeDirection() {
        AABB target = new AABB(-4.0D, -1.0D, -1.0D, -2.0D, 1.0D, 1.0D);
        Optional<Vec3> hit = DanmakuHitScan.sweepAabb(Vec3.ZERO, new Vec3(-6.0D, 0.0D, 0.0D), target);
        assertTrue(hit.isPresent());
        assertEquals(-2.0D, hit.get().x, EPS, "反向运动时入射点应取 x=-2（近侧）");
    }

    /** 擦身而过不应命中。 */
    @Test
    void missWhenNotIntersecting() {
        AABB target = new AABB(2.0D, 5.0D, -1.0D, 4.0D, 7.0D, 1.0D);
        assertTrue(DanmakuHitScan.sweepAabb(Vec3.ZERO, new Vec3(6.0D, 0.0D, 0.0D), target).isEmpty());
    }

    /** 擦边命中：沿盒的下边缘面平移而过，只擦到一条线也算。 */
    @Test
    void grazingContactHits() {
        AABB target = new AABB(2.0D, 1.0D, -1.0D, 4.0D, 3.0D, 1.0D);
        Optional<Vec3> hit = DanmakuHitScan.sweepAabb(
                new Vec3(0.0D, 1.0D, 0.0D), new Vec3(6.0D, 0.0D, 0.0D), target);
        assertTrue(hit.isPresent(), "恰好沿下边缘面掠过应判命中");
    }

    /** 从下方斜穿过、但始终够不到盒底的情形 MUST NOT 误判为擦边。 */
    @Test
    void passesBelowWithoutGrazing() {
        AABB target = new AABB(2.0D, 1.0D, -1.0D, 4.0D, 3.0D, 1.0D);
        assertTrue(DanmakuHitScan.sweepAabb(Vec3.ZERO, new Vec3(6.0D, 1.0D, 0.0D), target).isEmpty(),
                "射线在盒的 x 区间内始终低于盒底，不应命中");
    }

    /** 起点已在盒内：应立即命中（t=0）。 */
    @Test
    void startInsideHitsImmediately() {
        AABB target = new AABB(-1.0D, -1.0D, -1.0D, 1.0D, 1.0D, 1.0D);
        Optional<Vec3> hit = DanmakuHitScan.sweepAabb(new Vec3(0.5D, 0.0D, 0.0D),
                new Vec3(4.0D, 0.0D, 0.0D), target);
        assertTrue(hit.isPresent());
        assertEquals(0.0D, hit.get().distanceToSqr(new Vec3(0.5D, 0.0D, 0.0D)), EPS,
                "起点已在盒内时入射参数应为 0");
    }

    /**
     * 起点已<b>越过</b>盒子：必须不命中。
     *
     * <p>这条是纯 slab 裁剪最容易写错的分支——若未正确比较
     * {@code enter > exit}，射线会在盒后侧错误地报出命中。
     */
    @Test
    void startPastTargetMisses() {
        AABB target = new AABB(-1.0D, -1.0D, -1.0D, 1.0D, 1.0D, 1.0D);
        assertTrue(DanmakuHitScan.sweepAabb(new Vec3(5.0D, 0.0D, 0.0D),
                new Vec3(4.0D, 0.0D, 0.0D), target).isEmpty(),
                "整条线段都在盒后，不应命中");
    }

    /** 零位移：落在盒内即命中，落在盒外即不命中。 */
    @Test
    void zeroMotionDegeneratesToContainment() {
        AABB target = new AABB(-1.0D, -1.0D, -1.0D, 1.0D, 1.0D, 1.0D);
        assertTrue(DanmakuHitScan.sweepAabb(new Vec3(5.0D, 0.0D, 0.0D), Vec3.ZERO, target).isEmpty());
        assertTrue(DanmakuHitScan.sweepAabb(Vec3.ZERO, Vec3.ZERO, target).isPresent());
    }

    /** 纯 Y 或纯 Z 的位移（该轴近零）不得因除零而误判。 */
    @Test
    void axisAlignedMotionHandled() {
        AABB target = new AABB(-1.0D, 2.0D, -1.0D, 1.0D, 4.0D, 1.0D);
        assertTrue(DanmakuHitScan.sweepAabb(Vec3.ZERO, new Vec3(0.0D, 6.0D, 0.0D), target)
                .isPresent());
        assertTrue(DanmakuHitScan.sweepAabb(new Vec3(9.0D, 0.0D, 0.0D),
                new Vec3(0.0D, 6.0D, 0.0D), target).isEmpty(),
                "X 轴已在盒外且 X 向无位移，不应命中");
    }

    /** 包围盒的边界条件：恰好擦到角点算命中。 */
    @Test
    void cornerContactHits() {
        AABB target = new AABB(1.0D, 1.0D, 1.0D, 2.0D, 2.0D, 2.0D);
        assertTrue(DanmakuHitScan.sweepAabb(Vec3.ZERO, new Vec3(4.0D, 4.0D, 4.0D), target)
                .isPresent(), "对角线恰好穿过 (1,1,1) 角点应判命中");
    }

    /** 反向扫掠与正向扫掠的入射点应各自取近侧（互为镜像）。 */
    @Test
    void sweepIsSymmetric() {
        AABB target = new AABB(-1.0D, -1.0D, -1.0D, 1.0D, 1.0D, 1.0D);
        Vec3 from = new Vec3(4.0D, 0.0D, 0.0D);
        Vec3 to = new Vec3(-4.0D, 0.0D, 0.0D);
        Vec3 forward = DanmakuHitScan.sweepAabb(from, to.subtract(from), target).orElseThrow();
        Vec3 backward = DanmakuHitScan.sweepAabb(to, from.subtract(to), target).orElseThrow();
        assertEquals(1.0D, forward.x, EPS);
        assertEquals(-1.0D, backward.x, EPS);
        assertFalse(forward.equals(backward));
    }
}
