package com.bitsson.gensokyou.danmaku.render;

import java.util.UUID;

/**
 * 弹幕「运动输入」的单一真相：定长整数块的布局、指纹与编解码契约。
 *
 * <p><b>为什么需要它</b>——设计要求「恢复时不得拼接位置包与稍后到达的
 * {@code SynchedEntityData}」。但把 40 多个同步字段在自定义包里<b>再抄一遍</b>
 * 会制造第二个写入者，正是设计明确禁止的。本类的取法是：
 *
 * <pre>
 *   常规模径：快照只带 位置 / 速度 / 年龄锚点 / 时间 / 指纹    （约 40 字节）
 *   修复口径：指纹不符时按需补发整块运动输入                    （192 字节，罕见）
 * </pre>
 *
 * <p>于是「一致采样」有了可检验的保证：客户端每次都能算出自己那份运动输入的指纹，
 * 与权威指纹不符即说明它手上的参数不是同一时刻的那份——而不是「大概差不多」。
 *
 * <p><b>块的定长是刻意的</b>：变长数组需要长度前缀与上限校验，而这里的字段集合
 * 本身就是封闭的（新增行为轴就要新增同步字段，那时两处一起改）。
 */
public final class DanmakuMotionState {

    /** 运动输入块的字段数。改动 MUST 同步 {@code PARAM_*} 常量与实体的编解码。 */
    public static final int PARAM_COUNT = 48;

    public static final int P_FLAGS = 0;
    public static final int P_HOVER_TICK = 1;
    public static final int P_SPLIT_TICK = 2;
    public static final int P_SPLIT_COUNT = 3;
    public static final int P_CURVE_AXIS = 4;
    public static final int P_CURVE_RATE = 5;
    public static final int P_MINE_RADIUS = 6;
    public static final int P_LIFETIME = 7;
    public static final int P_HAS_PROFILE = 8;
    public static final int P_PROFILE_BASE = 9;   // v0 p0 v1 p1 v2 p2 v3
    public static final int P_PROFILE_COUNT = 7;
    public static final int P_DIES_AT_ORIGIN = 16;
    public static final int P_AXIS_X = 17;
    public static final int P_AXIS_Y = 18;
    public static final int P_AXIS_Z = 19;
    public static final int P_HAS_FRAME = 20;
    public static final int P_FRAME_BASE = 21;
    public static final int P_FRAME_COUNT = 16;
    public static final int P_FRAME_ADVANCE = 37;
    public static final int P_PHASE_PERIOD = 38;
    public static final int P_PHASE_DUTY = 39;
    public static final int P_PHASE_OFFSET = 40;
    public static final int P_LASER_BASE = 41;
    public static final int P_LASER_COUNT = 7;   // dir xyz, maxLength, radius, delay, duration

    /** 位置定点化比例。与原版实体位置包的 1/4096 一致，便于对照与诊断。 */
    public static final double POSITION_SCALE = 4096.0D;

    /** 位置定点化的 varint 安全上限（格）。超出即判定为非法样本。 */
    public static final double POSITION_LIMIT = 30_000_000.0D;

    private DanmakuMotionState() {
    }

    /**
     * 运动版本号。
     *
     * <p>本阶段取<b>年龄</b>：编队帧、速率曲线、显隐全都以年龄为自变量，客户端重算
     * 轨迹时用的就是这个自变量；把它放进样本，等于让两端在「用同一个自变量求同一条
     * 轨迹」这条前提上互相校验。真正改变运动<b>输入</b>的运行时操作（改向）走
     * 原版运动包并在本阶段视为罕见事件。
     */
    public static int revisionFor(int age) {
        return Math.max(0, age);
    }

    /**
     * 运动输入块的指纹（FNV-1a 32 位）。
     *
     * <p>纯 int 运算 ⇒ 双端逐位一致，无浮点、无超越函数。
     * 只用于「两端手上的是不是同一份参数」的检测，<b>不</b>用于安全校验——
     * 真正的身份判定走 {@link #identityMatches}。
     */
    public static int fingerprint(int[] params) {
        if (params == null) {
            return 0;
        }
        int hash = 0x811C9DC5;
        for (int value : params) {
            hash ^= value;
            hash *= 0x01000193;
        }
        return hash;
    }

    /**
     * 身份判定：UUID 相同即同一实体。
     *
     * <p>实体 id 会复用（Minecraft 的 entity id 是递增计数，会绕回），所以
     * 跨追踪周期比对 MUST 用 UUID。
     */
    public static boolean identityMatches(UUID known, UUID incoming) {
        return known != null && known.equals(incoming);
    }

    /**
     * 追踪令牌判定。
     *
     * <p>同一枚实体可以在<b>不同</b>追踪周期被重新推送初始化（玩家走远再回来、
     * 跨区块重载）。这种情况下客户端<b>应当</b>接受新快照并重锚——但不能把它当
     * 「同一周期的重复包」静默丢弃。令牌让这两件事可区分。
     */
    public static boolean tokenAcceptable(long known, long incoming) {
        return incoming != known;
    }
}
