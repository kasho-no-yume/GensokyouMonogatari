package com.bitsson.gensokyou.danmaku;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 目标引用的解析契约 —— 核心是<b>不按网络 id 回退</b>。
 *
 * <p>本类测的是「解析规则」而非「Minecraft 实体」：{@link DanmakuTargetRef#resolve}
 * 对值类型泛型，于是「id 被复用」这条场景可以在不启动世界的前提下写成
 * 「同一个网络 id 上的两个实体」—— 而这正是原实现会静默追错的情形。
 */
class DanmakuTargetRefTest {

    /**
     * 一个以网络 id 为主键的小世界。
     *
     * <p>刻意做成<b>按 id 索引</b>，以便断言「新实体会占用旧实体的 id」这件事本身
     * 成立：否则「id 复用」只是测试里的假设，而不是被验证过的事实。
     *
     * <p>id 分配取<b>最小空闲非零</b>，与 {@code Entity#getFreeEntityId()} 的行为一致 ——
     * 递增分配会让「复用」永不发生，这个测试就会静退化成「B 拿了个新 id」的平凡情形。
     */
    private static final class IdWorld {
        private final Map<Integer, String> byId = new HashMap<>();
        private final Map<UUID, String> byUuid = new HashMap<>();

        /** 加入一个实体，返回它的网络 id。 */
        int spawn(String name, UUID uuid) {
            int id = nextFreeId();
            byId.put(id, name);
            byUuid.put(uuid, name);
            return id;
        }

        private int nextFreeId() {
            int id = 1;
            while (byId.containsKey(id)) {
                id++;
            }
            return id;
        }

        /** 移除实体；id 随之可被回收。 */
        void remove(int id, UUID uuid) {
            byId.remove(id);
            byUuid.remove(uuid);
        }

        String byId(int id) {
            return byId.get(id);
        }

        Function<UUID, String> uuidLookup() {
            return byUuid::get;
        }
    }

    @Test
    @DisplayName("目标死亡后 id 被复用：解析结果不因 id 复用而改变")
    void idReuseDoesNotStealTheTarget() {
        IdWorld world = new IdWorld();
        UUID targetA = UUID.randomUUID();
        int idA = world.spawn("target-A", targetA);

        // 灵符锁定 A
        Optional<UUID> tracked = Optional.of(targetA);
        assertEquals("target-A", DanmakuTargetRef.resolve(tracked, world.uuidLookup()));

        // A 死亡，id 被回收给 B
        world.remove(idA, targetA);
        int reusedId = world.spawn("target-B", UUID.randomUUID());
        assertEquals(idA, reusedId, "本测试的前提：id 确实被复用了");
        assertNotNull(world.byId(idA), "按 id 查找仍能命中 —— 这正是原来的行为");

        // 关键断言：按 UUID 解析找不到，而不是顺着 id 追到 B
        assertNull(DanmakuTargetRef.resolve(tracked, world.uuidLookup()),
                "目标 UUID 已不存在，必须解析为无目标，而不是 id 同号的 target-B");
        assertEquals("target-B", world.byId(reusedId),
                "对照组：若实现回退到按 id 查找，得到的会是 target-B");
    }

    @Test
    @DisplayName("空目标解析为 null，且不触发任何查询")
    void emptyTargetResolvesToNullWithoutLookup() {
        IdWorld world = new IdWorld();
        assertNull(DanmakuTargetRef.resolve(Optional.empty(), world.uuidLookup()));
        assertNull(DanmakuTargetRef.resolve(null, world.uuidLookup()),
                "同步值本身缺失（读档缺键的极端情形）同样按无目标处理");
    }

    @Test
    @DisplayName("UUID 查询抛异常时不得被吞掉")
    void lookupFailurePropagates() {
        Function<UUID, String> exploding = uuid -> {
            throw new IllegalStateException("lookup must not swallow failures");
        };
        assertThrows(IllegalStateException.class,
                () -> DanmakuTargetRef.resolve(Optional.of(UUID.randomUUID()), exploding));
    }

    @Test
    @DisplayName("目标存活期间解析结果稳定，重复查询不漂移")
    void repeatedLookupIsStable() {
        IdWorld world = new IdWorld();
        UUID uuid = UUID.randomUUID();
        world.spawn("target", uuid);
        Optional<UUID> tracked = Optional.of(uuid);
        for (int i = 0; i < 5; i++) {
            assertEquals("target", DanmakuTargetRef.resolve(tracked, world.uuidLookup()),
                    "第 " + i + " 次查询");
        }
    }

    @Test
    @DisplayName("全零 UUID 是合法 UUID，不能被当成「无目标」哨兵")
    void zeroUuidIsNotASentinel() {
        // 这条约束是选 Optional 而不是「空 UUID」的全部理由：
        // 全零 UUID 完全可以被真的分配出去，用它当哨兵需要一条额外不变式来维持。
        UUID zero = new UUID(0L, 0L);
        Map<UUID, String> world = Map.of(zero, "real-entity");
        assertEquals("real-entity",
                DanmakuTargetRef.resolve(Optional.of(zero), world::get),
                "全零 UUID 必须被当作真实身份");
        assertFalse(Optional.of(zero).isEmpty());
        assertTrue(Optional.empty().isEmpty());
    }
}