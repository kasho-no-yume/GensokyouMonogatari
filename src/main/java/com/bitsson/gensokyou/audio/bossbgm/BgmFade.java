package com.bitsson.gensokyou.audio.bossbgm;

import com.bitsson.gensokyou.audio.BeamEnvelope;

/**
 * 音乐的淡入 / 常驻 / 淡出（纯状态机，可离线断言）。
 *
 * <p>与 {@link BeamEnvelope} 同形，但段长按<b>秒</b>计而不是按 tick：
 * 激光的 3 tick 起音、12 tick 收尾被实机调过，直接改它的常量会动到已归档的验收，
 * 因此这里独立成类。曲线与「引擎非零下限」则是同一份实现 —— 曲线指数与
 * {@link BeamEnvelope#PERCEPTUAL_EXPONENT} 共用，避免出现两份 {@code pow(p, 1.7)}。
 *
 * <h2>为什么淡变不能短于 2 秒</h2>
 * 客户端音量更新是 20 Hz；音乐本来就该"从远处飘过来"，短于 40 tick 的淡入
 * 听感像被切进去。淡出同理，硬切会让符卡切换的余音被掐断。
 *
 * <h2>看门狗</h2>
 * 连续 {@link #WATCHDOG_TICKS} tick 未收到 {@link #signal()} 即停止。
 * 它必须长于淡出时长，否则看门狗会抢在淡出完成前把实例砍掉。
 */
public final class BgmFade {

    /** 淡入时长（tick）：2 秒 @ 20 Hz。 */
    public static final int FADE_IN_TICKS = 40;
    /** 淡出时长（tick）：2.5 秒 @ 20 Hz。 */
    public static final int FADE_OUT_TICKS = 50;
    /** 存续信号容忍上限：必须 > {@link #FADE_OUT_TICKS}，否则淡出被截断。 */
    public static final int WATCHDOG_TICKS = 60;

    /** 引擎可接受的最小振幅（与持续音共用同一份下限）。 */
    public static double playableAmplitude(double amplitude) {
        return BeamEnvelope.playableAmplitude(amplitude);
    }

    /** 感知曲线（振幅，不是"音量"）。 */
    public static double perceptual(double linearProgress) {
        double p = Math.max(0.0D, Math.min(1.0D, linearProgress));
        return Math.pow(p, BeamEnvelope.PERCEPTUAL_EXPONENT);
    }

    private enum Phase {
        ATTACK,
        SUSTAIN,
        RELEASE,
        STOPPED
    }

    private final int fadeInTicks;
    private final int fadeOutTicks;

    private Phase phase = Phase.ATTACK;
    private int attackAge;
    private int releaseAge;
    private int silentTicks;
    private double releaseFrom = 1.0D;

    public BgmFade() {
        this(FADE_IN_TICKS, FADE_OUT_TICKS);
    }

    public BgmFade(int fadeInTicks, int fadeOutTicks) {
        this.fadeInTicks = Math.max(1, fadeInTicks);
        this.fadeOutTicks = Math.max(1, fadeOutTicks);
    }

    /** 消费方每 tick 的「仍在持续」信号。 */
    public void signal() {
        silentTicks = 0;
    }

    /** 请求淡出；重复调用幂等，且不会把淡出重新拉满。 */
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
                if (attackAge >= fadeInTicks) {
                    attackAge = fadeInTicks;
                    phase = Phase.SUSTAIN;
                }
            }
            case RELEASE -> {
                releaseAge++;
                if (releaseAge >= fadeOutTicks) {
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

    /** 是否已经进入常驻（淡入完成）。 */
    public boolean sustained() {
        return phase == Phase.SUSTAIN || phase == Phase.RELEASE;
    }

    /** 线性进度 0..1（感知曲线之前的位置）。 */
    public double linearProgress() {
        return switch (phase) {
            case ATTACK -> Math.min(1.0D, attackAge / (double) fadeInTicks);
            case SUSTAIN -> 1.0D;
            case RELEASE -> releaseFrom * (fadeOutTicks - releaseAge) / (double) fadeOutTicks;
            case STOPPED -> 0.0D;
        };
    }

    /** 实际振幅 0..1。 */
    public double amplitude() {
        return perceptual(linearProgress());
    }

    /** 交给声音引擎前必须夹到非零下限（{@code getVolume() == 0} 会被直接丢弃）。 */
    public double playableAmplitude() {
        return playableAmplitude(amplitude());
    }
}
