package com.bitsson.gensokyou.ritual;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

/**
 * 每核（per-core）的仪式专属状态容器。
 *
 * <p>行为在 {@code RitualBehaviors} 中是 per-pattern 单例，故 per-core 状态 MUST NOT 存于行为实例。
 * 由行为通过 {@link com.bitsson.gensokyou.ritual.RitualBehavior#newState()} 定义子类，
 * {@code RitualCoreBlockEntity} 持有其实例（{@code Map<patternId, RitualBehaviorState>}）。
 *
 * <p>序列化由状态对象自身负责（键族以 patternId 命名空间隔离，沿用历史扁平键以兼容旧档）；
 * 世界无关、可单测。需要在 NBT 中序列化 {@code ItemStack} 的状态（如金谷冶炼）覆写带
 * {@link HolderLookup.Provider} 的重载。
 */
public abstract class RitualBehaviorState {

    /** 结构失效 / 图案切换时清退到初始态。 */
    public abstract void clear();

    /** 写入本状态专属 NBT 段（缺省无数据时可 no-op）。 */
    public void save(CompoundTag tag) {
    }

    /** 读取本状态专属 NBT 段（缺段即保持默认）。 */
    public void load(CompoundTag tag) {
    }

    /** 需要注册表（ItemStack 等）时的写入重载；默认转发到无注册表版。 */
    public void save(CompoundTag tag, HolderLookup.Provider registries) {
        save(tag);
    }

    /** 需要注册表时的读取重载；默认转发到无注册表版。 */
    public void load(CompoundTag tag, HolderLookup.Provider registries) {
        load(tag);
    }

    /** 是否为空状态（用于读档后丢弃无数据的实例）。默认 false（一律保留）。 */
    public boolean isEmpty() {
        return false;
    }
}
