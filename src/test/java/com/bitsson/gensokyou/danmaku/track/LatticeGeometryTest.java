package com.bitsson.gensokyou.danmaku.track;

import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 凌乱激光网（{@link Shape#LATTICE}）——「让玩家置身网中、找夹缝」这个符卡的几何。
 *
 * <p>与 {@code AROUND_TARGET} 的分野是<b>随机性</b>：那一类角度等分、看着像道具生成的阵；
 * 这一类落在球体内、没有任何可读秩序。这些断言守的正是「不整齐」这件事——
 * 均匀排布不会报错，只会让符卡退化成另一种效果。
 */
class LatticeGeometryTest {

    private static final Vec3 BOSS = new Vec3(0, 64, -40);
    private static final Vec3 TARGET = new Vec3(0, 64, 0);
    private static final Vec3 FORWARD = TARGET.subtract(BOSS).normalize();
    private static final Vec3 WORLD_UP = new Vec3(0, 1, 0);

    private static List<Geometry.Shot> web(int count, double radius, double aimDeg, double bias,
                                           long seed) {
        return Geometry.build(Shape.LATTICE, BOSS, FORWARD, TARGET, WORLD_UP,
                Shape.Params.defaults().count(count).radius(radius)
                        .spread(aimDeg).aimBias(bias),
                0.0D, 0.0D, RandomSource.create(seed));
    }

    private static double aimAngleDeg(Geometry.Shot shot) {
        Vec3 toTarget = TARGET.subtract(shot.origin()).normalize();
        return Math.toDegrees(Math.acos(
                Math.max(-1.0D, Math.min(1.0D, shot.direction().dot(toTarget)))));
    }

    /**
     * 发射点 MUST <b>真随机</b>——位置与距离都随机。
     *
     * <p>这条守的是「不是均匀分布」：均匀排布在数量少时一眼就能看出规则，
     * 弹幕读起来像「道具生成的阵」而不是「网」。
     */
    @Test
    void originsAreTrulyRandomNotUniformlySpaced() {
        List<Geometry.Shot> shots = web(60, 6.0D, 60.0D, 0.3D, 12345L);
        Set<Vec3> distinct = new HashSet<>();
        for (Geometry.Shot shot : shots) {
            distinct.add(shot.origin());
        }
        assertEquals(60, distinct.size(), "发射点重复了——不是随机分布");

        // 距离也必须分散：全部挤在同一半径上就还是「环」
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (Geometry.Shot shot : shots) {
            double distance = shot.origin().distanceTo(TARGET);
            min = Math.min(min, distance);
            max = Math.max(max, distance);
        }
        assertTrue(max - min > 2.0D,
                "发射点距离只跨了 " + (max - min) + " 格（" + min + "~" + max
                        + "）——距离没有随机开");
    }

    /** 全部发射点 MUST 落在外半径之内，且离目标足够远（不从玩家身上冒出来）。 */
    @Test
    void originsStayInTheAnnulus() {
        double outer = 6.0D;
        for (Geometry.Shot shot : web(80, outer, 60.0D, 0.3D, 999L)) {
            double distance = shot.origin().distanceTo(TARGET);
            assertTrue(distance <= outer + 1.0E-6D,
                    "发射点离目标 " + distance + "，超出外半径 " + outer);
            assertTrue(distance >= outer / 3.0D - 1.0E-6D,
                    "发射点离目标只有 " + distance + " 格——会从玩家身上冒出来");
        }
    }

    /**
     * 瞄准 MUST <b>逐发独立</b>，且分布刻意不均匀。
     *
     * <p>「有的正好打到玩家、有的偏一点、有的很偏」——这是这个符卡能玩起来的核心。
     * 若所有激光共用一个夹角，整片网就变成一道平行的墙，没有夹缝可找。
     */
    @Test
    void aimAnglesVaryPerShotAcrossTheWholeRange() {
        List<Geometry.Shot> shots = web(120, 6.0D, 90.0D, 0.3D, 4242L);
        int deadOn = 0;
        int mid = 0;
        int wild = 0;
        for (Geometry.Shot shot : shots) {
            double degrees = aimAngleDeg(shot);
            if (degrees < 1.0D) {
                deadOn++;
            } else if (degrees < 45.0D) {
                mid++;
            } else if (degrees > 65.0D) {
                wild++;
            }
        }
        assertTrue(deadOn > 0, "没有任何激光精确瞄准目标——玩家不用躲");
        assertTrue(mid > 0, "没有中等偏角的激光——分布退化成了「全直瞄」或「全散射」");
        assertTrue(wild > 0, "没有大幅偏角的激光——网是整齐的，不是网");
    }

    /** 直瞄比例 MUST 大致等于声明值（否则「几条必躲、其余可穿」的比例不可控）。 */
    @Test
    void aimBiasControlsTheDirectFraction() {
        int count = 200;
        for (double bias : new double[]{0.1D, 0.3D, 0.6D}) {
            int deadOn = 0;
            for (Geometry.Shot shot : web(count, 6.0D, 90.0D, bias, 77L)) {
                if (aimAngleDeg(shot) < 1.0D) {
                    deadOn++;
                }
            }
            double actual = deadOn / (double) count;
            assertTrue(Math.abs(actual - bias) < 0.08D,
                    "声明直瞄比例 " + bias + "，实测 " + actual);
        }
    }

    /** 零直瞄比例 MUST NOT 出现「一条都不瞄准」——那不是网，是随机烟花。 */
    @Test
    void zeroBiasStillProducesSomeDeadOnShotsByChance() {
        int deadOn = 0;
        for (Geometry.Shot shot : web(200, 6.0D, 5.0D, 0.0D, 5L)) {
            if (aimAngleDeg(shot) < 1.0D) {
                deadOn++;
            }
        }
        assertTrue(deadOn == 0 || deadOn > 3,
                "直瞄 0 时命中 " + deadOn + " 条——夹角上限只有 5° 时偶然贴近也算不了「必躲」");
    }

    /** 夹角 MUST 被 LATTICE 的上限约束（网的语义是「从四周朝内收拢」）。 */
    @Test
    void aimAnglesRespectTheLatticeCap() {
        for (Geometry.Shot shot : web(200, 6.0D, 90.0D, 0.0D, 31337L)) {
            assertTrue(aimAngleDeg(shot) <= Geometry.LATTICE_MAX_AIM_DEG + 1.0E-4D,
                    "夹角 " + aimAngleDeg(shot) + " 超出了网的语义上限");
        }
    }

    /** 同一随机种子 MUST 给出同一批弹——否则调试与复现都无从谈起。 */
    @Test
    void sameSeedReproducesExactly() {
        List<Geometry.Shot> a = web(30, 6.0D, 70.0D, 0.35D, 2024L);
        List<Geometry.Shot> b = web(30, 6.0D, 70.0D, 0.35D, 2024L);
        assertEquals(a.size(), b.size());
        for (int i = 0; i < a.size(); i++) {
            assertEquals(a.get(i).origin(), b.get(i).origin(), "同种子下发射点不同");
            assertEquals(a.get(i).direction(), b.get(i).direction(), "同种子下方向不同");
        }
    }

    /** 不同种子 MUST 给出不同的网——否则「随机」是假的。 */
    @Test
    void differentSeedsGiveDifferentWebs() {
        List<Geometry.Shot> a = web(20, 6.0D, 70.0D, 0.3D, 1L);
        List<Geometry.Shot> b = web(20, 6.0D, 70.0D, 0.3D, 2L);
        int same = 0;
        for (int i = 0; i < a.size(); i++) {
            if (a.get(i).origin().equals(b.get(i).origin())) {
                same++;
            }
        }
        assertEquals(0, same, "不同种子却给出了相同的网");
    }

    /** 无随机源的重载 MUST NOT 崩，且产出合法弹。 */
    @Test
    void noRandomSourceOverloadStillWorks() {
        List<Geometry.Shot> shots = Geometry.build(Shape.LATTICE, BOSS, FORWARD, TARGET, WORLD_UP,
                Shape.Params.defaults().count(12).radius(5.0D).spread(60.0D).aimBias(0.3D),
                0.0D, 0.0D);
        assertFalse(shots.isEmpty());
        for (Geometry.Shot shot : shots) {
            assertTrue(Double.isFinite(shot.direction().x)
                    && Double.isFinite(shot.direction().y)
                    && Double.isFinite(shot.direction().z));
        }
    }
}
