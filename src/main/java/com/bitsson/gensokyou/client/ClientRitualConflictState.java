package com.bitsson.gensokyou.client;

import com.bitsson.gensokyou.config.GensokyouConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 构建器冲突红框的客户端暂存：坐标 → 过期游戏刻。
 * 收到新 payload 整体替换旧集合（不叠加历史）。
 */
public final class ClientRitualConflictState {

    private static final Map<BlockPos, Long> CONFLICTS = new HashMap<>();

    private ClientRitualConflictState() {
    }

    /** 服务端下发新一批冲突坐标：整体替换，按配置秒数计算过期刻。 */
    public static void update(List<BlockPos> positions) {
        CONFLICTS.clear();
        Minecraft minecraft = Minecraft.getInstance();
        long now = minecraft.level == null ? 0L : minecraft.level.getGameTime();
        long ttl = GensokyouConfig.RITUAL_BUILDER_OUTLINE_SECONDS.get() * 20L;
        long expire = now + ttl;
        for (BlockPos pos : positions) {
            CONFLICTS.put(pos.immutable(), expire);
        }
    }

    /** 当前仍在有效期内的冲突坐标（渲染阶段调用，顺带清理过期项）。 */
    public static Map<BlockPos, Long> active() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return Map.of();
        }
        long now = minecraft.level.getGameTime();
        CONFLICTS.values().removeIf(expire -> expire <= now);
        return CONFLICTS;
    }
}
