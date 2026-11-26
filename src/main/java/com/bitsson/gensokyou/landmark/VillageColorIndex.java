package com.bitsson.gensokyou.landmark;

import com.bitsson.gensokyou.network.HumanVillageLayoutSyncPayload.InstanceEntry;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Rotation;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.function.Function;

/** Immutable snapshot, safe for chunk mesh workers while the main thread replaces the index. */
public final class VillageColorIndex {
    private static final Rotation[] ROTATIONS = Rotation.values();
    private final Long2ObjectMap<List<Instance>> buckets;
    private final Set<Section> sections;
    public VillageColorIndex(List<InstanceEntry> entries, Function<ResourceLocation, LocalLandmarkColors> loader) {
        var mutable = new Long2ObjectOpenHashMap<List<Instance>>();
        var affected = new HashSet<Section>();
        for (var e : entries) {
            LocalLandmarkColors colors = loader.apply(e.templateId());
            if (colors == null || colors.sizeX() != e.sizeX() || colors.sizeY() != e.sizeY() || colors.sizeZ() != e.sizeZ()) continue;
            Rotation rotation = ROTATIONS[e.rotationOrdinal()];
            var instance = new Instance(e, colors, rotation, e.minX() + colors.rotatedSizeX(rotation) - 1,
                    e.minZ() + colors.rotatedSizeZ(rotation) - 1);
            for (int x = e.minX() >> 4; x <= instance.maxX >> 4; x++) {
                for (int z = e.minZ() >> 4; z <= instance.maxZ >> 4; z++) {
                    mutable.computeIfAbsent(ChunkPos.asLong(x, z), key -> new ArrayList<>()).add(instance);
                    for(int y=e.minY()>>4;y<=(e.minY()+e.sizeY()-1)>>4;y++) affected.add(new Section(x,y,z));
                }
            }
        }
        mutable.replaceAll((key, value) -> List.copyOf(value));
        buckets = Long2ObjectMaps.unmodifiable(mutable);
        sections = Set.copyOf(affected);
    }
    public int colorAt(int x, int y, int z, ResourceLocation actualBlock) {
        var bucket = buckets.get(ChunkPos.asLong(x >> 4, z >> 4));
        if (bucket == null) return LandmarkColors.NO_TINT;
        for (Instance instance : bucket) {
            var e = instance.entry;
            if (x < e.minX() || x > instance.maxX || z < e.minZ() || z > instance.maxZ || y < e.minY() || y >= e.minY() + e.sizeY()) continue;
            int color = instance.colors.colorAtWorld(x, y, z, e.minX(), e.minY(), e.minZ(), instance.rotation, e.paletteThemeId(), actualBlock);
            if (color != LandmarkColors.NO_TINT) return color;
        }
        return LandmarkColors.NO_TINT;
    }
    public long[] chunkKeys() { return buckets.keySet().toLongArray(); }
    public boolean hasChunk(int x,int z) { return buckets.containsKey(ChunkPos.asLong(x,z)); }
    public Set<Section> sections() { return sections; }
    public record Section(int x,int y,int z) {}
    private record Instance(InstanceEntry entry, LocalLandmarkColors colors, Rotation rotation, int maxX, int maxZ) {}
}
