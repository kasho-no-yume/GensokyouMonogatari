package com.bitsson.gensokyou.danmaku.render;

import com.bitsson.gensokyou.network.DanmakuResyncRequestPayload;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 失步恢复请求的<b>合并与限频</b>队列。
 *
 * <p><b>为什么放在 common 而不是 client 包</b>：它被 {@code AbstractDanmakuProjectile}
 * 的每 tick 路径直接引用，而那个类在专用服务端上也要加载。让 common 类静态引用一个
 * client 类，即使调用点在客户端分支里，也是把「类加载失败」的风险留在服务端。
 * 本类只用 {@link PacketDistributor}（common API）发送，因此两侧都能安全加载；
 * 真正的 tick 泵由一个 client-only 的事件类驱动。
 *
 * <p><b>三重去重</b>：实体自身按退避节流（{@link DanmakuRenderState#canRequestAgain}）、
 * 本队列按 id 去重、本队列再按全局节奏限流。少任何一层，失步风暴都会把带宽吃光——
 * 而那正是最不该占用带宽的时刻。
 */
public final class DanmakuResyncQueue {

    /** 全局最短发送间隔（tick）。把一帧内成百上千的请求压成一批。 */
    public static final int MIN_SEND_INTERVAL_TICKS = 5;

    /** 单次请求携带的实体数上限。 */
    public static final int MAX_BATCH = 128;

    private static final Set<Integer> PENDING = new LinkedHashSet<>();
    private static final Set<Integer> PENDING_WITH_PARAMS = new LinkedHashSet<>();

    private static long nextRequestId = 1L;
    private static long lastSendTick = Long.MIN_VALUE;

    private DanmakuResyncQueue() {
    }

    /**
     * 登记一次恢复请求。重复登记同一实体只保留最新一次。
     *
     * @param withParams 是否需要同时补发完整运动输入（指纹不符时为 true）
     */
    public static void submit(int entityId, boolean withParams) {
        if (entityId == 0) {
            return;
        }
        PENDING.add(entityId);
        if (withParams) {
            PENDING_WITH_PARAMS.add(entityId);
        } else {
            PENDING_WITH_PARAMS.remove(entityId);
        }
    }

    /** 队列中待发实体数。诊断用。 */
    public static int pendingCount() {
        return PENDING.size();
    }

    public static void clear() {
        PENDING.clear();
        PENDING_WITH_PARAMS.clear();
        lastSendTick = Long.MIN_VALUE;
    }

    /**
     * 每客户端 tick 泵一次。空队列时立即返回，不做任何分配。
     *
     * @param tick 客户端世界时间，仅用于节奏限制
     */
    public static void onClientTick(long tick) {
        if (PENDING.isEmpty()) {
            return;
        }
        if (lastSendTick != Long.MIN_VALUE
                && tick - lastSendTick < MIN_SEND_INTERVAL_TICKS) {
            return;
        }
        lastSendTick = tick;

        List<Integer> batch = new ArrayList<>(Math.min(MAX_BATCH, PENDING.size()));
        boolean withParams = false;
        for (Integer id : PENDING) {
            if (batch.size() >= MAX_BATCH) {
                break;
            }
            batch.add(id);
            if (PENDING_WITH_PARAMS.contains(id)) {
                withParams = true;
            }
        }
        for (Integer id : batch) {
            PENDING.remove(id);
            PENDING_WITH_PARAMS.remove(id);
        }
        PacketDistributor.sendToServer(
                new DanmakuResyncRequestPayload(nextRequestId++, withParams, batch));
        DanmakuSyncStats.recordResyncRequestSent();
    }
}
