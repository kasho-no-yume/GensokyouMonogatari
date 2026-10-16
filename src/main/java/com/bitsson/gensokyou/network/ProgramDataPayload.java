package com.bitsson.gensokyou.network;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.danmaku.program.network.NetworkLimits;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** S2C program protocol chunk. Envelope decoding runs before state installation. */
public record ProgramDataPayload(byte[] bytes) implements CustomPacketPayload {
    public ProgramDataPayload { bytes=bytes.clone(); if (bytes.length>NetworkLimits.defaults().messageBytes()) throw new IllegalArgumentException("program message budget"); }
    @Override public byte[] bytes() { return bytes.clone(); }
    public static final Type<ProgramDataPayload> TYPE=new Type<>(Gensokyou.id("program_data"));
    public static final StreamCodec<FriendlyByteBuf,ProgramDataPayload> STREAM_CODEC=StreamCodec.ofMember(ProgramDataPayload::write,ProgramDataPayload::read);
    private void write(FriendlyByteBuf buffer) { buffer.writeByteArray(bytes); }
    private static ProgramDataPayload read(FriendlyByteBuf buffer) { return new ProgramDataPayload(buffer.readByteArray(NetworkLimits.defaults().messageBytes())); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
