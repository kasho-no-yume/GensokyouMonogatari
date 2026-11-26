package com.bitsson.gensokyou.testkit;

import com.bitsson.gensokyou.client.resident.ResidentGround;
import com.bitsson.gensokyou.client.resident.ResidentRoute;
import com.bitsson.gensokyou.client.resident.ResidentScene;
import com.bitsson.gensokyou.client.resident.VillageResidentAnchors;
import com.bitsson.gensokyou.landmark.HakureiShrineLandmark;
import com.bitsson.gensokyou.network.HumanVillageLayoutSyncPayload;
import com.bitsson.gensokyou.registry.LandmarkBlocks;
import com.bitsson.gensokyou.registry.ModEntityTypes;
import com.bitsson.gensokyou.worldgen.human_village.*;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

/** Dev-only isolated native-world probe, excluded from the distributed jar with the testkit package. */
@EventBusSubscriber(modid="gensokyou")
public final class HumanVillageProbe {
    private HumanVillageProbe() {}
    @SubscribeEvent public static void run(ServerStartedEvent event) throws Exception {
        if(!Boolean.getBoolean("gensokyou.humanVillageProbe")) return;
        Path result=Path.of("human-village-probe-pass.txt");
        Files.deleteIfExists(result);
        var server=event.getServer();
        try {
            var level=server.getLevel(HakureiShrineLandmark.GENSOKYO_DIMENSION);
            check(level!=null,"Missing Gensokyo dimension");
            var manager=HumanVillageManager.get(level);
            if(Boolean.getBoolean("gensokyou.humanVillageServicesOnly")) {
                var serviceChunks=new LinkedHashSet<Long>();
                for(var b:manager.plan().buildings()) {
                    if(b.category()!=BuildingInstance.BuildingCategory.SPECIAL_LANDMARK) continue;
                    for(int x=(b.minX()-1)>>4;x<=b.maxX()>>4;x++) for(int z=b.minZ()>>4;z<=b.maxZ()>>4;z++)
                        serviceChunks.add(ChunkPos.asLong(x,z));
                }
                serviceChunks.add(ChunkPos.asLong(-200>>4,130>>4));
                serviceChunks.add(ChunkPos.asLong(-200>>4,100>>4));
                for(long key:serviceChunks) {
                    var chunk=new ChunkPos(ChunkPos.getX(key),ChunkPos.getZ(key));
                    level.getChunk(chunk.x,chunk.z);
                    check(manager.ensureChunkPopulated(chunk),"Service landmark chunk failed "+chunk);
                }
                verifyServiceSafety(level,manager,ground(level));
                String pass="HUMAN_VILLAGE_PROBE PASS buildings=4 indoorCustomers=10 protectedLandmarks=4 hostileSpawnChecks=PASS chunks="+serviceChunks.size();
                Files.writeString(result,pass+System.lineSeparator(),StandardCharsets.UTF_8);
                System.out.println(pass);
                return;
            }
            // A single estate slice must not force the rest of this 76x64-block building into memory.
            int sliceX=manager.data().hasChunk(ChunkPos.asLong(-13,-3))?-14:-13;
            level.getChunk(sliceX,-3);
            int before=level.getChunkSource().getLoadedChunksCount();
            long start=System.nanoTime();
            check(manager.ensureChunkPopulated(new ChunkPos(sliceX,-3)),"Estate slice failed");
            check(level.getChunkSource().getLoadedChunksCount()==before,"Chunk-local placement loaded neighbors");
            var witness=new BlockPos(sliceX==-13?-200:-216,manager.plan().layoutVersion()==1?76:75,-36);
            check(level.getBlockState(witness).is(LandmarkBlocks.WOOD.get()),"Estate floor slice missing: "+level.getBlockState(witness));
            int progress=manager.data().placedChunkCount();
            check(manager.ensureChunkPopulated(new ChunkPos(sliceX,-3)) && progress==manager.data().placedChunkCount(),"Repeated placement was not idempotent");
            System.out.println("HUMAN_VILLAGE_PROBE slice PASS no neighbor loads, " + (System.nanoTime()-start)/1_000_000 + " ms");

            // The TEST explicitly loads every used chunk, while the production manager never preloads them.
            var chunks=new LinkedHashSet<Long>();
            for(var b:manager.plan().buildings()) for(int cx=b.minX()>>4;cx<=b.maxX()>>4;cx++) for(int cz=b.minZ()>>4;cz<=b.maxZ()>>4;cz++) chunks.add(ChunkPos.asLong(cx,cz));
            for(var c:manager.plan().roadCells()) chunks.add(ChunkPos.asLong(c.x()>>4,c.z()>>4));
            for(int x=-346;x<=6;x++) for(int z=130;z<=180;z++) if(HumanVillageStreamPlacer.waterColumn(x,z)||HumanVillageStreamPlacer.bankColumn(x,z)) chunks.add(ChunkPos.asLong(x>>4,z>>4));
            int processed=0;
            for(long key:chunks) {
                var chunk=new ChunkPos(ChunkPos.getX(key),ChunkPos.getZ(key));
                level.getChunk(chunk.x,chunk.z);
                check(manager.ensureChunkPopulated(chunk),"Chunk failed "+chunk);
                if(++processed%40==0) System.out.println("HUMAN_VILLAGE_PROBE chunks "+processed+"/"+chunks.size());
            }
            check(manager.data().placedBuildingCount()==manager.plan().buildings().size(),"Incomplete building progress");
            var restored=HumanVillageSavedData.load(manager.data().save(new CompoundTag(),level.registryAccess()),level.registryAccess());
            check(restored.plan().equals(manager.plan()) && restored.placedChunkCount()==manager.data().placedChunkCount(),"Native save round trip failed");

            for(int x=-345;x<=5;x++) for(int z=130;z<=180;z++) if(HumanVillageStreamPlacer.waterColumn(x,z)) {
                boolean mill=false;
                for(var b:manager.plan().buildings()) if(b.templateId().getPath().equals("hv_watermill")&&b.containsColumn(x,z)) mill=true;
                for(int y=69;y<=70;y++) if(!mill) check(level.getBlockState(new BlockPos(x,y,z)).is(Blocks.WATER),"Blocked stream at "+x+","+y+","+z);
            }
            int floorY=manager.plan().layoutVersion()==1?72:71;
            var floor=new BlockPos(-200,floorY,130);
            check(EventHooks.onBlockPlace(null,BlockSnapshot.create(level.dimension(),level,floor.above()),Direction.UP),"Protected road allowed placement");

            ResidentScene scene;
            try(var stream=HumanVillageProbe.class.getResourceAsStream("/assets/gensokyou/village_residents/human_village.json")) {
                check(stream!=null,"Missing resident scene");
                scene=ResidentScene.parse("gensokyou:human_village",JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject());
            }
            var ground=ground(level);
            if(manager.plan().layoutVersion()==1) check(level.getBlockState(new BlockPos(40,72,100)).is(LandmarkBlocks.STAIR.get()),"Legacy approach endpoint ramp missing");
            else check(level.getBlockState(new BlockPos(40,71,100)).is(LandmarkBlocks.PACKED_EARTH.get()),"Flush village earth endpoint missing");
            ResidentRoute.Point previousSeam=null;
            for(int step=0;step<=12;step++) {
                var p=new ResidentRoute.Point(39.5+step*0.25,manager.plan().layoutVersion()==1?73.0-step/12.0:72,100.5);
                var resolved=ground.resolve(p,1.021f,false);
                check(resolved!=null,"Approach seam has no support at "+p);
                if(previousSeam!=null) check(ground.clear(previousSeam,resolved,1.021f),"Approach seam has collision at "+p);
                previousSeam=resolved;
            }
            int samples=0;
            for(var slot:scene.residents()) {
                var points=slot.route().points();
                for(int i=0;i<points.size();i++) {
                    var a=points.get(i);var b=points.get(Math.min(i+1,points.size()-1));
                    int steps=Math.max(1,(int)Math.ceil(a.distance(b)*4));
                    ResidentRoute.Point previous=null;
                    for(int j=0;j<=steps;j++) {
                        var raw=a.lerp(b,(double)j/steps);
                        var p=new ResidentRoute.Point(raw.x(),raw.y()-(manager.plan().layoutVersion()>=2?1:0),raw.z());
                        var resolved=ground.resolve(p,1.021f,true);
                        check(resolved!=null,slot.identity()+" has invalid native paving/collision at "+p);
                        if(previous!=null) check(ground.clear(previous,resolved,1.021f),slot.identity()+" has blocked native route at "+p);
                        previous=resolved;samples++;
                    }
                }
            }
            verifyServiceSafety(level,manager,ground);
            var connected=VillageWalkability.reachableDoors(manager.plan());
            long homes=manager.plan().buildings().stream().filter(b->b.category()==BuildingInstance.BuildingCategory.RESIDENTIAL).count();
            long reached=manager.plan().buildings().stream().filter(b->b.category()==BuildingInstance.BuildingCategory.RESIDENTIAL&&connected.contains(HumanVillagePlan.pack(b.doorWorldX(),b.doorWorldZ()))).count();
            check(reached>=homes*0.9,"Native layout has too many disconnected homes");
            String pass="HUMAN_VILLAGE_PROBE PASS buildings="+manager.plan().buildings().size()+" homes="+homes+" connected="+reached+" roads="+manager.plan().roadCells().size()+" chunks="+chunks.size()+" residents="+scene.residents().size()+" routeSamples="+samples+" indoorCustomers=10 protectedLandmarks=4 hostileSpawnChecks=PASS";
            Files.writeString(result,pass+System.lineSeparator(),StandardCharsets.UTF_8);
            System.out.println(pass);
        } finally {server.halt(false);}
    }

