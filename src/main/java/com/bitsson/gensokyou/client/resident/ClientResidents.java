package com.bitsson.gensokyou.client.resident;

import com.bitsson.gensokyou.Gensokyou;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.BlockGetter;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Bounded local presentation state. No Entity, server hooks, persistence or packets. */
@EventBusSubscriber(modid = Gensokyou.MODID, value = Dist.CLIENT)
public final class ClientResidents {
    private static List<ResidentScene> scenes = List.of();
    private static List<ResidentScene.Slot> preview = List.of();
    private static final Map<String, ResidentWalker> active = new HashMap<>();
    private static List<ResidentWalker> visible = List.of();
    private static ClientLevel lastLevel;
    private static int drawn;

    private ClientResidents() {}

    static void install(List<ResidentScene> definitions) {
        scenes = List.copyOf(definitions);
        clearWorld();
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            clearWorld();
            lastLevel = mc.level;
        }
        if (mc.level == null || mc.player == null || mc.isPaused()) return;
        List<ResidentScene.Slot> candidates = new ArrayList<>();
        String dimension = mc.level.dimension().location().toString();
        if (preview.isEmpty()) {
            for (ResidentScene scene : scenes) {
                if (scene.dimension().equals(dimension)) candidates.addAll(scene.residents());
            }
        }
        // Explicit local preview replaces the ordinary scene until cleared.
        candidates.addAll(preview);
        var viewer = mc.player.position();
        List<ResidentScene.Slot> nearest = ResidentScene.nearest(candidates,
                new ResidentRoute.Point(viewer.x, viewer.y, viewer.z));
        var selected = nearest.stream().map(ResidentScene.Slot::identity).collect(java.util.stream.Collectors.toSet());
        active.keySet().removeIf(key -> !selected.contains(key));
        ResidentGround ground = ground(mc.level);
        List<ResidentWalker> renderable = new ArrayList<>();
        for (ResidentScene.Slot slot : nearest) {
            ResidentWalker walker = active.computeIfAbsent(slot.identity(), ignored -> new ResidentWalker(slot));
            walker.tick(ground);
            if (walker.visible()) renderable.add(walker);
        }
        visible = List.copyOf(renderable);
    }

    static ResidentGround ground(ClientLevel level) {
        return new ResidentGround(new ResidentGround.World() {
            @Override public BlockGetter blocks() { return level; }
            @Override public boolean loaded(int x, int z) { return level.hasChunk(x >> 4, z >> 4); }
            @Override public int minY() { return level.getMinBuildHeight(); }
            @Override public int maxY() { return level.getMaxBuildHeight(); }
        });
    }

    public static int preview(int count) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !mc.player.isCreative()) return 0;
        if (lastLevel != mc.level) { clearWorld(); lastLevel = mc.level; }
        List<ResidentScene.Slot> slots = new ArrayList<>();
        var p = mc.player.position();
        int total = Math.max(1, Math.min(ResidentScene.ACTIVE_LIMIT, count));
        int columns = Math.min(8, total);
        double yaw = Math.toRadians(mc.player.getYRot());
        double forwardX = -Math.sin(yaw), forwardZ = Math.cos(yaw);
        double rightX = Math.cos(yaw), rightZ = Math.sin(yaw);
        for (int i = 0; i < total; i++) {
            double side = (i % columns - (columns - 1) / 2.0) * 2.5;
            double forward = 5 + (i / columns) * 3.5;
            double x = p.x + rightX * side + forwardX * forward;
            double z = p.z + rightZ * side + forwardZ * forward;
            List<ResidentRoute.Point> points = new ArrayList<>();
            points.add(new ResidentRoute.Point(x, p.y, z));
            if ((i & 1) != 0) points.add(new ResidentRoute.Point(x + forwardX * 2, p.y, z + forwardZ * 2));
            slots.add(new ResidentScene.Slot("gensokyou:preview/resident_" + i,
                    new ResidentRoute(points), ResidentWalker.wrapDegrees(mc.player.getYRot() + 180), false));
        }
        preview = List.copyOf(slots);
        active.clear(); visible = List.of();
        return total;
    }

    public static void clearPreview() {
        preview = List.of();
        active.clear(); visible = List.of(); drawn = 0;
    }

    private static void clearWorld() {
        clearPreview();
        lastLevel = null;
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { clearWorld(); }

    static List<ResidentWalker> visible() {
        // A render may occur between the level replacement and the next client tick.
        return Minecraft.getInstance().level == lastLevel ? visible : List.of();
    }
    static void drawn(int value) { drawn = value; }
    public static int activeCount() { return active.size(); }
    public static int drawnCount() { return drawn; }
    public static int previewCount() { return preview.size(); }
}
