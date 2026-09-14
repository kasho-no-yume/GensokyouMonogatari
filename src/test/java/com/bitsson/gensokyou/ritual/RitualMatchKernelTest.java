package com.bitsson.gensokyou.ritual;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 最大匹配（max 模式）贪心内核（世界无关）：子集命中余料留台、超集优先、倍数只造一份、
 * 缺口全有全无。对应 spec ritual-recipes "匹配语义（max）"四场景；生产侧
 * {@link RitualRecipeMatcher#allocate} 是真实物品栈到此矩阵的适配层。
 */
class RitualMatchKernelTest {

    /** 台面：4a、3b、5c、1d（各一独立台位，单件不变量下 count 即件数）。 */
    private static final int[] TABLE_4A3B5C1D = {4, 3, 5, 1};

    /** accepts[needIdx][poolIdx]：本例中 a→台0、b→台1、c→台2、d→台3。 */
    private static boolean[] only(int idx, int size) {
        boolean[] row = new boolean[size];
        row[idx] = true;
        return row;
    }

    /** max 子集命中：3a3b 在 4a3b5c1d 上命中，仅吃 3a3b，其余留台。 */
    @Test
    void maxSubsetHitsAndLeavesExtra() {
        int[] taken = RitualMatchKernel.allocate(
                new int[]{3, 3},
                TABLE_4A3B5C1D,
                new boolean[][]{only(0, 4), only(1, 4)});
        assertArrayEquals(new int[]{3, 3, 0, 0}, taken);
        // 台面确有剩余（1a5c1d）——max 语义 = 有剩余也命中；hasLeftover 仅被 EXACT 消费为判负
        assertTrue(RitualMatchKernel.hasLeftover(TABLE_4A3B5C1D, taken));
    }

    /** 超集优先：A=3a3b、B=3a3b5c 同在 4a3b5c1d 命中，取消耗总量最大者（B=11）。 */
    @Test
    void supersetWinsByLargestConsumption() {
        int[] smallTaken = RitualMatchKernel.allocate(
                new int[]{3, 3},
                TABLE_4A3B5C1D,
                new boolean[][]{only(0, 4), only(1, 4)});
        int[] bigTaken = RitualMatchKernel.allocate(
                new int[]{3, 3, 5},
                TABLE_4A3B5C1D,
                new boolean[][]{only(0, 4), only(1, 4), only(2, 4)});
        int smallTotal = RitualMatchKernel.total(smallTaken);
        int bigTotal = RitualMatchKernel.total(bigTaken);
        assertEquals(6, smallTotal);
        assertEquals(11, bigTotal);
        assertTrue(bigTotal > smallTotal, "max 选择应取 B（超集，消耗更大）");
    }

    /** 倍数只造一份：6a6b 台面对 3a3b 配方，一次执行仅消耗 3a3b。 */
    @Test
    void multipleConsumesOnlyOneSet() {
        int[] taken = RitualMatchKernel.allocate(
                new int[]{3, 3},
                new int[]{6, 6},
                new boolean[][]{only(0, 2), only(1, 2)});
        assertArrayEquals(new int[]{3, 3}, taken, "6a6b 一次只造一个，消耗 3a3b");
        assertEquals(6, RitualMatchKernel.total(taken));
    }

    /** 缺口即全有全无失败（不产生部分消耗账）。 */
    @Test
    void shortIngredientAllocatesNothing() {
        int[] taken = RitualMatchKernel.allocate(
                new int[]{3, 3},
                new int[]{3, 2},
                new boolean[][]{only(0, 2), only(1, 2)});
        assertNull(taken, "b 仅 2 件，缺 1 → 整体不匹配");
    }

    /** EXACT 严格等值：台面 4a3b 对 3a3b 因多余 1a 判负（max 不受此限）。 */
    @Test
    void exactRejectsExtrasViaLeftover() {
        int[] taken = RitualMatchKernel.allocate(
                new int[]{3, 3},
                new int[]{4, 3},
                new boolean[][]{only(0, 2), only(1, 2)});
        assertTrue(RitualMatchKernel.hasLeftover(new int[]{4, 3}, taken),
                "EXACT 下剩余 1a 应触发判负");
    }

    /** 标签跨多物品分配：一条目可吃多个匹配台（同 identity 跨台累加）。 */
    @Test
    void oneIngredientSpansMultiplePools() {
        // 需求 5 个 "a 类"，台面为 2a+2a+3a（三台均可供），应吃 2+2+1
        int[] taken = RitualMatchKernel.allocate(
                new int[]{5},
                new int[]{2, 2, 3},
                new boolean[][]{new boolean[]{true, true, true}});
        assertArrayEquals(new int[]{2, 2, 1}, taken);
        assertEquals(5, RitualMatchKernel.total(taken));
    }
}
