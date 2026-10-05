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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 少名汤池水面布局的离线守卫。
 *
 * <p>核心不变量（都是"看不见的水"这一类坑）：
 * <ul>
 *   <li><b>池面底面 MUST 等于核心所在层</b>。少名的最低层（y=-2）只是半径 5~6 的一圈滴水石，
 *       其上 y=-1 才是铺满的实心圆台。若照灵浴那样把池锚在"最低层顶面"，水会整片埋进
 *       y=-1 的实心方块里 —— 实机读作"完全没有特效"。</li>
 *   <li><b>水 MUST NOT 悬在结构破洞之上</b>：y=-1 在半径 6~7 处是空的（圆台开了环槽），
 *       只判"本格为空"会把水铺到空洞上，视觉上是一圈悬空薄片。故 MUST 同时要求脚下有地板。</li>
 *   <li><b>核心与祭品台 MUST NOT 被灌水</b>：它们就坐在池底那层上，判据"脚下有地板 + 本格为空"
 *       天然把两者排除，故池水从它们脚下流过、被顶出水面。</li>
 * </ul>
 *
 * <p>刻意不碰配置规范（单测环境未加载）：池半径显式传入，等价于
 * {@code FX_SUNAKO_WATER_RADIUS} 的默认 5.0。
 */
class SunakoBrewPoolTest {

    private static final String PATTERN = "sunako_circle.json";
    private static final ResourceLocation ID = Gensokyou.id("sunako_circle");

    /** 池半径，等价于 {@code FX_SUNAKO_WATER_RADIUS} 的默认 9.0（凹槽外沿半径 8.49）。 */
    private static final double POOL_RADIUS = 9.0D;

    /** 池面底面 Y = {@code anchorY - 1}：即 y=-2 那圈滴水石的顶面（环形凹槽的槽底）。 */
    private static final double GROOVE_Y = -1.0D;

    /** 槽底那一层（y=-2）。 */
    private static final int GROOVE_FLOOR_Y = -2;

    /**
     * 各阶池面格数（半径 9.0）。
     *
     * <p>三阶<b>恒等</b>（各 80 格）：环形凹槽开在 y=-1 层，而三阶的增量全落在
     * y=-1 的<b>外圈</b>（半径 9~12 的墙）与 y≥0，凹槽本身没人动。
     */
    private static final java.util.Map<Integer, Integer> CELLS_BY_LEVEL = java.util.Map.of(
            1, 80, 2, 80, 3, 80);

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

    private static RitualFxLayout.BathSurface pool(RitualPattern pattern, int level) {
        return RitualFxLayout.brewPool(pattern, level, POOL_RADIUS);
    }

    private static Set<String> solidOf(RitualPattern pattern, int level) {
        Set<String> solid = new HashSet<>();
        for (RitualPattern.LevelSlice candidate : pattern.levels()) {
            if (candidate.level() != level) {
                continue;
            }
            for (RitualPattern.BlockEntry b : candidate.blocks()) {
                solid.add(b.x() + "," + b.y() + "," + b.z());
            }
        }
        return solid;
    }

    @Test
    void footprintMatchesDerivedCounts() throws IOException {
        RitualPattern pattern = pattern();
        for (int level = 1; level <= 3; level++) {
            RitualFxLayout.BathSurface s = pool(pattern, level);
            assertEquals(CELLS_BY_LEVEL.get(level), s.cells().size(),
                    "level " + level + " water cell count");
            assertEquals(6, s.radiusXZ(), "level " + level + " water radius");
        }
    }

