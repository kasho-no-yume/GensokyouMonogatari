package com.bitsson.gensokyou.danmaku.render;

/**
 * 单个权威样本的可信度，以及「连续几个坏样本算失步」的转换规则。
 *
 * <p><b>为什么要有 UNCERTAIN 这一档</b>：网络抖动会让<b>单个</b>样本越界成为常态。
 * 一次就升级为失步会引发恢复风暴——一批弹同时各发一次恢复请求，而服务端那一刻
 * 采样到的状态与刚越界那次完全一致，于是全部被「修好」，然后下一次抖动再重来。
 * 反过来，两个连续坏样本才具备「不是抖动」的证据强度。
 *
 * <p><b>为什么 severe MUST 直接跳到 DESYNCED</b>：身份不符、生命周期不符、运动版本
 * 回退这三类不是「误差偏大」，而是「我们在拿另一枚弹/另一个时期的状态比现在」。
 * 连续计数对它们没有意义——它们不会因为再看一次就变正常。
 */
public enum DanmakuSampleTrust {

    /** 样本与模拟轨迹一致。 */
    TRUSTED,

    /** 单个样本越界，尚未确认。纠偏照常进行，但不发恢复请求。 */
    UNCERTAIN,

    /** 连续异常或严重违反一致性。停止纠偏，转入显式恢复。 */
    DESYNCED;

    /** 连续坏样本达到该值即判定失步。1 = 只要越界就升级（不推荐，见类注释）。 */
    public static final int DESYNCED_STREAK = 2;

    /**
     * 由「连续坏样本数」与「是否严重违反」求新的可信度。
     *
     * @param consecutiveBadSamples 连续越界样本数（一次有效样本后 MUST 归零）
     * @param severe                 身份 / 生命周期 / 运动版本类硬冲突
     */
    public static DanmakuSampleTrust afterSample(int consecutiveBadSamples, boolean severe) {
        if (severe || consecutiveBadSamples >= DESYNCED_STREAK) {
            return DESYNCED;
        }
        if (consecutiveBadSamples >= 1) {
            return UNCERTAIN;
        }
        return TRUSTED;
    }

    /** 本档是否仍允许用该样本调整视觉纠偏。DESYNCED MUST NOT 调整。 */
    public boolean allowsCorrection() {
        return this != DESYNCED;
    }
}
