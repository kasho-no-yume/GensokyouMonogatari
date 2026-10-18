package com.bitsson.gensokyou.danmaku.program.gameplay;

import com.bitsson.gensokyou.danmaku.program.network.NetworkLimits;
import com.bitsson.gensokyou.danmaku.program.server.DanmakuWorldRuntime;

/**
 * 正式 BOSS 共用的公共宿主设置。
 *
 * <p>同一维度的所有战斗 MUST 使用同一份设置（{@code BossProgramHost.open} 会校验相等），
 * 因此这里只保留一个不可变实例，不按 BOSS 定制。
 */
public final class BossProgramSettings {
    private BossProgramSettings() {}

    /** 同时容纳花海、山女尾珠与蓝八卡的最坏峰值；超限由运行期预算拒绝而不是崩溃。 */
    private static final DanmakuWorldRuntime.Limits LIMITS =
            new DanmakuWorldRuntime.Limits(8192, 512, 512, 512, 40000);

    private static final BossProgramHost.Settings STANDARD = new BossProgramHost.Settings(
            LIMITS, 512, NetworkLimits.defaults(), 32, 20, 256, 128);

    public static BossProgramHost.Settings standard() { return STANDARD; }
}
