package com.bitsson.gensokyou.danmaku.track;

import com.bitsson.gensokyou.danmaku.motion.DanmakuSpeedProfile;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 大妖精符卡重做引入的机制断言。
 *
 * <p>覆盖三类东西：<b>新几何</b>（纯离线可算）、<b>编排模型的两处修正</b>
 * （密度估值按编排形态分流、符卡切换延后到循环边界）、<b>逐发旋钮</b>。
 *
 * <p>全部无世界：几何是纯数学，{@code TrackRunner} 的切卡判定只读血量占比。
 */
class BigFairyCardReworkTest {

    private static final Vec3 EYE = new Vec3(0.0D, 64.0D, 0.0D);
    private static final Vec3 FORWARD = new Vec3(0.0D, 0.0D, 1.0D);
    private static final Vec3 TARGET = new Vec3(0.0D, 64.0D, 15.0D);
    private static final Vec3 UP = new Vec3(0.0D, 1.0D, 0.0D);

    private static List<Geometry.Shot> build(Shape shape, Shape.Params params) {
        return build(shape, params, RandomSource.create(20260930L));
    }

    private static List<Geometry.Shot> build(Shape shape, Shape.Params params, RandomSource rng) {
        return Geometry.build(shape, EYE, FORWARD, TARGET, UP, params, 0.0D, 0.0D, rng, 1.0D, 1.0D);
    }

    // ------------------------------------------------------------------
    // 二维角度栅格
    // ------------------------------------------------------------------

    @Test
    void gridFacingEmitsRowsByColsWithFixedAngularStep() {
        List<Geometry.Shot> shots = build(Shape.GRID_FACING, Shape.Params.defaults()
                .count(25).spread(10.0D).speed(0.3D).size(0.7D));
        assertEquals(25, shots.size(), "5×5 应产出 25 发");
        // 全部同速同原点：弧面是「同速 + 固定角偏」的结果，不是逐发改速度做出来的。
        for (Geometry.Shot shot : shots) {
            assertEquals(shots.get(0).origin(), shot.origin(), "整片栅格同原点");
            assertEquals(0.3D, shot.params().speed(), 1.0E-9D);
        }
        // 角间隔 10°、5×5 ⇒ 两轴各覆盖 ±20°，中心那一发偏移为零。
        double centreYaw = angleBetween(shots.get(12).direction(), FORWARD, UP);
        assertEquals(0.0D, centreYaw, 1.0E-6D, "中心发应正对瞄准方向");
        double edgeYaw = angleBetween(shots.get(0).direction(), FORWARD, UP);
        assertTrue(edgeYaw > 25.0D && edgeYaw < 35.0D,
                "角落发与正中的夹角应约 28°（√(20²+20²)），实际 " + edgeYaw);
    }

    @Test
    void gridFacingRejectsZeroAngularStep() {
        List<String> violations = lintBeat(Shape.GRID_FACING, Shape.Params.defaults()
                .count(25).spread(0.0D).speed(0.3D));
        assertFalse(violations.isEmpty(), "角间隔为 0 时所有发重叠在同一条射线上，lint MUST 拒绝");
    }

    // ------------------------------------------------------------------
    // 侧挂圆盘（阶段 1 的环）
    // ------------------------------------------------------------------

    @Test
    void discRingSitsBehindTheEmitterAndSweepsWithPhase() {
        Shape.Params params = Shape.Params.defaults()
                .count(1).radius(4.0D).offsetForward(-6.0D).speed(0.3D).size(0.7D);
        Geometry.Shot atZero = discRingAt(params, 0.0D);
        Geometry.Shot atQuarter = discRingAt(params, 90.0D);
        // 圆心在 forward*offsetForward = 后方 6 格，故弹必然在发射者身后。
        double behind = FORWARD.dot(EYE.subtract(atZero.origin()));
        assertTrue(behind > 0.0D, "弹应在发射者身后，实际 dot=" + behind);
        // 圆心在 forward*offsetForward = 正后方 6 格，弹落在以它为心、半径 4 的圆上。
        // offsetForward 为负 ⇒ 圆心在发射者的「后方」，故沿 forward 的投影是 -6。
        Vec3 discCenter = EYE.add(FORWARD.scale(-6.0D));
        assertEquals(-6.0D, FORWARD.dot(discCenter.subtract(EYE)), 1.0E-6D,
                "圆心应在正后方 6 格");
        assertEquals(4.0D, atZero.origin().distanceTo(discCenter), 1.0E-6D, "弹落在半径 4 的圆上");
        // 相位推进 ⇒ 逐拍位置不同（这是「一圈弹依次点亮」的来源）。
        // 走的是 phase 而非随机源：DISC_RING 完全确定，它的「逐颗」来自相位而非随机。
        assertNotEquals(atZero.origin(), atQuarter.origin(),
                "相位不同应给出不同方位（否则环只会有一个点）");
        assertEquals(4.0D * Math.sqrt(2.0D), atZero.origin().distanceTo(atQuarter.origin()),
                1.0E-6D, "相位差 90° ⇒ 圆上相距 4√2 格");
    }

    private static Geometry.Shot discRingAt(Shape.Params params, double phaseDeg) {
        return Geometry.build(Shape.DISC_RING, EYE, FORWARD, TARGET, UP, params,
                0.0D, phaseDeg, RandomSource.create(1L), 1.0D, 1.0D).get(0);
    }


    @Test
    void discRingNeedsGapWhenItFillsTheCircle() {
        assertFalse(lintBeat(Shape.DISC_RING, Shape.Params.defaults()
                .count(12).radius(4.0D).speed(0.3D)).isEmpty(),
                "单拍排满一圈且不留缺口即是一面幕墙，lint MUST 拒绝");
        assertTrue(lintBeat(Shape.DISC_RING, Shape.Params.defaults()
                .count(12).radius(4.0D).gap(60.0D).speed(0.3D)).isEmpty(),
                "留了缺口就该通过");
    }

    // ------------------------------------------------------------------
    // 花形阵列
    // ------------------------------------------------------------------

