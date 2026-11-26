package com.bitsson.gensokyou.client.resident;

import com.bitsson.gensokyou.Gensokyou;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.ArrayList;
import java.util.List;

/** Bounded local presentation state. No Entity, server hooks, persistence or packets. */
@EventBusSubscriber(modid = Gensokyou.MODID, value = Dist.CLIENT)
public final class ClientResidents {
    private static List<ResidentScene> scenes = List.of();
    private static List<ResidentScene.Slot> preview = List.of();
    private static final ResidentPopulation population = new ResidentPopulation();
    private static final ResidentStreetCrowd streetCrowd = new ResidentStreetCrowd();
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
        var position=mc.player.position();
        var viewer=new ResidentRoute.Point(position.x,position.y,position.z);
        ResidentGround ground = ground(mc.level);
        List<ResidentScene.Slot> candidates = new ArrayList<>();
        String dimension = mc.level.dimension().location().toString();
        if (preview.isEmpty()) {
            for (ResidentScene scene : scenes) {
                if (scene.dimension().equals(dimension)) scene.residents().stream().map(VillageResidentAnchors::coreSlot)
                        .filter(s->s.route().distanceSquared(viewer)<=ResidentScene.VIEW_DISTANCE*ResidentScene.VIEW_DISTANCE).forEach(candidates::add);
            }
            if(dimension.equals("gensokyou:gensokyo")) {
                streetCrowd.update(viewer,()->streetInterests(viewer,candidates),new ResidentStreetCrowd.Streets() {
                    @Override public ResidentRoute.Point groundAt(double x,double z) {
                        int bx=(int)Math.floor(x),bz=(int)Math.floor(z);
                        // Never ask the heightmap for a chunk not already in the client cache.
                        if(!mc.level.hasChunk(bx>>4,bz>>4) || !VillageResidentAnchors.publicGround(bx,bz,Double.NEGATIVE_INFINITY)) return null;
                        // MOTION_BLOCKING is sent to clients; NO_LEAVES is a server-only heightmap.
                        int y=mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING,bx,bz);
                        if(Math.abs(y-viewer.y())>ResidentScene.VIEW_DISTANCE || !VillageResidentAnchors.publicGround(bx,bz,y)) return null;
                        return ground.resolve(new ResidentRoute.Point(x,y,z),1.025f,true);
                    }
                    @Override public boolean clear(ResidentRoute.Point a,ResidentRoute.Point b) {return ground.clear(a,b,1.025f);}
                });
                candidates.addAll(VillageResidentAnchors.nearIndoors(viewer));
                candidates.addAll(streetCrowd.near(viewer,ResidentScene.MAX_RESIDENTS-candidates.size()));
            }
        }
        // Explicit local preview replaces the ordinary scene until cleared.
        candidates.addAll(preview);
        if(preview.isEmpty()) population.tickAoi(candidates,viewer,ground);
        else population.tick(candidates,viewer,ground); // An explicit preview intentionally appears in front.
    }

    private static List<ResidentStreetCrowd.Seed> streetInterests(ResidentRoute.Point viewer,List<ResidentScene.Slot> authored) {
        var seeds=new ArrayList<>(VillageResidentAnchors.interests(viewer));
        for(var slot:authored) {
            var points=slot.route().points();
            for(int i=0;i<points.size();i++) {
                var a=points.get(i);var b=points.get(Math.min(i+1,points.size()-1));
                int samples=Math.max(1,(int)Math.ceil(a.distance(b)/4));
                int axis=Math.abs(b.x()-a.x())>=Math.abs(b.z()-a.z())?0:1;
                for(int step=0;step<samples;step++) {
                    var p=a.lerp(b,(double)step/samples);
                    seeds.add(ResidentStreetCrowd.Seed.at(p.x(),p.z(),axis));
                }
            }
        }
        return seeds;
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
        population.clear(); streetCrowd.clear();
        return total;
    }

    public static void clearPreview() {
        preview = List.of();
        population.clear(); streetCrowd.clear(); drawn = 0;
    }

    public static void villageLayoutChanged() {
        population.clear(); streetCrowd.clear(); drawn=0;
    }

    private static void clearWorld() {
        clearPreview();
        lastLevel = null;
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { clearWorld(); }

    static List<ResidentWalker> visible() {
        // A render may occur between the level replacement and the next client tick.
        return Minecraft.getInstance().level == lastLevel ? population.visible() : List.of();
    }
    static void drawn(int value) { drawn = value; }
    public static int activeCount() { return population.size(); }
    public static int drawnCount() { return drawn; }
    public static int previewCount() { return preview.size(); }
    static float opacity(ResidentWalker walker, float partial) { return population.opacity(walker, partial); }
}
