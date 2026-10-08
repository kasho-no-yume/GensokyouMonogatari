package com.bitsson.gensokyou.ritual;

import com.bitsson.gensokyou.ritual.RitualBehaviorState;
import com.bitsson.gensokyou.ritual.SpiritBank;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

/**
 * 行为访问核心宿主的**通用**能力面。
 *
 * <p>只包含所有仪式共有的宿主服务：灵力账户（缓存四件套）、路由端点、启停生命周期、
 * 灵力核心槽、祭品台/额外槽宿主存储、per-core 状态取用口与脏标记。
 *
 * <p>仪式专属的东西（会话推进、渲染态/逐帧动画、灯坛/灵浴/无尽藏/献祭/结界等）
 * MUST NOT 进本接口——由各行为在自己的 {@link RitualBehaviorState} 与实现里自管。
 *
 * <p>打断 {@code block.entity} ↔ {@code ritual.behavior} 双向 import 的关键：行为只依赖本接口，
 * {@code RitualCoreBlockEntity} 实现之。
 */
public interface SpiritPowerAccess {

    // ---- 灵力账户（缓存四件套）----

    long getStored();

    long getCapacity();

    long receive(long maxAmount);

    long extract(long maxAmount);

    // ---- 路由端点（万象共鸣，通用宿主设施）----

    long extractRouted(long maxAmount);

    long receiveRouted(long maxAmount);

    long routedInTotal();

    long routedOutTotal();

    void clearRoutedLedgers();

    /** 托管储灵（八方归元等）；无则 null。 */
    @Nullable
    SpiritBank bank();

    // ---- 启停生命周期 / 宿主查询 ----

    boolean isEnabled();

    void setEnabled(boolean value);

    long ageTicks();

    @Nullable
    RitualMatch activeMatch();

    boolean isPattern(ResourceLocation id);

    @Nullable
    ResourceLocation activeRecipeId();

    void setActiveRecipeId(@Nullable ResourceLocation recipeId);

    BlockPos getBlockPos();

    @Nullable
    Level getLevel();

    // ---- 灵力核心槽（通用）----

    ItemStack batteryStack();

    void setBatteryStack(ItemStack stack);

    /** 电池核心 → 缓存（受核心注灵速率限速）；返回实搬运量。 */
    long tickBatteryToCacheFill();

    /** 缓存 → 电池核心（受核心注灵速率限速）；返回实搬运量。 */
    long tickBatteryAutoFill();

    // ---- 通用定点进位累加器（产灵/注灵速率折算）----

    com.bitsson.gensokyou.ritual.FixedPointAccumulator rateCarryAccumulator();

    com.bitsson.gensokyou.ritual.FixedPointAccumulator fillCarryAccumulator();

    com.bitsson.gensokyou.ritual.FixedPointAccumulator cacheFillCarryAccumulator();

    // ---- 祭品台 / 额外槽宿主存储 ----

    void broadcastPedestalsActive(boolean active);

    ItemStack extraSlot(int slot);

    void setExtraSlot(int slot, ItemStack stack);

    // ---- per-core 状态取用口 + 脏标记 ----

    /** 当前激活行为的 per-core 状态（无状态行为返回 null）。 */
    @Nullable
    RitualBehaviorState behaviorState();

    /** 标记核心数据已变（触发保存/同步）。 */
    void markDirty();

    // ---- 共享宿主机制（带持久化/脏标记副作用，多条仪式共用）----

    void beginBurnBatch(ItemStack fuel, int totalTicks);

    boolean advanceBurnTick();

    void clearBurnBatch();

    ItemStack burnFuelIcon();

    int burnTotalTicks();

    int burnRemainingTicks();

    boolean isBurning();

    int actionCooldown();

    void setActionCooldown(int ticks);

    void triggerSacrificeFx(int ticks);

    /** 会话取消/中止退还：先回槽内灵力核心（不限速率），剩余直回核心自身储灵。 */
    void refundCached(long amount);

    @Nullable
    BlockPos portalPos();

    void setPortalPos(@Nullable BlockPos pos);

    boolean isBarrierLatched();

    void setBarrierLatched(boolean latched);

    java.util.List<RitualLink> inLinks();

    java.util.List<RitualLink> outLinks();

    void setSpiritLinks(java.util.List<RitualLink> in, java.util.List<RitualLink> out);

    void setResoMovingMask(long mask);

    long resoMovingMask();

    // ---- 结构几何 / 渲染辅助（宿主）----

    int structureMinY();

    int structureMaxY();

    /** 结构水平半径（格，向上取整）：核心到最远结构块的 XZ 距离。 */
    int structureRadiusXZ();

    /** 结构内祭品台位置（规范序）。 */
    java.util.List<BlockPos> pedestalPositions();

    /** 献祭光柱剩余渲染刻（0 = 无）。 */
    int sacrificeFxTicks();
}
