package com.bitsson.gensokyou.danmaku.motion;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;

/**
 * 弹道相对<b>轨道时间</b>的形式，以及读档后能否自愈。
 *
 * <p><b>为什么要有这个类</b>——三条各自成立、但没人把它们放在一起的事实：
 * <ol>
 *   <li>位置<b>是否</b>是轨道时间的纯函数，按弹种分成两类：</li>
 *   <li>其中一类读档后能靠已持久化的参数自愈，另一类不能；</li>
 *   <li>不能自愈的那一类，缺的是<b>同一个</b>量：{@code deltaMovement}。</li>
 * </ol>
 * 把它们收在一处，才有一个 {@code danmaku-event-sync} 可以直接查询的答案 ——
 * 「这项状态能否由轨道时间推导」——而不是让人去读 95 KB 的
 * {@code AbstractDanmakuProjectile#tickDanmaku} 自己推。
 *
 * <p><b>逐条的依据</b>（全部来自 {@code tickDanmaku} 的实际分支，不是设计意图）：
 * <pre>
 *   编队帧    位置每 tick 被 rig 覆写为 positionAt(age) + axis·advance(age)，
 *            velocity 由「解析终点 − 当前坐标」反推 ⇒ 不需要 deltaMovement，自愈
 *   速率曲线  方向来自已持久化的 axis()（alongAxis 在速度为零时回落到它），
 *            速率来自 speedAt(age) ⇒ 不需要 deltaMovement，自愈
 *   曲射      rotateAbout(velocity, axis, ω) 作用在<b>上一步的速度</b>上；
 *            速度为零则旋转零向量恒为零 ⇒ 冻结
 *   直线      pos += velocity，速度为零则原地不动 ⇒ 冻结
 * </pre>
 *
 * <p><b>曲射为什么不能也变成闭式</b>：它的解析位置是
 * {@code p₀ + Σ R(axis,ω)ⁱv₀}，求和要把整条旋转历史闭式化。而
 * {@code Rotation.about} 用的 {@code sin/cos} 本就不保证跨端逐位一致
 * （见类注释里的纪律），闭式化会把这个已记录的例外从「一次旋转」放大成
 * 「整段轨迹」。曲射的相位恢复属于 {@code danmaku-event-sync} 的
 * {@code REDIRECT}，不在本类职责内。
 *
 * <p>纯静态，{@link CompoundTag} 与 {@link Vec3} 均无世界依赖，故可完全离线测试。
 */
public final class DanmakuTrackKinds {

    /** 弹道相对轨道时间的形式。 */
    public enum Form {
        /**
         * 位置是轨道时间的<b>纯函数</b>。给定年龄即可算出位置，不需要上一 tick 的状态。
         */
        CLOSED_FORM,
        /**
         * 位置是<b>上一步的累加</b>。丢掉速度就丢掉全部后续运动。
         */
        INCREMENTAL,
        /**
         * 位置是累加的（同 {@link #INCREMENTAL}），但<b>速度不是</b> ——
         * {@code vᵢ = dir(seedᵢ) · speedᵢ} 只由种子与段表决定，不依赖上一 tick 的速度。
         *
         * <p>这正是它与 {@link #INCREMENTAL} 的分界，也是它能进闭式分支的全部理由：
         * 双端逐位一致，同时位置可由「存档位置 + 按年龄重算的速度」续上。
         */
        SEGMENTED
    }

    /** 存档键。三个分量，与原版实体 NBT 的写法一致。 */
    public static final String KEY_VELOCITY = "MotionX";

    private DanmakuTrackKinds() {
    }

    /**
     * 该弹种的弹道形式（无段式运动时的旧口径）。
     *
     * @see #formOf(boolean, boolean, boolean, boolean)
     */
    public static Form formOf(boolean curving, boolean hasSpeedProfile,
                              boolean hasFormationFrame) {
        return formOf(curving, hasSpeedProfile, hasFormationFrame, false);
    }

    /**
     * 该弹种的弹道形式。
     *
     * @param segmented 挂了段式运动
     */
    public static Form formOf(boolean curving, boolean hasSpeedProfile,
                              boolean hasFormationFrame, boolean segmented) {
        // 编队帧最强：它每 tick 覆写位置，其余一切都不需要。
        // 速率曲线次之：它能从已持久化的方向轴与 speedAt(age) 重建速度。
        // 段式运动再次：速度可由种子与段表解出，但位置仍是累加的。
        if (hasFormationFrame || hasSpeedProfile) {
            return Form.CLOSED_FORM;
        }
        return segmented ? Form.SEGMENTED : Form.INCREMENTAL;
    }

    /**
     * 读档后能否在没有 {@code deltaMovement} 的情况下继续正确运动（无段式运动时的旧口径）。
     *
     * @see #survivesReloadWithoutVelocity(boolean, boolean, boolean, boolean)
     */
    public static boolean survivesReloadWithoutVelocity(boolean curving, boolean hasSpeedProfile,
                                                         boolean hasFormationFrame) {
        return survivesReloadWithoutVelocity(curving, hasSpeedProfile, hasFormationFrame, false);
    }

