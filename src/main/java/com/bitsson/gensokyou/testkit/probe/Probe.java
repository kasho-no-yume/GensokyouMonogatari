package com.bitsson.gensokyou.testkit.probe;

import com.google.gson.JsonObject;

/**
 * 探针：一次<b>只读</b>的游戏状态采样，产出 JSON 快照。
 *
 * <p>探针是「非视觉验收自动化」的读端：验收项不写 Java 断言，而是由 agent 用
 * {@code /gs_probe <id> [args]} 取一份 JSON，再对字段做比较。
 *
 * <p>纪律：
 * <ul>
 *   <li>MUST NOT 有副作用（不造物、不改状态、不扣灵力）——只读，否则断言会把探针本身
 *       变成被测对象，测出来的数不可信。</li>
 *   <li>MUST NOT 依赖玩家在场；玩家相关读数取不到时返回 {@code error} 字段而不是抛异常，
 *       好让场景把「读不到」与「读到 0」区分开。</li>
 *   <li>字段名用小写下划线（{@code game_time}），路径寻址才稳定。</li>
 * </ul>
 */
public interface Probe {

    /** 探针 id（命令词）。全小写，下划线分隔。 */
    String id();

    /** 执行一次采样。返回的 JSON 顶层 MUST 含 {@code probe} 字段（= 本探针 id）。 */
    JsonObject run(ProbeContext ctx) throws Exception;
}