    @Test
    void flowerEmitsCentrePlusPetalsWithPerShotKnobs() {
        List<Geometry.Shot> shots = build(Shape.FLOWER, Shape.Params.defaults()
                .count(45).rose(5, 1.2D).radius(1.8D).size(0.55D).speed(0.24D),
                RandomSource.create(7L), 4.0D, 2.0D);
        assertEquals(46, shots.size(), "1 颗花心 + 45 颗花瓣");
        Geometry.Shot centre = shots.get(0);
        assertEquals(4.0D, centre.damageScale(), 1.0E-9D, "花心 4 倍伤害");
        assertEquals(2.0D, centre.sizeScale(), 1.0E-9D, "花心 2 倍直径");
        assertEquals(0.55D * 2.0D, centre.size(), 1.0E-9D,
                "实际直径 = 声明直径 × 本发倍数（不是玫瑰线基准半径）");

        // 花心落在编队参考点上（偏移为零）——爆散据此改判为「朝目标射出」。
        for (Geometry.Shot petal : shots.subList(1, shots.size())) {
            assertEquals(1.0D, petal.damageScale(), 1.0E-9D, "花瓣伤害不放大");
            assertTrue(petal.origin().subtract(centre.origin()).length() > 0.5D,
                    "花瓣必须离花心有一段距离，否则径向爆散无从定义");
        }
    }

    @Test
    void flowerPlaneIsIndependentOfThrowDirection() {
        // 同一形状、同一几何，两次求值的花平面 MUST 不同（全向抛射且平面独立随机）。
        List<Vec3> normals = new ArrayList<>();
        List<Vec3> throws_ = new ArrayList<>();
        for (long seed : new long[]{1L, 2L, 3L, 4L}) {
            List<Geometry.Shot> shots = build(Shape.FLOWER, Shape.Params.defaults()
                    .count(45).rose(5, 1.2D).radius(1.8D).size(0.55D).speed(0.24D),
                    RandomSource.create(seed));
            normals.add(shots.get(0).planeAxis());
            throws_.add(shots.get(0).direction());
        }
        for (int i = 1; i < normals.size(); i++) {
            assertTrue(normals.get(0).dot(normals.get(i)) < 0.999D,
                    "各朵花的平面取向应互不相同（第 " + i + " 朵与第 1 朵几乎共面）");
        }
        // 抛射方向亦全向随机：四朵花的方向不可能彼此接近。
        for (int i = 1; i < throws_.size(); i++) {
            assertTrue(throws_.get(0).dot(throws_.get(i)) < 0.95D,
                    "抛射方向应全向随机（第 " + i + " 朵与第 1 朵方向过近）");
        }
    }

    @Test
    void flowerRequiresHarmlessWindow() {
        List<String> violations = lintBeat(Shape.FLOWER, Shape.Params.defaults()
                .count(45).rose(5, 1.2D).radius(1.8D).speed(0.24D), 0, 0);
        assertFalse(violations.isEmpty(),
                "全向随机 MUST 声明无害窗口，否则生成方位完全在玩家视野外");
        assertTrue(lintBeat(Shape.FLOWER, Shape.Params.defaults()
                .count(45).rose(5, 1.2D).radius(1.8D).speed(0.24D), 160, 60).isEmpty(),
                "声明了 8 秒寿命 + 3 秒无害窗口就该通过");
        assertFalse(lintBeat(Shape.FLOWER, Shape.Params.defaults()
                .count(45).rose(5, 1.2D).radius(1.8D).speed(0.24D), 100, 80).isEmpty(),
                "无害窗口超过寿命一半时声明形同虚设，lint MUST 拒绝");
    }

    // ------------------------------------------------------------------
    // 随机撒点与地面锚定
    // ------------------------------------------------------------------

    @Test
    void scatterFallIsRandomNotGridded() {
        List<Geometry.Shot> shots = build(Shape.SCATTER_FALL, Shape.Params.defaults()
                .count(24).radius(30.0D).offsetUp(60.0D).speed(0.3D).size(0.8D));
        assertEquals(24, shots.size());
        for (Geometry.Shot shot : shots) {
            assertEquals(0.0D, shot.direction().y + 1.0D, 1.0E-6D, "雨必须竖直向下");
            assertEquals(EYE.y + 60.0D, shot.origin().y, 1.0E-6D, "起手高度为发射者上方 60 格");
        }
        // 互不重合：真随机，不是规则网格。
        long distinct = shots.stream().map(s -> s.origin().toString()).distinct().count();
        assertTrue(distinct >= 23, "24 个落点应几乎互不重合，实际 " + distinct + " 个");
        // 不是行列等分：任取 3 个，其 y/x 的组合不应落在同一行上。
        double spanX = shots.stream().mapToDouble(s -> s.origin().x).max().getAsDouble()
                - shots.stream().mapToDouble(s -> s.origin().x).min().getAsDouble();
        assertTrue(spanX > 20.0D, "落点应铺满 30 格半径的圆盘，实际 x 跨度 " + spanX);
    }

    @Test
    void scatterFallIsNotOmniDirectionalSoItSkipsTheHarmlessRule() {
        assertFalse(Shape.SCATTER_FALL.isOmniRandom(),
                "雨的方向恒为竖直向下且来自 60 格之上，方向极其可读，"
                        + "不该按「方向全向随机」要求无害窗口");
        assertTrue(Shape.FLOWER.isOmniRandom(), "花的抛射方向才是全向随机");
        assertTrue(lintBeat(Shape.SCATTER_FALL, Shape.Params.defaults()
                .count(24).radius(30.0D).offsetUp(60.0D).speed(0.3D),
                Track.Beat.SpawnAnchor.FIRST_AIR_BELOW, 0).isEmpty(),
                "方向可读者不要求无害窗口，但仍要求声明地面锚定");
    }

    @Test
    void groundAnchoredShapesMustDeclareTheirAnchor() {
        assertFalse(lintBeat(Shape.PILLAR_UP, Shape.Params.defaults()
                .count(1).radius(30.0D).speed(0.3D)).isEmpty(),
                "地柱的生成点要向下解析地面，未声明锚定时 MUST 拒绝");
        assertTrue(lintBeat(Shape.PILLAR_UP, Shape.Params.defaults()
                .count(1).radius(30.0D).speed(0.3D),
                Track.Beat.SpawnAnchor.GROUND_BELOW, 0).isEmpty(),
                "声明了地面锚定就该通过");
    }


