package com.bitsson.gensokyou.ritual;

/**
 * ×scale 定点的进位累加器：把每 tick 的定点增量累积成整数份额，不足 1 份的零头跨 tick 结转，
 * 防止整除截断造成静默丢量。原 {@code rateCarry}/{@code fillCarry}/{@code cacheFillCarry}/
 * {@code reiyokuRateCarry} 四套手写 long 字段统一到本类。
 *
 * <p>世界无关、无状态依赖，可直接单测。
 */
public final class FixedPointAccumulator {

    private long carry;

    /** 累加一份定点增量，返回本次可整除的整数份额；零头留作下次结转。*/
    public long accumulate(long fixedDelta, long scale) {
        carry += fixedDelta;
        long whole = carry / scale;
        carry %= scale;
        return whole;
    }

    public long carry() {
        return carry;
    }

    public void setCarry(long value) {
        this.carry = value;
    }

    public void clear() {
        carry = 0L;
    }

    /**
     * 纯函数版 {@link #accumulate}：给定旧零头与定点增量，返回 {@code [整除份额, 新零头]}，
     * 不持有状态——供 {@code chargeStep} 等纯函数复用累加语义。
     */
    public static long[] step(long carry, long fixedDelta, long scale) {
        long accum = carry + fixedDelta;
        return new long[]{accum / scale, accum % scale};
    }
}
