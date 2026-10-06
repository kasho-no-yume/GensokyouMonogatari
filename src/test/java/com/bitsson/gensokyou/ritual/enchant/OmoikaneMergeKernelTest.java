package com.bitsson.gensokyou.ritual.enchant;

import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 思兼神封合并/随机内核回归（纯静态函数，不加载 ModConfig、不构造世界）。
 */
class OmoikaneMergeKernelTest {

    // ---- 升序折叠 ----

    @Test
    void foldMergesEqualLevelsUpward() {
        // 需求原文例子：保护1×2 + 保护2 + 保护3 → 保护4
        assertEquals(4, OmoikaneMerge.foldLevels(List.of(1, 1, 2, 3), 4));
    }

    @Test
    void foldIncludesGearOwnLevelInPool() {
        // 甲自带保护1 + 书保护1 + 书保护2 → [1,1,2] → 保护3
        assertEquals(3, OmoikaneMerge.foldLevels(List.of(1, 1, 2), 4));
    }

    @Test
    void foldFallsBackToHighestWhenNoPair() {
        // 书保护1 + 书保护3 → 取高
        assertEquals(3, OmoikaneMerge.foldLevels(List.of(1, 3), 4));
        // 乱序输入必须等价（排序是函数内部职责）
        assertEquals(3, OmoikaneMerge.foldLevels(List.of(3, 1), 4));
        assertEquals(4, OmoikaneMerge.foldLevels(List.of(3, 1, 1, 2), 4));
    }

    @Test
    void foldCapsAtMaxLevel() {
        // 保护4 + 保护4 → 5 截断到 4
        assertEquals(4, OmoikaneMerge.foldLevels(List.of(4, 4), 4));
        // 无限（maxLevel=1）永不升级
        assertEquals(1, OmoikaneMerge.foldLevels(List.of(1, 1), 1));
    }

    @Test
    void foldSingleEntryUnchanged() {
        assertEquals(2, OmoikaneMerge.foldLevels(List.of(2), 4));
    }

    @Test
    void overrideIsAlwaysMaxPlusOne() {
        assertEquals(5, OmoikaneMerge.overrideLevel(4));
        assertEquals(2, OmoikaneMerge.overrideLevel(1));
    }

    // ---- 随机池 ----

    @Test
    void drawIsWithoutReplacementAndCapped() {
        RandomSource random = RandomSource.create(42L);
        List<Integer> pool = List.of(1, 2, 3, 4, 5, 6);
        List<Integer> drawn = OmoikaneRandomPool.draw(pool, 4, random);
        assertEquals(4, drawn.size());
        assertEquals(4, drawn.stream().distinct().count(), "不放回抽取天然无重复");
        assertTrue(pool.containsAll(drawn));
        // count 超池大小时钳到池大小
        assertEquals(6, OmoikaneRandomPool.draw(pool, 99, random).size());
        assertEquals(0, OmoikaneRandomPool.draw(pool, 0, random).size());
    }

    @Test
    void tierFiltersMatchDesign() {
        assertTrue(OmoikaneRandomPool.tierAllowsCurse(1));
        assertTrue(!OmoikaneRandomPool.tierAllowsTreasure(1));
        assertTrue(!OmoikaneRandomPool.tierAllowsCurse(2));
        assertTrue(!OmoikaneRandomPool.tierAllowsTreasure(2));
        assertTrue(!OmoikaneRandomPool.tierAllowsCurse(3));
        assertTrue(OmoikaneRandomPool.tierAllowsTreasure(3));
    }

    @Test
    void rollLevelPerTier() {
        RandomSource random = RandomSource.create(7L);
        for (int i = 0; i < 64; i++) {
            assertEquals(1, OmoikaneRandomPool.rollLevel(1, 4, random), "1 阶全 1 级");
            assertEquals(5, OmoikaneRandomPool.rollLevel(3, 4, random), "3 阶全 maxLevel+1");
            int r = OmoikaneRandomPool.rollLevel(2, 4, random);
            assertTrue(r >= 1 && r <= 4, "2 阶在 1~maxLevel 间");
        }
        // maxLevel=1 的词条 2 阶恒为 1
        assertEquals(1, OmoikaneRandomPool.rollLevel(2, 1, random));
    }
}
