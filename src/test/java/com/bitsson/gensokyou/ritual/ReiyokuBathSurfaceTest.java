package com.bitsson.gensokyou.ritual;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.support.MinecraftTestBootstrap;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 灵浴水面布局的离线守卫：<b>主池 + 外圈院子</b>占地、<b>走道无水</b>、边缘只让最外圈渐隐、确定性。
 *
 * <p>三条血泪断言：
 * <ul>
 *   <li><b>包围判定必须在最低层</b>：灵浴外圈石砖环只在 {@code y = 核心Y-1} 是实心的，
 *       上一层的环有缺口。在 {@code y = 核心Y} 做 flood fill 会只得到 3 个 8~9 格的小口袋，
 *       4 个院子全丢；实测院子在最低层是 4 × 79 格，L1–L3 为 0。</li>
 *   <li><b>走廊不得铺水</b>：轴向通道是未声明且与图外连通的，包围法天然排除它。一旦
 *       改成"包围盒内所有未声明格"，整条走廊 + 整个外圈空地都会变成水池。</li>
 *   <li><b>距离变换方向</b>：chamfer 的距离源必须是<b>非水面格</b>（含图外补的一圈）。
 *       反过来初始化（格位置 0）会让每一格都读成"贴边"，整池等亮度衰减 —— 实机表现是
 *       "一大片水只剩边缘一圈微光、中心反而没有"。</li>
 * </ul>
 *
 * <p>渐隐范围同理只允许最外 1~2 圈：若用"离池心距离"归一化，大院子外缘会按比例变暗，
 * 读起来只剩中心一小块。
 *
 * <p>刻意不碰配置规范（单测环境未加载，读 config 会抛
 * "Cannot get config value before config is loaded"）：浴区半径作为参数显式传入
 * （等价于 {@code REIYOKU_BATH_RADIUS} 的默认 3.0），也不构造 {@link RitualMatch}。
 */
class ReiyokuBathSurfaceTest {

    private static final String PATTERN = "reiyoku_circle.json";
    private static final ResourceLocation ID = Gensokyou.id("reiyoku_circle");

    /** 浴区半径，等价于 {@code REIYOKU_BATH_RADIUS} 的默认 3.0。 */
    private static final double BATH_RADIUS = 3.0D;

    /**
     * 各阶水面格数 = 主池 12 + 院子 4×79。
     *
     * <p>院子在 L4 才随外圈环一起出现，L1–L3 为 0 块——这正是"4 阶起才有窝"的实现，
     * 免去按阶硬编码特判。
     */
    private static final Map<Integer, Integer> CELLS_BY_LEVEL = Map.of(
            1, 12, 2, 12, 3, 12, 4, 328, 5, 328);

    /** 各阶水面水平切比雪夫半径：低阶只有主池（3），4 阶起被院子撑到 11。 */
    private static final Map<Integer, Integer> RADIUS_BY_LEVEL = Map.of(
            1, 3, 2, 3, 3, 3, 4, 11, 5, 11);

    /**
     * 主池 MUST 四向对称（4 重旋转不变）。
     *
     * <p>这条钉死一个曾经真实发生、且只在默认半径下暴露的 bug：浴区半径原按格心算成
     * {@code hypot(x + 0.5, z + 0.5)}，于是 {@code |−3| 记作 2.5}、{@code |+3| 记作 3.5} ——
     * r = 3 时多出的 8 格<b>全落在 −x/−z 一个象限</b>，主池读作明显偏心（实机反馈：
     * "主核心室里的水看起来不对称了"）。非整半径（如 3.5）恰好对称，故回归 MUST 用默认 3.0。
     */
    private static final int POOL_CELLS = 12;

    /** pattern 解析要读方块/标签注册表，故必须先 bootstrap（否则单独跑本类会炸）。 */
    @BeforeAll
    static void bootstrap() {
        MinecraftTestBootstrap.ensureStarted();
    }

    private static RitualPattern pattern() throws IOException {
        Path p = Path.of("src", "main", "resources", "data", "gensokyou", "rituals", PATTERN);
        JsonObject json;
        try (var reader = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
            json = JsonParser.parseReader(reader).getAsJsonObject();
        }
        return RitualPatternLoader.parseForEdit(ID, json);
    }

    private static RitualFxLayout.BathSurface surface(RitualPattern pattern, int level) {
        return RitualFxLayout.bathSurface(pattern, level, BATH_RADIUS);
    }

