package com.bitsson.gensokyou.worldgen.human_village;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/** Stores the resolved plan as well as progress; later planner improvements cannot move an existing village. */
public final class HumanVillageSavedData extends SavedData {
    public static final String NAME = "gensokyou_human_village";
    public static final Factory<HumanVillageSavedData> FACTORY = new Factory<>(HumanVillageSavedData::new, HumanVillageSavedData::load, null);
    private HumanVillagePlan plan;
    private final Set<Long> placedChunks = new HashSet<>();
    private final Set<Integer> placedBuildingIds = new HashSet<>();

    public HumanVillagePlan plan() { return plan; }
    public void setPlan(HumanVillagePlan plan) {
        if (this.plan != null) throw new IllegalStateException("Village has already been planned");
        this.plan=plan;setDirty();
    }
    public boolean hasChunk(long pos) { return placedChunks.contains(pos); }
    public int placedChunkCount() { return placedChunks.size(); }
    public int placedBuildingCount() { return placedBuildingIds.size(); }
    public void markChunk(long pos) { if(placedChunks.add(pos)) setDirty(); }
    public void markBuilding(int id) { if(placedBuildingIds.add(id)) setDirty(); }

    public static HumanVillageSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        var data=new HumanVillageSavedData();
        if(!tag.getBoolean("planned")) return data;
        int version=tag.getInt("version");
        if(version<1 || version>HumanVillagePlan.CURRENT_VERSION) throw new IllegalStateException("Unsupported saved Human Village plan version");
        var buildings=new ArrayList<BuildingInstance>();
        ListTag list=tag.getList("buildings",Tag.TAG_COMPOUND);
        if(list.size()>HumanVillagePlan.MAX_BUILDINGS) throw new IllegalStateException("Invalid saved village building count");
        for(int i=0;i<list.size();i++) {
            CompoundTag b=list.getCompound(i);int[] v=b.getIntArray("geometry");
            if(v.length!=16 || v[6]<0 || v[6]>3 || v[15]<0 || v[15]>=BuildingInstance.BuildingCategory.values().length) throw new IllegalStateException("Invalid saved village building");
            buildings.add(new BuildingInstance(ResourceLocation.parse(b.getString("id")),BuildingInstance.BuildingCategory.values()[v[15]],
                    v[0],v[1],v[2],v[3],v[4],v[5],Rotation.values()[v[6]],v[7],v[8],v[9],v[10],v[11]!=0,v[12]!=0,v[13],v[14]));
        }
        var roads=new ArrayList<HumanVillagePlan.RoadCell>();
        int[] cells=tag.getIntArray("roads");
        if(cells.length%5!=0 || cells.length>1_000_000) throw new IllegalStateException("Invalid saved village paving");
        for(int i=0;i<cells.length;i+=5) {
            if(cells[i+3]<0 || cells[i+3]>=HumanVillagePlan.RoadType.values().length) throw new IllegalStateException("Invalid saved paving type");
            roads.add(new HumanVillagePlan.RoadCell(cells[i],cells[i+1],cells[i+2],HumanVillagePlan.RoadType.values()[cells[i+3]],cells[i+4]!=0));
        }
        var protection=new HashSet<Long>();
        if(version==1) {
            for(var b:buildings) for(int x=b.minX();x<=b.maxX();x++) for(int z=b.minZ();z<=b.maxZ();z++) protection.add(HumanVillagePlan.pack(x,z));
            for(var road:roads) protection.add(road.key());
        }
        data.plan=new HumanVillagePlan(version,tag.getLong("worldSeed"),buildings,roads,protection);
        for(long p:tag.getLongArray("placedChunks")) data.placedChunks.add(p);
        for(int id:tag.getIntArray("placedBuildingIds")) if(id>=0 && id<buildings.size()) data.placedBuildingIds.add(id);
        return data;
    }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("version",plan==null?HumanVillagePlan.CURRENT_VERSION:plan.layoutVersion());tag.putBoolean("planned",plan!=null);
        if(plan==null) return tag;
        tag.putLong("worldSeed",plan.worldSeed());
        var list=new ListTag();
        for(var b:plan.buildings()) {
            var entry=new CompoundTag();entry.putString("id",b.templateId().toString());
            entry.putIntArray("geometry",new int[]{b.minX(),b.minY(),b.minZ(),b.sizeX(),b.sizeY(),b.sizeZ(),b.rotation().ordinal(),b.paletteThemeId(),
                    b.doorWorldX(),b.doorWorldY(),b.doorWorldZ(),b.hasDoorAnchor()?1:0,b.fixed()?1:0,b.clearance(),b.foundationDepth(),b.category().ordinal()});
            list.add(entry);
        }
        tag.put("buildings",list);
        int[] roads=new int[plan.roadCells().size()*5];int i=0;
        for(var c:plan.roadCells()) {roads[i++]=c.x();roads[i++]=c.surfaceY();roads[i++]=c.z();roads[i++]=c.type().ordinal();roads[i++]=c.fixed()?1:0;}
        tag.putIntArray("roads",roads);
        tag.putLongArray("placedChunks",placedChunks.stream().mapToLong(Long::longValue).sorted().toArray());
        tag.putIntArray("placedBuildingIds",placedBuildingIds.stream().mapToInt(Integer::intValue).sorted().toArray());
        return tag;
    }
}
