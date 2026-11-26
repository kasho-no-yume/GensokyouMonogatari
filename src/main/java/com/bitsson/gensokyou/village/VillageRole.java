package com.bitsson.gensokyou.village;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.worldgen.human_village.BuildingInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Authoritative template-local ground-floor rooms; furniture is handled by real navigation. */
public enum VillageRole {
    RINNOSUKE("rinnosuke", "hv_kourindou", new BlockPos(19, 1, 8), 7, 6, 31, 24, "think", 0),
    MIYOI("miyoi", "hv_geidontei", new BlockPos(24, 1, 17), 23, 11, 35, 24, "serve", 90),
    KOSUZU("kosuzu", "hv_suzunaan", new BlockPos(22, 1, 16), 18, 7, 23, 26, "read", -90),
    AKYUU("akyuu", "hv_hieda_residence", new BlockPos(38, 2, 12), 26, 10, 41, 24, "write", 0);

    private final String name, template, occupation;
    private final BlockPos feet;
    private final int minX, minZ, maxX, maxZ;
    private final float facing;

    VillageRole(String name, String template, BlockPos feet, int minX, int minZ, int maxX, int maxZ,
                String occupation, float facing) {
        this.name = name; this.template = template; this.feet = feet;
        this.minX = minX; this.minZ = minZ; this.maxX = maxX; this.maxZ = maxZ;
        this.occupation = occupation; this.facing = facing;
    }
    public String key() { return name; }
    public ResourceLocation id() { return Gensokyou.id(name); }
    public ResourceLocation template() { return Gensokyou.id(template); }
    public BlockPos localFeet() { return feet; }
    public String occupation() { return occupation; }
    public boolean shop() { return this != AKYUU; }
    public Vec3 anchor(BuildingInstance building) {
        return Vec3.atBottomCenterOf(building.placementPivot().offset(feet.rotate(building.rotation())));
    }
    public AABB room(BlockPos pivot, Rotation rotation) {
        BlockPos a = pivot.offset(new BlockPos(minX, feet.getY(), minZ).rotate(rotation));
        BlockPos b = pivot.offset(new BlockPos(maxX, feet.getY(), maxZ).rotate(rotation));
        return new AABB(Math.min(a.getX(), b.getX()) + .35, a.getY() - .05,
                Math.min(a.getZ(), b.getZ()) + .35, Math.max(a.getX(), b.getX()) + .65,
                a.getY() + .65, Math.max(a.getZ(), b.getZ()) + .65);
    }
    public float yaw(Rotation rotation) {
        return facing + switch (rotation) {
            case NONE -> 0; case CLOCKWISE_90 -> 90; case CLOCKWISE_180 -> 180; case COUNTERCLOCKWISE_90 -> -90;
        };
    }
}
