package com.bitsson.gensokyou.danmaku.track;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 玫瑰线花形的几何性质。
 *
 * <p>这些断言守的是「花瓣真的排成了花瓣」——玫瑰线是 {@code r = base + amp·cos(k·θ)}，
 * 少一个 {@code cos} 就退化成圆环，多一次 {@code k} 就排错了瓣数，两者都不会报错。
 */
class RosetteGeometryTest {

    private static final Vec3 ORIGIN = new Vec3(0, 0, 0);
    private static final Vec3 FORWARD = new Vec3(0, 0, 1);
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final Vec3 RIGHT = FORWARD.cross(UP).normalize();

    private static List<Geometry.Shot> petals(int count, int petalCount, double base, double amp) {
        return Geometry.build(Shape.ROSETTE, ORIGIN, FORWARD, UP,
                Shape.Params.defaults().count(count).radius(base).rose(petalCount, amp),
                0.0D, 0.0D);
    }

    private static double radiusOf(Geometry.Shot shot) {
        return Math.sqrt(shot.origin().x * shot.origin().x
                + shot.origin().y * shot.origin().y
                + shot.origin().z * shot.origin().z);
    }

    /**
     * 弹在该平面内的方位角（弧度），在<b>生成时用的基</b>（right, up）里测量。
     *
     * <p>刻意不用 {@code atan2(y, x)}：本测试的 {@code right} 是
     * {@code forward × up = (-1,0,0)}，即 x 轴朝负方向，直接用世界 x/y 量角会把
     * 角度整体镜像，分瓣的桶全错位。坐标系必须与生成时一致——这不是测试的洁癖，
     * 正是「同一个概念必须在同一个基里表达」这条纪律。
     */
    private static double angleOf(Geometry.Shot shot) {
        return Math.atan2(shot.origin().dot(UP), shot.origin().dot(RIGHT));
    }

    /** 数量 MUST 严格等于声明值——「每瓣十几颗」是这张图形的全部意义。 */
    @Test
    void producesExactlyTheRequestedBulletCount() {
        for (int petals : new int[]{3, 5, 6, 8}) {
            for (int perPetal : new int[]{1, 4, 12, 20}) {
                assertEquals(petals * perPetal, petals(petals * perPetal, petals, 3.0D, 1.8D).size(),
                        "花瓣数 " + petals + " × 每瓣 " + perPetal + " 的弹数不对");
            }
        }
    }

    /** 半径 MUST 严格落在 {@code [base − amp, base + amp]} 内。 */
    @Test
    void radiusStaysWithinDeclaredBounds() {
        List<Geometry.Shot> shots = petals(60, 5, 3.0D, 1.8D);
        for (Geometry.Shot shot : shots) {
            double r = radiusOf(shot);
            assertTrue(r >= 1.2D - 1.0E-6D && r <= 4.8D + 1.0E-6D,
                    "半径 " + r + " 越出 [1.2, 4.8]");
        }
    }

    /**
     * MUST 真的出现「瓣」——半径的极差 MUST 显著大于零。
     *
     * <p>幅度为 0 时会退化成圆环，而圆环放在这里只是「一圈弹」，读不出花。
     * 这条钉住「幅度参数真的起作用」。
     */
    @Test
    void amplitudeActuallyProducesPetals() {
        List<Geometry.Shot> shots = petals(60, 5, 3.0D, 1.8D);
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (Geometry.Shot shot : shots) {
            min = Math.min(min, radiusOf(shot));
            max = Math.max(max, radiusOf(shot));
        }
        assertTrue(max - min > 3.0D,
                "半径极差只有 " + (max - min) + "，根本没排成花瓣（幅度可能没生效）");
    }

