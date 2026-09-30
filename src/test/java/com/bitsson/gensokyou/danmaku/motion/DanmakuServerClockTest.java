package com.bitsson.gensokyou.danmaku.motion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 客户端服务器时间轴估计器（{@code danmaku-timeline-sync} 任务 1.1~1.5）。
 *
 * <p>这些断言守的是三条互相独立的承诺，任何一条退化了都不会报错，只会变成一个
 * 「看起来在纠偏、实际在制造偏差」的症状：
 * <ol>
 *   <li><b>斜率</b>是本类存在的理由：锚点只能平移映射，补偿不了两端速率差；</li>
 *   <li><b>有界</b>是本类最危险的一条：残差超界 MUST 拒绝，绝不因为「看起来更准」而吸收；</li>
 *   <li><b>重锚只改映射</b>，因此映射出错的失败方向是<b>响亮的</b>（被判版本不符 → 升级），
 *       不是静默的错渲。这是敢做重锚的前提。</li>
 * </ol>
 */
class DanmakuServerClockTest {

    private static final double EPS = 1.0E-9D;

    private static DanmakuServerClock clock() {
        return new DanmakuServerClock();
    }

    /**
     * 喂一串观测：服务器每 {@code serverStep} 游戏时间发一次，客户端每
     * {@code serverStep * rate} 本地 tick 处理一次。
     *
     * @return 处理到的最后一个本地 tick
     */
    private static int feed(DanmakuServerClock clock, long startServerTime, int startClientTick,
                            long serverStep, double rate, int samples) {
        int clientTick = startClientTick;
        long serverTime = startServerTime;
        for (int i = 0; i < samples; i++) {
            clock.observe(serverTime, clientTick);
            serverTime += serverStep;
            clientTick += (int) Math.round(serverStep * rate);
        }
        return clientTick;
    }

    // ------------------------------------------------------------------
    // 1.1 默认行为与既有实现逐位一致
    // ------------------------------------------------------------------

    /**
     * 观测不足时斜率恒为 1，换算与 render-state 现有的锚点公式<b>逐位相同</b>。
     *
     * <p>这是「行为不变」承诺的保证。若这条不成立，时钟一接上就会在<b>每一发</b>新弹幕上
     * 引入偏差，而不是只在速率失配时。
     */
    @Test
    void defaultMappingMatchesTheLegacyAnchorFormula() {
        DanmakuServerClock clock = clock();
        clock.observe(1000L, 500);
        assertEquals(DanmakuServerClock.Availability.TRACKING, clock.availability(),
                "单个观测即足以建立斜率为 1 的映射，接上时钟不应改变任何现有行为");
        assertEquals(1.0D, clock.rate(), EPS);
        // anchorClientTick + (serverTime - anchorServerTime)
        assertEquals(510, clock.localTickFor(1010L));
        assertEquals(495, clock.localTickFor(995L));
        assertEquals(1005L, clock.serverTimeNow(505));
    }

    @Test
    void freshClockIsWarmingAndNotYetQueryable() {
        DanmakuServerClock clock = clock();
        assertEquals(DanmakuServerClock.Availability.WARMING, clock.availability());
        assertEquals(Integer.MIN_VALUE, clock.localTickFor(1000L),
                "尚未建立映射时 MUST 返回不可用，调用方据此走兼容路径");
        assertEquals(Integer.MIN_VALUE, clock.serverTimeNow(0));
    }

    // ------------------------------------------------------------------
    // 1.2 速率估计
    // ------------------------------------------------------------------

    @Test
    void rateConvergesWhenTheClientIsSlowerThanTheServer() {
        DanmakuServerClock clock = clock();
        feed(clock, 1000L, 0, 20, 0.95D, 40);
        assertEquals(0.95D, clock.rate(), 5.0E-3D,
                "慢 5% 的客户端 MUST 被估出慢 5%：" + clock.summary());
        assertTrue(clock.rateUpdateCount() > 0, "速率 MUST 至少更新过一次");
    }

    @Test
    void rateConvergesWhenTheClientIsFasterThanTheServer() {
        DanmakuServerClock clock = clock();
        feed(clock, 1000L, 0, 20, 1.05D, 40);
        assertEquals(1.05D, clock.rate(), 5.0E-3D, "快 5% MUST 被估出：" + clock.summary());
    }

