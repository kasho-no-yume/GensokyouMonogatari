package com.bitsson.gensokyou.network;

import com.bitsson.gensokyou.Gensokyou;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 弹体年龄种子：服务端在某客户端<b>开始跟踪</b>某枚弹的瞬间，向该客户端单发它的年龄。
 *
 * <p><b>为什么必须是「每客户端一份」的包，而不是一个同步字段</b>——两个客户端开始跟踪
 * 同一枚弹的时刻不同，共享单一值必然弄坏其中一方。
 *
 * <p><b>为什么不能用 {@code SynchedEntityData}</b>：配对 bundle 携带的 entityData 是
 * {@code ServerEntity} <b>构造时的快照</b>，不是配对时刻的值。在配对时刻 {@code set}
 * 的字段要等下一次脏更新才到达客户端，滞后可达 {@code updateInterval} 个 tick；客户端
 * 首个 tick 就会以未更新的年龄调用 {@code setPos(解析位置)}，产生可见闪跳。
 *
 * <p><b>为什么不能借用 {@code ClientboundAddEntityPacket} 的 {@code data} 字段</b>：
 * {@code Projectile.getAddEntityPacket} 以它下发 owner 实体 id，客户端用
 * {@code getEntity(packet.getData())} 反查。该字段不是空槽。
 *
 * <p>发送时机：{@code PlayerEvent.StartTracking}，即 {@code ServerEntity.addPairing}
 * 发出生成包<b>之后</b>。同连接同顺序，客户端在同一批包处理中先建实体再拿年龄，
 * 故实体的首次 tick 时基准已就位。
 *
 * <p>带宽：每「每弹 × 每客户端 × 每次配对」约 5 字节，<b>持续带宽为零</b>。
 */
public record DanmakuAgePayload(int entityId, int age) implements CustomPacketPayload {

    public static final Type<DanmakuAgePayload> TYPE =
            new Type<>(Gensokyou.id("danmaku_age"));

    public static final StreamCodec<FriendlyByteBuf, DanmakuAgePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, DanmakuAgePayload::entityId,
                    ByteBufCodecs.VAR_INT, DanmakuAgePayload::age,
                    DanmakuAgePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
