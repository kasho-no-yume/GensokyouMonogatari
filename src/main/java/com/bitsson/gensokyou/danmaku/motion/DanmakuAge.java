package com.bitsson.gensokyou.danmaku.motion;

/**
 * 弹体「年龄」的求值规则与存档判据。
 *
 * <p><b>年龄是全部运动学与终止判据的唯一自变量</b>：编队帧解析位置、速率曲线取值、
 * 悬停 / 分裂时刻、相位隐藏、寿命、越过发射点销毁，全都读它。
 *
 * <p><b>为什么不用原版的 {@code Entity.tickCount}</b>——三条实测事实（NeoForge 21.1.248）：
 * <ul>
 *   <li>它<b>不写入也不读出 NBT</b>。整个 MC 只有 {@code AreaEffectCloud} 自己存了 {@code "Age"}</li>
 *   <li>{@code Entity.recreateFromPacket} <b>不设它</b>——客户端实体一律自 0 起步</li>
 *   <li>两端仅由 {@code Level.tickNonPassenger} <b>各自自增</b>，速率相同</li>
 * </ul>
 *
 * <p>⇒ 客户端每「丢掉又重新拿到」这个实体（走出 {@code clientTrackingRange} 再回来、
 * 客户端卸载区块、世界读档……）就自 0 重数，而服务端那份一直在 tick。
 * <b>位置存了，年龄没存</b>，双端自变量从此不等且持续发散——位置纠偏包于是每
 * {@code updateInterval} 个 tick 把弹硬拽一次。
 *
 * <p>年龄 = 「基准 + 本地 tick 计数」。基准两侧各有一份、互不共享（两名客户端开始跟踪
 * 同一枚弹的时刻不同，共享单一值必然弄坏其中一方）。
 *
 * <p>本类是纯静态的，与 {@code FormationFrame} / {@code DanmakuSpeedProfile} 同处一层：
 * 规则放在值类型里，实体只负责提供两个分量。无世界测试也因此可以直接覆盖。
 */
public final class DanmakuAge {

    /**
     * 年龄钳位上限（tick）。取 100 万 ≈ 13.9 小时游戏时间，远超
     * {@code AbstractDanmakuProjectile.MAX_LIFETIME_TICKS}（1200）。
     *
     * <p>理论上界本应只是 {@code int}，两个 {@code int} 相加仍可能翻负；钳位使那种情况
     * 退化成「弹很老」而不是「弹刚出生」。
     */
    public static final int MAX_AGE_TICKS = 1_000_000;

    private DanmakuAge() {
    }

    /**
     * 年龄求值。两侧一致使用，负输入归零、超上限钳位。
     *
     * <p><b>基准为 0 时结果逐位等于 {@code tickCount}</b>——这是「正常发射路径逐位不变」
     * 的保证。绝大多数弹一生都不经存档，若这条不成立，本机制就会在<b>每一发</b>新弹幕上
     * 引入偏差，而不是只在读档后。
     */
    public static int at(int basis, int tickCount) {
        long sum = (long) basis + tickCount;
        if (sum <= 0L) {
            return 0;
        }
        return (int) Math.min(MAX_AGE_TICKS, sum);
    }

    /**
     * 方向轴是否需要写入存档。
     *
     * <p>条件是「挂速率曲线 <b>或</b>挂编队帧」，MUST NOT 窄于后者。
     * {@code bindToFrame} 与 {@code configureSpeedProfile} 都会写方向轴；写盘条件若只认前者，
     * 「有帧无曲线」的弹读档后就回落成默认 {@code (0,0,1)}，沿弹道推进项指向世界 +Z。
     *
     * <p>抽成单一判据是为了让读、写两侧共用一个真相——分两处各写一遍条件，
     * 正是这个 bug 的成因。
     */
    public static boolean axisNeedsPersistence(boolean hasSpeedProfile, boolean hasFormationFrame) {
        return hasSpeedProfile || hasFormationFrame;
    }
}