    /**
     * 带斜率的换算 MUST 把样本放回本地历史之内 —— 这正是探针实测失败的那一步。
     *
     * <p>无斜率时，服务器时刻 S 映射到 {@code S − S_snap}；真实对应是
     * {@code rate·(S − S_snap)}。慢 5% 且 ΔS = 400 时两者差 21 tick，
     * 远超 {@code DanmakuSampleTimeline} 的 40 tick 窗口余量。
     */
    @Test
    void rateAwareMappingLandsInsideLocalHistory() {
        DanmakuServerClock clock = clock();
        int lastClientTick = feed(clock, 1000L, 0, 20, 0.95D, 40);
        clock.noteTick(lastClientTick);
        int mapped = clock.localTickFor(1400L);
        assertTrue(mapped > 0 && mapped <= lastClientTick,
                "服务器时刻 1400 MUST 映射到已走过的本地 tick 之内，实测 " + mapped
                        + " > " + lastClientTick);
        assertNotEquals(400, mapped,
                "带斜率的映射 MUST NOT 等于无斜率的 ΔS —— 那正是变更前的行为");
    }

    // ------------------------------------------------------------------
    // 1.3 残差界限（反向测试的核心）
    // ------------------------------------------------------------------

    /**
     * 单次离群观测 MUST 被拒绝，且 MUST NOT 改变锚点或速率。
     *
     * <p>「不改变」比「拒绝」更重要：一个会吸收离群点的时钟，能把 8 tick 的离散基准跳变
     * 调没，把响亮的失步变成查不出的错渲。
     */
    @Test
    void singleOutlierIsRejectedWithoutMovingAnything() {
        DanmakuServerClock clock = clock();
        feed(clock, 1000L, 0, 20, 1.0D, 12);
        double rateBefore = clock.rate();
        long anchorServerBefore = clock.anchorServerTime();
        int anchorClientBefore = clock.anchorClientTick();

        // 一个远离趋势的到达 tick：残差 30 tick，远超 maxResidualTicks = 4。
        assertEquals(DanmakuServerClock.Outcome.REJECTED_OUTLIER,
                clock.observe(1000L + 20L * 12, (int) (20 * 12) + 30),
                "离群观测 MUST 被拒绝");
        assertEquals(rateBefore, clock.rate(), EPS, "拒绝 MUST NOT 改变速率");
        assertEquals(anchorServerBefore, clock.anchorServerTime());
        assertEquals(anchorClientBefore, clock.anchorClientTick());
        assertEquals(1L, clock.outlierCount());
    }

    /**
     * 窗口内任一点残差超界即拒绝，故离群点 MUST NOT 进入拟合窗口。
     *
     * <p>若离群点进了窗口，它会成为后续所有斜率计算的端点，把整条时间线带偏。
     */
    @Test
    void outlierNeverEntersTheFitWindow() {
        DanmakuServerClock clock = clock();
        feed(clock, 1000L, 0, 20, 1.0D, 12);
        double cleanRate = clock.rate();
        // 干净序列的下一个点本应是 (1240, 240)；这里给 +40 的偏离。
        assertEquals(DanmakuServerClock.Outcome.REJECTED_OUTLIER,
                clock.observe(1240L, 240 + 40));
        assertEquals(1L, clock.outlierCount());
        // 继续喂干净观测：速率 MUST 仍然干净，说明窗口没被污染。
        feed(clock, 1240L, 240, 20, 1.0D, 10);
        assertEquals(1.0D, clock.rate(), 5.0E-3D,
                "离群点 MUST NOT 影响后续拟合：" + clock.summary());
        assertEquals(cleanRate, 1.0D, EPS);
    }

    // ------------------------------------------------------------------
    // 1.4 硬重锚
    // ------------------------------------------------------------------

    /**
     * 连续同向残差 MUST 在达到阈值后重置窗口，且 MUST NOT 由单次离群触发。
     */
    @Test
    void hardReanchorRequiresAStreak() {
        DanmakuServerClock clock = clock();
        feed(clock, 1000L, 0, 20, 1.0D, 12);
        assertEquals(0L, clock.reanchorCount(), "起点 MUST 无重锚");

        for (int i = 0; i < 2; i++) {
            int n = 12 + i;
            assertEquals(DanmakuServerClock.Outcome.REJECTED_OUTLIER,
                    clock.observe(1000L + 20L * n, 20 * n + 30),
                    "第 " + (i + 1) + " 次同向离群 MUST 仍只是拒绝");
            assertEquals(0L, clock.reanchorCount(),
                    "未达阈值（3）前 MUST NOT 重锚");
        }
        int n = 14;
        clock.observe(1000L + 20L * n, 20 * n + 30);
        assertEquals(1L, clock.reanchorCount(),
                "第 3 次同向残差 MUST 触发重锚（阈值 = 3）");
        assertEquals(DanmakuServerClock.Availability.UNCERTAIN, clock.availability(),
                "重锚后 MUST 先降级，等待新观测重新建立映射");
    }

