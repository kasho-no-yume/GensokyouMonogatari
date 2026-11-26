package com.bitsson.gensokyou.client.resident;

import com.bitsson.gensokyou.network.HumanVillageLayoutSyncPayload;
import com.bitsson.gensokyou.network.HumanVillageLayoutSyncPayload.InstanceEntry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Rotation;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.LinkedHashMap;

/** Spatial metadata and public-space interests. No distant resident slots or simulation are created. */
public final class VillageResidentAnchors {
    private static final Map<Long,List<InstanceEntry>> buckets = new HashMap<>();
    private static final Map<ResidentScene.Slot,ResidentScene.Slot> loweredCore = new HashMap<>();
    private static final List<ResidentScene.Slot> indoorSlots = new ArrayList<>();
    private static int version = 1;
    private static boolean ready;
    private VillageResidentAnchors() {}
    private static long key(int x,int z) { return ((long)x<<32)|(z&0xffffffffL); }

    public static void install(HumanVillageLayoutSyncPayload payload) {
        clear(); version=payload.layoutVersion();ready=true;
        for(var e:payload.instances()) {
            indoorSlots.addAll(indoors(e));
            int sx=e.rotationOrdinal()%2==0?e.sizeX():e.sizeZ(),sz=e.rotationOrdinal()%2==0?e.sizeZ():e.sizeX();
            for(int x=e.minX()>>4;x<=(e.minX()+sx-1)>>4;x++) for(int z=e.minZ()>>4;z<=(e.minZ()+sz-1)>>4;z++)
                buckets.computeIfAbsent(key(x,z),k->new ArrayList<>()).add(e);
        }
    }

    public static ResidentScene.Slot doorstep(InstanceEntry e) {
        int lx=e.sizeX()/2,lz=e.sizeZ();
        int dx=switch(e.rotationOrdinal()) {case 0->lx;case 1->-1;case 2->e.sizeX()-1-lx;default->lz;};
        int dz=switch(e.rotationOrdinal()) {case 0->lz;case 1->lx;case 2->-1;default->e.sizeX()-1-lx;};
        var p=new ResidentRoute.Point(e.minX()+dx+.5,e.minY()+1,e.minZ()+dz+.5);
        return new ResidentScene.Slot("gensokyou:village_house/"+e.minX()+"/"+e.minZ(),
                new ResidentRoute(List.of(p)),e.rotationOrdinal()*90f,true);
    }

    public static ResidentScene.Slot coreSlot(ResidentScene.Slot slot) {
        if(version<2 || !slot.identity().startsWith("gensokyou:human_village/")) return slot;
        return loweredCore.computeIfAbsent(slot,s->new ResidentScene.Slot(s.identity(),new ResidentRoute(s.route().points().stream()
                .map(p->new ResidentRoute.Point(p.x(),p.y()-1,p.z())).toList()),s.facing(),s.protectedGround()));
    }

    public static List<ResidentScene.Slot> near(double x,double y,double z,int budget) {
        var viewer=new ResidentRoute.Point(x,y,z);
        return nearby(viewer).stream().filter(e->e.templateId().getPath().startsWith("hv_machiya_")).map(VillageResidentAnchors::doorstep)
                .sorted(java.util.Comparator.comparingDouble((ResidentScene.Slot s)->s.route().distanceSquared(viewer)).thenComparing(ResidentScene.Slot::identity))
                .limit(Math.max(0,budget)).toList();
    }

    /** A few authored shoppers, separate from heightmap-based public street interests. */
    public static List<ResidentScene.Slot> indoors(InstanceEntry e) {
        double[][][] routes = switch(e.templateId().toString()) {
            case "gensokyou:hv_kourindou" -> new double[][][] {
                    {{15.5,13.5},{15.5,20.5}}, {{23.5,14.5},{23.5,21.5}}, {{16.5,11.5},{21.5,11.5}}};
            case "gensokyou:hv_geidontei" -> new double[][][] {
                    {{18.5,12.5},{18.5,16.5}}, {{18.5,20.5},{18.5,24.5}},
                    {{14.5,12.5},{14.5,17.5}}, {{14.5,21.5},{14.5,24.5}}};
            case "gensokyou:hv_suzunaan" -> new double[][][] {
                    {{6.5,9.5},{6.5,11.5}}, {{10.5,14.5},{15.5,14.5}}, {{10.5,23.5},{15.5,23.5}}};
            default -> new double[0][][];
        };
        if(routes.length==0) return List.of();
        Rotation rotation=Rotation.values()[e.rotationOrdinal()];
        int sx=e.rotationOrdinal()%2==0?e.sizeX():e.sizeZ(),sz=e.rotationOrdinal()%2==0?e.sizeZ():e.sizeX();
        BlockPos pivot=switch(rotation) {
            case NONE -> new BlockPos(e.minX(),e.minY(),e.minZ());
            case CLOCKWISE_90 -> new BlockPos(e.minX()+sx-1,e.minY(),e.minZ());
            case CLOCKWISE_180 -> new BlockPos(e.minX()+sx-1,e.minY(),e.minZ()+sz-1);
            case COUNTERCLOCKWISE_90 -> new BlockPos(e.minX(),e.minY(),e.minZ()+sz-1);
        };
        var result=new ArrayList<ResidentScene.Slot>();
        for(int i=0;i<routes.length;i++) {
            var points=new ArrayList<ResidentRoute.Point>();
            for(var p:routes[i]) {
                // Rotate block centers around the same integer pivot as StructureTemplate.
                var local=new BlockPos((int)Math.floor(p[0]),1,(int)Math.floor(p[1])).rotate(rotation).offset(pivot);
                points.add(new ResidentRoute.Point(local.getX()+.5,local.getY(),local.getZ()+.5));
            }
            result.add(new ResidentScene.Slot("gensokyou:village_indoor/"+e.templateId().getPath()+"/"+e.minX()+"/"+e.minZ()+"/"+i,
                    new ResidentRoute(points),e.rotationOrdinal()*90f,true));
        }
        return List.copyOf(result);
    }