    @Test
    void footprintIsPoolPlusCourtyards() throws IOException {
        RitualPattern pattern = pattern();
        for (int level = 1; level <= 5; level++) {
            RitualFxLayout.BathSurface s = surface(pattern, level);
            assertEquals(CELLS_BY_LEVEL.get(level), s.cells().size(),
                    "level " + level + " water cell count (pool 12 + courtyards)");
            assertEquals(RADIUS_BY_LEVEL.get(level), s.radiusXZ(),
                    "level " + level + " water radius");
        }
    }

    /** 主池 MUST 四向对称：把每个格绕核心轴做 90° 旋转后仍落在同一个集合里。 */
    @Test
    void poolIsFourFoldSymmetric() throws IOException {
        RitualPattern pattern = pattern();
        for (int level = 1; level <= 5; level++) {
            java.util.Set<String> pool = new java.util.HashSet<>();
            for (RitualFxLayout.WaterCell cell : surface(pattern, level).cells()) {
                if (cheb(cell) <= 3) {
                    pool.add(key(grid(cell), gridZ(cell)));
                }
            }
            assertEquals(POOL_CELLS, pool.size(),
                    "level " + level + " pool cells (must be 4-fold symmetric)");
            for (String cell : pool) {
                int[] xy = parse(cell);
                assertTrue(pool.contains(key(-xy[1], xy[0])),
                        "level " + level + " pool missing 90deg rotation of " + cell);
                assertTrue(pool.contains(key(-xy[0], -xy[1])),
                        "level " + level + " pool missing 180deg rotation of " + cell);
                assertTrue(pool.contains(key(xy[1], -xy[0])),
                        "level " + level + " pool missing 270deg rotation of " + cell);
            }
        }
    }

    /**
     * 外圈院子 MUST 比主池低一格。
     *
     * <p>院子处最低层未声明（无地板），地面在更低一层；与主池同高会读作"四个悬空平板
     * 架在齐腰的池子旁边"，而不是下沉一格的水院（实机反馈："外围 4 池的高度高了 1 格"）。
     */
    @Test
    void courtyardsSitOneBlockLowerThanThePool() throws IOException {
        RitualPattern pattern = pattern();
        for (int level = 1; level <= 5; level++) {
            double poolY = Double.NaN;
            double yardY = Double.NaN;
            for (RitualFxLayout.WaterCell cell : surface(pattern, level).cells()) {
                if (cheb(cell) <= 3) {
                    poolY = cell.y();
                } else if (Double.isNaN(yardY)) {
                    yardY = cell.y();
                }
            }
            if (level <= 3) {
                continue;   // 低阶无院子
            }
            assertEquals(-1.0D, yardY - poolY,
                    "level " + level + " courtyard water must sit exactly 1 block lower");
        }
    }

    /** 院子 MUST 从 4 阶起才出现：低阶外圈没有实心环，flood fill 得不到被包围的空块。 */
    @Test
    void courtyardsAppearOnlyFromLevelFour() throws IOException {
        RitualPattern pattern = pattern();
        for (int level = 1; level <= 3; level++) {
            for (RitualFxLayout.WaterCell cell : surface(pattern, level).cells()) {
                assertTrue(cheb(cell) <= 3,
                        "level " + level + " has a courtyard cell at " + cell);
            }
        }
        for (int level = 4; level <= 5; level++) {
            int corners = 0;
            for (RitualFxLayout.WaterCell cell : surface(pattern, level).cells()) {
                if (Math.abs(grid(cell)) >= 9 && Math.abs(gridZ(cell)) >= 9) {
                    corners++;
                }
            }
            // 4 个院子，每个外角有 3×3 = 9 格
            assertEquals(36, corners, "level " + level + " courtyard outer corners");
        }
    }

    /**
     * 核心十字轴（{@code x=0} 或 {@code z=0} 的行/列）出了主池 MUST NOT 铺水。
     *
     * <p>这正是"水摆在走廊上"那条反馈的回归守卫：轴向通道是未声明且与图外连通的，
     * 包围法天然排除它。若改成"包围盒内所有未声明格"，整条通道 + 整个外圈空地都会变成水池。
     *
     * <p>判据 MUST 用"网格坐标恰为 0"而非"靠近中轴 ≤1 格"——院子实测跨到 {@code z = ±1}
     * （外圈环在轴向多铺了一格），用 ≤1 会把院子内角误判成走廊。
     */
    @Test
    void axialWalkwaysAreNotWatered() throws IOException {
        RitualPattern pattern = pattern();
        for (int level = 1; level <= 5; level++) {
            for (RitualFxLayout.WaterCell cell : surface(pattern, level).cells()) {
                boolean onAxis = grid(cell) == 0 || gridZ(cell) == 0;
                assertFalse(onAxis && cheb(cell) >= 4,
                        "level " + level + " walkway is watered at " + cell);
            }
        }
    }

