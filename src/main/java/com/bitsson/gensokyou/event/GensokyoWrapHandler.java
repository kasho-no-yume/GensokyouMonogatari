package com.bitsson.gensokyou.event;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.worldgen.GensokyoLayout;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;

/** Server-side prototype for same-dimension torus wrapping in the Sanzu test sea. */
@EventBusSubscriber(modid = Gensokyou.MODID)
public final class GensokyoWrapHandler {
    private static final double PREFETCH_MARGIN = 64.0;
    private static final int PREFETCH_TICKET_DISTANCE = 4;
    private static final Map<UUID, PrefetchTicket> PREFETCH_TICKETS = new HashMap<>();

    private GensokyoWrapHandler() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !player.level().dimension().location().equals(Gensokyou.id("gensokyo"))) {
            return;
        }
        if (!Boolean.getBoolean("gensokyou.enableDimensionWrap")) {
            removePrefetchTicket(player);
            return;
        }
        Entity root = player.getVehicle() == null ? player : player.getVehicle();
        Vec3 position = root.position();
        double half = GensokyoLayout.WRAP_PERIOD / 2.0;
        prefetchOppositeEdge(player, position, half);
        double x = wrapped(position.x, half);
        double z = wrapped(position.z, half);
        if (x == position.x && z == position.z) {
            return;
        }
        Map<Entity, Vec3> velocities = new IdentityHashMap<>();
        root.getSelfAndPassengers().forEach(entity -> velocities.put(entity, entity.getDeltaMovement()));
        if (root == player) {
            player.connection.teleport(x, position.y, z, player.getYRot(), player.getXRot(), java.util.Set.of());
        } else {
            root.teleportTo(x, position.y, z);
        }
        velocities.forEach(Entity::setDeltaMovement);
        root.getSelfAndPassengers().filter(ServerPlayer.class::isInstance).map(ServerPlayer.class::cast)
                .forEach(passenger -> passenger.connection.send(new ClientboundSetEntityMotionPacket(passenger)));
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            removePrefetchTicket(player);
        }
    }

    private static void prefetchOppositeEdge(ServerPlayer player, Vec3 position, double half) {
        double targetX = position.x;
        double targetZ = position.z;
        if (position.x >= half - PREFETCH_MARGIN) {
            targetX -= GensokyoLayout.WRAP_PERIOD;
        } else if (position.x < -half + PREFETCH_MARGIN) {
            targetX += GensokyoLayout.WRAP_PERIOD;
        }
        if (position.z >= half - PREFETCH_MARGIN) {
            targetZ -= GensokyoLayout.WRAP_PERIOD;
        } else if (position.z < -half + PREFETCH_MARGIN) {
            targetZ += GensokyoLayout.WRAP_PERIOD;
        }
        boolean nearSeam = targetX != position.x || targetZ != position.z;
        if (!nearSeam) {
            removePrefetchTicket(player);
            return;
        }

        ChunkPos targetChunk = new ChunkPos(((int) Math.floor(targetX)) >> 4,
                ((int) Math.floor(targetZ)) >> 4);
        PrefetchTicket current = PREFETCH_TICKETS.get(player.getUUID());
        if (current != null && current.chunkPos().equals(targetChunk)) {
            return;
        }
        removePrefetchTicket(player);
        BlockPos marker = new BlockPos(targetChunk.getMinBlockX(), player.getId(), targetChunk.getMinBlockZ());
        player.serverLevel().getChunkSource().addRegionTicket(TicketType.PORTAL, targetChunk,
                PREFETCH_TICKET_DISTANCE, marker);
        PREFETCH_TICKETS.put(player.getUUID(), new PrefetchTicket(targetChunk, marker));
    }

    private static void removePrefetchTicket(ServerPlayer player) {
        PrefetchTicket ticket = PREFETCH_TICKETS.remove(player.getUUID());
        if (ticket != null) {
            player.serverLevel().getChunkSource().removeRegionTicket(TicketType.PORTAL, ticket.chunkPos(),
                    PREFETCH_TICKET_DISTANCE, ticket.marker());
        }
    }

    private static double wrapped(double coordinate, double halfPeriod) {
        double result = coordinate;
        while (result >= halfPeriod) {
            result -= GensokyoLayout.WRAP_PERIOD;
        }
        while (result < -halfPeriod) {
            result += GensokyoLayout.WRAP_PERIOD;
        }
        return result;
    }

    private record PrefetchTicket(ChunkPos chunkPos, BlockPos marker) {
    }
}
