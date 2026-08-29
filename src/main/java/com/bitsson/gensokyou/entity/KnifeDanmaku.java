package com.bitsson.gensokyou.entity;

import com.bitsson.gensokyou.registry.ModDamageTypes;
import com.bitsson.gensokyou.registry.ModEntityTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 飞刀型弹幕。
 *
 * <p>特性：长端始终朝向速度方向、穿透所有实体（每个目标只造成一次伤害）、
 * 命中方块才消失、<b>无</b>外发光。
 */
public class KnifeDanmaku extends AbstractDanmakuProjectile {
    /** 已造成伤害的实体，避免同一目标被反复判伤。 */
    private final Set<UUID> hitEntities = new HashSet<>();

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
    protected void onHitBlock(BlockHitResult result) {
        if (this.level() instanceof ServerLevel) {
            this.discard();
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
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.hitEntities.clear();
        ListTag list = tag.getList("HitEntities", Tag.TAG_INT_ARRAY);
        for (int i = 0; i < list.size(); i++) {
            this.hitEntities.add(NbtUtils.loadUUID(list.get(i)));
        }
    }
}