    /** 花瓣数 MUST 影响形状：不同花瓣数 MUST 给出不同的半径序列。 */
    @Test
    void petalCountChangesTheShape() {
        String three = radiusSignature(petals(48, 3, 3.0D, 1.8D));
        String five = radiusSignature(petals(60, 5, 3.0D, 1.8D));
        assertTrue(!three.equals(five), "花瓣数 3 与 5 给出了相同的半径序列——cos 的 k 没生效");
    }

    /** 每颗弹 MUST 面向玩家——花在侧面看不该是「一根线」。 */
    @Test
    void liesInThePlaneFacingThePlayer() {
        for (Geometry.Shot shot : petals(40, 5, 3.0D, 1.8D)) {
            assertEquals(0.0D, shot.origin().z, 1.0E-6,
                    "弹的出生点偏离了面向玩家的平面");
        }
    }

    /**
     * 每颗弹的行进方向 MUST 是<b>平面法线</b>，而不是它在平面内的半径方向。
     *
     * <p>这是「花瓣在平面里开合、整组朝玩家压过来」的读法：平面内的远离/靠近由呼吸缩放
     * 负责，<b>行进</b>则必须是法线。早先的实现把初速设成半径方向，于是整组在平面里
     * 向外扩散、沿法线毫无分量——那是一朵「原地开的花」，不是一朵「扑过来的花」。
     */
    @Test
    void everyBulletTravelsAlongThePlaneNormal() {
        for (Geometry.Shot shot : petals(40, 5, 3.0D, 1.8D)) {
            assertEquals(1.0D, shot.direction().dot(FORWARD), 1.0E-9,
                    "弹的行进方向必须是平面法线（forward），实际 " + shot.direction());
            assertEquals(0.0D, shot.direction().dot(RIGHT), 1.0E-9,
                    "行进方向不得含平面内的水平分量");
            assertEquals(0.0D, shot.direction().dot(UP), 1.0E-9,
                    "行进方向不得含平面内的竖直分量");
        }
    }

    /** 全批弹的行进方向 MUST 完全一致——它们是「一组」，不是各自乱飞。 */
    @Test
    void allBulletsShareOneTravelDirection() {
        Vec3 first = petals(60, 5, 3.0D, 1.8D).get(0).direction();
        for (Geometry.Shot shot : petals(60, 5, 3.0D, 1.8D)) {
            assertEquals(1.0D, first.dot(shot.direction()), 1.0E-12,
                    "同一次发射里各弹的行进方向不一致");
        }
    }

    /**
     * 幅度 MUST 被夹到 {@code [0, base]}。
     *
     * <p>超过 base 时内侧半径会变成负数，花瓣穿过花心长到另一边去，看着是「反的」。
     * 夹到 base 之后半径下界恰好是 0（花瓣尖正好触到花心）——那是允许的，
     * 但绝不允许出现负半径或 NaN。
     */
    @Test
    void amplitudeIsClampedSoInnerRadiusNeverGoesNegative() {
        for (Geometry.Shot shot : petals(40, 5, 3.0D, 99.0D)) {
            assertTrue(radiusOf(shot) >= 0.0D, "幅度超过基准时花瓣穿过了花心（半径为负）");
            assertTrue(Double.isFinite(radiusOf(shot)), "幅度超过基准时产生了 NaN/Inf");
        }
        for (Geometry.Shot shot : petals(40, 5, 3.0D, 99.0D)) {
            assertTrue(radiusOf(shot) <= 6.0D + 1.0E-6D,
                    "幅度被夹到基准后，外侧半径不应超过 2×基准");
        }
        for (Geometry.Shot shot : petals(40, 5, 3.0D, -5.0D)) {
            assertTrue(radiusOf(shot) >= 3.0D - 1.0E-6D,
                    "负幅度被当成了花瓣反向，实际只是「换个朝向」，应被夹到 0");
        }
    }

