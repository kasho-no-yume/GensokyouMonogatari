package com.bitsson.gensokyou.audio.client;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.audio.Audio;
import com.bitsson.gensokyou.audio.MusicHandle;
import com.bitsson.gensokyou.audio.bossbgm.BgmTrack;
import com.bitsson.gensokyou.audio.bossbgm.BossBgms;
import com.bitsson.gensokyou.audio.bossbgm.BossBgmSelector;
import com.bitsson.gensokyou.entity.TouhouBoss;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * BOSS 战 BGM 的客户端「总监」：扫描 → 选曲 → 淡入淡出 → 失效恢复。
 *
 * <h2>为什么是纯客户端</h2>
 * 实体本来就同步给客户端，BGM 又是每个客户端本地的表现（与原版情境音乐一致）。
 * 服务端广播音轨需要额外包，还要处理中途加入/观战/跨维度，收益为零。
 *
 * <h2>生命周期</h2>
 * <pre>
 *   idle ──选中──▶ 播放(淡入 2s) ──事件 id 变化──▶ crossfade
 *     ▲                 │
 *     └──无候选/超距─────┘（淡出 2.5s）
 * </pre>
 *
 * 换曲的键是<b>事件 id</b>：同一只 BOSS 的符卡切换、同阶换 BOSS 都不会重开。
 *
 * <h2>存活探测</h2>
 * 资源重载（{@code F3+T}）会让 {@code SoundEngine.reload()} 丢掉正在播的实例，
 * 而本类毫不知情。因此每 tick 用 {@link Audio#isMusicActive} 检测一次；
 * 被丢弃即按当前曲目重起——顺手把「换包后用新文件恢复」也解决了。
 */
@EventBusSubscriber(modid = Gensokyou.MODID, value = Dist.CLIENT)
public final class BossBgmDirector {

    /** 扫描周期（tick）。每 tick 扫实体没有必要。 */
    static final int SCAN_TICKS = 10;
    /** 进入距离；上限与 {@code AbstractTouhouBoss.LOCK_RADIUS} 对齐但不取满。 */
    static final double ENTER_RADIUS = 32.0D;
    /** 退出距离：必须大于进入距离，形成滞回。 */
    static final double EXIT_RADIUS = 48.0D;
    /** 失效重起的冷却，避免异常情况下每 tick 重建实例。 */
    static final int RESTART_COOLDOWN_TICKS = 20;

    private static MusicHandle current;
    private static ResourceLocation currentEventId;
    private static BossBgmSelector.Selection selection;
    private static int scanCooldown;
    private static int restartCooldown;

    private BossBgmDirector() {
    }

    /** 当前是否有 BGM 在响（原版音乐抑制器读这个）。 */
    public static boolean isMusicActive() {
        return current != null && !current.stopped();
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        tick(Minecraft.getInstance());
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        forget();
    }

    static void tick(Minecraft minecraft) {
        if (minecraft.level == null || minecraft.player == null) {
            forget();
            return;
        }
        if (restartCooldown > 0) {
            restartCooldown--;
        }
        if (current != null) {
            if (!Audio.isMusicActive(current) && restartCooldown <= 0) {
                restart();
            }
            current.keepAlive();
        }
        if (--scanCooldown > 0) {
            return;
        }
        scanCooldown = SCAN_TICKS;
        scan(minecraft);
    }

    private static void scan(Minecraft minecraft) {
        ClientLevel level = minecraft.level;
        LocalPlayer player = minecraft.player;
        if (level == null || player == null) {
            return;
        }
        double exitSqr = EXIT_RADIUS * EXIT_RADIUS;
        double enterSqr = ENTER_RADIUS * ENTER_RADIUS;

        List<BossBgmSelector.Candidate> candidates = new ArrayList<>();
        for (Entity entity : level.getEntities(player, player.getBoundingBox().inflate(EXIT_RADIUS))) {
            if (!(entity instanceof TouhouBoss boss) || !entity.isAlive()) {
                continue;
            }
            ResourceLocation typeId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
            if (typeId == null || BossBgms.excluded(typeId)) {
                continue;
            }
            double distanceSqr = entity.distanceToSqr(player);
            if (distanceSqr > exitSqr) {
                continue;
            }
            int tier = Math.max(1, Math.min(5, boss.bossTier()));
            candidates.add(new BossBgmSelector.Candidate(
                    entity.getUUID(), tier, distanceSqr, isTargeting(entity, player),
                    BossBgms.eventFor(typeId, tier)));
        }

        Optional<BossBgmSelector.Selection> picked = BossBgmSelector.select(candidates, selection);
        BossBgmSelector.Selection next = picked.orElse(null);
        // 没在放时必须在进入半径内才开始；已在放则容忍到退出半径（滞回）。
        if (next != null && current == null && next.distanceSqr() > enterSqr) {
            next = null;
        }
        selection = next;

        if (next == null) {
            stopCurrent();
            return;
        }
        if (current != null && next.eventId().equals(currentEventId)) {
            return;
        }
        start(next.eventId());
    }

    private static boolean isTargeting(Entity entity, LocalPlayer player) {
        return entity instanceof Mob mob && mob.getTarget() == player;
    }

    private static void start(ResourceLocation eventId) {
        BgmTrack track = BossBgms.trackForEvent(eventId);
        if (track == null) {
            // 理论上不会发生：事件 id 全部来自 BossBgms。留日志而不是抛异常。
            Gensokyou.LOGGER.warn("[audio/bgm] 未知音乐事件 {}，跳过", eventId);
            return;
        }
        if (current != null) {
            Audio.stopMusic(current);
        }
        current = Audio.startMusic(eventId, track);
        currentEventId = eventId;
    }

    private static void stopCurrent() {
        if (current != null) {
            Audio.stopMusic(current);
            current = null;
            currentEventId = null;
        }
    }

    private static void restart() {
        ResourceLocation eventId = currentEventId;
        MusicHandle dead = current;
        current = null;
        currentEventId = null;
        // 旧实例可能已被引擎丢弃；stopMusic 只对活着的实例有意义，且是幂等的。
        Audio.stopMusic(dead);
        if (eventId != null) {
            start(eventId);
        }
        restartCooldown = RESTART_COOLDOWN_TICKS;
    }

    /** 断线 / 回标题：丢弃全部状态，允许原版音乐恢复。 */
    private static void forget() {
        current = null;
        currentEventId = null;
        selection = null;
        scanCooldown = 0;
    }

    /** 测试/诊断用：当前曲目 id。 */
    @Nullable
    static ResourceLocation currentEventId() {
        return currentEventId;
    }
}
