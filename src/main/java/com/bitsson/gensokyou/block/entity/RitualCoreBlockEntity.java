package com.bitsson.gensokyou.block.entity;

import com.bitsson.gensokyou.block.RitualPedestalBlock;
import com.bitsson.gensokyou.config.GensokyouConfig;
import com.bitsson.gensokyou.network.ModNetworking;
import com.bitsson.gensokyou.registry.ModBlockEntities;
import com.bitsson.gensokyou.registry.ModBlocks;
import com.bitsson.gensokyou.ritual.RitualBehavior;
import com.bitsson.gensokyou.ritual.RitualBehaviors;
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
import com.bitsson.gensokyou.ritual.behavior.HoujounoTeihouBehavior;
import com.bitsson.gensokyou.ritual.behavior.SpiritBank;
import com.bitsson.gensokyou.ritual.behavior.TickRateLedger;
import com.bitsson.gensokyou.ritual.behavior.KanayamahikoSmeltSession;
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

public class RitualCoreBlockEntity extends BlockEntity {
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
    private static final String TAG_BURN = "KagutsuchiBurn";
    private static final String TAG_BURN_FUEL = "Fuel";
    private static final String TAG_BURN_TOTAL = "TotalTicks";
    private static final String TAG_BURN_REMAINING = "RemainingTicks";
    private static final String TAG_RATE_ACCUM = "RateAccum";
    private static final String TAG_FILL_ACCUM = "FillAccum";
    private static final String TAG_CRAFT_PHASE = "CraftPhase";
    private static final String TAG_CRAFT_COLLECTED = "CraftCollected";
    private static final String TAG_CRAFT_COST = "CraftCost";
    private static final String TAG_CRAFT_FLIGHT_AGE = "CraftFlightAge";
    private static final String TAG_CRAFT_SESSION = "CraftSession";
    private static final String TAG_CRAFT_FLIGHT_IDS = "CraftFlightIds";
    private static final String TAG_CRAFT_RECIPE = "CraftRecipe";
    private static final String TAG_CRAFT_ID = "Id";
    private static final String TAG_LAST_POWERED = "LastPowered";
    private static final String TAG_GRACE_PHASE = "GracePhase";
    private static final String TAG_GRACE_SESSION = "GraceSession";
    private static final String TAG_GRACE_COLLECTED = "GraceCollected";
    private static final String TAG_GRACE_COST = "GraceCost";
    private static final String TAG_GRACE_TICKS = "GraceTicks";
    private static final String TAG_GRACE_TIER = "GraceTier";
    private static final String TAG_GRACE_REFINE = "GraceRefine";
    private static final String TAG_GRACE_RECIPE = "GraceRecipe";
    private static final String TAG_GRACE_INITIATOR = "GraceInitiator";
    private static final String TAG_GRACE_PENDING = "GracePending";
    /** 献祭仪式：结算后强制冷却剩余 tick（通用字段，仅该行为族使用）。 */
    private static final String TAG_ACTION_COOLDOWN = "ActionCooldown";
    /** 无尽藏之仪托管数据段键：分区组表 + 孤儿段 + 段位坐标（由 {@code WujinzangStorage} 读写）。 */
    public static final String TAG_WUJINZANG_VAULT = "WujinzangVault";

    /** 造化合成会话阶段（一次性合成型仪式共用存储；推进逻辑在行为侧）。 */
    public enum CraftPhase { IDLE, PAYING, FLIGHT }

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
    private long rateCarry;
    private long fillCarry;
    /** 路由面向（万象共鸣）的端点每 tick 速率账本：唯一权威，gameTime 锁存；运行时态不持久化。 */
    private final TickRateLedger routedInLedger = new TickRateLedger();
    private final TickRateLedger routedOutLedger = new TickRateLedger();
    /** 路由实搬单调累计计数（不持久化，BE 重建归零）：实测吞吐差分的唯一数据源。 */
    private long routedInTotal;
    private long routedOutTotal;

    // ---- 造化合成会话（一次性合成型仪式的每核状态；推进逻辑在行为侧）----
    /** 会话存储（换代/锁配方/聚灵进度/飞行计时/在飞实体 id；世界无关纯逻辑，可单测）。 */
    private final CraftSession craft = new CraftSession();
    /** 红石上升沿检测：上一拍邻居信号是否 >0（持久化，防重载后常亮信号误触发）。 */
    private boolean lastPowered;
    /** 献祭仪式：结算后强制冷却剩余 tick（每 tick 递减；持久化防重载连发）。 */
    private int actionCooldown;
    /** 献祭仪式：产出光柱剩余渲染刻（瞬态，仅驱动客户端 BER，不持久化）。 */
    private int sacrificeFxTicks;
    /** 无尽藏之仪托管数据段（键 {@link #TAG_WUJINZANG_VAULT}）：分区组表 + 孤儿段 + 段位坐标。 */
    private CompoundTag wujinzangVault;
    /** 无尽藏：电池核心→缓存的定点进位累加器（与产能方向的 fillCarry 分道）。 */
    private long cacheFillCarry;
    private final KanayamahikoSmeltSession kanayamahikoSession = new KanayamahikoSmeltSession();

