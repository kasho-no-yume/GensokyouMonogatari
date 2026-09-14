package com.bitsson.gensokyou.ritual;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 配方加载器纯决策（无世界/无注册表）：minTier 下限 0、match 解析缺省 exact、
 * passive+max 拒载、签名真互含判定（对应"歧义校验期拒绝与互含警告"与"minTier 门槛"要求）。
 */
class RitualRecipeLoaderLogicTest {

    @Test
    void minTierFloorsAtZero() {
        assertEquals(0, RitualRecipeLoader.clampMinTier(0));
        assertEquals(0, RitualRecipeLoader.clampMinTier(-5));
        assertEquals(3, RitualRecipeLoader.clampMinTier(3));
    }

    @Test
    void matchDefaultsAndRejectsUnknown() {
        assertEquals(RitualRecipe.MatchMode.EXACT, RitualRecipeLoader.parseMatch("exact"));
        assertEquals(RitualRecipe.MatchMode.MAX, RitualRecipeLoader.parseMatch("max"));
        assertThrows(IllegalArgumentException.class, () -> RitualRecipeLoader.parseMatch("loose"));
    }

    @Test
    void passiveMaxRejectedPassiveExactOk() {
        assertThrows(IllegalArgumentException.class, () -> RitualRecipeLoader.validateModeMatch(
                RitualRecipe.Mode.PASSIVE, RitualRecipe.MatchMode.MAX, true));
        // passive + exact + 有 result 合法
        RitualRecipeLoader.validateModeMatch(
                RitualRecipe.Mode.PASSIVE, RitualRecipe.MatchMode.EXACT, true);
        // passive 缺 result 仍拒
        assertThrows(IllegalArgumentException.class, () -> RitualRecipeLoader.validateModeMatch(
                RitualRecipe.Mode.PASSIVE, RitualRecipe.MatchMode.EXACT, false));
        // activation + max 合法（结果可空由 effect 承担，此处 hasResult=true）
        RitualRecipeLoader.validateModeMatch(
                RitualRecipe.Mode.ACTIVATION, RitualRecipe.MatchMode.MAX, true);
    }

    @Test
    void properSupersetDetectsContainment() {
        Map<String, Integer> a = Map.of("diamond", 3, "stone", 3);
        Map<String, Integer> b = Map.of("diamond", 3, "stone", 3, "shard", 5);
        assertFalse(RitualRecipeLoader.isProperSuperset(a, b), "a 不含 b 的 shard");
        assertTrue(RitualRecipeLoader.isProperSuperset(b, a), "b ⊇ a");
        assertFalse(RitualRecipeLoader.isProperSuperset(a, a), "相等非真包含");
    }

    @Test
    void properSupersetRespectsPerIdentityCount() {
        Map<String, Integer> big = Map.of("a", 4, "b", 3);
        Map<String, Integer> small = Map.of("a", 3, "b", 3);
        assertTrue(RitualRecipeLoader.isProperSuperset(big, small));
        assertFalse(RitualRecipeLoader.isProperSuperset(small, big));
    }
}
