package com.bitsson.gensokyou.client;

import com.bitsson.gensokyou.entity.spell.FoxServantEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.HashSet;
import java.util.Set;

/**
 * 客户端判断某个生物是否正被狐火附身（供两个渲染 mixin 用）。
 *
 * <p>不依赖 MobEffect——原版不会把非玩家生物的完整效果同步到客户端，只同步装饰粒子。
 * 狐火实体自身的 {@code DATA_STATE}/{@code DATA_TARGET} 是 {@code SynchedEntityData}，会自动同步。
 *
 * <p>每 gameTime 只重建一次"被附身目标 id 集合"，避免每次渲染都扫描周围实体。
 */
public final class FoxPossessionClient {
    private static final Set<Integer> POSSESSED = new HashSet<>();
    private static long lastRebuildTick = -1L;

    private FoxPossessionClient() {
    }

    public static boolean isSoulBurning(Entity entity) {
        if (!(entity instanceof LivingEntity)) {
            return false;
        }
        rebuildIfNeeded();
        return POSSESSED.contains(entity.getId());
    }

    private static void rebuildIfNeeded() {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            POSSESSED.clear();
            return;
        }
        long now = level.getGameTime();
        if (now == lastRebuildTick) {
            return;
        }
        lastRebuildTick = now;
        POSSESSED.clear();
        for (net.minecraft.world.entity.Entity e : level.entitiesForRendering()) {
            if (e instanceof FoxServantEntity fox
                    && fox.state() == FoxServantEntity.STATE_POSSESSING && fox.targetId() != 0) {
                POSSESSED.add(fox.targetId());
            }
        }
    }
}