    public RitualCoreBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.RITUAL_CORE.get(), pos, state);
    }

    /** 移除（含区块卸载/破坏）时注销索引条目，归属守卫防拆旧建新误删。 */
    @Override
    public void setRemoved() {
        clearRoutedLedgers();
        if (activeMatch != null && level instanceof ServerLevel serverLevel) {
            if (activeMatch.patternId().equals(RitualBehaviors.HOUJOUNO_TEIHOU)) {
                HoujounoTeihouBehavior.clearRuntimeFailures(serverLevel, getBlockPos());
            }
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

    /** 无尽藏之仪 vault 段（可为 null）；由 WujinzangStorage 读写。 */
    public CompoundTag getWujinzangVault() {
        return wujinzangVault;
    }

    /** 设置无尽藏 vault 段；null 清除。 */
    public void setWujinzangVault(CompoundTag vault) {
        this.wujinzangVault = vault;
        setChanged();
    }

    /** 取 vault（不存在则新建并挂上）。 */
    public CompoundTag wujinzangVault() {
        if (wujinzangVault == null) {
            wujinzangVault = new CompoundTag();
        }
        return wujinzangVault;
    }

    /** 无尽藏：电池核心→缓存的定点进位余额（供 Vault 与持久化）。 */
    public long cacheFillCarry() {
        return cacheFillCarry;
    }

    public void setCacheFillCarry(long value) {
        this.cacheFillCarry = value;
    }

    public KanayamahikoSmeltSession kanayamahikoSession() {
        return kanayamahikoSession;
    }

    public void clearKanayamahikoSession() {
        kanayamahikoSession.clear();
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

    /** 缓存上限按图案分派：托管型行为整体转发，共鸣塔零缓存，加具土命随等级指数放大，其余仪式常量兜底。 */
    public long getCapacity() {
        SpiritBank bank = bank();
        if (bank != null && level instanceof ServerLevel serverLevel && activeMatch != null) {
            return bank.capacity(serverLevel, worldPosition, activeMatch);
        }
        if (activeMatch != null) {
            if (activeMatch.patternId().equals(RitualBehaviors.RESONANCE)) {
                return 0L;
            }
            if (activeMatch.patternId().equals(RitualBehaviors.KAGUTSUICHI)) {
                return kagutsuchiCapacity(activeMatch.level());
            }
            if (activeMatch.patternId().equals(RitualBehaviors.YUMEWATARI)) {
                return yumewatariCapacity(activeMatch.level());
            }
            if (activeMatch.patternId().equals(RitualBehaviors.SHUJOU)) {
                return shujouCapacity(activeMatch.level());
            }
            if (activeMatch.patternId().equals(RitualBehaviors.WUJINZANG)) {
                return wujinzangCapacity(activeMatch.level());
            }
            if (activeMatch.patternId().equals(RitualBehaviors.SAIR_ENERGY)) {
                return GensokyouConfig.SAIR_ENERGY_BASE_CAPACITY.get();
            }
            if (activeMatch.patternId().equals(RitualBehaviors.BARRIER_BREAK)) {
                return barrierCapacity();
            }
            if (activeMatch.patternId().equals(RitualBehaviors.KANAYAMAHIKO)) {
                return kanayamahikoCapacity(activeMatch.level());
            }
            if (activeMatch.patternId().equals(RitualBehaviors.HOUJOUNO_TEIHOU)) {
                return HoujounoTeihouBehavior.capacity(activeMatch.level());
            }
            if (activeMatch.patternId().equals(RitualBehaviors.NICHIRIN)) {
                return daycycleCapacity(activeMatch.level(),
                        GensokyouConfig.NICHIRIN_BASE_CAPACITY.get());
            }
            if (activeMatch.patternId().equals(RitualBehaviors.TSUKIKAGE)) {
                return daycycleCapacity(activeMatch.level(),
                        GensokyouConfig.TSUKIKAGE_BASE_CAPACITY.get());
            }
            if (RitualBehaviors.isToolSacrifice(activeMatch.patternId())) {
                return sacrificeCapacity(activeMatch.level());
            }
            if (activeMatch.patternId().equals(RitualBehaviors.ZAOHUA)) {
                // 不启动不缓存灵力：空闲容量 0（路由选不中、注不进）；
                // 会话期容量 = 锁定配方 spCost（受灵缓冲上界恰为本次所需）
                return craft.phase() == CraftPhase.IDLE ? 0L : craft.cost();
            }
            if (activeMatch.patternId().equals(RitualBehaviors.KAMI_NO_MEGUMI)) {
                // 神恩同造化口径：不启动不缓存；会话（聚灵/演出）容量 = 锁定配方 spCost
                GracePhase phase = grace.phase();
                return phase == GracePhase.PAYING || phase == GracePhase.PERFORM
                        ? grace.cost() : 0L;
            }
            if (activeMatch.patternId().equals(RitualBehaviors.SEII)) {
                // 星移刻意不做会话态覆盖：缓存是跨洗练持久的真实蓄水池，
                // 一次洗练只抽本次花费量，剩余 (阶梯值 - 花费) 结转下一次。
                return com.bitsson.gensokyou.item.weapon.SeiiNumbers.capacity(activeMatch.level());
            }
        }
        return DEFAULT_CORE_CAPACITY;
    }

    /** 杂项仪式（无专属缓存语义）的兜底缓存上限。 */
    public static final long DEFAULT_CORE_CAPACITY = 10_000L;

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
        long cap = GensokyouConfig.KAGUTSUICHI_BASE_CAPACITY.get();
        for (int i = 0; i < level; i++) {
            cap *= 4L;
        }
        return cap;
    }

    /** 梦渡之座缓存上限 = 基础值 × 4^等级（溢出直注槽内灵力核心，见 YumewatariBehavior）。 */
    public static long yumewatariCapacity(int level) {
        long cap = GensokyouConfig.YUMEWATARI_BASE_CAPACITY.get();
        for (int i = 0; i < level; i++) {
            cap *= 4L;
        }
        return cap;
    }

    /** 昼夜发电机（日轮天台/月影水镜）缓存上限 = 基值 × 4^等级。 */
    public static long daycycleCapacity(int level, long base) {
        long cap = base;
        for (int i = 0; i < level; i++) {
            cap *= 4L;
        }
        return cap;
    }

    /** 众生余录缓存上限 = 基值 × 容量倍率^等级（默认 40000 × 20^L）。 */
    public static long shujouCapacity(int level) {
        long cap = GensokyouConfig.SHUJOU_BASE_CAPACITY.get();
        long mult = GensokyouConfig.SHUJOU_CAPACITY_MULT.get();
        for (int i = 0; i < level; i++) {
            cap *= mult;
        }
        return cap;
    }

    /** 献祭工具仪式缓存上限 = 基值 × 4^等级。 */
    public static long sacrificeCapacity(int level) {
        return daycycleCapacity(level, GensokyouConfig.SACRIFICE_BASE_CAPACITY.get());
    }

    public static long kanayamahikoCapacity(int level) {
        return com.bitsson.gensokyou.ritual.behavior.KanayamahikoSmelting.capacity(level);
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
        long mult = GensokyouConfig.WUJINZANG_MULT.get();
        long value = base;
        for (int i = 0; i < level; i++) {
            if (value > Long.MAX_VALUE / Math.max(1L, mult)) {
                return Long.MAX_VALUE;
            }
            value *= mult;
        }
        return value;
    }

    /** 托管型储灵行为（如八方归元）：图案命中且行为实现 SpiritBank 时灵力四件套整体转发。 */
    @Nullable
    private SpiritBank bank() {
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

    // ---- 造化合成会话（zaohua-crafting）：存储与跃迁，推进逻辑在行为侧 ----

    /** 会话存储（世界无关纯逻辑，可单测）；推进逻辑在 ZaohuaCraftingService。 */
    public static final class CraftSession {
        private CraftPhase phase = CraftPhase.IDLE;
        private long sessionId;
        private @Nullable ResourceLocation recipeId;
        private long collected;
        /** 会话容量口径：锁定配方的 spCost；空闲为 0（不启动不缓存灵力）。 */
        private long cost;
        private int ticks;
        private final List<Integer> flightIds = new ArrayList<>();

        public CraftPhase phase() {
            return phase;
        }

        public long sessionId() {
            return sessionId;
        }

        public @Nullable ResourceLocation recipeId() {
            return recipeId;
        }

        public long collected() {
            return collected;
        }

        public long cost() {
            return cost;
        }

        public int ticks() {
            return ticks;
        }

        public List<Integer> flightIds() {
            return List.copyOf(flightIds);
        }

        /** 启动新会话：换代、锁配方、进聚灵；返回新会话 id。 */
        public long begin(ResourceLocation recipe, long spCost) {
            sessionId++;
            recipeId = recipe;
            cost = spCost;
            collected = 0L;
            ticks = 0;
            phase = CraftPhase.PAYING;
            flightIds.clear();
            return sessionId;
        }

        public void addCollected(long amount) {
            collected += amount;
        }

        /** 聚灵足额 → 进入飞行阶段。 */
        public void enterFlight(List<Integer> entities) {
            flightIds.clear();
            flightIds.addAll(entities);
            phase = CraftPhase.FLIGHT;
            ticks = 0;
        }

        /** 飞行计时推进一格。 */
        public void advanceFlight() {
            ticks++;
        }

        /** 清退回空闲（正常收尾/中止/取消共用；已抽灵力不退）。 */
        public void clear() {
            phase = CraftPhase.IDLE;
            recipeId = null;
            collected = 0L;
            cost = 0L;
            ticks = 0;
            flightIds.clear();
        }

        /** 该代飞行实体是否仍在会话保护下（实体服务端自弃判据）。 */
        public boolean holdsFlight(long id) {
            return phase == CraftPhase.FLIGHT && sessionId == id;
        }

        public void save(CompoundTag tag) {
            if (phase == CraftPhase.IDLE && flightIds.isEmpty() && collected == 0L && cost == 0L) {
                return;
            }
            tag.putString(TAG_CRAFT_PHASE, phase.name());
            tag.putLong(TAG_CRAFT_SESSION, sessionId);
            tag.putLong(TAG_CRAFT_COLLECTED, collected);
            tag.putLong(TAG_CRAFT_COST, cost);
            tag.putInt(TAG_CRAFT_FLIGHT_AGE, ticks);
            if (recipeId != null) {
                tag.putString(TAG_CRAFT_RECIPE, recipeId.toString());
            }
            if (!flightIds.isEmpty()) {
                ListTag ids = new ListTag();
                for (int id : flightIds) {
                    CompoundTag entry = new CompoundTag();
                    entry.putInt(TAG_CRAFT_ID, id);
                    ids.add(entry);
                }
                tag.put(TAG_CRAFT_FLIGHT_IDS, ids);
            }
        }

        public void load(CompoundTag tag) {
            if (!tag.contains(TAG_CRAFT_PHASE)) {
                return;
            }
            try {
                phase = CraftPhase.valueOf(tag.getString(TAG_CRAFT_PHASE));
            } catch (IllegalArgumentException exception) {
                phase = CraftPhase.IDLE;
            }
            sessionId = tag.getLong(TAG_CRAFT_SESSION);
            collected = tag.getLong(TAG_CRAFT_COLLECTED);
            cost = tag.getLong(TAG_CRAFT_COST);
            ticks = tag.getInt(TAG_CRAFT_FLIGHT_AGE);
            recipeId = tag.contains(TAG_CRAFT_RECIPE)
                    ? ResourceLocation.tryParse(tag.getString(TAG_CRAFT_RECIPE)) : null;
            flightIds.clear();
            for (var item : tag.getList(TAG_CRAFT_FLIGHT_IDS, CompoundTag.TAG_COMPOUND)) {
                flightIds.add(((CompoundTag) item).getInt(TAG_CRAFT_ID));
            }
        }
    }

    public CraftPhase craftPhase() {
        return craft.phase();
    }

    public long craftSessionId() {
        return craft.sessionId();
    }

    @Nullable
    public ResourceLocation craftRecipeId() {
        return craft.recipeId();
    }

    public long craftCollected() {
        return craft.collected();
    }

    /** 本会话锁定配方的 spCost（= 会话期容量；IDLE 为 0）。 */
    public long craftCost() {
        return craft.cost();
    }

    public int craftTicks() {
        return craft.ticks();
    }

    /** 本会话在飞实体 id（正常收尾时由行为逐个移除）。 */
    public List<Integer> craftFlightIds() {
        return craft.flightIds();
    }

    /** 聚灵入账一笔（抽取即消耗，不退）。 */
    public void addCraftCollected(long amount) {
        craft.addCollected(amount);
        setChanged();
    }

    /** 该代飞行实体是否仍在会话保护下（实体服务端自弃判据）。 */
    public boolean holdsFlightSession(long sessionId) {
        return craft.holdsFlight(sessionId);
    }

    /** 启动新合成会话：换代、锁配方（含 spCost 容量）、进聚灵；返回新会话 id。 */
    public long beginCraftSession(ResourceLocation recipeId, long spCost) {
        long id = craft.begin(recipeId, spCost);
        activeRecipeId = recipeId;
        setChanged();
        return id;
    }

    /** 聚灵足额 → 进入飞行阶段（扣料与飞行实体生成由行为同 tick 完成）。 */
    public void enterCraftFlight(List<Integer> flightEntityIds) {
        craft.enterFlight(flightEntityIds);
        setChanged();
    }

    /** 飞行阶段计时推进一格。 */
    public void advanceCraftFlightTick() {
        craft.advanceFlight();
    }

    /** 会话清退回 IDLE（正常收尾/中止/取消共用；已抽灵力不退）。 */
    public void clearCraftSession() {
        craft.clear();
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

    /** 神恩会话阶段：IDLE→PAYING（聚灵）→PERFORM（演出，效果已入账）→[REVIEW（洗练预览待决）]→IDLE。 */
    public enum GracePhase { IDLE, PAYING, PERFORM, REVIEW }

    /** 会话存储（世界无关纯逻辑，可单测）；推进逻辑在 YaoyorozuGraceService。 */
    public static final class GraceSession {
        private GracePhase phase = GracePhase.IDLE;
        private long sessionId;
        private @Nullable ResourceLocation recipeId;
        private @Nullable java.util.UUID initiator;
        /** 配方阶级（effect 的 N；1..5）。 */
        private int tier;
        /** true=洗练配方；false=进阶配方。 */
        private boolean refine;
        private long collected;
        /** 会话容量口径：锁定配方的 spCost；空闲为 0（不启动不缓存灵力）。 */
        private long cost;
        private int ticks;
        /** 洗练预览（当场制：不入 NBT，重启/清退即作废）。 */
        private @Nullable com.bitsson.gensokyou.spirit.grace.GraceNumbers.GraceRoll pendingRefine;

        public GracePhase phase() {
            return phase;
        }

        public long sessionId() {
            return sessionId;
        }

        public @Nullable ResourceLocation recipeId() {
            return recipeId;
        }

        public @Nullable java.util.UUID initiator() {
            return initiator;
        }

        public int tier() {
            return tier;
        }

        public boolean refine() {
            return refine;
        }

        public long collected() {
            return collected;
        }

        public long cost() {
            return cost;
        }

        public int ticks() {
            return ticks;
        }

        public @Nullable com.bitsson.gensokyou.spirit.grace.GraceNumbers.GraceRoll pendingRefine() {
            return pendingRefine;
        }

        /** 启动新会话：换代、锁配方/阶级/类型与 initiator，进聚灵；返回新会话 id。 */
        public long begin(ResourceLocation recipe, long spCost, java.util.UUID who,
                          int recipeTier, boolean isRefine) {
            sessionId++;
            recipeId = recipe;
            cost = spCost;
            initiator = who;
            tier = recipeTier;
            refine = isRefine;
            collected = 0L;
            ticks = 0;
            phase = GracePhase.PAYING;
            pendingRefine = null;
            return sessionId;
        }

        public void addCollected(long amount) {
            collected += amount;
        }

        /** 聚灵足额 → 效果已 apply，进入演出。 */
        public void enterPerform() {
            phase = GracePhase.PERFORM;
            ticks = 0;
        }

        public void advancePerform() {
            ticks++;
        }

        /** 演出结束（洗练线）：预览挂会话等待当场决策。 */
        public void enterReview(com.bitsson.gensokyou.spirit.grace.GraceNumbers.GraceRoll roll) {
            pendingRefine = roll;
            phase = GracePhase.REVIEW;
            ticks = 0;
        }

        /** 洗练 roll 在 apply 瞬间挂上（跨演出期携带；REVIEW 提升时转正）。 */
        public void stageRefine(com.bitsson.gensokyou.spirit.grace.GraceNumbers.GraceRoll roll) {
            pendingRefine = roll;
        }

        /** 演出结束升为待决策态（保留 staged 预览）。 */
        public void promoteReview() {
            phase = GracePhase.REVIEW;
            ticks = 0;
        }

        public void clearPendingRefine() {
            pendingRefine = null;
            if (phase == GracePhase.REVIEW) {
                phase = GracePhase.IDLE;
            }
        }

        /** 清退回空闲（正常收尾/中止/取消共用）。 */
        public void clear() {
            phase = GracePhase.IDLE;
            recipeId = null;
            initiator = null;
            tier = 0;
            refine = false;
            collected = 0L;
            cost = 0L;
            ticks = 0;
            pendingRefine = null;
        }

        public void save(CompoundTag tag) {
            if (phase == GracePhase.IDLE) {
                return;
            }
            // REVIEW 也写盘：待决预览永久存续（跨区块卸载 / 服务器重启），
            // 决策权绑定 initiator，不会被他人关闭界面或掉线吞掉。
            tag.putString(TAG_GRACE_PHASE, phase.name());
            tag.putLong(TAG_GRACE_SESSION, sessionId);
            tag.putLong(TAG_GRACE_COLLECTED, collected);
            tag.putLong(TAG_GRACE_COST, cost);
            tag.putInt(TAG_GRACE_TICKS, ticks);
            tag.putInt(TAG_GRACE_TIER, tier);
            tag.putBoolean(TAG_GRACE_REFINE, refine);
            if (recipeId != null) {
                tag.putString(TAG_GRACE_RECIPE, recipeId.toString());
            }
            if (initiator != null) {
                tag.putUUID(TAG_GRACE_INITIATOR, initiator);
            }
            // REVIEW 的待决 roll 必须落盘：否则重启后 REVIEW 回来了但没有可决策的 roll，
            // 玩家既看不到结果也无法"全收 / 保留"，等于待决预览丢失。
            if (phase == GracePhase.REVIEW && pendingRefine != null) {
                tag.put(TAG_GRACE_PENDING, writeGraceRoll(pendingRefine));
            }
        }

        private static CompoundTag writeGraceRoll(
                com.bitsson.gensokyou.spirit.grace.GraceNumbers.GraceRoll roll) {
            CompoundTag t = new CompoundTag();
            t.putInt("tier", roll.tier());
            t.putFloat("maxGain", roll.maxGain());
            t.putFloat("powerGain", roll.powerGain());
            ListTag list = new ListTag();
            for (var entry : roll.contributions().entrySet()) {
                CompoundTag one = new CompoundTag();
                one.putString("k", entry.getKey().id());
                one.putFloat("v", entry.getValue());
                list.add(one);
            }
            t.put("contrib", list);
            return t;
        }

        private static @Nullable com.bitsson.gensokyou.spirit.grace.GraceNumbers.GraceRoll
        readGraceRoll(CompoundTag t) {
            if (!t.contains("contrib", Tag.TAG_LIST)) {
                return null;
            }
            Map<com.bitsson.gensokyou.spirit.attr.AttributeKey, Float> contrib = new LinkedHashMap<>();
            ListTag list = t.getList("contrib", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag one = list.getCompound(i);
                var key = com.bitsson.gensokyou.spirit.attr.AttributeKey.byId(one.getString("k"));
                if (key != null) {
                    contrib.put(key, one.getFloat("v"));
                }
            }
            return new com.bitsson.gensokyou.spirit.grace.GraceNumbers.GraceRoll(
                    t.getInt("tier"), t.getFloat("maxGain"), t.getFloat("powerGain"),
                    Map.copyOf(contrib));
        }

        /**
         * 读档：PAYING 复活（initiator 离线时由服务侧首 tick 取消退还）；REVIEW 复活为待决态
         * （暂存的 roll 不写盘而是按 tier 重新 roll —— 精确复现需要额外持久化，收益不匹配）；
         * PERFORM 视为不可续作，清退。
         */
        public void load(CompoundTag tag) {
            if (!tag.contains(TAG_GRACE_PHASE)) {
                return;
            }
            try {
                phase = GracePhase.valueOf(tag.getString(TAG_GRACE_PHASE));
            } catch (IllegalArgumentException exception) {
                phase = GracePhase.IDLE;
            }
            if (phase == GracePhase.IDLE) {
                return;
            }
            if (phase == GracePhase.PERFORM) {
                clear(); // 演出不可续作：效果已入账，清态不重演
                return;
            }
            sessionId = tag.getLong(TAG_GRACE_SESSION);
            collected = tag.getLong(TAG_GRACE_COLLECTED);
            cost = tag.getLong(TAG_GRACE_COST);
            ticks = tag.getInt(TAG_GRACE_TICKS);
            tier = tag.getInt(TAG_GRACE_TIER);
            refine = tag.getBoolean(TAG_GRACE_REFINE);
            recipeId = tag.contains(TAG_GRACE_RECIPE)
                    ? ResourceLocation.tryParse(tag.getString(TAG_GRACE_RECIPE)) : null;
            initiator = tag.hasUUID(TAG_GRACE_INITIATOR) ? tag.getUUID(TAG_GRACE_INITIATOR) : null;
            pendingRefine = null;
            if (phase == GracePhase.REVIEW && tag.contains(TAG_GRACE_PENDING, Tag.TAG_COMPOUND)) {
                pendingRefine = readGraceRoll(tag.getCompound(TAG_GRACE_PENDING));
            }
            // REVIEW 却读不出待决 roll（老存档 / 词条键已退役）→ 退回 IDLE，
            // 绝不停在一个"有 REVIEW 相位、无可决策内容"的死状态。
            if (phase == GracePhase.REVIEW && pendingRefine == null) {
                phase = GracePhase.IDLE;
            }
        }
    }

    private final GraceSession grace = new GraceSession();

    /** 星移之仪会话态（第三份会话持有者，对照 craft / grace 的既有范式）。 */
    private final com.bitsson.gensokyou.ritual.behavior.SeiiSession seii =
            new com.bitsson.gensokyou.ritual.behavior.SeiiSession();

    public com.bitsson.gensokyou.ritual.behavior.SeiiSession seiiSession() {
        return seii;
    }

    /** 开始星移会话：锁定配方、记花费与目标核阶、进 PAYING。 */
    public long beginSeiiSession(ResourceLocation recipeId, long spCost,
                                 int coreTier, java.util.UUID who) {
        long id = seii.begin(recipeId, spCost, coreTier, who);
        activeRecipeId = recipeId;
        setChanged();
        return id;
    }

    public void addSeiiCollected(long amount) {
        seii.addCollected(amount);
        setChanged();
    }

    public void enterSeiiPerform(com.bitsson.gensokyou.ritual.behavior.SeiiSession.Pending staged) {
        seii.stage(staged);
        setChanged();
    }

    public void tickSeiiPerform() {
        seii.tickPerform();
    }

    public void promoteSeiiReview() {
        seii.promoteReview();
        setChanged();
    }

    public void clearSeiiSession() {
        seii.clear();
        activeRecipeId = null;
        setChanged();
    }

    public GraceSession graceSession() {
        return grace;
    }

    public GracePhase gracePhase() {
        return grace.phase();
    }

    public long beginGraceSession(ResourceLocation recipeId, long spCost, java.util.UUID who,
                                  int tier, boolean refine) {
        long id = grace.begin(recipeId, spCost, who, tier, refine);
        activeRecipeId = recipeId;
        setChanged();
        return id;
    }

    public void addGraceCollected(long amount) {
        grace.addCollected(amount);
        setChanged();
    }

    public void enterGracePerform() {
        grace.enterPerform();
        setChanged();
    }

    public void advanceGracePerformTick() {
        grace.advancePerform();
    }

    public void enterGraceReview(com.bitsson.gensokyou.spirit.grace.GraceNumbers.GraceRoll roll) {
        grace.enterReview(roll);
        setChanged();
    }

    /** 演出收尾升 REVIEW（staged 预览已在会话内，无需再传）。 */
    public void promoteGraceReview() {
        grace.promoteReview();
        setChanged();
    }

    /** 神恩会话清退（收尾/中止/取消/预览作废共用）。 */
    public void clearGraceSession() {
        grace.clear();
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

    // ---- 仪式专用物品槽（星移之仪的增幅核目标槽） ----
    //
    // 刻意与「祭品台」分开：祭品台是配方催化剂的载体且一台一件，若核也占台位会挤掉催化剂
    // （1 阶只有 4 台）。核有自己的 GUI 槽位，祭品台全部留给催化剂。

    private ItemStack seiiTargetStack = ItemStack.EMPTY;

    /** 槽内增幅核（空栈表示无）。 */
    public ItemStack seiiTargetStack() {
        return seiiTargetStack;
    }

    /** 写入槽内增幅核（自动归一为 1 个；空栈写空）。 */
    public void setSeiiTargetStack(ItemStack stack) {
        this.seiiTargetStack = (stack == null || stack.isEmpty())
                ? ItemStack.EMPTY : stack.copyWithCount(1);
        setChanged();
    }

    private final IItemHandler seiiTargetHandler = new SeiiTargetHandler();

    /** 目标槽的 item handler 视图（供 RitualCoreMenu 的 TargetSlot 绑定）。 */
    public IItemHandler seiiTargetHandler() {
        return seiiTargetHandler;
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
        long carry = fillCarry + (long) spiritCore.fillRatePerSecond() * 1000L;
        fillCarry = carry % 1000L;
        long want = Math.min(carry / 1000L, stored);
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
    private final IItemHandler batteryHandler = new BatteryHandler();

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
        return rateCarry;
    }

    public void setRateCarry(long value) {
        rateCarry = value;
    }

    public long fillCarry() {
        return fillCarry;
    }

    public void setFillCarry(long value) {
        fillCarry = value;
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
        ResourceLocation id = activeMatch.patternId();
        if (id.equals(RitualBehaviors.RESONANCE)) {
            return buildRelayRenderState();
        }
        if (id.equals(RitualBehaviors.KAGUTSUICHI)) {
            // 注意：本 kind 的 maxY 语义 = 结构水平半径（格），供客户端火柱铺满台面用
            // （台位坐标可能因结构判定差异缺失，半径是权威且稳定的散布依据）
            return new RitualRenderState(RitualRenderState.KIND_KAGUTSUICHI, enabled,
                    activeMatch.level(), boundsMinY, structureRadiusXZ(), 0,
                    kagutsuchiPillarAnchors(), 0,
                    isBurning() ? RitualRenderState.MASK_KAGUTSUCHI_BURNING : 0L);
        }
        if (id.equals(RitualBehaviors.BAFANG_GUIYUAN)) {
            return new RitualRenderState(RitualRenderState.KIND_BAFANG, enabled,
                    activeMatch.level(), 0, 0, 0, new long[0], 0, 0L);
        }
        if (id.equals(RitualBehaviors.SEII)) {
            return buildSeiiRenderState();
        }
        if (id.equals(RitualBehaviors.KANAYAMAHIKO)) {
            // maxY 语义 = 结构水平半径（格）：客户端据此铺满密集火星场
            // 锚点与燃烧掩码共用同一份规范序台位，保证 bit(i+1) 与锚点 i 严格对齐
            List<BlockPos> forgePedestals = pedestalPositions();
            return new RitualRenderState(RitualRenderState.KIND_KANAYAMAHIKO, enabled,
                    activeMatch.level(), boundsMinY, structureRadiusXZ(), 0,
                    kanayamahikoPillarAnchors(forgePedestals), 0,
                    com.bitsson.gensokyou.ritual.behavior.KanayamahikoSmelting
                            .renderBurnMask(forgePedestals, kanayamahikoSession));
        }
        if (id.equals(RitualBehaviors.WUJINZANG)) {
            return new RitualRenderState(RitualRenderState.KIND_WUJINZANG, enabled,
                    activeMatch.level(), boundsMinY, boundsMaxY, 0,
                    com.bitsson.gensokyou.ritual.behavior.WujinzangStorage
                            .laserAnchors(this, activeMatch),
                    0, 0L);
        }
        if (RitualBehaviors.isToolSacrifice(id) || id.equals(RitualBehaviors.SHUJOU)
                || id.equals(RitualBehaviors.HOUJOUNO_TEIHOU)) {
            if (sacrificeFxTicks <= 0) {
                return null;
            }
            // 复用字段：minY=光柱高度(格)、maxY=剩余刻、period=色索引(0..6)
            return new RitualRenderState(RitualRenderState.KIND_SACRIFICE, enabled,
                    activeMatch.level(),
                    (int) Math.round(GensokyouConfig.FX_PILLAR_HEIGHT.get()),
                    sacrificeFxTicks,
                    RitualBehaviors.sacrificeColorIndex(id),
                    new long[0], 0, 0L);
        }
        return null;
    }

    /** 结构水平半径（格，向上取整）：核心到最远结构块的 XZ 距离，供迦具土火柱铺面参考。 */
    private int structureRadiusXZ() {
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

    /**
     * 迦具土火柱发射锚点：复用既有规范序祭品台位（按 BE 类型判定），转 long[] 并截断到通道上限。
     */
    private long[] kagutsuchiPillarAnchors() {
        List<BlockPos> peds = pedestalPositions();
        int cap = RitualRenderState.MAX_CHANNELS;
        long[] out = new long[Math.min(peds.size(), cap)];
        for (int i = 0; i < out.length; i++) {
            out[i] = peds.get(i).asLong();
        }
        return out;
    }

    /**
     * 煅炉火柱锚点：与 {@link com.bitsson.gensokyou.ritual.behavior.KanayamahikoSmelting
     * #renderBurnMask} 传入的台位列表完全一致，故"第 i 位点亮"必然对应"第 i 个台位冒火"。
     */
    private long[] kanayamahikoPillarAnchors(List<BlockPos> pedestals) {
        int cap = RitualRenderState.MAX_CHANNELS;
        long[] out = new long[Math.min(pedestals.size(), cap)];
        for (int i = 0; i < out.length; i++) {
            out[i] = pedestals.get(i).asLong();
        }
        return out;
    }

    @Nullable
    private RitualRenderState buildRelayRenderState() {
        int cap = RitualRenderState.MAX_CHANNELS;
        int total = Math.min(inLinks.size() + outLinks.size(), cap);
        long[] links = new long[total];
        int filled = 0;
        for (RitualLink link : inLinks) {
            if (filled >= cap) {
                break;
            }
            links[filled++] = link.corePos().asLong();
        }
        int inCount = filled;
        for (RitualLink link : outLinks) {
            if (filled >= cap) {
                break;
            }
            links[filled++] = link.corePos().asLong();
        }
        // 截断防御：配额翻倍越 64 时此处丢尾通道渲染（结算不受影响），届时掩码改 long[]
        return new RitualRenderState(RitualRenderState.KIND_RELAY, enabled, activeMatch.level(),
                boundsMinY, boundsMaxY,
                Math.max(1, GensokyouConfig.SETTLE_PERIOD_TICKS.get()), links, inCount,
                RitualRenderState.clampMask(resoMovingMask, total));
    }

    /**
     * 星移演出渲染态：只下发"演出中 + 档位 + 起始 gameTime + 总时长"四个标量，
     * 逐帧粒子表现全在客户端 BER 本地生成（稳态零持续包）。
     *
     * <p>{@code startTick = gameTime - session.ticks()}：PERFORM 期间两者每 tick 同步 +1，
     * 故该差值恒定且自校正 —— 无需给会话新增持久化字段，也不会累积漂移。
     */
    private RitualRenderState buildSeiiRenderState() {
        com.bitsson.gensokyou.ritual.behavior.SeiiSession session = seiiSession();
        boolean performing = level != null && !level.isClientSide
                && session.phase() == com.bitsson.gensokyou.ritual.behavior.SeiiSession.Phase.PERFORM;
        int duration = com.bitsson.gensokyou.config.GensokyouConfig.SEII_PERFORM_TICKS.get();
        int startTick = performing
                ? (int) (level.getGameTime() - session.ticks()) : 0;
        return new RitualRenderState(RitualRenderState.KIND_SEII, performing,
                activeMatch.level(), startTick, duration, 0, new long[0], 0, 0L);
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
        if (enabled != value) {
            enabled = value;
            setChanged();
        }
    }

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
    private final IItemHandler itemHandler = new PedestalItemHandler();
    private final IItemHandler wujinzangHandler =
            new com.bitsson.gensokyou.ritual.behavior.WujinzangStorage.ProxyHandler(this);

    /** 物品接入面按图案分派：无尽藏 = 跨晶块合并箱，其余 = 祭品台代理箱。 */
    public IItemHandler itemHandler() {
        if (activeMatch != null && RitualBehaviors.WUJINZANG.equals(activeMatch.patternId())) {
            return wujinzangHandler;
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
        long carry = cacheFillCarry + (long) spiritCore.fillRatePerSecond() * 1000L;
        cacheFillCarry = carry % 1000L;
        long want = Math.min(carry / 1000L, space);
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
    private List<BlockPos> pedestalPositions() {
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

    private RitualPedestalBlockEntity pedestalAt(int slot) {
        List<BlockPos> positions = pedestalPositions();
        if (slot < 0 || slot >= positions.size() || level == null) {
            return null;
        }
        return level.getBlockEntity(positions.get(slot))
                instanceof RitualPedestalBlockEntity pedestal ? pedestal : null;
    }

    /** 活代理实现：每次调用现场解析台位并直读直写祭品台 BE，核心零存储。 */
    private final class PedestalItemHandler implements IItemHandler {

        @Override
        public int getSlots() {
            return pedestalPositions().size();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            RitualPedestalBlockEntity pedestal = pedestalAt(slot);
            return pedestal == null ? ItemStack.EMPTY : pedestal.getHeld();
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (stack.isEmpty()) {
                return ItemStack.EMPTY;
            }
            RitualPedestalBlockEntity pedestal = pedestalAt(slot);
            if (pedestal == null || !pedestal.getHeld().isEmpty()) {
                return stack;
            }
            if (!simulate) {
                pedestal.setHeld(stack.copyWithCount(1));
            }
            return stack.getCount() <= 1 ? ItemStack.EMPTY : stack.copyWithCount(stack.getCount() - 1);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (amount <= 0) {
                return ItemStack.EMPTY;
            }
            RitualPedestalBlockEntity pedestal = pedestalAt(slot);
            if (pedestal == null || pedestal.getHeld().isEmpty()) {
                return ItemStack.EMPTY;
            }
            ItemStack held = pedestal.getHeld();
            int take = Math.min(amount, held.getCount());
            if (!simulate) {
                int remaining = held.getCount() - take;
                pedestal.setHeld(remaining <= 0 ? ItemStack.EMPTY : held.copyWithCount(remaining));
            }
            return held.copyWithCount(take);
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            if (stack.isEmpty()) {
                return false;
            }
            RitualPedestalBlockEntity pedestal = pedestalAt(slot);
            return pedestal != null && pedestal.getHeld().isEmpty();
        }
    }

    /** 电池槽单槽代理：仅收灵力核心物品，直读直写 BE 字段（SlotItemHandler.set 要求可写接口）。 */
    /**
     * 星移之仪的增幅核目标槽 handler：只收增幅核，永远 1 个（与祭品台的一台一件同理）。
     * 允许取出（玩家要把核拿回去），但取出前若有待决洗练，服务侧复验会发现核已不在而拒绝写入。
     */
    private final class SeiiTargetHandler implements net.neoforged.neoforge.items.IItemHandlerModifiable {

        @Override
        public void setStackInSlot(int slot, ItemStack stack) {
            if (slot == 0) {
                setSeiiTargetStack(stack);
            }
        }

        @Override
        public int getSlots() {
            return 1;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return slot == 0 ? seiiTargetStack : ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (slot != 0 || !isItemValid(slot, stack)) {
                return stack;
            }
            if (!simulate && seiiTargetStack.isEmpty()) {
                setSeiiTargetStack(stack);
                return stack.copyWithCount(stack.getCount() - 1);
            }
            return simulate ? stack : stack.copyWithCount(0);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot != 0 || seiiTargetStack.isEmpty()) {
                return ItemStack.EMPTY;
            }
            int n = Math.min(amount, seiiTargetStack.getCount());
            ItemStack out = seiiTargetStack.copyWithCount(n);
            if (!simulate) {
                setSeiiTargetStack(ItemStack.EMPTY);
            }
            return out;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot == 0 && stack.getItem() instanceof com.bitsson.gensokyou.item.weapon.AmpCoreItem;
        }
    }

    private final class BatteryHandler implements net.neoforged.neoforge.items.IItemHandlerModifiable {
        @Override
        public void setStackInSlot(int slot, ItemStack stack) {
            if (slot == 0 && isItemValid(slot, stack)) {
                setBatteryStack(stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1));
            }
        }

        @Override
        public int getSlots() {
            return 1;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return slot == 0 ? batteryStack : ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (slot != 0 || !isItemValid(slot, stack)) {
                return stack;
            }
            if (!simulate) {
                setBatteryStack(stack.copyWithCount(1));
            }
            return stack.getCount() <= 1 ? ItemStack.EMPTY : stack.copyWithCount(stack.getCount() - 1);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot != 0 || amount <= 0 || batteryStack.isEmpty()) {
                return ItemStack.EMPTY;
            }
            ItemStack copy = batteryStack.copyWithCount(1);
            if (!simulate) {
                setBatteryStack(ItemStack.EMPTY);
            }
            return copy;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot == 0 && stack.getItem()
                    instanceof com.bitsson.gensokyou.spirit.SpiritCoreItem;
        }
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
        if (!seiiTargetStack.isEmpty()) {
            tag.put(TAG_SEII_TARGET, seiiTargetStack.save(registries));
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
        if (rateCarry != 0) {
            tag.putLong(TAG_RATE_ACCUM, rateCarry);
        }
        if (fillCarry != 0) {
            tag.putLong(TAG_FILL_ACCUM, fillCarry);
        }
        craft.save(tag);
        grace.save(tag);
        seii.save(tag);
        if (lastPowered) {
            tag.putBoolean(TAG_LAST_POWERED, true);
        }
        if (actionCooldown > 0) {
            tag.putInt(TAG_ACTION_COOLDOWN, actionCooldown);
        }
        if (wujinzangVault != null) {
            tag.put(TAG_WUJINZANG_VAULT, wujinzangVault.copy());
        }
        if (cacheFillCarry != 0L) {
            tag.putLong("WujinzangCacheCarry", cacheFillCarry);
        }
        kanayamahikoSession.save(tag, registries);
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
        seiiTargetStack = tag.contains(TAG_SEII_TARGET)
                ? ItemStack.parseOptional(registries, tag.getCompound(TAG_SEII_TARGET))
                : ItemStack.EMPTY;
        if (tag.contains(TAG_BURN)) {
            CompoundTag burn = tag.getCompound(TAG_BURN);
            burnFuelIcon = burn.contains(TAG_BURN_FUEL)
                    ? ItemStack.parseOptional(registries, burn.getCompound(TAG_BURN_FUEL))
                    : ItemStack.EMPTY;
            burnTotalTicks = burn.getInt(TAG_BURN_TOTAL);
            burnRemainingTicks = Math.min(burn.getInt(TAG_BURN_REMAINING), burnTotalTicks);
        }
        rateCarry = tag.getLong(TAG_RATE_ACCUM);
        fillCarry = tag.getLong(TAG_FILL_ACCUM);
        craft.load(tag);
        grace.load(tag);
        seii.load(tag);
        lastPowered = tag.getBoolean(TAG_LAST_POWERED);
        actionCooldown = Math.max(0, tag.getInt(TAG_ACTION_COOLDOWN));
        wujinzangVault = tag.contains(TAG_WUJINZANG_VAULT)
                ? tag.getCompound(TAG_WUJINZANG_VAULT).copy() : null;
        cacheFillCarry = tag.getLong("WujinzangCacheCarry");
        kanayamahikoSession.load(tag, registries);
        if (tag.contains(TAG_RENDER_STATE)) {
            renderState = RitualRenderState.fromTag(tag.getCompound(TAG_RENDER_STATE));
        }
    }
}
