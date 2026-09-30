package com.bitsson.gensokyou.danmaku.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 客户端同刻比较的<b>合成时钟实测</b>：在完全已知的失真下，测真实状态机的判定。
 *
 * <p><b>这份测试要回答的问题</b>——{@code danmaku-timeline-sync} 的提案认为
 * 「客户端以本地 tick 推进 ⇒ 确定性弹幕仍可能带有相位偏差」，并据此规划了一整套
 * 服务器时间轴 + 轨道快照。提案全文<b>没有一条断言</b>说明这个偏差会产生什么可观测后果，
 * 而 {@code docs/danmaku-sync-architecture-and-open-problems.md} 的实机读数
 * （4~8 tick、双峰翻转、847 次硬纠正）又被该文档 §5.3、§6.5 自己标注为口径可疑。
 *
 * <p>于是先离线合成，把「哪种失真会让比较失效」变成数字。<b>实测结论与提案的预期
 * 三处不一致</b>，都写进了下面的用例注释：
 * <ol>
 *   <li>网络延迟与延迟抖动<b>完全不</b>影响比较误差，也不触发恢复——映射是在年龄空间
 *       做的，延迟被吸收成两侧同时带有的常数偏置；</li>
 *   <li>速率失配<b>不会</b>产生比较误差，也<b>不会</b>触发恢复风暴——误差恒为 0；</li>
 *   <li>速率失配真正的后果是别的：它让<b>每一个</b>校准样本从第二个起就变成
 *       {@code TOO_EARLY}，客户端<b>立即且永久地</b>停止自检，且不产生任何告警。
 *       而提案设计的「再加一个锚点」修不了它——需要的是<b>斜率</b>。</li>
 * </ol>
 *
 * <p>另有一条与提案方向相反的发现：真正会打穿比较的是<b>离散</b>年龄基准跳变，
 * 而它走 {@code REVISION_MISMATCH}（severe 档，第一个样本就升级），
 * <b>不是</b>「误差过大」那条需要连续两次的分支。
 */
class DanmakuSyncProbeTest {

    private static final double EPS = 1.0E-9D;

    private static DanmakuSyncProbe.Result probe(DanmakuSyncProbe.Builder builder) {
        return new DanmakuSyncProbe().run(builder);
    }

    // ------------------------------------------------------------------
    // 1. 网络延迟与抖动
    // ------------------------------------------------------------------

    /**
     * 单向延迟 0~20 tick、抖动 ±10 tick：比较误差恒为 0，且从不触发恢复。
     *
     * <p><b>推导</b>：映射是 {@code localTick = anchorLocalTick + (serverTime − anchorServerTime)}，
     * 而 {@code anchorLocalTick} 记的是快照<b>到达</b>时的本地 tick。于是单向延迟 d 被
     * 吸收成一个常数偏置，且<b>两侧同时带</b>它：查表得到的本地年龄与样本年龄都等于
     * {@code sampleTime − snapshotServerTime}，位置因此逐位相等。抖动只改变样本
     * 「何时到达」，不改变映射本身。
     *
     * <p><b>为什么值得锁死</b>：它一次性否掉「延迟造成失步」这条路径。提案 Why 段的
     * 「固定相位偏差」指的正是 d，而 d 是客户端预测架构的固有代价（客户端<b>应该</b>领先），
     * 不是缺陷。想改它的人必须先推翻本用例。
     */
    @Test
    void networkDelayAndJitterCannotProduceErrorOrResync() {
        for (int delay : new int[]{0, 2, 5, 10, 20}) {
            for (int jitter : new int[]{0, 5, 10}) {
                DanmakuSyncProbe.Result r = probe(DanmakuSyncProbe.builder()
                        .delay(delay, jitter)
                        .run(600)
                        .sampleInterval(20));
                assertEquals(0.0D, r.maxErrorBlocks(), EPS,
                        "延迟 " + delay + " ±" + jitter + " MUST NOT 产生比较误差：" + r);
                assertEquals(0, r.resyncs(),
                        "延迟 MUST NOT 触发恢复——它是常数偏置，不是失步：" + r);
            }
        }
    }

