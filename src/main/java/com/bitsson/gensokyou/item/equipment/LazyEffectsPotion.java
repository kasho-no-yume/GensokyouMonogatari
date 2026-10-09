package com.bitsson.gensokyou.item.equipment;

import com.bitsson.gensokyou.Gensokyou;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.alchemy.Potion;

/**
 * 效果在<b>首次读取时</b>才从注册表解析的药水条目。
 *
 * <p><b>为什么必须惰性</b>：{@code MobEffectInstance} 的构造函数里有
 * {@code this.effect.value().fillEffectCures(...)}——它当场解引用 {@code Holder<MobEffect>}。
 * 而 {@code Potion} 的注册事件排在 {@code MobEffect} <b>之后</b>执行时不确定：
 * 一旦 {@code Registry<POTION>} 的 supplier 先跑，这里拿到的还是未绑定的
 * {@code DeferredHolder}，直接抛 {@code IllegalStateException: Trying to access unbound value}。
 * 把效果列表推迟到 {@link #getEffects()}（运行时，注册必然已完成）即可彻底绕开该顺序依赖。
 *
 * <p><b>为什么用继承而不是反射塞字段</b>：{@code Potion.effects} 是 {@code private final}，
 * 反射写既脆又要 {@code --add-opens}。{@code Potion#getEffects()} 非 final、{@code Potion}
 * 也没有 hashCode/equals（走对象 identity），覆写它是安全的。
 */
public final class LazyEffectsPotion extends Potion {

    private final ResourceLocation effectId;
    private final int duration;
    private final int amplifier;

    private List<MobEffectInstance> resolved;

    public LazyEffectsPotion(ResourceLocation effectId, int duration, int amplifier) {
        // 空效果构造：Holder 一个都不碰，注册期零依赖
        super((String) null);
        this.effectId = effectId;
        this.duration = duration;
        this.amplifier = amplifier;
    }

    @Override
    public List<MobEffectInstance> getEffects() {
        if (resolved == null) {
            resolved = List.of(new MobEffectInstance(lookupEffect(), duration, amplifier));
        }
        return resolved;
    }

    /** 注册表查找。运行期调用故必然已绑定；查不到说明数据与代码脱节，直接炸比静默空药水好。 */
    private Holder<MobEffect> lookupEffect() {
        return BuiltInRegistries.MOB_EFFECT
                .getHolder(ResourceKey.create(Registries.MOB_EFFECT, effectId))
                .orElseThrow(() -> new IllegalStateException(
                        "mob effect not registered: " + effectId));
    }

    /** 便捷工厂：把路径片段补成 {@code gensokyou:<path>}。 */
    public static LazyEffectsPotion of(String path, int duration, int amplifier) {
        return new LazyEffectsPotion(Gensokyou.id(path), duration, amplifier);
    }
}