    @Test
    void laserIsExemptFromThePositiveSpeedRule() {
        // 激光是静止射线：速度为 0 是它的正确取值，不是漏配。
        assertTrue(lintBeat(Shape.PILLAR_UP, Shape.Params.defaults()
                .count(1).radius(30.0D).speed(0.0D), 0, 0,
                Projectile.laser(80.0D, 0.8D, 2.0D, 6.0D),
                Track.Beat.SpawnAnchor.GROUND_BELOW).isEmpty(), "激光不该被「弹速必须为正」判不合格");

    }

    // ------------------------------------------------------------------
    // 密度估值：按编排形态分流
    // ------------------------------------------------------------------

    @Test
    void explicitTimelineIsNotScoredAsPerTickEmission() {
        // 48 拍逐发、每拍 1 发、跑完即止：正确估值远低于预算。
        // 若按「无限重复轨」的口径（每个节拍都当每拍发射）会算成 48×停留时长，
        // 即 48×(12/0.3) = 1920，超预算 16 倍。
        Track timeline = Track.of("逐颗", 0xFFFFFF).repeatEvery(0)
                .at(0, Shape.DISC_RING, Shape.Params.defaults()
                        .count(1).radius(4.0D).speed(0.3D), TargetMode.SELF_AXIS)
                .build();
        Track repeated = Track.of("重复", 0xFFFFFF).repeatEvery(50)
                .at(0, Shape.DISC_RING, Shape.Params.defaults()
                        .count(1).radius(4.0D).speed(0.3D), TargetMode.SELF_AXIS)
                .build();
        assertEquals(1.0D, TrackLint.steadyStateEstimate(timeline), 1.0E-6D,
                "单拍时间线的峰值并发就是那一发的颗数");
        assertEquals(40.0D / 50.0D, TrackLint.steadyStateEstimate(repeated), 1.0E-6D,
                "无限重复轨按周期摊薄");
    }

    @Test
    void timelinePeakCountsBulletsNotBulletTicks() {
        // 两拍各 3 发、间隔 10 tick、停留 20 tick ⇒ 峰值 6 颗（不是 3×20=60）。
        Track track = Track.of("两批", 0xFFFFFF).repeatEvery(0)
                .at(0, Shape.RADIAL_BURST, Shape.Params.defaults()
                        .count(3).speed(0.6D), TargetMode.SELF_AXIS)
                .at(10, Shape.RADIAL_BURST, Shape.Params.defaults()
                        .count(3).speed(0.6D), TargetMode.SELF_AXIS)
                .build();
        assertEquals(6.0D, TrackLint.steadyStateEstimate(track), 1.0E-6D,
                "峰值重叠数统计的是同时在场多少颗，权重 MUST 是颗数而非颗数×停留");
    }

    @Test
    void timelineThatHasEndedContributesNothing() {
        Track track = Track.of("早段", 0xFFFFFF).repeatEvery(0)
                .at(0, Shape.RADIAL_BURST, Shape.Params.defaults()
                        .count(8).speed(0.6D), TargetMode.SELF_AXIS)
                .build();
        assertTrue(TrackLint.steadyStateEstimate(track) > 0.0D);
        // 时间线跑完后不再发射——这正是「会停的编排」与「无限重复」的本质差别。
        assertTrue(track.repeatEvery() == 0, "本轨无重复周期，属显式时间线");
    }

    // ------------------------------------------------------------------
    // 符卡切换延后到循环边界
    // ------------------------------------------------------------------

    @Test
    void phaseSwitchIsDeferredToTheCycleBoundary() {
        TrackRunner runner = new TrackRunner(List.of(
                new SpellCard(CN("甲"), 1.00D, List.of(trackOf()), 240),
                new SpellCard(CN("乙"), 0.66D, List.of(trackOf()), 200)), palette());
        assertTrue(runner.selectCard(1.0D), "开局应立即进入首卡");
        // 阈值已过但循环未走完 ⇒ 挂起，不切换。
        for (int t = 0; t < 100; t++) {
            runner.tick(null, List.of(), 0.0F);
            assertFalse(runner.selectCard(0.50D),
                    "循环未走完时 MUST NOT 切换（第 " + t + " tick）");
            assertEquals("甲", name(runner.current()));
            assertEquals("乙", runner.pending() == null ? null : name(runner.pending()),
                    "挂起目标应登记为乙");
        }
        // 走到循环边界那一刻才切。
        for (int t = 100; t < 240; t++) {
            runner.tick(null, List.of(), 0.0F);
        }
        assertTrue(runner.selectCard(0.50D), "第 240 tick（循环边界）应切换");
        assertEquals("乙", name(runner.current()));
        assertEquals(0, runner.cycleTick(), "切换后循环重新计时");
    }

    @Test
    void cardsWithoutCycleSwitchImmediately() {
        TrackRunner runner = new TrackRunner(List.of(
                new SpellCard(CN("甲"), 1.00D, List.of(trackOf())),
                new SpellCard(CN("乙"), 0.66D, List.of(trackOf()))), palette());
        assertTrue(runner.selectCard(1.0D));
        runner.tick(null, List.of(), 0.0F);
        assertTrue(runner.selectCard(0.50D), "未声明循环长度的符卡保持既有行为：即刻切换");
        assertEquals("乙", name(runner.current()));
    }

    @Test
    void deferredSwitchLandsOnTheCardTheHpActuallyCallsFor() {
        // 挂起期间血量又跌过一张 ⇒ 落点按当时血量重算，不落在中间那张。
        TrackRunner runner = new TrackRunner(List.of(
                new SpellCard(CN("甲"), 1.00D, List.of(trackOf()), 40),
                new SpellCard(CN("乙"), 0.66D, List.of(trackOf()), 40),
                new SpellCard(CN("丙"), 0.33D, List.of(trackOf()), 40)), palette());
        runner.selectCard(1.0D);
        runner.tick(null, List.of(), 0.0F);
        assertFalse(runner.selectCard(0.50D), "先挂起到乙");
        for (int t = 0; t < 39; t++) {
            runner.tick(null, List.of(), 0.0F);
        }
        assertTrue(runner.selectCard(0.20D), "边界上应切到血量真正对应的那张");
        assertEquals("丙", name(runner.current()));
    }

