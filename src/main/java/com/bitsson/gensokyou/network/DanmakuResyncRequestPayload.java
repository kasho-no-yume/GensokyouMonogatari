package com.bitsson.gensokyou.network;

import com.bitsson.gensokyou.Gensokyou;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

/**
 * 失步恢复请求（客户端 → 服务端）：批量、按需、限频。
 *
 * <p><b>为什么是请求而不是服务端无条件重推</b>：一次失步风暴下，几百枚弹同时请求
 * 若都立即应答，服务端会在同一 tick 内被要求生成几百份快照——那正是最不该发生
 * 的时候（本该在降载）。请求进队列、由服务端按预算应答，把压力摊平。
 *
 * <p><b>请求 id 只是关联标记，不是授权</b>：服务端 MUST 自行校验该玩家确实在跟踪
 * 目标实体。客户端声称什么不构成依据。
 */
public record DanmakuResyncRequestPayload(long requestId, boolean withMotionParams,
                                           List<Integer> entityIds)
        implements CustomPacketPayload {

    /** 单次请求的实体数上限。 */
    public static final int MAX_IDS = 512;

    public static final Type<DanmakuResyncRequestPayload> TYPE =
            new Type<>(Gensokyou.id("danmaku_resync_request"));

    public static final StreamCodec<FriendlyByteBuf, DanmakuResyncRequestPayload> STREAM_CODEC =
            StreamCodec.ofMember(DanmakuResyncRequestPayload::write,
                    DanmakuResyncRequestPayload::read);

    private static void write(DanmakuResyncRequestPayload payload, FriendlyByteBuf buf) {
        buf.writeVarLong(payload.requestId);
        buf.writeBoolean(payload.withMotionParams);
        buf.writeVarInt(payload.entityIds.size());
        for (int id : payload.entityIds) {
            buf.writeVarInt(id);
        }
    }

    private static DanmakuResyncRequestPayload read(FriendlyByteBuf buf) {
        long requestId = buf.readVarLong();
        boolean withParams = buf.readBoolean();
        int count = Math.max(0, Math.min(MAX_IDS, buf.readVarInt()));
        List<Integer> ids = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            ids.add(buf.readVarInt());
        }
        return new DanmakuResyncRequestPayload(requestId, withParams, ids);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
