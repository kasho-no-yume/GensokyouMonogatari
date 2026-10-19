package com.bitsson.gensokyou.entity;

import com.bitsson.gensokyou.balance.BalanceNumbers;

import com.bitsson.gensokyou.entity.goal.EightAngleDanmakuGoal;
import com.bitsson.gensokyou.entity.goal.FanDanmakuGoal;
import com.bitsson.gensokyou.entity.goal.FlashToNearestPlayerGoal;
import com.bitsson.gensokyou.entity.goal.FourOfAKindGoal;
import com.bitsson.gensokyou.registry.ModItems;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class FlandreEntity extends Monster implements TouhouMonster, TouhouBoss {
    // ⚠️ 传 this 而非 this.getUUID()：实体的 UUID 在构造之后还会被 Entity#load 用存档值覆盖。
    private final TouhouBossBar bossBar = new TouhouBossBar(
            this, this.getDisplayName(),
            BossEvent.BossBarColor.BLUE, BossEvent.BossBarOverlay.NOTCHED_20);

    private boolean fake = false;
    private boolean statsApplied = false;

    public FlandreEntity(EntityType<? extends FlandreEntity> type, Level level) {
        super(type, level);
        this.xpReward = 0;
    }

    /**
     * {@inheritDoc}
     *
     * <p><b>占位待定，且比别的 BOSS 更没有依据</b>：芙兰朵露目前<b>根本召不出来</b>——
     * {@code SummonBossEffects.REGISTRY} 只注册 4 只（big_fairy / yamame /
     * yakumo_ran / nomen），她不在任何 spawn tag 或 biome modifier 里，只能 {@code /summon}。
     *
     * <p>按 {@code boss-dev-design} §7 的定位她属「寝宫 BOSS」（专属场地、高阶），
     * 而寝宫 worldgen 尚未落地，故暂给 5 阶紫位占位。
     *
     * <p>本类<b>不</b>覆写 {@code activeCardIndex()}：四重存在的「血量比 ⇒ 选阶段」
     * 对她不成立（4 条命、HP 分数语义不同），走接口缺省即"无符卡"，HUD 不画该行。
     */
    @Override
    public int bossTier() {
        return 5;
    }

    public boolean isFake() {
        return fake;
    }

    protected void markFake() {
        this.fake = true;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, BalanceNumbers.FLANDRE_MAX_HEALTH)
                .add(Attributes.ATTACK_DAMAGE, BalanceNumbers.FLANDRE_ATTACK_DAMAGE)
                .add(Attributes.ARMOR, BalanceNumbers.FLANDRE_ARMOR)
                .add(Attributes.MOVEMENT_SPEED, BalanceNumbers.FLANDRE_MOVEMENT_SPEED)
                .add(Attributes.FOLLOW_RANGE, BalanceNumbers.FLANDRE_FOLLOW_RANGE);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(3, new FlashToNearestPlayerGoal(this,
                BalanceNumbers.FLASH_INTERVAL));
        goalSelector.addGoal(3, new EightAngleDanmakuGoal(this,
                BalanceNumbers.EIGHT_ANGLE_INTERVAL,
                BalanceNumbers.DANMAKU_SPEED.floatValue(),
                BalanceNumbers.EIGHT_ANGLE_DAMAGE.floatValue()));
        goalSelector.addGoal(3, new FanDanmakuGoal(this,
                BalanceNumbers.RANDOM_SHOT_INTERVAL, 3, 15D,
                BalanceNumbers.DANMAKU_SPEED, 8F));
        goalSelector.addGoal(4, new FourOfAKindGoal(this));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.7D));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) {
            return;
        }
        if (!statsApplied) {
            statsApplied = true;
            applyRuntimeStats();
        }
        if (!isFake()) {
            bossBar.setProgress(getHealth() / getMaxHealth());
        }
    }

    protected void applyRuntimeStats() {
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(BalanceNumbers.FLANDRE_MAX_HEALTH);
        setHealth(getMaxHealth());
        getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(BalanceNumbers.FLANDRE_ATTACK_DAMAGE);
        getAttribute(Attributes.ARMOR).setBaseValue(BalanceNumbers.FLANDRE_ARMOR);
        getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(BalanceNumbers.FLANDRE_MOVEMENT_SPEED);
        getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(BalanceNumbers.FLANDRE_FOLLOW_RANGE);
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        if (!isFake()) {
            bossBar.addPlayer(player);
        }
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        if (!isFake()) {
            bossBar.removePlayer(player);
        }
    }

    @Override
    public void die(DamageSource damageSource) {
        if (!isFake()) {
            this.level().getEntitiesOfClass(FakeFlandreEntity.class,
                    getBoundingBox().inflate(30D)).forEach(entity -> entity.discard());
        }
        super.die(damageSource);
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource damageSource, boolean recentlyHit) {
        if (isFake()) {
            return;
        }
        super.dropCustomDeathLoot(level, damageSource, recentlyHit);
        spawnAtLocation(new ItemStack(ModItems.SPELLCARD_STAR.get()));
        spawnAtLocation(new ItemStack(ModItems.BROKEN_SPELL_CARD_STAR.get()));
        int min = Mth.floor(BalanceNumbers.BOSS_YEN_MIN);
        int max = Math.max(min, Mth.floor(BalanceNumbers.BOSS_YEN_MAX));
        spawnAtLocation(new ItemStack(ModItems.YEN.get(), min + getRandom().nextInt(max - min + 1)));
        if (getRandom().nextFloat() < BalanceNumbers.LAEVATEIN_CHANCE.floatValue()) {
            spawnAtLocation(new ItemStack(ModItems.LAEVATEIN.get()));
        }
    }

    @Override
    protected int getBaseExperienceReward() {
        return isFake() ? 0 : BalanceNumbers.FLANDRE_EXPERIENCE;
    }
}
