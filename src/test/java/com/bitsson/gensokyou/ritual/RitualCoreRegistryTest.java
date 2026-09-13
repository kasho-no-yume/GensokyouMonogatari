package com.bitsson.gensokyou.ritual;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import java.util.function.BiPredicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 查询几何、排除参数、惰性剔除与归属守卫（任务 3.1）。 */
class RitualCoreRegistryTest {

    private static final ResourceLocation ALPHA = ResourceLocation.parse("gensokyou:alpha_circle");
    private static final ResourceLocation BETA = ResourceLocation.parse("gensokyou:beta_circle");

    private static BlockPos at(int x, int y, int z) {
        return new BlockPos(x, y, z);
    }

    /** 现场校验桩：全部坐标视为"成型且图案一致"。 */
    private static final BiPredicate<BlockPos, RitualCoreRegistry.Entry> LIVE = (pos, entry) -> true;

    @Test
    void xzChebyshevSquareIgnoresY() {
        RitualCoreRegistry reg = new RitualCoreRegistry();
        reg.register(at(10, 120, 5), ALPHA, 2);   // 对角边缘 + 高差 56：命中
        reg.register(at(-10, -60, -10), ALPHA, 2); // 反向对角角落：命中
        reg.register(at(11, 64, 0), ALPHA, 2);     // x 超一格：不中
        reg.register(at(0, 64, 11), ALPHA, 2);     // z 超一格：不中
        List<BlockPos> hit = reg.matchedPositions(at(0, 64, 0), 10, null, LIVE);
        assertEquals(2, hit.size());
        assertTrue(hit.contains(at(10, 120, 5)));
        assertTrue(hit.contains(at(-10, -60, -10)));
    }

    @Test
    void excludeSkipsWithoutEvicting() {
        RitualCoreRegistry reg = new RitualCoreRegistry();
        reg.register(at(3, 64, 3), ALPHA, 2);
        reg.register(at(4, 64, 4), BETA, 2);

        List<BlockPos> excl = reg.matchedPositions(at(0, 64, 0), 10, ALPHA, LIVE);
        assertEquals(List.of(at(4, 64, 4)), excl);

        // 被排除 ≠ 陈旧：不带 exclude 再查，ALPHA 条目仍在
        List<BlockPos> all = reg.matchedPositions(at(0, 64, 0), 10, null, LIVE);
        assertEquals(2, all.size());
    }

    @Test
    void staleEntryEvictedLazily() {
        RitualCoreRegistry reg = new RitualCoreRegistry();
        reg.register(at(1, 64, 1), ALPHA, 2);
        Set<BlockPos> live = new HashSet<>(List.of(at(1, 64, 1)));

        List<BlockPos> first = reg.matchedPositions(at(0, 64, 0), 10, null,
                (pos, entry) -> live.contains(pos));
        assertEquals(1, first.size());

        live.clear(); // 现场已死（拆核心但异常路径未注销）
        List<BlockPos> second = reg.matchedPositions(at(0, 64, 0), 10, null,
                (pos, entry) -> live.contains(pos));
        assertTrue(second.isEmpty());

        // 剔除已生效：即使现场"复活"，条目也不在索引里（由核心重扫重新入册）
        live.add(at(1, 64, 1));
        assertTrue(reg.matchedPositions(at(0, 64, 0), 10, null,
                (pos, entry) -> live.contains(pos)).isEmpty());
    }

    @Test
    void unregisterGuardsOwnership() {
        RitualCoreRegistry reg = new RitualCoreRegistry();
        reg.register(at(2, 64, 2), ALPHA, 2);
        reg.unregister(at(2, 64, 2), BETA); // 非己条目：不删（拆旧建新守卫）
        assertEquals(1, reg.matchedPositions(at(0, 64, 0), 10, null, LIVE).size());
        reg.unregister(at(2, 64, 2), ALPHA); // 归属一致：删除
        assertTrue(reg.matchedPositions(at(0, 64, 0), 10, null, LIVE).isEmpty());
    }

    @Test
    void registerOverwritesSamePos() {
        RitualCoreRegistry reg = new RitualCoreRegistry();
        reg.register(at(5, 64, 5), ALPHA, 2);
        reg.register(at(5, 64, 5), ALPHA, 3); // 升级刷新（幂等覆盖，条目数不变）
        reg.register(at(5, 64, 5), BETA, 1);  // 同坐标换仪式：条目值现应为 BETA
        assertEquals(1, reg.matchedPositions(at(0, 64, 0), 10, ALPHA, LIVE).size());
        assertTrue(reg.matchedPositions(at(0, 64, 0), 10, BETA, LIVE).isEmpty());
    }
}
