package com.bitsson.gensokyou.worldgen.human_village;

import com.bitsson.gensokyou.worldgen.GensokyoLayout;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Rotation;

import java.util.*;

import static com.bitsson.gensokyou.worldgen.human_village.BuildingInstance.BuildingCategory.*;
import static com.bitsson.gensokyou.worldgen.human_village.HumanVillagePlan.pack;

/** Pure plan computation. No world access, chunk requests, block registry, or mutable global state. */
public final class HumanVillagePlanner {
    private static final int[][] HOUSE_SIZES = {{9,12,10},{11,12,10},{9,14,11},{11,14,11},{11,16,12},
            {13,16,12},{9,15,11},{11,13,12},{13,15,13},{11,13,13}};
    private HumanVillagePlanner() {}
    public static HumanVillagePlan plan(long worldSeed) { return plan(worldSeed, new GensokyoLayout(worldSeed)); }
    public static HumanVillagePlan plan(long worldSeed, GensokyoLayout layout) {
        var grid = new OccupancyGrid();
        for (int x = OccupancyGrid.MIN_X; x <= OccupancyGrid.MAX_X; x++) {
            for (int z = OccupancyGrid.MIN_Z; z <= OccupancyGrid.MAX_Z; z++) {
                if (VillageRoadRouter.streamDistance(x, z) <= 5) grid.set(x, z, OccupancyGrid.STREAM);
            }
        }
        VillageRoadRouter.reserve(grid, layout);
        var buildings = new ArrayList<BuildingInstance>();
        addFixed(grid, buildings, landmark("hv_dragon_plaza", DECOR_LANDMARK, -214,71,86,29,16,29,Rotation.NONE,0,0,0,false));
        addFixed(grid, buildings, landmark("hv_hieda_residence", SPECIAL_LANDMARK, -238,75,-44,76,28,64,Rotation.NONE,-200,75,20,true));
        addFixed(grid, buildings, landmark("hv_suzunaan", SPECIAL_LANDMARK, -253,72,114,38,26,34,Rotation.NONE,-215,72,131,true));
        addFixed(grid, buildings, landmark("hv_geidontei", SPECIAL_LANDMARK, -148,72,164,42,29,36,Rotation.CLOCKWISE_90,-130,72,163,true));
        addFixed(grid, buildings, landmark("hv_kourindou", SPECIAL_LANDMARK, -364,72,60,44,25,36,Rotation.NONE,-345,72,97,true));
        addFixed(grid, buildings, landmark("hv_gate_east", DECOR_LANDMARK,-78,72,91,14,12,19,Rotation.NONE,0,0,0,false));
        addFixed(grid, buildings, landmark("hv_gate_west", DECOR_LANDMARK,-316,72,93,10,11,15,Rotation.NONE,0,0,0,false));
        addFixed(grid, buildings, landmark("hv_gate_north", DECOR_LANDMARK,-207,72,-74,15,11,10,Rotation.NONE,0,0,0,false));
        addFixed(grid, buildings, landmark("hv_watermill", DECOR_LANDMARK,-182,70,156,12,13,12,Rotation.NONE,-176,72,155,true));

        var random = new SplittableRandom(worldSeed ^ 0x4856_2026_CAFEL);
        int[] northX = {-302,-284,-239,-180,-162,-126,-108,-90};
        int[] northVariant = {2,3,2,4,3,2,3,2};
        for (int i = 0; i < northX.length; i++) {
            addFixed(grid, buildings, house(northVariant[i], northX[i],72,80,Rotation.NONE,random.nextInt(8),true,1,2));
        }
        int[] southX = {-302,-280,-180,-126};
        for (int i = 0; i < southX.length; i++) {
            addFixed(grid, buildings, house(i % 2 == 0 ? 3 : 2,southX[i],72,110,Rotation.CLOCKWISE_180,random.nextInt(8),true,1,2));
        }
        String[] stalls = {"dango","fish","umbrella","books"};
        int[] stallNorthX = {-239,-180,-126,-108};
        int[] stallSouthX = {-280,-262,-180,-126};
        for (int i = 0; i < 4; i++) {
            int nx = stallNorthX[i], sx = stallSouthX[i];
            addFixed(grid, buildings, landmark("hv_stall_" + stalls[i],DECOR_LANDMARK,nx,72,93,5,5,4,Rotation.NONE,nx+2,72,97,true));
            addFixed(grid, buildings, landmark("hv_stall_" + stalls[(i+2)%4],DECOR_LANDMARK,sx,72,104,5,5,4,Rotation.CLOCKWISE_180,sx+2,72,103,true));
        }

        // Some fixed storefronts lie in the middle ring. Fill each ring to its own total.
        fillRing(grid, buildings, layout, random, 30,110,26,2);
        fillRing(grid, buildings, layout, random, 110,160,24,18);
        fillRing(grid, buildings, layout, random, 160,198,12,32);
        addDecor(grid, buildings, layout, random, "hv_well",5,6,5,3);
        addDecor(grid, buildings, layout, random, "hv_jizo",3,5,3,3);
        addDecor(grid, buildings, layout, random, "hv_rest_tree",7,9,7,2);
        var roads = VillageRoadRouter.route(grid, buildings, layout);
        Set<Long> protectedColumns = new HashSet<>();
        for (var b : buildings) for (int x = b.minX(); x <= b.maxX(); x++) for (int z = b.minZ(); z <= b.maxZ(); z++) protectedColumns.add(pack(x,z));
        for (var cell : roads) protectedColumns.add(cell.key());
        return new HumanVillagePlan(worldSeed, buildings, roads, protectedColumns);
    }