    /**
     * 池面底面 MUST 等于 {@code anchorY - 1}（= -1），即环形凹槽的槽底。
     *
     * <p>两种错法都读作"水放错地方"：锚在核心所在层（0）会把水铺在中央平台<b>顶面</b>上，
     * 比凹槽高一整格（实机反馈："水的特效高度高了，应该低渲染一格高度，到旁边的环形池子里"）；
     * 锚在最低层（-2）则整个埋进滴水石方块里彻底看不见。
     */
    @Test
    void poolSitsInTheRingChannel() throws IOException {
        RitualPattern pattern = pattern();
        for (int level = 1; level <= 3; level++) {
            for (RitualFxLayout.WaterCell cell : pool(pattern, level).cells()) {
                assertEquals(GROOVE_Y, cell.y(),
                        "level " + level + " water must sit one block below the core, got " + cell);
            }
        }
    }

    /** 池面 MUST 落在环形凹槽的半径带内：既不压中央平台（r≤5），也不越到外圈墙基。 */
    @Test
    void poolStaysInsideTheChannelBand() throws IOException {
        RitualPattern pattern = pattern();
        for (int level = 1; level <= 3; level++) {
            for (RitualFxLayout.WaterCell cell : pool(pattern, level).cells()) {
                double r = Math.hypot(gx(cell), gz(cell));
                assertTrue(r > 5.0D,
                        "level " + level + " water sits on the central dais at " + cell);
                assertTrue(r < 8.5D,
                        "level " + level + " water escaped the channel at " + cell);
            }
        }
    }

    /** 每一格池面 MUST 脚下有地板：破洞之上不铺水，否则是一圈悬空薄片。 */
    @Test
    void everyWaterCellHasSolidFloor() throws IOException {
        RitualPattern pattern = pattern();
        for (int level = 1; level <= 3; level++) {
            Set<String> solid = solidOf(pattern, level);
            for (RitualFxLayout.WaterCell cell : pool(pattern, level).cells()) {
                String below = gx(cell) + "," + GROOVE_FLOOR_Y + "," + gz(cell);
                assertTrue(solid.contains(below),
                        "level " + level + " water at " + cell + " floats over a hole (no " + below + ")");
            }
        }
    }

    /** 每一格池面 MUST 自身为空：水不得画进实心方块里。 */
    @Test
    void noWaterCellIsInsideABlock() throws IOException {
        RitualPattern pattern = pattern();
        for (int level = 1; level <= 3; level++) {
            Set<String> solid = solidOf(pattern, level);
            for (RitualFxLayout.WaterCell cell : pool(pattern, level).cells()) {
                String here = gx(cell) + "," + (int) GROOVE_Y + "," + gz(cell);
                assertFalse(solid.contains(here),
                        "level " + level + " water is inside a solid block at " + cell);
            }
        }
    }

    /** 核心轴线 MUST NOT 被灌水：池在环形凹槽（y=-1 空层），核心在中央平台上（y=0 实心）。 */
    @Test
    void coreCellIsNotWatered() throws IOException {
        RitualPattern pattern = pattern();
        for (int level = 1; level <= 3; level++) {
            for (RitualFxLayout.WaterCell cell : pool(pattern, level).cells()) {
                assertFalse(gx(cell) == 0 && gz(cell) == 0,
                        "level " + level + " water covers the core axis at " + cell);
            }
        }
    }

    /** 池面 MUST 四向对称：绕核心轴旋转 90° 后仍落在同一集合里。 */
    @Test
    void poolIsFourFoldSymmetric() throws IOException {
        RitualPattern pattern = pattern();
        for (int level = 1; level <= 3; level++) {
            Set<String> cells = new HashSet<>();
            for (RitualFxLayout.WaterCell cell : pool(pattern, level).cells()) {
                cells.add(gx(cell) + "," + gz(cell));
            }
            for (String cell : cells) {
                String[] parts = cell.split(",");
                int x = Integer.parseInt(parts[0]);
                int z = Integer.parseInt(parts[1]);
                assertTrue(cells.contains(-z + "," + x), "level " + level + " missing 90deg of " + cell);
                assertTrue(cells.contains(-x + "," + -z), "level " + level + " missing 180deg of " + cell);
                assertTrue(cells.contains(z + "," + -x), "level " + level + " missing 270deg of " + cell);
            }
        }
    }

