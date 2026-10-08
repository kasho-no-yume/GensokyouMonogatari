package com.bitsson.gensokyou.block.entity;

import com.bitsson.gensokyou.block.RitualPedestalBlock;
import com.bitsson.gensokyou.event.AchievementAwards;
import com.bitsson.gensokyou.config.GensokyouConfig;
import com.bitsson.gensokyou.network.ModNetworking;
import com.bitsson.gensokyou.registry.ModBlockEntities;
import com.bitsson.gensokyou.registry.ModBlocks;
import com.bitsson.gensokyou.ritual.RitualBehavior;
import com.bitsson.gensokyou.ritual.RitualBehaviors;
import com.bitsson.gensokyou.ritual.RitualExtraSlots;
import com.bitsson.gensokyou.ritual.RitualMatch;
import com.bitsson.gensokyou.ritual.RitualMatcher;
import com.bitsson.gensokyou.ritual.RitualCoreRegistry;
import com.bitsson.gensokyou.ritual.RitualLink;
import com.bitsson.gensokyou.ritual.RitualOfferings;
import com.bitsson.gensokyou.ritual.RitualPattern;
import com.bitsson.gensokyou.ritual.RitualPatternLoader;
import com.bitsson.gensokyou.ritual.RitualPedestals;
import com.bitsson.gensokyou.ritual.RitualRecipe;
import com.bitsson.gensokyou.ritual.RitualRecipeLoader;
import com.bitsson.gensokyou.ritual.RitualRecipeMatcher;
import com.bitsson.gensokyou.ritual.RitualRenderState;
import com.bitsson.gensokyou.ritual.SpiritBank;
import com.bitsson.gensokyou.ritual.TickRateLedger;
import com.bitsson.gensokyou.ritual.KanayamahikoSmeltSession;
import com.bitsson.gensokyou.ritual.CraftSession;
import com.bitsson.gensokyou.ritual.SummonSession;
import com.bitsson.gensokyou.ritual.GraceSession;
import com.bitsson.gensokyou.ritual.CraftPhase;
import com.bitsson.gensokyou.ritual.SummonPhase;
import com.bitsson.gensokyou.ritual.GracePhase;
import com.bitsson.gensokyou.ritual.BousenState;
import com.bitsson.gensokyou.ritual.ReiyokuState;
import com.bitsson.gensokyou.ritual.WujinzangState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import com.bitsson.gensokyou.spirit.SpiritPowerHelper;
import net.neoforged.neoforge.items.IItemHandler;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class RitualCoreBlockEntity extends BlockEntity
        implements com.bitsson.gensokyou.ritual.SpiritPowerAccess {
    private static final String TAG_STORED = "StoredSpiritPower";
    private static final String TAG_RESO_IN = "ResoInLinks";
    private static final String TAG_RESO_OUT = "ResoOutLinks";
    private static final String TAG_RENDER_STATE = "Rendu";
    private static final String TAG_LINK_POS = "P";
    private static final String TAG_LINK_PATTERN = "I";
    private static final String TAG_ENABLED = "Enabled";
    /** 旧版结界引爆开关字段：收编为 enabled 的存档兼容读。 */
    private static final String TAG_LEGACY_BARRIER_ACTIVATED = "BarrierActivated";
    private static final String TAG_PORTAL_POS = "PortalPos";
    /** 结界闩锁（独立于 Enabled；缺字段的旧存档按未闩锁处理）。 */
    private static final String TAG_BARRIER_LATCHED = "BarrierLatched";
    private static final String TAG_ACTIVE_RECIPE = "ActiveRecipe";
    private static final String TAG_BATTERY = "SpiritCoreBattery";
    /** 星移之仪的增幅核目标槽（与祭品台分开，避免占用催化剂台位）。 */
    private static final String TAG_SEII_TARGET = "SeiiTargetCore";

    /** 泛化额外槽的落盘键（ListTag，每项一个 ItemStack）。 */
    private static final String TAG_EXTRA_SLOTS = "ExtraSlots";
    private static final String TAG_BURN = "KagutsuchiBurn";
    private static final String TAG_BURN_FUEL = "Fuel";
    private static final String TAG_BURN_TOTAL = "TotalTicks";
    private static final String TAG_BURN_REMAINING = "RemainingTicks";
    private static final String TAG_RATE_ACCUM = "RateAccum";
    private static final String TAG_FILL_ACCUM = "FillAccum";
    private static final String TAG_LAST_POWERED = "LastPowered";
    /** 献祭仪式：结算后强制冷却剩余 tick（通用字段，仅该行为族使用）。 */
    private static final String TAG_ACTION_COOLDOWN = "ActionCooldown";

    // ---- 百鬼夜行召唤会话（hyakki-yagyo-summon）----


    /** 无尽藏之仪托管数据段键：分区组表 + 孤儿段 + 段位坐标（由 {@code WujinzangStorage} 读写）。 */
    public static final String TAG_WUJINZANG_VAULT = "WujinzangVault";



    private RitualMatch activeMatch;
    private long ageTicks;
    private long storedSpiritPower;
    private boolean enabled;
    private BlockPos portalPos;
    /**
     * 结界破坏仪式的闩锁：一经置位即需跨结构失配存续（拆方块又补回来传送门必须仍是开的）。
     * <p>MUST NOT 复用 {@code enabled}——框架在 activeMatch 失效时会把 enabled 归零。
     * 仅在「核心被破坏」或「该核心变为其他仪式」时清除（见 BarrierBreakBehavior.onStructureLost
     * 与 RitualCoreBlock.onRemove）。
     */
    private boolean barrierLatched;
    /** 当前激活配方（配方驱动的仪式启动时记录，停止/失效清除）。 */
    private ResourceLocation activeRecipeId;
    /** 万象共鸣：输入/输出链接（身份 = 目标核心坐标 + 图案）。 */
    private List<RitualLink> inLinks = List.of();
    private List<RitualLink> outLinks = List.of();
    /** 万象共鸣：最近结算周期实搬通道位掩码（规范序同链接；链接变更即清零）。 */
    private long resoMovingMask;
    /** 渲染态：服务端=上次已推送态（变化比较基准），客户端=已接收态（只读绘制）。 */
    private @Nullable RitualRenderState lastSentRenderState;
    private RitualRenderState renderState = RitualRenderState.EMPTY;
    /** 万象共鸣：当前结构的 Y 包围盒（仅内存，重扫刷新；驱动螺旋高度）。 */
    private int boundsMinY;
    private int boundsMaxY;
    /** 产能仪式输出槽内灵力核心（单件，NBT 持久化；迦具土结算段/梦渡自发注灵共用）。 */
    private ItemStack batteryStack = ItemStack.EMPTY;
    /** 加具土命：当前燃烧批次的燃料显示图标（点火即吞，仅存身份，供 GUI 与掉落不回流）。 */
    private ItemStack burnFuelIcon = ItemStack.EMPTY;
    private int burnTotalTicks;
    private int burnRemainingTicks;
    /** 产灵/注灵速率的小数进位累加器（tick 级折算，避免整除截断）。 */
    private final com.bitsson.gensokyou.ritual.FixedPointAccumulator rateCarry = new com.bitsson.gensokyou.ritual.FixedPointAccumulator();
    private final com.bitsson.gensokyou.ritual.FixedPointAccumulator fillCarry = new com.bitsson.gensokyou.ritual.FixedPointAccumulator();
    /** 路由面向（万象共鸣）的端点每 tick 速率账本：唯一权威，gameTime 锁存；运行时态不持久化。 */
    private final TickRateLedger routedInLedger = new TickRateLedger();
    private final TickRateLedger routedOutLedger = new TickRateLedger();
    /** 路由实搬单调累计计数（不持久化，BE 重建归零）：实测吞吐差分的唯一数据源。 */
    private long routedInTotal;
    private long routedOutTotal;

    // ---- 造化合成会话（一次性合成型仪式的每核状态；推进逻辑在行为侧）----
    /** per-core 行为状态容器（键 = patternId；惰性创建，见各 *State() 访问器）。 */
    private final java.util.Map<ResourceLocation, com.bitsson.gensokyou.ritual.RitualBehaviorState> behaviorStates = new java.util.HashMap<>();

    /** 红石上升沿检测：上一拍邻居信号是否 >0（持久化，防重载后常亮信号误触发）。 */
    private boolean lastPowered;
    /** 献祭仪式：结算后强制冷却剩余 tick（每 tick 递减；持久化防重载连发）。 */
    private int actionCooldown;
    /** 献祭仪式：产出光柱剩余渲染刻（瞬态，仅驱动客户端 BER，不持久化）。 */
    private int sacrificeFxTicks;
    /** 无尽藏：电池核心→缓存的定点进位累加器（与产能方向的 fillCarry 分道）。 */
    private final com.bitsson.gensokyou.ritual.FixedPointAccumulator cacheFillCarry = new com.bitsson.gensokyou.ritual.FixedPointAccumulator();
    // 无尽藏 vault、灵浴 roster 与两级进位、忘川灯坛点亮态已下沉至各行为 state（见 *State() 访问器）。

    public RitualCoreBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.RITUAL_CORE.get(), pos, state);
    }

    /** 移除（含区块卸载/破坏）时注销索引条目，归属守卫防拆旧建新误删。 */
    @Override
    public void setRemoved() {
        clearRoutedLedgers();
        if (activeMatch != null && level instanceof ServerLevel serverLevel) {
            RitualBehaviors.get(activeMatch.patternId()).ifPresent(behavior ->
                    behavior.onRemoved(serverLevel, getBlockPos(), activeMatch, this));
            RitualCoreRegistry registry = RitualCoreRegistry.peek(serverLevel);
            if (registry != null) {
                registry.unregister(getBlockPos(), activeMatch.patternId());
            }
        }
        super.setRemoved();
    }

    public long ageTicks() {
        return ageTicks;
    }

    public RitualMatch activeMatch() {
        return activeMatch;
    }

    /** 献祭仪式冷却剩余 tick（0 = 就绪）。 */
    public int actionCooldown() {
        return actionCooldown;
    }

    /** 设置冷却剩余 tick（行为结算后调用；负值夹 0）。 */
    public void setActionCooldown(int ticks) {
        int value = Math.max(0, ticks);
        if (actionCooldown != value) {
            actionCooldown = value;
            setChanged();
        }
    }

    /** 无尽藏 per-core 状态（惰性创建；工厂由 WujinzangBehavior.newState 定义）。 */
    private WujinzangState wujinzangState() {
        return (WujinzangState) behaviorStates.computeIfAbsent(
                RitualBehaviors.WUJINZANG,
                key -> new WujinzangState());
    }

    /** 无尽藏之仪 vault 段（可为 null）；由 WujinzangStorage 读写。 */
    public CompoundTag getWujinzangVault() {
        return wujinzangState().rawVault();
    }

    /** 设置无尽藏 vault 段；null 清除。 */
    public void setWujinzangVault(CompoundTag vault) {
        wujinzangState().setVault(vault);
        setChanged();
    }

    /** 取 vault（不存在则新建并挂上）。 */
    public CompoundTag wujinzangVault() {
        return wujinzangState().vault();
    }

    /** 无尽藏：电池核心→缓存的定点进位余额（供 Vault 与持久化；通用设施，留 BE）。 */
    public long cacheFillCarry() {
        return cacheFillCarry.carry();
    }

    public void setCacheFillCarry(long value) {
        this.cacheFillCarry.setCarry(value);
    }

    /** 灵浴 per-core 状态（惰性创建；工厂由 ReiyokuBehavior.newState 定义）。 */
    private ReiyokuState reiyokuState() {
        return (ReiyokuState) behaviorStates.computeIfAbsent(
                RitualBehaviors.REIYOKU,
                key -> new ReiyokuState());
    }

    /** 灵浴：速率定点余数（×1000 口径）。 */
    public long reiyokuRateCarry() {
        return reiyokuState().rateCarry();
    }

    public void setReiyokuRateCarry(long value) {
        reiyokuState().setRateCarry(value);
    }

    /** 灵浴：整数点数在 N 名浴者间的均分余（与速率余数 MUST 分道）。 */
    public long reiyokuSplitCarry() {
        return reiyokuState().splitCarry();
    }

    public void setReiyokuSplitCarry(long value) {
        reiyokuState().setSplitCarry(value);
    }

    /** 灵浴：在浴人数（瞬态，由行为侧每 tick 重算）。 */
    public int reiyokuBatherCount() {
        return reiyokuState().batherCount();
    }

    public void setReiyokuBatherCount(int value) {
        reiyokuState().setBatherCount(value);
    }

    // ---- 忘川灯坛：蜡烛点亮态（瞬态，不进 NBT）----

    /** 忘川灯坛 per-core 状态（惰性创建；工厂由 BousenBehavior.newState 定义）。 */
    private BousenState bousenState() {
        return (BousenState) behaviorStates.computeIfAbsent(
                RitualBehaviors.BOUSEN,
                key -> new BousenState());
    }

    /** 蜡烛点亮位掩码（bit i = 规范序第 i 根点亮）；0 = 尚未重扫或无蜡烛。 */
    public long bousenLitMask() {
        return bousenState().litMask();
    }

    /** 当前点亮根数。 */
    public int bousenLitCount() {
        return bousenState().litCount();
    }

    /** 该阶蜡烛总数（16 / 32 / 64；0 = 尚未重扫或无蜡烛）。 */
    public int bousenLanternTotal() {
        return bousenState().lanternTotal();
    }

    /** 一次落定三件瞬态态（行为侧每秒重扫时整体写入）。 */
    public void setBousenLanterns(long mask, int litCount, int total) {
        bousenState().setLanterns(mask, litCount, total);
    }

    /** 灵浴：在浴名单文本（瞬态，供 GUI tooltip 零重扫读取）。 */
    public String reiyokuRosterText() {
        return reiyokuState().rosterText();
    }

    public void setReiyokuRosterText(String value) {
        reiyokuState().setRosterText(value);
    }

    /** 金谷冶炼 per-core 状态（惰性创建；工厂由 KanayamahikoBehavior.newState 定义）。 */
    private KanayamahikoSmeltSession kanayamahikoState() {
        return (KanayamahikoSmeltSession) behaviorStates.computeIfAbsent(
                RitualBehaviors.KANAYAMAHIKO,
                key -> new KanayamahikoSmeltSession());
    }

    public KanayamahikoSmeltSession kanayamahikoSession() {
        return kanayamahikoState();
    }

    public void clearKanayamahikoSession() {
        kanayamahikoState().clear();
        setChanged();
    }

    /** 每 tick 递减冷却（核心 tick 统一调用）。 */
    private void tickActionCooldown() {
        if (actionCooldown > 0) {
            actionCooldown--;
        }
        if (sacrificeFxTicks > 0) {
            sacrificeFxTicks--;
        }
    }

    /** 献祭产出瞬间触发光柱（剩余刻写入渲染态，仅此一次）。 */
    public void triggerSacrificeFx(int ticks) {
        sacrificeFxTicks = Math.max(0, ticks);
    }

    public ResourceLocation activeRecipeId() {
        return activeRecipeId;
    }

    @Override
    public void setActiveRecipeId(ResourceLocation recipeId) {
        this.activeRecipeId = recipeId;
    }

    /** 当前激活行为的 per-core 状态（无状态行为返回 null）；惰性创建。 */
    @Override
    public com.bitsson.gensokyou.ritual.RitualBehaviorState behaviorState() {
        if (activeMatch == null) {
            return null;
        }
        RitualBehavior behavior = RitualBehaviors.get(activeMatch.patternId()).orElse(null);
        if (behavior == null || behavior.newState() == null) {
            return null;
        }
        return behaviorStates.computeIfAbsent(activeMatch.patternId(), key -> behavior.newState());
    }

    /** 标记核心数据已变。 */
    @Override
    public void markDirty() {
        setChanged();
    }

    public boolean isPattern(ResourceLocation id) {
        return activeMatch != null && activeMatch.patternId().equals(id);
    }

    public long getStored() {
        SpiritBank bank = bank();
        if (bank != null && level instanceof ServerLevel serverLevel && activeMatch != null) {
            return bank.stored(serverLevel, worldPosition, activeMatch);
        }
        return storedSpiritPower;
    }

    /** 缓存上限委托当前行为的 {@link RitualBehavior#capacity} 钩子；缺覆写回落默认并告警。 */
    public long getCapacity() {
        SpiritBank bank = bank();
        if (bank != null && level instanceof ServerLevel serverLevel && activeMatch != null) {
            return bank.capacity(serverLevel, worldPosition, activeMatch);
        }
        if (activeMatch != null) {
            RitualBehavior behavior = RitualBehaviors.get(activeMatch.patternId()).orElse(null);
            if (behavior != null) {
                long cap = behavior.capacity(activeMatch.level(), this);
                if (cap != RitualBehavior.CAPACITY_NOT_SET) {
                    return cap;
                }
                if (!(behavior instanceof SpiritBank)) {
                    warnMissingCapacity(activeMatch.patternId());
                }
            }
        }
        return DEFAULT_CORE_CAPACITY;
    }

    /** 杂项仪式（无专属缓存语义）的兜底缓存上限。 */
    public static final long DEFAULT_CORE_CAPACITY = 10_000L;

    private static final java.util.Set<ResourceLocation> WARNED_MISSING_CAPACITY =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** 行为未覆写 capacity 钩子时每 patternId 告警一次（不再静默回落）。 */
    private static void warnMissingCapacity(ResourceLocation patternId) {
        if (WARNED_MISSING_CAPACITY.add(patternId)) {
            com.bitsson.gensokyou.Gensokyou.LOGGER.warn(
                    "Ritual behavior for {} does not override capacity(); using default {}",
                    patternId, DEFAULT_CORE_CAPACITY);
        }
    }

    /**
     * 结界破坏仪式缓存上限。单一阶级（tiers [2]），故不随 level 缩放。
     * 这是该仪式唯一的硬性开启门槛：缓存充盈 + 祭品齐备即闩锁开门。
     */
    public static long barrierCapacity() {
        return Math.max(0L, GensokyouConfig.BARRIER_CAPACITY.get());
    }

    /** 结界破坏仪式每秒自然流失量（充能期与待献祭期的行为差异由行为侧裁决）。 */
    public static long barrierDrainPerSecond() {
        return Math.max(0L, GensokyouConfig.BARRIER_DRAIN_PER_SECOND.get());
    }

    /** 加具土命缓存上限 = 基础值 × 4^等级。 */
    public static long kagutsuchiCapacity(int level) {
        return com.bitsson.gensokyou.ritual.SpiritCapacityScaling.scaled(
                GensokyouConfig.KAGUTSUICHI_BASE_CAPACITY.get(), 4L, level);
    }

    /** 梦渡之座缓存上限 = 基础值 × 4^等级（溢出直注槽内灵力核心，见 YumewatariBehavior）。 */
    public static long yumewatariCapacity(int level) {
        return com.bitsson.gensokyou.ritual.SpiritCapacityScaling.scaled(
                GensokyouConfig.YUMEWATARI_BASE_CAPACITY.get(), 4L, level);
    }

    /** 昼夜发电机（日轮天台/月影水镜）缓存上限 = 基值 × 4^等级。 */
    public static long daycycleCapacity(int level, long base) {
        return com.bitsson.gensokyou.ritual.SpiritCapacityScaling.scaled(base, 4L, level);
    }

    /** 众生余录缓存上限 = 基值 × 容量倍率^等级（默认 40000 × 20^L）。 */
    public static long shujouCapacity(int level) {
        return com.bitsson.gensokyou.ritual.SpiritCapacityScaling.scaled(
                GensokyouConfig.SHUJOU_BASE_CAPACITY.get(), GensokyouConfig.SHUJOU_CAPACITY_MULT.get(), level);
    }

    /** 献祭工具仪式缓存上限 = 基值 × 4^等级。 */
    public static long sacrificeCapacity(int level) {
        return daycycleCapacity(level, GensokyouConfig.SACRIFICE_BASE_CAPACITY.get());
    }

    /** 无尽藏之仪缓存上限 = 基值 × mult^等级。 */
    public static long wujinzangCapacity(int level) {
        return scaledWujinzang(GensokyouConfig.WUJINZANG_BASE_CAPACITY.get(), level);
    }

    /** 无尽藏之仪每秒耗电 = 基值 × mult^等级（饱和防溢出）。 */
    public static long wujinzangDrain(int level) {
        return scaledWujinzang(GensokyouConfig.WUJINZANG_BASE_DRAIN.get(), level);
    }

    private static long scaledWujinzang(long base, int level) {
        return com.bitsson.gensokyou.ritual.SpiritCapacityScaling.scaledSaturating(
                base, GensokyouConfig.WUJINZANG_MULT.get(), level);
    }

    /** 托管型储灵行为（如八方归元）：图案命中且行为实现 SpiritBank 时灵力四件套整体转发。 */
    @Nullable
    public SpiritBank bank() {
        if (activeMatch == null) {
            return null;
        }
        return RitualBehaviors.get(activeMatch.patternId())
                .filter(SpiritBank.class::isInstance)
                .map(SpiritBank.class::cast)
                .orElse(null);
    }

    public long receive(long maxAmount) {
        SpiritBank bank = bank();
        if (bank != null && level instanceof ServerLevel serverLevel && activeMatch != null) {
            long added = bank.receive(serverLevel, worldPosition, activeMatch, maxAmount);
            if (added > 0) {
                setChanged();
            }
            return added;
        }
        long added = Math.min(maxAmount, getCapacity() - storedSpiritPower);
        if (added > 0) {
            storedSpiritPower += added;
            setChanged();
        }
        return added;
    }

    public long extract(long maxAmount) {
        SpiritBank bank = bank();
        if (bank != null && level instanceof ServerLevel serverLevel && activeMatch != null) {
            long taken = bank.extract(serverLevel, worldPosition, activeMatch, maxAmount);
            if (taken > 0) {
                setChanged();
            }
            return taken;
        }
        long taken = Math.min(maxAmount, storedSpiritPower);
        if (taken > 0) {
            storedSpiritPower -= taken;
            setChanged();
        }
        return taken;
    }

    // ---- 路由面向端点账本（resonance-relay-routing）：速率由端点强制，调用方预算仅为建议 ----

    /**
     * 路由抽取（万象共鸣专用）：先经端点输出速率账本按 {@code spiritOutRatePerSecond} 全局限速，
     * 再走普通 {@link #extract}。速率&gt;0 才成源；多塔同 tick 共享同一份额度，先到先得。
     * 既有定向链路（配方/激活费就近抽储灵）走普通 extract，不经此账本。
     */
    public long extractRouted(long maxAmount) {
        if (maxAmount <= 0L || activeMatch == null || !(level instanceof ServerLevel serverLevel)) {
            return 0L;
        }
        long rate = RitualBehaviors.get(activeMatch.patternId())
                .map(b -> b.spiritOutRatePerSecond(serverLevel, worldPosition, activeMatch, this))
                .orElse(0L);
        if (rate <= 0L) {
            return 0L;
        }
        long granted = routedOutLedger.grant(serverLevel.getGameTime(), rate,
                GensokyouConfig.SETTLE_PERIOD_TICKS.get(), maxAmount);
        long taken = granted <= 0L ? 0L : extract(granted);
        routedOutTotal += taken;
        return taken;
    }

    /**
     * 路由注入（万象共鸣专用）：先经端点输入速率账本按 {@code spiritInRatePerSecond} 全局限速，
     * 再走普通 {@link #receive}。速率&gt;0 才成汇；多塔同 tick 共享同一份额度。
     */
    public long receiveRouted(long maxAmount) {
        if (maxAmount <= 0L || activeMatch == null || !(level instanceof ServerLevel serverLevel)) {
            return 0L;
        }
        long rate = RitualBehaviors.get(activeMatch.patternId())
                .map(b -> b.spiritInRatePerSecond(serverLevel, worldPosition, activeMatch, this))
                .orElse(0L);
        if (rate <= 0L) {
            return 0L;
        }
        long granted = routedInLedger.grant(serverLevel.getGameTime(), rate,
                GensokyouConfig.SETTLE_PERIOD_TICKS.get(), maxAmount);
        long put = granted <= 0L ? 0L : receive(granted);
        routedInTotal += put;
        return put;
    }

    /** 路由实搬累计（实测吞吐展示用；BE 重建后从 0 重新播种）。 */
    public long routedInTotal() {
        return routedInTotal;
    }

    public long routedOutTotal() {
        return routedOutTotal;
    }

    /** 结构失效/移除时清空路由账本残留额度。 */
    public void clearRoutedLedgers() {
        routedInLedger.clear();
        routedOutLedger.clear();
    }

    // ---- 百鬼夜行召唤会话（hyakki-yagyo-summon）：存储与跃迁，推进逻辑在 HyakkiYagyoSummonService ----

    /**
     * 召唤会话存储（世界无关纯逻辑，可单测）。
     *
     * <p>与 {@link CraftSession} 刻意分开而非复用：造化的容量口径是"锁定 spCost 的<b>目标</b>，
     * 另有一个与缓存解耦的累计量 {@code collected}（因为它要在足额那一刻把原料发射出去）；
     * 百鬼夜行<b>没有产物飞行</b>，缓存本身就是进度，故只需一个 {@code cost}。
     */

    // ---- 造化合成会话（zaohua-crafting）：存储与跃迁，推进逻辑在行为侧 ----

    /** 会话存储（世界无关纯逻辑，可单测）；推进逻辑在 ZaohuaCraftingService。 */

    /** 造化合成 per-core 状态（惰性创建；工厂由 ZaohuaCraftingBehavior.newState 定义）。 */
    private CraftSession craftState() {
        return (CraftSession) behaviorStates.computeIfAbsent(
                RitualBehaviors.ZAOHUA,
                key -> new CraftSession());
    }

    public CraftPhase craftPhase() {
        return craftState().phase();
    }

    public long craftSessionId() {
        return craftState().sessionId();
    }

    @Nullable
    public ResourceLocation craftRecipeId() {
        return craftState().recipeId();
    }

    public long craftCollected() {
        return craftState().collected();
    }

    /** 本会话锁定配方的 spCost（= 会话期容量；IDLE 为 0）。 */
    public long craftCost() {
        return craftState().cost();
    }

    public int craftTicks() {
        return craftState().ticks();
    }

    /** 本会话在飞实体 id（正常收尾时由行为逐个移除）。 */
    public List<Integer> craftFlightIds() {
        return craftState().flightIds();
    }

    /** 聚灵入账一笔（抽取即消耗，不退）。 */
    public void addCraftCollected(long amount) {
        craftState().addCollected(amount);
        setChanged();
    }

    /** 该代飞行实体是否仍在会话保护下（实体服务端自弃判据）。 */
    public boolean holdsFlightSession(long sessionId) {
        return craftState().holdsFlight(sessionId);
    }

    /** 启动新合成会话：换代、锁配方（含 spCost 容量）、进聚灵；返回新会话 id。 */
    public long beginCraftSession(ResourceLocation recipeId, long spCost) {
        long id = craftState().begin(recipeId, spCost);
        activeRecipeId = recipeId;
        setChanged();
        return id;
    }

    /** 聚灵足额 → 进入飞行阶段（扣料与飞行实体生成由行为同 tick 完成）。 */
    public void enterCraftFlight(List<Integer> flightEntityIds) {
        craftState().enterFlight(flightEntityIds);
        setChanged();
    }

    /** 飞行阶段计时推进一格。 */
    public void advanceCraftFlightTick() {
        craftState().advanceFlight();
    }

    /** 会话清退回 IDLE（正常收尾/中止/取消共用；已抽灵力不退）。 */
    public void clearCraftSession() {
        craftState().clear();
        activeRecipeId = null;
        setChanged();
    }

    // ---- 百鬼夜行召唤会话 ----

    /** 百鬼夜行 per-core 状态（惰性创建；工厂由 HyakkiYagyoBehavior.newState 定义）。 */
    private SummonSession summonState() {
        return (SummonSession) behaviorStates.computeIfAbsent(
                RitualBehaviors.HYAKKI_YAGYO,
                key -> new SummonSession());
    }

    /**
     * 召唤会话阶段。{@code IDLE} 时缓存容量与受灵汇速率双双为 0（本核心对供灵网络完全隐身）。
     */
    public SummonPhase summonPhase() {
        return summonState().phase();
    }

    /** 会话锁定配方的 spCost（= 会话期容量）；{@code IDLE} 为 0。 */
    public long summonCost() {
        return summonState().cost();
    }

    /** 相位序号，供渲染态携带（{@link SummonPhase#ordinal()}）。 */
    public int summonPhaseIndex() {
        return summonState().phase().ordinal();
    }

    /** 会话锁定的配方 id（{@code effect` 的解释权在行为侧）。 */
    @Nullable
    public ResourceLocation summonRecipeId() {
        return summonState().recipeId();
    }

    /** 演出开始时的结构层号（1/2/3）——一切特效规模标量的唯一来源。 */
    public int summonTier() {
        return summonState().tier();
    }

    /** 演出起始 gameTime（绝对锚点，<b>爆散那一刻</b>）；{@code < 0} = 尚未进入演出段。 */
    public int summonFxStart() {
        return summonState().fxStart();
    }

    /** 演出已进行 tick（服务端与客户端同口径，均取 {@code level.getGameTime()}）。 */
    public int summonElapsed() {
        if (summonState().fxStart() < 0 || level == null) {
            return 0;
        }
        return Math.max(0, (int) level.getGameTime() - summonState().fxStart());
    }

    /** 会话是否进行中（供 GUI「取消」按钮显隐与 {@code enabled} 门控用）。 */
    public boolean summonActive() {
        return summonState().phase() != SummonPhase.IDLE;
    }

    /**
     * 启动召唤会话：锁配方（spCost 即容量）、记结构层号，<b>不落演出锚点</b>。
     *
     * <p>锚点 MUST NOT 在这里落：此刻才刚进充能态，充能时长取决于玩家供灵（最快也可能是
     * 几十秒）。若把锚点钉在启动瞬间，整段演出会被"充能剩余时间"压缩——零供灵时表现为
     * 球、爆散、光柱在一秒内接连播完。锚点改由 {@link #markSummonBurst(int)} 在缓存填满
     * 那一刻落下；充能段的球与闪电由<b>相位</b>驱动而非时间轴，故与锚点无关。
     *
     * @param tier 结构层号（{@code RitualMatch.level()}），特效规模全部由它派生
     */
    public void beginSummonSession(ResourceLocation recipeId, long spCost, int tier) {
        summonState().begin(recipeId, spCost, tier);
        setChanged();
    }

    /**
     * 缓存填满 → 落下<b>演出锚点</b>并转入爆散段。
     *
     * <p>这一刻是"球消失、爆散开始"的分界，也是整段一次性演出的计时零点。
     */
    public void markSummonBurst(int gameTime) {
        summonState().markBurst(gameTime);
        setChanged();
    }

    /** 清掉演出锚点（退回"演出段未开始"）。仅调试命令与旧档迁移用。 */
    public void clearSummonFxStart() {
        summonState().clearFxStart();
        setChanged();
    }

    /** 会话阶段推进（行为侧按锚点自算后调用）。 */
    public void setSummonPhase(SummonPhase phase) {
        if (summonState().phase() == phase) {
            return;
        }
        summonState().setPhase(phase);
        setChanged();
    }

    /**
     * 会话清退回 IDLE：容量归零、演出锚点作废、{@code activeRecipeId} 清空。
     *
     * <p><b>同时抽干缓存</b>。这是必须的而非顺手为之：容量一归零，残留的灵力就成了
     * "无归属的存量"，下一次会话开启时会被算作已投入量——玩家等于白嫖一次召唤。
     * 需求是"取消不退还"，故直接丢弃（不是返还给玩家，也不是白送进下一次）。
     */
    public void clearSummonSession() {
        summonState().clear();
        storedSpiritPower = 0L;
        activeRecipeId = null;
        setChanged();
    }

    // ---- 红石上升沿标志 ----

    public boolean wasPowered() {
        return lastPowered;
    }

    public void setWasPowered(boolean powered) {
        if (lastPowered != powered) {
            lastPowered = powered;
            setChanged();
        }
    }

    // ---- 八百万神恩会话（yaoyorozu-grace-ritual）：存储与跃迁，推进逻辑在行为侧 ----


    /** 星移之仪 per-core 状态（惰性创建；工厂由 SeiiBehavior.newState 定义）。 */
    private com.bitsson.gensokyou.ritual.SeiiSession seiiState() {
        return (com.bitsson.gensokyou.ritual.SeiiSession) behaviorStates.computeIfAbsent(
                RitualBehaviors.SEII,
                key -> new com.bitsson.gensokyou.ritual.SeiiSession());
    }

    public com.bitsson.gensokyou.ritual.SeiiSession seiiSession() {
        return seiiState();
    }

    /** 开始星移会话：锁定配方、记花费与目标核阶、进 PAYING。 */
    public long beginSeiiSession(ResourceLocation recipeId, long spCost,
                                 int coreTier, java.util.UUID who) {
        long id = seiiState().begin(recipeId, spCost, coreTier, who);
        activeRecipeId = recipeId;
        setChanged();
        return id;
    }

    public void addSeiiCollected(long amount) {
        seiiState().addCollected(amount);
        setChanged();
    }

    public void enterSeiiPerform(com.bitsson.gensokyou.ritual.SeiiSession.Pending staged) {
        seiiState().stage(staged);
        setChanged();
    }

    public void tickSeiiPerform() {
        seiiState().tickPerform();
    }

    public void promoteSeiiReview() {
        seiiState().promoteReview();
        setChanged();
    }

    public void clearSeiiSession() {
        seiiState().clear();
        activeRecipeId = null;
        setChanged();
    }

    /** 八百万神恩 per-core 状态（惰性创建；工厂由 YaoyorozuGraceBehavior.newState 定义）。 */
    private GraceSession graceState() {
        return (GraceSession) behaviorStates.computeIfAbsent(
                RitualBehaviors.KAMI_NO_MEGUMI,
                key -> new GraceSession());
    }

    public GraceSession graceSession() {
        return graceState();
    }

    public GracePhase gracePhase() {
        return graceState().phase();
    }

    public long beginGraceSession(ResourceLocation recipeId, long spCost, java.util.UUID who,
                                  int tier, boolean refine) {
        long id = graceState().begin(recipeId, spCost, who, tier, refine);
        activeRecipeId = recipeId;
        setChanged();
        return id;
    }

    public void addGraceCollected(long amount) {
        graceState().addCollected(amount);
        setChanged();
    }

    public void enterGracePerform() {
        graceState().enterPerform();
        setChanged();
    }

    public void advanceGracePerformTick() {
        graceState().advancePerform();
    }

    public void enterGraceReview(com.bitsson.gensokyou.spirit.grace.GraceNumbers.GraceRoll roll) {
        graceState().enterReview(roll);
        setChanged();
    }

    /** 演出收尾升 REVIEW（staged 预览已在会话内，无需再传）。 */
    public void promoteGraceReview() {
        graceState().promoteReview();
        setChanged();
    }

    /** 神恩会话清退（收尾/中止/取消/预览作废共用）。 */
    public void clearGraceSession() {
        graceState().clear();
        activeRecipeId = null;
        setChanged();
    }

    /**
     * PAYING 取消/中止退还：先回槽内灵力核心（不限速率），剩余直回核心自身储灵
     * （退还旁路容量闸——容量口径=会话 spCost，逐段退会截断丢灵）。
     */
    public void refundCached(long amount) {
        if (amount <= 0L) {
            return;
        }
        long rest = amount;
        if (batteryStack.getItem() instanceof com.bitsson.gensokyou.spirit.SpiritCoreItem) {
            rest -= com.bitsson.gensokyou.spirit.SpiritCoreItem.receive(batteryStack, rest);
            setBatteryStack(batteryStack);
        }
        if (rest > 0L) {
            storedSpiritPower += rest;
            setChanged();
        }
    }

    /** 广播激活态到结构内祭品台（一次性触发会话用；封装既有私有路径）。 */
    public void broadcastPedestalsActive(boolean active) {
        if (level instanceof ServerLevel serverLevel && activeMatch != null) {
            setPedestalsActive(serverLevel, activeMatch, active);
        }
    }

    // ---- 加具土命：电池槽与燃烧批次态 ----

    public ItemStack batteryStack() {
        return batteryStack;
    }

    public void setBatteryStack(ItemStack stack) {
        this.batteryStack = stack;
        setChanged();
    }

    // ---- 仪式专用额外物品槽（泛化机制，见 ritual/RitualExtraSlots） ----
    //
    // 刻意与「祭品台」分开：祭品台是配方催化剂的载体且一台一件，若额外槽也占台位会挤掉催化剂
    // （1 阶只有 4 台）。额外槽有自己的 GUI 槽位，祭品台全部留给催化剂。
    //
    // 存储固定 MAX_EXTRA_SLOTS 格、实际可见格数由行为的 slotCount() 决定：菜单在
    // ClientboundOpenScreenPacket 之后构造，该包附加数据只有 BlockPos，客户端拿不到
    // "本仪式有几格"，故两侧必须注册同样数量的槽，否则 ContainerSetContent 会抛
    // "Slot N not in valid range" 把玩家踢下线。

    /** 额外槽格数上限（菜单两侧都按此数注册）。 */
    public static final int MAX_EXTRA_SLOTS = 4;
    /** 额外槽物品原点坐标（与既有目标槽同位，迁移后星移的核位置不变）。 */
    public static final int EXTRA_SLOT_X = 9;
    public static final int EXTRA_SLOT_Y = 61;
    public static final int EXTRA_SLOT_SPACING = 20;

    private final ItemStack[] extraSlotStacks = createEmptyExtraSlots();

    private static ItemStack[] createEmptyExtraSlots() {
        ItemStack[] stacks = new ItemStack[MAX_EXTRA_SLOTS];
        java.util.Arrays.fill(stacks, ItemStack.EMPTY);
        return stacks;
    }

    public ItemStack extraSlot(int slot) {
        return slot >= 0 && slot < MAX_EXTRA_SLOTS ? extraSlotStacks[slot] : ItemStack.EMPTY;
    }

    /** 写入额外槽（自动归一为 1 个；空栈写空）。 */
    public void setExtraSlot(int slot, ItemStack stack) {
        if (slot < 0 || slot >= MAX_EXTRA_SLOTS) {
            return;
        }
        extraSlotStacks[slot] = (stack == null || stack.isEmpty())
                ? ItemStack.EMPTY : stack.copyWithCount(1);
        setChanged();
    }

    public boolean anyExtraSlotOccupied() {
        for (ItemStack stack : extraSlotStacks) {
            if (!stack.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private final IItemHandler extraSlotsHandler = new RitualCoreExtraSlotsHandler(this);

    /** 额外槽的 item handler 视图（供 RitualCoreMenu 的 ExtraSlot 绑定）。 */
    public IItemHandler extraSlotsHandler() {
        return extraSlotsHandler;
    }

    private ListTag writeExtraSlots(HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (ItemStack stack : extraSlotStacks) {
            // ⚠️ MUST NOT 无条件 save：{@link ItemStack#save} 对空栈**抛异常**
            //    （"Cannot encode empty ItemStack"）。而本数组恒为 MAX_EXTRA_SLOTS=4 格，
            //    少名只声明 1 格 → 剩下 3 格永远是 EMPTY → 每次存盘必抛。
            //
            //    后果不只是"存盘失败"：{@code getUpdateTag()} 走同一条 saveAdditional 路径，
            //    它一抛，{@code sendBlockUpdated} 就发不出方块实体数据 → 客户端永远收不到
            //    渲染态 → **所有特效静默消失**（实机反馈：特效全没了 + LevelChunk 报错）。
            //
            //    空槽写空 CompoundTag 而非跳过，保持**下标与格位一一对应**；
            //    读回侧 {@link ItemStack#parseOptional} 对空 CompoundTag 返回 EMPTY，正好还原。
            list.add(stack.isEmpty() ? new CompoundTag() : stack.save(registries));
        }
        return list;
    }

    /**
     * 读回额外槽，并兼容旧存档的 {@code SeiiTargetCore} 单值键。
     *
     * <p>旧键只对应第 0 格（当年的星移增幅核槽），迁移后落到 {@code extraSlots[0]}，
     * 因此旧世界里的核不会被吞掉。
     */
    private void readExtraSlots(CompoundTag tag, HolderLookup.Provider registries) {
        java.util.Arrays.fill(extraSlotStacks, ItemStack.EMPTY);
        if (tag.contains(TAG_EXTRA_SLOTS, Tag.TAG_LIST)) {
            ListTag list = tag.getList(TAG_EXTRA_SLOTS, Tag.TAG_COMPOUND);
            for (int i = 0; i < Math.min(MAX_EXTRA_SLOTS, list.size()); i++) {
                // 空 CompoundTag 是"该格为空"的合法编码（见 writeExtraSlots），必须跳过
                // setCount，否则会对 ItemStack.EMPTY 调 copyWithCount
                ItemStack parsed = ItemStack.parseOptional(registries, list.getCompound(i));
                extraSlotStacks[i] = parsed.isEmpty() ? ItemStack.EMPTY : parsed.copyWithCount(1);
            }
        } else if (tag.contains(TAG_SEII_TARGET)) {
            ItemStack legacy = ItemStack.parseOptional(registries, tag.getCompound(TAG_SEII_TARGET));
            extraSlotStacks[0] = legacy.isEmpty() ? ItemStack.EMPTY : legacy.copyWithCount(1);
        }
    }

    /** 当前仪式行为声明的额外槽格数（无匹配仪式/行为未声明时为 0）。 */
    public int extraSlotCount() {
        RitualMatch match = activeMatch;
        if (match == null || level == null) {
            return 0;
        }
        return RitualBehaviors.get(match.patternId())
                .filter(RitualExtraSlots.class::isInstance)
                .map(RitualExtraSlots.class::cast)
                .map(RitualExtraSlots::slotCount)
                .map(count -> Math.max(0, Math.min(MAX_EXTRA_SLOTS, count)))
                .orElse(0);
    }

    /**
     * 产能仪式自发注灵：缓存 → 槽内灵力核心，按核心注灵速率每秒搬运
     * （逐秒 carry 进位口径同迦具土第二段）。无核心/空缓存/核心已满返回 0。
     */
    public long tickBatteryAutoFill() {
        if (!(batteryStack.getItem()
                instanceof com.bitsson.gensokyou.spirit.SpiritCoreItem spiritCore)) {
            return 0L;
        }
        long stored = getStored();
        if (stored <= 0L) {
            return 0L;
        }
        long want = fillCarry.accumulate((long) spiritCore.fillRatePerSecond() * 1000L, 1000L);
        want = Math.min(want, stored);
        if (want <= 0L) {
            return 0L;
        }
        long pushed = com.bitsson.gensokyou.spirit.SpiritCoreItem.receive(batteryStack, want);
        if (pushed > 0L) {
            extract(pushed);
        }
        return pushed;
    }

    /** 电池槽活代理（服务端菜单用；单槽，仅收灵力核心）。 */
    private final IItemHandler batteryHandler = new RitualCoreBatteryHandler(this);

    public IItemHandler batteryHandler() {
        return batteryHandler;
    }

    public ItemStack burnFuelIcon() {
        return burnFuelIcon;
    }

    public int burnTotalTicks() {
        return burnTotalTicks;
    }

    public int burnRemainingTicks() {
        return burnRemainingTicks;
    }

    public boolean isBurning() {
        return burnRemainingTicks > 0;
    }

    public long rateCarry() {
        return rateCarry.carry();
    }

    public void setRateCarry(long value) {
        rateCarry.setCarry(value);
    }

    public long fillCarry() {
        return fillCarry.carry();
    }

    public void setFillCarry(long value) {
        fillCarry.setCarry(value);
    }

    public com.bitsson.gensokyou.ritual.FixedPointAccumulator rateCarryAccumulator() {
        return rateCarry;
    }

    public com.bitsson.gensokyou.ritual.FixedPointAccumulator fillCarryAccumulator() {
        return fillCarry;
    }

    public com.bitsson.gensokyou.ritual.FixedPointAccumulator cacheFillCarryAccumulator() {
        return cacheFillCarry;
    }

    /** 点火新批次：仅记录显示图标与时长——燃料实体已在台侧销毁。 */
    public void beginBurnBatch(ItemStack fuel, int totalTicks) {
        this.burnFuelIcon = fuel.copyWithCount(1);
        this.burnTotalTicks = totalTicks;
        this.burnRemainingTicks = totalTicks;
        setChanged();
    }

    /** 推进一批燃烧；返回是否恰好烧尽（remaining 归零）。逐 tick 走字不置脏，随批次变更/秒结算持久化。 */
    public boolean advanceBurnTick() {
        if (burnRemainingTicks > 0) {
            burnRemainingTicks--;
        }
        return burnRemainingTicks == 0;
    }

    public void clearBurnBatch() {
        if (burnRemainingTicks != 0 || burnTotalTicks != 0 || !burnFuelIcon.isEmpty()) {
            burnFuelIcon = ItemStack.EMPTY;
            burnTotalTicks = 0;
            burnRemainingTicks = 0;
            setChanged();
        }
    }

    // ---- 万象共鸣：链接存储（配额/互斥/属性等不变量校验在行为侧写入路径） ----

    public List<RitualLink> inLinks() {
        return inLinks;
    }

    public List<RitualLink> outLinks() {
        return outLinks;
    }

    /** 整体替换两列链接（行为侧唯一写入口，raw 存储不再判）。链接变更即清搬运掩码（防旧位错位）。 */
    public void setSpiritLinks(List<RitualLink> in, List<RitualLink> out) {
        this.inLinks = List.copyOf(in);
        this.outLinks = List.copyOf(out);
        this.resoMovingMask = 0L;
        setChanged();
    }

    /** 结算周期末由行为侧写入"本周期实搬 >0"掩码；稳态（值不变）不触发任何推送。 */
    public void setResoMovingMask(long mask) {
        this.resoMovingMask = mask;
    }

    @Override
    public long resoMovingMask() {
        return resoMovingMask;
    }

    @Override
    public int sacrificeFxTicks() {
        return sacrificeFxTicks;
    }

    /** 渲染态（客户端 BER 只读入口；服务端侧恒为计算基准，不参与结算）。 */
    public RitualRenderState renderState() {
        return renderState;
    }

    private static void writeLinks(CompoundTag tag, String key, List<RitualLink> links) {
        if (links.isEmpty()) {
            return;
        }
        ListTag list = new ListTag();
        for (RitualLink link : links) {
            CompoundTag entry = new CompoundTag();
            entry.putLong(TAG_LINK_POS, link.corePos().asLong());
            entry.putString(TAG_LINK_PATTERN, link.patternId().toString());
            list.add(entry);
        }
        tag.put(key, list);
    }

    private static List<RitualLink> readLinks(CompoundTag tag, String key) {
        if (!tag.contains(key)) {
            return List.of();
        }
        List<RitualLink> out = new ArrayList<>();
        for (var item : tag.getList(key, CompoundTag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) item;
            out.add(new RitualLink(BlockPos.of(entry.getLong(TAG_LINK_POS)),
                    ResourceLocation.parse(entry.getString(TAG_LINK_PATTERN))));
        }
        return List.copyOf(out);
    }

    /** 结构 Y 包围盒（世界坐标，重扫时刷新）：螺旋纵向覆盖范围。 */
    public int structureMinY() {
        return boundsMinY;
    }

    public int structureMaxY() {
        return boundsMaxY;
    }

    private void refreshStructureBounds() {
        int min = worldPosition.getY();
        int max = worldPosition.getY();
        if (activeMatch != null) {
            for (List<BlockPos> positions : activeMatch.keyedPositions().values()) {
                for (BlockPos p : positions) {
                    if (p.getY() < min) {
                        min = p.getY();
                    }
                    if (p.getY() > max) {
                        max = p.getY();
                    }
                }
            }
        }
        boundsMinY = min;
        boundsMaxY = max;
    }

    // ---- 渲染态同步（resonance-relay-render-perf D1/D2）：仅共鸣塔产态，仅变化即推，稳态零包 ----

    /**
     * 每服务端 tick 末尾调用：重算渲染态并与上次已推送值比较，仅不同才
     * {@code sendBlockUpdated}。非共鸣图案返回 null = 无渲染流量；曾推送过则补一次清零态。
     */
    private void syncRenderState() {
        if (level == null || level.isClientSide) {
            return;
        }
        RitualRenderState desired = buildRenderState();
        if (desired == null) {
            if (lastSentRenderState == null) {
                return;
            }
            desired = RitualRenderState.EMPTY;
        }
        if (desired.equals(lastSentRenderState)) {
            return;
        }
        lastSentRenderState = desired;
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Nullable
    private RitualRenderState buildRenderState() {
        if (activeMatch == null) {
            return null;
        }
        return RitualBehaviors.get(activeMatch.patternId())
                .map(behavior -> behavior.buildRenderState(activeMatch, this))
                .orElse(null);
    }
    /** 结构水平半径（格，向上取整）：核心到最远结构块的 XZ 距离，供迦具土火柱铺面参考。 */
    public int structureRadiusXZ() {
        if (activeMatch == null) {
            return 0;
        }
        int radius = 0;
        for (List<BlockPos> positions : activeMatch.keyedPositions().values()) {
            for (BlockPos p : positions) {
                radius = Math.max(radius, (int) Math.ceil(Math.hypot(
                        p.getX() - worldPosition.getX(), p.getZ() - worldPosition.getZ())));
            }
        }
        return radius;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        RitualRenderState state = lastSentRenderState != null ? lastSentRenderState : renderState;
        tag.put(TAG_RENDER_STATE, state.toTag());
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // ---- 生命周期：enabled 门控与启停 ----

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean value) {
        if (enabled == value) {
            return;
        }
        enabled = value;
        if (!value && activeMatch != null && level instanceof ServerLevel serverLevel) {
            RitualBehaviors.get(activeMatch.patternId()).ifPresent(behavior ->
                    behavior.onDisabled(serverLevel, getBlockPos(), activeMatch, this));
        }
        setChanged();
    }

    /**
     * 百鬼夜行召唤演出态。
     *
     * <p>只有<b>一个</b>包要发。字段位分配（每项都有唯一主人，互不串扰）：
     * <ul>
     *   <li>{@code enabled} = 会话进行中（CHARGING/BURST/PILLAR 皆为真）</li>
     *   <li>{@code movingMask} = <b>相位序号</b>（{@code SummonPhase.ordinal()}）。
     *       充能段的球与闪电由<b>相位</b>判定而非时间轴——充能时长随玩家供灵而变，
     *       若按时间推进，零供灵时整段演出会在启动后一秒内全部播完。</li>
     *   <li>{@code minY} = <b>演出起始 gameTime</b>，由 {@code markSummonBurst} 在缓存填满
     *       那一刻落下。爆散与光柱的相位全部由 {@code gameTime − minY} 自算。</li>
     *   <li>{@code maxY} = 爆散时长，{@code period} = 光柱保持时长（均随包下发，
     *       使两侧拿到同一份分拍边界，不会各自读配置而永久漂移）。</li>
     *   <li>{@code tier} = 结构层号，一切规模标量的唯一来源。</li>
     * </ul>
     *
     * <p>MUST NOT 把 {@code elapsed} 从服务端逐 tick 推下来——那会变成"开一次门发 60 个包、
     * 丢一个就永久卡死"的经典故障（见 {@code SukimaBlockEntity} 的同款 postmortem）。
     */

    /**
     * UI 启动按钮的唯一入口：门槛校验 → 行为侧 onStart（收费等）→ 扣除 on_activate 消耗 → 置位。
     * 任一步失败则整体不生效并向玩家回显原因。
     */
    public boolean start(ServerPlayer player) {
        if (!(level instanceof ServerLevel serverLevel) || activeMatch == null || enabled) {
            return false;
        }
        Optional<RitualPattern> patternOpt = RitualPatternLoader.byId(activeMatch.patternId());
        if (patternOpt.isEmpty()) {
            return false;
        }
        RitualPattern pattern = patternOpt.get();
        if (!pattern.requirements().isEmpty()) {
            RitualOfferings.Result result = RitualOfferings.check(pattern, activeMatch, serverLevel);
            if (!result.satisfied()) {
                player.displayClientMessage(Component.translatable(
                        "msg.gensokyou.ritual_missing_offerings", result.missingCount()), true);
                return false;
            }
        }
        Optional<RitualBehavior> behavior = RitualBehaviors.get(activeMatch.patternId());
        // 会话型仪式（造化/神恩）不响应通用启停：启动唯一入口是行为的 UiAction 会话触发
        if (behavior.isPresent() && behavior.get().handlesStartViaUiAction()) {
            return false;
        }

        // 配方解析：声明了 activation 配方的仪式必须命中一条当前等级可用配方；
        // EXACT 候选先行（既有严格等值语义），未中再对 MAX 候选取消耗总量最大的子集命中
        List<RitualRecipe> candidates = RitualRecipeLoader.forPattern(pattern.id()).stream()
                .filter(RitualRecipe::activation)
                .filter(r -> r.minTier() <= activeMatch.level())
                .toList();
        RitualRecipeMatcher.Match matched = null;
        if (!candidates.isEmpty()) {
            RitualRecipeLoader.warnIfPatternMissing(pattern.id(), true);
            var pools = RitualRecipeMatcher.collectPools(activeMatch, serverLevel);
            for (RitualRecipe candidate : candidates) {
                if (candidate.match() != RitualRecipe.MatchMode.EXACT) {
                    continue;
                }
                Optional<RitualRecipeMatcher.Match> attempt =
                        RitualRecipeMatcher.match(candidate, pools);
                if (attempt.isPresent()) {
                    matched = attempt.get();
                    break;
                }
            }
            if (matched == null) {
                matched = RitualRecipeMatcher.matchMax(candidates, pools).orElse(null);
            }
            if (matched == null) {
                player.displayClientMessage(
                        Component.translatable("msg.gensokyou.ritual_no_matching_recipe"), true);
                return false;
            }
            // 配方灵力消耗：三段式来源（槽核→自身储→周围兜底）全有全无预扣，不足即零消耗中止
            if (!SpiritPowerHelper.payCost(serverLevel, worldPosition, this, matched.recipe().spCost())) {
                player.displayClientMessage(Component.translatable(
                        "msg.gensokyou.ritual_no_power", matched.recipe().spCost()), true);
                return false;
            }
        }

        if (behavior.isPresent()
                && behavior.get().onStart(serverLevel, worldPosition, activeMatch, this, player)
                        == InteractionResult.FAIL) {
            return false;
        }
        if (!RitualOfferings.consumeActivations(pattern, activeMatch, serverLevel)) {
            player.displayClientMessage(Component.translatable(
                    "msg.gensokyou.ritual_missing_offerings", 1), true);
            return false;
        }
        if (matched != null) {
            RitualRecipeMatcher.apply(serverLevel, matched.takes());
            activeRecipeId = matched.recipe().id();
        } else {
            activeRecipeId = null;
        }
        setEnabled(true);
        setPedestalsActive(serverLevel, activeMatch, true);
        if (matched != null && behavior.isPresent()) {
            behavior.get().onRecipeExecuted(serverLevel, worldPosition, activeMatch, this,
                    player, matched.recipe());
        }
        String achievement = behavior
                .map(b -> b.startAchievement(serverLevel, worldPosition, activeMatch, this))
                .orElse(null);
        if (achievement != null) {
            AchievementAwards.award(player, achievement);
        }
        return true;
    }

    /** 广播激活态到结构内全部祭品台（驱动悬浮旋转渲染）。 */
    private void setPedestalsActive(ServerLevel level, RitualMatch match, boolean active) {
        for (List<BlockPos> positions : match.keyedPositions().values()) {
            for (BlockPos pos : positions) {
                if (level.getBlockEntity(pos) instanceof RitualPedestalBlockEntity pedestal) {
                    pedestal.setRituallyActive(active);
                }
            }
        }
    }

    /** UI 停止按钮：仅暂停运行，结构不动，可再次启动。 */
    public void stop() {
        setEnabled(false);
        // 召唤会话的清理由 setEnabled(false) 统一承担（覆盖全部自动停机路径）。
        activeRecipeId = null;
        setChanged();
        if (level instanceof ServerLevel serverLevel && activeMatch != null) {
            setPedestalsActive(serverLevel, activeMatch, false);
        }
    }

    // ---- 结界引爆状态 ----

    public BlockPos portalPos() {
        return portalPos;
    }

    public void setPortalPos(BlockPos portalPos) {
        this.portalPos = portalPos;
        setChanged();
    }

    // ---- 结界闩锁（独立于 enabled，跨结构失配存续） ----

    /** 闩锁是否已置位：置位后传送门永久开启，不再校验祭品也不再耗灵。 */
    public boolean isBarrierLatched() {
        return barrierLatched;
    }

    /**
     * 置位/清除闩锁。置位时同时记录主世界侧门位与幻想乡侧孪生门位（可为 null）。
     * 清除时一并清空门位，避免残留坐标被后续逻辑误用。
     */
    public void setBarrierLatched(boolean latched) {
        if (this.barrierLatched == latched) {
            return;
        }
        this.barrierLatched = latched;
        if (!latched) {
            this.portalPos = null;
        }
        setChanged();
    }

    // ---- 自动化接口：祭品台代理箱（槽位 ⇄ 成型结构内祭品台，每槽容量 1，活代理） ----

    /** 代理箱 handler（稳定单例：NeoForge 按返回实例缓存 capability）。 */
    private final IItemHandler itemHandler = new RitualCorePedestalHandler(this);
    private IItemHandler customItemHandler;
    private net.minecraft.resources.ResourceLocation customItemHandlerPattern;

    /** 物品接入面：行为可自定义（如无尽藏跨晶块合并箱），否则默认祭品台代理箱。 */
    public IItemHandler itemHandler() {
        if (activeMatch != null) {
            ResourceLocation pid = activeMatch.patternId();
            if (customItemHandler == null || !pid.equals(customItemHandlerPattern)) {
                customItemHandler = RitualBehaviors.get(pid)
                        .map(behavior -> behavior.itemHandler(this))
                        .orElse(null);
                customItemHandlerPattern = pid;
            }
            if (customItemHandler != null) {
                return customItemHandler;
            }
        }
        return itemHandler;
    }

    /** 无尽藏：电池核心→缓存的补料（每 tick 由 WujinzangStorage 调用；非产灵方向）。 */
    public long tickBatteryToCacheFill() {
        if (!(batteryStack.getItem() instanceof com.bitsson.gensokyou.spirit.SpiritCoreItem spiritCore)) {
            return 0L;
        }
        long space = getCapacity() - storedSpiritPower;
        if (space <= 0L) {
            return 0L;
        }
        long want = cacheFillCarry.accumulate((long) spiritCore.fillRatePerSecond() * 1000L, 1000L);
        want = Math.min(want, space);
        if (want <= 0L) {
            return 0L;
        }
        long pulled = com.bitsson.gensokyou.spirit.SpiritCoreItem.extract(batteryStack, want);
        if (pulled > 0L) {
            setBatteryStack(batteryStack);
            receive(pulled);
        }
        return pulled;
    }

    /** 成型结构内全部祭品台位（按 BE 类型判定、跨 key 汇总后规范序 y,z,x）；未成型为空。 */
    public List<BlockPos> pedestalPositions() {
        if (activeMatch == null || level == null) {
            return List.of();
        }
        List<BlockPos> out = new ArrayList<>();
        for (List<BlockPos> positions : activeMatch.keyedPositions().values()) {
            for (BlockPos p : positions) {
                if (level.getBlockEntity(p) instanceof RitualPedestalBlockEntity) {
                    out.add(p.immutable());
                }
            }
        }
        out.sort(Comparator.<BlockPos>comparingInt(BlockPos::getY)
                .thenComparingInt(BlockPos::getZ)
                .thenComparingInt(BlockPos::getX));
        return out;
    }

    RitualPedestalBlockEntity pedestalAt(int slot) {
        List<BlockPos> positions = pedestalPositions();
        if (slot < 0 || slot >= positions.size() || level == null) {
            return null;
        }
        return level.getBlockEntity(positions.get(slot))
                instanceof RitualPedestalBlockEntity pedestal ? pedestal : null;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state,
                                  RitualCoreBlockEntity core) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        core.ageTicks++;
        core.tickActionCooldown();
        boolean rescan = core.ageTicks % 20 == 1L || core.activeMatch == null;
        RitualMatch previous = core.activeMatch;
        if (rescan) {
            core.activeMatch = RitualMatcher.matchAt(serverLevel, pos).orElse(null);
            RitualCoreRegistry registry = RitualCoreRegistry.forLevel(serverLevel);
            if (core.activeMatch != null) {
                registry.register(pos, core.activeMatch.patternId(), core.activeMatch.level());
            } else if (previous != null) {
                registry.unregister(pos, previous.patternId());
            }
            core.refreshStructureBounds();
            // 图案直接切换（A 命中 → B 命中）：先按 A 失效清理，再按 B 成型（ritual-lifecycle 增量）
            boolean patternChanged = previous != null && core.activeMatch != null
                    && !previous.patternId().equals(core.activeMatch.patternId());
            if (patternChanged) {
                core.clearRoutedLedgers();
                core.setEnabled(false);
                core.activeRecipeId = null;
                core.setPedestalsActive(serverLevel, previous, false);
                writePedestalTiers(serverLevel, previous, 0);
                RitualBehaviors.get(previous.patternId())
                        .ifPresent(behavior -> behavior.onStructureLost(serverLevel, pos));
            }
            if (core.activeMatch != null && (previous == null || patternChanged)) {
                // 成型瞬间：代理箱从无槽变有槽，失效块 cap 缓存让漏斗等消费者重查
                serverLevel.invalidateCapabilities(pos);
                // 台面激活态不持久化：成型/重载后按核心当前 enabled 补广播（唯一事实源）
                core.setPedestalsActive(serverLevel, core.activeMatch, core.enabled);
                // 成型替换扩展点：命中瞬间调用（含图案切换的新图案）
                RitualBehaviors.get(core.activeMatch.patternId())
                        .ifPresent(behavior -> behavior.onFormed(serverLevel, pos, core.activeMatch));
            }
            // 品阶视觉随仪式等级（结构内仪式石最高品阶）；结构失效回落 0 级灰。
            // 仅在值变化时写块（重扫幂等，无循环）；setBlock 触发的邻居更新不参与重扫。
            int ritualTier = core.activeMatch == null ? 0 : core.activeMatch.ritualTier();
            writeTier(serverLevel, pos, ritualTier);
            if (core.activeMatch != null) {
                writePedestalTiers(serverLevel, core.activeMatch, ritualTier);
            }
        }
        if (core.activeMatch == null) {
            if (previous != null) {
                // 重扫失效即自动停机（全仪式统一判据），并触发既有失效清理
                serverLevel.invalidateCapabilities(pos);
                core.clearRoutedLedgers();
                core.setEnabled(false);
                core.activeRecipeId = null;
                core.setPedestalsActive(serverLevel, previous, false);
                writePedestalTiers(serverLevel, previous, 0);
                RitualBehaviors.get(previous.patternId())
                        .ifPresent(behavior -> behavior.onStructureLost(serverLevel, pos));
            }
            core.syncRenderState();
            return;
        }
        Optional<RitualPattern> pattern = RitualPatternLoader.byId(core.activeMatch.patternId());
        pattern.ifPresent(value -> tickPassiveRecipes(serverLevel, pos, core, value));
        // 被动行为通道（产能注灵等）：成型即走，不经 enabled 门控
        RitualBehaviors.get(core.activeMatch.patternId()).ifPresent(
                value -> value.serverPassiveTick(serverLevel, pos, core.activeMatch, core));
        if (core.enabled) {
            if (pattern.isPresent()
                    && !RitualOfferings.upkeepTick(core.ageTicks, pattern.get(), core.activeMatch, serverLevel)) {
                // 周期供给断供 → 自动停机，需玩家重新启动
                core.setEnabled(false);
                core.activeRecipeId = null;
                core.setPedestalsActive(serverLevel, core.activeMatch, false);
            } else {
                Optional<RitualBehavior> behavior =
                        RitualBehaviors.get(core.activeMatch.patternId());
                behavior.ifPresent(value -> value.serverTick(serverLevel, pos, core.activeMatch, core));
            }
        }
        // GUI 快照心跳：界面打开期间 1Hz 推送，停机/断供态同样收敛
        // （缓存可被路由抽取等外部变化不依赖行为 enabled tick）
        core.syncRenderState();
        if (core.ageTicks % 20 == 0L) {
            ModNetworking.sendRitualInfoToViewers(serverLevel, pos);
        }
    }

    /** 将核心方块的 tier 属性更新为指定品阶（仅在变化时 setBlock，避免重扫循环）。 */
    private static void writeTier(ServerLevel level, BlockPos pos, int tier) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof com.bitsson.gensokyou.block.RitualCoreBlock
                && state.getValue(com.bitsson.gensokyou.block.RitualCoreBlock.TIER) != tier) {
            level.setBlock(pos, state.setValue(com.bitsson.gensokyou.block.RitualCoreBlock.TIER, tier), 3);
        }
    }

    /**
     * 将匹配结构内全部祭品台的 tier 属性同步为仪式等级（与核心同源同值）。
     * 仅变化时写块；同方块改属性不重建 BE，台面物品无损。
     */
    private static void writePedestalTiers(ServerLevel level, RitualMatch match, int tier) {
        for (BlockPos p : RitualPedestals.positions(match)) {
            BlockState state = level.getBlockState(p);
            if (state.is(ModBlocks.RITUAL_PEDESTAL.get())
                    && state.getValue(RitualPedestalBlock.TIER) != tier) {
                level.setBlock(p, state.setValue(RitualPedestalBlock.TIER, tier), 3);
            }
        }
    }

    /** 持续型配方循环：成型即可运行（不受 enabled 门控），成功一条即结束本周期。 */
    private static void tickPassiveRecipes(ServerLevel level, BlockPos pos,
                                           RitualCoreBlockEntity core, RitualPattern pattern) {
        if (core.ageTicks % GensokyouConfig.PASSIVE_CYCLE_TICKS.get() != 0) {
            return;
        }
        List<RitualRecipe> passives = RitualRecipeLoader.forPattern(pattern.id()).stream()
                .filter(RitualRecipe::passive)
                .filter(r -> r.minTier() <= core.activeMatch.level())
                .toList();
        if (passives.isEmpty()) {
            return;
        }
        RitualRecipeLoader.warnIfPatternMissing(pattern.id(), true);
        for (RitualRecipe recipe : passives) {
            Optional<RitualRecipeMatcher.Match> attempt =
                    RitualRecipeMatcher.match(recipe, core.activeMatch, level);
            if (attempt.isEmpty()) {
                continue;
            }
            ItemStack result = recipe.resultStack();
            if (result == null) {
                continue;
            }
            // 三段式来源全有全无扣费（槽核→自身储→周围兜底），不足本轮跳过
            if (!SpiritPowerHelper.payCost(level, pos, core, recipe.spCost())) {
                continue;
            }
            RitualRecipeMatcher.apply(level, attempt.get().takes());
            dropPassiveOutput(level, pos, result);
            break;
        }
    }

    /** 被动产物掉落：核心上 1 格、水平半径 R 圆盘内随机落点（统一落点工具），产物从不经停台面。 */
    private static void dropPassiveOutput(ServerLevel level, BlockPos pos, ItemStack result) {
        com.bitsson.gensokyou.ritual.RitualOutputs.spawn(level, pos, result.copy());
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putLong(TAG_STORED, storedSpiritPower);
        writeLinks(tag, TAG_RESO_IN, inLinks);
        writeLinks(tag, TAG_RESO_OUT, outLinks);
        tag.putBoolean(TAG_ENABLED, enabled);
        if (activeRecipeId != null) {
            tag.putString(TAG_ACTIVE_RECIPE, activeRecipeId.toString());
        }
        if (portalPos != null) {
            tag.putLong(TAG_PORTAL_POS, portalPos.asLong());
        }
        if (barrierLatched) {
            tag.putBoolean(TAG_BARRIER_LATCHED, true);
        }
        if (!batteryStack.isEmpty()) {
            tag.put(TAG_BATTERY, batteryStack.save(registries));
        }
        if (anyExtraSlotOccupied()) {
            tag.put(TAG_EXTRA_SLOTS, writeExtraSlots(registries));
        }
        if (burnTotalTicks > 0) {
            CompoundTag burn = new CompoundTag();
            if (!burnFuelIcon.isEmpty()) {
                burn.put(TAG_BURN_FUEL, burnFuelIcon.save(registries));
            }
            burn.putInt(TAG_BURN_TOTAL, burnTotalTicks);
            burn.putInt(TAG_BURN_REMAINING, burnRemainingTicks);
            tag.put(TAG_BURN, burn);
        }
        if (rateCarry.carry() != 0) {
            tag.putLong(TAG_RATE_ACCUM, rateCarry.carry());
        }
        if (fillCarry.carry() != 0) {
            tag.putLong(TAG_FILL_ACCUM, fillCarry.carry());
        }
        for (com.bitsson.gensokyou.ritual.RitualBehaviorState state : behaviorStates.values()) {
            state.save(tag, registries);
        }
        if (lastPowered) {
            tag.putBoolean(TAG_LAST_POWERED, true);
        }
        if (actionCooldown > 0) {
            tag.putInt(TAG_ACTION_COOLDOWN, actionCooldown);
        }
        if (cacheFillCarry.carry() != 0L) {
            tag.putLong("WujinzangCacheCarry", cacheFillCarry.carry());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        // 加载期 activeMatch 未就位，不可用 getCapacity 夹取（分图案上限会误截）；
        // 超储自愈：receive 对 stored≥cap 恒 0，extract 正常，无需读档时 clamp
        storedSpiritPower = Math.max(0L, tag.getLong(TAG_STORED));
        inLinks = readLinks(tag, TAG_RESO_IN);
        outLinks = readLinks(tag, TAG_RESO_OUT);
        if (tag.contains(TAG_ENABLED)) {
            enabled = tag.getBoolean(TAG_ENABLED);
        } else if (tag.contains(TAG_LEGACY_BARRIER_ACTIVATED)) {
            // 旧存档兼容：结界引爆开关收编为统一 enabled 态
            enabled = tag.getBoolean(TAG_LEGACY_BARRIER_ACTIVATED);
        }
        portalPos = tag.contains(TAG_PORTAL_POS)
                ? BlockPos.of(tag.getLong(TAG_PORTAL_POS)) : null;
        // 缺字段 = 未闩锁（向后兼容：旧存档的 BarrierActivated 是一次性扣费时代的语义，
        // 不得据此推断闩锁——那是两个不同的状态）
        barrierLatched = tag.getBoolean(TAG_BARRIER_LATCHED);
        activeRecipeId = tag.contains(TAG_ACTIVE_RECIPE)
                ? ResourceLocation.parse(tag.getString(TAG_ACTIVE_RECIPE)) : null;
        batteryStack = tag.contains(TAG_BATTERY)
                ? ItemStack.parseOptional(registries, tag.getCompound(TAG_BATTERY))
                : ItemStack.EMPTY;
        readExtraSlots(tag, registries);
        if (tag.contains(TAG_BURN)) {
            CompoundTag burn = tag.getCompound(TAG_BURN);
            burnFuelIcon = burn.contains(TAG_BURN_FUEL)
                    ? ItemStack.parseOptional(registries, burn.getCompound(TAG_BURN_FUEL))
                    : ItemStack.EMPTY;
            burnTotalTicks = burn.getInt(TAG_BURN_TOTAL);
            burnRemainingTicks = Math.min(burn.getInt(TAG_BURN_REMAINING), burnTotalTicks);
        }
        rateCarry.setCarry(tag.getLong(TAG_RATE_ACCUM));
        fillCarry.setCarry(tag.getLong(TAG_FILL_ACCUM));
        for (var entry : RitualBehaviors.all().entrySet()) {
            com.bitsson.gensokyou.ritual.RitualBehaviorState state = entry.getValue().newState();
            if (state == null) {
                continue;
            }
            state.load(tag, registries);
            if (!state.isEmpty()) {
                behaviorStates.put(entry.getKey(), state);
            }
        }
        lastPowered = tag.getBoolean(TAG_LAST_POWERED);
        actionCooldown = Math.max(0, tag.getInt(TAG_ACTION_COOLDOWN));
        cacheFillCarry.setCarry(tag.getLong("WujinzangCacheCarry"));
        if (tag.contains(TAG_RENDER_STATE)) {
            renderState = RitualRenderState.fromTag(tag.getCompound(TAG_RENDER_STATE));
        }
    }
}
