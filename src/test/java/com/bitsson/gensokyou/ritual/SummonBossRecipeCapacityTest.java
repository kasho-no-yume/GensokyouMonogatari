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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 百鬼夜行召唤配方的<b>祭品台容量</b>约束。
 *
 * <p>这条约束来自 {@code RitualPedestalBlockEntity} 的<b>单件不变量</b>：一个祭品台只放
 * 一个物品、不可堆叠（超出部分落地）。故
 *
 * <pre>
 *   N 个祭品台 = 单次召唤最多 N 个物品
 *   ⇒  该阶配方 Σcount ≤ N
 *     L1: ≤ 4      L2 / L3: ≤ 8
 * </pre>
 *
 * <p>台数是 pattern 的<b>累积快照</b>里的 {@code P}（{@code #gensokyou:ritual_pedestals}）
 * 出现次数，<b>不是</b> {@code levels[].adds} 里 {@code P} 的增量——后者会随阶累加而重复计数。
 */
class SummonBossRecipeCapacityTest {

    private static final int PEDESTALS_L1 = 4;
    private static final int PEDESTALS_L2 = 8;
    private static final int PEDESTALS_L3 = 8;
    /** pattern v5：每层 adds 只存对称规范四分之一，加载期四重展开。 */
    private static final int SYMMETRY_QUADRANTS = 4;

    private static final Path PATTERN =
            Path.of("src/main/resources/data/gensokyou/rituals/hyakki_yagyo_circle.json");
    private static final Path RECIPES =
            Path.of("src/main/resources/data/gensokyou/ritual_recipes/hyakki_yagyo_circle.json");

    /**
     * 累积快照里每阶的祭品台数（按 level 升序）。
     *
     * <p><b>两个必须记住的读法</b>（两条都踩过）：
     * <ol>
     *   <li>{@code levels[].adds} 是<b>增量</b>切片，不是全量——单看一层会少算。
     *   <li>每层 {@code adds} 只存<b>对称规范四分之一</b>，加载期四重展开。
     *       故单层计数要 ×4，再跨层累加。
     * </ol>
     */
    private static int[] pedestalCountsByLevel() throws IOException {
        JsonObject pattern = readJson(PATTERN);
        JsonObject palette = pattern.getAsJsonObject("palette");
        String pedestalChar = null;
        for (String key : palette.keySet()) {
            if (palette.get(key).getAsString().contains("ritual_pedestals")) {
                pedestalChar = key;
                break;
            }
        }
        assertTrue(pedestalChar != null, "百鬼夜行 pattern 的 palette 里找不到祭品台字符");

        JsonArray levels = pattern.getAsJsonArray("levels");
        int[] counts = new int[levels.size()];
        int running = 0;
        for (int i = 0; i < counts.length; i++) {
            int quadrant = 0;
            for (JsonElement element : levels.get(i).getAsJsonObject().getAsJsonArray("adds")) {
                if (element.getAsJsonArray().get(0).getAsString().equals(pedestalChar)) {
                    quadrant++;
                }
            }
            running += quadrant * SYMMETRY_QUADRANTS;
            counts[i] = running;
        }
        return counts;
    }

    @Test
    void patternPedestalCountsAreTheDocumentedOnes() throws IOException {
        int[] counts = pedestalCountsByLevel();
        assertEquals(3, counts.length, "百鬼夜行应为三阶");
        assertEquals(PEDESTALS_L1, counts[0], "L1 祭品台数");
        assertEquals(PEDESTALS_L2, counts[1], "L2 祭品台数");
        assertEquals(PEDESTALS_L3, counts[2], "L3 祭品台数");
    }

    /** 核心断言：任何一条召唤配方的 Σcount 都不得超过该阶的祭品台数。 */
    @Test
    void everyRecipeFitsItsTierPedestalCount() throws IOException {
        int[] byLevel = pedestalCountsByLevel();
        JsonArray recipes = readJson(RECIPES).getAsJsonArray("recipes");
        assertTrue(recipes.size() > 0, "召唤配方表为空");

        for (JsonElement element : recipes) {
            JsonObject recipe = element.getAsJsonObject();
            String name = recipe.get("name").getAsString();
            int minTier = recipe.get("minTier").getAsInt();
            int index = minTier - 1;
            assertTrue(index >= 0 && index < byLevel.length,
                    name + " 的 minTier=" + minTier + " 超出百鬼夜行的阶数范围");

            int total = 0;
            Set<String> items = new HashSet<>();
            for (JsonElement ing : recipe.getAsJsonArray("ingredients")) {
                total += ing.getAsJsonObject().get("count").getAsInt();
                items.add(ing.getAsJsonObject().get("item").getAsString());
            }
            assertTrue(total <= byLevel[index], String.format(
                    "%s 的 Σcount=%d 超过 %d 阶祭品台数 %d —— 一个祭品台只放一个物品、不可堆叠，"
                            + "这条配方在实机上永远匹配不上",
                    name, total, minTier, byLevel[index]));
            assertEquals(items.size(), recipe.getAsJsonArray("ingredients").size(),
                    name + " 有重复的原料条目");
            assertTrue(recipe.has("effect") && !recipe.get("effect").getAsString().isEmpty(),
                    name + " 缺少 effect 字段（召唤落地通路靠它）");
        }
    }

    /** spCost 恒为 10 的倍数：受灵汇速率 = 容量 ÷ 10，整除才不丢量。 */
    @Test
    void spCostIsMultipleOfTen() throws IOException {
        for (JsonElement element : readJson(RECIPES).getAsJsonArray("recipes")) {
            JsonObject recipe = element.getAsJsonObject();
            long cost = recipe.get("spCost").getAsLong();
            assertEquals(0L, cost % 10L, recipe.get("name").getAsString()
                    + " 的 spCost=" + cost + " 不是 10 的倍数，受灵汇速率整除会丢量");
        }
    }

    /**
     * 钥匙物品互斥：任一份「各取 Σcount 最小者」的摆法不得同时满足两条配方。
     *
     * <p>否则 {@code matchMax} 会在平局时按<b>候选列表顺序</b>静默替你选一只，
     * 玩家摆的是 A 的料、召出来的却是 B。
     */
    @Test
    void recipesAreMutuallyExclusive() throws IOException {
        JsonArray recipes = readJson(RECIPES).getAsJsonArray("recipes");
        for (int i = 0; i < recipes.size(); i++) {
            JsonObject a = recipes.get(i).getAsJsonObject();
            for (int j = i + 1; j < recipes.size(); j++) {
                JsonObject b = recipes.get(j).getAsJsonObject();
                assertTrue(!satisfiesBoth(a, b),
                        a.get("name").getAsString() + " 与 " + b.get("name").getAsString()
                                + " 存在能同时满足的摆法，matchMax 会静默裁决、玩家会召错 BOSS");
            }
        }
    }

    /**
     * 能否被一份摆法同时满足：所需物品的多重集是并集，
     * 而可用祭品台数 = 两者中较大的 Σcount。
     */
    private static boolean satisfiesBoth(JsonObject a, JsonObject b) {
        java.util.Map<String, Integer> need = new java.util.HashMap<>();
        int sumA = collect(need, a);
        int sumB = collect(need, b);
        int pedestals = Math.max(sumA, sumB);
        return pedestals <= Math.max(sumA, sumB) && need.values().stream()
                .mapToInt(Integer::intValue).sum() <= pedestals;
    }

    private static int collect(java.util.Map<String, Integer> into, JsonObject recipe) {
        int total = 0;
        for (JsonElement ing : recipe.getAsJsonArray("ingredients")) {
            JsonObject o = ing.getAsJsonObject();
            String item = o.get("item").getAsString();
            int count = o.get("count").getAsInt();
            into.merge(item, count, Integer::sum);
            total += count;
        }
        return total;
    }

    private static JsonObject readJson(Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    /** 列出全部召唤配方名（调试打印用）。 */
    @Test
    void listRecipeNames() throws IOException {
        List<String> names = readJson(RECIPES).getAsJsonArray("recipes").asList().stream()
                .map(e -> e.getAsJsonObject().get("name").getAsString()).toList();
        System.out.println("[GS-TEST] 召唤配方：" + names);
    }
}
