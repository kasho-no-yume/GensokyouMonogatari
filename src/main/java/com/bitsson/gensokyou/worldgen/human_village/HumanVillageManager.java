package com.bitsson.gensokyou.worldgen.human_village;

import com.bitsson.gensokyou.landmark.HakureiShrineLandmark;
import com.bitsson.gensokyou.network.HumanVillageLayoutSyncPayload;
import com.bitsson.gensokyou.registry.LandmarkBlocks;
import com.bitsson.gensokyou.worldgen.GensokyoLayout;
import com.bitsson.gensokyou.worldgen.GensokyoLayoutDensity;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;

import static com.bitsson.gensokyou.worldgen.human_village.HumanVillagePlan.*;
import static com.bitsson.gensokyou.worldgen.human_village.HumanVillageStreamPlacer.set;

/** Server-thread owner. Ordinary loading never requests adjacent chunks, even for the large estate. */
public final class HumanVillageManager {
    private static final Map<ServerLevel,HumanVillageManager> MANAGERS=new IdentityHashMap<>();
    private final ServerLevel level;
    private final HumanVillageSavedData data;
    private final HumanVillageLayoutSyncPayload payload;
    private final Map<Long,List<RoadCell>> roadsByChunk=new HashMap<>();
    private final Map<Long,RoadCell> roads=new HashMap<>();
    private final Map<Long,List<Integer>> buildingsByChunk=new HashMap<>();
    private final Set<Long> queue=new LinkedHashSet<>();
    private boolean populating;