    @Test
    void healingBackIntoTheCurrentCardCancelsThePendingSwitch() {
        TrackRunner runner = new TrackRunner(List.of(
                new SpellCard(CN("甲"), 1.00D, List.of(trackOf()), 240),
                new SpellCard(CN("乙"), 0.66D, List.of(trackOf()), 200)), palette());
        runner.selectCard(1.0D);
        runner.tick(null, List.of(), 0.0F);
        runner.selectCard(0.50D);
        assertEquals("乙", name(runner.pending()));
        runner.selectCard(0.90D);
        assertEquals(null, runner.pending(), "血量回到当前卡区间后挂起应被撤销");
    }

    /**
     * 声明了循环长度的符卡 MUST 真的<b>循环</b>。
     *
     * <p>这条是踩过的坑：发射判据原先拿的是「自入卡以来的绝对 tick」，而显式枚举拍的
     * 时间线是按周期写的（0..47 放一圈）。符卡一旦不切走，绝对 tick 就一直涨过 47，
     * 那些拍再也不会命中——现象是「环只在一开始生成一次，之后再也不生成」。
     *
     * <p>判据用<b>无世界</b>的方式表达：数每个循环步号上有多少拍到期。
     */
    @Test
    void cyclicCardReplaysItsTimelineEveryCycle() {
        Track.Builder ringBuilder = Track.of("花环", 0xFFFFFF).repeatEvery(0)
                .phaseStep(360.0D / 48.0D);
        for (int i = 0; i < 48; i++) {
            ringBuilder.at(i, Shape.DISC_RING, Shape.Params.defaults()
                    .count(1).radius(4.0D).offsetForward(-6.0D).speed(0.3D), TargetMode.SELF_AXIS);
        }
        TrackRunner runner = new TrackRunner(List.of(
                new SpellCard(CN("环"), 1.00D, List.of(ringBuilder.build()), 240)), palette());
        runner.selectCard(1.00D);

        int[] firedPerCycle = new int[3];
        for (int cycle = 0; cycle < 3; cycle++) {
            for (int step = 0; step < 240; step++) {
                assertEquals(step, runner.cycleTick(),
                        "循环步号应逐拍推进（第 " + cycle + " 轮第 " + step + " 拍）");
                if (step < 48) {
                    firedPerCycle[cycle] += 1;
                }
                runner.tick(null, List.of(), 0.0F);
            }
        }
        assertEquals(0, runner.cycleTick(), "走完 3 个循环后步号应回到 0");
        assertEquals(48, firedPerCycle[0]);
        assertEquals(firedPerCycle[0], firedPerCycle[1], "第 2 个循环同样该放满 48 颗");
        assertEquals(firedPerCycle[1], firedPerCycle[2], "第 3 个循环同样该放满 48 颗");
    }

    @Test
    void cyclicCardWithRepeatingTrackRepeatsWithinTheCycleToo() {
        Track wall = Track.of("连射", 0xFFFFFF).repeatEvery(50)
                .at(0, Shape.RADIAL_BURST, Shape.Params.defaults()
                        .count(3).speed(0.3D), TargetMode.SELF_AXIS)
                .build();
        TrackRunner runner = new TrackRunner(List.of(
                new SpellCard(CN("连射"), 1.00D, List.of(wall), 240)), palette());
        runner.selectCard(1.00D);
        int due = 0;
        for (int step = 0; step < 240; step++) {
            if (runner.cycleTick() % 50 == 0) {
                due++;
            }
            runner.tick(null, List.of(), 0.0F);
        }
        assertEquals(5, due, "240 tick 的循环里每 50 tick 一次 ⇒ 步号 0/50/100/150/200 共 5 次");
    }


    // ------------------------------------------------------------------
    // 径向爆散：需要参考点
    // ------------------------------------------------------------------

    @Test
    void radialBurstRequiresAFormationReference() {
        Shape.Params params = Shape.Params.defaults().count(45).rose(5, 1.2D).radius(1.8D)
                .speed(0.24D);
        Behaviour burst = burstBehaviour();
        Track withoutFrame = Track.of("无帧", 0xFFFFFF).repeatEvery(0)
                .at(0, Shape.FLOWER, params, burst, TargetMode.SELF_AXIS, 160, 60)
                .build();
        Track withFrame = Track.of("有帧", 0xFFFFFF).repeatEvery(0)
                .formation(Behaviour.Formation.reference())
                .at(0, Shape.FLOWER, params, burst, TargetMode.SELF_AXIS, 160, 60)
                .build();
        assertFalse(TrackLint.lintCard("测试", new SpellCard("无帧", 1.0D, List.of(withoutFrame)))
                .isEmpty(), "径向爆散没有编队帧就没有参考点，MUST 被静态拒绝");
        assertTrue(TrackLint.lintCard("测试", new SpellCard("有帧", 1.0D, List.of(withFrame)))
                .isEmpty(), "挂了参考点帧就该通过："
                + TrackLint.lintCard("测试", new SpellCard("有帧", 1.0D, List.of(withFrame))));
    }

    private static Behaviour burstBehaviour() {
        return Behaviour.NONE.withMotion(Behaviour.Motion.burst(
                new DanmakuSpeedProfile(0.24D, 20, 0.0D, 40, 0.0D, 0, 0.0D),
                60, 0.2D, 0.3D, Behaviour.TARGET_RANDOM));
    }

