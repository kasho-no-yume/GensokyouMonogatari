package com.bitsson.gensokyou.network;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.spirit.SkillStateData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** S2C：技能槽同步（v2：5 格定容 + 配装卡 id；显示槽数由阶级裁剪，见 HUD）。 */
public record SkillSyncPayload(boolean[] learned, int[] remainingTicks, String[] equipped)
        implements CustomPacketPayload {

    public static final Type<SkillSyncPayload> TYPE =
            new Type<>(Gensokyou.id("skill_state_sync"));

    public static final StreamCodec<FriendlyByteBuf, SkillSyncPayload> STREAM_CODEC =
            StreamCodec.ofMember(SkillSyncPayload::write, SkillSyncPayload::read);

    private void write(FriendlyByteBuf buf) {
        for (int i = 0; i < SkillStateData.MAX_SLOTS; i++) {
            buf.writeBoolean(i < learned.length && learned[i]);
            buf.writeVarInt(i < remainingTicks.length ? Math.max(0, remainingTicks[i]) : 0);
        }
        for (int i = 0; i < SkillStateData.MAX_SLOTS; i++) {
            buf.writeUtf(i < equipped.length && equipped[i] != null ? equipped[i] : "");
        }
    }

    private static SkillSyncPayload read(FriendlyByteBuf buf) {
        boolean[] learned = new boolean[SkillStateData.MAX_SLOTS];
        int[] remaining = new int[SkillStateData.MAX_SLOTS];
        for (int i = 0; i < SkillStateData.MAX_SLOTS; i++) {
            learned[i] = buf.readBoolean();
            remaining[i] = buf.readVarInt();
        }
        String[] equipped = new String[SkillStateData.MAX_SLOTS];
        for (int i = 0; i < SkillStateData.MAX_SLOTS; i++) {
            equipped[i] = buf.readUtf();
        }
        return new SkillSyncPayload(learned, remaining, equipped);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