    private HumanVillageManager(ServerLevel level) {
        this.level=level;
        data=level.getDataStorage().computeIfAbsent(HumanVillageSavedData.FACTORY,HumanVillageSavedData.NAME);
        boolean first=data.plan()==null;
        if(first) {
            // Worldgen derives its geography seed from router noise, not directly from level.getSeed().
            GensokyoLayout layout=GensokyoLayoutDensity.layoutOf(level.getChunkSource().randomState().sampler().continentalness());
            data.setPlan(HumanVillagePlanner.plan(level.getSeed(),layout));
        }
        payload=new HumanVillageLayoutSyncPayload(plan().layoutVersion(),plan().buildings().stream().map(HumanVillageLayoutSyncPayload.InstanceEntry::of).toList());
        for(int i=0;i<plan().buildings().size();i++) {
            var b=plan().buildings().get(i);
            for(int cx=b.minX()>>4;cx<=b.maxX()>>4;cx++) for(int cz=b.minZ()>>4;cz<=b.maxZ()>>4;cz++)
                buildingsByChunk.computeIfAbsent(ChunkPos.asLong(cx,cz),key->new ArrayList<>()).add(i);
        }
        for(var road:plan().roadCells()) {
            roads.put(road.key(),road);
            roadsByChunk.computeIfAbsent(ChunkPos.asLong(road.x()>>4,road.z()>>4),key->new ArrayList<>()).add(road);
        }
        if(first) for(var player:level.players()) sync(player);
    }
    public static HumanVillageManager get(ServerLevel level) {
        if(!level.dimension().equals(HakureiShrineLandmark.GENSOKYO_DIMENSION)) throw new IllegalArgumentException("Not Gensokyo");
        return MANAGERS.computeIfAbsent(level,HumanVillageManager::new);
    }
    public static HumanVillageManager existing(ServerLevel level) { return MANAGERS.get(level); }
    public static void clear(ServerLevel level) { MANAGERS.remove(level); }
    public static void clearAll() { MANAGERS.clear(); }
    public HumanVillagePlan plan() { return data.plan(); }
    public HumanVillageSavedData data() { return data; }
    public void sync(ServerPlayer player) { PacketDistributor.sendToPlayer(player,payload); }
    public static boolean intersectsChunk(int cx,int cz) {
        return (cx<<4)<=OccupancyGrid.MAX_X && (cx<<4)+15>=OccupancyGrid.MIN_X
                && (cz<<4)<=OccupancyGrid.MAX_Z && (cz<<4)+15>=OccupancyGrid.MIN_Z;
    }
    private boolean usesChunk(ChunkPos chunk) {
        return buildingsByChunk.containsKey(chunk.toLong()) || roadsByChunk.containsKey(chunk.toLong())
                || (chunk.x>=(-346>>4) && chunk.x<=(6>>4) && chunk.z>=(125>>4) && chunk.z<=(190>>4));
    }
    private List<Integer> buildingIds(ChunkPos chunk) { return buildingsByChunk.getOrDefault(chunk.toLong(),List.of()); }
    private boolean buildingColumn(int x,int z) {
        for(int id:buildingIds(new ChunkPos(x>>4,z>>4))) if(plan().buildings().get(id).containsColumn(x,z)) return true;
        return false;
    }
    public void enqueue(ChunkPos chunk) { if(usesChunk(chunk) && !data.hasChunk(chunk.toLong())) queue.add(chunk.toLong()); }
    public void tick() {
        for(int budget=0;budget<2 && !queue.isEmpty();budget++) {
            var iterator=queue.iterator();long key=iterator.next();iterator.remove();
            int cx=ChunkPos.getX(key),cz=ChunkPos.getZ(key);
            if(level.getChunkSource().getChunkNow(cx,cz)!=null) ensureChunkPopulated(new ChunkPos(cx,cz));
        }
        if(level.getGameTime() % 20 == 0) AkyuuResidence.ensure(level, data);
    }
    public boolean ensureChunkPopulated(ChunkPos chunk) {
        if(populating || data.hasChunk(chunk.toLong()) || !intersectsChunk(chunk.x,chunk.z)) return data.hasChunk(chunk.toLong());
        if(level.getChunkSource().getChunkNow(chunk.x,chunk.z)==null) return false;
        if(!usesChunk(chunk)) return true;
        // Validate resources before touching terrain; a broken data pack remains retryable.
        for(int id:buildingIds(chunk)) {
            var b=plan().buildings().get(id);
            var template=level.getStructureManager().get(b.templateId()).orElse(null);
            if(template==null || template.getSize().getX()!=b.sizeX() || template.getSize().getY()!=b.sizeY() || template.getSize().getZ()!=b.sizeZ()) {
                LogUtils.getLogger().error("Missing or mismatched village template {}",b.templateId());return false;
            }
        }
        populating=true;
        try {
            HumanVillageStreamPlacer.populate(level,chunk);
            for(var cell:roadsByChunk.getOrDefault(chunk.toLong(),List.of())) placeRoad(cell);
            var box=new BoundingBox(chunk.x<<4,level.getMinBuildHeight(),chunk.z<<4,(chunk.x<<4)+15,level.getMaxBuildHeight()-1,(chunk.z<<4)+15);
            for(int id:buildingIds(chunk)) {
                var b=plan().buildings().get(id);
                prepareBuilding(b,chunk);
                var settings=new StructurePlaceSettings().setRotation(b.rotation()).setBoundingBox(box).setIgnoreEntities(true)
                        .setKnownShape(true).setLiquidSettings(LiquidSettings.IGNORE_WATERLOGGING);
                var template=level.getStructureManager().get(b.templateId()).orElseThrow();
                if(!template.placeInWorld(level,b.placementPivot(),b.placementPivot(),settings,
                        RandomSource.create(plan().worldSeed()^pack(b.minX(),b.minZ())),HumanVillageStreamPlacer.FLAGS)) return false;
            }
            placeRoadFurniture(chunk);
            data.markChunk(chunk.toLong());
            for(int i:buildingIds(chunk)) {
                var b=plan().buildings().get(i);boolean complete=true;
                for(int cx=b.minX()>>4;cx<=b.maxX()>>4;cx++) for(int cz=b.minZ()>>4;cz<=b.maxZ()>>4;cz++) if(!data.hasChunk(ChunkPos.asLong(cx,cz))) complete=false;
                if(complete) data.markBuilding(i);
            }
            return true;
        } finally { populating=false; }
    }
    private void prepareBuilding(BuildingInstance b,ChunkPos chunk) {
        for(int x=Math.max(b.minX(),chunk.x<<4);x<=Math.min(b.maxX(),(chunk.x<<4)+15);x++) {
            for(int z=Math.max(b.minZ(),chunk.z<<4);z<=Math.min(b.maxZ(),(chunk.z<<4)+15);z++) {
                int clearTop=Math.max(b.minY()+b.sizeY(),level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z));
                for(int y=b.minY()+1;y<=clearTop;y++) set(level,x,y,z,Blocks.AIR.defaultBlockState());
                // A watermill is intentionally over water. All other foundations stop at real solid ground.
                if(!b.templateId().getPath().equals("hv_watermill")) fillFoundation(x,b.minY()-1,z);
            }
        }
    }
    private void fillFoundation(int x,int y,int z) {
        for(;y>=level.getMinBuildHeight();y--) {
            var pos=new BlockPos(x,y,z);var state=level.getBlockState(pos);
            if(state.isSolidRender(level,pos) && state.getFluidState().isEmpty()) break;
            set(level,x,y,z,LandmarkBlocks.BRICK.get().defaultBlockState());
        }
    }
    private void placeRoad(RoadCell cell) {
        // Template-owned paving is represented in the graph but left intact, including statue basins.
        if(buildingColumn(cell.x(),cell.z())) return;
        int x=cell.x(),y=cell.surfaceY(),z=cell.z();
        int clearTop=Math.max(y+4,level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z));
        for(int py=y+1;py<=clearTop;py++) set(level,x,py,z,Blocks.AIR.defaultBlockState());
        if(cell.type()==RoadType.L4_BRIDGE) {
            double distance=VillageRoadRouter.streamDistance(x,z);
            if(distance<=1.2) {
                set(level,x,y,z,Blocks.AIR.defaultBlockState());
                set(level,x,y+1,z,LandmarkBlocks.WOOD_SLAB.get().defaultBlockState().setValue(SlabBlock.TYPE,SlabType.BOTTOM));
            } else set(level,x,y,z,LandmarkBlocks.WOOD.get().defaultBlockState());
        } else {
            fillFoundation(x,y-1,z);
            var state=cell.type()==RoadType.EARTH ? LandmarkBlocks.PACKED_EARTH.get().defaultBlockState()
                    : cell.type()==RoadType.L3_BRANCH && ((x*31+z)&3)==0 ? LandmarkBlocks.BRICK.get().defaultBlockState() : LandmarkBlocks.TILE.get().defaultBlockState();
            RoadCell north=roads.get(pack(x,z-1)),south=roads.get(pack(x,z+1)),east=roads.get(pack(x+1,z)),west=roads.get(pack(x-1,z));
            if(north!=null && north.surfaceY()==y+1) state=LandmarkBlocks.BRICK_STAIRS.get().defaultBlockState().setValue(StairBlock.FACING,Direction.NORTH);
            else if(south!=null && south.surfaceY()==y+1) state=LandmarkBlocks.BRICK_STAIRS.get().defaultBlockState().setValue(StairBlock.FACING,Direction.SOUTH);
            else if(east!=null && east.surfaceY()==y+1) state=LandmarkBlocks.BRICK_STAIRS.get().defaultBlockState().setValue(StairBlock.FACING,Direction.EAST);
            else if(west!=null && west.surfaceY()==y+1) state=LandmarkBlocks.BRICK_STAIRS.get().defaultBlockState().setValue(StairBlock.FACING,Direction.WEST);
            // The original approach feature paves heightmap-1, one block below the village floor.
            // A west-facing stair at the shared endpoint gives two half steps instead of a jump.
            if(plan().layoutVersion()==1 && x==GensokyoLayout.HAKUREI_ROAD_END_X && Math.abs(z-100)<=1) {
                state=LandmarkBlocks.STAIR.get().defaultBlockState().setValue(StairBlock.FACING,Direction.WEST);
            }
            set(level,x,y,z,state);
        }
    }
    private void placeRoadFurniture(ChunkPos chunk) {
        // Furniture belongs to the TARGET chunk, so reversed chunk loading cannot lose edge railings.
        for(int x=chunk.x<<4;x<(chunk.x<<4)+16;x++) for(int z=chunk.z<<4;z<(chunk.z<<4)+16;z++) {
            if(roads.containsKey(pack(x,z))) continue;
            boolean reserved=false;
            for(int id:buildingIds(chunk)) {
                var b=plan().buildings().get(id);
                if(b.containsColumn(x,z) || (b.hasDoorAnchor() && Math.abs(x-b.doorWorldX())+Math.abs(z-b.doorWorldZ())<=2)) reserved=true;
            }
            if(reserved) continue;
            for(int[] direction:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) {
                var cell=roads.get(pack(x+direction[0],z+direction[1]));
                if(cell==null) continue;
                double stream=VillageRoadRouter.streamDistance(x,z);
                if(cell.type()==RoadType.L4_BRIDGE && stream<=4.2) {
                    var rail=LandmarkBlocks.FENCE.get().defaultBlockState();
                    rail=direction[0]!=0 ? rail.setValue(FenceBlock.NORTH,true).setValue(FenceBlock.SOUTH,true)
                            : rail.setValue(FenceBlock.EAST,true).setValue(FenceBlock.WEST,true);
                    set(level,x,cell.surfaceY()+1,z,rail);break;
                }
                boolean bridgeHead=cell.type()==RoadType.L4_BRIDGE && stream>4.2 && stream<=7;
                int interval=cell.type()==RoadType.L1_MAIN?14:18;
                if(!bridgeHead && (stream<=5 || (cell.type()!=RoadType.L1_MAIN && cell.type()!=RoadType.L2_RING)
                        || Math.floorMod(cell.x()+cell.z(),interval)!=0)) continue;
                int y=cell.surfaceY();
                fillFoundation(x,y-1,z);
                set(level,x,y,z,LandmarkBlocks.BRICK.get().defaultBlockState());
                set(level,x,y+1,z,LandmarkBlocks.FENCE.get().defaultBlockState());
                set(level,x,y+2,z,LandmarkBlocks.FENCE.get().defaultBlockState());
                set(level,x,y+3,z,LandmarkBlocks.LANTERN.get().defaultBlockState());
                break;
            }
        }
    }
    public boolean isProtected(BlockPos pos) {
        if(!data.hasChunk(ChunkPos.asLong(pos.getX()>>4,pos.getZ()>>4))) return false;
        for(int id:buildingIds(new ChunkPos(pos.getX()>>4,pos.getZ()>>4))) {
            var b=plan().buildings().get(id);
            if(b.containsColumn(pos.getX(),pos.getZ()) && pos.getY()>=b.minY()-Math.max(8,b.foundationDepth()) && pos.getY()<b.minY()+b.sizeY()) return true;
        }
        RoadCell road=roads.get(pack(pos.getX(),pos.getZ()));
        return road!=null && pos.getY()>=road.surfaceY()-2 && pos.getY()<=road.surfaceY()+5;
    }
}
