package com.bitsson.gensokyou.client.resident;

import com.bitsson.gensokyou.network.HumanVillageLayoutSyncPayload;
import com.bitsson.gensokyou.network.HumanVillageLayoutSyncPayload.InstanceEntry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Local neighborhood slots derived from the existing building descriptors; no new NPC network state. */
public final class VillageResidentAnchors {
    private static final Map<Long,List<ResidentScene.Slot>> buckets = new HashMap<>();
    private static final Map<ResidentScene.Slot,ResidentScene.Slot> loweredCore = new HashMap<>();
    private static int version = 1;
    private VillageResidentAnchors() {}
    private static long key(int x,int z) { return ((long)x<<32)|(z&0xffffffffL); }

    public static void install(HumanVillageLayoutSyncPayload payload) {
        clear(); version=payload.layoutVersion();
        if(version<2) return;
        for(var e:payload.instances()) {
            if(!e.templateId().getPath().startsWith("hv_machiya_") || Math.hypot(e.minX()+200,e.minZ()-100)<240) continue;
            var slot=doorstep(e);
            var p=slot.route().points().getFirst();
            buckets.computeIfAbsent(key((int)Math.floor(p.x())>>4,(int)Math.floor(p.z())>>4),k->new ArrayList<>()).add(slot);
        }
    }

    public static ResidentScene.Slot doorstep(InstanceEntry e) {
        int lx=e.sizeX()/2,lz=e.sizeZ();
        int dx=switch(e.rotationOrdinal()) {case 0->lx;case 1->-1;case 2->e.sizeX()-1-lx;default->lz;};
        int dz=switch(e.rotationOrdinal()) {case 0->lz;case 1->lx;case 2->-1;default->e.sizeX()-1-lx;};
        var p=new ResidentRoute.Point(e.minX()+dx+.5,e.minY()+1,e.minZ()+dz+.5);
        // Only a subset walk, along the public frontage. Runtime ground/collision checks remain authoritative.
        boolean walk=Math.floorMod(e.minX()*31+e.minZ(),3)==0;
        var q=new ResidentRoute.Point(p.x()+(e.rotationOrdinal()%2==0?3:0),p.y(),p.z()+(e.rotationOrdinal()%2!=0?3:0));
        return new ResidentScene.Slot("gensokyou:village_house/"+e.minX()+"/"+e.minZ(),
                new ResidentRoute(walk?List.of(p,q):List.of(p)),e.rotationOrdinal()*90f,true);
    }

    public static ResidentScene.Slot coreSlot(ResidentScene.Slot slot) {
        if(version<2 || !slot.identity().startsWith("gensokyou:human_village/")) return slot;
        return loweredCore.computeIfAbsent(slot,s->new ResidentScene.Slot(s.identity(),new ResidentRoute(s.route().points().stream()
                .map(p->new ResidentRoute.Point(p.x(),p.y()-1,p.z())).toList()),s.facing(),s.protectedGround()));
    }

    public static List<ResidentScene.Slot> near(double x,double y,double z,int budget) {
        var result=new ArrayList<ResidentScene.Slot>();
        int cx=(int)Math.floor(x)>>4,cz=(int)Math.floor(z)>>4;
        for(int dx=-7;dx<=7;dx++) for(int dz=-7;dz<=7;dz++)
            result.addAll(buckets.getOrDefault(key(cx+dx,cz+dz),List.of()));
        var viewer=new ResidentRoute.Point(x,y,z);
        return result.stream().sorted(java.util.Comparator.comparingDouble((ResidentScene.Slot s)->s.route().distanceSquared(viewer)).thenComparing(ResidentScene.Slot::identity))
                .limit(Math.max(0,budget)).toList();
    }

    public static void clear() { buckets.clear(); loweredCore.clear(); version=1; }
}
