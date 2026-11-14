package com.bitsson.gensokyou.audio;

import com.bitsson.gensokyou.audio.bossbgm.BgmFade;

/**
 * 一首正在播放的背景音乐的句柄。
 *
 * <p>与 {@link BeamHandle} 同形：消费方每 tick {@link #keepAlive()}，
 * 需要结束时 {@link #stop()}（触发 2.5 秒淡出）。忘掉两者也不会永久占用声道 ——
 * {@link BgmFade} 的看门狗会在 60 tick 后自停。
 *
 * <p>淡变状态保存在句柄里（而不是播放后端），因此后端为 no-op 时句柄行为依旧确定，
 * 测试可直接断言"该淡出了"。
 */
public final class MusicHandle {

    private final BgmFade fade;

    public MusicHandle(BgmFade fade) {
        this.fade = fade == null ? new BgmFade() : fade;
    }

    /** 本 tick 仍然需要这首音乐。 */
    public void keepAlive() {
        fade.signal();
    }

    /** 开始淡出。重复调用幂等。 */
    public void stop() {
        fade.requestStop();
    }

    public boolean stopped() {
        return fade.stopped();
    }

    public BgmFade fade() {
        return fade;
    }
}
