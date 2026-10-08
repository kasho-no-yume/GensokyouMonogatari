package com.bitsson.gensokyou.ritual;

/**
 * 百鬼夜行召唤会话阶段。
 *
 * <p>{@code IDLE} 之外的三段合起来是一次<b>一次性</b>演出，全部由
 * {@code gameTime − 演出锚点} 推进，MUST NOT 逐 tick 同步渲染态。
 * 非 {@code IDLE} 时 {@code enabled} 恒为真——「会话进行中」正是启停位的语义。
 */
public enum SummonPhase { IDLE, CHARGING, BURST, PILLAR }