    /**
     * 抖动会让<b>相当一部分</b>样本早到被判 {@code TOO_EARLY}，但这属于正常语义。
     *
     * <p>{@code TOO_EARLY} 的定义是「本地还没走到那一 tick」——数据不足，不是偏差。
     * 客户端每个本地 tick 都记一条历史，所以一个早到 k tick 的样本缺的恰好是
     * {@code newest+1 … newest+k}。均匀抖动下约一半的样本会早到，占比接近 50%
     * 是<b>几何必然</b>，不是异常。
     *
     * <p>真正该被锁死的是两条不变量：抖动<b>不产生误差</b>、<b>不升级为失步</b>，
     * 且末尾会回到 OK（零星的数据不足不是失效）。
     */
    @Test
    void jitterInducesEarlySamplesWithoutErrorOrEscalation() {
        DanmakuSyncProbe.Result steady = probe(DanmakuSyncProbe.builder()
                .delay(2, 0).run(600).sampleInterval(20));
        DanmakuSyncProbe.Result jittery = probe(DanmakuSyncProbe.builder()
                .delay(2, 8).run(600).sampleInterval(20));
        assertEquals(0, steady.notComparableFraction(), EPS,
                "无抖动时 MUST 每个样本都可解：" + steady);
        assertFalse(steady.stillNotComparable(), "无抖动时末尾 MUST 是 OK：" + steady);
        assertEquals(0.0D, jittery.maxErrorBlocks(), EPS,
                "抖动 MUST NOT 产生比较误差：" + jittery);
        assertEquals(0, jittery.resyncs(),
                "数据不足 MUST NOT 被升级为失步——那会变成恢复风暴：" + jittery);
        assertEquals(DanmakuSyncProbe.Kind.OK, jittery.lastKind(),
                "抖动是零星的，末尾 MUST 回到 OK：" + jittery);
    }

    /**
     * 延迟只体现为「客户端年龄比服务端小」这一个常数项，且它精确等于延迟。
     *
     * <p>即玩家看到的是「弹比服务端落后 d tick 地在飞」。这是设计取舍而非缺陷，
     * 但它必须是<b>已知量</b>：容差、生命周期边界与命中窗口都要拿它做预算。
     */
    @Test
    void delayShowsUpAsAConstantPhaseDeficit() {
        DanmakuSyncProbe.Result noDelay = probe(DanmakuSyncProbe.builder().delay(0, 0).run(200));
        DanmakuSyncProbe.Result delayed = probe(DanmakuSyncProbe.builder().delay(6, 0).run(200));
        assertEquals(0.0D, noDelay.finalPhaseErrorTicks(), 1.0E-6D);
        assertEquals(6.0D, delayed.finalPhaseErrorTicks(), 1.0E-6D,
                "相位亏欠 MUST 精确等于单向延迟——它是常数项，不随时间增长");
    }

    // ------------------------------------------------------------------
    // 2. 速率失配
    // ------------------------------------------------------------------

    /**
     * 持续 ±5% 速率失配：比较误差<b>恒为 0</b>，且<b>从不</b>触发恢复。
     *
     * <p><b>这条推翻了「速率失配 ⇒ 恢复风暴」</b>。原因同上：映射把
     * {@code (S − S_snap)} 同时当成服务端年龄增量和客户端 tick 增量，两侧同增，误差相消。
     * 速率失配改变的是相位亏欠的<b>增长率</b>与样本的<b>可达性</b>（见下一条），
     * 不是比较误差。所以恢复风暴的成因<b>不可能</b>是速率失配。
     */
    @Test
    void rateMismatchNeverProducesErrorOrResync() {
        for (double rate : new double[]{0.90D, 0.95D, 1.05D, 1.10D}) {
            DanmakuSyncProbe.Result r = probe(DanmakuSyncProbe.builder()
                    .clientRate(rate)
                    .run(400)
                    .sampleInterval(20));
            assertEquals(0.0D, r.maxErrorBlocks(), EPS,
                    "速率 " + rate + " MUST NOT 产生比较误差——误差只由基准跳变产生：" + r);
            assertEquals(0, r.resyncs(),
                    "速率失配 MUST NOT 触发恢复：" + r);
        }
    }

