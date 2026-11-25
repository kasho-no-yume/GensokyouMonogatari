package com.bitsson.gensokyou.landmark;

import com.bitsson.gensokyou.network.HumanVillageLayoutSyncPayload;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class LocalLandmarkColorsTest {
    private static final ResourceLocation WOOD=ResourceLocation.fromNamespaceAndPath("gensokyou","landmark_wood");
    @Test void sparseRoundTripAndAllFourRotationsMatchVanillaTransform() throws Exception {
        var colors=LocalLandmarkColors.read(new ByteArrayInputStream(fixture(15)));
        var out=new ByteArrayOutputStream(); colors.write(out);
        var copy=LocalLandmarkColors.read(new ByteArrayInputStream(out.toByteArray()));
        assertEquals(5,copy.sizeX()); assertEquals(3,copy.sizeY()); assertEquals(3,copy.sizeZ());
        for (Rotation rotation:Rotation.values()) {
            BlockPos pivot=copy.placementPivot(-31,72,-17,rotation);
            for(int x=0;x<5;x++) for(int z=0;z<3;z++) {
                BlockPos world=StructureTemplate.transform(new BlockPos(x,1,z),Mirror.NONE,rotation,BlockPos.ZERO).offset(pivot);
                assertEquals(0xFF102030+x*3+z,copy.colorAtWorld(world.getX(),world.getY(),world.getZ(),-31,72,-17,rotation,-1,WOOD));
                assertEquals(LandmarkColors.NO_TINT,copy.colorAtWorld(world.getX(),world.getY(),world.getZ(),-31,72,-17,rotation,-1,ResourceLocation.withDefaultNamespace("stone")));
            }
            assertEquals(LandmarkColors.NO_TINT,copy.colorAtWorld(-32,73,-17,-31,72,-17,rotation,-1,WOOD));
        }
    }
    @Test void allEightThemesReplaceEveryRoleAndPreserveFixedColors() {
        int[][] expected={{0x4B5E6F,0x687D8F,0xECE6D9,0x8A5D3B,0x4E3320,0x3A5F82,0x8E8980},
                {0x363A40,0x525861,0xE5DCCB,0x6F482D,0x3C2516,0x9E3D2E,0x7F7B74},
                {0x5A737A,0x79939A,0xF2EEE4,0xB58A5E,0x7A5635,0x4B775B,0x969188},
                {0x6B433B,0x885C52,0xE3D8C5,0x7C4E32,0x442818,0xC48A3A,0x867F76},
                {0x485561,0x637280,0xEFE6D5,0x9B6A43,0x5B3A22,0xC14436,0x8D877E},
                {0x555E66,0x737E88,0xE8E6DF,0x7A5A42,0x463224,0x2F4E7A,0x8A8A86},
                {0x3F4652,0x5C6573,0xF4F1EA,0x734C32,0x3E2719,0x6D4875,0x84817B},
                {0x7D694D,0x988363,0xDFD3BC,0x9C754F,0x62462C,0x7A6A46,0x92897B}};
        for(int theme=0;theme<8;theme++) {
            assertEquals(0xFF123456,HumanVillagePalette.resolveRgb(0xFF123456,0,theme));
            for(int role=1;role<=7;role++) assertEquals(0xFF000000|expected[theme][role-1],HumanVillagePalette.resolveRgb(0xFF123456,role,theme));
        }
        assertEquals(0xFF123456,HumanVillagePalette.resolveRgb(0xFF123456,4,-1));
    }
    @Test void shippedTemplatesDecodeAndRejectMalformedEntryCounts() throws Exception {
        for (var exhibit:HumanVillageShowcase.EXHIBITS) {
            try(var in=getClass().getResourceAsStream("/assets/gensokyou/landmark/"+exhibit.id().getPath()+".local.bin")) {
                assertNotNull(in); var colors=LocalLandmarkColors.read(in);
                assertEquals(exhibit.sizeX(),colors.sizeX()); assertEquals(exhibit.sizeY(),colors.sizeY()); assertEquals(exhibit.sizeZ(),colors.sizeZ());
            }
        }
        assertThrows(IOException.class,()->LocalLandmarkColors.read(new ByteArrayInputStream(fixture(-1))));
        assertThrows(IOException.class,()->LocalLandmarkColors.read(new ByteArrayInputStream(fixture(46))));
    }
    @Test void wireRoundTripAndChunkBucketsWorkAcrossNegativeBoundaries() throws Exception {
        var first=new HumanVillageLayoutSyncPayload.InstanceEntry(WOOD,-17,72,-17,5,3,3,(byte)0,(byte)0);
        var second=new HumanVillageLayoutSyncPayload.InstanceEntry(WOOD,15,72,15,5,3,3,(byte)1,(byte)1);
        var payload=new HumanVillageLayoutSyncPayload(List.of(first,second));
        var buffer=new FriendlyByteBuf(Unpooled.buffer());
        try {
            HumanVillageLayoutSyncPayload.STREAM_CODEC.encode(buffer,payload);
            assertEquals(payload,HumanVillageLayoutSyncPayload.STREAM_CODEC.decode(buffer));
        } finally { buffer.release(); }
        var colors=LocalLandmarkColors.read(new ByteArrayInputStream(fixture(15)));
        var index=new VillageColorIndex(payload.instances(), id->colors);
        assertEquals(0xFF4B5E6F,index.colorAt(-16,73,-16,WOOD));
        assertEquals(0xFF363A40,index.colorAt(16,73,16,WOOD));
        assertEquals(LandmarkColors.NO_TINT,index.colorAt(1,73,1,WOOD));
        assertThrows(IllegalArgumentException.class,()->new HumanVillageLayoutSyncPayload.InstanceEntry(WOOD,0,0,0,5,3,3,(byte)4,(byte)0));
        assertEquals(LandmarkColors.NO_TINT,HumanVillageProceduralColors.colorAt(-421,72,100,WOOD));
        assertNotEquals(LandmarkColors.NO_TINT,HumanVillageProceduralColors.colorAt(-200,72,100,WOOD));
    }
    private static byte[] fixture(int count) throws IOException {
        var bytes=new ByteArrayOutputStream();
        try(var out=new DataOutputStream(new GZIPOutputStream(bytes))) {
            out.writeInt(LocalLandmarkColors.GLC3_MAGIC);out.writeShort(5);out.writeShort(3);out.writeShort(3);
            out.writeShort(16);out.writeInt(0);out.writeByte(0);
            for(int i=0;i<15;i++) {out.writeInt(0x102030+i);out.writeByte(1);}
            out.writeByte(1);out.writeUTF(WOOD.toString());out.writeInt(count);
            if(count==15) for(int x=0;x<5;x++) for(int z=0;z<3;z++) {out.writeShort(x);out.writeShort(1);out.writeShort(z);out.writeByte(x*3+z+1);out.writeByte(1);}
        }
        return bytes.toByteArray();
    }
}
