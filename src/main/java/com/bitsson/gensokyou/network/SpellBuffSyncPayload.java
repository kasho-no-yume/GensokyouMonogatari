package com.bitsson.gensokyou.network;

import com.bitsson.gensokyou.Gensokyou;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 玩家符卡即时状态同步（add-player-spellcards）：花瓣护盾余量与疫符窗口剩余 tick。
 * 剩余量用"剩余 tick"而非绝对 gameTime，客户端本地递减，避免两端时钟差。
 */
public record SpellBuffSyncPayload(int petals, int armorTicks, int plagueTicks) implements CustomPacketPayload {

    public static final Type<SpellBuffSyncPayload> TYPE = new Type<>(Gensokyou.id("spell_buff_sync"));

    public static final StreamCodec<FriendlyByteBuf, SpellBuffSyncPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, SpellBuffSyncPayload::petals,
                    ByteBufCodecs.VAR_INT, SpellBuffSyncPayload::armorTicks,
                    ByteBufCodecs.VAR_INT, SpellBuffSyncPayload::plagueTicks,
                    SpellBuffSyncPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
