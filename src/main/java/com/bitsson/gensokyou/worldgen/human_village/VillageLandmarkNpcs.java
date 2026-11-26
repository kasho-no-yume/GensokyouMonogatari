package com.bitsson.gensokyou.worldgen.human_village;

import com.bitsson.gensokyou.entity.VillageNpcEntity;
import com.bitsson.gensokyou.landmark.HakureiShrineLandmark;
import com.bitsson.gensokyou.quest.QuestSnapshot;
import com.bitsson.gensokyou.registry.ModEntityTypes;
import com.bitsson.gensokyou.village.VillageRole;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;

import java.util.Optional;
import java.util.UUID;

/** Entity-section receipts, not proximity to the spawn point, establish absence of a walking owner. */
public final class VillageLandmarkNpcs {
    public static void ensure(ServerLevel level, HumanVillageSavedData data) {
        if (data.plan() == null) return;
        for (int id = 0; id < data.plan().buildings().size(); id++) {
            var building = data.plan().buildings().get(id);
            for (VillageRole role : VillageRole.values()) {
                if (!building.templateId().equals(role.template()) || !AkyuuResidence.complete(data, id)) continue;
                UUID receipt = data.landmarkNpc(id);
                if (receipt == null && role == VillageRole.AKYUU) receipt = data.akyuuUuid();
                if (receipt != null && level.getEntity(receipt) instanceof VillageNpcEntity owner && owner.villageRole() == role) {
                    owner.bindResidence(building, id); record(data, id, owner); continue;
                }
                AABB room = role.room(building.placementPivot(), building.rotation()).inflate(1, 3, 1);
                boolean loaded = true;
                for (int x = (int) Math.floor(room.minX) >> 4; x <= (int) Math.floor(room.maxX) >> 4; x++)
                    for (int z = (int) Math.floor(room.minZ) >> 4; z <= (int) Math.floor(room.maxZ) >> 4; z++)
                        if (level.getChunkSource().getChunkNow(x, z) == null || !level.areEntitiesLoaded(ChunkPos.asLong(x, z))) loaded = false;
                if (!loaded) continue;
                final int homeId = id;
                var found = level.getEntitiesOfClass(VillageNpcEntity.class, room,
                        n -> n.villageRole() == role && (n.residenceId() == homeId || n.residenceId() < 0));
                if (!found.isEmpty()) {
                    var owner = found.getFirst(); owner.bindResidence(building, id); record(data, id, owner); continue;
                }
                VillageNpcEntity owner = switch (role) {
                    case RINNOSUKE -> ModEntityTypes.RINNOSUKE.get().create(level);
                    case MIYOI -> ModEntityTypes.MIYOI.get().create(level);
                    case KOSUZU -> ModEntityTypes.KOSUZU.get().create(level);
                    case AKYUU -> ModEntityTypes.AKYUU.get().create(level);
                };
                if (owner == null) continue;
                owner.bindResidence(building, id);
                owner.setYRot(role.yaw(building.rotation())); owner.setYHeadRot(owner.getYRot());
                if (!level.noCollision(owner, owner.getBoundingBox())) continue;
                if (level.addFreshEntity(owner)) record(data, id, owner);
            }
        }
    }
    private static void record(HumanVillageSavedData data, int id, VillageNpcEntity owner) {
        data.setLandmarkNpc(id, owner.getUUID());
        if (owner.villageRole() == VillageRole.AKYUU) data.setAkyuuUuid(owner.getUUID());
    }
    public static Optional<QuestSnapshot.Destination> destination(MinecraftServer server, ResourceLocation roleId, String label) {
        var level = server.getLevel(HakureiShrineLandmark.GENSOKYO_DIMENSION);
        if (level == null) return Optional.empty();
        var data = level.getDataStorage().computeIfAbsent(HumanVillageSavedData.FACTORY, HumanVillageSavedData.NAME);
        if (data.plan() == null) return Optional.empty();
        for (VillageRole role : VillageRole.values()) if (role.id().equals(roleId))
            return data.plan().buildings().stream().filter(b -> b.templateId().equals(role.template())).findFirst().map(b -> {
                var p = role.anchor(b);
                return new QuestSnapshot.Destination(level.dimension().location(), p.x, p.y + 1, p.z, label);
            });
        return Optional.empty();
    }
    private VillageLandmarkNpcs() {}
}
