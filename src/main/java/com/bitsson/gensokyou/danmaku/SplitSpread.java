package com.bitsson.gensokyou.danmaku;

import net.minecraft.world.phys.Vec3;

/**
 * 分裂子代的<b>方向编排</b>——母弹炸开时 {@code count} 颗子代各自往哪飞。
 *
 * <p>两种母题，按母弹<b>是否还在动</b>分派：
 * <table border="1">
 *   <caption>分派规则</caption>
 *   <tr><th>母弹</th><th>编排</th><th>母题</th></tr>
 *   <tr><td>运动中</td><td>{@link #ring} 垂直于速度的环</td><td>「一圈扩散」——弹幕先铺开再收拢</td></tr>
 *   <tr><td>已停住</td><td>{@link #fibonacciSphere} 球面均布</td><td>停住后炸开成球壳（需求⑤）</td></tr>
 * </table>
 *
 * <p><b>为什么静止时要换成球面</b>：母弹停住时没有「前进方向」，若仍套用环，
 * 整圈子代会落进同一个垂直平面——从侧面看是一道墙，从正面看才是个圈，
 * 弹幕会明显「缺一半」。球面均布在<b>任何</b>视角下都是完整的一圈。
 *
 * <p><b>本类只在服务端分裂那一 tick 求值一次</b>，结果作为速度经实体数据同步到客户端，
 * 因此不参与双端逐 tick 复算——{@code sin/cos} 在这里无害（对比
 * {@link com.bitsson.gensokyou.danmaku.motion.DanmakuSpeedProfile} 的四则运算约束）。
 */
public final class SplitSpread {

    /** 母弹速率低于此值即视为「已停住」（格/tick）。 */
    public static final double STATIONARY_SPEED_EPS = 1.0E-4D;

    /** 静止母弹的子代初速下限：过慢的子代几乎不动，看着像凭空消失。 */
    public static final double STATIONARY_SPLIT_SPEED = 0.15D;

    private SplitSpread() {
    }

    /**
     * 按母弹状态分派：{@code i} of {@code count} 的子代方向。
     *
     * @param velocity     母弹当前速度（可能为零向量）
     * @param speed        母弹速率
     * @param index        子代序号 {@code [0, count)}
     * @param count        子代总数
     * @param fallBackSpeed 静止母弹的子代初速
     */
    public static Vec3 forMotion(Vec3 velocity, double speed, int index, int count,
                                  double fallBackSpeed) {
        return moving(velocity, speed) ? ring(velocity, index, count) : fibonacciSphere(index, count);
    }

    /** 静止母弹的子代初速。 */
    public static double childSpeed(double speed, double fallBackSpeed) {
        return moving(speed) ? speed : Math.max(fallBackSpeed, STATIONARY_SPLIT_SPEED);
    }

    /** 母弹是否算「还在动」。 */
    public static boolean moving(Vec3 velocity, double speed) {
        return speed >= STATIONARY_SPEED_EPS && velocity.lengthSqr() > STATIONARY_SPEED_EPS;
    }

    /** 母弹是否算「还在动」（只有速率可用时）。 */
    public static boolean moving(double speed) {
        return speed >= STATIONARY_SPEED_EPS;
    }

    /**
     * 环状：垂直于 {@code velocity} 的圆上第 {@code index} 个方向（{@code count} 等分）。
     *
     * <p>环的法线就是母弹速度方向，故子代在「母弹前进方向」上没有任何分量——
     * 它们只向两侧扩散，不会跑到母弹前面去。
     */
    public static Vec3 ring(Vec3 velocity, int index, int count) {
        Vec3 normal = velocity.normalize();
        Vec3 reference = Math.abs(normal.y) < 0.9D ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 right = normal.cross(reference).normalize();
        Vec3 up = normal.cross(right).normalize();
        double angle = Math.PI * 2.0D * index / count;
        return right.scale(Math.cos(angle)).add(up.scale(Math.sin(angle)));
    }

    /**
     * 球面均布：单位球上第 {@code index} 个方向（{@code count} 个）。
     *
     * <p>用 Fibonacci（黄金角）序列而非经纬网格：等分纬度会在两极挤成一坨、在赤道稀成
     * 两圈；黄金角序列在球面上趋于<b>等面积</b>，任何 {@code count} 都不出明显空洞。
     */
    public static Vec3 fibonacciSphere(int index, int count) {
        if (count <= 1) {
            return new Vec3(0, 0, 1);
        }
        double y = 1.0D - 2.0D * index / (count - 1.0D);
        double radius = Math.sqrt(Math.max(0.0D, 1.0D - y * y));
        double angle = Math.PI * (3.0D - Math.sqrt(5.0D)) * index;
        return new Vec3(Math.cos(angle) * radius, y, Math.sin(angle) * radius);
    }
}
