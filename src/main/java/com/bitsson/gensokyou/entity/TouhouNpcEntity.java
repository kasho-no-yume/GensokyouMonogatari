package com.bitsson.gensokyou.entity;

import com.bitsson.gensokyou.config.GensokyouConfig;
import com.bitsson.gensokyou.dialogue.DialogueGraph;
import com.bitsson.gensokyou.dialogue.DialogueManager;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.UUID;

/**
 * 东方 NPC 基类：尽力无敌（一切伤害静默拒绝），仅「将死（玩家一击致死）或真死（hack 绕过）」
 * 触发恢复流程——紫色粒子 → discard → 原坐标复制体。
 * 雕像行为：无移动 AI、无重力、不可推、不可拴绳、不自然消失。
 * 子类按需实现 {@link #updateTrades()}（交易）与 {@link #dialogueGraph()}（对话）。
 */
public abstract class TouhouNpcEntity extends PathfinderMob implements Merchant {

    private static final Vector3f PURPLE = new Vector3f(0.55F, 0.1F, 0.85F);

    private final MerchantOffers offers = new MerchantOffers();
    @Nullable
    private Player tradingPlayer;
    /** 锚点坐标：NPC 永恒驻留之地，NBT 持久；仅 setAnchorPos 可改。 */
    @Nullable
    private Vec3 anchorPos;

