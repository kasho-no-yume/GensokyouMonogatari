package com.bitsson.gensokyou.entity;

import com.bitsson.gensokyou.config.GensokyouConfig;
import com.bitsson.gensokyou.registry.ModDamageTypes;
import com.bitsson.gensokyou.registry.ModEntityTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 飞刀型弹幕。
 *
 * <p>特性：长端始终朝向速度方向、穿透所有实体（每个目标只造成一次伤害）、
 * 命中方块后如箭般插驻在墙上（持续 {@code knifeStickTicks}，0 = 立即消失）、
 * <b>无</b>外发光。
 */
public class KnifeDanmaku extends AbstractDanmakuProjectile {
    /** 刀尖到实体锚点的距离：插墙时让刀尖压入墙面（渲染几何的 BLADE_TIP_Z 对应值）。 */
    private static final double TIP_OFFSET = 0.6D;
    /** 刀尖入墙深度。 */
    private static final double EMBED_DEPTH = 0.15D;

    /** 已造成伤害的实体，避免同一目标被反复判伤。 */
    private final Set<UUID> hitEntities = new HashSet<>();

    /** 插墙标记：走 SynchedEntityData，客户端即使漏判命中也能冻结。 */
    private static final EntityDataAccessor<Boolean> DATA_STUCK =
            SynchedEntityData.defineId(KnifeDanmaku.class, EntityDataSerializers.BOOLEAN);

    /** 插驻剩余 tick（仅服务端计时，客户端靠移除包同步消失）。 */
    private int stickRemaining;

    /** 插驻锚点：撤销命中 tick 的惯性推进后钉回的位置。 */
    private Vec3 anchor = Vec3.ZERO;

    public KnifeDanmaku(EntityType<? extends KnifeDanmaku> type, Level level) {
        super(type, level);
    }

    public KnifeDanmaku(Level level, LivingEntity owner, float damage, Set<EntityType<?>> whitelist) {
        super(ModEntityTypes.KNIFE_DANMAKU.get(), owner, level);
        this.damage = damage;
        this.setWhitelist(whitelist);
        this.setColor(0xFFFFFF);
    }

    /**
     * 已经打过的目标不再参与命中判定，否则飞刀会卡在第一个目标上反复触发。
     */
    @Override
    protected boolean canHitEntity(Entity target) {
        return super.canHitEntity(target) && !this.hitEntities.contains(target.getUUID());
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        Entity hit = result.getEntity();

        // 双端都要记录，保证客户端不会重复触发命中逻辑
        this.hitEntities.add(hit.getUUID());

        if (!(this.level() instanceof ServerLevel)) {
            return;
        }
        if (this.isWhitelisted(hit)) {
            return;
        }
        if (hit.isAlive()) {
            hit.hurt(ModDamageTypes.danmaku(hit, this.getOwner()), this.damage);
        }
        // 不 discard：飞刀继续穿透
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_STUCK, false);
    }

    public boolean isStuck() {
        return this.entityData.get(DATA_STUCK);
    }

    @Override
    public void tick() {
        if (this.isStuck()) {
            // 冻结：不推进、不判伤；仅服务端计时，到点移除（客户端由移除包同步）
            if (!this.level().isClientSide && --this.stickRemaining <= 0) {
                this.discard();
            }
            return;
        }
        super.tick();
        if (this.isStuck()) {
            // 命中发生在本次 super.tick() 内：末尾仍会用命中前捕获的速度推进一格，
            // 把刀推进墙里（表现为"消失"）——撤销该次推进，钉回嵌入锚点
            this.setPos(this.anchor.x, this.anchor.y, this.anchor.z);
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        int ticks = GensokyouConfig.KNIFE_STICK_TICKS.get();
        if (ticks <= 0) {
            this.discard();
            return;
        }
        // 沿飞行方向把刀尖压入墙面：锚点 = 命中点 - 飞行方向 * (刀尖偏移 - 入墙深度)
        Vec3 dir = this.getDeltaMovement();
        double speed = dir.length();
        if (speed > 1.0E-4D) {
            Vec3 normalized = dir.scale(1.0D / speed);
            this.anchor = result.getLocation().add(normalized.scale(-(TIP_OFFSET - EMBED_DEPTH)));
        } else {
            this.anchor = this.position();
        }
        this.setPos(this.anchor.x, this.anchor.y, this.anchor.z);
        this.setDeltaMovement(Vec3.ZERO);
        this.entityData.set(DATA_STUCK, true);
        if (this.level() instanceof ServerLevel) {
            this.stickRemaining = ticks;
            this.hurtMarked = true;   // 立即把零速同步给客户端
        }
    }

    @Override
    public boolean hasGlowEffect() {
        return false;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        ListTag list = new ListTag();
        for (UUID uuid : this.hitEntities) {
            list.add(NbtUtils.createUUID(uuid));
        }
        tag.put("HitEntities", list);
        tag.putBoolean("Stuck", this.isStuck());
        tag.putInt("StickRemaining", this.stickRemaining);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.hitEntities.clear();
        ListTag list = tag.getList("HitEntities", Tag.TAG_INT_ARRAY);
        for (int i = 0; i < list.size(); i++) {
            this.hitEntities.add(NbtUtils.loadUUID(list.get(i)));
        }
        this.stickRemaining = tag.getInt("StickRemaining");
        if (tag.getBoolean("Stuck")) {
            // 重载后位置即锚点，恢复插驻标记供客户端渲染冻结
            this.entityData.set(DATA_STUCK, true);
        }
    }
}
