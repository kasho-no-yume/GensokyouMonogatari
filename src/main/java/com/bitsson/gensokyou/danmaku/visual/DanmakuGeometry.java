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
    STAR_PRISM,

    /** 面向相机的五角星弹幕贴图。 */
    STAR_SPRITE;

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

    /**
     * 鳞形箭头弹体朝向观察者一侧的轮廓。外侧圆钝、两肩张开，底缘收成短尖，
     * 与细长锥尖区分开来。
     *
     * @return 长度 36 的数组 {@code [x0,y0, ...]}，顺时针排列
     */
    public static double[] arrowheadOutline(double radius) {
        double[] unit = {
                0.00D, 1.00D, 0.30D, 0.96D, 0.58D, 0.82D, 0.86D, 0.58D,
                0.96D, 0.28D, 0.92D, 0.00D, 0.76D, -0.28D, 0.47D, -0.56D,
                0.23D, -0.80D, 0.00D, -1.00D, -0.23D, -0.80D, -0.47D, -0.56D,
                -0.76D, -0.28D, -0.92D, 0.00D, -0.96D, 0.28D, -0.86D, 0.58D,
                -0.58D, 0.82D, -0.30D, 0.96D
        };
        for (int i = 0; i < unit.length; i++) {
            unit[i] *= radius;
        }
        return unit;
    }
}
