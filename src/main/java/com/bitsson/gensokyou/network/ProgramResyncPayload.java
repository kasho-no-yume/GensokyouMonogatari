package com.bitsson.gensokyou.network;

import com.bitsson.gensokyou.Gensokyou;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** C2S recovery envelope; deliberately much smaller than S2C snapshot chunks. */
public record ProgramResyncPayload(byte[] bytes) implements CustomPacketPayload {
    public ProgramResyncPayload { bytes=bytes.clone(); if (bytes.length>1024) throw new IllegalArgumentException("recovery request budget"); }
    @Override public byte[] bytes() { return bytes.clone(); }
    public static final Type<ProgramResyncPayload> TYPE=new Type<>(Gensokyou.id("program_resync"));
    public static final StreamCodec<FriendlyByteBuf,ProgramResyncPayload> STREAM_CODEC=StreamCodec.ofMember(ProgramResyncPayload::write,ProgramResyncPayload::read);
    private void write(FriendlyByteBuf buffer) { buffer.writeByteArray(bytes); }
    private static ProgramResyncPayload read(FriendlyByteBuf buffer) { return new ProgramResyncPayload(buffer.readByteArray(1024)); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
