package com.bitsson.gensokyou.danmaku;

/**
 * 弹幕「存在性」（寿命）的纯函数。
 *
 * <p><b>为什么存在性独立于 {@code 年龄}（{@code DanmakuAge}）</b>：
 * 年龄是<b>运动</b>的自变量，必须由「基准 + tickCount」驱动以保轨迹连续（见
 * {@code danmaku-age-continuity}）。但 tickCount 只在实体被 tick 时自增，而原版对
 * 「超出模拟距离」的区块<b>不做实体 tick</b> ⇒ 远处弹幕的年龄冻结、永不判寿命、
 * 无限堆积。故存在性 MUST 由<b>服务端游戏时间</b>单独裁决：游戏时间只在服务器运行时推进，
 * 冻结时间照常计入，世界关停期间不计（读档按剩余寿命继续）。
 *
 * <p>本类是纯静态的，与 {@code DanmakuAge} 同处一层，便于无世界测试直接覆盖。
 */
public final class DanmakuLifetime {

    /** 出生游戏时间「未设置」的哨兵。 */
    public static final long UNSET_BIRTH = Long.MIN_VALUE;

    private DanmakuLifetime() {
    }

    /**
     * 旧存档缺出生时间键时的回退出生点。
     *
     * <p>取 {@code now − age}：使读档得到的弹从「已恢复年龄」处继续正常寿命，
     * 行为不劣于本变更之前（不会因缺键而被立即判死，也不会白得完整寿命）。
     *
     * @param age 已恢复年龄，负数按 0 处理
     */
    public static long fallbackBirth(long now, int age) {
        return now - Math.max(0, age);
    }

    /**
     * 绝对寿命是否已过。
     *
     * @param birth 出生游戏时间；{@link #UNSET_BIRTH} 视为「尚未初始化」⇒ 未过期
     */
    public static boolean overdue(long now, long birth, int lifetimeTicks) {
        if (birth == UNSET_BIRTH) {
            return false;
        }
        return now - birth > lifetimeTicks;
    }
}