    /** 池面 MUST NOT 越出配置半径（否则会爬上圆台外的墙基）。 */
    @Test
    void poolStaysWithinRadius() throws IOException {
        RitualPattern pattern = pattern();
        for (int level = 1; level <= 3; level++) {
            for (RitualFxLayout.WaterCell cell : pool(pattern, level).cells()) {
                assertTrue(Math.hypot(gx(cell), gz(cell)) <= POOL_RADIUS,
                        "level " + level + " water outside radius " + POOL_RADIUS + " at " + cell);
            }
        }
    }

    /** 渐隐值 MUST 落在 0..1（越界会被 vertex alpha 截断成整格全黑或全白）。 */
    @Test
    void edgeStaysInRange() throws IOException {
        RitualPattern pattern = pattern();
        for (int level = 1; level <= 3; level++) {
            for (RitualFxLayout.WaterCell cell : pool(pattern, level).cells()) {
                assertTrue(cell.edge() >= 0.0F && cell.edge() <= 1.0F,
                        "edge out of range: " + cell.edge());
            }
        }
    }

    /** 池心 MUST 有完全不衰减的格：否则整片水读作"只有边缘一圈微光"。 */
    @Test
    void interiorCellsAreNotDimmed() throws IOException {
        RitualPattern pattern = pattern();
        for (int level = 1; level <= 3; level++) {
            List<RitualFxLayout.WaterCell> cells = pool(pattern, level).cells();
            int full = 0;
            for (RitualFxLayout.WaterCell cell : cells) {
                if (cell.edge() <= 0.0F) {
                    full++;
                }
            }
            assertTrue(full > 0, "level " + level + " has no undimmed interior cell");
        }
    }

    /** 布局是纯函数：同输入必得同输出（客户端可长期缓存的依据）。 */
    @Test
    void layoutIsDeterministic() throws IOException {
        RitualPattern pattern = pattern();
        RitualFxLayout.BathSurface a = pool(pattern, 3);
        RitualFxLayout.BathSurface b = pool(pattern, 3);
        assertEquals(a.cells().size(), b.cells().size());
        assertEquals(a.radiusXZ(), b.radiusXZ());
        for (int i = 0; i < a.cells().size(); i++) {
            assertEquals(a.cells().get(i).x(), b.cells().get(i).x(), 1e-9);
            assertEquals(a.cells().get(i).y(), b.cells().get(i).y(), 1e-9);
            assertEquals(a.cells().get(i).z(), b.cells().get(i).z(), 1e-9);
            assertEquals(a.cells().get(i).edge(), b.cells().get(i).edge(), 1e-6);
            assertEquals(a.cells().get(i).seed(), b.cells().get(i).seed());
        }
    }

    /** 半径 ≤0 MUST 返回空布局（调用方据此"不绘制"），不抛异常也不画空几何。 */
    @Test
    void nonPositiveRadiusYieldsEmptyLayout() throws IOException {
        RitualPattern pattern = pattern();
        assertTrue(RitualFxLayout.brewPool(pattern, 1, 0.0D).cells().isEmpty());
        assertTrue(RitualFxLayout.brewPool(pattern, 1, -1.0D).cells().isEmpty());
    }

    /** 不存在的阶级 MUST 返回空布局。 */
    @Test
    void missingLevelYieldsEmptyLayout() throws IOException {
        RitualFxLayout.BathSurface s = pool(pattern(), 9);
        assertTrue(s.cells().isEmpty());
        assertEquals(0, s.radiusXZ());
    }

    private static int gx(RitualFxLayout.WaterCell cell) {
        return (int) (cell.x() - 0.5D);
    }

    private static int gz(RitualFxLayout.WaterCell cell) {
        return (int) (cell.z() - 0.5D);
    }
}