    /**
     * <b>核心发现</b>：速率失配让自检失效，但<b>两侧的机制与时标完全不同</b>。
     *
     * <p><b>慢侧（r &lt; 1）：立即且永久。</b>样本到达的本地 tick 是 {@code floor(r·ΔS)}，
     * 映射（斜率恒为 1）指向 {@code ΔS}，两者之差 {@code ΔS(1−r)} 每过一个采样间隔
     * 就多涨 1 tick，于是映射<b>永久地</b>指向本地尚未推进到的位置。
     *
     * <p><b>快侧（r &gt; 1）：先正常，窗口耗尽后才永久。</b>映射指向的 tick 落在
     * <b>过去</b>，而过去的历史还在环形缓冲里，所以仍然可比——直到
     * {@code ΔS(r−1)} 超过 {@link DanmakuSampleTimeline#DEFAULT_WINDOW_TICKS}(40)
     * 才滑出，此后永久 {@code EXPIRED}。5% 快 ⇒ 约 800 tick ≈ 40 秒。
     *
     * <p><b>为什么两侧都静默</b>：{@code recordCalibration} 对 TOO_EARLY / EXPIRED
     * 既不升级也不发请求——设计如此，因为它们是「数据不足」而非「偏差」。于是
     * render-state 的全部校验能力在任何一个非 tick 锁定到服务端的客户端上整体失效，
     * 没有任何症状；同时画面以 {@code ΔS(1−r)} 的速度越来越慢（或越快）。
     *
     * <p><b>慢侧严格更糟</b>：立即发生、无恢复路径、且是玩家更容易遇到的方向
     * （弱机掉帧）。任何修复方案都必须先满足慢侧。
     */
    @Test
    void rateMismatchLosesSelfCheckSilentlyAndAsymmetrically() {
        DanmakuSyncProbe.Result slow = probe(DanmakuSyncProbe.builder()
                .clientRate(0.95D).run(400).sampleInterval(20));
        assertTrue(slow.stillNotComparable(),
                "慢速客户端末尾 MUST 仍不可比：" + slow);
        assertEquals(1, slow.count(DanmakuSyncProbe.Kind.OK),
                "只有第一个样本（ΔS=0）可解，其余全部早到。实测 " + slow);
        assertEquals(0, slow.resyncs(),
                "这正是它静默的原因：数据不足不升级、不发请求：" + slow);
        assertTrue(slow.firstOf(DanmakuSyncProbe.Kind.TOO_EARLY)
                        <= slow.firstOf(DanmakuSyncProbe.Kind.OK) + 20,
                "慢侧 MUST 从第一个采样间隔就失效，而不是几十秒之后："
                        + slow.firstOf(DanmakuSyncProbe.Kind.TOO_EARLY));

        DanmakuSyncProbe.Result fast = probe(DanmakuSyncProbe.builder()
                .clientRate(1.05D).run(400).sampleInterval(20));
        assertFalse(fast.stillNotComparable(),
                "快侧在窗口耗尽前 MUST 仍然可比——它指向过去，不是未来：" + fast);
        assertEquals(0.0D, fast.maxErrorBlocks(), EPS);
        assertTrue(fast.finalPhaseErrorTicks() < 0.0D,
                "快客户端的相位亏欠 MUST 为负（它画得比服务端年轻）：实测 "
                        + fast.finalPhaseErrorTicks());

        DanmakuSyncProbe.Result fastLong = probe(DanmakuSyncProbe.builder()
                .clientRate(1.05D).run(1600).sampleInterval(20));
        assertTrue(fastLong.stillNotComparable(),
                "快侧跑够 40 秒后 MUST 也永久失效（EXPIRED 方向）：" + fastLong);
        assertEquals(0, fastLong.resyncs(), "同样静默：" + fastLong);
    }

    /**
     * 客户端越慢，画面就成比例地慢，且这个偏差无上界。
     *
     * <p>与「延迟」那一条对照：延迟是常数亏欠，一次预算就能覆盖；速率失配是
     * <b>线性增长</b>的亏欠——1600 个服务端 tick、5% 慢就是整整 80 tick，
     * 已经超过历史窗口宽度（40）本身。
     */
    @Test
    void rateMismatchAccumulatesPhaseDeficitWithoutBound() {
        DanmakuSyncProbe.Result healthy = probe(DanmakuSyncProbe.builder()
                .clientRate(1.0D).run(1600).sampleInterval(20));
        DanmakuSyncProbe.Result slow = probe(DanmakuSyncProbe.builder()
                .clientRate(0.95D).run(1600).sampleInterval(20));
        assertEquals(2.0D, healthy.finalPhaseErrorTicks(), 1.0E-6D,
                "健康态只剩单向延迟那一个常数项");
        assertTrue(slow.finalPhaseErrorTicks() > 70.0D,
                "5% 慢跑 1600 tick 后相位亏欠 MUST 达到几十 tick 量级，实测 "
                        + slow.finalPhaseErrorTicks());
    }

    // ------------------------------------------------------------------
    // 3. 时钟已知时会发生什么（为变更 A 的形状取证）
    // ------------------------------------------------------------------

