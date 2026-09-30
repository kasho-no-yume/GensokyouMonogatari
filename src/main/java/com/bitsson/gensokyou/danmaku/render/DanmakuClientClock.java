package com.bitsson.gensokyou.danmaku.render;

import com.bitsson.gensokyou.danmaku.motion.DanmakuServerClock;

/**
 * 客户端的服务器时间轴门面：持有一个全局 {@link DanmakuServerClock}，对外只暴露
 * <b>速率</b>。
 *
 * <p><b>为什么速率全局、锚点逐实体</b>——「服务器时刻 ↔ 本地 tick」必须逐实体成立，
 * 因为不同实体的本地 tick 计数互相有偏移（新生成的实体从 0 起数），而本地模拟历史
 * （{@link DanmakuSampleTimeline}）又是按各自的 {@code tickCount} 寻址的。
 * 但速率是<b>连接</b>的属性：同一个客户端只有一个相对服务器的快慢，全局一份即可。
 *
 * <p>把两者分开还有一个好处：跨维度时各维度的 {@code getGameTime()} 基准不同，
 * 那种差异是<b>常数偏移</b>而非速率差，会被逐实体锚点吸收；若把偏移也塞进全局时钟，
 * 换维度就会表现成一次剧烈的速率跳变并触发无谓的重锚。
 *
 * <p><b>刻意不引用任何 Minecraft 类型</b>：{@link DanmakuRenderState} 在专用服务端上
 * 也会被类加载，它一旦静态引用本类，本类就间接拉进 {@code Minecraft}。
 * 调用方（客户端事件与 payload 处理器）负责把客户端 tick 传进来。
 *
 * <p>观测通道是既有的 {@code DanmakuCalibrationPayload}：它已经携带服务器采样时刻，
 * <b>本变更不新增协议包</b>。
 */
public final class DanmakuClientClock {

    private static DanmakuServerClock clock = new DanmakuServerClock();

    private DanmakuClientClock() {
    }

    /**
     * 提交一次观测。
     *
     * @param serverTime 该数据在服务器上的采样时刻（{@code level.getGameTime()}）
     * @param clientTick 本端处理到它时的客户端 tick（{@code level.getGameTime()}）
     * @return 判定结果，供诊断
     */
    public static DanmakuServerClock.Outcome observe(long serverTime, long clientTick) {
        DanmakuServerClock.Outcome outcome = clock.observe(serverTime, (int) clientTick);
        DanmakuSyncStats.recordClockObservation(outcome);
        return outcome;
    }

    /**
     * 当前速率：每个服务器 tick 走多少本地 tick。
     *
     * <p>不可用时返回 1.0，使下游行为与时钟接入前<b>逐位相同</b>。
     */
    public static double rate() {
        return clock.availability() == DanmakuServerClock.Availability.TRACKING
                ? clock.rate() : 1.0D;
    }

    /** 客户端每 tick 调用：陈旧即降级，停止外推。 */
    public static void noteTick(long clientTick) {
        clock.noteTick((int) clientTick);
    }

    public static DanmakuServerClock clock() {
        return clock;
    }

    /**
     * 切世界 / 断线重连时清空。
     *
     * <p>不清空的后果不是「慢一拍收敛」：上一段连接的速率会被套到新一段上，
     * 而新连接的快慢可能完全不同。
     */
    public static void reset() {
        clock = new DanmakuServerClock();
        DanmakuSyncStats.recordClockReset();
    }

    /** 一行摘要，供 {@code /gs_boss danmaku} 汇总。 */
    public static String summary() {
        return clock.summary();
    }
}
