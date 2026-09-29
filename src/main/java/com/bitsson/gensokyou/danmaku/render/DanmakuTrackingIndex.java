package com.bitsson.gensokyou.danmaku.render;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 服务端侧「谁在跟踪哪些弹」的索引。
 *
 * <p><b>为什么必须自己记</b>：校准样本要发给<b>正在跟踪该弹</b>的玩家，恢复请求要
 * 被<b>确实在跟踪的玩家</b>发起才有效。这两个判据都需要一份「玩家 × 弹」的对应关系，
 * 而原版把它封在 {@code ServerEntity} 里，模组侧拿不到。
 *
 * <p><b>不自己记会怎样</b>：只能退化成「给玩家视野内的所有弹发校准」或「无条件响应
 * 任何恢复请求」。前者把带宽乘上玩家数，后者让任意客户端能拖着服务端无限生成快照。
 *
 * <p>生命周期：{@code StartTracking} 加入、实体离世界或玩家退出时移除，并有一轮低频
 * 对账兜住非常规移除路径。与 {@code DanmakuBudget} 的对账同思路——只兜底、不追求精度。
 *
 * <p>仅服务端使用，全部方法在服务端线程调用。
 */
public final class DanmakuTrackingIndex {

    private static final Map<UUID, Set<Integer>> BY_PLAYER = new HashMap<>();
    private static final Map<Integer, Set<UUID>> BY_ENTITY = new HashMap<>();

    private static int sincePrune;

    private DanmakuTrackingIndex() {
    }

    public static void onStartTracking(UUID player, int entityId) {
        BY_PLAYER.computeIfAbsent(player, k -> new HashSet<>()).add(entityId);
        BY_ENTITY.computeIfAbsent(entityId, k -> new HashSet<>()).add(player);
    }

    public static void onStopTracking(UUID player, int entityId) {
        Set<Integer> ids = BY_PLAYER.get(player);
        if (ids != null) {
            ids.remove(entityId);
            if (ids.isEmpty()) {
                BY_PLAYER.remove(player);
            }
        }
        Set<UUID> players = BY_ENTITY.get(entityId);
        if (players != null) {
            players.remove(player);
            if (players.isEmpty()) {
                BY_ENTITY.remove(entityId);
            }
        }
    }

    /** 实体离世界：所有玩家对它的追踪关系一并作废。 */
    public static void onEntityRemoved(int entityId) {
        Set<UUID> players = BY_ENTITY.remove(entityId);
        if (players == null) {
            return;
        }
        for (UUID player : players) {
            Set<Integer> ids = BY_PLAYER.get(player);
            if (ids != null) {
                ids.remove(entityId);
                if (ids.isEmpty()) {
                    BY_PLAYER.remove(player);
                }
            }
        }
    }

    public static void onPlayerLeft(UUID player) {
        Set<Integer> ids = BY_PLAYER.remove(player);
        if (ids == null) {
            return;
        }
        for (Integer id : ids) {
            Set<UUID> players = BY_ENTITY.get(id);
            if (players != null) {
                players.remove(player);
                if (players.isEmpty()) {
                    BY_ENTITY.remove(id);
                }
            }
        }
    }

    public static boolean isTracking(UUID player, int entityId) {
        Set<Integer> ids = BY_PLAYER.get(player);
        return ids != null && ids.contains(entityId);
    }

    /** 该玩家当前跟踪的弹 id 快照。迭代期不得持有本集合。 */
    public static List<Integer> trackedIds(UUID player) {
        Set<Integer> ids = BY_PLAYER.get(player);
        return ids == null ? List.of() : new ArrayList<>(ids);
    }

    /**
     * 低频对账：清掉指向已不存在实体的条目。
     *
     * <p>事件路径不覆盖全部移除方式（区块卸载、末影箱、跨维度），漏掉的条目会让
     * 校准白发给已经没人看的弹、并让恢复请求对着一具空壳通过追踪校验。
     */
    public static void prune(java.util.function.IntPredicate entityAlive) {
        if (++sincePrune < 200) {
            return;
        }
        sincePrune = 0;
        BY_ENTITY.entrySet().removeIf(entry -> !entityAlive.test(entry.getKey()));
        BY_PLAYER.values().forEach(ids -> ids.removeIf(id -> !BY_ENTITY.containsKey(id)));
        BY_PLAYER.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    public static void clear() {
        BY_PLAYER.clear();
        BY_ENTITY.clear();
        sincePrune = 0;
    }
}
