package com.bitsson.gensokyou.ritual;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 仪式配方的<b>祭品台容量</b>硬约束（全量，离线，不依赖加载器）。
 *
 * <p>来自 {@code RitualPedestalBlockEntity} 的单件不变量：一个祭品台只放一个物品、不可堆叠。
 * 故配方归一化原料总量 {@code Σcount} 不得超过其 {@code minTier} 生效结构阶级的祭品台数——
 * 超量配方在 {@code RitualRecipeMatcher.allocate} 处分配失败，实机<b>永久静默失配</b>。
 *
 * <p>口径：
 * <ul>
 *   <li>台数 = pattern 对应阶级<b>累积快照</b>里 {@code #gensokyou:ritual_pedestals} 格数
 *       （不是 {@code adds} 增量，否则跨阶重复计数）。</li>
 *   <li>{@code adds} 只存对称规范四分之一，需按 v5 规则四重展开（轴位/离轴位不同）。</li>
 *   <li>配方生效的最低结构 = 声明 level 中 {@code >= minTier} 的最小者。</li>
 * </ul>
 *
 * <p>八百万神恩额外要求逐阶<b>恰好填满</b>（{@code ==}），其余仪式为 {@code <=}。
 */
class RitualRecipePedestalCapacityTest {

    private static final Path DATA = Path.of("src", "main", "resources", "data", "gensokyou");
    private static final Path RITUALS = DATA.resolve("rituals");
    private static final Path RECIPES = DATA.resolve("ritual_recipes");
    private static final String PEDESTAL_TAG = "#gensokyou:ritual_pedestals";
    private static final String EXACT_FILL_PATTERN = "gensokyou:kami_no_megumi_circle";

    @Test
    void everyRecipeFitsItsStructurePedestalCapacity() throws IOException {
        Map<String, JsonObject> patterns = new LinkedHashMap<>();
        try (Stream<Path> files = Files.list(RITUALS)) {
            for (Path path : files.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                JsonObject raw = readJson(path);
                if (raw.has("id")) {
                    patterns.put(raw.get("id").getAsString(), raw);
                }
            }
        }
        assertTrue(!patterns.isEmpty(), "未加载到任何 pattern");

        try (Stream<Path> files = Files.list(RECIPES)) {
            for (Path path : files.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                JsonObject root = readJson(path);
                String patternId = root.get("pattern").getAsString();
                JsonObject pattern = patterns.get(patternId);
                assertTrue(pattern != null, path.getFileName() + " 引用未知 pattern " + patternId);
                List<int[]> caps = pedestalCountsByLevel(pattern);
                for (JsonElement element : root.getAsJsonArray("recipes")) {
                    JsonObject recipe = element.getAsJsonObject();
                    String name = recipe.get("name").getAsString();
                    int minTier = recipe.has("minTier") ? recipe.get("minTier").getAsInt() : 1;
                    int total = 0;
                    for (JsonElement ing : recipe.getAsJsonArray("ingredients")) {
                        total += ing.getAsJsonObject().get("count").getAsInt();
                    }
                    Integer cap = capacityAt(caps, minTier);
                    assertTrue(cap != null, String.format(
                            "%s:%s minTier=%d 超过该 pattern 最高阶，配方永不可用",
                            patternId, name, minTier));
                    assertTrue(total <= cap, String.format(
                            "%s:%s 原料总量 Σcount=%d 超过 minTier %d 的祭品台数 %d —— "
                                    + "祭品台一台一件，该配方在实机上永远匹配不上",
                            patternId, name, total, minTier, cap));
                    if (EXACT_FILL_PATTERN.equals(patternId)) {
                        assertEquals(cap.intValue(), total, String.format(
                                "%s:%s 八百万神恩要求逐阶恰好填满：Σcount=%d 应等于台位数 %d",
                                patternId, name, total, cap));
                    }
                }
            }
        }
    }

    /** 该 pattern 各声明 level 的累积祭品台数（level 升序）：{levelNo, count}。 */
    private static List<int[]> pedestalCountsByLevel(JsonObject pattern) {
        JsonObject palette = pattern.getAsJsonObject("palette");
        Set<String> pedestalKeys = new HashSet<>();
        for (String key : palette.keySet()) {
            if (PEDESTAL_TAG.equals(palette.get(key).getAsString())) {
                pedestalKeys.add(key);
            }
        }
        List<JsonObject> levels = new ArrayList<>();
        for (JsonElement element : pattern.getAsJsonArray("levels")) {
            levels.add(element.getAsJsonObject());
        }
        levels.sort(Comparator.comparingInt(l -> l.get("level").getAsInt()));

        Set<String> seen = new HashSet<>();
        List<int[]> caps = new ArrayList<>();
        int running = 0;
        for (JsonObject level : levels) {
            for (JsonElement entry : level.getAsJsonArray("adds")) {
                JsonArray array = entry.getAsJsonArray();
                String key = array.get(0).getAsString();
                int x = array.get(1).getAsInt();
                int y = array.get(2).getAsInt();
                int z = array.get(3).getAsInt();
                for (int[] pos : expand(x, y, z)) {
                    if (seen.add(pos[0] + "," + pos[1] + "," + pos[2])
                            && pedestalKeys.contains(key)) {
                        running++;
                    }
                }
            }
            caps.add(new int[]{level.get("level").getAsInt(), running});
        }
        return caps;
    }

    /** 最小声明 level >= minTier 的累积台数；无此 level 返回 null。 */
    private static Integer capacityAt(List<int[]> caps, int minTier) {
        for (int[] cap : caps) {
            if (cap[0] >= minTier) {
                return cap[1];
            }
        }
        return null;
    }

    /** v5 四重对称展开（与 RitualPatternLoader / validate_ritual_pattern.expand_entry 同规则）。 */
    private static List<int[]> expand(int x, int y, int z) {
        List<int[]> out = new ArrayList<>(4);
        if (x == 0 && z == 0) {
            out.add(new int[]{0, y, 0});
            return out;
        }
        if (x == 0) {
            int d = z;
            out.add(new int[]{0, y, d});
            out.add(new int[]{0, y, -d});
            out.add(new int[]{d, y, 0});
            out.add(new int[]{-d, y, 0});
            return out;
        }
        if (z == 0) {
            int d = x;
            out.add(new int[]{d, y, 0});
            out.add(new int[]{-d, y, 0});
            out.add(new int[]{0, y, d});
            out.add(new int[]{0, y, -d});
            return out;
        }
        for (int sx : new int[]{x, -x}) {
            for (int sz : new int[]{z, -z}) {
                out.add(new int[]{sx, y, sz});
            }
        }
        return out;
    }

    private static JsonObject readJson(Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
}
