package com.bitsson.gensokyou.network;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.danmaku.render.DanmakuMotionState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * 实体级初始化／恢复快照：让客户端在一个原子更新边界内建立一致的运动状态。
 *
 * <p><b>为什么必须有它</b>——原版位置包只有一个坐标。没有它，「客户端年龄基准错了」
 * 这类失步在客户端<b>无法被修复</b>：本地模拟会一直按错误的自变量算下去，位置包
 * 又不带时间，既分不清是延迟还是失步，也无法重建。
 *
 * <p>携带内容分四组，缺一不可：
 * <ul>
 *   <li><b>身份</b>：{@code entityId} + {@code UUID} + {@code trackingToken}。
 *       id 会复用，故跨周期判定 MUST 用 UUID；token 区分「同一周期的重复包」
 *       与「新的追踪周期」，前者幂等丢弃、后者接受并重锚。</li>
 *   <li><b>锚点</b>：{@code serverGameTime} + {@code age}。客户端据此把服务器时刻
 *       映射回自己的本地 tick，从而能与「同一采样时刻」的模拟状态比较。</li>
 *   <li><b>运动</b>：{@code position} + {@code velocity} + {@code motionRevision}。</li>
 *   <li><b>参数</b>：{@code paramFingerprint} 常驻；{@code motionParams} 仅在指纹
 *       不符或显式请求修复时随包补发（见 {@link DanmakuMotionState} 类注释）。</li>
 * </ul>
 *
 * <p><b>它不声明「收到就等于服务器当前状态」</b>：采样到送达之间的延迟仍然存在。
 * 快照建立的是「一个可追溯的对应关系」，不是零延迟。
 */
public record DanmakuSnapshotPayload(int entityId, UUID uuid, long trackingToken,
                                     int motionRevision, long serverGameTime, int age,
                                     Vec3 position, Vec3 velocity,
                                     int paramFingerprint, int[] motionParams)
        implements CustomPacketPayload {

    public static final Type<DanmakuSnapshotPayload> TYPE =
            new Type<>(Gensokyou.id("danmaku_snapshot"));

    public static final StreamCodec<FriendlyByteBuf, DanmakuSnapshotPayload> STREAM_CODEC =
            StreamCodec.ofMember(DanmakuSnapshotPayload::write, DanmakuSnapshotPayload::read);

    /** 本次快照是否携带了完整运动输入（否则 {@code motionParams} 为空数组）。 */
    public boolean carriesMotionParams() {
        return this.motionParams != null && this.motionParams.length > 0;
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeVarInt(this.entityId);
        DanmakuWire.writeUuid(buf, this.uuid);
        buf.writeVarLong(this.trackingToken);
        buf.writeVarInt(this.motionRevision);
        buf.writeVarLong(this.serverGameTime);
        buf.writeVarInt(this.age);
        DanmakuWire.writePosition(buf, this.position);
        DanmakuWire.writeVelocity(buf, this.velocity);
        buf.writeVarInt(this.paramFingerprint);
        if (this.carriesMotionParams()) {
            buf.writeBoolean(true);
            buf.writeVarInt(this.motionParams.length);
            for (int value : this.motionParams) {
                buf.writeVarInt(value);
            }
        } else {
            buf.writeBoolean(false);
        }
    }

    private static DanmakuSnapshotPayload read(FriendlyByteBuf buf) {
        int entityId = buf.readVarInt();
        UUID uuid = DanmakuWire.readUuid(buf);
        long trackingToken = buf.readVarLong();
        int motionRevision = buf.readVarInt();
        long serverGameTime = buf.readVarLong();
        int age = buf.readVarInt();
        Vec3 position = DanmakuWire.readPosition(buf);
        Vec3 velocity = DanmakuWire.readVelocity(buf);
        int fingerprint = buf.readVarInt();
        int[] params = null;
        if (buf.readBoolean()) {
            // 长度必须封顶：这一项直接决定堆分配，恶意／损坏的长度前缀会打爆客户端。
            int count = Math.max(0, Math.min(DanmakuMotionState.PARAM_COUNT, buf.readVarInt()));
            params = new int[count];
            for (int i = 0; i < count; i++) {
                params[i] = buf.readVarInt();
            }
        }
        return new DanmakuSnapshotPayload(entityId, uuid, trackingToken, motionRevision,
                serverGameTime, age, position, velocity, fingerprint, params);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
