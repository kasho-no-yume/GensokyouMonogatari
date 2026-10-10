package com.bitsson.gensokyou.block.entity;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.config.GensokyouConfig;
import com.bitsson.gensokyou.registry.ModBlockEntities;
import com.bitsson.gensokyou.spirit.ModAttachments;
import com.bitsson.gensokyou.spirit.SpiritPowerData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * 灵力引爆器的方块实体：承载「沉睡 / 待爆」两态、可调参数（起爆时间 / 强度 / 半径）
 * 与玩家灵力扣费，并在倒计时结束时触发全掉落爆炸。
 *
 * <p><b>为什么是方块而不是实体</b>：原先用纯 {@code Entity} 实现，但实体天然无法阻挡
 * 玩家移动（1.21 的玩家移动碰撞只查方块），也 {@code isPickable()==false}，准星无法选中，
 * 右键永远开不了界面。改成方块 + {@code BlockEntity} 后，碰撞箱、方块模型贴图、
 * {@link com.bitsson.gensokyou.block.SpiritBombBlock} 的右键开界面全部天然成立。
 *
 * <p>计时由 {@link #serverTick} 承担：只认「已启动」，与放置者是否在线无关；区块被卸载时
 * 计时暂停（与原版实体同构，非本实现的取舍）。
 */
public class SpiritBombBlockEntity extends BlockEntity {

    public static final int STATE_DORMANT = 0;
    public static final int STATE_ARMED = 1;

    private static final String TAG_STATE = "BombState";
    private static final String TAG_FUSE = "FuseTicks";
    private static final String TAG_POWER = "Power";
    private static final String TAG_RADIUS = "BlastRadius";
    private static final String TAG_OWNER = "Owner";

    private int bombState = STATE_DORMANT;
    private int fuseTicks;
    private float power = 4.0F;
    private int blastRadius = 6;
    @Nullable
    private UUID owner;

    public SpiritBombBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SPIRIT_BOMB.get(), pos, state);
        this.fuseTicks = GensokyouConfig.SPIRIT_BOMB_DEFAULT_FUSE_TICKS.get();
    }

    // ------------------------------------------------------------------ 参数访问

    public int getBombState() {
        return bombState;
    }

    public boolean isArmed() {
        return bombState == STATE_ARMED;
    }

    public int getFuseTicks() {
        return fuseTicks;
    }

    public void setFuseTicks(int ticks) {
        this.fuseTicks = Math.max(0, ticks);
        setChanged();
    }

    public float getPower() {
        return power;
    }

    public void setPower(float value) {
        this.power = (float) clamp(value, 1.0D, 12.0D);
        setChanged();
    }

    public int getBlastRadius() {
        return blastRadius;
    }

    public void setBlastRadius(int value) {
        this.blastRadius = (int) clamp(value, 1, 24);
        setChanged();
    }

    @Nullable
    public UUID getOwner() {
        return owner;
    }

    public void setOwner(@Nullable UUID owner) {
        this.owner = owner;
        setChanged();
    }

    // ------------------------------------------------------------------ 启动

    /**
     * 尝试启动：从启动者灵力池扣费，不足则拒绝。
     *
     * <p><b>全有全无</b>：先比 {@code current} 再扣，同口径，不会出现「显示够、扣下去失败」。
     */
    public boolean arm(ServerPlayer player) {
        if (isArmed()) {
            return false;
        }
        long cost = estimatedCost();
        SpiritPowerData data = ModAttachments.get(player);
        if (data.current() < cost) {
            return false;
        }
        ModAttachments.set(player, data.withAddedCurrent(-cost));
        setOwner(player.getUUID());
        this.bombState = STATE_ARMED;
        setChanged();
        syncToClients();
        return true;
    }

    /** 估算灵力消耗：{@code BASE × (强度/4)² × (半径/6)}。 */
    public long estimatedCost() {
        long base = GensokyouConfig.SPIRIT_BOMB_BASE_COST.get();
        double powerFactor = Math.pow(power / 4.0D, 2);
        double radiusFactor = blastRadius / 6.0D;
        return Math.round(base * powerFactor * radiusFactor);
    }

    // ------------------------------------------------------------------ tick

    /** 服务端计时器：仅「已启动」推进，到点引爆。 */
    public static void serverTick(Level level, BlockPos pos, BlockState state,
                                  SpiritBombBlockEntity be) {
        if (level.isClientSide || !be.isArmed()) {
            return;
        }
        if (be.fuseTicks <= 0) {
            be.detonate();
            return;
        }
        be.fuseTicks--;
        be.setChanged();
    }

    private void detonate() {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        // 先无声移除自身方块（避免爆炸把它当普通方块重复掉落），再在方块中心引爆
        Vec3 center = worldPosition.getCenter();
        server.removeBlock(worldPosition, false);
        server.explode(null, center.x, center.y, center.z, power,
                Level.ExplosionInteraction.BLOCK);
        applyRadiusDamage(server, center);
        capDroppedProducts(server, center);
        // 引爆器是可重复使用的设备：起爆后本体掉回物品（爆炸之后再放，避免被炸飞/炸没）
        server.addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(server,
                center.x, center.y + 0.25D, center.z,
                new net.minecraft.world.item.ItemStack(
                        com.bitsson.gensokyou.registry.ModItems.SPIRIT_BOMB.get())));
    }

    /**
     * 掉落物实体数上限保护。
     *
     * <p>{@code ExplosionInteraction.BLOCK} 会让每个被毁方块各生成一个物品实体，
     * 大半径一次可产数千个，直接卡死服务端。超限部分在同一 tick 内丢弃并告警：
     * 保命优先于保物资。
     */
    private void capDroppedProducts(ServerLevel server, Vec3 center) {
        int cap = GensokyouConfig.SPIRIT_BOMB_MAX_PRODUCTS.get();
        AABB area = new AABB(center, center).inflate(blastRadius + 4.0D);
        var items = server.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, area);
        if (items.size() <= cap) {
            return;
        }
        int culled = 0;
        for (var item : items) {
            if (items.size() - culled <= cap) {
                break;
            }
            item.discard();
            culled++;
        }
        Gensokyou.LOGGER.warn("Spirit bomb at {} produced {} item entities; culled {} over the cap of {}",
                worldPosition, items.size(), culled, cap);
    }

    /**
     * 半径的二次伤害：原版 {@code explode(power)} 的破坏范围只由 power 决定，
     * 没有独立半径参数，故按 radius 补一段伤害与击退，兑现「强度与半径分开调」。
     */
    private void applyRadiusDamage(ServerLevel level, Vec3 center) {
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class,
                new AABB(center, center).inflate(blastRadius))) {
            double distance = target.position().distanceTo(center);
            if (distance > blastRadius) {
                continue;
            }
            float falloff = (float) (1.0D - distance / Math.max(1, blastRadius));
            target.hurt(level.damageSources().explosion(null, ownerEntity(level)),
                    power * 2.0F * falloff);
            Vec3 knock = target.position().subtract(center).normalize().scale(falloff * 1.5D);
            target.push(knock.x, Math.max(0.2D, knock.y), knock.z);
        }
    }

    @Nullable
    private Entity ownerEntity(ServerLevel level) {
        return owner == null ? null : level.getEntity(owner);
    }

    // ------------------------------------------------------------------ 同步

    /** 参数变化后推给客户端，供 GUI 回显与将来的倒计时特效读取。 */
    public void syncToClients() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    // ------------------------------------------------------------------ 持久化

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt(TAG_STATE, bombState);
        tag.putInt(TAG_FUSE, fuseTicks);
        tag.putFloat(TAG_POWER, power);
        tag.putInt(TAG_RADIUS, blastRadius);
        if (owner != null) {
            tag.putUUID(TAG_OWNER, owner);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        bombState = tag.getInt(TAG_STATE);
        fuseTicks = tag.contains(TAG_FUSE) ? tag.getInt(TAG_FUSE)
                : GensokyouConfig.SPIRIT_BOMB_DEFAULT_FUSE_TICKS.get();
        power = tag.contains(TAG_POWER) ? tag.getFloat(TAG_POWER) : 4.0F;
        blastRadius = tag.contains(TAG_RADIUS) ? tag.getInt(TAG_RADIUS) : 6;
        if (tag.hasUUID(TAG_OWNER)) {
            owner = tag.getUUID(TAG_OWNER);
        }
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
