package com.bitsson.gensokyou.audio;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * 「目标缺失 → 占位 → 静默」的降级裁决。
 *
 * <p>拆成独立类是为了可离线断言：测试注入一个「目标解析不到 / 占位解析得到」
 * 的 {@link SoundLookup}，就能验证请求确实落到占位；两边都解析不到时，
 * 日志只会出现一次，且不抛异常。
 *
 * <p>{@link #vanilla(Consumer)} 是生产侧入口：查 {@code BuiltInRegistries.SOUND_EVENT}。
 * 该注册表在客户端与服务端都存在，因此本类可以留在 common 包。
 */
public final class AudioResolver {

    private final SoundLookup lookup;
    private final Consumer<String> logger;
    private final Set<AudioCue> reportedMissing = EnumSet.noneOf(AudioCue.class);

    public AudioResolver(SoundLookup lookup, Consumer<String> logger) {
        this.lookup = lookup;
        this.logger = logger;
    }

    /** 生产侧：按注册表解析。 */
    public static AudioResolver vanilla(Consumer<String> logger) {
        return new AudioResolver(id -> BuiltInRegistries.SOUND_EVENT.getOptional(id), logger);
    }

    /**
     * 解析本轮实际要播放的事件。
     *
     * @return 目标事件；目标不可用时返回登记的占位；两者都不可用时返回 {@code null}
     *         （静默，且每个 cue 只记一次日志）
     */
    @Nullable
    public SoundEvent resolve(AudioCue cue) {
        Optional<SoundEvent> direct = lookup.resolve(cue.eventId());
        if (direct.isPresent()) {
            return direct.get();
        }
        Optional<SoundEvent> placeholder = lookup.resolve(cue.placeholderId());
        if (placeholder.isPresent()) {
            return placeholder.get();
        }
        if (reportedMissing.add(cue)) {
            logger.accept("cue " + cue.eventId()
                    + " 的目标与占位音效均不可用；本次静默（每个 cue 只报告一次）");
        }
        return null;
    }

    /** 已经报告过「目标与占位都缺失」的 cue（测试用）。 */
    public Set<AudioCue> reportedMissing() {
        return Set.copyOf(reportedMissing);
    }

    public void reset() {
        reportedMissing.clear();
    }
}
