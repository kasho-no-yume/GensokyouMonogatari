package com.bitsson.gensokyou.block.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 递进提示音节点的离线守卫。
 *
 * <p><b>这条守卫来自一次真实故障。</b>旧存档里的已闩锁门没有演出锚点，于是
 * {@code fxElapsed()} 恒为 0；而蓄能长度从缺失的 tag 读成 1，导致
 * {@code (int)(1 × 0.1/0.3/0.5/0.7)} 四个相对节点<b>全部塌成 tick 0</b>，
 * 于是每一 tick 都命中 → 每 tick 叠 4 声紫水晶共鸣、永不停。
 *
 * <p>这类纯算术故障没有任何运行时防护：音效照播、画面照常，日志里一行错都没有。
 */
class SukimaChargeCueNodesTest {

    @Test
    void noNodeEverCollapsesToTickZero() {
        for (int chargeEnd = 1; chargeEnd <= 400; chargeEnd++) {
            for (int node : SukimaBlockEntity.chargeCueNodes(chargeEnd)) {
                assertTrue(node >= 1,
                        "chargeEnd=" + chargeEnd + " 产生了 tick 0 的节点——"
                                + "锚点缺失时 fxElapsed() 恒为 0，两者相等会变成每 tick 叠声");
            }
        }
    }

    @Test
    void nodesAreUniqueAndAscending() {
        for (int chargeEnd = 1; chargeEnd <= 400; chargeEnd++) {
            int[] nodes = SukimaBlockEntity.chargeCueNodes(chargeEnd);
            for (int i = 0; i < nodes.length; i++) {
                if (i > 0) {
                    assertTrue(nodes[i] > nodes[i - 1],
                            "chargeEnd=" + chargeEnd + " 节点未去重/未升序："
                                    + java.util.Arrays.toString(nodes));
                }
            }
        }
    }

    @Test
    void nodesNeverExceedTheChargeLength() {
        for (int chargeEnd = 1; chargeEnd <= 400; chargeEnd++) {
            for (int node : SukimaBlockEntity.chargeCueNodes(chargeEnd)) {
                assertTrue(node <= chargeEnd,
                        "chargeEnd=" + chargeEnd + " 的节点 " + node + " 越过了蓄能段末尾"
                                + "（会落在爆炸之后，听感错位）");
            }
        }
    }

    @Test
    void defaultChargeEndSpreadsNodesAcrossTheWindow() {
        // 默认 8 秒 = 160 tick：节点应大致落在 1/10、3/10、5/10、7/10 处
        int[] nodes = SukimaBlockEntity.chargeCueNodes(160);
        assertEquals(4, nodes.length, "160 tick 下应有 4 个互不相同的递进节点");
        assertEquals(16, nodes[0]);
        assertEquals(48, nodes[1]);
        assertEquals(80, nodes[2]);
        assertEquals(112, nodes[3]);
    }
}
