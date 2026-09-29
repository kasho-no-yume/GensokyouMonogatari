package com.bitsson.gensokyou.danmaku.render;

import net.minecraft.world.phys.Vec3;

/**
 * 「权威样本 vs 同一采样时刻的本地模拟」比较规则。
 *
 * <p>纯函数：给出时间线、样本与容差，返回「能不能比、比出来差多少、为什么不能比」。
 * 不能比的原因 MUST 与「比出来超界」分开——前者是数据不足（不升级失步），
 * 后者才是真失步（升级）。把两者混为一谈会让每次网络抖动都变成恢复请求。
 */
public final class DanmakuSampleCheck {

    /** 比较结果分类。 */
    public enum Outcome {
        /** 可比，且误差在容差内。 */
        OK,
        /** 可比，但误差超容差。真失步候选。 */
        OUT_OF_TOLERANCE,
        /** 本地还没推进到该采样时刻对应的 tick。样本比本地「新」。 */
        TOO_EARLY,
        /** 对应的本地历史已滑出窗口或尚未记录。样本比本地「旧」。 */
        EXPIRED,
        /** 样本与本地历史的运动版本不同，两者不在同一条轨迹上，不能直接比。 */
        REVISION_MISMATCH
    }

    /**
     * @param outcome       分类
     * @param errorBlocks   空间误差长度（格）；不可比时为 {@link Double#NaN}
     * @param expectedAge   样本声称的年龄
     * @param localAge      对应 tick 上的本地年龄
     * @param localPosition 对应 tick 上的本地模拟位置（不可比时为 {@code null}）
     */
    public record Result(Outcome outcome, double errorBlocks, int expectedAge, int localAge,
                         Vec3 localPosition) {

        public boolean comparable() {
            return this.outcome == Outcome.OK || this.outcome == Outcome.OUT_OF_TOLERANCE;
        }

        /**
         * 可比时给出「权威位置 − 同刻本地模拟位置」，即应当被视觉偏移吸收的漂移。
         *
         * <p>这是纠偏的<b>目标</b>而不是终点：它只说「客户端的模拟在那一刻偏了多少」，
         * 画面据此对齐权威轨迹，而模拟状态本身分毫不动。
         */
        public Vec3 driftVector(Vec3 samplePosition) {
            if (!comparable() || this.localPosition == null || samplePosition == null) {
                return null;
            }
            return samplePosition.subtract(this.localPosition);
        }
    }

    private DanmakuSampleCheck() {
    }

    /**
     * 执行一次比较。
     *
     * @param timeline           本地模拟时间线（必须已锚定）
     * @param sampleServerTime   样本携带的服务器采样时刻
     * @param sampleAge          样本声称的弹幕年龄
     * @param sampleRevision     样本声称的运动版本
     * @param samplePosition     样本位置
     * @param toleranceBlocks    空间容差（格）
     */
    public static Result compare(DanmakuSampleTimeline timeline,
                                  long sampleServerTime, int sampleAge, int sampleRevision,
                                  Vec3 samplePosition, double toleranceBlocks) {
        if (timeline == null || !timeline.anchored() || samplePosition == null) {
            return new Result(Outcome.EXPIRED, Double.NaN, sampleAge, -1, null);
        }
        int localTick = timeline.localTickFor(sampleServerTime);
        if (localTick == Integer.MIN_VALUE) {
            return new Result(Outcome.EXPIRED, Double.NaN, sampleAge, -1, null);
        }
        DanmakuSampleTimeline.Entry entry = timeline.at(localTick);
        if (entry == null) {
            // 本地尚未推进到该 tick（样本比本地新）；已滑出窗口则同样走这里。
            return new Result(localTick > timeline.newestTick() ? Outcome.TOO_EARLY : Outcome.EXPIRED,
                    Double.NaN, sampleAge, -1, null);
        }
        if (entry.motionRevision() != sampleRevision) {
            return new Result(Outcome.REVISION_MISMATCH, Double.NaN, sampleAge, entry.age(),
                    entry.position());
        }
        double error = entry.position().distanceTo(samplePosition);
        Outcome outcome = error <= toleranceBlocks ? Outcome.OK : Outcome.OUT_OF_TOLERANCE;
        return new Result(outcome, error, sampleAge, entry.age(), entry.position());
    }

    /**
     * 样本容差（格）。
     *
     * <p>用「弹在滞后窗口内能走多远」而不是固定值，理由与
     * {@link com.bitsson.gensokyou.danmaku.motion.DanmakuCorrection} 相同：
     * 固定阈值与速度无关，会把全部弹幕劈成「永不纠正（永久滞后）」与
     * 「每包硬拽（抖动）」两种失败。
     */
    public static double toleranceBlocks(double speedBlocksPerTick, int maxLagTicks,
                                         double floorBlocks) {
        double windowed = Math.abs(speedBlocksPerTick) * Math.max(0, maxLagTicks);
        return Math.max(windowed, Math.max(0.0D, floorBlocks));
    }
}
