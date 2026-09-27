package com.bitsson.gensokyou.danmaku.track;

/**
 * 节拍的目标模式——决定它在多玩家场上复制几份。
 *
 * <p>这是「单人难度不随人数线性加难」的关键（{@code remnant-touhou-bosses}：
 * 多目标受击与瞄准型复制）。两种模式的语义：
 *
 * <ul>
 *   <li>{@link #AIMED}——「有一发是给你的」。对每名被锁定目标各生成一份，5 人 = 5 份。
 *   <li>{@link #SELF_AXIS} / {@link #ARENA}——「这一发是给场地的」。只生成一份。
 *       若也按人复制，单个玩家会同时吃到 5 份环形压力，那是加难不是公平。
 * </ul>
 */
public enum TargetMode {
    /** 瞄准型：朝目标发射，按目标数复制。 */
    AIMED,
    /** 自轴型：绕 BOSS 自身基向量发射，不复制。 */
    SELF_AXIS,
    /** 场地型：绕固定世界轴发射（如贴地带、地滑环），不复制。 */
    ARENA;

    /** 该模式是否随玩家数复制。 */
    public boolean copiesPerTarget() {
        return this == AIMED;
    }
}
