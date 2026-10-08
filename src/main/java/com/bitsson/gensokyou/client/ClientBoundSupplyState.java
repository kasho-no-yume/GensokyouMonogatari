package com.bitsson.gensokyou.client;

import com.bitsson.gensokyou.network.BoundSupplyCountsPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;

import java.util.HashMap;
import java.util.Map;

/**
 * 绑定无尽藏仓储计数的客户端暂存：按核心坐标索引服务端下发的快照。
 *
 * <p>生命周期与静态暂存范式一致（{@code ClientRitualPreviewState}）：payload 驱动更新，
 * 登出/重连时由 {@link #clear()} 清理。
 */
public final class ClientBoundSupplyState {

    /** 某核心的快照：闸门状态 + 物品 → 数量（按物品合并）。 */
    public record Snapshot(int status, Map<Item, Long> counts) {
        public long count(Item item) {
            Long value = counts.get(item);
            return value == null ? 0L : value;
        }
    }

    private static final Map<BlockPos, Snapshot> CACHE = new HashMap<>();

    private ClientBoundSupplyState() {
    }

    public static void update(BoundSupplyCountsPayload payload) {
        Map<Item, Long> counts = new HashMap<>();
        for (BoundSupplyCountsPayload.Entry entry : payload.entries()) {
            if (!entry.key().isEmpty()) {
                counts.merge(entry.key().getItem(), entry.count(), Long::sum);
            }
        }
        CACHE.put(payload.corePos().immutable(), new Snapshot(payload.status(), counts));
    }

    public static Snapshot get(BlockPos pos) {
        return CACHE.get(pos);
    }

    public static void clear() {
        CACHE.clear();
    }
}
