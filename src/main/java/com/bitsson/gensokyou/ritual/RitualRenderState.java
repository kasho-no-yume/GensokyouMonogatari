package com.bitsson.gensokyou.ritual;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

import java.util.Arrays;

/**
 * 仪式核心的最小渲染态（ritual-fx-overhaul D1）：仅承载"运行态特效如何到达客户端"
 * 所需数据，服务端权威结算 MUST NOT 读取本状态。
 *
 * <p>{@code kind} 决定辅助字段的解释方式（字段按 kind 复用，语义互不串扰）：
 * <ul>
 *   <li>{@link #KIND_RELAY}：linkPos=链接目标（in 前 out 后，与 {@code movingMask} 位序
 *       一一对应），minY/maxY=螺旋纵向范围，period=结算周期。</li>
 *   <li>{@link #KIND_KAGUTSUICHI}：linkPos=祭品台 'P' 坐标（规范序，可能为空），
 *       movingMask bit0=燃烧中，**maxY=结构水平半径（格）**——火柱铺面依据（不依赖台位数据）。</li>
 *   <li>{@link #KIND_BAFANG}：仅 enabled+tier 有意义。</li>
 *   <li>{@link #KIND_KANAYAMAHIKO}：linkPos=祭品台 'P' 坐标（规范序），
 *       maxY=结构水平半径（格），movingMask bit0=存在燃烧任务、bit(i+1)=第 i 台位燃烧。</li>
 *   <li>{@link #KIND_NONE}：客户端不绘制。</li>
 * </ul>
 * 位宽 {@code long} 上限 {@link #MAX_CHANNELS} 条，超限由构建侧截断（未来配额翻倍越界
 * 时改 long[]，见 resonance-relay design R4）。
 */
