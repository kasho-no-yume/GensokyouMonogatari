package com.bitsson.gensokyou.worldgen.human_village;

import com.bitsson.gensokyou.client.resident.ResidentGround;
import com.bitsson.gensokyou.client.resident.ResidentRoute;
import com.bitsson.gensokyou.client.resident.ResidentScene;
import com.bitsson.gensokyou.client.resident.VillageResidentAnchors;
import com.bitsson.gensokyou.network.HumanVillageLayoutSyncPayload;
import com.bitsson.gensokyou.support.MinecraftTestBootstrap;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.material.FluidState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static com.bitsson.gensokyou.worldgen.human_village.HumanVillagePlan.*;
import static org.junit.jupiter.api.Assertions.*;

class HumanVillageResidentsAlignmentTest {
    @BeforeAll static void bootstrap() { MinecraftTestBootstrap.ensureStarted(); }
    @Test void everyResidentAndEveryInterpolatedRouteSegmentHasFixedDryCollisionFreePaving() throws Exception {
        var json=JsonParser.parseString(Files.readString(Path.of("src/main/resources/assets/gensokyou/village_residents/human_village.json"))).getAsJsonObject();
        var scene=ResidentScene.parse("gensokyou:human_village",json);
        assertEquals(116,scene.residents().size());
        assertEquals(66,scene.residents().stream().filter(s->s.route().points().size()>1).count());
        for(long seed:new long[]{0,42,-8712}) {
            var plan=VillageTestPlans.get(seed);var world=new TemplateWorld(plan);var ground=new ResidentGround(world);
            Set<Long> stage=new HashSet<>();
            List<String> failures=new ArrayList<>();
            for(var c:plan.roadCells()) if(c.fixed()) stage.add(c.key());
            for(var slot:scene.residents()) {
                try {
                var points=slot.route().points();
                for(int i=0;i<points.size();i++) {
                    var from=points.get(i);
                    var to=points.get(Math.min(i+1,points.size()-1));
                    int steps=Math.max(1,(int)Math.ceil(from.distance(to)*4));
                    ResidentRoute.Point previous=null;
                    for(int step=0;step<=steps;step++) {
                        var raw=from.lerp(to,(double)step/steps);
                        var expected=new ResidentRoute.Point(raw.x(),raw.y()-(plan.layoutVersion()>=2?1:0),raw.z());
                        int x=(int)Math.floor(expected.x()),z=(int)Math.floor(expected.z());
                        assertTrue(stage.contains(pack(x,z)),slot.identity()+" left fixed stage at "+expected);
                        // Vanilla counterparts preserve actual collision shapes; no mod registration is needed in JUnit.
                        var resolved=ground.resolve(expected,1.021f,false);
                        assertNotNull(resolved,slot.identity()+" is obstructed, unsupported or wet at "+expected);
                        if(previous!=null) assertTrue(ground.clear(previous,resolved,1.021f),slot.identity()+" crosses collision at "+expected);
                        previous=resolved;
                    }
                }
                } catch(AssertionError error) { failures.add(error.getMessage()); }
            }
            assertTrue(failures.isEmpty(),String.join("\n",failures));
        }
    }

    @Test void allIndoorCustomerRoutesFitRealTemplatesInEveryRotationAndLayoutHeight() throws Exception {
        var shops=VillageTestPlans.get(42).buildings().stream()
                .filter(b->!VillageResidentAnchors.indoors(HumanVillageLayoutSyncPayload.InstanceEntry.of(b)).isEmpty()).toList();
        assertEquals(3,shops.size());
        for(var shop:shops) for(var rotation:Rotation.values()) for(int version:new int[]{1,2}) {
            var b=new BuildingInstance(shop.templateId(),shop.category(),-200,version==1?72:71,100,
                    shop.sizeX(),shop.sizeY(),shop.sizeZ(),rotation,shop.paletteThemeId(),0,0,0,false,true,1,2);
            var plan=new HumanVillagePlan(version,42,List.of(b),List.of(),Set.of());
            var ground=new ResidentGround(new TemplateWorld(plan));
            for(var slot:VillageResidentAnchors.indoors(HumanVillageLayoutSyncPayload.InstanceEntry.of(b))) {
                var points=slot.route().points();var from=points.getFirst();var to=points.getLast();
                int steps=(int)Math.ceil(from.distance(to)*4);
                ResidentRoute.Point previous=null;
                for(int i=0;i<=steps;i++) {
                    var expected=from.lerp(to,(double)i/steps);
                    var actual=ground.resolve(expected,1.025f,false);
                    String context=slot.identity()+" "+rotation+" v"+version+" "+expected;
                    assertNotNull(actual,context);
                    assertEquals(expected.y(),actual.y(),.01,context+" must stay on the room floor");
                    if(previous!=null) assertTrue(ground.clear(previous,actual,1.025f),context);
                    previous=actual;
                }
            }
        }
    }