    private static void addFixed(OccupancyGrid grid, List<BuildingInstance> buildings, BuildingInstance b) {
        for (var other : buildings) {
            if (b.minX() <= other.maxX() && b.maxX() >= other.minX() && b.minZ() <= other.maxZ() && b.maxZ() >= other.minZ()) {
                throw new IllegalStateException("Fixed village templates overlap: " + b.templateId() + " and " + other.templateId());
            }
        }
        buildings.add(b); grid.occupy(b);
    }
    private static BuildingInstance landmark(String id, BuildingInstance.BuildingCategory category,int x,int y,int z,int sx,int sy,int sz,
                                             Rotation rotation,int doorX,int doorY,int doorZ,boolean door) {
        return new BuildingInstance(id(id),category,x,y,z,sx,sy,sz,rotation,-1,doorX,doorY,doorZ,door,true,1,8);
    }
    private static BuildingInstance house(int variant,int x,int y,int z,Rotation rotation,int theme,boolean fixed,int clearance,int depth) {
        int[] s = HOUSE_SIZES[variant];
        int lx = s[0]/2, lz = s[2];
        int dx = switch (rotation) { case NONE -> lx; case CLOCKWISE_90 -> -1; case CLOCKWISE_180 -> s[0]-1-lx; case COUNTERCLOCKWISE_90 -> lz; };
        int dz = switch (rotation) { case NONE -> lz; case CLOCKWISE_90 -> lx; case CLOCKWISE_180 -> -1; case COUNTERCLOCKWISE_90 -> s[0]-1-lx; };
        return new BuildingInstance(id(String.format(Locale.ROOT,"hv_machiya_v%02d",variant+1)),RESIDENTIAL,x,y,z,s[0],s[1],s[2],rotation,theme,
                x+dx,y,z+dz,true,fixed,clearance,depth);
    }
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("gensokyou",path); }

    private static void fillRing(OccupancyGrid grid,List<BuildingInstance> buildings,GensokyoLayout layout,SplittableRandom random,
                                 int minRadius,int maxRadius,int target,int spacing) {
        int lowerBound=minRadius==30?0:minRadius;
        int count = (int) buildings.stream().filter(b -> b.category()==RESIDENTIAL && b.radius()>lowerBound && b.radius()<=maxRadius).count();
        var candidates = new ArrayList<Candidate>();
        for (int x=-398;x<=0;x+=spacing) for (int z=-98;z<=298;z+=spacing) {
            int jitter = spacing==2 ? 0 : spacing==18 ? 4 : 7;
            int cx=x+random.nextInt(-jitter,jitter+1),cz=z+random.nextInt(-jitter,jitter+1);
            double radius=Math.hypot(cx+200,cz-100);
            if (radius<=minRadius || radius>maxRadius) continue;
            double roadDistance=Math.min(Math.min(Math.abs(cx+200),Math.abs(cz-100)),Math.min(Math.abs(cx+256),Math.abs(cz-48)));
            candidates.add(new Candidate(cx,cz,(spacing==2 ? roadDistance : 0)+random.nextDouble()*4));
        }
        candidates.sort(Comparator.comparingDouble(Candidate::priority));
        // Extra seeded samples fill awkward lots without changing the fixed commercial stage.
        for (int i=0;i<5000;i++) {
            double angle=random.nextDouble()*Math.PI*2;
            double radius=random.nextDouble(minRadius+1,maxRadius);
            candidates.add(new Candidate(-200+(int)Math.round(Math.cos(angle)*radius),100+(int)Math.round(Math.sin(angle)*radius),0));
        }
        int[] variants = maxRadius<=110 ? new int[]{2,3,4,5,6,8} : maxRadius<=160 ? new int[]{0,1,2,3,6,7,8} : new int[]{0,1,9};
        for (var c : candidates) {
            if (count>=target) break;
            int variant=variants[random.nextInt(variants.length)];
            int[] size=HOUSE_SIZES[variant];
            int dx=c.x+200,dz=c.z-100;
            Rotation rotation=Math.abs(dx)>Math.abs(dz) ? (dx<0 ? Rotation.COUNTERCLOCKWISE_90 : Rotation.CLOCKWISE_90)
                    : (dz<0 ? Rotation.NONE : Rotation.CLOCKWISE_180);
            boolean quarter=rotation==Rotation.CLOCKWISE_90 || rotation==Rotation.COUNTERCLOCKWISE_90;
            int width=quarter ? size[2] : size[0],depth=quarter ? size[0] : size[2];
            int x=c.x-width/2,z=c.z-depth/2;
            int clearance=maxRadius<=110 ? 1 : maxRadius<=160 ? random.nextInt(3,6) : random.nextInt(12,19);
            if (!grid.available(x,z,width,depth,clearance) || !isSafeFromBiomeEdge(layout,c.x,c.z)) continue;
            double farthest=0;
            for (int px : new int[]{x,x+width-1}) for (int pz : new int[]{z,z+depth-1}) farthest=Math.max(farthest,Math.hypot(px+200,pz-100));
            if (farthest>200) continue;
            double[] heights=heights(layout,x,z,width,depth);
            if (heights[1]-heights[0]>2.5) continue;
            int y=(int)Math.round(heights[2]);
            int theme=maxRadius>160 ? new int[]{2,3,7}[random.nextInt(3)] : random.nextInt(8);
            var b=house(variant,x,y,z,rotation,theme,false,clearance,Math.max(1,y-(int)Math.floor(heights[0])));
            if (b.radius()<=minRadius || b.radius()>maxRadius || grid.get(b.doorWorldX(),b.doorWorldZ())==OccupancyGrid.BUILDING) continue;
            buildings.add(b); grid.occupy(b); count++;
        }
        if (count<target) throw new IllegalStateException("Unable to fill village ring " + minRadius + ".." + maxRadius + ": " + count + "/" + target);
    }
    private static double[] heights(GensokyoLayout layout,int x,int z,int width,int depth) {
        double min=Double.POSITIVE_INFINITY,max=Double.NEGATIVE_INFINITY,total=0;
        for (int px:new int[]{x,x+width-1}) for (int pz:new int[]{z,z+depth-1}) {
            double h=layout.surfaceHeight(px,pz,0); min=Math.min(min,h); max=Math.max(max,h); total+=h;
        }
        return new double[]{min,max,total/4};
    }
    public static boolean isSafeFromBiomeEdge(GensokyoLayout layout,int cx,int cz) {
        if (Math.hypot(cx+200,cz-100)>200 || layout.regionAt(cx,cz)!=GensokyoLayout.Region.HUMAN_VILLAGE) return false;
        int[][] directions={{1,0},{-1,0},{0,1},{0,-1},{1,1},{1,-1},{-1,1},{-1,-1}};
        for (int[] d:directions) {
            double distance=d[0]!=0 && d[1]!=0 ? 220/Math.sqrt(2) : 220;
            if (layout.regionAt(cx+(int)Math.round(d[0]*distance),cz+(int)Math.round(d[1]*distance))!=GensokyoLayout.Region.HUMAN_VILLAGE) return false;
        }
        return true;
    }
    /** Debug statistic only: 32 rays, eight-block steps. Explicitly labeled sampled, not an exact distance field. */
    public static int sampledBiomeEdgeDistance(GensokyoLayout layout,List<BuildingInstance> buildings) {
        int nearest=4096;
        for(var b:buildings) {
            int cx=(b.minX()+b.maxX())/2,cz=(b.minZ()+b.maxZ())/2;
            if(layout.regionAt(cx,cz)!=GensokyoLayout.Region.HUMAN_VILLAGE) return 0;
            for(int ray=0;ray<32;ray++) {
                double angle=ray*Math.PI/16,dx=Math.cos(angle),dz=Math.sin(angle);
                for(int distance=8;distance<nearest;distance+=8) {
                    if(layout.regionAt(cx+(int)Math.round(dx*distance),cz+(int)Math.round(dz*distance))!=GensokyoLayout.Region.HUMAN_VILLAGE) {
                        nearest=distance;break;
                    }
                }
            }
        }
        return nearest;
    }
    private static void addDecor(OccupancyGrid grid,List<BuildingInstance> buildings,GensokyoLayout layout,SplittableRandom random,
                                  String id,int width,int height,int depth,int count) {
        int added=0;
        for (int attempt=0;attempt<3000 && added<count;attempt++) {
            int x=random.nextInt(-325,-80),z=random.nextInt(35,220);
            if (!grid.available(x,z,width,depth,1) || !isSafeFromBiomeEdge(layout,x+width/2,z+depth/2)) continue;
            double[] h=heights(layout,x,z,width,depth);
            if (h[1]-h[0]>1.5) continue;
            var b=landmark(id,DECOR_LANDMARK,x,(int)Math.round(h[2]),z,width,height,depth,Rotation.NONE,0,0,0,false);
            b=new BuildingInstance(b.templateId(),b.category(),b.minX(),b.minY(),b.minZ(),b.sizeX(),b.sizeY(),b.sizeZ(),b.rotation(),-1,0,0,0,false,false,1,3);
            buildings.add(b);grid.occupy(b);added++;
        }
        if (added<count) throw new IllegalStateException("Not enough street-corner lots for " + id);
    }
    private record Candidate(int x,int z,double priority) {}
}
