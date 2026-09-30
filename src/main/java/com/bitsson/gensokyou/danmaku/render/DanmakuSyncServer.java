package com.bitsson.gensokyou.danmaku.render;

import com.bitsson.gensokyou.config.GensokyouConfig;
import com.bitsson.gensokyou.entity.AbstractDanmakuProjectile;
import com.bitsson.gensokyou.network.DanmakuCalibrationPayload;
import com.bitsson.gensokyou.network.DanmakuResyncRequestPayload;
import com.bitsson.gensokyou.network.DanmakuSnapshotPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * 服务端侧的弹幕状态同步：初始化快照、低频校准、恢复应答。
 *
 * <p>三条路径的分工：
 * <ul>
 *   <li><b>开始追踪</b> → 主动推送一份完整快照。这是「双端自变量对齐」的唯一入口。</li>
 *   <li><b>每 N tick</b> → 批量校准样本。让客户端能发现年龄基准漂移，
 *       并在与「同一采样时刻」的本地状态比较后把画面拉回权威轨迹。</li>
 *   <li><b>客户端请求</b> → 应答完整快照。请求只作提示，
 *       <b>是否真的在跟踪该实体由本类自行校验</b>。</li>
 * </ul>
 *
 * <p>校准的频率是本阶段明确接受的带宽取舍，配置项 {@code danmakuCalibrationIntervalTicks}
 * 置 0 即完全关闭（退回纯原版位置包 + 兼容诊断）。
 */
@EventBusSubscriber(modid = com.bitsson.gensokyou.Gensokyou.MODID)
public final class DanmakuSyncServer {

    /** 单次恢复请求最多应答几枚弹。请求本身已被协议层限到 128，这里是服务端侧的二次上限。 */
    public static final int MAX_SNAPSHOTS_PER_REQUEST = 64;

    /** 校准样本的单调序号。客户端用它拒绝重复与过期样本。 */
    private static int calibrationSequence;

    /** 服务端 tick 计数。用自己的计数器而不是 {@code MinecraftServer} 的内部计数。 */
    private static int serverTicks;

    private DanmakuSyncServer() {
    }

