package com.bitsson.gensokyou.audio.bossbgm;

import com.bitsson.gensokyou.Gensokyou;
import net.minecraft.resources.ResourceLocation;

/**
 * 一首内置 BOSS 战 BGM 的<b>描述体</b>：一条阶事件 id + 它实际播放的音频路径 + 基准音量。
 *
 * <p>与 {@link com.bitsson.gensokyou.audio.AudioCue} 刻意分开：cue 描述的是
 * <b>一次性音效</b>（限流间隔、音高抖动、字幕、占位降级），音乐这四项全是反向的。
 * 把音乐塞进 cue 会让「每 tick 新增上限」开始统计音乐，也会逼音乐带一条没有意义的字幕键。
 *
 * <p>本枚举不出声，只描述参数；播放由 {@link com.bitsson.gensokyou.audio.Audio}
 * 与 {@link com.bitsson.gensokyou.audio.AudioSink} 完成。
 */
public enum BgmTrack {

    TIER1(1, "bgm/tier1", 0.85F),
    TIER2(2, "bgm/tier2", 0.85F),
    TIER3(3, "bgm/tier3", 0.85F),
    TIER4(4, "bgm/tier4", 0.85F),
    TIER5(5, "bgm/tier5", 0.85F);

    private final int tier;
    private final ResourceLocation eventId;
    private final ResourceLocation audioPath;
    private final float volume;

    BgmTrack(int tier, String path, float volume) {
        this.tier = tier;
        this.eventId = Gensokyou.id(path);
        this.audioPath = Gensokyou.id(path);
        this.volume = volume;
    }

    public int tier() {
        return tier;
    }

    /** 缺省的阶事件 id（`gensokyou:bgm/tierN`）。 */
    public ResourceLocation eventId() {
        return eventId;
    }

    /** 实际音频文件路径（与事件 id 路径一致；BOSS 事件会指向同一个文件）。 */
    public ResourceLocation audioPath() {
        return audioPath;
    }

    /** 基准音量；响度已在素材侧归一，故五首共用同一个值。 */
    public float volume() {
        return volume;
    }

    /** 越界/未知阶级回落到 1 阶（对齐 boss-tier 的越界回退语义）。 */
    public static BgmTrack forTier(int tier) {
        return switch (tier) {
            case 2 -> TIER2;
            case 3 -> TIER3;
            case 4 -> TIER4;
            case 5 -> TIER5;
            default -> TIER1;
        };
    }
}