    public static List<ResidentScene.Slot> nearIndoors(ResidentRoute.Point viewer) {
        return ResidentScene.candidates(indoorSlots,viewer);
    }

    public static boolean isIndoor(ResidentScene.Slot slot) {
        return slot.identity().startsWith("gensokyou:village_indoor/");
    }

    public static List<ResidentStreetCrowd.Seed> interests(ResidentRoute.Point viewer) {
        var result=new LinkedHashMap<String,ResidentStreetCrowd.Seed>();
        for(var e:nearby(viewer)) {
            boolean quarter=e.rotationOrdinal()%2!=0;
            int sx=quarter?e.sizeZ():e.sizeX(),sz=quarter?e.sizeX():e.sizeZ();
            if(e.templateId().getPath().startsWith("hv_machiya_")) {
                var door=doorstep(e).route().points().getFirst();
                int nx=e.rotationOrdinal()==1?-1:e.rotationOrdinal()==3?1:0;
                int nz=e.rotationOrdinal()==0?1:e.rotationOrdinal()==2?-1:0;
                for(double forward:new double[]{1.5,3.5,5.5}) for(int along:new int[]{-6,-2,2,6})
                    addInterest(result,door.x()+nx*forward+(quarter?0:along),door.z()+nz*forward+(quarter?along:0),quarter?1:0);
            } else if(e.templateId().getPath().equals("hv_dragon_plaza")) {
                for(int dx=2;dx<sx-2;dx+=3) for(int dz=2;dz<sz-2;dz+=3)
                    addInterest(result,e.minX()+dx+.5,e.minZ()+dz+.5,(dx+dz)&1);
            } else {
                for(int along=2;along<sx;along+=6) for(int offset:new int[]{-3,sz+2})
                    addInterest(result,e.minX()+along+.5,e.minZ()+offset+.5,0);
                for(int along=2;along<sz;along+=6) for(int offset:new int[]{-3,sx+2})
                    addInterest(result,e.minX()+offset+.5,e.minZ()+along+.5,1);
            }
        }
        return result.values().stream().filter(s->s.distanceSquared(viewer)<=ResidentStreetCrowd.SPAWN_RADIUS*ResidentStreetCrowd.SPAWN_RADIUS)
                .sorted(java.util.Comparator.comparingDouble((ResidentStreetCrowd.Seed s)->s.distanceSquared(viewer)).thenComparing(ResidentStreetCrowd.Seed::identity))
                .limit(ResidentScene.MAX_RESIDENTS).toList();
    }

    private static void addInterest(Map<String,ResidentStreetCrowd.Seed> result,double x,double z,int axis) {
        var seed=ResidentStreetCrowd.Seed.at(x,z,axis);result.putIfAbsent(seed.identity(),seed);
    }
    private static java.util.Set<InstanceEntry> nearby(ResidentRoute.Point viewer) {
        var result=new HashSet<InstanceEntry>();
        int cx=(int)Math.floor(viewer.x())>>4,cz=(int)Math.floor(viewer.z())>>4;
        for(int dx=-7;dx<=7;dx++) for(int dz=-7;dz<=7;dz++) result.addAll(buckets.getOrDefault(key(cx+dx,cz+dz),List.of()));
        return result;
    }

    /** Heightmap results must not turn a template roof into a street spawn. */
    public static boolean publicGround(int x,int z,double feetY) {
        if(!ready) return false;
        for(var e:buckets.getOrDefault(key(x>>4,z>>4),List.of())) {
            int sx=e.rotationOrdinal()%2==0?e.sizeX():e.sizeZ(),sz=e.rotationOrdinal()%2==0?e.sizeZ():e.sizeX();
            if(x<e.minX() || x>=e.minX()+sx || z<e.minZ() || z>=e.minZ()+sz) continue;
            String id=e.templateId().getPath();
            if((!id.equals("hv_dragon_plaza") && !id.startsWith("hv_gate_")) || feetY>e.minY()+2.1) return false;
        }
        return true;
    }
    public static void clear() { buckets.clear(); loweredCore.clear(); indoorSlots.clear(); version=1;ready=false; }
}