    /**
     * 反向测试：重锚只改映射，MUST NOT 把客户端的年龄基准一起改掉。
     *
     * <p>本类不持有也不修改客户端的年龄。若它试图「顺手修正」基准，离散的基准跳变就会
     * 被静默吸收 —— 那是本设计明确拒绝的行为。放错位置的后果由
     * {@code DanmakuSampleCheck} 判为运动版本不符，属于<b>响亮的</b>失败。
     */
    @Test
    void reanchorMovesTheMappingOnly() {
        DanmakuServerClock clock = clock();
        feed(clock, 1000L, 0, 20, 1.0D, 12);
        for (int i = 0; i < 3; i++) {
            int n = 12 + i;
            clock.observe(1000L + 20L * n, 20 * n + 30);
        }
        assertEquals(1L, clock.reanchorCount());
        assertEquals(1.0D, clock.rate(), EPS,
                "重锚 MUST 把速率退回默认，而不是保留一个可疑值");
        assertEquals(Integer.MIN_VALUE, clock.localTickFor(1400L),
                "重锚后映射不可用，MUST NOT 继续外推一个不可信的时间轴");
    }

    // ------------------------------------------------------------------
    // 1.5 陈旧与区间外
    // ------------------------------------------------------------------

    @Test
    void stalenessDegradesInsteadOfExtrapolating() {
        DanmakuServerClock clock = clock();
        int last = feed(clock, 1000L, 0, 20, 1.0D, 20);
        assertEquals(DanmakuServerClock.Availability.TRACKING, clock.availability());
        clock.noteTick(last + 10);
        assertEquals(DanmakuServerClock.Availability.TRACKING, clock.availability(),
                "未过陈旧线 MUST 继续可用");
        clock.noteTick(last + 1000);
        assertEquals(DanmakuServerClock.Availability.UNCERTAIN, clock.availability());
        assertEquals(Integer.MIN_VALUE, clock.localTickFor(1400L),
                "陈旧后 MUST 停止外推，而不是给出一个越来越离谱的答案");
    }

    /**
     * 速率落在可信区间外 MUST 被拒绝并降级，斜率保持默认。
     *
     * <p>两倍速差不是「噪声大」，而是两端根本不在同一个时间基准上；接受它会让整条
     * 时间线按错误速率外推。
     */
    @Test
    void rateOutsideTheTrustedRangeIsRejected() {
        DanmakuServerClock clock = clock();
        feed(clock, 1000L, 0, 20, 1.0D, 12);
        // 突然变成 2 倍速差。
        assertEquals(DanmakuServerClock.Outcome.REJECTED_RATE_RANGE,
                clock.observe(1000L + 20L * 12, 20 * 12 * 2),
                "2 倍速差 MUST 被判区间外");
        assertEquals(1.0D, clock.rate(), EPS, "拒绝后速率 MUST 保持默认");
        assertEquals(DanmakuServerClock.Availability.UNCERTAIN, clock.availability());
        assertEquals(1L, clock.rateOutOfRangeCount());
    }

    @Test
    void nonMonotonicObservationsAreIgnored() {
        DanmakuServerClock clock = clock();
        feed(clock, 1000L, 0, 20, 1.0D, 12);
        assertEquals(DanmakuServerClock.Outcome.REJECTED_STALE,
                clock.observe(1000L, 5), "重复/倒序的服务器时刻 MUST 被忽略");
        assertEquals(DanmakuServerClock.Outcome.REJECTED_STALE,
                clock.observe(1600L, 220), "到达本地 tick 不前进 MUST 被忽略 —— "
                        + "否则窗口无法表达「随时间推移」，斜率会被拟成 0");
        assertEquals(2L, clock.staleCount());
    }

    // ------------------------------------------------------------------
    // 参数退化
    // ------------------------------------------------------------------

    @Test
    void degenerateParamsAreClamped() {
        DanmakuServerClock.Params p = new DanmakuServerClock.Params(
                2.0D, 0.5D, 0, 0, 0, 0, 1).sanitized();
        assertTrue(p.minRate() < p.maxRate(), "区间 MUST 被摆正");
        assertTrue(p.maxResidualTicks() >= 1, "残差界限为 0 会让每个观测都超界");
        assertTrue(p.hardReanchorStreak() >= 1);
        assertTrue(p.stalenessTicks() >= 1);
        assertTrue(p.minSpanClientTicks() >= 1, "跨度为 0 会让拟合除零");
        assertTrue(p.windowSamples() >= 2, "窗口为 1 无法表达斜率");
    }

    @Test
    void clearReturnsToWarming() {
        DanmakuServerClock clock = clock();
        feed(clock, 1000L, 0, 20, 0.95D, 20);
        assertEquals(0.95D, clock.rate(), 5.0E-3D);
        clock.clear();
        assertEquals(DanmakuServerClock.Availability.WARMING, clock.availability());
        assertEquals(1.0D, clock.rate(), EPS);
        assertEquals(Integer.MIN_VALUE, clock.localTickFor(1400L));
    }
}
