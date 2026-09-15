package com.bitsson.gensokyou.ritual;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;

/**
 * 仪式特效发射点布局（ritual-fx-overhaul D3）：世界无关纯函数，服务端与测试共用口径。
 * 全部输出为**相对核心方块原点**的局部坐标（块），客户端 BER 直接用于局部 PoseStack。
 */
public final class RitualFxLayout {

    /** 一点火柱发射点：局部坐标 + 确定性种子（相位/抖动由 seed 派生，帧间稳定）。 */
    public record Pillar(double x, double y, double z, long seed) {
    }

    private RitualFxLayout() {
    }

    /** 祭品台伪随机散布的半径（格）。 */
    private static final double PED_JITTER = 0.42D;

    /**
     * 迦具土火柱发射点：核心柱 1 + 每座 'P' 台位 `perPedestalBase + tier` 点（台位到达时才加）
     * + **同心环带**（按结构水平半径铺满，1~3 层随阶级加层）+ 台面面积均匀填充点。
     *
     * <p>刻意不依赖台位坐标推算半径：台位数据缺失时旧实现会退化到最小半径、火柱全挤在中心；
     * 半径由服务端直接同步（{@code structureRadius}），保证任何情况下都从内到外铺开。
     */
    public static List<Pillar> flamePillars(BlockPos core, long[] pedestalPos, int tier,
                                            int perPedestalBase, int ringPoints,
                                            int structureRadius) {
        List<Pillar> out = new ArrayList<>();
        out.add(new Pillar(0.5D, 0.0D, 0.5D, core.asLong()));
        double radiusMax = Math.max(2.0D, structureRadius);

        for (long posLong : pedestalPos) {
            BlockPos p = BlockPos.of(posLong);
            double dx = p.getX() - core.getX() + 0.5D;
            double dz = p.getZ() - core.getZ() + 0.5D;
            double dy = p.getY() - core.getY();
            int count = Math.max(1, perPedestalBase + tier);
            RandomSource random = RandomSource.create(posLong);
            out.add(new Pillar(dx, dy, dz, posLong));
            for (int i = 1; i < count; i++) {
                out.add(new Pillar(dx + (random.nextDouble() * 2.0D - 1.0D) * PED_JITTER,
                        dy, dz + (random.nextDouble() * 2.0D - 1.0D) * PED_JITTER,
                        posLong + i * 7919L));
            }
        }

        // 同心环带：0.42 / 0.70 / 0.98 倍结构半径，层数随阶级 1→2→3
        int layers = tier <= 1 ? 1 : (tier <= 3 ? 2 : 3);
        int perLayer = Math.max(3, (ringPoints + 2 * tier) / layers);
        for (int layer = 0; layer < layers; layer++) {
            double frac = layers == 1 ? 0.75D
                    : 0.42D + 0.56D * layer / (double) (layers - 1);
            double ringR = radiusMax * frac;
            RandomSource random = RandomSource.create(core.asLong() ^ (0x51EDL + layer * 7919L));
            for (int i = 0; i < perLayer; i++) {
                double angle = random.nextDouble() * Math.PI * 2.0D;
                double r = ringR * (0.88D + random.nextDouble() * 0.24D);
                out.add(new Pillar(0.5D + Math.cos(angle) * r, 0.0D,
                        0.5D + Math.sin(angle) * r, core.asLong() + layer * 104729L + i * 31L));
            }
        }

        // 台面面积均匀填充（sqrt 采样）：补掉环带之间的空隙，远看是"一片火坛"
        int fill = 4 + 2 * Math.max(0, tier);
        RandomSource random = RandomSource.create(core.asLong() ^ 0xF111L);
        for (int i = 0; i < fill; i++) {
            double rr = radiusMax * (0.28D + 0.78D * Math.sqrt(random.nextDouble()));
            double angle = random.nextDouble() * Math.PI * 2.0D;
            out.add(new Pillar(0.5D + Math.cos(angle) * rr, 0.0D,
                    0.5D + Math.sin(angle) * rr, core.asLong() + 0x51EEDL + i * 53L));
        }
        return out;
    }
}
