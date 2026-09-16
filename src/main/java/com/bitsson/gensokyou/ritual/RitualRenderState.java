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
    /** 献祭仪式产出光柱：minY=光柱高度(格)、maxY=剩余刻、period=色索引(0..3)。 */
    public static final int KIND_SACRIFICE = 4;

    /** 位掩码通道上限（当前 L5 配额合计 40 &lt; 64）。 */
    public static final int MAX_CHANNELS = 64;

    /** 迦具土燃烧标记（kind=KAGUTSUICHI 时 movingMask 的 bit0）。 */
    public static final long MASK_KAGUTSUCHI_BURNING = 1L;

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

    /** 把 mask 收敛到前 bits 位（链接截断/重置时防残留位点亮错位通道）。 */
    public static long clampMask(long mask, int bits) {
        if (bits >= MAX_CHANNELS) {
            return mask;
        }
        return bits <= 0 ? 0L : mask & ((1L << bits) - 1L);
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
