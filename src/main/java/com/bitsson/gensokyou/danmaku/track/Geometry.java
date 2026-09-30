package com.bitsson.gensokyou.danmaku.track;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import java.util.Random;
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

    /**
     * 一条发射指令。
     *
     * @param damageScale <b>本发</b>的伤害乘数。默认 1.0。
     *        它存在的理由是「一颗 4 倍伤害的中心弹与 45 颗普通花瓣属于同一拍」：
     *        轨道级 {@code damageScale} 无法表达同拍内不同发，而拆成两轨又做不到
     *        两轨共享同一个随机抛射方向与同一段寿命。
     * @param sizeScale <b>本发</b>的直径乘数。默认 1.0，语义同 {@code damageScale}。
     */
    public record Shot(Vec3 origin, Vec3 direction, Vec3 planeAxis, Shape.Params params,
                       double damageScale, double sizeScale) {

        /** 无逐发差异的发射指令（绝大多数）。 */
        public Shot(Vec3 origin, Vec3 direction, Vec3 planeAxis, Shape.Params params) {
            this(origin, direction, planeAxis, params, 1.0D, 1.0D);
        }

        /** 本发的实际直径 = 几何声明的直径 × 本发尺寸乘数。 */
        public double size() {
            return params.size() * sizeScale;
        }
    }


    /**
     * {@link Shape#AROUND_TARGET} 的瞄准夹角上限（度）。
     *
     * <p><b>180 = 完全自由</b>（锥半角的几何极限），刻意不设更严的上限。
     *
     * <p>早先这里硬夹在 90°，理由是「超过就会从背后射向目标，读作护住玩家而非攻击，
     * 玩家的闪避直觉会失效」。那条理由是错的：<b>激光有 {@code Phase.DELAY} 预警</b>，
     * 玩家在射线亮起前就看得见它从哪来、指向哪。公平性来自预警，不来自方向。
     * 硬夹 90° 只会让这一形态少掉一半的表现力（「背后交叉火网」这类设计写不出来），
     * 而换来的好处是零。
     */
    public static final double AROUND_TARGET_MAX_AIM_DEG = 180.0D;

    /**
     * {@link Shape#LATTICE} 的瞄准夹角上限（度）。

     * <p>比 {@link Shape#AROUND_TARGET} 严：网的设计前提是「射线从四周朝内收拢」，
     * 一旦允许射线指向背离目标的方向，玩家看到的就��是一团没有方向的线。
     */
    public static final double LATTICE_MAX_AIM_DEG = 90.0D;

    /** 无随机源时使用的共享源。几何只在服务端求值，故默认用真随机不影响双端一致性。 */
    private static final RandomSource DEFAULT_RANDOM = RandomSource.create();

    /** {@link Shape#AROUND_TARGET} 发射区域的竖直幅度占半径的比例。 */
    private static final double VERTICAL_EXTENT = 0.5D;

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
        return build(shape, origin, forward, origin.add(forward), worldUp, params, gapPhase, phase);
    }

    /**
     * 带随机源的完整形式。
     *
     * <p><b>用真随机是安全的</b>：几何只在<b>服务端</b>求值一次，产出的位置随即被烘进
     * 弹的出生数据，客户端从不重算这条路径。所以这里用 {@code RandomSource} 而非确定性
     * 伪随机不会引入任何双端一致性问题——这一点与「编队帧 MUST 用确定性函数」正好相反，
     * 因为编队帧是双端<b>各算一遍</b>的。
     *
     * <p>需要复现时传一个固定种子的源即可（测试与离线 lint 就这么做）。
     */
    public static List<Shot> build(Shape shape, Vec3 origin, Vec3 forward, Vec3 target,
                                   Vec3 worldUp, Shape.Params params, double gapPhase, double phase,
                                   RandomSource random) {
        return buildInternal(shape, origin, forward, target, worldUp, params, gapPhase, phase,
                random, 1.0D, 1.0D);
    }

    /**
     * 计算一拍的发射指令，额外给出<b>目标位置</b>。
     *
     * <p>只有 {@link Shape#AROUND_TARGET} 与 {@link Shape#LATTICE} 需要它——那两类的
     * 发射点采样自目标周围，方向由「该发自己的原点 → 目标」决定，故必须知道目标在哪。
     * 其余形状忽略该参数。
     *
     * @param target 目标（被瞄准者）位置
     */
    public static List<Shot> build(Shape shape, Vec3 origin, Vec3 forward, Vec3 target,
                                   Vec3 worldUp, Shape.Params params, double gapPhase, double phase) {
        return buildInternal(shape, origin, forward, target, worldUp, params, gapPhase, phase, null,
                1.0D, 1.0D);
    }

    /**
     * 带随机源与逐发差异旋钮的完整形式。
     *
     * <p>{@code centreDamage} / {@code centreSize} 只被 {@link Shape#FLOWER} 读：
     * 「一颗 4 倍伤害、2 倍大的中心弹与 45 颗普通花瓣属于同一拍」这件事，
     * 轨道级 {@code damageScale} 表达不了，拆两轨也做不到两轨共享同一个随机抛射方向。
     */
    public static List<Shot> build(Shape shape, Vec3 origin, Vec3 forward, Vec3 target,
                                   Vec3 worldUp, Shape.Params params, double gapPhase, double phase,
                                   RandomSource random, double centreDamage, double centreSize) {
        return buildInternal(shape, origin, forward, target, worldUp, params, gapPhase, phase,
                random, centreDamage, centreSize);
    }

    /**
     * 以整拍为输入的便捷形式：直接取拍上的逐发差异旋钮。
     */
    public static List<Shot> build(Track.Beat beat, Vec3 origin, Vec3 forward, Vec3 target,
                                   Vec3 worldUp, double gapPhase, double phase,
                                   RandomSource random) {
        return buildInternal(beat.shape(), origin, forward, target, worldUp, beat.params(),
                gapPhase, phase, random, beat.centreDamage(), beat.centreSize());
    }

    /** 玫瑰线上一点：极坐标（方位角, 半径）。 */
    private record Polar(double theta, double radius) {
    }

    /**
     * 玫瑰线排布——<b>先分组再瓣内铺满</b>，不是整圈均分。
     *
     * <p>分母 MUST 含花瓣数：玫瑰线的一个花瓣占据整整 {@code 2π/k} 的方位角
     * （半径从极值走到谷底再走回极值，一整圈）。写成 {@code 2π·within/perPetal} 时
     * 只铺了该扇区的 40%，于是「伸出去」那一半有、「收回来」那一半没有——
     * 读起来是<b>半片花瓣</b>，且完全不报错。
     *
     * <p>花瓣数 {@code < 2} 时幅度强制归零（{@code cos θ} 会给出一颗「心脏线」
     * 而不是圆环，与「参数为 0 即退化成圆」的约定不符）；幅度夹到 {@code [0, 基准]}，
     * 负幅度只是把花瓣换个朝向，超过基准则内侧半径变负、花瓣会穿过花心长到另一边。
     */
    private static List<Polar> rosette(int count, int petals, double base, double amp,
                                       double phase) {
        int n = Math.max(3, count);
        int k = Math.max(1, petals);
        double base2 = Math.max(0.5D, base);
        double amp2 = k < 2 ? 0.0D : Math.min(base2, Math.max(0.0D, amp));
        double sector = Math.PI * 2.0D / k;
        int perPetal = Math.max(1, n / k);
        List<Polar> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            int petal = i / perPetal;
            int within = i % perPetal;
            double theta = sector * petal + sector * within / perPetal + phase;
            out.add(new Polar(theta, base2 + amp2 * Math.cos(k * theta)));
        }
        return out;
    }

    /** 球面均匀单位向量（z 两次均匀采样，避免极点聚集）。 */
    private static Vec3 uniformSphere(RandomSource rng) {
        double z = rng.nextDouble() * 2.0D - 1.0D;
        double phi = rng.nextDouble() * Math.PI * 2.0D;
        double radial = Math.sqrt(Math.max(0.0D, 1.0D - z * z));
        return new Vec3(radial * Math.cos(phi), z, radial * Math.sin(phi));
    }

    /** 与 {@code dir} 正交的一个单位向量。 */
    private static Vec3 anyPerpendicular(Vec3 dir) {
        Vec3 seed = Math.abs(dir.y) > 0.9D ? new Vec3(1.0D, 0.0D, 0.0D) : new Vec3(0.0D, 1.0D, 0.0D);
        return seed.cross(dir).normalize();
    }

    /**
     * 生成点沿两个轴的偏移。
     *
     * <p>「沿 forward」与「沿世界竖直」两个偏移对绝大多数「换个地方冒出来」的需求
     * 已经够用，故不再新增几何——偏移只改弹「在哪儿冒出来」，不改弹的方位。
     */
    private static Vec3 offsetOrigin(Vec3 origin, Vec3 forward, Vec3 worldUp,
                                     Shape.Params params) {
        Vec3 out = origin;
        if (params.offsetForward() != 0.0D) {
            out = out.add(forward.scale(params.offsetForward()));
        }
        if (params.offsetUp() != 0.0D) {
            out = out.add(worldUp.scale(params.offsetUp()));
        }
        return out;
    }

    /** 水平圆盘上的一个点（围绕给定中心，方位由参数给出，距离真随机）。 */
    private static Vec3 onDisc(Vec3 center, double radius, double angle, RandomSource rng) {
        Vec3 east = new Vec3(1.0D, 0.0D, 0.0D);
        Vec3 north = new Vec3(0.0D, 0.0D, 1.0D);
        double r = Math.max(0.0D, radius) * Math.sqrt(rng.nextDouble());
        return center.add(east.scale(Math.cos(angle) * r)).add(north.scale(Math.sin(angle) * r));
    }

    private static List<Shot> buildInternal(Shape shape, Vec3 origin, Vec3 forward, Vec3 target,
                                             Vec3 worldUp, Shape.Params params,
                                             double gapPhase, double phase, RandomSource random,
                                             double centreDamage, double centreSize) {
        RandomSource rng = random == null ? DEFAULT_RANDOM : random;
        Vec3 right = forward.cross(worldUp);
        if (right.lengthSqr() < 1.0E-6D) {
            right = new Vec3(1.0D, 0.0D, 0.0D);
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

            case AROUND_TARGET -> {
                // 目标周围环形发射。发射点采样自目标周围的区域（不是 BOSS 位置），
                // 方向由**每一发自己的**「原点 → 目标」连线决定。
                //
                // <p>逐发独立是这一形态的全部意义：全批共用一个方向的话，
                // 就会退化成「从玩家外侧射向中心的一束平行光」，而不是一圈向内的光。
                int n = Math.max(1, params.count());
                double ring = Math.max(0.5D, params.radius());
                // 瞄准夹角上限：0 = 全部精确指向目标；越大越散，上限 180°（完全自由）。
                // 刻意不设更严的硬上限——激光靠 Phase.DELAY 预警，不靠方向来保证公平。
                double cap = Math.min(AROUND_TARGET_MAX_AIM_DEG,
                        Math.max(0.0D, params.spreadDeg()));
                // 竖直方向的幅度占 ring 的 0.5，故合成半径是 ring·√(1+0.5²) ≈ 1.118·ring。
                // 先把纵向幅度按「合成后仍在 ring 内」反解出来——否则声明的「区域半径」
                // 会被悄悄超出，读作「弹幕比配的更散」，而参数与实际对不上。
                double vertical = ring * VERTICAL_EXTENT;
                double horizontal = ring * Math.sqrt(Math.max(0.0D, 1.0D - VERTICAL_EXTENT * VERTICAL_EXTENT));
                List<Shot> out = new ArrayList<>(n);
                for (int i = 0; i < n; i++) {
                    double theta = Math.PI * 2.0D * i / n + phase;
                    // 发射点在目标周围的区域里：环绕方向 + 竖直方向的 Fibonacci 偏移。
                    // 刻意不等分竖直——等分会把弹排成几条水平线，看着像「道具」不像「弹幕」。
                    double lift = 1.0D - 2.0D * (i + 0.5D) / n;
                    Vec3 at = target.add(right.scale(Math.cos(theta) * horizontal))
                            .add(up.scale(lift * vertical));
                    Vec3 toTarget = target.subtract(at);
                    if (toTarget.lengthSqr() < 1.0E-9D) {
                        continue;
                    }
                    out.add(new Shot(at,
                            tiltWithin(toTarget.normalize(), worldUp, cap, phase + i),
                            worldUp, params));
                }
                yield List.copyOf(out);
            }

            case LATTICE -> {
                // 凌乱激光网：发射点<b>真随机</b>（方向与距离都随机），瞄准也逐发随机。
                //
                // <p>与 AROUND_TARGET 的分野就在「随机」二字：那一类是角度等分 + Fibonacci
                // 竖直偏移，看着像道具生成的阵；这一类落在目标周围的<b>球体内</b>，没有
                // 任何可读的秩序——那才是「网」。
                //
                // <p>瞄准分布刻意<b>不均匀</b>：aimBias 比例精确瞄准，其余在上限内散开。
                // 全散射则玩家随便走就能躲，全直瞄则没有夹缝。
                int n = Math.max(1, params.count());
                double outer = Math.max(0.5D, params.radius());
                // 内半径：不让射线从玩家身上冒出来。取外径的 1/3，
                // 于是「很近但不是贴脸」，读起来是「网」而不是「被瞄准」。
                double inner = outer / 3.0D;
                double cap = Math.min(LATTICE_MAX_AIM_DEG, Math.max(0.0D, params.spreadDeg()));
                double bias = Math.min(1.0D, Math.max(0.0D, params.aimBias()));
                List<Shot> out = new ArrayList<>(n);
                for (int i = 0; i < n; i++) {
                    // 球面均匀方向：z 用两次均匀采样凑出球面均匀，避免极点聚集。
                    double z = rng.nextDouble() * 2.0D - 1.0D;
                    double phi = rng.nextDouble() * Math.PI * 2.0D;
                    double radial = Math.sqrt(Math.max(0.0D, 1.0D - z * z));
                    Vec3 offset = new Vec3(radial * Math.cos(phi), z, radial * Math.sin(phi))
                            .scale(inner + rng.nextDouble() * (outer - inner));
                    Vec3 at = target.add(offset);
                    Vec3 toTarget = offset.scale(-1.0D).normalize();
                    Vec3 dir = rng.nextDouble() < bias
                            ? toTarget
                            : tiltWithin(toTarget, worldUp, cap, rng.nextDouble() * 360.0D);
                    out.add(new Shot(at, dir, worldUp, params));
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

            case ROSETTE -> {
                // 玫瑰线花形：r = 基准 + 幅度·cos(花瓣数·θ)，排布在「面向玩家」的竖直平面内
                // （由 right 与 up 张成，法线 = forward）。
                //
                // <p><b>每颗弹的初速方向 = 平面法线（forward），不是它在平面内的半径方向。</b>
                // 这是「花瓣在平面里开合、整组朝玩家压过来」的读法：平面内的远离/靠近
                // 由编队帧的呼吸缩放负责（每颗弹沿自己的半径往复，正是「到中心连线」上的运动），
                // 而「行进方向」是法线——玩家会撞上来的正是这个方向。
                //
                // <p>若把初速设成半径方向（早先的实现），整组就变成在平面里向外扩散，
                // 沿法线没有任何分量——花瓣是张开了，但整组不动，读作「一朵原地开的花」，
                // 而不是「一朵扑过来的花」。
                int n = Math.max(3, params.count());
                int petals = Math.max(1, params.petals());
                double base = Math.max(0.5D, params.radius());
                // 幅度夹到 [0, base]：负幅度只是把花瓣换个朝向（等价于 θ 平移半瓣），
                // 超过 base 则内侧半径变负，花瓣会穿过花心长到另一边去，看着是「反的」。
                // 花瓣数 < 2 时幅度强制归零——cos(θ) 会给出一颗「心脏线」而不是圆环，
                // 与本形状「参数为 0 即退化成圆」的约定不符，直接归零更可预期。
                double amp = petals < 2 ? 0.0D
                        : Math.min(base, Math.max(0.0D, params.radialAmp()));
                List<Shot> out = new ArrayList<>(n);
                double sector = Math.PI * 2.0D / petals;
                for (int i = 0; i < n; i++) {
                    // 先按花瓣分组，再把该瓣的弹<b>铺满它自己那 2π/k 的角度扇区</b>。
                    //
                    // <p>这一步的分母 MUST 含 {@code petals}：玫瑰线的一个花瓣占据整整
                    // 2π/k 的方位角（半径从极值走到谷底再走回极值，一整圈）。早先写成
                    // {@code 2π·within/perPetal} 时只铺了该扇区的 40%，
                    // 于是「伸出去」那一半有、「收回来」那一半没有——
                    // 读起来是<b>半片花瓣</b>，且完全不报错。
                    int perPetal = Math.max(1, n / petals);
                    int petal = i / perPetal;
                    int within = i % perPetal;
                    double theta = sector * petal + sector * within / perPetal + phase;
                    double r = base + amp * Math.cos(petals * theta);
                    Vec3 radial = right.scale(Math.cos(theta)).add(up.scale(Math.sin(theta)));
                    out.add(new Shot(origin.add(radial.scale(r)), forward, forward, params));
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

            case GRID_FACING -> {
                // 二维角度栅格：两轴各按固定角间隔展开，<b>全部同速同原点同刻</b>。
                //
                // <p>于是在空间里落到的是弧面而非平面——这是「同速 + 固定角偏」的必然结果。
                // 刻意不去构造球壳：球壳要让远处的弹更慢，那就得按距离改速度，
                // 而「同速」正是本形态可读的原因（整片是一次性压过来的，不是分批到的）。
                int total = Math.max(1, params.count());
                int rows = Math.max(1, (int) Math.round(Math.sqrt(total)));
                int cols = Math.max(1, (int) Math.ceil((double) total / rows));
                double step = Math.toRadians(params.spreadDeg());
                Vec3 gridOrigin = offsetOrigin(origin, forward, worldUp, params);
                List<Shot> out = new ArrayList<>(total);
                for (int i = 0; i < total; i++) {
                    int r = i / cols;
                    int c = i % cols;
                    double yaw = cols <= 1 ? 0.0D : step * (c - (cols - 1) / 2.0D);
                    double pitch = rows <= 1 ? 0.0D : step * (r - (rows - 1) / 2.0D);
                    double cp = Math.cos(pitch);
                    Vec3 dir = forward.scale(Math.cos(yaw) * cp)
                            .add(right.scale(Math.sin(yaw) * cp))
                            .add(up.scale(Math.sin(pitch)))
                            .normalize();
                    out.add(new Shot(gridOrigin, dir, forward, params));
                }
                yield List.copyOf(out);
            }

            case DISC_RING -> {
                // 侧挂圆盘：一圈弹排在垂直于 forward 的平面上，圆心在 forward*offsetForward。
                //
                // <p>取 count=1 并让相位逐拍推进，即得「一圈弹依次点亮」——
                // 逐颗出现比整圈同时出现可读得多：玩家能数出还剩几颗没出来。
                int n = Math.max(1, params.count());
                double ringRadius = Math.max(0.0D, params.radius());
                Vec3 discCenter = offsetOrigin(origin, forward, worldUp, params);
                List<Shot> out = new ArrayList<>(n);
                for (int i = 0; i < n; i++) {
                    double rad = Math.toRadians(i * 360.0D / n + phase);
                    Vec3 at = discCenter.add(right.scale(Math.cos(rad) * ringRadius))
                            .add(up.scale(Math.sin(rad) * ringRadius));
                    out.add(new Shot(at, forward, forward, params));
                }
                yield List.copyOf(out);
            }

            case SCATTER_FALL -> {
                // 体积内随机撒点 + 竖直向下。位置与距离都真随机。
                //
                // <p>与 FALL_FROM_ABOVE 的分野是秩序 vs 随机：后者是规则网格、看得见行列；
                // 本形态是「天降一片雨」。最终坐标由翻译层按 FIRST_AIR_BELOW 锚定求出，
                // 几何只负责给出「相对发射者的落点」。
                int n = Math.max(1, params.count());
                Vec3 rainOrigin = offsetOrigin(origin, forward, worldUp, params);
                List<Shot> out = new ArrayList<>(n);
                for (int i = 0; i < n; i++) {
                    Vec3 at = onDisc(rainOrigin, params.radius(),
                            rng.nextDouble() * Math.PI * 2.0D, rng);
                    out.add(new Shot(at, worldUp.scale(-1.0D), worldUp, params));
                }
                yield List.copyOf(out);
            }

            case PILLAR_UP -> {
                // 地柱：地面上的一个点，方向竖直向上。存在的理由是激光需要
                // 「起点在地面、指向天空」，而 FALL_FROM_ABOVE 正好方向相反。
                int n = Math.max(1, params.count());
                Vec3 base = offsetOrigin(origin, forward, worldUp, params);
                List<Shot> out = new ArrayList<>(n);
                for (int i = 0; i < n; i++) {
                    Vec3 at = onDisc(base, params.radius(),
                            rng.nextDouble() * Math.PI * 2.0D, rng);
                    out.add(new Shot(at, worldUp, worldUp, params));
                }
                yield List.copyOf(out);
            }

            case FLOWER -> {
                // 一朵刚体花 = 1 颗中心弹 + count 颗花瓣弹，全部同速同刻发射。
                //
                // <p>刚体性由「同速同刻」物理地给出，不需要编队帧：本形态刻意不自转，
                // 花瓣的长短已经编码在出生点里。
                //
                // <p><b>平面取向与抛射方向各自独立随机</b>——这是本形态与 ROSETTE 的
                // 关键分野。ROSETTE 的平面恒为「面向主目标的竖直面」，于是所有花共面，
                // 16 朵一起抛出来读作一堵共面的墙；本形态让每朵花有自己的平面，
                // 且该平面<b>不必垂直于</b>它的飞行方向，于是「花之海洋」才读作海而不是墙。
                int petals = Math.max(1, params.count());
                Vec3 throwDir = uniformSphere(rng);
                Vec3 planeN = uniformSphere(rng);
                Vec3 petalU = anyPerpendicular(planeN);
                Vec3 petalW = planeN.cross(petalU).normalize();
                Vec3 flowerCenter = offsetOrigin(origin, forward, worldUp, params);
                List<Shot> out = new ArrayList<>(petals + 1);
                // 中心弹：出生点即编队中心，故后续「径向爆散」对它是退化情形，
                // 由爆散实现改判为「朝指定目标射出」。
                out.add(new Shot(flowerCenter, throwDir, planeN, params,
                        Math.max(0.0D, centreDamage), Math.max(0.0D, centreSize)));
                for (Polar p : rosette(petals, params.petals(), params.radius(),
                        params.radialAmp(), phase)) {
                    Vec3 radial = petalU.scale(Math.cos(p.theta()))
                            .add(petalW.scale(Math.sin(p.theta())));
                    out.add(new Shot(flowerCenter.add(radial.scale(p.radius())),
                            throwDir, planeN, params));
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
        return insideCone(forward, worldUp, params.spreadDeg() * 0.5D, seed);
    }

    /**
     * 锥内随机单位向量；锥半角（度）直接给定。
     *
     * <p><b>采样域是「正方形」而不是「圆盘」</b>：横向与纵向各取一个偏移，
     * 于是对角处的实际夹角可达 {@code 半角·√2}。
     *
     * <p>对 {@code CONE_RANDOM} 这是有意的——它要的是「读得出大致方向、读不出精确点位」，
     * 方形域让四角的偏移量与轴向偏移量等权，视觉上更均匀。
     * 但 {@link Shape#AROUND_TARGET} 的夹角是<b>硬上限</b>（需求：「超过上限的方向
     * MUST NOT 出现」），故它 MUST NOT 用这个采样器——它量的是相对正交基锥角、
     * 不是相对目标连线的夹角，见 {@link #tiltWithin}。
     */
    private static Vec3 insideCone(Vec3 forward, Vec3 worldUp, double coneHalfDeg, double seed) {
        Vec3 right = forward.cross(worldUp).normalize();
        Vec3 up = right.cross(forward).normalize();
        double coneHalf = Math.max(0.0D, coneHalfDeg);
        double ang = Math.toRadians(pseudo(seed) * coneHalf);
        double tilt = Math.toRadians((pseudo(seed * 1.7D + 0.31D) - 0.5D) * coneHalf);
        return forward.scale(Math.cos(ang) * Math.cos(tilt))
                .add(right.scale(Math.sin(ang) * Math.cos(tilt)))
                .add(up.scale(Math.sin(tilt)))
                .normalize();
    }

    /**
     * 绕 {@code forward} 旋转，角度严格不超过 {@code maxDeg}。
     *
     * <p><b>用 {@code atan2} 的球面三角余弦定理解旋转角</b>，而不是「取两个正交偏移再相加」：
     * 后者量出来的夹角是相对某个<b>正交基的锥角</b>，与「相对目标连线的夹角」不是一回事。
     * 早先用了基锥采样，声明 90° 上限却能产出 137° 的方向——上限形同虚设，
     * 且不报错，只是「射线从背后射过来了」。
     *
     * @param dirDeg 偏转方位（度），只影响方向落在锥的哪个方位，不影响角度大小
     */
    private static Vec3 tiltWithin(Vec3 forward, Vec3 worldUp, double maxDeg, double dirDeg) {
        double maxRad = Math.toRadians(Math.max(0.0D, maxDeg));
        if (maxRad <= 0.0D) {
            return forward;
        }
        // 半径按面积均匀（√u），使样本在圆盘内等面积而非挤在锥心。
        double tilt = maxRad * Math.sqrt(pseudo(dirDeg * 1.7D + 0.31D));
        double bearing = Math.toRadians(pseudo(dirDeg));
        if (tilt < 1.0E-12D) {
            return forward;
        }
        Vec3 right = worldUp.cross(forward);
        if (right.lengthSqr() < 1.0E-9D) {
            right = new Vec3(1, 0, 0).cross(forward);
            if (right.lengthSqr() < 1.0E-9D) {
                right = new Vec3(0, 0, 1);
            }
        }
        right = right.normalize();
        Vec3 up = forward.cross(right).normalize();
        return forward.scale(Math.cos(tilt))
                .add(right.scale(Math.sin(tilt) * Math.cos(bearing)))
                .add(up.scale(Math.sin(tilt) * Math.sin(bearing)))
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
