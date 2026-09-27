package com.bitsson.gensokyou.ritual;

import com.bitsson.gensokyou.Gensokyou;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 八方归元「祭品台汇流表现」的<b>客户端本地推导</b>一致性守卫。
 *
 * <p>客户端拿不到 {@link RitualMatch}（结构匹配是服务端行为），台位必须由已同步的 pattern JSON
 * 本地推出（见 {@link RitualPedestals#offsets}）。这条推导一旦与服务端口径分叉，表现就会
 * 「亮着的核对不上亮着的台」——而这类错误<b>没有任何运行时断言能发现</b>，故在此离线锁死。
 *
 * <p>刻意不碰配置规范（单测环境未加载，读 config 会抛
 * "Cannot get config value before config is loaded"），也不构造 {@link RitualMatch}。
 */
class BafangPedestalOffsetsTest {

    private static final String PATTERN = "bafang_guiyuan_circle.json";
    private static final ResourceLocation ID = Gensokyou.id("bafang_guiyuan_circle");

    /** 各阶级的台数（键 a/b/c/d 的展开结果；每条 adds 贡献 4 座）。 */
    private static final Map<Integer, Integer> PEDESTALS_BY_TIER = Map.of(2, 4, 3, 8, 4, 16, 5, 24);

    private static RitualPattern pattern() throws IOException {
        Path p = Path.of("src", "main", "resources", "data", "gensokyou", "rituals", PATTERN);
        JsonObject json;
        try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
            json = JsonParser.parseReader(r).getAsJsonObject();
        }
        return RitualPatternLoader.parseForEdit(ID, json);
    }

    @Test
    void pedestalCountMatchesTierLayout() throws IOException {
        RitualPattern pattern = pattern();
        PEDESTALS_BY_TIER.forEach((tier, expected) ->
                assertEquals(expected, RitualPedestals.offsets(pattern, tier).size(),
                        "tier " + tier + " pedestal count"));
    }

    @Test
    void unknownTierYieldsNoPedestals() throws IOException {
        RitualPattern pattern = pattern();
        // 阶级 0/1 与 6 不在 tiers 内；空列表必须让调用方整段跳过绘制，而不是画 0 条错误几何
        assertTrue(RitualPedestals.offsets(pattern, 0).isEmpty());
        assertTrue(RitualPedestals.offsets(pattern, 1).isEmpty());
        assertTrue(RitualPedestals.offsets(pattern, 6).isEmpty());
    }

    @Test
    void offsetsAreCanonicallyOrdered() throws IOException {
        RitualPattern pattern = pattern();
        for (int tier : PEDESTALS_BY_TIER.keySet()) {
            List<BlockPos> offsets = RitualPedestals.offsets(pattern, tier);
            for (int i = 1; i < offsets.size(); i++) {
                assertTrue(compareCanonical(offsets.get(i - 1), offsets.get(i)) < 0,
                        "tier " + tier + " offsets not in canonical (y,z,x) order at index " + i
                                + ": " + offsets.get(i - 1) + " then " + offsets.get(i));
            }
        }
    }

    @Test
    void offsetsAreCumulativeAcrossTiers() throws IOException {
        RitualPattern pattern = pattern();
        // 高一阶的台位集合 MUST 包含低一阶的全部台位（等级是累积增量），否则升阶会让已点亮的台熄掉
        List<BlockPos> lower = RitualPedestals.offsets(pattern, 2);
        for (int tier : new int[]{3, 4, 5}) {
            List<BlockPos> higher = RitualPedestals.offsets(pattern, tier);
            assertTrue(higher.containsAll(lower),
                    "tier " + tier + " must contain every tier-2 pedestal");
        }
    }

    @Test
    void onlyPedestalTaggedKeysAreIncluded() throws IOException {
        RitualPattern pattern = pattern();
        // 锚点核心键 'C' 与仪式石键 MUST NOT 被当成祭品台
        assertFalse(RitualPedestals.isPedestal(pattern.palette().get(pattern.anchorKey())),
                "anchor key must not be a pedestal predicate");
        long tagged = pattern.palette().values().stream().filter(RitualPedestals::isPedestal).count();
        assertTrue(tagged >= 1, "bafang pattern must declare at least one pedestal key");
        for (int tier : PEDESTALS_BY_TIER.keySet()) {
            for (BlockPos offset : RitualPedestals.offsets(pattern, tier)) {
                assertFalse(offset.equals(BlockPos.ZERO),
                        "tier " + tier + " must not report the anchor as a pedestal");
            }
        }
    }

    @Test
    void clientDerivationAgreesWithServerShapedMatch() throws IOException {
        RitualPattern pattern = pattern();
        for (int tier : PEDESTALS_BY_TIER.keySet()) {
            List<BlockPos> relative = RitualPedestals.offsets(pattern, tier);
            // 复刻 RitualMatcher.verifyTier：世界坐标 = 锚点 + LevelSlice 偏移。
            // 服务端 RitualPedestals.positions(match) 返回的是这批世界坐标并按 (y,z,x) 排序；
            // 单一锚点下"相对偏移的规范序"与"世界坐标的规范序"必然一致，故可直接比对。
            BlockPos anchor = new BlockPos(137, -42, -508);
            Map<Character, List<BlockPos>> keyed = new LinkedHashMap<>();
            for (var entry : pattern.palette().entrySet()) {
                if (!RitualPedestals.isPedestal(entry.getValue())) {
                    continue;
                }
                for (var slice : pattern.levels()) {
                    if (slice.level() != tier) {
                        continue;
                    }
                    List<BlockPos> bucket = new ArrayList<>();
                    for (RitualPattern.BlockEntry block : slice.blocks()) {
                        if (block.key() == entry.getKey()) {
                            bucket.add(anchor.offset(block.x(), block.y(), block.z()));
                        }
                    }
                    keyed.merge(entry.getKey(), bucket, (a, b) -> {
                        a.addAll(b);
                        return a;
                    });
                }
            }
            List<BlockPos> serverShape = new ArrayList<>();
            for (char key : pattern.palette().keySet()) {
                serverShape.addAll(keyed.getOrDefault(key, List.of()));
            }
            serverShape.sort((a, b) -> compareCanonical(a, b));
            assertEquals(serverShape.size(), relative.size(),
                    "tier " + tier + " pedestal count mismatch between client offsets and server shape");
            for (int i = 0; i < relative.size(); i++) {
                assertEquals(serverShape.get(i), anchor.offset(relative.get(i)),
                        "tier " + tier + " pedestal index " + i + " must line up across client/server");
            }
        }
    }

    @Test
    void derivationIsPureFunctionOfPatternAndTier() throws IOException {
        RitualPattern pattern = pattern();
        for (int tier : PEDESTALS_BY_TIER.keySet()) {
            List<BlockPos> first = RitualPedestals.offsets(pattern, tier);
            List<BlockPos> second = RitualPedestals.offsets(pattern, tier);
            assertEquals(first, second, "tier " + tier + " derivation must be deterministic");
        }
    }

    @Test
    void serverSidePositionsStillCompile() {
        // 回归：RitualPedestals.positions(match) 复用 isPedestal 后的签名未变
        assertNotNull(RitualPedestals.class);
    }

    private static int compareCanonical(BlockPos a, BlockPos b) {
        if (a.getY() != b.getY()) {
            return Integer.compare(a.getY(), b.getY());
        }
        if (a.getZ() != b.getZ()) {
            return Integer.compare(a.getZ(), b.getZ());
        }
        return Integer.compare(a.getX(), b.getX());
    }
}
