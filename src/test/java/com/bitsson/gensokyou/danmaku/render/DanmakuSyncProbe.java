package com.bitsson.gensokyou.danmaku.render;

import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * 合成双端探针：用一个可配置的「时钟 + 网络」模型驱动<b>真实的</b>
 * {@link DanmakuRenderState} 与 {@link DanmakuSampleTimeline}，把「哪一种失真真的会让
 * 同刻比较失效」变成可复现的数字，而不是推测。
 *
 * <p><b>为什么需要它</b>——{@code danmaku-timeline-sync} 的正当性建立在
 * 「客户端时间轴缺少速率、因此会静默失去自检」这个前提上，而这个前提在实现之前
 * 是<b>未被验证</b>的。实机测量的口径本身有缺陷
 * （{@code docs/danmaku-sync-architecture-and-open-problems.md} §5.3 记着一次守卫把
 * 99% 的样本丢掉、导致归因被带偏三轮），所以这里改为离线合成：输入完全已知的失真，
 * 读出真实状态机的判定。
 *
 * <p><b>模型</b>（全部可由 {@link #builder()} 配置）：
 * <pre>
 *   服务端   gameTime 每 tick +1；弹年龄 = gameTime − 出生 gameTime；位置 = v · 年龄
 *   客户端   本地 tick L 落在墙钟 snapshotServerTime + delay0 + (L − anchorClientTick) / rate
 *   锚点     快照在 snapshotServerTime 采样、在本地 tick 0 到达 ⇒ 锚点吸收单向延迟
 *   校准     每 sampleInterval 个服务端 tick 采样一次，经 delay(+jitter) 后到达
 *   恢复     升级失步后按 roundTrip 往返；服务端在更晚时刻应答完整快照并重新锚定
 * </pre>
 *
 * <p><b>闭式轨迹是刻意的</b>：它让「映射误差」成为唯一的误差来源，于是测到的数字
 * 就是映射误差本身，而不是「闭式误差 + 映射误差」的混合。真实弹种里曲射的
 * {@code sin/cos} 逐 tick 累积误差另算，那是 {@code Rotation} 的数值纪律问题，
 * 与时间轴无关。
 *
 * <p><b>本类不做的事</b>：不模拟渲染、不模拟命中、不模拟生成包。客户端实体创建与
 * 快照到达被合并为「本地 tick 0」这一个事件——真实链路里两者相差一两个 tick，
 * 对本探针要测的量级不产生区别。
 */
public final class DanmakuSyncProbe {

    /** 一次比较的分类。与 {@link DanmakuSampleCheck.Outcome} 对齐。 */
    public enum Kind {
        /** 可比且在容差内。 */
        OK,
        /** 可比但超容差。真失步候选，需连续两次才升级。 */
        OUT_OF_TOLERANCE,
        /** 映射指向本地尚未推进到的 tick。客户端比服务端慢时出现。 */
        TOO_EARLY,
        /** 映射指向已滑出窗口的 tick。客户端比服务端快时出现。 */
        EXPIRED,
        /** 运动版本不同：两端不在同一条轨迹上，位置比较无意义。 */
        REVISION_MISMATCH
    }

    /**
     * 一次运行的统计。字段全部是「这段运行里发生了什么」，不含任何期望值。
     *
     * @param firstOf              各类首次出现的本地 tick；未出现为 {@link Integer#MIN_VALUE}
     * @param finalPhaseErrorTicks 结束时客户端年龄相对服务端年龄的亏欠（tick），正数 = 客户端慢
     */
    public record Result(
            int clientTicks,
            int samplesDelivered,
            Map<Kind, Integer> byKind,
            Map<Kind, Integer> firstOf,
            Kind lastKind,
            double maxErrorBlocks,
            double p95ErrorBlocks,
            int resyncs,
            int firstResyncClientTick,
            double finalPhaseErrorTicks) {

        public int count(Kind kind) {
            return this.byKind.getOrDefault(kind, 0);
        }

        public int firstOf(Kind kind) {
            return this.firstOf.getOrDefault(kind, Integer.MIN_VALUE);
        }

        /** 「不可比」= 客户端已经停止自检，且没有任何样本会升级为失步。 */
        public boolean stillNotComparable() {
            return this.lastKind == Kind.TOO_EARLY || this.lastKind == Kind.EXPIRED;
        }

        /** 不可比样本占比。 */
        public double notComparableFraction() {
            return this.samplesDelivered == 0 ? 0.0D
                    : (double) (count(Kind.TOO_EARLY) + count(Kind.EXPIRED))
                    / this.samplesDelivered;
        }

        /** 终态健康：既没触发恢复，末尾也仍在自检。 */
        public boolean healthy() {
            return this.resyncs == 0 && !stillNotComparable();
        }

        @Override
        public String toString() {
            return String.format(
                    "ticks=%d samples=%d %s last=%s maxErr=%.4f p95Err=%.4f resync=%d@%d"
                            + " phaseErr=%.2f",
                    clientTicks, samplesDelivered, byKind, lastKind, maxErrorBlocks,
                    p95ErrorBlocks, resyncs, firstResyncClientTick, finalPhaseErrorTicks);
        }
    }

    /** 参数。默认值是「健康」配置。 */
    public static final class Builder {

        /** 弹速（格/tick）。与 {@link DanmakuSampleCheck} 的容差同量纲。 */
        double speedBlocksPerTick = 0.4D;

        /** 客户端每单位服务端时间推进的本地 tick 数。1.0 = 同速，0.95 = 慢 5%。 */
        double clientRate = 1.0D;

        long birthServerTime = 1000L;

        /** 快照的服务端采样时刻。与出生时刻相同 = 新发射；更大 = 中途才被追踪。 */
        long snapshotServerTime = 1000L;

        /** 单向传播延迟的中位数（tick）。同时充当快照延迟，故锚点把它吸收成常数偏置。 */
        int baseDelayTicks = 2;

        /** 均匀抖动幅度 ±N（tick）。 */
        int jitterTicks = 0;

        int sampleInterval = 40;

        /** 跑多少个服务端 tick。 */
        int runServerTicks = 400;

        int maxLagTicks = 4;

        double correctionFloor = 0.25D;

        long seed = 20260930L;

        /** 恢复往返（tick）。默认按两次单向延迟 + 服务端处理估算。 */
        int resyncRoundTripTicks = -1;

        /**
         * 在第几个本地 tick 把客户端的年龄基准整体跳 {@link #ageBasisStepTicks}。
         * 模拟 {@code seedPeerAge} 被二次配对改写。<b>-1 表示不注入；只发生一次。</b>
         */
        int ageBasisStepAtClientTick = -1;

        int ageBasisStepTicks = 0;

        /**
         * 年龄跳变时位置是否也跟着跳。
         *
         * <p>这是编队帧 / 速率曲线弹与匀速弹的真正差别：前者的位置是年龄的<b>解析</b>
         * 函数，基准一跳位置就整体位移；后者逐步累加，基准跳变对它毫无影响。
         * 两种都必须测——它们在状态机里走<b>不同的</b>分支。
         */
        boolean stepAlsoMovesPosition = false;

        /**
         * 已知的真实客户端速率。负值 = 未知，映射只能用斜率恒为 1 的锚点。
         *
         * <p>正值的含义是「假装时钟已经估出了速率」。它走的是<b>真实接线</b>：
         * 把速率推给 {@code timeline.setRate(...)}，再交给
         * {@link DanmakuSampleCheck#compare}。存在的目的是把「速率能修」与
         * 「只有锚点修不了」区分开。
         */
        double knownClientRate = -1.0D;

        public Builder speed(double value) {
            this.speedBlocksPerTick = value;
            return this;
        }

        public Builder clientRate(double value) {
            this.clientRate = value;
            return this;
        }

        public Builder delay(int base, int jitter) {
            this.baseDelayTicks = base;
            this.jitterTicks = jitter;
            return this;
        }

        public Builder sampleInterval(int value) {
            this.sampleInterval = value;
            return this;
        }

        public Builder run(int serverTicks) {
            this.runServerTicks = serverTicks;
            return this;
        }

        public Builder snapshotAt(long serverTime) {
            this.snapshotServerTime = serverTime;
            return this;
        }

        public Builder maxLagTicks(int value) {
            this.maxLagTicks = value;
            return this;
        }

        public Builder ageBasisStep(int atClientTick, int stepTicks, boolean movesPosition) {
            this.ageBasisStepAtClientTick = atClientTick;
            this.ageBasisStepTicks = stepTicks;
            this.stepAlsoMovesPosition = movesPosition;
            return this;
        }

        public Builder knownClientRate(double value) {
            this.knownClientRate = value;
            return this;
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public Result run(Builder b) {
        Random random = new Random(b.seed);
        int roundTrip = b.resyncRoundTripTicks >= 0
                ? b.resyncRoundTripTicks : 2 * b.baseDelayTicks + 2;
        double speed = b.speedBlocksPerTick;

        // 锚点：服务端采样时刻 ↔ 客户端本地 tick。恢复会改写它。
        long anchorServerTime = b.snapshotServerTime;
        int anchorClientTick = 0;
        // 重配对注入的年龄基准偏置。完整快照把它清零——快照是权威基准。
        int skew = 0;
        boolean stepApplied = false;

        DanmakuRenderState state = new DanmakuRenderState(
                UUID.nameUUIDFromBytes(new byte[]{0x64, 0x61, 0x6E, 0x01}));
        state.acceptSnapshot(1L, anchorServerTime, ageAt(anchorServerTime, b.birthServerTime),
                ageAt(anchorServerTime, b.birthServerTime), 0, Vec3.ZERO, Vec3.ZERO,
                anchorClientTick);

        List<Arrival> plan = planArrivals(b, random);
        Map<Kind, Integer> byKind = new EnumMap<>(Kind.class);
        Map<Kind, Integer> firstOf = new EnumMap<>(Kind.class);
        List<Double> errors = new ArrayList<>();
        int delivered = 0;
        int resyncs = 0;
        int firstResync = Integer.MIN_VALUE;
        long token = 1L;
        int cursor = 0;
        int lastLocalTick = 0;
        Kind lastKind = null;

        for (int localTick = 0; localTick <= lastLocalTick(b, plan); localTick++) {
            if (!stepApplied && b.ageBasisStepAtClientTick >= 0
                    && localTick >= b.ageBasisStepAtClientTick) {
                skew = b.ageBasisStepTicks;
                stepApplied = true;
            }

            int baseAge = ageAt(anchorServerTime, b.birthServerTime)
                    + (localTick - anchorClientTick);
            int age = baseAge + skew;
            // 位置是「基准年龄」的闭式函数；只有解析式弹种（编队/速率曲线）才跟着偏置走。
            int posAge = b.stepAlsoMovesPosition ? age : baseAge;
            state.clientTick(localTick, age, age, new Vec3(speed * posAge, 0.0D, 0.0D),
                    new Vec3(speed, 0.0D, 0.0D));
            lastLocalTick = localTick;

            while (cursor < plan.size() && plan.get(cursor).clientTick() == localTick) {
                Arrival arrival = plan.get(cursor++);
                long sampleTime = arrival.serverTime();
                int sampleAge = ageAt(sampleTime, b.birthServerTime);
                Vec3 samplePos = new Vec3(speed * sampleAge, 0.0D, 0.0D);
                delivered++;

                double tolerance = DanmakuSampleCheck.toleranceBlocks(
                        speed, b.maxLagTicks, b.correctionFloor);
                if (b.knownClientRate > 0.0D) {
                    // 真实接线：速率是全局的、锚点是逐实体的，这里只推速率。
                    state.timeline().setRate(b.knownClientRate);
                }
                DanmakuSampleCheck.Result check = DanmakuSampleCheck.compare(
                        state.timeline(), sampleTime, sampleAge, sampleAge, samplePos, tolerance);
                Kind kind = kindOf(check);
                byKind.merge(kind, 1, Integer::sum);
                firstOf.putIfAbsent(kind, localTick);
                lastKind = kind;
                if (check.comparable()) {
                    errors.add(check.errorBlocks());
                }

                if (state.recordCalibration(sampleTime, sampleAge, delivered, sampleAge,
                        samplePos, tolerance)) {
                    // 生产路径：升级 → 发恢复请求 → 服务端在 roundTrip 之后应答完整快照
                    // → applyDanmakuSnapshot 原子改写位置与年龄基准 → 重新锚定。
                    resyncs++;
                    if (firstResync == Integer.MIN_VALUE) {
                        firstResync = localTick;
                    }
                    anchorServerTime = sampleTime + roundTrip;
                    anchorClientTick = localTick;
                    skew = 0;
                    token++;
                    int serverAge = ageAt(anchorServerTime, b.birthServerTime);
                    state.acceptSnapshot(token, anchorServerTime, serverAge, serverAge, 0,
                            Vec3.ZERO, Vec3.ZERO, anchorClientTick);
                }
            }
        }

        return new Result(lastLocalTick, delivered, byKind, firstOf, lastKind,
                max(errors), percentile(errors, 0.95), resyncs, firstResync,
                phaseErrorTicks(b, lastLocalTick, anchorServerTime, anchorClientTick, skew));
    }

    // ------------------------------------------------------------------
    // 排程：把服务端采样时刻换算成「在哪个本地 tick 到达」
    // ------------------------------------------------------------------

    /** 一次待投递的样本：服务端采样时刻 + 到达的本地 tick。 */
    private record Arrival(long serverTime, int clientTick) {
    }

    /**
     * 排出整段运行的全部样本。
     *
     * <p>到达 tick 由墙钟反解：样本在服务端时刻 {@code s} 发出、经 {@code delay} 延迟后
     * 落在墙钟 {@code s + delay}，而本地 tick {@code L} 对应的墙钟是
     * {@code snapshotServerTime + delay0 + (L − anchorClientTick) / rate}。
     * 两者相等即得到达 tick。锚点在 tick 0 建立，故此处 anchor 取 0。
     */
    private static List<Arrival> planArrivals(Builder b, Random random) {
        List<Arrival> plan = new ArrayList<>();
        for (long s = b.birthServerTime; s <= b.birthServerTime + b.runServerTicks; s++) {
            if ((s - b.birthServerTime) % b.sampleInterval != 0) {
                continue;
            }
            int delay = b.baseDelayTicks + (b.jitterTicks == 0
                    ? 0
                    : random.nextInt(2 * b.jitterTicks + 1) - b.jitterTicks);
            double sinceAnchor = s + delay - b.snapshotServerTime - b.baseDelayTicks;
            plan.add(new Arrival(s, Math.max(0, (int) Math.floor(b.clientRate * sinceAnchor))));
        }
        return plan;
    }

    private static int lastLocalTick(Builder b, List<Arrival> plan) {
        int last = b.runServerTicks;
        for (Arrival a : plan) {
            last = Math.max(last, a.clientTick());
        }
        return last;
    }

    // ------------------------------------------------------------------
    // 辅助
    // ------------------------------------------------------------------

    private static int ageAt(long serverTime, long birthServerTime) {
        return (int) (serverTime - birthServerTime);
    }

    /**
     * 结束时客户端年龄相对服务端年龄的亏欠（tick）。
     *
     * <p>这就是玩家看到的「慢」：正数 = 客户端画出来的年龄比服务端小。
     * 单向延迟贡献常数项，速率失配贡献随时间线性增长的那一项。
     */
    private static double phaseErrorTicks(Builder b, int lastLocalTick,
                                          long anchorServerTime, int anchorClientTick, int skew) {
        double wall = b.snapshotServerTime + b.baseDelayTicks
                + (lastLocalTick - anchorClientTick) / b.clientRate;
        int clientAge = ageAt(anchorServerTime, b.birthServerTime)
                + (lastLocalTick - anchorClientTick) + skew;
        return ageAt((long) Math.floor(wall), b.birthServerTime) - clientAge;
    }

    private static Kind kindOf(DanmakuSampleCheck.Result check) {
        return switch (check.outcome()) {
            case OK -> Kind.OK;
            case OUT_OF_TOLERANCE -> Kind.OUT_OF_TOLERANCE;
            case TOO_EARLY -> Kind.TOO_EARLY;
            case EXPIRED -> Kind.EXPIRED;
            case REVISION_MISMATCH -> Kind.REVISION_MISMATCH;
        };
    }

    private static double max(List<Double> values) {
        double m = 0.0D;
        for (double v : values) {
            m = Math.max(m, v);
        }
        return m;
    }

    private static double percentile(List<Double> values, double q) {
        if (values.isEmpty()) {
            return 0.0D;
        }
        List<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int index = (int) Math.ceil(q * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(sorted.size() - 1, index)));
    }
}
