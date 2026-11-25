package com.bitsson.gensokyou.testkit;

import com.bitsson.gensokyou.client.resident.ResidentGround;
import com.bitsson.gensokyou.client.resident.ResidentRoute;
import com.bitsson.gensokyou.client.resident.ResidentScene;
import com.bitsson.gensokyou.landmark.HakureiShrineLandmark;
import com.bitsson.gensokyou.registry.LandmarkBlocks;
import com.bitsson.gensokyou.worldgen.human_village.*;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;

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
            // A single estate slice must not force the rest of this 76x64-block building into memory.
            int sliceX=manager.data().hasChunk(ChunkPos.asLong(-13,-3))?-14:-13;
            level.getChunk(sliceX,-3);
            int before=level.getChunkSource().getLoadedChunksCount();
            long start=System.nanoTime();
            check(manager.ensureChunkPopulated(new ChunkPos(sliceX,-3)),"Estate slice failed");
            check(level.getChunkSource().getLoadedChunksCount()==before,"Chunk-local placement loaded neighbors");
            var witness=new BlockPos(sliceX==-13?-200:-216,76,-36);
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
                for(int y=69;y<=70;y++) if(!(mill&&y==70)) check(level.getBlockState(new BlockPos(x,y,z)).is(Blocks.WATER),"Blocked stream at "+x+","+y+","+z);
            }
            var floor=new BlockPos(-200,72,130);
            check(EventHooks.onBlockPlace(null,BlockSnapshot.create(level.dimension(),level,floor.above()),Direction.UP),"Protected road allowed placement");

            ResidentScene scene;
            try(var stream=HumanVillageProbe.class.getResourceAsStream("/assets/gensokyou/village_residents/human_village.json")) {
                check(stream!=null,"Missing resident scene");
                scene=ResidentScene.parse("gensokyou:human_village",JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject());
            }
            var ground=new ResidentGround(new ResidentGround.World() {
                @Override public net.minecraft.world.level.BlockGetter blocks() {return level;}
                @Override public boolean loaded(int x,int z) {return level.getChunkSource().getChunkNow(x>>4,z>>4)!=null;}
                @Override public int minY() {return level.getMinBuildHeight();}
                @Override public int maxY() {return level.getMaxBuildHeight();}
            });
            check(level.getBlockState(new BlockPos(40,72,100)).is(LandmarkBlocks.STAIR.get()),"Approach endpoint ramp missing");
            ResidentRoute.Point previousSeam=null;
            for(int step=0;step<=12;step++) {
                var p=new ResidentRoute.Point(39.5+step*0.25,73.0-step/12.0,100.5);
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
                        var p=a.lerp(b,(double)j/steps);
                        var resolved=ground.resolve(p,1.021f,true);
                        check(resolved!=null,slot.identity()+" has invalid native paving/collision at "+p);
                        if(previous!=null) check(ground.clear(previous,resolved,1.021f),slot.identity()+" has blocked native route at "+p);
                        previous=resolved;samples++;
                    }
                }
            }
            var connected=VillageRoadRouter.connectedColumns(manager.plan().roadCells());
            long homes=manager.plan().buildings().stream().filter(b->b.category()==BuildingInstance.BuildingCategory.RESIDENTIAL).count();
            long reached=manager.plan().buildings().stream().filter(b->b.category()==BuildingInstance.BuildingCategory.RESIDENTIAL&&connected.contains(HumanVillagePlan.pack(b.doorWorldX(),b.doorWorldZ()))).count();
            check(reached>=homes*0.9,"Native layout has too many disconnected homes");
            String pass="HUMAN_VILLAGE_PROBE PASS buildings="+manager.plan().buildings().size()+" homes="+homes+" connected="+reached+" roads="+manager.plan().roadCells().size()+" chunks="+chunks.size()+" residents="+scene.residents().size()+" routeSamples="+samples;
            Files.writeString(result,pass+System.lineSeparator(),StandardCharsets.UTF_8);
            System.out.println(pass);
        } finally {server.halt(false);}
    }
    private static void check(boolean condition,String message) {if(!condition) throw new AssertionError(message);}
}
