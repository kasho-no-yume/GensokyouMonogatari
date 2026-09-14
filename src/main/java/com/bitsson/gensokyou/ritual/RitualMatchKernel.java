package com.bitsson.gensokyou.ritual;

import javax.annotation.Nullable;

/**
 * 无序匹配贪心内核（世界无关纯算法）：需求条目 × 台面池的消耗分配。
 * 条目序需带「精确物品优先于标签」语义（由调用方排好）；
 * {@link RitualRecipeMatcher} 是其在真实物品栈上的适配层，本类可直接单测。
 */
public final class RitualMatchKernel {

    private RitualMatchKernel() {
    }

    /**
     * 贪心分配：required[i] 为第 i 条需求数，poolCounts[j] 为第 j 台持有数，
     * accepts[i][j] 为条目 i 可否消耗台 j。逐条需求顺序吃台（同一条目内台序即参数序）。
     *
     * @return 每台消耗量（与 poolCounts 等长）；任一条目缺口返回 null（全有全无，不落部分账）。
     */
    public static @Nullable int[] allocate(int[] required, int[] poolCounts, boolean[][] accepts) {
        int[] taken = new int[poolCounts.length];
        for (int i = 0; i < required.length; i++) {
            int remaining = required[i];
            for (int j = 0; j < poolCounts.length && remaining > 0; j++) {
                int available = poolCounts[j] - taken[j];
                if (available <= 0 || !accepts[i][j]) {
                    continue;
                }
                int take = Math.min(remaining, available);
                remaining -= take;
                taken[j] += take;
            }
            if (remaining > 0) {
                return null;
            }
        }
        return taken;
    }

    /** 消耗总量（Σ taken）。 */
    public static int total(int[] taken) {
        int sum = 0;
        for (int v : taken) {
            sum += v;
        }
        return sum;
    }

    /** 是否存在未被消耗的多余物品（EXACT 严格等值判据）。 */
    public static boolean hasLeftover(int[] poolCounts, int[] taken) {
        for (int j = 0; j < poolCounts.length; j++) {
            if (poolCounts[j] - taken[j] > 0) {
                return true;
            }
        }
        return false;
    }
}
