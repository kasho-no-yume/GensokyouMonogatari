package com.bitsson.gensokyou.audio;

import com.bitsson.gensokyou.Gensokyou;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;

/**
 * 一条音效 cue 的<b>完整描述</b>。
 *
 * <p>混音参数写在这里，不写在调用点：同一条 cue 在十个地方播放，音量必须相同，
 * 而「写入注册表」是唯一能保证这件事的结构。
 *
 * <p><b>为什么占位音效存 {@link ResourceLocation} 而不是 {@code SoundEvent}</b>：
 * 存 {@code SoundEvent} / 引用 {@code SoundEvents} 会把 Minecraft 类拖进本枚举的静态初始化，
 * 纯 JUnit（无 registry bootstrap）就无法加载它。id 由 {@link SoundLookup} 在实现侧解析，
 * 测试侧可以模拟「解析失败」来验证降级。
 *
 * <p><b>本枚举不出声。</b>它只描述参数；真正的播放由 {@link Audio} + {@link AudioSink} 完成。
 */
public enum AudioCue {

    SHOT_NORMAL("shot_normal", 1.0F, 1.0F, 0.06F, 0,
            SoundSource.PLAYERS, true,
            "subtitles.gensokyou.shot_normal", "minecraft:entity.arrow.shoot",
            false, false),

    SHOT_FAST("shot_fast", 0.55F, 1.0F, 0.10F, 2,
            SoundSource.PLAYERS, true,
            "subtitles.gensokyou.shot_fast", "minecraft:entity.snowball.throw",
            false, false),

    KNIFE_THROW("knife_throw", 0.9F, 1.0F, 0.08F, 0,
            SoundSource.PLAYERS, true,
            "subtitles.gensokyou.knife_throw", "minecraft:entity.trident.throw",
            false, false),

    LASER_SHOOT("laser_shoot", 0.95F, 1.0F, 0.05F, 0,
            SoundSource.PLAYERS, true,
            "subtitles.gensokyou.laser_shoot", "minecraft:entity.blaze.shoot",
            false, false),

    LASER_BEAM("laser_beam", 0.75F, 1.0F, 0.0F, 0,
            SoundSource.PLAYERS, true,
            "subtitles.gensokyou.laser_beam", "minecraft:block.beacon.ambient",
            false, true),

    BOSS_BURST("boss_burst", 0.8F, 1.0F, 0.05F, 3,
            SoundSource.HOSTILE, true,
            "subtitles.gensokyou.boss_burst", "minecraft:entity.dragon_fireball.explode",
            false, false),

    WARNING_SHIFT("warn_shift", 1.0F, 1.0F, 0.0F, 0,
            SoundSource.PLAYERS, true,
            "subtitles.gensokyou.warn_shift", "minecraft:block.note_block.pling",
            true, false),

    SPELLCARD_SWITCH("spellcard_switch", 1.0F, 1.0F, 0.0F, 0,
            SoundSource.PLAYERS, false,
            "subtitles.gensokyou.spellcard_switch", "minecraft:entity.ender_dragon.growl",
            true, false);

    private final ResourceLocation eventId;
    private final float baseVolume;
    private final float basePitch;
    private final float pitchJitter;
    private final int minIntervalTicks;
    private final SoundSource source;
    private final boolean positional;
    private final String subtitleKey;
    private final ResourceLocation placeholderId;
    private final boolean informational;
    private final boolean pitchRamp;

    AudioCue(String path, float baseVolume, float basePitch, float pitchJitter,
             int minIntervalTicks, SoundSource source, boolean positional,
             String subtitleKey, String placeholderId, boolean informational,
             boolean pitchRamp) {
        this.eventId = Gensokyou.id(path);
        this.baseVolume = baseVolume;
        this.basePitch = basePitch;
        this.pitchJitter = pitchJitter;
        this.minIntervalTicks = minIntervalTicks;
        this.source = source;
        this.positional = positional;
        this.subtitleKey = subtitleKey;
        this.placeholderId = ResourceLocation.parse(placeholderId);
        this.informational = informational;
        this.pitchRamp = pitchRamp;
    }

    /** 自制声音事件（已由 {@code ModSounds} 注册）。 */
    public ResourceLocation eventId() {
        return eventId;
    }

    public float baseVolume() {
        return baseVolume;
    }

    public float basePitch() {
        return basePitch;
    }

    /** 音高抖动的半幅；实际音高在 {@code basePitch ± pitchJitter} 内取值。 */
    public float pitchJitter() {
        return pitchJitter;
    }

    public int minIntervalTicks() {
        return minIntervalTicks;
    }

    public SoundSource source() {
        return source;
    }

    public boolean positional() {
        return positional;
    }

    public String subtitleKey() {
        return subtitleKey;
    }

    /** 目标资源不可用时的原版占位音效。 */
    public ResourceLocation placeholderId() {
        return placeholderId;
    }

    /** 信息类 cue：不占用每 tick 的全局新增额度。 */
    public boolean informational() {
        return informational;
    }

    /** 持续音是否启用 0.85 → 1.0 的音高斜坡。 */
    public boolean pitchRamp() {
        return pitchRamp;
    }
}
