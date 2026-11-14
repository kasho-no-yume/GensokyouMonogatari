package com.bitsson.gensokyou.audio;

/**
 * 持续音的音量/音高包络（纯状态机，可离线断言）。
 *
 * <p>三段：<b>起音 → 循环 → 收尾</b>。之所以把包络从 {@code BeamSound} 里拆出来：
 * {@code AbstractTickableSoundInstance} 是客户端类，纯 JUnit 无法实例化；
 * 拆开后「淡变下限」「感知曲线」「看门狗自停」都能离线验证，
 * {@code BeamSound} 只剩「把包络值喂给 getVolume()/getPitch()」。
 *
 * <h2>为什么每段至少 3 tick</h2>
 * 客户端 {@code SoundEngine} 的音量更新是 <b>20 Hz（每 tick 一次）</b>。
 * 少于 3 tick 的淡变实际上只有一两个台阶，听感是「啪」甚至爆音。
 *
 * <h2>为什么用 p^1.7 而不是线性</h2>
 * {@code getVolume()} 在 MC 里是<b>振幅</b>。线性从满拉到零，人耳听到的是
 * 「前半段几乎没变、末尾骤降」。幂曲线让感知响度近似线性下降。
 *
 * <h2>看门狗</h2>
 * 连续 {@link #WATCHDOG_TICKS} tick 未收到 {@link #signal()} 即<b>立即</b>停止。
 * 这里刻意不做淡出：看门狗是「句柄泄漏」的兜底，泄漏本身已经说明生命周期没被接住；
 * 多留 3 tick 只会推迟声道回收。
 */
public final class BeamEnvelope {

    /** 起音/收尾的最短时长（tick）。150 ms @ 20 Hz。 */
    public static final int FADE_TICKS = 3;
    /** 未收到存续信号的容忍上限（tick）。 */
    public static final int WATCHDOG_TICKS = 10;
    /** 振幅的感知曲线指数。 */
    public static final double PERCEPTUAL_EXPONENT = 1.7D;

    /** 音高斜坡起点（相对基准音高）。 */
    public static final double PITCH_FROM = 0.85D;

    /**
     * 引擎可接受的最小振幅。
     *
     * <p>{@code SoundEngine.play} 在 {@code getVolume() == 0} 时直接跳过实例
     * （日志 "Skipped playing sound ..., volume was zero"）。包络的起音本来从 0 开始，
     * 若原样交给引擎，循环音会在入队前被丢弃 —— 表现为「持续音完全没有声音」。
     * 实际播放时用 {@link #playableAmplitude(double)} 给引擎一个极小非零值。
     */
    public static final double MIN_PLAYABLE_AMPLITUDE = 1.0E-4D;

    /** 把包络振幅夹到引擎可接受的非零下限；第一 tick 约 -80 dB，听不见。 */
    public static double playableAmplitude(double amplitude) {
        return Math.max(MIN_PLAYABLE_AMPLITUDE, amplitude);
    }

    private enum Phase {
        ATTACK,
        SUSTAIN,
        RELEASE,
        STOPPED
    }

    private final boolean pitchRamp;

    private Phase phase = Phase.ATTACK;
    private int attackAge;
    private int releaseAge;
    private int silentTicks;
    private double releaseFrom = 1.0D;

    public BeamEnvelope(boolean pitchRamp) {
        this.pitchRamp = pitchRamp;
    }

    /** 消费方每 tick 的「仍在持续」信号。 */
    public void signal() {
        silentTicks = 0;
    }

    /** 请求正常收尾（3 tick 淡出）。重复调用幂等。 */
    public void requestStop() {
        if (phase == Phase.STOPPED || phase == Phase.RELEASE) {
            return;
        }
        releaseFrom = linearProgress();
        releaseAge = 0;
        phase = Phase.RELEASE;
    }

    /** 推进一个客户端 tick。 */
    public void advance() {
        if (phase == Phase.STOPPED) {
            return;
        }
        silentTicks++;
        if (silentTicks >= WATCHDOG_TICKS) {
            phase = Phase.STOPPED;
            return;
        }
        switch (phase) {
            case ATTACK -> {
                attackAge++;
                if (attackAge >= FADE_TICKS) {
                    attackAge = FADE_TICKS;
                    phase = Phase.SUSTAIN;
                }
            }
            case RELEASE -> {
                releaseAge++;
                if (releaseAge >= FADE_TICKS) {
                    phase = Phase.STOPPED;
                }
            }
            case SUSTAIN, STOPPED -> {
                // 无状态推进
            }
        }
    }

    public boolean stopped() {
        return phase == Phase.STOPPED;
    }

    /** 线性进度 0..1（感知曲线之前的「位置」）。 */
    public double linearProgress() {
        return switch (phase) {
            case ATTACK -> Math.min(1.0D, attackAge / (double) FADE_TICKS);
            case SUSTAIN -> 1.0D;
            case RELEASE -> releaseFrom * (FADE_TICKS - releaseAge) / (double) FADE_TICKS;
            case STOPPED -> 0.0D;
        };
    }

    /** 实际振幅 0..1。 */
    public double amplitude() {
        return Math.pow(linearProgress(), PERCEPTUAL_EXPONENT);
    }

    /**
     * 音高倍率（相对 cue 的基准音高）。
     *
     * <p>起音阶段 0.85 → 1.0，之后恒为 1.0；cue 未开启斜坡时恒为 1.0。
     */
    public double pitchFactor() {
        if (!pitchRamp) {
            return 1.0D;
        }
        return switch (phase) {
            case ATTACK -> PITCH_FROM + (1.0D - PITCH_FROM) * (attackAge / (double) FADE_TICKS);
            case SUSTAIN, RELEASE, STOPPED -> 1.0D;
        };
    }
}
