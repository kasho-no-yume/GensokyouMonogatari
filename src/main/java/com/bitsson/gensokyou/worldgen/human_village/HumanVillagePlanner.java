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
        for (int x = -350; x <= 10; x++) {
            for (int z = 125; z <= 190; z++) {
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

        var field = new VillageSettlementField(layout);
        fillDistricts(grid, buildings, layout, field, worldSeed);
        addDecor(grid, buildings, layout, random, "hv_well",5,6,5,3);
        addDecor(grid, buildings, layout, random, "hv_jizo",3,5,3,3);
        addDecor(grid, buildings, layout, random, "hv_rest_tree",7,9,7,2);
        var roads = VillageRoadRouter.route(grid, buildings, layout);
        // Protection is resolved through chunk-indexed building bounds, not millions of boxed columns.
        return new HumanVillagePlan(HumanVillagePlan.CURRENT_VERSION, worldSeed, buildings, roads, Set.of());
    }

    private static void addFixed(OccupancyGrid grid, List<BuildingInstance> buildings, BuildingInstance b) {
        for (var other : buildings) {
            if (b.minX() <= other.maxX() && b.maxX() >= other.minX() && b.minZ() <= other.maxZ() && b.maxZ() >= other.minZ()) {
                throw new IllegalStateException("Fixed village templates overlap: " + b.templateId() + " and " + other.templateId());
            }
        }
        b = new BuildingInstance(b.templateId(), b.category(), b.minX(), b.minY()-1, b.minZ(),
                b.sizeX(), b.sizeY(), b.sizeZ(), b.rotation(), b.paletteThemeId(),
                b.doorWorldX(), b.doorWorldY()-1, b.doorWorldZ(), b.hasDoorAnchor(), b.fixed(), b.clearance(), b.foundationDepth());
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

    private static void fillDistricts(OccupancyGrid grid, List<BuildingInstance> buildings, GensokyoLayout layout,
                                      VillageSettlementField field, long seed) {
        var districts = new ArrayList<Candidate>();
        // Staggered, smoothly displaced groups. The gap between facing houses is the lane.
        for (int gz = -90; gz <= 90; gz++) for (int gx = -90; gx <= 90; gx++) {
            int x = -200 + gx * 48 + Math.floorMod(gz, 2) * 24 + (int)Math.round(9*Math.sin(gz*.47));
            int z = 100 + gz * 44 + (int)Math.round(8*Math.sin(gx*.39));
            if (field.edgeDistance(x,z) >= VillageSettlementField.EDGE_BUFFER)
                districts.add(new Candidate(x,z,field.outward(x,z)));
        }
        districts.sort(Comparator.comparingDouble(Candidate::priority).thenComparingInt(Candidate::x).thenComparingInt(Candidate::z));
        for (var c : districts) {
            var random = new SplittableRandom(seed ^ pack(c.x,c.z) ^ 0x485644495354524CL);
            double t = c.priority;
            if (random.nextDouble() < .04 + .20*t*t) continue;
            boolean quarter = random.nextBoolean();
            int columns = t < .38 ? 3 : t < .72 ? 2 : 1;
            int gap = t < .38 ? 4 : t < .72 ? 6 : 10;
            int cx = c.x + random.nextInt(-3,4), cz = c.z + random.nextInt(-3,4);
            // Reserve the common yard before inserting its facing rows. It opens at both ends.
            for(int along=-columns*7-3;along<=columns*7+3;along++) for(int across=-gap/2;across<gap/2;across++) {
                int x=quarter?cx+across:cx+along,z=quarter?cz+along:cz+across;
                if(grid.get(x,z)==OccupancyGrid.EMPTY) grid.set(x,z,OccupancyGrid.MAIN_ROAD);
            }
            for (int side : new int[]{-1,1}) for (int slot=0;slot<columns;slot++) {
                if (buildings.size() >= HumanVillagePlan.MAX_BUILDINGS) throw new IllegalStateException("Village building budget exceeded");
                int variant = random.nextInt(HOUSE_SIZES.length);
                int[] s = HOUSE_SIZES[variant];
                int along = (slot*14) - (columns-1)*7 + random.nextInt(-1,2);
                int setback = gap/2 + random.nextInt(0,3);
                Rotation rotation = quarter ? (side<0 ? Rotation.COUNTERCLOCKWISE_90 : Rotation.CLOCKWISE_90)
                        : (side<0 ? Rotation.NONE : Rotation.CLOCKWISE_180);
                int width = quarter?s[2]:s[0], depth=quarter?s[0]:s[2];
                int x = quarter ? cx+(side<0 ? -setback-width : setback) : cx+along-width/2;
                int z = quarter ? cz+along-depth/2 : cz+(side<0 ? -setback-depth : setback);
                if (!grid.available(x,z,width,depth,1) || !field.containsLot(x,z,width,depth)) continue;
                double[] h=heights(layout,x,z,width,depth);
                if (h[1]-h[0]>2.5) continue;
                int y=(int)Math.ceil(h[2])-1;
                var b=house(variant,x,y,z,rotation,random.nextInt(8),false,1,Math.max(1,y-(int)Math.floor(h[0])+2));
                if (grid.get(b.doorWorldX(),b.doorWorldZ())==OccupancyGrid.BUILDING) continue;
                buildings.add(b); grid.occupy(b);
                // Reserve the doorstep; later groups cannot seal another household's entrance.
                grid.set(b.doorWorldX(),b.doorWorldZ(),OccupancyGrid.MAIN_ROAD);
            }
        }
    }
    private static double[] heights(GensokyoLayout layout,int x,int z,int width,int depth) {
        double min=Double.POSITIVE_INFINITY,max=Double.NEGATIVE_INFINITY,total=0;
        for (int px:new int[]{x,x+width-1}) for (int pz:new int[]{z,z+depth-1}) {
            double h=layout.surfaceHeight(px,pz,0); min=Math.min(min,h); max=Math.max(max,h); total+=h;
        }
        return new double[]{min,max,total/4};
    }
    public static boolean isSafeFromBiomeEdge(GensokyoLayout layout,int cx,int cz) {
        if (layout.regionAt(cx,cz)!=GensokyoLayout.Region.HUMAN_VILLAGE) return false;
        int[][] directions={{1,0},{-1,0},{0,1},{0,-1},{1,1},{1,-1},{-1,1},{-1,-1}};
        for (int[] d:directions) {
            double distance=d[0]!=0 && d[1]!=0 ? 64/Math.sqrt(2) : 64;
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
            if(buildings.size()>=HumanVillagePlan.MAX_BUILDINGS) return;
            int x=random.nextInt(-325,-80),z=random.nextInt(35,220);
            if (!grid.available(x,z,width,depth,1) || !isSafeFromBiomeEdge(layout,x+width/2,z+depth/2)) continue;
            double[] h=heights(layout,x,z,width,depth);
            if (h[1]-h[0]>1.5) continue;
            var b=landmark(id,DECOR_LANDMARK,x,(int)Math.ceil(h[2])-1,z,width,height,depth,Rotation.NONE,0,0,0,false);
            b=new BuildingInstance(b.templateId(),b.category(),b.minX(),b.minY(),b.minZ(),b.sizeX(),b.sizeY(),b.sizeZ(),b.rotation(),-1,0,0,0,false,false,1,3);
            buildings.add(b);grid.occupy(b);added++;
        }
        // A crowded district may omit optional street furniture rather than failing the entire village.
    }
    private record Candidate(int x,int z,double priority) {}
}
