package com.bitsson.gensokyou.ritual.behavior;

/**
 * 端点每结算周期速率账本：单方向（收或发）速率的唯一权威。
 *
 * <p>以 {@code gameTime / period} 幂等锁存——同一周期内无论多少调用方请求，都共享同一份剩余额度，
 * MUST NOT 每次调用重新发放（多塔共拉一源 / 同周期多笔不再超发）。本周期额度 =
 * {@code 速率 × period / 20}（速率是每秒口径），以 ×1000 定点折算、零头跨周期结转，
 * MUST NOT 因整除截断丢失。周期默认 20 tick = 1 秒（见 {@code SETTLE_PERIOD_TICKS}）。
 *
 * <p>未消化的整数额度不跨周期囤积（防 burst）；只有 &lt;1 单位的定点零头结转。
 * 世界无关、无状态依赖，可直接单测。
 */
public final class TickRateLedger {

    private static final long FIXED = 1000L;
    /** 每秒速率折算为定点额度的倍率：FIXED / 20 = 50（再乘周期 tick 数）。 */
    private static final long CREDIT_PER_RATE_UNIT = FIXED / 20L;

    /** 上次结算的周期序号（gameTime / period；Long.MIN_VALUE = 从未结算）。 */
    private long tick = Long.MIN_VALUE;
    /** 本周期剩余可准予整数单位。 */
    private long remaining;
    /** &lt;FIXED 的定点零头，跨周期结转。 */
    private long carry;
    /** 加权分配的跨周期优先级累加器（WFQ）：低速率核靠它累积出份额，不长期饥饿。 */
    private long allocPriority;

    /** 推进到 {@code now} 所属周期（同周期幂等）：按速率补足本周期额度并结转零头。 */
    public void refill(long now, long ratePerSecond, int periodTicks) {
        int period = Math.max(1, periodTicks);
        long key = Math.floorDiv(now, period);
        if (key == tick) {
            return;
        }
        tick = key;
        carry += Math.max(0L, ratePerSecond) * CREDIT_PER_RATE_UNIT * period;
        remaining = carry / FIXED;
        carry %= FIXED;
    }

    /** 本周期剩余额度（先按 {@code now/rate/period} 推进）。 */
    public long peek(long now, long ratePerSecond, int periodTicks) {
        refill(now, ratePerSecond, periodTicks);
        return remaining;
    }

    /** 准予至多 {@code want}，扣减额度并返回实准予量。 */
    public long grant(long now, long ratePerSecond, int periodTicks, long want) {
        refill(now, ratePerSecond, periodTicks);
        long granted = Math.min(Math.max(0L, want), remaining);
        remaining -= granted;
        return granted;
    }

    /** 记账实际搬运量（不应超过已准予量；超出按 0 夹取防透支）。 */
    public void consume(long actual) {
        if (actual <= 0L) {
            return;
        }
        remaining = Math.max(0L, remaining - actual);
    }

    /** WFQ 分配优先级（跨周期持久，不由周期推进重置）。 */
    public long allocPriority() {
        return allocPriority;
    }

    public void setAllocPriority(long value) {
        allocPriority = value;
    }

    /** 结构失效/卸载清理：丢弃周期额度、零头与分配优先级。 */
    public void clear() {
        tick = Long.MIN_VALUE;
        remaining = 0L;
        carry = 0L;
        allocPriority = 0L;
    }
}
