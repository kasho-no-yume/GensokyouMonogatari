package com.bitsson.gensokyou.network;

import com.bitsson.gensokyou.danmaku.render.DanmakuMotionState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * 弹幕网络的定点编解码工具。
 *
 * <p><b>为什么不用 {@code StreamCodec.composite}</b>：便捷重载能组合的字段数有限，
 * 而本协议的分组身份、顺序、锚点、运动、参数块远超那个数量。改用
 * {@code StreamCodec.ofMember} 自行读写，字段数不再受重载限制——分组是<b>语义</b>需要，
 * 不是为了绕过网络限制。
 *
 * <p>位置用 1/4096 格定点 + varint（与原版实体位置包同一精度），比 float 省字节，
 * 比 double 少 4 倍带宽；速度同样定点。
 */
public final class DanmakuWire {

    /** 速度定点化比例。1/4096 格/tick ≈ 0.00024，对弹幕运动绰绰有余。 */
    public static final double VELOCITY_SCALE = 4096.0D;

    /** 速度的合法上限（格/tick）。超过即视为非法数据。 */
    public static final double VELOCITY_LIMIT = 512.0D;

    private DanmakuWire() {
    }

    public static void writePosition(FriendlyByteBuf buf, Vec3 value) {
        buf.writeVarLong(fixed(value.x, DanmakuMotionState.POSITION_SCALE));
        buf.writeVarLong(fixed(value.y, DanmakuMotionState.POSITION_SCALE));
        buf.writeVarLong(fixed(value.z, DanmakuMotionState.POSITION_SCALE));
    }

    public static Vec3 readPosition(FriendlyByteBuf buf) {
        double x = buf.readVarLong() / DanmakuMotionState.POSITION_SCALE;
        double y = buf.readVarLong() / DanmakuMotionState.POSITION_SCALE;
        double z = buf.readVarLong() / DanmakuMotionState.POSITION_SCALE;
        return new Vec3(x, y, z);
    }

    public static void writeVelocity(FriendlyByteBuf buf, Vec3 value) {
        buf.writeVarLong(fixed(value.x, VELOCITY_SCALE));
        buf.writeVarLong(fixed(value.y, VELOCITY_SCALE));
        buf.writeVarLong(fixed(value.z, VELOCITY_SCALE));
    }

    public static Vec3 readVelocity(FriendlyByteBuf buf) {
        double x = buf.readVarLong() / VELOCITY_SCALE;
        double y = buf.readVarLong() / VELOCITY_SCALE;
        double z = buf.readVarLong() / VELOCITY_SCALE;
        return new Vec3(x, y, z);
    }

    public static void writeUuid(FriendlyByteBuf buf, UUID uuid) {
        if (uuid == null) {
            buf.writeLong(0L);
            buf.writeLong(0L);
            return;
        }
        buf.writeLong(uuid.getMostSignificantBits());
        buf.writeLong(uuid.getLeastSignificantBits());
    }

    public static UUID readUuid(FriendlyByteBuf buf) {
        long most = buf.readLong();
        long least = buf.readLong();
        return most == 0L && least == 0L ? null : new UUID(most, least);
    }

    /**
     * 定点化。溢出与非有限值一律折到 0：宁可让这一个样本位置退化到原点被容差判据
     * 拒掉，也不要把一个 NaN 坐标写进网络——后者会一路传染到渲染与包围盒。
     */
    private static long fixed(double value, double scale) {
        if (!Double.isFinite(value)) {
            return 0L;
        }
        double scaled = value * scale;
        if (scaled >= Long.MAX_VALUE / 2.0D) {
            return Long.MAX_VALUE / 2L;
        }
        if (scaled <= Long.MIN_VALUE / 2.0D) {
            return Long.MIN_VALUE / 2L;
        }
        return Math.round(scaled);
    }
}
