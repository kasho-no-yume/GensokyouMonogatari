package com.bitsson.gensokyou.entity.spell;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.config.GensokyouConfig;
import com.bitsson.gensokyou.entity.AbstractTouhouBoss;
import com.bitsson.gensokyou.registry.ModEntityTypes;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 網符『蜘蛛の巣』：立方体网域。域内所有实体（含 BOSS）移速 ×mult（默认 0.2，绝不为 0）；
 * 离开或到期恢复。BOSS 另走自定义减速开关（不走原版 Slowness）。
 */
public class WebFieldEntity extends AbstractSpellFieldEntity {

    private static final String TAG_EDGE = "Edge";
    private static final String TAG_MULT = "Mult";
    private static final String TAG_CASTER = "Caster";

    private float edge = 6F;
    private double speedMult = 0.2D;
    /** 施放者：网域不减速自己。 */
    private java.util.UUID casterId;
    private final Set<UUID> affected = new HashSet<>();

    public WebFieldEntity(EntityType<? extends WebFieldEntity> type, Level level) {
        super(type, level);
    }

    /** 立方体边长（渲染大网用）。 */
    public float edge() {
        return edge;
    }

    public WebFieldEntity(Level level, java.util.UUID casterId, double x, double y, double z,
                          float edge, int durationTicks, double speedMult) {
        this(ModEntityTypes.WEB_FIELD.get(), level);
        setPos(x, y, z);
        setRadius(edge * 0.5F);
        this.ticksLeft = durationTicks;
        this.edge = edge;
        this.speedMult = speedMult;
        this.casterId = casterId;
    }

    private ResourceLocation modifierId() {
        return Gensokyou.id("spell_web_slow/" + getUUID());
    }

    private AABB fieldBox() {
        double h = edge * 0.5D;
        return new AABB(getX() - h, getY() - h, getZ() - h, getX() + h, getY() + h, getZ() + h);
    }

    @Override
    protected void serverTick(ServerLevel level) {
        AABB box = fieldBox();
        List<LivingEntity> inside = level.getEntitiesOfClass(LivingEntity.class, box,
                e -> e.isAlive() && (casterId == null || !casterId.equals(e.getUUID())));
        Set<UUID> nowInside = new HashSet<>();
        for (LivingEntity entity : inside) {
            nowInside.add(entity.getUUID());
            if (affected.add(entity.getUUID())) {
                applySlow(entity);
            }
            if (entity instanceof AbstractTouhouBoss boss) {
                boss.applySpellSlow(5, speedMult);
            }
        }
        // 离开网域者移除修饰符
        affected.removeIf(uuid -> {
            if (nowInside.contains(uuid)) {
                return false;
            }
            removeSlow(level, uuid);
            return true;
        });
    }

    private void applySlow(LivingEntity entity) {
        AttributeInstance speed = entity.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        speed.addOrUpdateTransientModifier(new AttributeModifier(
                modifierId(), speedMult - 1.0D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    private void removeSlow(ServerLevel level, UUID uuid) {
        if (level.getEntity(uuid) instanceof LivingEntity living) {
            AttributeInstance speed = living.getAttribute(Attributes.MOVEMENT_SPEED);
            if (speed != null) {
                speed.removeModifier(modifierId());
            }
        }
    }

    @Override
    protected void onExpire(ServerLevel level) {
        for (UUID uuid : affected) {
            removeSlow(level, uuid);
        }
        affected.clear();
    }

    @Override
    protected void clientTick() {
        double h = edge * 0.5D;
        if (tickCount % 3 == 0) {
            double x = getX() + (random.nextDouble() - 0.5D) * edge;
            double y = getY() + (random.nextDouble() - 0.5D) * edge;
            double z = getZ() + (random.nextDouble() - 0.5D) * edge;
            level().addParticle(ParticleTypes.WHITE_ASH, x, y, z, 0D, -0.01D, 0D);
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat(TAG_EDGE, edge);
        tag.putDouble(TAG_MULT, speedMult);
        if (casterId != null) {
            tag.putUUID(TAG_CASTER, casterId);
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.edge = tag.getFloat(TAG_EDGE);
        this.speedMult = tag.getDouble(TAG_MULT);
        this.casterId = tag.hasUUID(TAG_CASTER) ? tag.getUUID(TAG_CASTER) : null;
    }
}
