package com.bitsson.gensokyou.ritual;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.ritual.RitualPattern.BlockEntry;
import com.bitsson.gensokyou.ritual.RitualPattern.LevelSlice;
import com.bitsson.gensokyou.support.MinecraftTestBootstrap;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 摆放规划数值的回归：建议离地高度（{@code |minY|}）与最大占地半径（Chebyshev）。
 *
 * <p>这两个数直接决定玩家"核心埋多深 / 核心间留多远"，算错会让高阶升级被静默拒绝
 * （被占格位 → {@code classify} 判冲突 → {@code build} 整体拒绝），且界面上看不出原因。
 */
class RitualPlacementMetricsTest {

    /** 构造 ItemStack / 解析图案需要 Minecraft 注册表就绪。 */
    @BeforeAll
    static void bootstrap() {
        MinecraftTestBootstrap.ensureStarted();
    }

    private static BlockEntry at(int x, int y, int z) {
        return new BlockEntry('0', x, y, z, null);
    }

    private static LevelSlice slice(BlockEntry... entries) {
        return new LevelSlice(0, List.of(entries));
    }

    @Test
    void coreHeightIsAbsoluteDepthOfLowestCell() {
        // 6 层在核心下、100 层在核心上 → 建议离地高度 6（向上多高不参与）
        LevelSlice s = slice(
                at(0, -6, 0), at(1, -3, 0), at(2, -1, 0),
                at(0, 0, 0), at(0, 100, 0));
        assertEquals(-6, s.minY());
        assertEquals(6, Math.abs(s.minY()));
        assertEquals(100, s.maxY());
    }

    @Test
    void flatPatternNeedsNoLifting() {
        LevelSlice s = slice(at(0, 0, 0), at(1, 0, 0), at(0, 0, 1));
        assertEquals(0, s.minY());
        assertEquals(1, s.maxChebRadius());
    }

    @Test
    void radiusIsChebyshevNotEuclidean() {
        // 对角 (3,4) 距离 5，但格位占用是轴对齐的 → 正方形半宽取 4
        LevelSlice s = slice(at(3, 0, 4));
        assertEquals(4, s.maxChebRadius());
    }

    @Test
    void fourWayExpansionIsAlreadyFullyRepresented() {
        // loader 已把 (x,z) 展开为 (±x, y, ±z)，故原始坐标即完整跨度
        LevelSlice s = slice(at(7, 0, 0), at(0, 0, 3));
        assertEquals(7, s.maxChebRadius());
    }

    @Test
    void emptySliceIsZeroNotNegative() {
        LevelSlice s = slice();
        assertEquals(0, s.minY());
        assertEquals(0, s.maxChebRadius());
        assertTrue(s.blocks().isEmpty());
    }

    /**
     * 用真实图案 JSON 核一遍，防止 loader 的四重展开或累加语义与这些查询脱节。
     * 走 {@code parseForEdit} 直接读资源（与 ReiyokuBathSurfaceTest 同款），
     * 因为 {@code byId()} 依赖运行期填充的单例，单元测试里为空。
     */
    @Test
    void realPatternMetricsMatchKnownValues() throws IOException {
        LevelSlice zaohuaTop = topSliceOf("zaohua_circle");
        // zaohua 最高阶整座结构都在核心之下（该级最高格位为 -1）
        assertEquals(-7, zaohuaTop.minY());
        assertEquals(7, Math.abs(zaohuaTop.minY()));
        assertTrue(zaohuaTop.maxY() <= 2, "zaohua 不该向上长太高，实际 " + zaohuaTop.maxY());
        assertEquals(20, zaohuaTop.maxChebRadius());

        LevelSlice relayTop = topSliceOf("resonance_relay");
        assertEquals(-1, relayTop.minY());
        assertEquals(8, relayTop.maxChebRadius());
        assertEquals(45, relayTop.maxY());

        LevelSlice flat = topSliceOf("sair_energy_circle");
        assertEquals(0, flat.minY(), "纯平地图案不需要抬核心");
        assertEquals(1, flat.maxChebRadius());
    }

    private static LevelSlice topSliceOf(String file) throws IOException {
        Path p = Path.of("src", "main", "resources", "data", "gensokyou", "rituals", file + ".json");
        JsonObject json;
        try (var reader = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
            json = JsonParser.parseReader(reader).getAsJsonObject();
        }
        RitualPattern pattern = RitualPatternLoader.parseForEdit(
                Gensokyou.id(file), json);
        return pattern.levels().stream()
                .max((a, b) -> Integer.compare(a.level(), b.level()))
                .orElseThrow();
    }
}
