package com.bitsson.gensokyou.entity;

import com.bitsson.gensokyou.entity.goal.TestBossPatternGoal;
import com.bitsson.gensokyou.registry.ModDamageTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * 数值测试 BOSS（add-balance-test-harness）：移动/悬停与小妖精一致（继承 {@link FairyEntity}），
 * 非弹幕伤害沿用东方怪减免（实现 {@link TouhouMonster}），每 3 秒随机切换一种弹幕模式。
 *
 * <p>仅由 {@code /gs_test boss} 生成；生命/弹伤由命令按标准玩家 DPS/EHP 标定后写入，不产正式掉落。
 *
 * <p><b>大生命处理</b>：原版 {@code MAX_HEALTH} 是 RangedAttribute，上限 1024，无法直接承载
 * 数万~百万级标定 HP。故实际血量钳到 1024，其余部分以"弹幕伤害除数"实现：
 * 有效 HP = 实际血量 × 伤害除数。血条按实际血量占比显示。
 */
public class BalanceTestBossEntity extends FairyEntity {

    /** 原版 MAX_HEALTH 上限。 */
    private static final float VANILLA_MAX_HEALTH = 1024F;

    private final ServerBossEvent bossBar = new ServerBossEvent(
            this.getDisplayName(), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10);

    private int testTier = 1;
    private boolean testMaxVariant = false;
    private float testHp = 200F;
    private float testDanmakuDamage = 5F;

    public BalanceTestBossEntity(EntityType<? extends BalanceTestBossEntity> type, Level level) {
        super(type, level);
        this.xpReward = 0;
    }

    /** 命令生成前调用：写入阶级/变体与标定后的生命、弹伤。 */
    public void configure(int tier, boolean maxVariant, float hp, float danmakuDamage) {
        this.testTier = Math.max(1, tier);
        this.testMaxVariant = maxVariant;
        this.testHp = Math.max(1F, hp);
        this.testDanmakuDamage = Math.max(0F, danmakuDamage);
        this.bossBar.setName(Component.literal(
                "Test Boss T" + this.testTier + (maxVariant ? " max" : " min")
                        + "  HP " + (long) this.testHp));
    }

    public int testTier() {
        return this.testTier;
    }

    public boolean testMaxVariant() {
        return this.testMaxVariant;
    }

    public float testDanmakuDamage() {
        return this.testDanmakuDamage;
    }

    /** 实际写入原版属性的血量（受 MAX_HEALTH 上限钳制）。 */
    private float actualMaxHealth() {
        return Math.min(this.testHp, VANILLA_MAX_HEALTH);
    }

    /** 弹幕伤害除数：有效 HP = 实际血量 × 除数。 */
    private float damageScale() {
        return this.testHp / actualMaxHealth();
    }

    @Override
    protected void addAttackGoals() {
        this.goalSelector.addGoal(3, new TestBossPatternGoal(this));
    }

    @Override
    protected double maxHealthValue() {
        return actualMaxHealth();
    }

    @Override
    protected double attributeDamage() {
        return this.testDanmakuDamage;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(ModDamageTypes.DANMAKU) && damageScale() > 1F) {
            amount /= damageScale();
        }
        return super.hurt(source, amount);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!this.level().isClientSide) {
            this.bossBar.setProgress(this.getHealth() / Math.max(1F, this.getMaxHealth()));
        }
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        this.bossBar.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        this.bossBar.removePlayer(player);
    }

    @Override
    public void die(DamageSource damageSource) {
        this.bossBar.removeAllPlayers();
        super.die(damageSource);
    }

    /** 测试实体不产正式掉落。 */
    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource damageSource, boolean recentlyHit) {
        // none
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("TestTier", this.testTier);
        tag.putBoolean("TestMaxVariant", this.testMaxVariant);
        tag.putFloat("TestHp", this.testHp);
        tag.putFloat("TestDanmakuDamage", this.testDanmakuDamage);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("TestTier")) {
            this.testTier = tag.getInt("TestTier");
        }
        this.testMaxVariant = tag.getBoolean("TestMaxVariant");
        if (tag.contains("TestHp")) {
            this.testHp = tag.getFloat("TestHp");
        }
        if (tag.contains("TestDanmakuDamage")) {
            this.testDanmakuDamage = tag.getFloat("TestDanmakuDamage");
        }
    }
}
