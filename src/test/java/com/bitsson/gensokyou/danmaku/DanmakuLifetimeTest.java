package com.bitsson.gensokyou.danmaku;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 弹幕绝对寿命（存在性）的纯函数。
 *
 * <p>守的是 {@code fix-danmaku-laser-render-and-frozen-expiry} 的核心不变量：
 * 存在性由<b>服务端游戏时间</b>而非 tickCount 裁决，故冻结时间照常计入寿命。
 * 全部断言都不需要世界——{@link DanmakuLifetime} 是抽出来的纯静态入口，与实体读同一实现。
 */
class DanmakuLifetimeTest {

    @Test
    @DisplayName("缺键回退：出生时间 = 当前时间 − 已恢复年龄")
    void fallbackBirthKeepsRemainingLifetime() {
        // 一枚存活 200 tick 的弹在第 150 tick 处读档：回退出生点 = now − 150，
        // 于是它还剩 50 tick，而不是白得完整 200 tick，也不会立即判死。
        long now = 10_000L;
        long birth = DanmakuLifetime.fallbackBirth(now, 150);
        assertEquals(now - 150, birth, "回退出生点必须扣掉已恢复年龄");
        assertEquals(50, birth + 200 - now, "读档后剩余寿命必须是 50 tick");
    }

    @Test
    @DisplayName("缺键回退：负年龄按 0 处理")
    void fallbackBirthClampsNegativeAge() {
        assertEquals(1_000L, DanmakuLifetime.fallbackBirth(1_000L, -5),
                "负年龄 MUST 按 0 处理，不得把出生点推到未来");
    }

    @Test
    @DisplayName("冻结时间计入寿命：冻结 K tick 后剩余 L−K")
    void frozenTimeCountsTowardLifetime() {
        int lifetime = 1_200;
        long birth = 1_000L;

        // 冻结了 500 tick：尚未到期
        assertFalse(DanmakuLifetime.overdue(birth + 500, birth, lifetime),
                "冻结 500 tick（<1200）时 MUST NOT 判死");
        // 冻结了 1300 tick：已到期
        assertTrue(DanmakuLifetime.overdue(birth + 1_300, birth, lifetime),
                "冻结 1300 tick（>1200）时 MUST 判死");
    }

    @Test
    @DisplayName("边界：恰好等于寿命时不判死，超过一 tick 才判死")
    void boundaryIsStrictlyGreater() {
        int lifetime = 100;
        long birth = 0L;
        assertFalse(DanmakuLifetime.overdue(100L, birth, lifetime),
                "恰好到寿命终点 MUST NOT 判死（严格大于）");
        assertTrue(DanmakuLifetime.overdue(101L, birth, lifetime),
                "越过寿命终点一 tick MUST 判死");
    }

    @Test
    @DisplayName("未初始化的出生时间视为未过期")
    void unsetBirthIsNeverOverdue() {
        assertFalse(DanmakuLifetime.overdue(1_000_000L, DanmakuLifetime.UNSET_BIRTH, 1),
                "出生时间未设置时 MUST NOT 判死（等待惰性初始化）");
    }

    @Test
    @DisplayName("服务器关停期间不计：游戏时间不推进则剩余寿命不变")
    void downtimeDoesNotConsumeLifetime() {
        // 游戏时间在服务器关停期间不推进，故「存档时 now」与「重载时 now」相同。
        int lifetime = 200;
        long birth = 1_000L;
        long savedAt = 1_150L;   // 存档时已过 150
        long reloadedAt = 1_150L; // 关停期间游戏时间不动

        assertFalse(DanmakuLifetime.overdue(reloadedAt, birth, lifetime),
                "关停期间 MUST NOT 计入寿命");
        assertEquals(50, birth + lifetime - reloadedAt,
                "重载后剩余寿命 MUST 与存档时一致（50 tick）");
        assertEquals(50, birth + lifetime - savedAt, "存档时刻的剩余寿命同样是 50");
    }
}
