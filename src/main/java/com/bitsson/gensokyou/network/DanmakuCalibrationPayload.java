package com.bitsson.gensokyou.network;

import com.bitsson.gensokyou.Gensokyou;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * 批量校准样本：低频、带服务器采样时刻的位置锚点。
 *
 * <p><b>它替代不了什么</b>：原版位置包照发、照被 {@code lerpTo} 收到，但只进诊断。
 * 本包的作用是给客户端一个<b>时间已知</b>的权威坐标，使它能与「同一采样时刻」的
 * 本地模拟状态比较，从而发现年龄基准漂移。
 *
 * <p><b>批量与低频是必需的，不是优化</b>：每弹每 tick 一次校准在 400 颗弹下就是
 * 8000 包/秒。本包的形态是「每 N tick 一批、每批覆盖全部被跟踪的弹」，N 由配置
 * 决定；N 越大越省带宽，漂移暴露得越晚。这是本阶段明确接受的取舍——提案里
 * 承诺的「零持续带宽」属于 {@code danmaku-timeline-sync} 的轨道级同步。
 *
 * <p><b>不带速度</b>：校准的职责是发现<b>位置</b>漂移。速度由完整快照携带，
 * 重复携带 3 个分量会让带宽翻倍而收益为零。
 */
public record DanmakuCalibrationPayload(long serverGameTime, List<Sample> samples)
        implements CustomPacketPayload {

    /**
     * 单条校准样本。
     *
     * @param entityId 实体 id（配合客户端自己的 UUID 校验）
     * @param sequence 单调序号，用于拒绝重复／过期样本
     * @param age      采样时刻的弹幕年龄
     * @param position 采样时刻的权威位置
     */
    public record Sample(int entityId, int sequence, int age, Vec3 position) {
    }

    /** 单批样本数上限。防恶意／损坏的长度前缀打爆客户端堆。 */
    public static final int MAX_SAMPLES = 1024;

    public static final Type<DanmakuCalibrationPayload> TYPE =
            new Type<>(Gensokyou.id("danmaku_calibration"));

    public static final StreamCodec<FriendlyByteBuf, DanmakuCalibrationPayload> STREAM_CODEC =
            StreamCodec.ofMember(DanmakuCalibrationPayload::write, DanmakuCalibrationPayload::read);

    private static void write(DanmakuCalibrationPayload payload, FriendlyByteBuf buf) {
        buf.writeVarLong(payload.serverGameTime);
        List<Sample> samples = payload.samples;
        buf.writeVarInt(samples.size());
        for (Sample sample : samples) {
            buf.writeVarInt(sample.entityId());
            buf.writeVarInt(sample.sequence());
            buf.writeVarInt(sample.age());
            DanmakuWire.writePosition(buf, sample.position());
        }
    }

    private static DanmakuCalibrationPayload read(FriendlyByteBuf buf) {
        long serverTime = buf.readVarLong();
        int count = Math.max(0, Math.min(MAX_SAMPLES, buf.readVarInt()));
        List<Sample> samples = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            samples.add(new Sample(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    DanmakuWire.readPosition(buf)));
        }
        return new DanmakuCalibrationPayload(serverTime, samples);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
