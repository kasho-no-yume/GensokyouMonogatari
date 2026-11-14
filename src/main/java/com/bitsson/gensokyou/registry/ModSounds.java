package com.bitsson.gensokyou.registry;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.audio.SoundIds;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 模组自制音效事件注册表。
 *
 * <p><b>为什么必须有这一层</b>：{@code sounds.json} 只描述「事件 id → 音频文件」，
 * 事件本身仍须注册进 {@code BuiltInRegistries.SOUND_EVENT}。没有注册的 id 在注册表里
 * 查不到，{@code AudioResolver} 会判定为不可用 —— 表现为「资源文件明明在，却永远静音」。
 *
 * <p>id 清单的唯一来源是 {@link SoundIds#NAMES}（纯字符串）。本类刻意<b>不</b>自带
 * 字符串表，也不引用 {@code audio} 包的其它内容：依赖方向保持
 * {@code registry -> audio.SoundIds}，而 {@code AudioCues} 也只看纯字符串清单，
 * 于是纯 JUnit 不需要 bootstrap 注册表。
 */
public final class ModSounds {

    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(Registries.SOUND_EVENT, Gensokyou.MODID);

    /** 注册名 → 事件 holder。 */
    public static final Map<String, DeferredHolder<SoundEvent, SoundEvent>> ALL;

    static {
        Map<String, DeferredHolder<SoundEvent, SoundEvent>> holders = new LinkedHashMap<>();
        for (String name : SoundIds.NAMES) {
            holders.put(name, SOUNDS.register(name,
                    () -> SoundEvent.createVariableRangeEvent(Gensokyou.id(name))));
        }
        ALL = Collections.unmodifiableMap(holders);
    }

    private ModSounds() {
    }
}