public record RitualRenderState(int kind, boolean enabled, int tier, int minY, int maxY, int period,
                                long[] linkPos, int inCount, long movingMask) {

    /** 无特效（清零态）。 */
    public static final int KIND_NONE = 0;
    public static final int KIND_RELAY = 1;
    public static final int KIND_KAGUTSUICHI = 2;
    public static final int KIND_BAFANG = 3;
    /** 献祭仪式产出光柱：minY=光柱高度(格)、maxY=剩余刻、period=色索引(0..6)。 */
    public static final int KIND_SACRIFICE = 4;
    /** 无尽藏之仪：minY/maxY=结构 Y 范围（雾带高度），linkPos=底座 8 个激光锚点（绝对坐标）。 */
    public static final int KIND_WUJINZANG = 5;
    /**
     * 金山彦命煅炉：linkPos=祭品台 'P' 坐标（规范序，可能为空），
     * maxY=结构水平半径（格），movingMask bit0=存在燃烧任务、bit(i+1)=第 i 个台位在燃烧。
     */
    public static final int KIND_KANAYAMAHIKO = 6;
    /**
     * 星移之仪洗练演出：{@code enabled} = 演出中；{@code tier} = 仪式阶（1/3/5，决定演出档）；
     * {@code minY} = <b>演出起始 gameTime</b>（客户端据此推进进度，MUST NOT 逐帧同步）；
     * {@code maxY} = 演出总时长 tick。
     */
    public static final int KIND_SEII = 7;
    /**
     * 百鬼夜行召唤演出：{@code enabled} = 会话进行中；{@code tier} = 结构层号（1/2/3，
     * 一切规模标量的唯一来源）；{@code movingMask} = <b>相位序号</b>
     * （0=IDLE / 1=CHARGING / 2=BURST / 3=PILLAR）；{@code minY} = <b>爆散起始 gameTime</b>
     * （绝对锚点，由服务端在缓存填满那一刻落下，MUST 持久化）；{@code maxY} = 爆散时长；
     * {@code period} = 降临光柱保持时长。
     *
     * <p>充能段的球与闪电 MUST 由<b>相位</b>判定而非时间轴——充能时长随玩家供灵而变，
     * 按时间推进会在零供灵时把整段演出压缩成一秒内播完。
     * {@link #linkPos} 不使用（无逐台通道）。
     */
    public static final int KIND_SUMMON = 8;

    /** 位掩码通道上限（当前 L5 配额合计 40 < 64）。 */
    public static final int MAX_CHANNELS = 64;

    /** 迦具土燃烧标记（kind=KAGUTSUICHI 时 movingMask 的 bit0）。 */
    public static final long MASK_KAGUTSUCHI_BURNING = 1L;
    /** 煅炉"存在燃烧任务"标记（kind=KANAYAMAHIKO 时 movingMask 的 bit0）。 */
    public static final long MASK_KANAYAMAHIKO_BURNING = 1L;

    public static final RitualRenderState EMPTY =
            new RitualRenderState(KIND_NONE, false, 0, 0, 0, 20, new long[0], 0, 0L);

    public RitualRenderState {
        linkPos = linkPos == null ? new long[0] : linkPos.clone();
    }

    public int channelCount() {
        return linkPos.length;
    }

    public BlockPos linkAt(int index) {
        return BlockPos.of(linkPos[index]);
    }

    /** 通道 i 是否"最近结算周期实搬 >0"（出向：i ≥ inCount）。 */
    public boolean channelMoving(int index) {
        return index >= 0 && index < linkPos.length && index < MAX_CHANNELS
                && (movingMask & (1L << index)) != 0L;
    }

    /** kind=KAGUTSUICHI：当前是否有批次在烧（含空烧期）。 */
    public boolean burning() {
        return kind == KIND_KAGUTSUICHI && (movingMask & MASK_KAGUTSUCHI_BURNING) != 0L;
    }

    /** kind=KANAYAMAHIKO：当前是否有任务在燃烧。 */
    public boolean forgeBurning() {
        return kind == KIND_KANAYAMAHIKO && (movingMask & MASK_KANAYAMAHIKO_BURNING) != 0L;
    }

    /**
     * kind=KANAYAMAHIKO：第 index 个祭品台是否在燃烧。
     *
     * <p>bit0 被"存在燃烧任务"占用，故台位 i 占用 bit(i+1)；
     * 这样 63 个台位可独立点亮（结构上限 32，留有余量）。
     */
    public boolean forgePedestalBurning(int index) {
        if (kind != KIND_KANAYAMAHIKO || index < 0 || index >= linkPos.length
                || index + 1 >= MAX_CHANNELS) {
            return false;
        }
        return (movingMask & (1L << (index + 1))) != 0L;
    }

    /** 构造煅炉燃烧掩码：bit0=任一燃烧，bit(i+1)=第 i 个台位燃烧。 */
    public static long forgeBurnMask(boolean anyBurning, long pedestalBurningMask) {
        long high = clampMask(pedestalBurningMask, MAX_CHANNELS - 1) << 1;
        return anyBurning ? (high | MASK_KANAYAMAHIKO_BURNING) : high;
    }

    /** 把 mask 收敛到前 bits 位（链接截断/重置时防残留位点亮错位通道）。 */
    public static long clampMask(long mask, int bits) {
        if (bits >= MAX_CHANNELS) {
            return mask;
        }
        return bits <= 0 ? 0L : mask & ((1L << bits) - 1L);
    }

    /**
     * kind=SEII：本客户端进入演出态的<b>起始 gameTime</b>（含小数 tick 由客户端自行插值）。
     * 非演出态返回 0。
     */
    public int seiiStartTick() {
        return kind == KIND_SEII && enabled ? minY : 0;
    }

    /** kind=SEII：演出总时长 tick。 */
    public int seiiDurationTicks() {
        return kind == KIND_SEII ? Math.max(1, maxY) : 1;
    }

    /**
     * kind=SUMMON：本客户端进入<b>演出段</b>的起始 gameTime。非演出段返回 0。
     */
    public int summonStartTick() {
        return kind == KIND_SUMMON && enabled ? minY : 0;
    }

    /** kind=SUMMON：相位序号（0=IDLE / 1=CHARGING / 2=BURST / 3=PILLAR）。 */
    public int summonPhase() {
        return kind == KIND_SUMMON ? (int) movingMask : 0;
    }

    /** kind=SUMMON：是否处于充能段（球与闪电可见）。 */
    public boolean summonCharging() {
        return kind == KIND_SUMMON && enabled && summonPhase() == 1;
    }

    /**
     * kind=SUMMON：<b>演出段</b>已进行 tick（从爆散锚点算）。充能段恒返回 0。
     */
    public int summonElapsed(int gameTime) {
        if (kind != KIND_SUMMON || !enabled || minY < 0) {
            return 0;
        }
        return Math.max(0, gameTime - minY);
    }

    /** kind=SUMMON：爆散段长度（tick）。 */
    public int summonBurstTicks() {
        return kind == KIND_SUMMON ? Math.max(1, maxY) : 1;
    }

    /** kind=SUMMON：降临光柱保持时长（tick）。 */
    public int summonPillarHoldTicks() {
        return kind == KIND_SUMMON ? Math.max(1, period) : 1;
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putByte("K", (byte) kind);
        tag.putByte("E", (byte) (enabled ? 1 : 0));
        tag.putByte("T", (byte) tier);
        tag.putInt("Y0", minY);
        tag.putInt("Y1", maxY);
        tag.putByte("P", (byte) Math.min(period, 255));
        tag.putInt("NI", inCount);
        if (linkPos.length > 0) {
            tag.putLongArray("L", linkPos);
        }
        if (movingMask != 0L) {
            tag.putLong("M", movingMask);
        }
        return tag;
    }

    public static RitualRenderState fromTag(CompoundTag tag) {
        long[] links = tag.contains("L") ? tag.getLongArray("L") : new long[0];
        // 旧档/旧包无 "K" 键：按 relay 语义读（kind 引入前的唯一产态者）
        int kind = tag.contains("K") ? tag.getByte("K") : KIND_RELAY;
        return new RitualRenderState(kind, tag.getByte("E") != 0, tag.getByte("T"),
                tag.getInt("Y0"), tag.getInt("Y1"), tag.getByte("P") & 0xFF,
                links, tag.getInt("NI"), tag.getLong("M"));
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof RitualRenderState state
                && kind == state.kind
                && enabled == state.enabled && tier == state.tier
                && minY == state.minY && maxY == state.maxY && period == state.period
                && inCount == state.inCount && movingMask == state.movingMask
                && Arrays.equals(linkPos, state.linkPos);
    }

    @Override
    public int hashCode() {
        int result = kind;
        result = 31 * result + Boolean.hashCode(enabled);
        result = 31 * result + tier;
        result = 31 * result + minY;
        result = 31 * result + maxY;
        result = 31 * result + period;
        result = 31 * result + inCount;
        result = 31 * result + Long.hashCode(movingMask);
        result = 31 * result + Arrays.hashCode(linkPos);
        return result;
    }
}
