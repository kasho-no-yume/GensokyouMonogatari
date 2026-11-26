package com.bitsson.gensokyou.network;

import com.bitsson.gensokyou.worldgen.human_village.BuildingInstance;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;

/** Bounded instance descriptors only; shared GLC3 resources are never sent over the network. */
public record HumanVillageLayoutSyncPayload(int layoutVersion, List<InstanceEntry> instances) implements CustomPacketPayload {
    public static final int MAX_INSTANCES = com.bitsson.gensokyou.worldgen.human_village.HumanVillagePlan.MAX_BUILDINGS;
    public HumanVillageLayoutSyncPayload(List<InstanceEntry> instances) { this(1,instances); }
    public static final Type<HumanVillageLayoutSyncPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("gensokyou", "human_village_layout"));
    public static final StreamCodec<FriendlyByteBuf, HumanVillageLayoutSyncPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override public HumanVillageLayoutSyncPayload decode(FriendlyByteBuf buf) {
            int version=buf.readVarInt();
            int templates=buf.readVarInt();
            if(templates<0 || templates>MAX_INSTANCES) throw new IllegalArgumentException("Invalid village template count");
            var ids=new ResourceLocation[templates];
            for(int i=0;i<templates;i++) ids[i]=buf.readResourceLocation();
            int count = buf.readVarInt();
            if (count < 0 || count > MAX_INSTANCES) throw new IllegalArgumentException("Invalid village instance count");
            var entries = new ArrayList<InstanceEntry>(count);
            for (int i = 0; i < count; i++) {
                int id=buf.readVarInt();
                if(id<0 || id>=ids.length) throw new IllegalArgumentException("Invalid village template index");
                entries.add(new InstanceEntry(ids[id],readCoordinate(buf),readCoordinate(buf),readCoordinate(buf),
                        buf.readVarInt(),buf.readVarInt(),buf.readVarInt(),buf.readByte(),buf.readByte()));
            }
            return new HumanVillageLayoutSyncPayload(version,entries);
        }
        @Override public void encode(FriendlyByteBuf buf, HumanVillageLayoutSyncPayload value) {
            buf.writeVarInt(value.layoutVersion);
            var templates=new LinkedHashMap<ResourceLocation,Integer>();
            for(var e:value.instances) templates.computeIfAbsent(e.templateId,key->templates.size());
            buf.writeVarInt(templates.size());
            for(var id:templates.keySet()) buf.writeResourceLocation(id);
            buf.writeVarInt(value.instances.size());
            for (var e : value.instances) {
                buf.writeVarInt(templates.get(e.templateId));
                writeCoordinate(buf,e.minX);writeCoordinate(buf,e.minY);writeCoordinate(buf,e.minZ);
                buf.writeVarInt(e.sizeX); buf.writeVarInt(e.sizeY); buf.writeVarInt(e.sizeZ);
                buf.writeByte(e.rotationOrdinal); buf.writeByte(e.paletteThemeId);
            }
        }
    };
    private static int readCoordinate(FriendlyByteBuf buf) { int value=buf.readVarInt();return (value>>>1)^-(value&1); }
    private static void writeCoordinate(FriendlyByteBuf buf,int value) { buf.writeVarInt((value<<1)^(value>>31)); }
    public HumanVillageLayoutSyncPayload {
        if(layoutVersion<1 || layoutVersion>2) throw new IllegalArgumentException("Invalid village layout version");
        instances = List.copyOf(instances);
        if (instances.size() > MAX_INSTANCES) throw new IllegalArgumentException("Too many village instances");
    }
    public record InstanceEntry(ResourceLocation templateId, int minX, int minY, int minZ,
                                int sizeX, int sizeY, int sizeZ, byte rotationOrdinal, byte paletteThemeId) {
        public InstanceEntry {
            if (templateId == null || Math.abs((long) minX) > 29_999_000 || Math.abs((long) minZ) > 29_999_000
                    || minY < -2048 || minY > 2048 || sizeX < 1 || sizeX > 128 || sizeY < 1 || sizeY > 128
                    || sizeZ < 1 || sizeZ > 128 || rotationOrdinal < 0 || rotationOrdinal > 3 || paletteThemeId < -1 || paletteThemeId > 7) {
                throw new IllegalArgumentException("Invalid village instance");
            }
        }
        public static InstanceEntry of(BuildingInstance b) {
            return new InstanceEntry(b.templateId(), b.minX(), b.minY(), b.minZ(), b.sizeX(), b.sizeY(), b.sizeZ(),
                    (byte) b.rotation().ordinal(), (byte) b.paletteThemeId());
        }
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
