package com.bitsson.gensokyou.danmaku.track;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * 形状 → 发射指令的几何翻译。
 *
 * <p>每条指令 = （发射原点偏移, 方向, 平面内旋转轴或零, 几何参数）。
 * 全部以 BOSS 的局部基向量（forward = 指向主目标，right = 水平右，up = 竖直上）表达，
 * 故任何形状都与 BOSS 的朝向无关。
 *
 * <p><b>本类只产几何</b>。弹怎么动、怎么显、怎么死由 {@link Behaviour} 在
 * {@code DanmakuEmitter} 中施加——本类 MUST NOT 依据形状改写运动（改前
 * {@code GROUND_BAND} 分支会把竖直分量抹掉，那是行为而非几何）。
 *
 * <p>三维设计要点：
 * <ul>
 *   <li>{@link Shape#RING_FACING} 的环平面<b>竖直且面向玩家</b>——玩家能翻过它，
 *       这是二维弹幕给不出的解法（二维只能穿洞）。
 *   <li>{@link Shape#CAGE} 用三个正交竖直面而非一个水平面——空格子才有体积。
 *   <li>所有「缺口」角都由调用方传入的 gapPhase 对齐到玩家方位，
 *       缺口永远朝玩家这一侧（可读性）。
 * </ul>
 */
public final class Geometry {

    /** 一条发射指令。 */
    public record Shot(Vec3 origin, Vec3 direction, Vec3 planeAxis, Shape.Params params) {
    }

    private Geometry() {
    }

    /**
     * 计算一拍的发射指令。
     *
     * @param origin BOSS 发射原点
     * @param forward BOSS 指向目标的单位向量
     * @param worldUp 世界竖直单位向量
     * @param params 形状参数
     * @param gapPhase 缺口对齐角（度）：缺口中心相对玩家方位的偏移，令 0 即「缺口朝着玩家」
     * @param phase 形状自身的相位（度），用于让重复的拍之间错开
     */
    public static List<Shot> build(Shape shape, Vec3 origin, Vec3 forward, Vec3 worldUp,
                                   Shape.Params params, double gapPhase, double phase) {
        Vec3 right = forward.cross(worldUp);
        if (right.lengthSqr() < 1.0E-6D) {
            right = new Vec3(1, 0, 0);
        }
        right = right.normalize();
        Vec3 up = right.cross(forward).normalize();
        return switch (shape) {
            case AIMED_SINGLE -> List.of(new Shot(origin, forward, up, params));

            case FAN -> {
                int n = Math.max(1, params.count());
                double half = params.spreadDeg() * 0.5D;
                List<Shot> out = new ArrayList<>(n);
                for (int i = 0; i < n; i++) {
                    double offset = n <= 1 ? 0.0D : params.spreadDeg() * (i - (n - 1) / 2.0D) / (n - 1);
                    out.add(new Shot(origin, rotateY(forward, up, Math.toRadians(offset)),
                            up, params));
                }
                yield List.copyOf(out);
            }

            case RING_FACING -> {
                // 竖直环：环平面由 right 与 up 张成，法线 = forward（面向玩家）。
                // 缺口对齐玩家方位，环自转由 phase 给出。
                int n = Math.max(3, params.count());
                double step = 360.0D / n;
                double gapCenter = Mth.wrapDegrees(gapPhase + phase);
                List<Shot> out = new ArrayList<>(n);
                for (int i = 0; i < n; i++) {
                    double a = i * step + phase;
                    double d = Math.abs(Mth.wrapDegrees(a - gapCenter));
                    if (d < params.gapDeg() * 0.5D) {
                        continue;
                    }
                    double rad = Math.toRadians(a);
                    Vec3 dir = right.scale(Math.cos(rad)).add(up.scale(Math.sin(rad)));
                    out.add(new Shot(origin, dir, forward, params));
                }
                yield List.copyOf(out);
            }

            case RING_HORIZONTAL -> {
                int n = Math.max(3, params.count());
                double step = 360.0D / n;
                double gapCenter = Mth.wrapDegrees(gapPhase + phase);
                List<Shot> out = new ArrayList<>(n);
                for (int i = 0; i < n; i++) {
                    double a = i * step + phase;
                    if (Math.abs(Mth.wrapDegrees(a - gapCenter)) < params.gapDeg() * 0.5D) {
                        continue;
                    }
                    double rad = Math.toRadians(a);
                    Vec3 dir = right.scale(Math.cos(rad)).add(forward.scale(Math.sin(rad)));
                    out.add(new Shot(origin, dir, worldUp, params));
                }
                yield List.copyOf(out);
            }

            case AXIAL_STAR -> {
                List<Shot> out = new ArrayList<>(6);
                for (Vec3 dir : List.of(right, right.scale(-1), worldUp, worldUp.scale(-1),
                        forward, forward.scale(-1))) {
                    out.add(new Shot(origin, dir, up, params));
                }
                yield List.copyOf(out);
            }

            case CAGE -> {
                // 三个正交竖直面：XZ 环绕 forward、YZ 环绕 right、XY 环绕 up。
                int n = Math.max(3, params.count());
                double step = 360.0D / n;
                List<Shot> out = new ArrayList<>(n * 3);
                List<Vec3> axes = List.of(forward, right, up);
                List<Vec3> planes = List.of(worldUp, up, forward);
                for (int p = 0; p < 3; p++) {
                    Vec3 axis = axes.get(p);
                    Vec3 plane = planes.get(p);
                    Vec3 a1 = axis.cross(plane).normalize();
                    Vec3 a2 = axis.cross(a1).normalize();
                    for (int i = 0; i < n; i++) {
                        double ang = i * step + phase + p * step * 0.5D;
                        double rad = Math.toRadians(ang);
                        out.add(new Shot(origin,
                                a1.scale(Math.cos(rad)).add(a2.scale(Math.sin(rad))), axis, params));
                    }
                }
                yield List.copyOf(out);
            }

            case SHELL -> {
                // 球面壳。riseFactor 决定竖直分量：0.15 贴地外推，1.0 会闭合的穹顶。
                int n = Math.max(3, params.count());
                double r = Math.max(0.5D, params.radius());
                List<Shot> out = new ArrayList<>(n);
                for (int i = 0; i < n; i++) {
                    double rad = Math.PI * 2.0D * i / n + phase;
                    Vec3 dir = right.scale(Math.cos(rad))
                            .add(worldUp.scale(Math.sin(rad) * params.riseFactor()))
                            .add(forward.scale(Math.sin(rad) * 0.6D));
                    out.add(new Shot(origin, dir.normalize(),
                            worldUp, params.withRadiusPerTick(params.radiusPerTick())));
                }
                yield List.copyOf(out);
            }

            case FALL_FROM_ABOVE -> {
                int n = Math.max(1, params.count());
                int side = Math.max(1, (int) Math.round(Math.sqrt(n)));
                double span = Math.max(1.0D, params.radius());
                List<Shot> out = new ArrayList<>(n);
                for (int i = 0; i < n; i++) {
                    int gx = i % side;
                    int gz = i / side;
                    double fx = side <= 1 ? 0.5D : gx / (double) (side - 1) - 0.5D;
                    double fz = side <= 1 ? 0.5D : gz / (double) (side - 1) - 0.5D;
                    Vec3 start = origin.add(right.scale(fx * span))
                            .add(forward.scale(fz * span))
                            .add(worldUp.scale(6.0D));
                    out.add(new Shot(start, worldUp.scale(-1.0D), right, params));
                }
                yield List.copyOf(out);
            }

            case RING -> {
                // 纯等角环，无任何行为含义。曲射与否由 Behaviour 决定。
                int n = Math.max(3, params.count());
                double step = 360.0D / n;
                List<Shot> out = new ArrayList<>(n);
                for (int i = 0; i < n; i++) {
                    double rad = Math.toRadians(i * step + phase);
                    Vec3 dir = right.scale(Math.cos(rad)).add(up.scale(Math.sin(rad)));
                    out.add(new Shot(origin, dir, forward, params));
                }
                yield List.copyOf(out);
            }

            case CONE_RANDOM -> {
                int n = Math.max(1, params.count());
                List<Shot> out = new ArrayList<>(n);
                for (int i = 0; i < n; i++) {
                    out.add(new Shot(origin, insideCone(forward, worldUp, params, phase + i),
                            up, params));
                }
                yield List.copyOf(out);
            }

            case RADIAL_BURST -> {
                // 一圈等角径向方向。悬停与否由 Behaviour 决定。
                int n = Math.max(1, params.count());
                List<Shot> out = new ArrayList<>(n);
                for (int i = 0; i < n; i++) {
                    double rad = Math.PI * 2.0D * i / n + phase;
                    Vec3 dir = right.scale(Math.cos(rad)).add(up.scale(Math.sin(rad)));
                    out.add(new Shot(origin, dir, forward, params));
                }
                yield List.copyOf(out);
            }

            case SCATTER_STATIC -> {
                // 环带上的静止弹。溜め与否由 Behaviour 决定——几何只负责「放在哪」。
                int n = Math.max(1, params.count());
                double r = Math.max(1.0D, params.radius());
                List<Shot> out = new ArrayList<>(n);
                for (int i = 0; i < n; i++) {
                    double rad = Math.PI * 2.0D * i / n + phase;
                    Vec3 at = origin.add(right.scale(Math.cos(rad) * r))
                            .add(forward.scale(Math.sin(rad) * r))
                            .add(worldUp.scale(-1.0D));
                    out.add(new Shot(at, Vec3.ZERO, worldUp, params));
                }
                yield List.copyOf(out);
            }

            case GAP_FAN -> {
                // 补位：沿缺口扇形内等分排布。名字里的「补位」指几何排布，与行为无关。
                int n = Math.max(1, params.count());
                double gapCenter = Mth.wrapDegrees(gapPhase + phase);
                List<Shot> out = new ArrayList<>(n);
                for (int i = 0; i < n; i++) {
                    double a = gapCenter + params.gapDeg() * (i + 1) / (n + 1)
                            - params.gapDeg() * 0.5D;
                    double rad = Math.toRadians(a);
                    out.add(new Shot(origin,
                            right.scale(Math.cos(rad)).add(up.scale(Math.sin(rad))),
                            forward, params));
                }
                yield List.copyOf(out);
            }
        };
    }

    /** 绕 {@code up} 轴在 (forward, up) 平面内旋转。 */
    private static Vec3 rotateY(Vec3 forward, Vec3 up, double angle) {
        Vec3 right = forward.cross(up).normalize();
        return forward.scale(Math.cos(angle)).add(right.scale(Math.sin(angle))).normalize();
    }

    /** 由 (yaw, pitch) 还原曲射轴。 */
    public static Vec3 axisFrom(double yawDeg, double pitchDeg) {
        double yaw = Math.toRadians(yawDeg);
        double pitch = Math.toRadians(pitchDeg);
        double cp = Math.cos(pitch);
        return new Vec3(-Math.sin(yaw) * cp, -Math.sin(pitch), Math.cos(yaw) * cp);
    }

    /**
     * 锥内随机单位向量。<b>刻意不做全向随机</b>——锥轴随 BOSS 转向，故玩家总能看到
     * 这片随机的大致方向；读不出精确点位是因为有包络，不是因为在视野外。
     */
    private static Vec3 insideCone(Vec3 forward, Vec3 worldUp, Shape.Params params, double seed) {
        Vec3 right = forward.cross(worldUp).normalize();
        Vec3 up = right.cross(forward).normalize();
        double coneHalf = Math.max(1.0D, params.spreadDeg() * 0.5D);
        double ang = Math.toRadians(pseudo(seed) * coneHalf);
        double tilt = Math.toRadians((pseudo(seed * 1.7D + 0.31D) - 0.5D) * coneHalf);
        return forward.scale(Math.cos(ang) * Math.cos(tilt))
                .add(right.scale(Math.sin(ang) * Math.cos(tilt)))
                .add(up.scale(Math.sin(tilt)))
                .normalize();
    }

    /**
     * 确定性伪随机（0..1）。用纯函数而非 RandomSource，使形状可离线复现与断言。
     *
     * <p>注意本函数的精度随 {@code seed} 的量级单调退化（{@code sin} 内部��双重精度
     * 有效位有限）。符卡表的 {@code phase} 随运行时间线性增长，实践中几十分钟内
     * 仍够用；MUST NOT 用于需要长期高精度复现的场景。
     */
    private static double pseudo(double seed) {
        double v = Math.sin(seed * 12.9898D + 78.233D) * 43758.5453D;
        return v - Math.floor(v);
    }
}
