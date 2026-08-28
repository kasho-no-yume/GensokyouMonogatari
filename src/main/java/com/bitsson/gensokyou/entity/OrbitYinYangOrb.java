package com.bitsson.gensokyou.entity;

import com.bitsson.gensokyou.config.GensokyouConfig;
import com.bitsson.gensokyou.registry.ModDamageTypes;
import com.bitsson.gensokyou.registry.ModEntityTypes;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.Optional;
import java.util.UUID;

public class OrbitYinYangOrb extends Entity {
    private static final String TAG_HOST = "Host";
    private static final String TAG_RADIUS = "Radius";
    private static final String TAG_ANGULAR_SPEED = "AngularSpeed";
    private static final String TAG_TICKS_LEFT = "TicksLeft";
    private static final String TAG_DAMAGE_CAP = "DamageCap";
    private static final String TAG_INITIAL_ANGLE = "InitialAngle";

    private static final EntityDataAccessor<Optional<UUID>> DATA_HOST =
            SynchedEntityData.defineId(OrbitYinYangOrb.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Float> DATA_RADIUS =
            SynchedEntityData.defineId(OrbitYinYangOrb.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_ANGULAR_SPEED =
            SynchedEntityData.defineId(OrbitYinYangOrb.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_INITIAL_ANGLE =
            SynchedEntityData.defineId(OrbitYinYangOrb.class, EntityDataSerializers.FLOAT);

    private int ticksLeft;
    private float damageCap = 20F;

    public OrbitYinYangOrb(EntityType<? extends OrbitYinYangOrb> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setInvulnerable(true);
    }

    public OrbitYinYangOrb(Level level, Player host, int index, double radius, double angularSpeed,
                            int durationTicks, float damageCap) {
        this(ModEntityTypes.ORBIT_YIN_YANG_ORB.get(), level);
        this.ticksLeft = durationTicks;
        this.damageCap = damageCap;
        entityData.set(DATA_HOST, Optional.of(host.getUUID()));
        entityData.set(DATA_RADIUS, (float) radius);
        entityData.set(DATA_ANGULAR_SPEED, (float) angularSpeed);
        entityData.set(DATA_INITIAL_ANGLE, (float) (index * (Math.PI / 3D)));
        setOrbitPosition(tickCount);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_HOST, Optional.empty());
        builder.define(DATA_RADIUS, 2F);
        builder.define(DATA_ANGULAR_SPEED, (float) (Math.PI / 30D));
        builder.define(DATA_INITIAL_ANGLE, 0F);
    }

    @Override
    public void tick() {
        super.tick();
        if (level() instanceof ServerLevel serverLevel) {
            Player host = resolveHost(serverLevel);
            if (host == null || !host.isAlive()) {
                discard();
                return;
            }
            setOrbitPosition(tickCount);
            ticksLeft--;
            if (ticksLeft <= 0) {
                resolveDamage(serverLevel, host, 1.0F);
                discard();
                return;
            }
            if (ticksLeft % pulseIntervalTicks() == 0) {
                resolveDamage(serverLevel, host, pulseFactor());
            }
        } else {
            if (tickCount % 3 == 0) {
                level().addParticle(ParticleTypes.END_ROD,
                        getX(), getY() + 0.2D, getZ(), 0D, 0D, 0D);
            }
            Player host = resolveHost(level());
            if (host != null) {
                setOrbitPosition(tickCount);
            }
        }
    }

    private Player resolveHost(Level level) {
        return entityData.get(DATA_HOST).map(level::getPlayerByUUID).orElse(null);
    }

    private void setOrbitPosition(int age) {
        double angle = age * entityData.get(DATA_ANGULAR_SPEED) + entityData.get(DATA_INITIAL_ANGLE);
        double radius = entityData.get(DATA_RADIUS);
        Player host = resolveHost(level());
        if (host == null) {
            return;
        }
        setPos(host.getX() + radius * Math.cos(angle),
                host.getY() + 1.0D,
                host.getZ() + radius * Math.sin(angle));
    }

    private int pulseIntervalTicks() {
        return GensokyouConfig.MUSOU_PULSE_INTERVAL.get();
    }

    private float pulseFactor() {
        return GensokyouConfig.MUSOU_PULSE_FACTOR.get().floatValue();
    }

    private void resolveDamage(ServerLevel level, Player host, float factor) {
        AABB box = host.getBoundingBox().inflate(entityData.get(DATA_RADIUS) + 1.5D);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, box,
                e -> e != host && e instanceof Enemy)) {
            float full = Math.min(target.getMaxHealth() / 2F, damageCap);
            target.hurt(ModDamageTypes.danmaku(target, host), Math.max(1F, full * factor));
        }
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        Optional<UUID> host = entityData.get(DATA_HOST);
        host.ifPresent(uuid -> tag.putUUID(TAG_HOST, uuid));
        tag.putDouble(TAG_RADIUS, entityData.get(DATA_RADIUS));
        tag.putDouble(TAG_ANGULAR_SPEED, entityData.get(DATA_ANGULAR_SPEED));
        tag.putInt(TAG_TICKS_LEFT, ticksLeft);
        tag.putFloat(TAG_DAMAGE_CAP, damageCap);
        tag.putDouble(TAG_INITIAL_ANGLE, entityData.get(DATA_INITIAL_ANGLE));
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID(TAG_HOST)) {
            entityData.set(DATA_HOST, Optional.of(tag.getUUID(TAG_HOST)));
        }
        entityData.set(DATA_RADIUS, tag.getFloat(TAG_RADIUS));
        entityData.set(DATA_ANGULAR_SPEED, tag.getFloat(TAG_ANGULAR_SPEED));
        this.ticksLeft = tag.getInt(TAG_TICKS_LEFT);
        this.damageCap = tag.getFloat(TAG_DAMAGE_CAP);
        entityData.set(DATA_INITIAL_ANGLE, tag.getFloat(TAG_INITIAL_ANGLE));
    }
}
