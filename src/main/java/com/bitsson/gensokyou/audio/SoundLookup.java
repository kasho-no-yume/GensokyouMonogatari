package com.bitsson.gensokyou.audio;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

import java.util.Optional;

/**
 * 声音事件 id 的解析口。
 *
 * <p>存在的唯一理由：让 {@link AudioCue} 与 {@link AudioResolver} 不直接触碰
 * {@code BuiltInRegistries}。生产实现查注册表，测试实现可以返回空，
 * 于是「目标缺失 → 占位」这条降级路径能离线断言。
 */
@FunctionalInterface
public interface SoundLookup {

    Optional<SoundEvent> resolve(ResourceLocation id);

    /** 什么都解析不出来：用于「专用服务器 / 未安装后端」的默认状态。 */
    SoundLookup NONE = id -> Optional.empty();
}
