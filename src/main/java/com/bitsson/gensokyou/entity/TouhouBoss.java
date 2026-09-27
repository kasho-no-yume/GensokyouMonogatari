package com.bitsson.gensokyou.entity;

/**
 * 东方 BOSS 标记（touhou-boss-bar）：该实体在场时，其原版 BOSS 血条被改绘为咒符条。
 *
 * <p><b>刻意放在 common 侧、且是一个空接口</b>。BOSS 实体本身是 common 类（服务端与客户端
 * 都要加载），若把标记嵌在客户端渲染器里，`FlandreEntity` 为了 implements 它就得让服务端
 * 去加载那个客户端类——而它引用了 {@code LerpingBossEvent} / {@code CustomizeGuiOverlayEvent}
 * 等纯客户端类型，专用服务器会直接崩在类加载阶段。空接口则没有这个风险。
 *
 * <p>做成接口而非"逐个 instanceof 列出具体类"：新增 BOSS 只要 implements 这一个标记就自动
 * 套用咒符条，不会因为漏改判定而悄悄退回原版血条。判定方见
 * {@code TouhouBossBarRenderer#isTouhouBoss}。
 */
public interface TouhouBoss {
}
