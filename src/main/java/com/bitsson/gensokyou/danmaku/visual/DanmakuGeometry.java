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
     * 有厚度的五角星（五角星柱）。
     *
     * <p><b>刻意不做完全 billboard。</b>完全朝向相机会让厚度永远不可见，这个几何
     * 就没有存在意义。它的朝向是「偏航锁相机方位以保证轮廓可读，俯仰在有限幅度内
     * 摆动以使厚度成为可见信息」——摆动幅度由档案的 {@code tumbleAmpDeg} / 
     * {@code tumblePeriod} 给出。
     *
     * <p>成本特征与 {@link #QUAD} <b>相反</b>：30 quad = 120 顶点/层（顶点重），
     * 但屏幕覆盖只有同尺寸四边形的四成左右（填充轻）。密集弹幕下填充率因此下降。
     */
    STAR_PRISM;

    /**
     * 正五角星柱的轮廓顶点。
     *
     * <p>10 个顶点凸凹交替，间隔 36°：奇数下标为凸（外接圆半径 R），偶数下标为凹
     * （内半径 {@code r = R·(3−√5)/2}）。
     *
     * @return 长度 20 的数组 {@code [x0,y0, x1,y1, …]}，逆时针（极角递增）
     */
    public static double[] starPrismOutline(double radius) {
        double inner = radius * DanmakuVisualProfile.PENTAGRAM_INNER_RATIO;
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

    /** 五角星柱的总厚度（半厚的一半）。厚度 = 半径的一半，故半厚 = 半径的四分之一。 */
    public static double starPrismHalfThickness(double radius) {
        return radius * 0.25D;
    }
}