    /** 外圈石砖环（已声明，故必然非水）不得出现水格。 */
    @Test
    void outerRingIsNotWatered() throws IOException {
        RitualPattern pattern = pattern();
        for (int level = 1; level <= 5; level++) {
            for (RitualFxLayout.WaterCell cell : surface(pattern, level).cells()) {
                assertTrue(cheb(cell) < 12,
                        "level " + level + " water cell inside the outer ring at " + cell);
            }
        }
    }

    /** 池心/院心必须完全不衰减——否则大院子读起来只剩一小块。 */
    @Test
    void interiorCellsAreNotDimmed() throws IOException {
        RitualPattern pattern = pattern();
        for (int level = 1; level <= 5; level++) {
            List<RitualFxLayout.WaterCell> cells = surface(pattern, level).cells();
            int full = 0;
            for (RitualFxLayout.WaterCell cell : cells) {
                if (cell.edge() <= 0.0F) {
                    full++;
                }
            }
            assertTrue(full > 0, "level " + level + " has no undimmed interior cell");
            if (level >= 4) {
                // 院子占 316/332 格，主体 MUST 处于满亮度
                assertTrue(full * 100 >= cells.size() * 80,
                        "level " + level + ": only " + full + "/" + cells.size()
                                + " cells at full brightness (>=80% expected)");
            }
        }
    }

    /** 渐隐值 MUST 落在 0..1（越界会被 vertex alpha 截断成整格全黑或全白）。 */
    @Test
    void edgeStaysInRange() throws IOException {
        RitualPattern pattern = pattern();
        for (int level = 1; level <= 5; level++) {
            for (RitualFxLayout.WaterCell cell : surface(pattern, level).cells()) {
                assertTrue(cell.edge() >= 0.0F && cell.edge() <= 1.0F,
                        "edge out of range: " + cell.edge());
            }
        }
    }

    /** 布局是纯函数：同输入必得同输出（可长期缓存的依据）。 */
    @Test
    void layoutIsDeterministic() throws IOException {
        RitualPattern pattern = pattern();
        RitualFxLayout.BathSurface a = surface(pattern, 5);
        RitualFxLayout.BathSurface b = surface(pattern, 5);
        assertEquals(a.cells().size(), b.cells().size());
        assertEquals(a.radiusXZ(), b.radiusXZ());
        for (int i = 0; i < a.cells().size(); i++) {
            assertEquals(a.cells().get(i).x(), b.cells().get(i).x(), 1e-9);
            assertEquals(a.cells().get(i).z(), b.cells().get(i).z(), 1e-9);
            assertEquals(a.cells().get(i).edge(), b.cells().get(i).edge(), 1e-6);
            assertEquals(a.cells().get(i).seed(), b.cells().get(i).seed());
        }
    }

    /** 浴区半径 ≤0 MUST 只剩院子（主池被关掉），不抛异常也不画空几何。 */
    @Test
    void zeroBathRadiusKeepsOnlyCourtyards() throws IOException {
        RitualPattern pattern = pattern();
        assertEquals(316, RitualFxLayout.bathSurface(pattern, 4, 0.0D).cells().size(),
                "courtyards alone should be 4 x 79");
        assertTrue(RitualFxLayout.bathSurface(pattern, 1, 0.0D).cells().isEmpty(),
                "level 1 has no courtyards, so no water at all");
    }
    /** 不存在的阶级 MUST 返回空布局（调用方据此"不绘制"，而不是画 0 条的错误几何）。 */
    @Test
    void missingLevelYieldsEmptyLayout() throws IOException {
        RitualFxLayout.BathSurface s = surface(pattern(), 9);
        assertTrue(s.cells().isEmpty());
        assertEquals(0, s.radiusXZ());
    }

    private static int cheb(RitualFxLayout.WaterCell cell) {
        return (int) Math.max(Math.abs(cell.x() - 0.5D), Math.abs(cell.z() - 0.5D));
    }

    /** 网格 X 坐标（格心的 0.5 偏移需扣掉）。 */
    private static double grid(RitualFxLayout.WaterCell cell) {
        return cell.x() - 0.5D;
    }

    /** 网格 Z 坐标。 */
    private static double gridZ(RitualFxLayout.WaterCell cell) {
        return cell.z() - 0.5D;
    }

    private static String key(double x, double z) {
        return (int) x + "," + (int) z;
    }

    private static int[] parse(String cell) {
        String[] parts = cell.split(",");
        return new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1])};
    }
}
