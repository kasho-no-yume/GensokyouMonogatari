package com.bitsson.gensokyou.audio.bossbgm;

import com.bitsson.gensokyou.Gensokyou;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 选曲的纯函数断言：最近优先、目标优先、sticky、滞回。
 *
 * <p>这些规则全部是为了「不要切歌切得很烦」：同阶 BOSS 交叉走位、双 BOSS 异阶顶替，
 * 都必须落到确定且稳定的结果上。
 */
class BossBgmSelectorTest {

    private static final ResourceLocation T1 = Gensokyou.id("bgm/boss/big_fairy");
    private static final ResourceLocation T1_OTHER = Gensokyou.id("bgm/boss/rumia");
    private static final ResourceLocation T2 = Gensokyou.id("bgm/boss/yakumo_ran");

    private static final UUID A = new UUID(0L, 1L);
    private static final UUID B = new UUID(0L, 2L);
    private static final UUID C = new UUID(0L, 3L);

    @Test
    void emptyFieldMeansNoMusic() {
        assertEquals(Optional.empty(), BossBgmSelector.select(List.of(), null));
        assertEquals(Optional.empty(), BossBgmSelector.select(null, null));
    }

    @Test
    void singleBossIsSelected() {
        var chosen = BossBgmSelector.select(List.of(candidate(A, T1, 100.0D, false)), null);
        assertTrue(chosen.isPresent());
        assertEquals(T1, chosen.get().eventId());
        assertEquals(A, chosen.get().provider());
    }

    @Test
    void nearestWinsWhenNothingIsPlaying() {
        var chosen = BossBgmSelector.select(List.of(
                candidate(A, T1, 400.0D, false),
                candidate(B, T2, 100.0D, false)), null);
        assertEquals(B, chosen.orElseThrow().provider());
        assertEquals(T2, chosen.orElseThrow().eventId());
    }

    @Test
    void targetingMeBeatsBeingCloser() {
        var chosen = BossBgmSelector.select(List.of(
                candidate(A, T1, 50.0D, false),
                candidate(B, T2, 400.0D, true)), null);
        assertEquals(B, chosen.orElseThrow().provider(),
                "正在打我的那只优先于只是站得更近的那只");
    }

    @Test
    void sameEventIdNeverRestarts() {
        // 两只同阶 BOSS：事件 id 相同（都指向同一条阶事件时是这样；这里显式同 id）。
        var current = new BossBgmSelector.Selection(A, 100.0D, T1);
        var chosen = BossBgmSelector.select(List.of(
                candidate(A, T1, 300.0D, false),
                candidate(B, T1, 20.0D, false)), current);
        assertEquals(T1, chosen.orElseThrow().eventId());
    }

    @Test
    void crossfadesOnlyOnSignificantImprovement() {
        // 当前 30 格；另一只 25 格 —— 只近 17%，不够换（否则会来回切歌）。
        var current = new BossBgmSelector.Selection(A, 900.0D, T1);
        var stable = BossBgmSelector.select(List.of(
                candidate(A, T1, 900.0D, false),
                candidate(B, T2, 625.0D, false)), current);
        assertEquals(A, stable.orElseThrow().provider(), "未达 25% 不应换曲");

        // 20 格 —— 近 33%，够换。
        var switched = BossBgmSelector.select(List.of(
                candidate(A, T1, 900.0D, false),
                candidate(B, T2, 400.0D, false)), current);
        assertEquals(B, switched.orElseThrow().provider());
        assertEquals(T2, switched.orElseThrow().eventId());
    }

    @Test
    void vanishedProviderIsReplacedImmediately() {
        var current = new BossBgmSelector.Selection(A, 100.0D, T1);
        var chosen = BossBgmSelector.select(List.of(
                candidate(B, T2, 800.0D, false)), current);
        assertEquals(B, chosen.orElseThrow().provider(),
                "提供者不在了就必须换，哪怕新的更远");
    }

    @Test
    void tiesAreResolvedDeterministically() {
        var first = BossBgmSelector.select(List.of(
                candidate(B, T1, 100.0D, false),
                candidate(A, T1_OTHER, 100.0D, false)), null);
        var second = BossBgmSelector.select(List.of(
                candidate(A, T1_OTHER, 100.0D, false),
                candidate(B, T1, 100.0D, false)), null);
        assertEquals(first.orElseThrow().provider(), second.orElseThrow().provider(),
                "输入顺序不得影响结果");
        assertEquals(A, first.orElseThrow().provider(), "同分按 UUID 字典序");
    }

    @Test
    void switchingThresholdIsAQuarter() {
        assertEquals(0.75D, BossBgmSelector.SWITCH_IMPROVEMENT);
    }

    private static BossBgmSelector.Candidate candidate(UUID id, ResourceLocation eventId,
                                                       double distanceSqr, boolean targetingMe) {
        return new BossBgmSelector.Candidate(id, 1, distanceSqr, targetingMe, eventId);
    }

    @Test
    void stabilityAcrossManyTicks() {
        // 模拟 120 次扫描：A 固定在 30 格，B 在 24..36 格之间摆动。
        // 阈值是「距离近 25%」，等价于平方距离近 43.75%，所以 24/30 也够不着。
        BossBgmSelector.Selection current = null;
        int switches = 0;
        ResourceLocation last = null;
        for (int tick = 0; tick < 120; tick++) {
            double bDistance = 30.0D + 6.0D * Math.sin(tick / 3.0D);
            var chosen = BossBgmSelector.select(List.of(
                    candidate(A, T1, 900.0D, false),
                    candidate(B, T2, bDistance * bDistance, false)), current);
            current = chosen.orElse(null);
            if (last != null && current != null && !last.equals(current.eventId())) {
                switches++;
            }
            last = current == null ? null : current.eventId();
        }
        assertTrue(switches <= 1, "摆动 120 次最多允许换 1 次，实际 " + switches);
    }
}
