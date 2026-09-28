package com.bitsson.gensokyou.ritual;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;

import java.util.ArrayDeque;
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

    /**
     * 一点煅炉火星：局部坐标 + 边缘衰减 + 循环相位 + 尺寸抖动 + 确定性种子。
     *
     * @param cycle 该点的上升/渐隐循环相位（0..1，确定性，渲染端按时间偏移）
     * @param scale 尺寸抖动系数（0.55..1.0，确定性）
     */
    public record EmberPoint(double x, double y, double z, float edge,
                             double cycle, float scale, long seed) {
    }

    /**
     * 一点灵浴水面：局部坐标（XYZ，块）+ <b>边缘</b>系数（1=贴边，0=池心）+ 确定性种子。
     *
     * <p>覆盖主池与外圈院子两类格位（见 {@link #bathSurface}），两类不区分外观但
     * <b>Y 不同</b>（院子低一格），故此处 MUST 携带 y。
     *
     * @param y 水体<b>底面</b>的局部 Y 偏移（相对核心方块原点）；渲染端自该面向上画水深
     */
    public record WaterCell(double x, double y, double z, float edge, long seed) {
    }

    /**
     * 水面布局的静态结论：格位集合 + 水平切比雪夫半径（供渲染包围盒取值，避免逐帧重扫）。
     *
     * @param radiusXZ 水面格位到核心 X/Z 轴的最大切比雪夫距离（格）
     */
    public record BathSurface(List<WaterCell> cells, int radiusXZ) {
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

    /**
     * 金山彦命煅炉的**密集火星场**：结构水平半径内铺满小型火舌点，
     * 客户端按 {@code seed} 驱动上升/渐隐循环，观感等同"大量火焰粒子"，
     * 但全部是交叉面片几何，MUST NOT 走原版粒子系统。
     *
     * <p>与 {@link #fireBed} 的区别：火床是"少量大贴地火舌"，本方法是"海量小火点"，
     * 因此不做祭品台补点（台位由煅炉自己的火柱负责），改为每个点自带
     * {@code cycle}（0..1 循环相位）与 {@code scale}（尺寸抖动），让渲染端
     * 无需任何额外随机数即可得到稳定且各不相同的跳动。
     *
     * <p>半径硬钳：任一采样点水平半径 MUST ≤ {@code structureRadius}，绝不溢出结构。
     */
    public static List<EmberPoint> emberField(BlockPos core, int tier,
                                               int baseCount, int countPerTier,
                                               double structureRadius, double radiusRatio) {
        double hardCap = Math.max(MIN_RADIUS, structureRadius);
        double rMax = hardCap * clamp(radiusRatio, 0.1D, 1.0D);
        int count = Math.max(0, baseCount + Math.max(0, tier) * countPerTier);
        long seedBase = core.asLong() * 0xD1B54A32D192ED03L + 0x2545F491L;
        RandomSource random = RandomSource.create(seedBase);
        double golden = Math.PI * (3.0D - Math.sqrt(5.0D));
        List<EmberPoint> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            double t = (i + 0.5D) / Math.max(1, count);
            double rad = rMax * Math.sqrt(t) * (1.0D + (random.nextDouble() - 0.5D) * 0.22D);
            rad = Math.min(rad, hardCap);
            double angle = i * golden + (random.nextDouble() - 0.5D) * 0.4D;
            out.add(new EmberPoint(
                    0.5D + Math.cos(angle) * rad,
                    0.0D,
                    0.5D + Math.sin(angle) * rad,
                    edgeOf(rad, rMax),
                    random.nextDouble(),
                    0.55F + 0.45F * (float) random.nextDouble(),
                    seedBase + i * 65537L));
        }
        return out;
    }

    /** 归一化边缘系数（0=中心，1=半径上限），供渲染端做边缘透明衰减。 */
    private static float edgeOf(double radius, double rMax) {
        return rMax <= 0.0D ? 0F : (float) Math.min(1.0D, radius / rMax);
    }

    // ================================================================== 灵浴水面

    /**
     * 灵浴水面占地：<b>主池 + 外圈院子</b>两块，都是 pattern 已展开方块表的纯函数（零魔数）。
     *
     * <p><b>主池</b>：{@code y = 最低层 + 1}（即室内地板面那一层）中<b>未被 pattern 声明</b>、
     * 且格坐标距核心 ≤ {@code bathRadius} 的格位。取"未声明"而非"地板层全部格位"是因为
     * 地板层含核心基座与仪式石环（它们占 {@code y = 最低层 + 1} 那一格），水画进去会被埋在
     * 方块内不可见。得到的集合恰好等于"玩家能站进去泡水"的全部格位。
     *
     * <p><b>院子</b>：<b>最低层</b>（{@code y = 最低层}）中未被声明、且<b>被已声明格位包围</b>的
     * 空连通块（自图外 flood fill 后取余）。灵浴外圈石砖环只在最低层是实心的，故包围判定
     * MUST 在该层做；实测 L1–L3 得 0 块、L4/L5 各得 4 块（每块 79 格），与"4 阶起外圈才有院子"
     * 一致。低阶自动没有院子水，无需按阶特判。
     *
     * <p><b>两块的底面相差一格</b>：主池坐在最低层的顶面（{@code minY + 1}），院子低一格
     * （{@code minY}）—— 院子处最低层未声明、无地板，地面在更低一层。与主池同高会读作
     * "四个悬空平板架在齐腰的池子旁边"，而不是下沉一格的水院。
     *
     * <p>逐格 {@code edge} = 到最近<b>非水面格</b>的切比雪夫距离经 chamfer 两遍距离变换
     * 归一化（1=贴边，0=离边 ≥2 格）。距离源 MUST 是非水面格（含图外补的一圈）；
     * 反过来初始化会让每格都读成"贴边"，整池等亮度衰减。
     *
     * @param bathRadius 主池半径（格）；≤0 时不生成主池
     * @return 空 {@code cells} 表示该阶不存在该 pattern 或切片为空，调用方 MUST 视为「不绘制」
     */
    public static BathSurface bathSurface(RitualPattern pattern, int level, double bathRadius) {
        RitualPattern.LevelSlice slice = null;
        for (RitualPattern.LevelSlice candidate : pattern.levels()) {
            if (candidate.level() == level) {
                slice = candidate;
                break;
            }
        }
        if (slice == null || slice.blocks().isEmpty()) {
            return new BathSurface(List.of(), 0);
        }
        int minY = Integer.MAX_VALUE;
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (RitualPattern.BlockEntry block : slice.blocks()) {
            minY = Math.min(minY, block.y());
            minX = Math.min(minX, block.x());
            maxX = Math.max(maxX, block.x());
            minZ = Math.min(minZ, block.z());
            maxZ = Math.max(maxZ, block.z());
        }
        long seedBase = pattern.id().hashCode() * 0x9E3779B97F4A7C15L + minY * 0x2545F491L;

        // 图外补一圈 margin，flood fill 的"外部"起点必须落在确定的水/陆之外
        final int margin = 1;
        int x0 = minX - margin, x1 = maxX + margin;
        int z0 = minZ - margin, z1 = maxZ + margin;
        int w = x1 - x0 + 1;
        int h = z1 - z0 + 1;

        // 最低层已声明格位（= 实心地板 / 外圈环）
        boolean[][] floor = new boolean[h][w];
        boolean[][] air = new boolean[h][w];
        for (RitualPattern.BlockEntry block : slice.blocks()) {
            if (block.y() != minY) {
                continue;
            }
            floor[block.z() - z0][block.x() - x0] = true;
        }
        for (int z = 0; z < h; z++) {
            for (int x = 0; x < w; x++) {
                air[z][x] = !floor[z][x];
            }
        }

        // ---- 院子：最低层中被已声明格位包围的空连通块 ----
        boolean[][] seen = new boolean[h][w];
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        for (int x = 0; x < w; x++) {
            push(queue, seen, air, x, 0);
            push(queue, seen, air, x, h - 1);
        }
        for (int z = 0; z < h; z++) {
            push(queue, seen, air, 0, z);
            push(queue, seen, air, w - 1, z);
        }
        while (!queue.isEmpty()) {
            int[] c = queue.poll();
            for (int[] d : NEIGHBOURS) {
                int nx = c[0] + d[0], nz = c[1] + d[1];
                if (nx < 0 || nz < 0 || nx >= w || nz >= h) {
                    continue;
                }
                if (air[nz][nx] && !seen[nz][nx]) {
                    seen[nz][nx] = true;
                    queue.add(new int[]{nx, nz});
                }
            }
        }
        List<int[]> water = new ArrayList<>();
        boolean[][] taken = new boolean[h][w];
        // 逐格记录"该格属于主池（true）还是院子（false）"，供最后一步决定底面 Y。
        boolean[][] yHigh = new boolean[h][w];
        for (int z = 0; z < h; z++) {
            for (int x = 0; x < w; x++) {
                if (!air[z][x] || seen[z][x]) {
                    continue;
                }
                // 收集一个连通块
                List<int[]> comp = new ArrayList<>();
                ArrayDeque<int[]> q2 = new ArrayDeque<>();
                q2.add(new int[]{x, z});
                seen[z][x] = true;
                while (!q2.isEmpty()) {
                    int[] c = q2.poll();
                    comp.add(c);
                    for (int[] d : NEIGHBOURS) {
                        int nx = c[0] + d[0], nz = c[1] + d[1];
                        if (nx < 0 || nz < 0 || nx >= w || nz >= h) {
                            continue;
                        }
                        if (air[nz][nx] && !seen[nz][nx]) {
                            seen[nz][nx] = true;
                            q2.add(new int[]{nx, nz});
                        }
                    }
                }
                if (comp.size() < MIN_COURTYARD_CELLS) {
                    continue;
                }
                for (int[] c : comp) {
                    water.add(c);
                    taken[c[1]][c[0]] = true;
                }
            }
        }
        // 院子 MUST 比主池低一格：最低层在院子处未声明（无地板），地面在更低一层。
        // 与主池同高会读作"四个悬空平板架在齐腰的池子旁边"，而不是下沉一格的水院。
        int courtyardBaseY = minY;

        // ---- 主池：地板面那一层未声明、且在浴区半径内 ----
        if (bathRadius > 0.0D) {
            double r2 = bathRadius * bathRadius;
            for (RitualPattern.BlockEntry block : slice.blocks()) {
                if (block.y() == minY + 1) {
                    int lx = block.x() - x0, lz = block.z() - z0;
                    if (lx >= 0 && lz >= 0 && lx < w && lz < h) {
                        taken[lz][lx] = true;   // 已声明 = 实心，不铺水
                    }
                }
            }
            int reach = (int) Math.ceil(bathRadius);
            for (int z = -reach; z <= reach; z++) {
                for (int x = -reach; x <= reach; x++) {
                    // ⚠️ 半径 MUST 按**格坐标距**（相对核心方块原点）算，即 hypot(x, z)。
                    //    按格心算会写成 hypot(x + 0.5, z + 0.5)，于是 |−3| 记成 2.5、
                    //    |+3| 记成 3.5 —— r = 3 时多出的 8 格**全落在 -x/-z 一个象限**，
                    //    主池读作明显偏心。此坑只在半径取整数值时发作（3.0 / 4.0），
                    //    故 r = 3.5 之类非整值下对称，回归时务必用默认 3.0 覆盖。
                    if (x * x + z * z > r2) {
                        continue;
                    }
                    int lx = x - x0, lz = z - z0;
                    if (lx < 0 || lz < 0 || lx >= w || lz >= h) {
                        continue;
                    }
                    if (!taken[lz][lx]) {
                        taken[lz][lx] = true;
                        yHigh[lz][lx] = true;
                        water.add(new int[]{lx, lz});
                    }
                }
            }
        }

        if (water.isEmpty()) {
            return new BathSurface(List.of(), 0);
        }

        // ---- 边缘：chamfer 两遍距离变换（距离源 = 非水面格，图外补 0）----
        int dw = w + 2, dh = h + 2;
        final int far = 1 << 20;
        int[][] depth = new int[dh][dw];
        for (int[] row : depth) {
            java.util.Arrays.fill(row, far);
        }
        for (int x = 0; x < dw; x++) {
            depth[0][x] = 0;
            depth[dh - 1][x] = 0;
        }
        for (int z = 0; z < dh; z++) {
            depth[z][0] = 0;
            depth[z][dw - 1] = 0;
        }
        chamferForward(depth, dw, dh);
        chamferBackward(depth, dw, dh);

        List<WaterCell> cells = new ArrayList<>(water.size());
        int maxCheb = 0;
        for (int[] c : water) {
            float edge = edgeOf(depth[c[1] + 1][c[0] + 1], FADE_CELLS);
            int wx = c[0] + x0, wz = c[1] + z0;
            maxCheb = Math.max(maxCheb, Math.max(Math.abs(wx), Math.abs(wz)));
            // 底面 Y：主池坐在最低层的顶面（minY+1）；院子低一格（minY）。
            int baseY = yHigh[c[1]][c[0]] ? minY + 1 : courtyardBaseY;
            cells.add(new WaterCell(wx + 0.5D, baseY, wz + 0.5D, edge,
                    seedBase + cells.size() * 131071L));
        }
        return new BathSurface(List.copyOf(cells), maxCheb);
    }

    /** 4 邻域（院子是方形，用 4 邻域比 8 邻域更贴合"方形区域"的语义）。 */
    private static final int[][] NEIGHBOURS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    /**
     * 院子连通块的最小格数：低于此值视为"结构缝隙"而非院子，不铺水。
     * 实测灵浴院子为 79 格，该阈值只作兜底（防止将来 pattern 改动产生 1~2 格的破洞被误判成院子）。
     */
    private static final int MIN_COURTYARD_CELLS = 16;

    private static void push(ArrayDeque<int[]> queue, boolean[][] seen, boolean[][] air, int x, int z) {
        if (air[z][x] && !seen[z][x]) {
            seen[z][x] = true;
            queue.add(new int[]{x, z});
        }
    }

    /** 边缘渐隐的格数：离边 {@code FADE_CELLS} 格外即为池心（edge = 0）。 */
    private static final int FADE_CELLS = 2;

    /** 边缘系数：depth 0（贴边）→ 1；depth ≥ {@code fade} → 0。 */
    private static float edgeOf(int depth, int fade) {
        if (depth >= fade) {
            return 0F;
        }
        return 1.0F - (float) depth / (float) fade;
    }

    /** chamfer 距离变换正遍（前向）：8 邻域，步长 1。 */
    private static void chamferForward(int[][] d, int w, int h) {
        for (int z = 0; z < h; z++) {
            for (int x = 0; x < w; x++) {
                int best = d[z][x];
                if (z > 0) {
                    best = Math.min(best, d[z - 1][x] + 1);
                    if (x > 0) {
                        best = Math.min(best, d[z - 1][x - 1] + 1);
                    }
                    if (x + 1 < w) {
                        best = Math.min(best, d[z - 1][x + 1] + 1);
                    }
                }
                if (x > 0) {
                    best = Math.min(best, d[z][x - 1] + 1);
                }
                d[z][x] = best;
            }
        }
    }

    /** chamfer 距离变换反遍（后向）：补齐正遍看不到的邻域。 */
    private static void chamferBackward(int[][] d, int w, int h) {
        for (int z = h - 1; z >= 0; z--) {
            for (int x = w - 1; x >= 0; x--) {
                int best = d[z][x];
                if (z + 1 < h) {
                    best = Math.min(best, d[z + 1][x] + 1);
                    if (x + 1 < w) {
                        best = Math.min(best, d[z + 1][x + 1] + 1);
                    }
                    if (x > 0) {
                        best = Math.min(best, d[z + 1][x - 1] + 1);
                    }
                }
                if (x + 1 < w) {
                    best = Math.min(best, d[z][x + 1] + 1);
                }
                d[z][x] = best;
            }
        }
    }

    private static double clamp(double value, double min, double max) {
        return value < min ? min : (value > max ? max : value);
    }
}
