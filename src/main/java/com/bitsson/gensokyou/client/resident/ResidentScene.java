package com.bitsson.gensokyou.client.resident;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Resource-pack scene contract, independent of Minecraft client classes for validation/tests. */
public record ResidentScene(String dimension, List<Slot> residents) {
    public static final int MAX_SCENE_RESIDENTS = 128;
    public static final int MAX_RESIDENTS = 512;
    public static final int ACTIVE_LIMIT = 96;
    public static final double VIEW_DISTANCE = 96;

    public ResidentScene {
        if (!dimension.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
            throw new IllegalArgumentException("invalid scene dimension");
        }
        residents = List.copyOf(residents);
        if (residents.isEmpty() || residents.size() > MAX_SCENE_RESIDENTS) {
            throw new IllegalArgumentException("scene needs 1.." + MAX_SCENE_RESIDENTS + " residents");
        }
        Set<String> identities = new HashSet<>();
        for (Slot slot : residents) {
            if (!identities.add(slot.identity)) throw new IllegalArgumentException("duplicate resident " + slot.identity);
        }
    }

    public static ResidentScene parse(String resourceId, JsonObject json) {
        String dimension = json.get("dimension").getAsString();
        ResidentRoute.Point origin = point(json.getAsJsonArray("origin"));
        JsonArray entries = json.getAsJsonArray("residents");
        if (entries.size() > MAX_SCENE_RESIDENTS) throw new IllegalArgumentException("too many residents");
        List<Slot> slots = new ArrayList<>();
        for (JsonElement entry : entries) {
            JsonObject object = entry.getAsJsonObject();
            String id = object.get("id").getAsString();
            if (!id.matches("[a-z0-9_./-]{1,80}")) throw new IllegalArgumentException("invalid resident id " + id);
            JsonArray nodes = object.getAsJsonArray("points");
            if (nodes.size() > ResidentRoute.MAX_POINTS) throw new IllegalArgumentException("too many route points for " + id);
            List<ResidentRoute.Point> points = new ArrayList<>();
            for (JsonElement node : nodes) {
                ResidentRoute.Point offset = point(node.getAsJsonArray());
                points.add(new ResidentRoute.Point(origin.x() + offset.x(), origin.y() + offset.y(), origin.z() + offset.z()));
            }
            float facing = object.has("facing") ? object.get("facing").getAsFloat() : 0;
            slots.add(new Slot(resourceId + "/" + id, new ResidentRoute(points), facing, true));
        }
        return new ResidentScene(dimension, slots);
    }

    private static ResidentRoute.Point point(JsonArray array) {
        if (array.size() != 3) throw new IllegalArgumentException("point must contain x, y, z");
        return new ResidentRoute.Point(array.get(0).getAsDouble(), array.get(1).getAsDouble(), array.get(2).getAsDouble());
    }

    public static List<Slot> nearest(List<Slot> slots, ResidentRoute.Point viewer) {
        return candidates(slots,viewer).stream().limit(ACTIVE_LIMIT).toList();
    }

    /** Keep spare candidates so unavailable ground cannot consume the active population budget. */
    public static List<Slot> candidates(List<Slot> slots, ResidentRoute.Point viewer) {
        return slots.stream().filter(s -> s.route.distanceSquared(viewer) <= VIEW_DISTANCE * VIEW_DISTANCE)
                .sorted(Comparator.comparingDouble((Slot s) -> s.route.distanceSquared(viewer)).thenComparing(Slot::identity))
                .limit(MAX_RESIDENTS).toList();
    }

    public record Slot(String identity, ResidentRoute route, float facing, boolean protectedGround) {
        public Slot {
            if (identity.isBlank() || route == null || !Float.isFinite(facing)) {
                throw new IllegalArgumentException("invalid resident slot");
            }
        }
    }
}