    private static final class TemplateWorld implements ResidentGround.World,BlockGetter {
        private final Map<BlockPos,BlockState> blocks=new HashMap<>();
        TemplateWorld(HumanVillagePlan plan) throws Exception {
            for(var c:plan.roadCells()) {
                blocks.put(new BlockPos(c.x(),c.surfaceY(),c.z()),(c.type()==RoadType.EARTH?Blocks.DIRT:Blocks.STONE_BRICKS).defaultBlockState());
                if(c.type()==RoadType.L4_BRIDGE && VillageRoadRouter.streamDistance(c.x(),c.z())<=1.2) {
                    blocks.remove(new BlockPos(c.x(),c.surfaceY(),c.z()));
                    blocks.put(new BlockPos(c.x(),c.surfaceY()+1,c.z()),Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE,SlabType.BOTTOM));
                }
            }
            for(var b:plan.buildings()) {
                // This test checks the authored core, not every voxel of thousands of distant houses.
                if(b.maxX()<-382 || b.minX()>42 || b.maxZ()<-84 || b.minZ()>232) continue;
                try(var stream=getClass().getResourceAsStream("/data/gensokyou/structure/"+b.templateId().getPath()+".nbt")) {
                    assertNotNull(stream);var nbt=NbtIo.readCompressed(stream,NbtAccounter.unlimitedHeap());
                    var size=nbt.getList("size",Tag.TAG_INT);
                    assertEquals(b.sizeX(),size.getInt(0));assertEquals(b.sizeY(),size.getInt(1));assertEquals(b.sizeZ(),size.getInt(2));
                    var palette=nbt.getList("palette",Tag.TAG_COMPOUND);var states=new ArrayList<BlockState>();
                    for(int i=0;i<palette.size();i++) {
                        var entry=palette.getCompound(i);var state=analog(entry.getString("Name")).defaultBlockState();
                        var properties=entry.getCompound("Properties");
                        for(String key:properties.getAllKeys()) {
                            var property=state.getBlock().getStateDefinition().getProperty(key);
                            if(property!=null) state=withValue(state,property,properties.getString(key));
                        }
                        states.add(state.rotate(b.rotation()));
                    }
                    var voxels=nbt.getList("blocks",Tag.TAG_COMPOUND);
                    for(int i=0;i<voxels.size();i++) {
                        var voxel=voxels.getCompound(i);var pos=voxel.getList("pos",Tag.TAG_INT);
                        BlockPos world=StructureTemplate.transform(new BlockPos(pos.getInt(0),pos.getInt(1),pos.getInt(2)),Mirror.NONE,b.rotation(),BlockPos.ZERO).offset(b.placementPivot());
                        blocks.put(world,states.get(voxel.getInt("state")));
                    }
                }
            }
        }
        private static Block analog(String name) {
            return switch(name) {
                case "gensokyou:landmark_brick", "gensokyou:landmark_tile" -> Blocks.STONE_BRICKS;
                case "gensokyou:landmark_wood" -> Blocks.OAK_PLANKS;
                case "gensokyou:landmark_tile_slab", "gensokyou:landmark_brick_slab" -> Blocks.STONE_BRICK_SLAB;
                case "gensokyou:landmark_wood_slab" -> Blocks.OAK_SLAB;
                case "gensokyou:landmark_tile_stairs", "gensokyou:landmark_brick_stairs" -> Blocks.STONE_BRICK_STAIRS;
                case "gensokyou:landmark_wood_stairs" -> Blocks.OAK_STAIRS;
                case "gensokyou:landmark_wood_fence" -> Blocks.OAK_FENCE;
                case "gensokyou:landmark_wood_fence_gate" -> Blocks.OAK_FENCE_GATE;
                case "gensokyou:landmark_wood_door" -> Blocks.OAK_DOOR;
                case "gensokyou:landmark_wood_trapdoor" -> Blocks.OAK_TRAPDOOR;
                case "gensokyou:landmark_stone_wall" -> Blocks.COBBLESTONE_WALL;
                case "gensokyou:landmark_shoji" -> Blocks.IRON_BARS;
                case "gensokyou:landmark_curtain" -> Blocks.AIR;
                case "gensokyou:landmark_bookshelf" -> Blocks.BOOKSHELF;
                case "gensokyou:landmark_lantern" -> Blocks.LANTERN;
                case "gensokyou:landmark_lamp" -> Blocks.GLOWSTONE;
                case "gensokyou:gensokyo_grass_block" -> Blocks.GRASS_BLOCK;
                default -> BuiltInRegistries.BLOCK.get(ResourceLocation.parse(name));
            };
        }
        @Override public BlockGetter blocks() { return this; }
        @Override public boolean loaded(int x,int z) { return true; }
        @Override public int minY() { return 0; }
        @Override public int maxY() { return 256; }
        @Override public BlockEntity getBlockEntity(BlockPos pos) { return null; }
        @Override public BlockState getBlockState(BlockPos pos) { return blocks.getOrDefault(pos,Blocks.AIR.defaultBlockState()); }
        @Override public FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }
        @Override public int getHeight() { return 256; }
        @Override public int getMinBuildHeight() { return 0; }
        private static <T extends Comparable<T>> BlockState withValue(BlockState state,Property<T> property,String text) {
            return property.getValue(text).map(value->state.setValue(property,value)).orElse(state);
        }
    }
}