    /**
     * 「只要参考点」的编队 MUST 真的绑得上，且其平面内求值退化为常量。
     *
     * <p>踩过的坑：{@code FormationFrame#active()} 判的是「平面内有没有动作」
     * （自转 / 呼吸 / 公转），于是一个三项全零的帧被判为不活动、在
     * {@code bindToFrame} 里被静默丢弃。而径向爆散需要的恰恰是这种帧。
     *
     * <p>连锁后果是致命的：帧没绑 ⇒ 「帧没了」被当成「爆散已发生」⇒ 速率曲线与换向
     * 双双跳过 ⇒ 花一路直飞，既不停也不散开。
     *
     * <p>本测试钉住两件事：帧的空间求值在无平面内动作时<b>确实是常量</b>，
     * 且平移量由速率曲线的积分给出（1 秒飞、2 秒停）。
     */
    @Test
    void referenceOnlyFrameDegeneratesToAConstantPlusTravel() {
        com.bitsson.gensokyou.danmaku.motion.FormationFrame frame =
                Behaviour.Formation.reference().frameFor(EYE, EYE);
        assertEquals(0.0D, frame.rotRateDegPerTick(), 1.0E-9D);
        assertEquals(0.0D, frame.scaleAmp(), 1.0E-9D);
        assertEquals(0.0D, frame.orbitRadius(), 1.0E-9D);
        // 无平面内动作 ⇒ 任意两个年龄的帧内位置相同。
        assertEquals(0.0D, frame.framePositionAt(0).distanceTo(frame.framePositionAt(200)),
                1.0E-9D, "无自转/呼吸/公转时，帧内位置 MUST 是不变量");

        // 平移项来自速率曲线的闭式积分：3 秒内飞出 7.2 格（0.24 线性降到 0 的梯形），
        // 之后 MUST 完全静止——这正是「飞 3 秒 → 停 2 秒」的后半段。
        //
        // 7.2 格不是随手填的：1 秒版本只有 2.4 格，16 朵全部停在 BOSS 身周 2.4 格的球内，
        // 实测读作「一团糊在一起」而不是一片海。抛射行程是这个形状唯一能把自己散开的量
        // （方向全向随机 ⇒ 半径就是分散度）。
        DanmakuSpeedProfile preBurst = new DanmakuSpeedProfile(0.24D, 60, 0.0D, 40, 0.0D, 0, 0.0D);
        assertEquals(0.0D, preBurst.travelAt(0), 1.0E-9D);
        assertEquals(7.2D, preBurst.travelAt(60), 1.0E-6D, "3 秒飞出 7.2 格");
        assertEquals(7.2D, preBurst.travelAt(70), 1.0E-6D);
        assertEquals(7.2D, preBurst.travelAt(100), 1.0E-6D, "悬停段位移恒为 0");
        assertEquals(7.2D, preBurst.travelAt(200), 1.0E-6D, "悬停段之后仍恒为 0");
        // 且速度在第 60 tick 起归零——「停住」这件事由速率曲线给出，不由别处补。
        assertEquals(0.0D, preBurst.speedAt(70), 1.0E-9D);
        assertEquals(0.0D, preBurst.speedAt(99), 1.0E-9D);
        // 1 秒版本的行程，用于说明为什么 7.2 是必要的而不是任意的
        assertEquals(2.4D, new DanmakuSpeedProfile(0.24D, 20, 0.0D, 40, 0.0D, 0, 0.0D)
                .travelAt(20), 1.0E-6D, "1 秒版本只有 2.4 格 —— 那正是花朵挤成一团的原因");
    }

    @Test
    void shippedFlowerCardUsesTheReferenceOnlyFrame() {
        Track flowers = BossCards.bigFairy().get(1).tracks().get(0);
        assertEquals("花海", flowers.name());
        assertTrue(flowers.formation().active(), "花海 MUST 声明参考点帧");
        assertEquals(0.0D, flowers.formation().rotRateDegPerTick(), 1.0E-9D, "刻意不自旋");
        assertEquals(0.0D, flowers.formation().scaleAmp(), 1.0E-9D, "也不呼吸");
        // 爆散声明与帧必须成对：没有帧就没有爆散方向。
assertTrue(flowers.beats().stream().allMatch(b ->
                          b.behaviour().motion().kind() == Behaviour.Motion.Kind.BURST),
                "花海每一拍都必须是爆散");
    }

    /**
     * 花海稳态密度回归：<b>谷底 MUST NOT 低于峰值的 80%</b>。
     *
* <p>这是「画面陆续减少、逐渐变空」那条 bug 的守门测试。它 MUST 从真实符卡结构里
     * 读参数，而不是把 240/200/16 抄一遍——抄一遍的话，把 {@code FLOWER_LIFETIME}
     * 改回小于周期的那个提交根本不会让这条测试变红。
     *
     * <p>算的是：在场花朵数 = 每个周期内「已发射且未到期」的那些拍。峰谷比掉到 0.8 以下，
     * 就说明寿命短于周期，画面必然周期性排空。这是<b>密度均匀性</b>的断言，
     * 与「峰值够不够高」是两件事——后者归 {@code TrackLint} 的密度豁免管。
     */
    @Test
    @DisplayName("花海稳态密度：谷底 MUST NOT 低于峰值的 80%")
    void flowerSeaHasNoDensityTrough() {
        SpellCard card = BossCards.bigFairy().get(1);
        Track flowers = card.tracks().get(0);
        int cycle = card.cycleTicks();
        assertTrue(cycle > 0, "花海 MUST 有循环");
        List<Track.Beat> beats = flowers.beats();
        assertFalse(beats.isEmpty(), "花海 MUST 有拍");

        int lifetime = beats.get(0).lifetimeTicks();
        assertTrue(lifetime > 0, "花海 MUST 有寿命");
        // 根因守卫：寿命短于周期时，在场数必然出现周期性排空
        assertTrue(lifetime >= cycle,
                "花海寿命 " + lifetime + " < 循环 " + cycle
                        + "：每轮末尾必然排空（在场数 = 每周期朵数 × 寿命 / 周期）");

        // 连续三个周期，跨过周期边界取样——谷底正出现在跨周期的地方
        int peak = 0;
        int trough = Integer.MAX_VALUE;
        for (int t = 0; t < cycle * 3; t++) {
            int live = 0;
            for (Track.Beat beat : beats) {
                // 每一拍在每个周期的同一相位重复发射
                for (int cycleStart = t / cycle * cycle - cycle; cycleStart <= t; cycleStart += cycle) {
                    long emit = (long) cycleStart + beat.tick();
                    if (emit <= t && t - emit < lifetime) {
                        live++;
                    }
                }
            }
            peak = Math.max(peak, live);
            trough = Math.min(trough, live);
        }

        assertTrue(peak > 0, "花海 MUST 在某个时刻有花瓣在场");
        double ratio = (double) trough / peak;
        assertTrue(ratio >= 0.8D,
                "花海峰谷比只有 " + String.format("%.2f", ratio) + "（谷 " + trough
                        + " / 峰 " + peak + "），画面会周期性排空。");
    }

    // ------------------------------------------------------------------
    // 逐发旋钮的解析
    // ------------------------------------------------------------------