    /**
     * 把真实速率喂进映射，样本立刻回到可比，自检恢复。
     *
     * <p>这是本测试集里唯一一条「未来形状」的取证：它证明修法是<b>斜率</b>而不是
     * 「再要一个锚点」。锚点只能平移映射，斜率为 1 的直线<b>永远</b>够不到
     * {@code floor(r·ΔS)}。
     *
     * <p>对照 {@link #rateMismatchLosesSelfCheckSilentlyAndAsymmetrically()}：同一条
     * 时间线、同一批样本、同一份状态机，只把「怎么把服务器时刻换算成本地 tick」
     * 换成带速率的版本，{@code TOO_EARLY} 从「几乎全部」降到 0，且误差仍然是 0。
     * ⇒ 变更 A 的最小充分形态是<b>带速率与残差界限的时钟估计</b>，
     * 不需要轨道、scope、版本或生命周期。
     *
     * <p>本探针的「速率已知」模式走的是<b>真实代码路径</b>
     * （{@code DanmakuClientClock} → {@code DanmakuSampleTimeline.setRate} →
     * {@code DanmakuSampleCheck.compare}），验证的是生产接线而非模型。
     */
    @Test
    void knowingTheRateRestoresSelfCheck() {
        for (double rate : new double[]{0.90D, 0.95D, 1.05D, 1.10D}) {
            DanmakuSyncProbe.Result blind = probe(DanmakuSyncProbe.builder()
                    .clientRate(rate).run(400).sampleInterval(20));
            DanmakuSyncProbe.Result aware = probe(DanmakuSyncProbe.builder()
                    .clientRate(rate).knownClientRate(rate).run(400).sampleInterval(20));
            assertEquals(0.0D, aware.maxErrorBlocks(), EPS,
                    "速率已知时误差 MUST 仍为 0：" + aware);
            assertEquals(0, aware.resyncs(),
                    "速率已知 MUST NOT 引入恢复——它是纯映射修正：" + aware);
            assertFalse(aware.stillNotComparable(),
                    "速率已知 MUST 在任何速率下都恢复自检，速率 " + rate + "：" + aware);
            assertTrue(aware.notComparableFraction() <= blind.notComparableFraction() + EPS,
                    "速率 " + rate + " 下时钟 MUST NOT 让自检变差：盲 "
                            + blind.notComparableFraction() + " → 已知 "
                            + aware.notComparableFraction());
        }

        // 严格改善只在<b>确有缺陷</b>的一侧成立：慢侧立即失效，快侧在窗口内仍然正常，
        // 所以那里没有可改善的余地。用「相对」断言而不是「绝对为零」才说得通。
        DanmakuSyncProbe.Result slowBlind = probe(DanmakuSyncProbe.builder()
                .clientRate(0.95D).run(400).sampleInterval(20));
        DanmakuSyncProbe.Result slowAware = probe(DanmakuSyncProbe.builder()
                .clientRate(0.95D).knownClientRate(0.95D).run(400).sampleInterval(20));
        assertEquals(0, slowAware.count(DanmakuSyncProbe.Kind.TOO_EARLY),
                "慢侧 MUST 被完全修好：" + slowAware);
        assertTrue(slowBlind.count(DanmakuSyncProbe.Kind.TOO_EARLY) > 0,
                "慢侧盲态 MUST 确实有早到样本，否则本用例无意义：" + slowBlind);
    }

    /**
     * 速率为 1.0 时，带速率的接线 MUST 与「无速率」逐项一致。
     *
     * <p>探针早期版本里那段带速率的比较是手抄的 {@link DanmakuSampleCheck#compare}，
     * 只改斜率；抄错一处就会让上面那条取证变成自说自话。现在它已换成真实接线，
     * 本用例改为钉住更重要的那条性质：<b>速率为 1 的换算与旧的斜率硬编码公式
     * 逐位相同</b>。否则接上时钟就会在每一发新弹幕上引入偏差，而不只是修正速率失配。
     */
    @Test
    void knownRateModelAgreesWithProductionAtUnitRate() {
        for (int delay : new int[]{0, 3, 12}) {
            DanmakuSyncProbe.Result production = probe(DanmakuSyncProbe.builder()
                    .delay(delay, 4).run(400).sampleInterval(20));
            DanmakuSyncProbe.Result model = probe(DanmakuSyncProbe.builder()
                    .delay(delay, 4).knownClientRate(1.0D).run(400).sampleInterval(20));
            assertEquals(production.byKind(), model.byKind(),
                    "rate=1.0 时带速率的模型 MUST 与生产逐项一致（delay=" + delay + "）");
            assertEquals(production.maxErrorBlocks(), model.maxErrorBlocks(), EPS);
        }
    }

    // ------------------------------------------------------------------
    // 4. 离散年龄基准跳变
    // ------------------------------------------------------------------

