package com.bitsson.gensokyou.client;

import java.util.HashMap;
import java.util.Map;
import java.util.OptionalInt;

/**
 * 客户端侧的「BOSS 实体 id → 当前符卡下标」表。
 *
 * <p>数据来源是 {@code SpellCardNamePayload}：服务端在切卡时向所有跟踪该 BOSS 的
 * 玩家单发，{@code StartTracking} 时补发一次当前值。<b>只存下标不存名字</b>——
 * 名字由 {@code BossCards} 的表在本地反查，于是 Component 永不上线。
 *
 * <p><b>为什么按 entityId 而非 UUID 存</b>：血条判别路径（{@code TouhouBossBarRenderer#isTouhouBoss}）
 * 已经在扫 {@code entitiesForRendering()} 比对 UUID，那条路径必须用 UUID（血条只给 UUID）。
 * 但本表是下发的，entityId 就在包里，用它省一次 UUID→实体的反查。
 *
 * <p><b>泄漏与错读的防护</b>：entityId 会被回收，故实体离开世界时（{@code EntityLeaveLevelEvent}）
 * MUST 清条目——否则一个已死的 BOSS 的下标会一直留在表里。id 被回收给<b>另一只</b>东方 BOSS 时，
 * 它的 {@code StartTracking} 补发会直接覆盖旧值，故错读窗口不存在。
 */
public final class ClientSpellCardNames {

    private static final Map<Integer, Integer> CARD_BY_ENTITY = new HashMap<>();

    private ClientSpellCardNames() {
    }

    public static void put(int entityId, int cardIndex) {
        CARD_BY_ENTITY.put(entityId, cardIndex);
    }

    public static void remove(int entityId) {
        CARD_BY_ENTITY.remove(entityId);
    }

    public static OptionalInt get(int entityId) {
        Integer index = CARD_BY_ENTITY.get(entityId);
        return index == null ? OptionalInt.empty() : OptionalInt.of(index);
    }

    /** 客户端断线重连 / 切换世界时清空——旧世界的 id 在新世界里毫无意义。 */
    public static void clear() {
        CARD_BY_ENTITY.clear();
    }

    /** 调试用。 */
    public static int size() {
        return CARD_BY_ENTITY.size();
    }
}
