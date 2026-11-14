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

    /**
     * 背景音乐事件（注册名 = 事件 id 的 path）。
     *
     * <p>与 {@link #NAMES} 分开维护是硬要求：一次性音效的清单要与 {@code AudioCues}
     * 的事件 id 集合<b>相等</b>，音乐一旦混进去，那条断言就会变成"音乐也算 cue"。
     *
     * <p>内容必须与 {@code BossBgms.musicEventPaths()} 逐项一致（由
     * {@code BossBgms.validate()} 与 {@code BossBgmCatalogTest} 双向断言）。
     */
    public static final List<String> MUSIC_NAMES = List.of(
            "bgm/tier1",
            "bgm/tier2",
            "bgm/tier3",
            "bgm/tier4",
            "bgm/tier5",
            "bgm/boss/big_fairy",
            "bgm/boss/rumia",
            "bgm/boss/yamame",
            "bgm/boss/yakumo_ran",
            "bgm/boss/flandre",
            "bgm/boss/fake_flandre");

    private SoundIds() {
    }
}
