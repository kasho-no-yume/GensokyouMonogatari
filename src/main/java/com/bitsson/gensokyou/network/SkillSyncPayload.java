package com.bitsson.gensokyou.network;

import com.bitsson.gensokyou.Gensokyou;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record SkillSyncPayload(boolean[] learned, int[] remainingTicks) implements CustomPacketPayload {

    public static final Type<SkillSyncPayload> TYPE =
            new Type<>(Gensokyou.id("skill_state_sync"));

    public static final StreamCodec<FriendlyByteBuf, SkillSyncPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, payload -> learnedAt(payload, 0),
                    ByteBufCodecs.BOOL, payload -> learnedAt(payload, 1),
                    ByteBufCodecs.BOOL, payload -> learnedAt(payload, 2),
                    ByteBufCodecs.VAR_INT, payload -> remainingAt(payload, 0),
                    ByteBufCodecs.VAR_INT, payload -> remainingAt(payload, 1),
                    ByteBufCodecs.VAR_INT, payload -> remainingAt(payload, 2),
                    SkillSyncPayload::new);

    private static boolean learnedAt(SkillSyncPayload payload, int slot) {
        return slot < payload.learned().length && payload.learned()[slot];
    }

    private static int remainingAt(SkillSyncPayload payload, int slot) {
        return slot < payload.remainingTicks().length ? payload.remainingTicks()[slot] : 0;
    }

    public SkillSyncPayload(boolean l0, boolean l1, boolean l2, int r0, int r1, int r2) {
        this(new boolean[]{l0, l1, l2}, new int[]{r0, r1, r2});
    }

    public SkillSyncPayload(boolean[] learned, int[] remainingTicks) {
        this.learned = new boolean[]{learned.length > 0 && learned[0],
                learned.length > 1 && learned[1], learned.length > 2 && learned[2]};
        this.remainingTicks = new int[]{remainingTicks.length > 0 ? remainingTicks[0] : 0,
                remainingTicks.length > 1 ? remainingTicks[1] : 0,
                remainingTicks.length > 2 ? remainingTicks[2] : 0};
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
