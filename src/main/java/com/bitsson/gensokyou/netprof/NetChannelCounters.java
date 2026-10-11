package com.bitsson.gensokyou.netprof;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-connection outbound accounting for the network profiler.
 *
 * <p>Two handlers straddle the {@code encoder} (Packet -&gt; ByteBuf):
 * {@link ClassHandler} runs tail-ward of it and sees the {@link Packet} (so it knows the
 * class, the entity type, and the payload id), {@link SizeHandler} runs head-ward of it and
 * sees the encoded {@link ByteBuf} (so it knows the exact pre-compression size). They are
 * inserted as a pair and share a {@link Counters}; because a channel's pipeline runs on a
 * single event-loop thread, the class recorded by one is the size attributed by the other.
 *
 * <p>Placement note (NeoForge 21.1 pipeline order):
 * {@code head .. splitter, decoder, prepender, compress, encoder, hackfix, packet_handler}.
 * Outbound flows tail-&gt;head, so "after encoder" is tail-ward (sees Packet) and
 * "before encoder" is head-ward (sees ByteBuf).
 */
final class NetChannelCounters {

    private NetChannelCounters() {
    }

    /** Shared, per-connection accumulator. Touched by the event loop and the server thread. */
    static final class Counters {
        /** Set by ClassHandler, read by SizeHandler -- both on the same event-loop thread. */
        String pendingClass;
        private final Map<String, long[]> data = new HashMap<>();
        /** Live entity-id -> type, maintained from Add/RemoveEntity so we can report concurrency. */
        private final Map<Integer, String> liveById = new HashMap<>();
        private final Map<String, Long> liveByType = new HashMap<>();

        synchronized void add(int size) {
            String cls = this.pendingClass == null ? "raw" : this.pendingClass;
            long[] slot = this.data.computeIfAbsent(cls, k -> new long[2]);
            slot[0] += size;
            slot[1]++;
        }

        synchronized void addEntity(int id, String type) {
            String previous = this.liveById.put(id, type);
            if (previous != null) {
                dropLive(previous);
            }
            this.liveByType.merge(type, 1L, Long::sum);
        }

        synchronized void removeEntity(int id) {
            String type = this.liveById.remove(id);
            if (type != null) {
                dropLive(type);
            }
        }

        private void dropLive(String type) {
            Long count = this.liveByType.get(type);
            if (count == null) {
                return;
            }
            if (count <= 1L) {
                this.liveByType.remove(type);
            } else {
                this.liveByType.put(type, count - 1L);
            }
        }

        synchronized Map<String, long[]> drain() {
            if (this.data.isEmpty()) {
                return Map.of();
            }
            Map<String, long[]> out = new HashMap<>(this.data);
            this.data.clear();
            return out;
        }

        /** Snapshot of current concurrency per entity type; does NOT clear (state is cumulative). */
        synchronized Map<String, Long> snapshotLive() {
            return new HashMap<>(this.liveByType);
        }
    }

    /** Runs tail-ward of the encoder: records the class/type/payload of the packet being written. */
    static final class ClassHandler extends ChannelOutboundHandlerAdapter {
        private final Counters counters;

        ClassHandler(Counters counters) {
            this.counters = counters;
        }

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise)
                throws Exception {
            if (msg instanceof ClientboundAddEntityPacket add) {
                String type = String.valueOf(BuiltInRegistries.ENTITY_TYPE.getKey(add.getType()));
                this.counters.pendingClass = "AddEntity:" + type;
                this.counters.addEntity(add.getId(), type);
            } else if (msg instanceof ClientboundCustomPayloadPacket payload) {
                this.counters.pendingClass = "Payload:" + payload.payload().type().id();
            } else if (msg instanceof ClientboundRemoveEntitiesPacket remove) {
                this.counters.pendingClass = "ClientboundRemoveEntitiesPacket";
                for (int id : remove.getEntityIds()) {
                    this.counters.removeEntity(id);
                }
            } else if (msg instanceof Packet<?> packet) {
                this.counters.pendingClass = packet.getClass().getSimpleName();
            }
            ctx.write(msg, promise);
        }
    }

    /** Runs head-ward of the encoder: records the encoded size against the pending class. */
    static final class SizeHandler extends ChannelOutboundHandlerAdapter {
        private final Counters counters;

        SizeHandler(Counters counters) {
            this.counters = counters;
        }

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise)
                throws Exception {
            int size = sizeOf(msg);
            if (size > 0) {
                this.counters.add(size);
            }
            ctx.write(msg, promise);
        }

        private static int sizeOf(Object msg) {
            if (msg instanceof ByteBuf buf) {
                return buf.readableBytes();
            }
            if (msg instanceof List<?> list) {
                int total = 0;
                for (Object element : list) {
                    if (element instanceof ByteBuf buf) {
                        total += buf.readableBytes();
                    }
                }
                return total;
            }
            return 0;
        }
    }
}
