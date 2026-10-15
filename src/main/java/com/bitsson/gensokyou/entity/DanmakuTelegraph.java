package com.bitsson.gensokyou.entity;

import com.bitsson.gensokyou.danmaku.DanmakuOrchestrationStats;
import com.bitsson.gensokyou.registry.ModEntityTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 发射源预警符印（{@code extend-danmaku-orchestration}）。
 *
 * <p>一拍声明了领示提前量时，在真弹发射前的第 {@code lead} tick，于该拍的发射原点
 * 生成一枚本实体；它存活到真弹发射那一刻（{@code remainingTicks == lead}），
 * 到点自行消失。于是「符印 → 真弹」在原点上重合，玩家在真弹出现前有完整的观察窗。
 *
 * <p><b>无伤害、无碰撞、不计入弹数预算</b>：
 * <ul>
 *   <li>它继承 {@link Entity}（非 {@code LivingEntity}、非 {@code AbstractDanmakuProjectile}），
 *       故 {@code DanmakuBudget} 的计数与上限都不含它；</li>
 *   <li>{@code noPhysics} + 关闭可选中/可推动，玩家无法与它交互，它也不阻挡任何东西；</li>
 *   <li>{@link #hurt} 恒返回 false。</li>
 * </ul>
 *
 * <p>渲染复用既有 {@code BillboardRenderer}（一张发光贴图），不新增资产。
 */
public class DanmakuTelegraph extends Entity {

    /** 剩余存活 tick：到 0 即消失。真弹发射时刻 = 生成时刻 + 该值 == 领示提前量。 */
    private int remainingTicks = 1;

    public DanmakuTelegraph(EntityType<? extends DanmakuTelegraph> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    /**
     * 在给定世界坐标生成一枚符印，存活 {@code ticks} tick。
     *
     * @param ticks 领示提前量（tick）；{@code <= 0} 会被夹到 1
     */
    public static DanmakuTelegraph spawn(ServerLevel level, Vec3 at, int ticks) {
        DanmakuTelegraph telegraph = new DanmakuTelegraph(ModEntityTypes.DANMAKU_TELEGRAPH.get(), level);
        telegraph.setPos(at.x, at.y, at.z);
        telegraph.remainingTicks = Math.max(1, ticks);
        level.addFreshEntity(telegraph);
        DanmakuOrchestrationStats.recordTelegraph();
        return telegraph;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        // 无同步字段：符印的生命周期由「服务端到点 discard」给出，客户端只需在存在期间渲染。
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide && --this.remainingTicks <= 0) {
            this.discard();
        }
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.remainingTicks = Math.max(1, tag.getInt("Remain"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Remain", this.remainingTicks);
    }
}
