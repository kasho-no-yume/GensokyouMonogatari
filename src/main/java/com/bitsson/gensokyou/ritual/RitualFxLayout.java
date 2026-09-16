package com.bitsson.gensokyou.ritual;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;

/**
 * 仪式特效发射点布局（ritual-presentation-polish D6）：世界无关纯函数，服务端与测试共用口径。
 * 全部输出为**相对核心方块原点**的局部坐标（块），客户端 BER 直接用于局部 PoseStack。
 */
public final class RitualFxLayout {

    /** 一点贴地火焰：局部坐标 + 边缘衰减（0=中心，1=最外）+ 确定性种子（相位抖动用）。 */
    public record FirePoint(double x, double y, double z, float edge, long seed) {
    }

    private RitualFxLayout() {
    }

    /** 结构半径未知时的最小铺开半径（格）。 */
    private static final double MIN_RADIUS = 1.5D;

    /**
     * 迦具土贴地烈火场：以核心为圆心，在结构水平半径内用**黄金角盘状采样**均匀铺点
     * （sqrt 半径 → 面积均匀、无环状空隙），再补每座祭品台一点，保证台面被火舌舔到。
     *
     * <p>半径硬钳：任一采样点（含抖动后）水平半径 MUST ≤ {@code structureRadius}，
     * 绝不溢出仪式结构；结构半径未知（<=0）时回落 {@link #MIN_RADIUS}。
     * 采样数随阶级增长；linkPos（祭品台）缺失时仍按结构半径铺开，MUST NOT 挤在核心附近。
     *
     * @param radiusRatio 覆盖半径占结构水平半径的比例（&lt;=1，硬钳）
     */
    public static List<FirePoint> fireBed(BlockPos core, long[] pedestalPos, int tier,
                                          int basePoints, int pointsPerTier,
                                          double structureRadius, double radiusRatio) {
        double hardCap = Math.max(MIN_RADIUS, structureRadius);
        double rMax = hardCap * clamp(radiusRatio, 0.1D, 1.0D);
        int count = Math.max(1, basePoints + Math.max(0, tier) * pointsPerTier);
        long seedBase = core.asLong() * 0x9E3779B97F4A7C15L;
        RandomSource random = RandomSource.create(seedBase);
        double golden = Math.PI * (3.0D - Math.sqrt(5.0D));
        List<FirePoint> out = new ArrayList<>(count + pedestalPos.length + 1);
        // 火心：核心格位一点（视觉中心，glow 覆盖整个核心）
        out.add(new FirePoint(0.5D, 0.0D, 0.5D, 0F, core.asLong()));
        for (int i = 0; i < count; i++) {
            double t = (i + 0.5D) / count;
            double rad = rMax * Math.sqrt(t) * (1.0D + (random.nextDouble() - 0.5D) * 0.14D);
            rad = Math.min(rad, hardCap);
            double angle = i * golden + (random.nextDouble() - 0.5D) * 0.3D;
            out.add(new FirePoint(0.5D + Math.cos(angle) * rad, 0.0D,
                    0.5D + Math.sin(angle) * rad, edgeOf(rad, rMax), seedBase + i * 104729L));
        }
        for (long posLong : pedestalPos) {
            BlockPos p = BlockPos.of(posLong);
            double dx = p.getX() - core.getX() + 0.5D;
            double dz = p.getZ() - core.getZ() + 0.5D;
            out.add(new FirePoint(dx, 0.0D, dz, edgeOf(Math.hypot(dx, dz), rMax), posLong));
        }
        return out;
    }

    /** 归一化边缘系数（0=中心，1=半径上限），供渲染端做边缘透明衰减。 */
    private static float edgeOf(double radius, double rMax) {
        return rMax <= 0.0D ? 0F : (float) Math.min(1.0D, radius / rMax);
    }

    private static double clamp(double value, double min, double max) {
        return value < min ? min : (value > max ? max : value);
    }
}