    protected TouhouNpcEntity(EntityType<? extends TouhouNpcEntity> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
        this.setPersistenceRequired();
        this.xpReward = 0;
        if (!level.isClientSide) {
            this.updateTrades();
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, GensokyouConfig.NPC_MAX_HEALTH.getDefault())
                .add(Attributes.FOLLOW_RANGE, 16.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(2, new RandomLookAroundGoal(this));
    }

    // ---- 雕像行为 ----

    @Override
    public boolean isPushable() {
        return false;
    }

    /** 流体不推动（水流/岩浆流）。与下方 setDeltaMovement 封死互为双保险。 */
    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    /**
     * 速度恒为零：所有位移来源（水流、爆炸击退、实体碰撞、活塞、mod 调用）
     * 最终都经 {@code setDeltaMovement(Vec3)} 收口，覆写为 no-op 即彻底不可移动。
     * 传送走 {@code setPosition} 不受影响；克隆体 load 读 Motion 亦恒零。
     */
    @Override
    public void setDeltaMovement(Vec3 deltaMovement) {
        // 雕像：永不获得速度
    }

    /**
     * 重力恒为 0。{@code getGravity()} 是 final 且 {@code isNoGravity()?0:getDefaultGravity()}，
     * 而 {@code Entity.load()} 会用 NBT 缺省的 {@code NoGravity=false} 覆盖构造期的
     * {@code setNoGravity(true)}，故必须在此覆写默认重力才能彻底免疫。
     */
    @Override
    protected double getDefaultGravity() {
        return 0.0D;
    }

    // ---- 锚点驻留（design D6：永远待在刷出位置，仅 setAnchorPos 可移位）----

    /**
     * 唯一合法移位入口：更新锚点并落位（如未来"NPC 搬家"玩法调用）。
     * 其余一切位置写入都会被钉回锚点。
     */
    public final void setAnchorPos(Vec3 pos) {
        this.anchorPos = pos;
        super.setPos(pos.x, pos.y, pos.z);
    }

    @Nullable
    public Vec3 getAnchorPos() {
        return this.anchorPos;
    }

    /** 位置写入收口 1：setPos(Vec3) final 包装与 move() 都落到这里。 */
    @Override
    public void setPos(double x, double y, double z) {
        if (this.anchorPos == null) {
            super.setPos(x, y, z);
        } else {
            super.setPos(this.anchorPos.x, this.anchorPos.y, this.anchorPos.z);
        }
    }

    /** 位置写入收口 2：moveTo 各重载最终走 5 参版（内部 setPosRaw，不经 setPos）。 */
    @Override
    public void moveTo(double x, double y, double z, float yRot, float xRot) {
        if (this.anchorPos == null) {
            super.moveTo(x, y, z, yRot, xRot);
        } else {
            super.moveTo(this.anchorPos.x, this.anchorPos.y, this.anchorPos.z, yRot, xRot);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            return;
        }
        if (this.anchorPos == null) {
            this.anchorPos = this.position();
        } else if (this.distanceToSqr(this.anchorPos.x, this.anchorPos.y, this.anchorPos.z) > 1.0E-4) {
            // 兜底：setPosRaw 等不可覆写路径造成的漂移，下一 tick 内钉回
            super.setPos(this.anchorPos.x, this.anchorPos.y, this.anchorPos.z);
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (this.anchorPos != null) {
            tag.putDouble("NpcAnchorX", this.anchorPos.x);
            tag.putDouble("NpcAnchorY", this.anchorPos.y);
            tag.putDouble("NpcAnchorZ", this.anchorPos.z);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("NpcAnchorX")) {
            this.anchorPos = new Vec3(tag.getDouble("NpcAnchorX"),
                    tag.getDouble("NpcAnchorY"), tag.getDouble("NpcAnchorZ"));
        }
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public boolean requiresCustomPersistence() {
        return true;
    }

    @Override
    public void checkDespawn() {
        // 永不消失
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Nullable
    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return null;
    }

    @Nullable
    @Override
    protected SoundEvent getDeathSound() {
        return null;
    }

    // ---- 无敌与恢复（design D1/D2）----

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.level().isClientSide || this.isRemoved()) {
            return false;
        }
        if (amount >= this.getMaxHealth() && source.getEntity() instanceof ServerPlayer attacker) {
            NpcOffenseTracker.recordOffense(attacker);
            this.restore();
            return true;
        }
        return false;
    }

    @Override
    public void die(DamageSource source) {
        if (this.level().isClientSide) {
            return;
        }
        if (source.getEntity() instanceof ServerPlayer attacker) {
            NpcOffenseTracker.recordOffense(attacker);
        }
        this.restore();
    }

    /** 恢复流程：粒子爆发 → discard → 原坐标复制体（保留自定义名等 NBT，UUID 除外）。 */
    private void restore() {
        if (this.isRemoved() || !(this.level() instanceof ServerLevel level)) {
            return;
        }
        int total = GensokyouConfig.NPC_DEATH_PARTICLE_COUNT.get();
        double x = this.getX();
        double y = this.getY() + 1.0D;
        double z = this.getZ();
        if (total > 0) {
            level.sendParticles(ParticleTypes.WITCH, x, y, z, total / 2, 0.5, 0.75, 0.5, 0.08);
            level.sendParticles(new DustParticleOptions(PURPLE, 1.3F),
                    x, y, z, total - total / 2, 0.5, 0.75, 0.5, 0.0);
        }
        this.discard();
        Entity copy = this.getType().create(level);
        if (copy == null) {
            return;
        }
        CompoundTag tag = this.saveWithoutId(new CompoundTag());
        copy.load(tag);
        copy.setUUID(UUID.randomUUID());
        level.addFreshEntity(copy);
    }

    // ---- 交互入口分发（design D4/D5）----

    /** 子类覆写以提供交易表（服务端构造时调用一次）。 */
    protected void updateTrades() {
    }

    /** 对话型 NPC 覆写返回非空图；非空时右键优先开对话（交易经 OPEN_TRADE 衔接）。 */
    @Nullable
    public DialogueGraph dialogueGraph() {
        return null;
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (this.level().isClientSide) {
            return InteractionResult.sidedSuccess(true);
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }
        DialogueGraph graph = this.dialogueGraph();
        if (graph != null) {
            DialogueManager.open(serverPlayer, this, graph);
            return InteractionResult.sidedSuccess(false);
        }
        if (!this.getOffers().isEmpty()) {
            this.openTrade(serverPlayer);
            return InteractionResult.sidedSuccess(false);
        }
        return InteractionResult.PASS;
    }

    /** 打开原版交易界面（对话 OPEN_TRADE 也走这里）。 */
    public void openTrade(ServerPlayer player) {
        if (this.getOffers().isEmpty()) {
            return;
        }
        this.setTradingPlayer(player);
        this.openTradingScreen(player, this.getDisplayName(), 1);
    }

    // ---- Merchant 接口（非 AbstractVillager，成员手写）----

    @Override
    public void setTradingPlayer(@Nullable Player player) {
        this.tradingPlayer = player;
    }

    @Nullable
    @Override
    public Player getTradingPlayer() {
        return this.tradingPlayer;
    }

    @Override
    public MerchantOffers getOffers() {
        return this.offers;
    }

    @Override
    public void overrideOffers(MerchantOffers newOffers) {
        this.offers.clear();
        this.offers.addAll(newOffers);
    }

    @Override
    public void notifyTrade(MerchantOffer offer) {
        offer.increaseUses();
    }

    @Override
    public void notifyTradeUpdated(ItemStack stack) {
    }

    @Override
    public int getVillagerXp() {
        return 0;
    }

    @Override
    public void overrideXp(int xpIn) {
    }

    @Override
    public boolean showProgressBar() {
        return false;
    }

    @Nullable
    @Override
    public SoundEvent getNotifyTradeSound() {
        return null;
    }

    @Override
    public boolean isClientSide() {
        return this.level().isClientSide;
    }
}
