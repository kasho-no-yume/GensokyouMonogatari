package com.bitsson.gensokyou.entity.spell;

import com.bitsson.gensokyou.registry.ModEntityTypes;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 網符『蜘蛛の巣』的丝弹：朝准星前方飞行 {@code distance} 格后炸开立方体网域。
 * 纯投放载体，不造成伤害。
 */
public class SilkProjectileEntity extends Entity {

    private Vec3 dir = Vec3.ZERO;
    private double speed = 1.2D;
    private double travelled;
    private double totalDistance = 6D;
    private float edge = 6F;
    private int fieldDurationTicks;
    private double speedMult = 0.2D;
    private java.util.UUID ownerId;

    public SilkProjectileEntity(EntityType<? extends SilkProjectileEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setInvulnerable(true);
    }

    public SilkProjectileEntity(Level level, Player owner, Vec3 dir, double distance,
                                float edge, int fieldDurationTicks, double speedMult) {
        this(ModEntityTypes.SILK_PROJECTILE.get(), level);
        setPos(owner.getX(), owner.getEyeY(), owner.getZ());
        this.dir = dir.normalize();
        this.totalDistance = Math.max(1D, distance);
        this.edge = edge;
        this.fieldDurationTicks = fieldDurationTicks;
        this.speedMult = speedMult;
        this.ownerId = owner.getUUID();
    }

    @Override
    public void tick() {
        super.tick();
        if (level() instanceof ServerLevel serverLevel) {
            Vec3 step = dir.scale(speed);
            setPos(getX() + step.x, getY() + step.y, getZ() + step.z);
            travelled += speed;
            if (travelled >= totalDistance) {
                WebFieldEntity field = new WebFieldEntity(serverLevel, ownerId, getX(), getY(), getZ(),
                        edge, fieldDurationTicks, speedMult);
                serverLevel.addFreshEntity(field);
                discard();
            }
        } else if (tickCount % 2 == 0) {
            level().addParticle(ParticleTypes.WHITE_ASH, getX(), getY(), getZ(), 0D, 0D, 0D);
        }
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putDouble("DirX", dir.x);
        tag.putDouble("DirY", dir.y);
        tag.putDouble("DirZ", dir.z);
        tag.putDouble("Speed", speed);
        tag.putDouble("Travelled", travelled);
        tag.putDouble("Total", totalDistance);
        tag.putFloat("Edge", edge);
        tag.putInt("FieldTicks", fieldDurationTicks);
        tag.putDouble("Mult", speedMult);
        if (ownerId != null) {
            tag.putUUID("Owner", ownerId);
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.dir = new Vec3(tag.getDouble("DirX"), tag.getDouble("DirY"), tag.getDouble("DirZ"));
        this.speed = tag.getDouble("Speed");
        this.travelled = tag.getDouble("Travelled");
        this.totalDistance = tag.getDouble("Total");
        this.edge = tag.getFloat("Edge");
        this.fieldDurationTicks = tag.getInt("FieldTicks");
        this.speedMult = tag.getDouble("Mult");
        this.ownerId = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }
}