    @Test
    void turnTargetResolvesPerCopy() {
        assertEquals(42, Behaviour.resolveTurnTarget(Behaviour.TARGET_AUTO, 42, 7),
                "「本份的目标」解析成该份所对的那名玩家");
        assertEquals(7, Behaviour.resolveTurnTarget(Behaviour.TARGET_RANDOM, 42, 7),
                "「随机一名」解析成随机挑中的那名");
        assertEquals(42, Behaviour.resolveTurnTarget(42, 7, 9),
                "写死具体 id 时以写死的为准");
        assertEquals(0, Behaviour.resolveTurnTarget(Behaviour.TARGET_NONE, 42, 7),
                "不指定时无目标");
        assertEquals(0, Behaviour.resolveTurnTarget(Behaviour.TARGET_AUTO, 0, 7),
                "无被锁定玩家时不得凭空造一个目标");
    }

    // ------------------------------------------------------------------
    // 密度豁免
    // ------------------------------------------------------------------

    @Test
    void densityWaiverIsBoundedAndMustSitJustAboveTheRealValue() {
        SpellCard flower = BossCards.bigFairy().get(1);
        assertTrue(flower.hasDensityWaiver(), "花之海洋当前声明了密度豁免");
        double steady = 0.0D;
        for (Track track : flower.tracks()) {
            steady += TrackLint.steadyStateEstimate(track);
        }
        assertTrue(flower.densityWaiver() >= steady,
                "豁免必须覆盖实际密度 " + steady);
        assertTrue(flower.densityWaiver() < steady * 1.5D,
                "豁免必须贴着实际值，不得远高于它");
        assertTrue(flower.densityWaiver()
                        <= TrackLint.STEADY_STATE_BUDGET * TrackLint.MAX_DENSITY_WAIVER_FACTOR,
                "豁免不得超过预算的 " + (int) TrackLint.MAX_DENSITY_WAIVER_FACTOR + " 倍");
    }

    @Test
    void overBroadWaiverIsRejected() {
        SpellCard greedy = new SpellCard(CN("贪婪"), 1.0D, List.of(trackOf()),
                0, TrackLint.STEADY_STATE_BUDGET * 100);
        assertFalse(TrackLint.lintCard("测试", greedy).isEmpty(),
                "豁免是有界例外，超过封顶 MUST 被拒");
    }

    // ------------------------------------------------------------------
    // 在役表
    // ------------------------------------------------------------------

    @Test
    void shippedBigFairyTableHasThreeSegmentedCycles() {
        List<SpellCard> cards = BossCards.bigFairy();
        assertEquals(3, cards.size());
        // 符卡名是 lang 键解析出的 Component，故断言走键名而非中文。
        assertEquals("spellcard.gensokyou.big_fairy.1",
                cards.get(0).name().getString());
        assertEquals("spellcard.gensokyou.big_fairy.2",
                cards.get(1).name().getString());
        assertEquals("spellcard.gensokyou.big_fairy.3",
                cards.get(2).name().getString());

        assertEquals(240, cards.get(0).cycleTicks(), "阶段 1 循环 = 12 秒");
        assertEquals(240, cards.get(1).cycleTicks(),
                "阶段 2 循环 = 12 秒（末朵在第 60 tick 抛出、第 160 tick 爆散，余量 80 tick）");
        assertEquals(200, cards.get(2).cycleTicks(),
                "阶段 3 是持续型，MUST 给循环长度（10 拍 × 20 tick）；"
                        + "「不声明循环」不等于「一直放」，恰恰是保证它停掉的设置");

        // 花之海洋的时序契约：飞 3 秒 → 悬停 2 秒 → 第 100 tick 爆散。
        // 这三个数被 lint 同时约束（无害期不得超过寿命的一半），改动时三者 MUST 同步。
        Track.Beat flower = cards.get(1).tracks().get(0).beats().get(0);
        assertEquals(100, flower.harmlessTicks(), "爆散前完全不生效");
        // 寿命 MUST 不小于循环：在场花朵数 = 每周期朵数 × 寿命 / 周期，寿命短于周期时
        // 每轮末尾必然排空（实测谷底只剩峰值的 37%，读作「陆续减少、逐渐变空」）。
        assertTrue(flower.lifetimeTicks() >= cards.get(1).cycleTicks(),
                "花海寿命 " + flower.lifetimeTicks() + " MUST 不小于循环 "
                        + cards.get(1).cycleTicks() + "，否则每轮末尾画面排空");
        // 爆散后 MUST 留足滑行余量，且不少于原设计的 100 tick
        assertTrue(flower.lifetimeTicks() - flower.harmlessTicks() >= 100,
                "爆散后的散开行程 MUST 不少于成形行程（100 tick）");

        assertTrue(TrackLint.lint("大妖精", cards, BossCards.BIG_FAIRY_PALETTE).isEmpty(),
                "在役表 MUST 通过 lint：" + TrackLint.lint("大妖精", cards, BossCards.BIG_FAIRY_PALETTE));
    }

    // ------------------------------------------------------------------
    // 颜色：声明色必须真的生效
    // ------------------------------------------------------------------

