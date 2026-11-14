package com.bitsson.gensokyou.audio;

/**
 * 持续音句柄：消费方用它维持与停止一条循环音。
 *
 * <p>消费方每 tick 调用一次 {@link #keepAlive()}；不再需要时调用 {@link #stop()}。
 * 忘了调用任何一方也不会永久占用声道 —— {@link BeamEnvelope} 的看门狗会在
 * 10 tick 后自行停止。
 */
public final class BeamHandle {

    private final BeamEnvelope envelope;

    BeamHandle(BeamEnvelope envelope) {
        this.envelope = envelope;
    }

    /** 本 tick 仍然存在。 */
    public void keepAlive() {
        envelope.signal();
    }

    /** 开始收尾（3 tick 淡出）。重复调用幂等。 */
    public void stop() {
        envelope.requestStop();
    }

    public boolean stopped() {
        return envelope.stopped();
    }

    public BeamEnvelope envelope() {
        return envelope;
    }
}
