package com.bitsson.gensokyou.danmaku.visual;

/**
 * 弹幕的几何形状。
 *
 * <p>新形状 MUST 收敛到既有的形状输出契约——一个把「局部坐标系下的顶点」写进给定
 * {@code VertexConsumer} 的方法。换色、换 alpha、发光层、核心层、深度处理全部由
 * {@code AbstractDanmakuRenderer} 的公共实现承担，新形状不得重写。
 */
public enum DanmakuGeometry {

    /**
     * 面向相机的四边形。原始弹幕形状，也是性能最好的一个：
     * 4 顶点/层、覆盖满方框。
     */
    QUAD,

    /**
     * 原作风格的立体五角星：宽白芯、彩色星角、柔和面缘与实体侧壁。
     *
     * <p>局部 +Z 轴沿弹道方向，不读取相机朝向；按年龄自转并小幅摆动以显露棱边。
     * 摆动幅度由档案的 {@code tumbleAmpDeg} / {@code tumblePeriod} 给出。
     *
     * <p>前后弧面与侧边在单次本体绘制中完成，不叠加金属刻纹或 billboard。
     */
    STAR_PRISM;

    /** 游戏星弹的凹角较浅；它不是绘制法阵时使用的正五芒星。 */
    public static final double STAR_INNER_RATIO = 0.50D;
    public static final double STAR_WHITE_CORE_RADIUS = 0.42D;

    /**
     * 星弹的五角轮廓顶点。
     *
     * <p>10 个顶点凸凹交替，间隔 36°：偶数下标为凸（外接圆半径 R），奇数下标为凹
     * （内半径 {@code r = R·0.5}），保留原作较饱满的星角。
     *
     * @return 长度 20 的数组 {@code [x0,y0, x1,y1, …]}，顺时针（极角递减）
     */
    public static double[] starPrismOutline(double radius) {
        double inner = radius * STAR_INNER_RATIO;
        double[] out = new double[20];
        for (int i = 0; i < 10; i++) {
            double r = (i % 2 == 0) ? radius : inner;
            // 凸角自正上方起，每 72° 一个；凹角再错开 36°
            double deg = 90.0D - (i * 36.0D);
            double rad = Math.toRadians(deg);
            out[i * 2] = r * Math.cos(rad);
            out[i * 2 + 1] = r * Math.sin(rad);
        }
        return out;
    }

    /** 五角星模型的半厚度；侧壁保持实体体积，正面轮廓与碰撞尺寸独立。 */
    public static double starPrismHalfThickness(double radius) {
        return radius * 0.20D;
    }

    /** 鳞片横截面：外翼、翼内缘、中央鳞瓣的半宽及厚度，均为归一化尺寸。 */
    public record ArrowheadSection(double z, double outerWidth, double wingInnerWidth,
                                   double coreWidth, double height) {}

    /**
     * 原作鳞弹的拱形双翼和中央亮瓣，沿 X-Z 平面展开，尖端在 +Z。
     * 后缘分为三瓣，没有圆杆状尾柄；前半段的中央鳞瓣与双翼相连。
     * 只保留尾缘、白芯、内纹和前尖所需的五个截面，避免为平滑弧度过度细分。
     */
    public static ArrowheadSection[] arrowheadSections() {
        return new ArrowheadSection[]{
                new ArrowheadSection(-1.00D, 1.00D, 0.80D, 0.02D, 0.30D),
                new ArrowheadSection(-0.22D, 0.72D, 0.42D, 0.40D, 0.96D),
                new ArrowheadSection( 0.34D, 0.46D, 0.18D, 0.18D, 0.88D),
                new ArrowheadSection( 0.62D, 0.34D, 0.13D, 0.13D, 0.65D),
                new ArrowheadSection( 1.00D, 0.00D, 0.00D, 0.00D, 0.00D)
        };
    }
}