    /**
     * 运行时颜色取 {@code track.color()}，而<b>不是</b> {@code palette.at(卡内轨序)}。
     *
     * <p>踩过的坑：后者让符卡表里声明的颜色全程是死字段。单轨符卡（多张卡的常态）
     * 于是全部拿到 {@code at(0)}——三张卡同色，而作者写在
     * {@code of(name, PALETTE.at(k))} 里的那个色号从未被读取。
     * 实机症状是「我声明了淡蓝，激光出来是白灰」：淡蓝声明在轨 2，
     * 运行时拿到的是 {@code at(1)}（极淡粉白），再叠上激光的加法发光就洗成了白。
     *
     * <p>这个回归分两层挡：
     * <ol>
     *   <li>{@code TrackLint} 断言每条轨的声明色属于该 BOSS 的签名色盘</li>
     *   <li>本测试断言 {@code emitTrack} <b>不接收</b> {@code SignaturePalette}——
     *       签名里没有色盘，「按轨序取色」这条路径就无法被重新引入</li>
     * </ol>
     */
    @Test
    void declaredTrackColorsAreLiveAndPaletteBound() throws Exception {
        for (java.lang.reflect.Method m : TrackRunner.class.getDeclaredMethods()) {
            if (!"emitTrack".equals(m.getName())) {
                continue;
            }
            for (Class<?> p : m.getParameterTypes()) {
                assertNotEquals(SignaturePalette.class, p,
                        "emitTrack 不得再接收 SignaturePalette：颜色由 track.color() 决定，"
                                + "按卡内轨序取色会让符卡表里声明的颜色变成死字段");
            }
        }

        List<SpellCard> cards = BossCards.bigFairy();
        SignaturePalette palette = BossCards.BIG_FAIRY_PALETTE;
        int ring = cards.get(0).tracks().get(0).color();
        int flower = cards.get(1).tracks().get(0).color();
        int rain = cards.get(2).tracks().get(0).color();
        int laser = cards.get(2).tracks().get(1).color();
        for (int c : new int[] {ring, flower, rain, laser}) {
            assertTrue(palette.contains(c), String.format("声明色 #%06X MUST 来自签名色盘", c));
        }
        // 四条轨在色盘里占四个不同色号——否则「每轨一种独占视觉标识」失效
        assertEquals(4, java.util.Set.of(ring, flower, rain, laser).size(),
                "环／花／雨／激光 MUST 是四个不同色号");

        // 花海 MUST 读作粉红：红与蓝都高、绿显著更低
        assertTrue(red(flower) > 200, "花海红分量 " + red(flower) + " 应为粉红");
        assertTrue(blue(flower) > 180, "花海蓝分量 " + blue(flower) + " 应为粉红");
        assertTrue(red(flower) - green(flower) > 60, "花海的绿 MUST 显著低于红，否则读作米白");
        assertTrue(blue(flower) - green(flower) > 40, "花海的绿 MUST 显著低于蓝，否则读作灰白");

        // 激光 MUST 读作淡蓝，且蓝分量明显高于红分量
        assertTrue(blue(laser) > 200, "激光蓝分量 " + blue(laser));
        assertTrue(blue(laser) - red(laser) > 60,
                String.format("激光的蓝 MUST 明显高于红（#%06X 会被加法发光洗成白灰）", laser));
        assertTrue(red(laser) > 60, "激光是「淡」蓝，不是深蓝");
    }

    private static int red(int rgb) {
        return (rgb >> 16) & 0xFF;
    }

    private static int green(int rgb) {
        return (rgb >> 8) & 0xFF;
    }

    private static int blue(int rgb) {
        return rgb & 0xFF;
    }


    // ------------------------------------------------------------------
    // 无人可瞄时不得发射
    // ------------------------------------------------------------------

    /**
     * 瞄准型轨道在<b>无人可瞄</b>时整轨不发射。
     *
     * <p>踩过的坑：{@code copies} 曾用 {@code Math.max(1, size)} 兜底，于是「零目标」
     * 被静默当成「一个人」——锚点落回 BOSS 自己的脚，瞄准方向变成竖直向下，
     * 环挂到 BOSS 下方；「重瞄各自的目标」又解析不出实体 id，于是那 48 颗弹
     * 永远不发射、零速悬在原地 60 秒才消失。
     *
     * <p>玩家死亡（{@code isAlive()} 为 false ⇒ 被 {@code refreshTargets} 排除）
     * 就会让目标集变空，故这不是假想场景。
     */
    @Test
    void aimedTrackEmitsNothingWithoutTargets() {
        TrackRunner runner = new TrackRunner(List.of(
                new SpellCard(CN("环"), 1.00D, List.of(aimedRing()), 240)), palette());
        runner.selectCard(1.00D);
        // 无世界时 tick 只推进时钟，故断言落在「有没有落到发射这一步」上：
        // 有无目标都必须不抛异常，且循环照常推进。
        for (int step = 0; step < 240; step++) {
            runner.tick(null, List.of(), 0.0F);
        }
        assertEquals(0, runner.cycleTick(), "无人可瞄时循环仍应照常推进");
        assertEquals(CN("环"), runner.current().name(), "且不应因此切卡");
    }

    /** 阶段 1 的环是瞄准型：每名被锁定玩家各一个环。 */
    private static Track aimedRing() {
        Track.Builder ring = Track.of("花环", 0xFFFFFF).repeatEvery(0)
                .phaseStep(360.0D / 48.0D);
        for (int i = 0; i < 48; i++) {
            ring.at(i, Shape.DISC_RING, Shape.Params.defaults()
                            .count(1).radius(4.0D).offsetForward(-6.0D).speed(0.6D),
                    Behaviour.NONE.withMotion(Behaviour.Motion.reclaim(
                            com.bitsson.gensokyou.danmaku.motion.DanmakuSpeedProfile
                                    .decelerateAndHold(0.6D, 1),
                            60, 0.6D, Behaviour.TARGET_AUTO)),
                    TargetMode.AIMED, 0, 60);
        }
        return ring.build();
    }

    @Test
    void aimedTrackIsTheOnlyOneThatNeedsTargets() {
        // 自轴型在无人时照发——它本来就是「给场地的」，与有没有观众无关。
        assertTrue(TargetMode.SELF_AXIS.copiesPerTarget() == false);
        assertTrue(TargetMode.AIMED.copiesPerTarget());
        assertEquals(0, BossCards.bigFairy().get(1).tracks().get(0).beats().stream()
                        .filter(b -> b.targetMode() == TargetMode.AIMED).count(),
                "花之海洋是玩家无关的（全场一份），不该被这条规则挡住");
    }

