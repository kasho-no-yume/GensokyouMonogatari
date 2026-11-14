package com.bitsson.gensokyou.audio;

import java.util.List;

/**
 * 自制声音事件的 <b>id 清单</b>（纯字符串，无任何 Minecraft 注册表依赖）。
 *
 * <p>拆出来的原因很具体：{@code DeferredRegister.register(...)} 会构造
 * {@code DeferredHolder}，进而触发 {@code BuiltInRegistries} 的静态初始化，
 * 在纯 JUnit 下直接抛 {@code Not bootstrapped}。而 {@code AudioCues.validate()}
 * 与一致性测试都需要这份清单。
 *
 * <p>于是依赖方向是：{@code registry.ModSounds -> audio.SoundIds <- audio.AudioCues}，
 * 两边都只依赖纯字符串，注册表实现不再被测试拖进类加载。
 */
public final class SoundIds {

    /** 全部自制声音事件（注册名，同时是事件 id 的 path）。 */
    public static final List<String> NAMES = List.of(
            "shot_normal",
            "shot_fast",
            "knife_throw",
            "laser_shoot",
            "laser_beam",
            "boss_burst",
            "warn_shift",
            "spellcard_switch");

    private SoundIds() {
    }
}
