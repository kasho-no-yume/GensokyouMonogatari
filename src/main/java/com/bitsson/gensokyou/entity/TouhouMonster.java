package com.bitsson.gensokyou.entity;

/**
 * 东方系怪物的标记接口。
 *
 * <p>凡实现本接口的怪物，对**非** {@code gensokyou:danmaku} 伤害天然减免
 * （比例见 {@code GensokyouConfig.TOUHOU_NON_DANMAKU_RESIST}，默认 90%），
 * 结算收口于 {@code event/TouhouCombatEvents}。
 *
 * <p>全局设计规则见 {@code docs/mob-design-guidelines.md}。
 */
public interface TouhouMonster {
}