    /**
     * 读档后能否在没有 {@code deltaMovement} 的情况下继续正确运动。
     *
     * <p>与 {@link #formOf} 不是同一个判据：曲射弹是 {@code INCREMENTAL}，
     * 但挂了速率曲线之后它仍能自愈，因为 {@code alongAxis} 会回落到方向轴。
     *
     * <p><b>段式运动能自愈</b>：段内速度是 {@code f(种子, 段号, 段表)} 的纯函数，
     * 段索引是年龄的纯函数，故缺 {@code deltaMovement} 也能重算出来。
     */
    public static boolean survivesReloadWithoutVelocity(boolean curving, boolean hasSpeedProfile,
                                                         boolean hasFormationFrame,
                                                         boolean segmented) {
        return hasFormationFrame || hasSpeedProfile || segmented;
    }

    /**
     * 该弹种是否需要把 {@code deltaMovement} 写入存档（无段式运动时的旧口径）。
     *
     * @see #needsVelocityPersistence(boolean, boolean, boolean, boolean)
     */
    public static boolean needsVelocityPersistence(boolean curving, boolean hasSpeedProfile,
                                                   boolean hasFormationFrame) {
        return needsVelocityPersistence(curving, hasSpeedProfile, hasFormationFrame, false);
    }

    /**
     * 该弹种是否需要把 {@code deltaMovement} 写入存档。
     *
     * <p>为 false 时<b>不写</b>，读档也不读 —— 不是「读了但不用」，而是不占那 6 个
     * 键的体积。编队弹与速率曲线弹的存档里不该出现速度，因为它们的位置不由速度决定；
     * 写进去反而会让人误以为速度是它们的权威。
     *
     * <p><b>裸段式运动刻意返回 true，与 {@link #survivesReloadWithoutVelocity} 解耦。</b>
     * 理由<b>不是</b>「速度不可推导」—— 它是可推导的 —— 而是<b>顺序依赖</b>：
     * 服务端从存档的 {@code pos} 继续，读侧重算速度必须<b>先于</b>位置积分发生。
     * 现有代码已经因 {@code DATA_HAS_FRAME} / {@code DATA_HAS_PROFILE} 的推断顺序踩过一次
     * （见 {@code AbstractDanmakuProjectile} 里「速度 MUST 最后读」的注释）。
     * 宁可多存 3 个 double。
     *
     * <p><b>但编队帧与速率曲线优先于段式判断</b>：它们已让位置不由速度决定，
     * 此时段式不该反过来把「不写」改写成「写」。
     */
    public static boolean needsVelocityPersistence(boolean curving, boolean hasSpeedProfile,
                                                   boolean hasFormationFrame, boolean segmented) {
        // 编队帧与速率曲线**优先**：它们每 tick 覆写位置或速率，
        // 速度对位置没有影响 ⇒ 不写。这条先于段式判断，否则段式会把既有语义盖掉。
        if (hasFormationFrame || hasSpeedProfile) {
            return false;
        }
        // 裸段式运动返回 true，与 {@link #survivesReloadWithoutVelocity} 解耦。
        // 理由**不是**「速度不可推导」—— 它是可推导的 —— 而是**顺序依赖**：
        // 服务端从存档的 {@code pos} 继续，读侧重算速度必须<b>先于</b>位置积分发生，
        // 而这个判据本身依赖「是否挂了段式运动」（读档期顺序问题）。
        // 现有代码已经因 {@code DATA_HAS_FRAME} / {@code DATA_HAS_PROFILE} 的推断顺序踩过一次。
        // 宁可多存 3 个 double。
        return segmented || !survivesReloadWithoutVelocity(curving, hasSpeedProfile, hasFormationFrame);
    }

    /**
     * 写盘。
     *
     * @param needed   {@link #needsVelocityPersistence} 的结果
     * @param velocity 当前速度；仅在 {@code needed} 时被读取
     */
    public static void writeVelocity(CompoundTag tag, boolean needed, Vec3 velocity) {
        if (!needed || velocity == null) {
            return;
        }
        tag.putDouble(KEY_VELOCITY, velocity.x);
        tag.putDouble(KEY_VELOCITY + "Y", velocity.y);
        tag.putDouble(KEY_VELOCITY + "Z", velocity.z);
    }

    /**
     * 读盘。
     *
     * <p>缺键（旧存档、或该弹种本就不需要）时返回 {@link Vec3#ZERO}，即
     * <b>与本变更之前的行为完全一致</b>：宁可继续冻结，也不引入一个从未验证过的新路径。
     *
     * <p>逐项判存在而不是「三个键齐全才读」：写侧是逐项 put 的，读侧若要求齐全，
     * 一个残缺存档就会静默退化成冻结，且不留任何痕迹。
     */
    public static Vec3 readVelocity(CompoundTag tag, boolean needed) {
        if (!needed || tag == null || !tag.contains(KEY_VELOCITY)) {
            return Vec3.ZERO;
        }
        double x = tag.getDouble(KEY_VELOCITY);
        double y = tag.contains(KEY_VELOCITY + "Y") ? tag.getDouble(KEY_VELOCITY + "Y") : 0.0D;
        double z = tag.contains(KEY_VELOCITY + "Z") ? tag.getDouble(KEY_VELOCITY + "Z") : 0.0D;
        return new Vec3(x, y, z);
    }
}