    private static ResidentGround ground(ServerLevel level) {
        return new ResidentGround(new ResidentGround.World() {
            @Override public net.minecraft.world.level.BlockGetter blocks() {return level;}
            @Override public boolean loaded(int x,int z) {return level.getChunkSource().getChunkNow(x>>4,z>>4)!=null;}
            @Override public int minY() {return level.getMinBuildHeight();}
            @Override public int maxY() {return level.getMaxBuildHeight();}
        });
    }

    private static void verifyServiceSafety(ServerLevel level,HumanVillageManager manager,ResidentGround ground) {
        var player=FakePlayerFactory.get(level,new GameProfile(UUID.fromString("48115202-7c7f-42c8-9c82-472bb7cfbe55"),"[VillageProbe]"));
        int landmarks=0,customers=0;
        for(var b:manager.plan().buildings()) {
            if(b.category()!=BuildingInstance.BuildingCategory.SPECIAL_LANDMARK) continue;
            landmarks++;
            var inside=new BlockPos(b.minX(),b.minY()+1,b.minZ()+b.rotSizeZ()/2);
            var outside=inside.west();
            check(!manager.isPlacementProtected(outside,true),"Landmark placement guard escaped its footprint: "+b.templateId());
            for(var mode:new GameType[]{GameType.SURVIVAL,GameType.CREATIVE}) {
                player.setGameMode(mode);
                check(EventHooks.onBlockPlace(player,BlockSnapshot.create(level.dimension(),level,inside),Direction.UP),
                        mode+" placed inside "+b.templateId());
                check(EventHooks.onMultiBlockPlace(player,List.of(BlockSnapshot.create(level.dimension(),level,outside),
                                BlockSnapshot.create(level.dimension(),level,inside)),Direction.UP),
                        mode+" multi-place crossed into "+b.templateId());
            }
            check(!EventHooks.onBlockPlace(player,BlockSnapshot.create(level.dimension(),level,outside),Direction.UP),
                    "Creative placement outside the landmark was blocked: "+b.templateId());
            check(!manager.isPlacementProtected(new BlockPos(inside.getX(),b.minY()+b.sizeY(),inside.getZ()),true),
                    "Landmark placement guard escaped its height: "+b.templateId());
            for(var slot:VillageResidentAnchors.indoors(HumanVillageLayoutSyncPayload.InstanceEntry.of(b))) {
                customers++;
                var from=slot.route().points().getFirst();var to=slot.route().points().getLast();
                int steps=(int)Math.ceil(from.distance(to)*4);
                ResidentRoute.Point previous=null;
                for(int i=0;i<=steps;i++) {
                    var p=from.lerp(to,(double)i/steps);var resolved=ground.resolve(p,1.025f,true);
                    check(resolved!=null && Math.abs(resolved.y()-p.y())<.01,slot.identity()+" has invalid native indoor support at "+p);
                    if(previous!=null) check(ground.clear(previous,resolved,1.025f),slot.identity()+" crosses native furniture at "+p);
                    previous=resolved;
                }
            }
        }
        check(landmarks==4 && customers==10,"Expected four protected landmarks and ten indoor customers");
        var road=new BlockPos(-200,manager.plan().layoutVersion()==1?73:72,130);
        check(!EventHooks.onBlockPlace(player,BlockSnapshot.create(level.dimension(),level,road),Direction.UP),
                "Creative road placement lost its existing exemption");

        var village=new BlockPos(-200,manager.plan().layoutVersion()==1?73:72,100);
        check(level.getBiome(village).is(HumanVillageSpawns.BIOME),"Spawn test is outside the human village biome");
        check(level.getBiome(village).value().getMobSettings().getMobs(MobCategory.MONSTER).isEmpty(),"Human village still has hostile biome spawns");
        for(var type:List.of(EntityType.ZOMBIE,EntityType.SLIME,ModEntityTypes.FAIRY.get(),EntityType.PHANTOM,EntityType.PILLAGER,EntityType.COW)) {
            var mob=type.create(level);
            check(mob!=null,"Spawn test could not create "+type);
            mob.moveTo(village.getX()+.5,village.getY(),village.getZ()+.5,0,0);
            for(var reason:MobSpawnType.values()) {
                var spawn=new MobSpawnEvent.PositionCheck(mob,level,reason,null);
                NeoForge.EVENT_BUS.post(spawn);
                boolean blocked=type!=EntityType.COW && (reason==MobSpawnType.NATURAL || reason==MobSpawnType.CHUNK_GENERATION || reason==MobSpawnType.PATROL);
                check(spawn.getResult()==(blocked?MobSpawnEvent.PositionCheck.Result.FAIL:MobSpawnEvent.PositionCheck.Result.DEFAULT),
                        "Unexpected village spawn decision for "+type+" "+reason+": "+spawn.getResult());
                mob.setSpawnCancelled(false);
                var finalize=new FinalizeSpawnEvent(mob,level,mob.getX(),mob.getY(),mob.getZ(),level.getCurrentDifficultyAt(village),reason,null,null);
                NeoForge.EVENT_BUS.post(finalize);
                check(finalize.isSpawnCancelled()==blocked,"Unexpected village finalization for "+type+" "+reason);
            }
            mob.setSpawnCancelled(false);
            mob.moveTo(3072.5,97,128.5,0,0);
            var outside=new MobSpawnEvent.PositionCheck(mob,level,MobSpawnType.NATURAL,null);
            NeoForge.EVENT_BUS.post(outside);
            check(outside.getResult()==MobSpawnEvent.PositionCheck.Result.DEFAULT,"Village spawn guard leaked into another biome");
            var finalizeOutside=new FinalizeSpawnEvent(mob,level,mob.getX(),mob.getY(),mob.getZ(),level.getCurrentDifficultyAt(mob.blockPosition()),MobSpawnType.NATURAL,null,null);
            NeoForge.EVENT_BUS.post(finalizeOutside);
            check(!finalizeOutside.isSpawnCancelled(),"Village finalization guard leaked into another biome");
        }
        System.out.println("HUMAN_VILLAGE_PROBE services PASS indoorCustomers=10 protectedLandmarks=4 hostileSpawnChecks=PASS");
    }
    private static void check(boolean condition,String message) {if(!condition) throw new AssertionError(message);}
}
