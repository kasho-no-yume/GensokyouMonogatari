package com.bitsson.gensokyou.worldgen.human_village;

import com.bitsson.gensokyou.registry.LandmarkBlocks;
import com.bitsson.gensokyou.registry.ModBlocks;
import com.bitsson.gensokyou.worldgen.GensokyoLayout;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** Chunk-local canal excavation. Banks and sealed ends are built before any water sources. */
public final class HumanVillageStreamPlacer {
    static final int FLAGS=Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE;
    private HumanVillageStreamPlacer() {}
    public static boolean waterColumn(int x,int z) { return x>=-345 && x<=5 && Math.abs(z-GensokyoLayout.villageStreamCenterZ(x))<=3; }
    public static boolean bankColumn(int x,int z) {
        double d=Math.abs(z-GensokyoLayout.villageStreamCenterZ(x));
        return (x>=-345 && x<=5 && d>3 && d<=4.2) || ((x==-346 || x==6) && d<=4.2);
    }
    public static void populate(ServerLevel level,ChunkPos chunk) {
        int startX=chunk.x<<4,startZ=chunk.z<<4;
        for(int x=startX;x<startX+16;x++) for(int z=startZ;z<startZ+16;z++) {
            if(bankColumn(x,z)) {
                for(int y=68;y<=71;y++) set(level,x,y,z,LandmarkBlocks.BRICK.get().defaultBlockState());
            } else if(waterColumn(x,z)) {
                set(level,x,68,z,((x*31+z)&3)==0 ? Blocks.GRAVEL.defaultBlockState() : ModBlocks.GENSOKYO_SAND.get().defaultBlockState());
                for(int y=69;y<=76;y++) set(level,x,y,z,Blocks.AIR.defaultBlockState());
            }
        }
        // Flags deliberately do not notify fluid neighbors, including unloaded chunk edges.
        for(int x=startX;x<startX+16;x++) for(int z=startZ;z<startZ+16;z++) {
            if(waterColumn(x,z)) for(int y=69;y<=70;y++) set(level,x,y,z,Blocks.WATER.defaultBlockState());
            if(x>=-300 && x<=-90 && Math.floorMod(x,10)==0 && bankColumn(x,z)) {
                set(level,x,72,z,LandmarkBlocks.WALL.get().defaultBlockState());
                set(level,x,73,z,LandmarkBlocks.LAMP.get().defaultBlockState());
                set(level,x,74,z,LandmarkBlocks.SLAB.get().defaultBlockState());
            }
        }
    }
    static void set(ServerLevel level,int x,int y,int z,net.minecraft.world.level.block.state.BlockState state) {
        var pos=new BlockPos(x,y,z);
        if(!level.getBlockState(pos).equals(state)) level.setBlock(pos,state,FLAGS);
    }
}
