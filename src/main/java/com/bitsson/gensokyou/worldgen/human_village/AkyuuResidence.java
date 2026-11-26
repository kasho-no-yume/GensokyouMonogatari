package com.bitsson.gensokyou.worldgen.human_village;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.entity.AkyuuEntity;
import com.bitsson.gensokyou.landmark.HakureiShrineLandmark;
import com.bitsson.gensokyou.quest.QuestSnapshot;
import com.bitsson.gensokyou.registry.ModEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/** Uses the saved building geometry and placement receipts, including already-populated legacy chunks. */
public final class AkyuuResidence {
    public static final ResourceLocation ROLE = Gensokyou.id("akyuu");
    public static final ResourceLocation TEMPLATE = Gensokyou.id("hv_hieda_residence");
    // Beside the writing desk, behind the reception aisle. Floor y=1, feet y=2; both layouts share this template.
    public static final BlockPos LOCAL_FEET = new BlockPos(38, 2, 12);
    public static Vec3 anchor(BuildingInstance building) {
        return Vec3.atBottomCenterOf(building.placementPivot().offset(LOCAL_FEET.rotate(building.rotation())));
    }
    public static float yaw(BuildingInstance building) {
        return switch (building.rotation()) { case NONE -> 0F; case CLOCKWISE_90 -> 90F; case CLOCKWISE_180 -> 180F; case COUNTERCLOCKWISE_90 -> -90F; };
    }
    public static boolean complete(HumanVillageSavedData data, int id) {
        if (data.hasBuilding(id)) return true;
        var b = data.plan().buildings().get(id);
        for (int x = b.minX() >> 4; x <= b.maxX() >> 4; x++) for (int z = b.minZ() >> 4; z <= b.maxZ() >> 4; z++)
            if (!data.hasChunk(ChunkPos.asLong(x, z))) return false;
        data.markBuilding(id);
        return true;
    }
    public static void ensure(ServerLevel level, HumanVillageSavedData data) {
        if (data.plan() == null) return;
        for (int id = 0; id < data.plan().buildings().size(); id++) {
            var b = data.plan().buildings().get(id);
            if (!b.templateId().equals(TEMPLATE) || !complete(data, id)) continue;
            Vec3 position = anchor(b);
            var pos = BlockPos.containing(position);
            // Entity sections must finish loading before absence is meaningful. Never request an adjacent chunk.
            if (level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) == null
                    || !level.areEntitiesLoaded(ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4))) continue;
            final int buildingId = id;
            var residents = level.getEntitiesOfClass(AkyuuEntity.class, new AABB(pos).inflate(3), n -> n.residenceId() == buildingId);
            if (!residents.isEmpty()) { data.setAkyuuUuid(residents.getFirst().getUUID()); continue; }
            if (data.akyuuUuid() != null && level.getEntity(data.akyuuUuid()) instanceof AkyuuEntity) continue;
            var npc = ModEntityTypes.AKYUU.get().create(level);
            if (npc == null) continue;
            npc.setResidenceId(id);
            npc.setAnchorPos(position);
            npc.setYRot(yaw(b)); npc.setYHeadRot(yaw(b));
            if (level.addFreshEntity(npc)) data.setAkyuuUuid(npc.getUUID());
        }
    }
    public static Optional<QuestSnapshot.Destination> destination(MinecraftServer server, ResourceLocation role, String label) {
        if (!ROLE.equals(role)) return Optional.empty();
        var level = server.getLevel(HakureiShrineLandmark.GENSOKYO_DIMENSION);
        if (level == null) return Optional.empty();
        var data = level.getDataStorage().computeIfAbsent(HumanVillageSavedData.FACTORY, HumanVillageSavedData.NAME);
        if (data.plan() == null) return Optional.empty();
        return data.plan().buildings().stream().filter(b -> b.templateId().equals(TEMPLATE)).findFirst().map(b -> {
            Vec3 pos = anchor(b);
            return new QuestSnapshot.Destination(level.dimension().location(), pos.x, pos.y + 1D, pos.z, label);
        });
    }
    private AkyuuResidence() {}
}