    /**
     * 匀速弹的年龄基准跳变走 {@code REVISION_MISMATCH}，<b>第一个样本就升级</b>。
     *
     * <p>匀速弹的位置逐步累加，基准跳变<b>不影响位置</b>，所以位置比较是干净的
     * （误差 0）。但 {@code revision = 年龄}，基准一跳版本就不同，两端被判定为
     * 「不在同一条轨迹上」——severe 档，<b>不需要连续两次</b>。
     *
     * <p>于是 {@code docs/…-open-problems.md} §6.4 那张表（rebuilt 各档都按「误差大小」
     * 解释）从一开始就分错了类：真实的 rebuilt 失步是<b>版本不符</b>，不是位置超差。
     * 两条分支的处置不同（一个先降级为 UNCERTAIN，一个直接升级），混成「误差」
     * 会误导阈值设计。
     */
    @Test
    void discreteAgeBasisStepOnAnIncrementalBulletIsCaughtImmediately() {
        DanmakuSyncProbe.Result r = probe(DanmakuSyncProbe.builder()
                .ageBasisStep(100, 8, false)
                .run(400)
                .sampleInterval(20));
        assertTrue(r.count(DanmakuSyncProbe.Kind.REVISION_MISMATCH) > 0,
                "基准跳变 MUST 被版本检查抓住：" + r);
        assertEquals(0.0D, r.maxErrorBlocks(), EPS,
                "匀速弹的位置不受基准跳变影响，位置误差 MUST 仍为 0：" + r);
        assertTrue(r.resyncs() >= 1, "版本不符 MUST 触发恢复：" + r);
    }

    /**
     * 解析式弹种（编队帧 / 速率曲线）的基准跳变同时打穿版本与位置。
     *
     * <p>这类弹的位置是年龄的解析函数，基准一跳位置整体位移 {@code |v| · step}。
     * 它同时命中版本检查与位置容差，而 severe 档让版本检查先赢——恢复只发生一次，
     * 不会因为位置持续超差而反复升级。
     */
    @Test
    void discreteAgeBasisStepOnAFormationBulletMovesPositionToo() {
        DanmakuSyncProbe.Result r = probe(DanmakuSyncProbe.builder()
                .speed(0.4D)
                .ageBasisStep(100, 8, true)
                .run(400)
                .sampleInterval(20));
        assertTrue(r.count(DanmakuSyncProbe.Kind.REVISION_MISMATCH) > 0,
                "解析式弹同样先命中版本检查：" + r);
        assertTrue(r.resyncs() >= 1, "MUST 触发恢复：" + r);
        assertTrue(r.p95ErrorBlocks() < 0.4D,
                "恢复之后 MUST 回到干净状态，实测 p95=" + r.p95ErrorBlocks() + " " + r);
    }

    /**
     * 一次恢复之后比较立刻恢复精确，且不复发。
     *
     * <p>这条锁住「恢复是幂等且终止的」：若将来给时钟加上有界重锚，必须仍然满足它，
     * 否则会退化成重锚—失步—重锚的自激振荡。
     */
    @Test
    void recoveryIsTerminalAndNotOscillating() {
        DanmakuSyncProbe.Result r = probe(DanmakuSyncProbe.builder()
                .ageBasisStep(100, 8, true)
                .run(600)
                .sampleInterval(20));
        assertTrue(r.resyncs() <= 2,
                "单次基准跳变 MUST NOT 引发恢复振荡，实测 resync=" + r.resyncs() + " " + r);
    }

    // ------------------------------------------------------------------
    // 5. 容量
    // ------------------------------------------------------------------

    /**
     * 采样更密 MUST NOT 改变误差——误差只由映射决定。
     *
     * <p>因此提高校准频率并不能提高自检的<b>可达性</b>：可达性由映射与真实速率的
     * 偏差决定（见速率失配那几条），与包量无关。提案 tasks 1.1 想要的「校准包流量基线」
     * MUST 连同<b>窗口宽度与客户端速率</b>一起测，只测包量会得出「调大频率就能自检得更勤」
     * 的错觉。
     */
    @Test
    void sampleRateChangesCostButNotCorrectness() {
        DanmakuSyncProbe.Result rare = probe(DanmakuSyncProbe.builder()
                .run(400).sampleInterval(40));
        DanmakuSyncProbe.Result dense = probe(DanmakuSyncProbe.builder()
                .run(400).sampleInterval(5));
        assertTrue(dense.samplesDelivered() > rare.samplesDelivered() * 4,
                "本用例的前提是「密集采样确实送出了更多样本」");
        assertEquals(0.0D, rare.maxErrorBlocks(), EPS);
        assertEquals(0.0D, dense.maxErrorBlocks(), EPS,
                "采样更密 MUST NOT 改变误差：" + dense);
    }
}