    /**
     * 花瓣数 &lt; 2 MUST 退化成圆环。
     *
     * <p>{@code cos(1·θ)} 会给出一颗心脏线，那是个合法的形状，但与本形状
     * 「参数为 0 即退化成圆」的约定不符——花瓣数填错时不该得到一个意外图形。
     */
    @Test
    void degeneratePetalCountFallsBackToARing() {
        for (int petals : new int[]{1, 0, -3}) {
            for (Geometry.Shot shot : petals(24, petals, 3.0D, 1.8D)) {
                assertEquals(3.0D, radiusOf(shot), 1.0E-6,
                        "花瓣数 " + petals + " 时应退化成半径恒定的圆环");
            }
        }
    }

    /**
     * 每瓣 MUST 把「伸出去」和「收回来」<b>两笔都画到</b>。
     *
     * <p>玫瑰线的一瓣是：半径从极值走到谷底再走回极值，对应 {@code cos(k·θ)} 的一个完整周期。
     * 早先每瓣只铺了该扇区的 40%，只有「伸出去」那一半有弹——读出来是<b>半片花瓣</b>，
     * 而且不报错，只是「看着有点怪」。
     *
     * <p>判据用 {@code cos(k·θ)} 的取值范围：它同时取到 +1 与 −1 才说明两笔都在。
     * <p>（刻意不按角度扇区分桶计数：玫瑰线的<b>相邻两瓣在瓣尖处共点</b>，
     * θ=0 与 θ=2π/k 都是瓣尖，桶边界天然歧义——那是几何事实，不该由测试去绕。）
     */
    @Test
    void everyPetalIsTracedBothOutAndBack() {
        for (int petals : new int[]{3, 5, 6, 8}) {
            List<Geometry.Shot> shots = petals(petals * 20, petals, 3.0D, 1.8D);
            double minPhase = Double.MAX_VALUE;
            double maxPhase = -Double.MAX_VALUE;
            for (Geometry.Shot shot : shots) {
                double phase = Math.cos(petals * angleOf(shot));
                minPhase = Math.min(minPhase, phase);
                maxPhase = Math.max(maxPhase, phase);
            }
            assertTrue(maxPhase > 0.9D, petals + " 瓣：没伸到瓣尖（cos(kθ) 最大只到 " + maxPhase + "）");
            assertTrue(minPhase < -0.9D,
                    petals + " 瓣：没收到谷底（cos(kθ) 最小只到 " + minPhase
                            + "）——只有半片花瓣");
        }
    }

    /**
     * 弹 MUST 铺满整个圆周，MUST NOT 留出明显的角度空洞。
     *
     * <p>这是「花瓣真的闭合」的另一个侧面：只铺部分扇区时，相邻弹之间的角度间隔
     * 会出现一处远大于其余的跳变。
     */
    @Test
    void noAngularGapsAroundTheCircle() {
        int petals = 5;
        int perPetal = 20;
        double sector = Math.PI * 2.0D / petals;
        double[] angles = new double[petals * perPetal];
        List<Geometry.Shot> shots = petals(petals * perPetal, petals, 3.0D, 1.8D);
        for (int i = 0; i < angles.length; i++) {
            angles[i] = angleOf(shots.get(i));
        }
        java.util.Arrays.sort(angles);
        double expected = Math.PI * 2.0D / angles.length;
        double worst = 0.0D;
        for (int i = 0; i < angles.length; i++) {
            double gap = angles[(i + 1) % angles.length] - angles[i];
            if (i == angles.length - 1) {
                gap += Math.PI * 2.0D;
            }
            worst = Math.max(worst, gap);
        }
        assertTrue(worst < expected * 1.6D,
                "圆周上出现了 " + (worst / expected) + " 倍于平均间隔的空洞（花瓣没闭合）");
    }

    private static String radiusSignature(List<Geometry.Shot> shots) {
        StringBuilder sb = new StringBuilder();
        for (Geometry.Shot shot : shots) {
            sb.append(String.format("%.3f", radiusOf(shot))).append(';');
        }
        return sb.toString();
    }
}