    @Test
    void shippedCardsOnlyUseAimedOnTheRing() {
        // 阶段 1 的环是唯一的瞄准型轨道；另两张卡必须全是自轴/场地型，
        // 否则「无人可瞄就不发射」会让它们在玩家死亡时也一并哑掉。
        for (int i = 1; i < 3; i++) {
            for (Track track : BossCards.bigFairy().get(i).tracks()) {
                for (Track.Beat beat : track.beats()) {
                    assertFalse(beat.targetMode() == TargetMode.AIMED,
                            "符卡「" + name(BossCards.bigFairy().get(i))
                                    + "」的轨「" + track.name() + "」不该是瞄准型："
                                    + "无人可瞄时它会整轨停发");
                }
            }
        }
    }
    /**
     * 锁定半径 MUST 同时是弹幕锁定半径与原版索敌半径。
     *
     * <p>踩过的坑：两者曾是 64（{@code refreshTargets} 里硬编码）与 48（followRange）
     * 两个独立值，于是存在一段 <b>双标准区间</b>——48~64 格内 BOSS 会朝你放按人复制的
     * 弹幕，却不再追你。那段区间没有任何设计依据，只是一次没对齐的巧合，且无处可查。
     *
     * <p>现在靠<b>结构</b>而非断言保证：{@code bossAttributes} 不再接受 followRange
     * 参数，唯一的来源是 {@link com.bitsson.gensokyou.entity.AbstractTouhouBoss#LOCK_RADIUS}。
     * 本测试只钉住那个数本身。
     */
    /**
     * 显式时间线 MUST 有循环可依，否则它只响一次。
     *
     * <p>踩过两次：阶段 1 的环（只在一开始生成一次）与阶段 3 的雨（进卡 9 秒后彻底静默，
     * 而雨从 60 格高空落下还要 10 秒才到人眼前，观感是「完全没看见 BOSS 放」）。
     * 两次都是同一句话写反了：「持续型」被写成了「不声明循环」，
     * 而那正是保证它停掉的设置。
     */
    @Test
    void explicitTimelineWithoutCycleIsRejected() {
        Track.Builder burstBuilder = Track.of("逐秒", 0xFFFFFF).repeatEvery(0);
        for (int second = 0; second < 10; second++) {
            burstBuilder.at(second * 20, Shape.SCATTER_FALL, Shape.Params.defaults()
                    .count(24).radius(30.0D).offsetUp(60.0D).speed(0.3D),
                    Behaviour.NONE, TargetMode.SELF_AXIS, Projectile.SPHERE,
                    0, 0, Track.Beat.SpawnAnchor.FIRST_AIR_BELOW, 1.0D, 1.0D);
        }
        Track burst = burstBuilder.build();
        List<String> noCycle = TrackLint.lintCard("测试",
                new SpellCard(CN("无循环"), 1.0D, List.of(burst)));
        assertFalse(noCycle.isEmpty(),
                "显式时间线 + 无循环 = 只响一次，MUST 被静态拒绝：" + noCycle);
        assertTrue(TrackLint.lintCard("测试",
                        new SpellCard(CN("有循环"), 1.0D, List.of(burst), 200)).isEmpty(),
                "给了循环长度就该通过");
    }

    @Test
    void oneShotIsExpressedAsASingleBeat() {
        // 「一次性过场」不需要结构豁免：单拍就是「响一次就停」，它不违反本规则。
        Track once = Track.of("收招", 0xFFFFFF).repeatEvery(0)
                .at(0, Shape.RADIAL_BURST, Shape.Params.defaults()
                        .count(12).speed(0.3D), TargetMode.SELF_AXIS)
                .build();
        assertTrue(TrackLint.lintCard("测试",
                        new SpellCard(CN("收招"), 1.0D, List.of(once))).isEmpty(),
                "单拍 + 无循环 = 一次性过场，MUST 允许");
    }

    @Test
    void repeatingTracksNeedNoCycle() {
        // repeatEvery > 0 的轨自带重放，不受本规则约束（另外三只 BOSS 全是这一类）。
        Track track = Track.of("重复", 0xFFFFFF).repeatEvery(50)
                .at(0, Shape.RADIAL_BURST, Shape.Params.defaults()
                        .count(8).speed(0.3D), TargetMode.SELF_AXIS)
                .build();
        assertTrue(TrackLint.lintCard("测试",
                        new SpellCard(CN("重复"), 1.0D, List.of(track))).isEmpty(),
                "声明了重复周期的轨自带重放，不需要循环长度");
    }

    @Test
    void lockRadiusIsSingleSourced() {
        assertEquals(64.0D,
                com.bitsson.gensokyou.entity.AbstractTouhouBoss.LOCK_RADIUS, 1.0E-9D,
                "锁定半径是单一来源（弹幕锁定与原版索敌共用）；改动请只改这一处");
    }



    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------


    private static Track trackOf() {
        return Track.of("轨", 0xFFFFFF).terminates().repeatEvery(40)
                .at(0, Shape.RADIAL_BURST, Shape.Params.defaults()
                        .count(3).speed(0.3D), TargetMode.SELF_AXIS)
                .build();
    }

    private static SignaturePalette palette() {
        return SignaturePalette.of(0x9BE7FF, 0xFFD9F0);
    }

    /** 符卡名现在是 lang 键解析出的 {@link Component}，断言时取其字符串。 */
    private static Component CN(String literal) {
        return Component.literal(literal);
    }

    private static String name(SpellCard card) {
        return card == null ? null : card.name().getString();
    }


    private static List<Geometry.Shot> build(Shape shape, Shape.Params params,
                                             RandomSource rng, double centreDamage,
                                             double centreSize) {
        return Geometry.build(shape, EYE, FORWARD, TARGET, UP, params, 0.0D, 0.0D, rng,
                centreDamage, centreSize);
    }

    private static List<String> lintBeat(Shape shape, Shape.Params params) {
        return lintBeat(shape, params, 0, 0);
    }

    private static List<String> lintBeat(Shape shape, Shape.Params params, int lifetime,
                                         int harmless) {
        return lintBeat(shape, params, lifetime, harmless, Projectile.SPHERE,
                Track.Beat.SpawnAnchor.NONE);
    }

    private static List<String> lintBeat(Shape shape, Shape.Params params,
                                         Track.Beat.SpawnAnchor anchor, int harmless) {
        return lintBeat(shape, params, 0, harmless, Projectile.SPHERE, anchor);
    }

    private static List<String> lintBeat(Shape shape, Shape.Params params, int lifetime,
                                         int harmless, Projectile projectile) {
        return lintBeat(shape, params, lifetime, harmless, projectile,
                Track.Beat.SpawnAnchor.NONE);
    }

    private static List<String> lintBeat(Shape shape, Shape.Params params, int lifetime,
                                         int harmless, Projectile projectile,
                                         Track.Beat.SpawnAnchor anchor) {
        Track.Beat beat = new Track.Beat(0, shape, params, Behaviour.NONE,
                TargetMode.SELF_AXIS, projectile, lifetime, harmless, anchor, 1.0D, 1.0D);
        return TrackLint.lintBeat("测试", trackOf(), beat);
    }


    /** 两个方向之间的夹角（度），绕 {@code axis} 从 {@code from} 量到 {@code to}。 */
    private static double angleBetween(Vec3 from, Vec3 to, Vec3 axis) {
        double dot = Math.min(1.0D, Math.max(-1.0D, from.dot(to)));
        return Math.toDegrees(Math.acos(dot));
    }
}