    /**
     * 开始跟踪：登记 + 推送完整快照。
     *
     * <p>NeoForge 在 {@code ServerEntity.addPairing} 内、生成包发出<b>之后</b>才触发本事件，
     * 故快照必定排在生成包之后到达，客户端实体的首次 tick 时状态已就位。
     */
    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(event.getTarget() instanceof AbstractDanmakuProjectile bullet)) {
            return;
        }
        long token = bullet.noteTracking();
        DanmakuTrackingIndex.onStartTracking(player.getUUID(), bullet.getId());
        sendSnapshot(player, bullet, token, false);
    }

    @SubscribeEvent
    public static void onStopTracking(PlayerEvent.StopTracking event) {
        if (event.getEntity() instanceof ServerPlayer player
                && event.getTarget() instanceof AbstractDanmakuProjectile bullet) {
            DanmakuTrackingIndex.onStopTracking(player.getUUID(), bullet.getId());
            // 追踪周期到此结束。不清零的话，「重复配对」会把玩家飞远再回来这种
            // 合法重新配对和真正的重复触发数在一起，那条诊断就再也回答不了
            // 「有没有 bug」——见 AbstractDanmakuProjectile#endTrackingPeriod。
            bullet.endTrackingPeriod();
        }
    }

    /**
     * 实体离世界：作废所有玩家对它的追踪关系。
     *
     * <p>漏掉这一步的后果不是「内存涨一点」：条目会留在索引里，于是校准继续为一枚
     * 已经死掉的弹占用带宽，而恢复请求会对着一个空壳通过「确实在跟踪」的校验。
     */
    @SubscribeEvent
    public static void onEntityLeave(net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof AbstractDanmakuProjectile) {
            DanmakuTrackingIndex.onEntityRemoved(event.getEntity().getId());
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            DanmakuTrackingIndex.onPlayerLeft(player.getUUID());
        }
    }

    /** 低频校准。每 tick 检查一次，命中间隔的那一 tick 才真正组包。 */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        int tick = ++serverTicks;
        DanmakuTrackingIndex.prune(id -> entityAlive(server, id));
        int interval = GensokyouConfig.DANMAKU_CALIBRATION_INTERVAL_TICKS.get();
        if (interval <= 0 || tick % interval != 0) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            for (ServerPlayer player : level.players()) {
                sendCalibration(level, player);
            }
        }
    }

    /**
     * 实体是否仍存在于任一维度。
     *
     * <p>{@code getAllLevels()} 返回 {@code Iterable} 而非 {@code List}，没有
     * {@code stream()}——写成流会直接编译不过。
     */
    private static boolean entityAlive(MinecraftServer server, int entityId) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.getEntity(entityId) != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * 给一名玩家发一批校准样本。
     *
     * <p>批内所有样本共享同一个服务器时刻与同一个序号——它们本来就是同一次采样循环的
     * 产物。逐弹单独发包会把同一个时间戳重复 N 次，那部分带宽是白花的。
     */
    private static void sendCalibration(ServerLevel level, ServerPlayer player) {
        List<Integer> tracked = DanmakuTrackingIndex.trackedIds(player.getUUID());
        if (tracked.isEmpty()) {
            return;
        }
        int cap = Math.max(1, GensokyouConfig.DANMAKU_CALIBRATION_BATCH_CAP.get());
        int sequence = ++calibrationSequence;
        List<DanmakuCalibrationPayload.Sample> samples = new ArrayList<>();
        for (Integer id : tracked) {
            if (samples.size() >= cap) {
                break;
            }
            if (!(level.getEntity(id) instanceof AbstractDanmakuProjectile bullet)
                    || bullet.isRemoved()) {
                continue;
            }
            samples.add(new DanmakuCalibrationPayload.Sample(
                    id, sequence, bullet.age(), bullet.position()));
        }
        if (samples.isEmpty()) {
            return;
        }
        PacketDistributor.sendToPlayer(player,
                new DanmakuCalibrationPayload(level.getGameTime(), samples));
        DanmakuSyncStats.recordCalibrationBatch(samples.size());
    }

    /**
     * 客户端恢复请求。
     *
     * <p>校验顺序 MUST 是「先确认在跟踪，再取实体」：先取实体的话，一个已经知道
     * entity id 的客户端可以让服务端反复生成它碰巧能猜到的实体的快照。
     */
    public static void handleResyncRequest(DanmakuResyncRequestPayload payload,
                                           IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            ServerLevel level = player.serverLevel();
            int served = 0;
            for (Integer id : payload.entityIds()) {
                if (served >= MAX_SNAPSHOTS_PER_REQUEST) {
                    break;
                }
                if (!DanmakuTrackingIndex.isTracking(player.getUUID(), id)) {
                    DanmakuSyncStats.recordResyncRefused();
                    continue;
                }
                if (level.getEntity(id) instanceof AbstractDanmakuProjectile bullet
                        && !bullet.isRemoved()) {
                    sendSnapshot(player, bullet, bullet.trackingToken(), payload.withMotionParams());
                    served++;
                    DanmakuSyncStats.recordResyncServed();
                } else {
                    DanmakuSyncStats.recordResyncRefused();
                }
            }
        });
    }

    /**
     * 采集并下发一份快照。
     *
     * <p>所有分量取自<b>同一次</b>服务端更新边界：位置、速度、年龄、参数指纹。跨 tick
     * 拼装出来的快照会自相矛盾，而客户端拿它重锚之后，症状是「重建成功但依然错位」——
     * 那比不重建更难查。
     */
    private static void sendSnapshot(ServerPlayer player, AbstractDanmakuProjectile bullet,
                                     long token, boolean withParams) {
        int age = bullet.age();
        int revision = DanmakuMotionState.revisionFor(age);
        Vec3 position = bullet.position();
        Vec3 velocity = bullet.getDeltaMovement();
        int[] params = withParams ? bullet.motionParams() : null;
        PacketDistributor.sendToPlayer(player, new DanmakuSnapshotPayload(
                bullet.getId(), bullet.getUUID(), token, revision,
                bullet.level().getGameTime(), age, position, velocity,
                bullet.motionFingerprint(), params));
        DanmakuSyncStats.recordSnapshotSent();
    }
}
