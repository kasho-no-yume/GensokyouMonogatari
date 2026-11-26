package com.bitsson.gensokyou.worldgen.human_village;

import java.util.BitSet;
import java.util.HashSet;
import java.util.Set;
import static com.bitsson.gensokyou.worldgen.human_village.HumanVillagePlan.*;

/** On-demand planar clearance check, independent of paving. Not a runtime terrain/collision proof. */
public final class VillageWalkability {
    private VillageWalkability() {}
    public static Set<Long> reachableDoors(HumanVillagePlan plan) {
        if(plan.buildings().isEmpty()) return Set.of();
        int minX=plan.buildings().stream().mapToInt(b->b.minX()).min().orElse(-200)-24;
        int maxX=plan.buildings().stream().mapToInt(b->b.maxX()).max().orElse(-200)+24;
        int minZ=plan.buildings().stream().mapToInt(b->b.minZ()).min().orElse(100)-24;
        int maxZ=plan.buildings().stream().mapToInt(b->b.maxZ()).max().orElse(100)+24;
        int width=maxX-minX+1,depth=maxZ-minZ+1,size=width*depth;
        var blocked=new BitSet(size);
        for(var b:plan.buildings()) {
            if(b.publicPassage()) continue;
            for(int z=b.minZ();z<=b.maxZ();z++) blocked.set((z-minZ)*width+b.minX()-minX,(z-minZ)*width+b.maxX()-minX+1);
        }
        for(int x=-345;x<=5;x++) for(int z=125;z<=190;z++) {
            if(x>=minX && x<=maxX && z>=minZ && z<=maxZ && HumanVillageStreamPlacer.waterColumn(x,z)) blocked.set((z-minZ)*width+x-minX);
        }
        for(var c:plan.roadCells()) if(c.type()==RoadType.L4_BRIDGE) blocked.clear((c.z()-minZ)*width+c.x()-minX);
        int start=(100-minZ)*width-185-minX;
        if(start<0 || start>=size || blocked.get(start)) return Set.of();
        var visited=new BitSet(size); int[] queue=new int[size];int head=0,tail=0;
        visited.set(start);queue[tail++]=start;
        while(head<tail) {
            int p=queue[head++],x=p%width,z=p/width;
            if(x>0) tail=offer(p-1,blocked,visited,queue,tail);
            if(x+1<width) tail=offer(p+1,blocked,visited,queue,tail);
            if(z>0) tail=offer(p-width,blocked,visited,queue,tail);
            if(z+1<depth) tail=offer(p+width,blocked,visited,queue,tail);
        }
        var result=new HashSet<Long>();
        for(var b:plan.buildings()) if(b.hasDoorAnchor() && visited.get((b.doorWorldZ()-minZ)*width+b.doorWorldX()-minX))
            result.add(pack(b.doorWorldX(),b.doorWorldZ()));
        return Set.copyOf(result);
    }
    private static int offer(int p,BitSet blocked,BitSet visited,int[] queue,int tail) {
        if(!blocked.get(p) && !visited.get(p)) {visited.set(p);queue[tail++]=p;}
        return tail;
    }
}
