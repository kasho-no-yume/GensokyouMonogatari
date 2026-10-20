package com.bitsson.gensokyou.entity;

import com.bitsson.gensokyou.balance.BalanceNumbers;

import com.bitsson.gensokyou.registry.ModItems;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.Optional;

/**
 * 大妖精（正式化）。
 *
 * <p>教学档：3 张符卡、全大慢弹、全部朝玩家前向。它教的第一件事不是招式，
 * 而是「东方的怪会动、会读预警、弹幕是可读的」。
 *
 * <p>刻意<b>不发环</b>：二维弹幕里「环」是万能母题，三维里满向环等于一半弹在
 * 玩家背后，是第一只 BOSS 最不该教的东西。
 */
public class BigFairyEntity extends AbstractTouhouBoss implements GeoEntity {

    private static final RawAnimation IDLE_ANIM =
            RawAnimation.begin().thenLoop("animation.greater_fairy.idle");
    private static final RawAnimation FLY_ANIM =
            RawAnimation.begin().thenLoop("animation.greater_fairy.fly");
    private static final RawAnimation CAST_ANIM =
            RawAnimation.begin().thenPlay("animation.greater_fairy.cast");

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    public BigFairyEntity(EntityType<? extends BigFairyEntity> type, Level level) {
        super(type, level);
        this.xpReward = 60;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return bossAttributes(BalanceNumbers.BIG_FAIRY_MAX_HEALTH,
                BalanceNumbers.BIG_FAIRY_DAMAGE);
    }

    /** 正式程序内容 id。 */
    @Override
    protected java.util.Optional<String> programContentId() {
        return java.util.Optional.of("big_fairy");
    }


    @Override
    protected double bossSeconds() {
        return BalanceNumbers.BIG_FAIRY_BOSS_SECONDS;
    }

    @Override
    protected double configSeconds() {
        return BalanceNumbers.BIG_FAIRY_BOSS_SECONDS;
    }

    @Override
    protected int configHits() {
        return BalanceNumbers.BIG_FAIRY_BOSS_HITS;
    }

    /**
     * {@inheritDoc}
     *
     * <p><b>占位待定</b>。大妖精是百鬼夜行 L1 的召唤物，属低阶杂兵，暂定 1 阶。
     * 正式阶级表随寝宫 BOSS / 高阶内容落地后重定（见
     * {@code openspec/changes/boss-bar-tier-and-spellcard-name} design D3）。
     */
    @Override
    public int bossTier() {
        return 1;
    }


    @Override
    protected double moveMin() {
        return 10.0D;
    }

    @Override
    protected double moveMax() {
        return 20.0D;
    }

    @Override
    protected int starDropCount() {
        return 2;
    }

    /** 主题信物：花精之滴（击杀保底掉，见 {@code #gensokyou:boss_signature}）。 */
    @Override
    public Optional<Item> signatureItem() {
        return Optional.of(ModItems.FAIRY_NECTAR.get());
    }

    /** 主题卡代表：花符『癒しの花園』（低概率直掉）。 */
    @Override
    public Optional<Item> themedCard() {
        return Optional.of(ModItems.HEALING_GARDEN.get());
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        this.spawnAtLocation(new ItemStack(ModItems.YEN.get(), 4 + this.getRandom().nextInt(9)));
    }

    @Override
    protected void onProgramCardChanged(int index) {
        super.onProgramCardChanged(index);
        this.triggerCast();
    }

    // ------------------------------------------------------------------ GeckoLib

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.geoCache;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "controller", 5, this::animationPredicate)
                .triggerableAnim("cast", CAST_ANIM));
    }

    private PlayState animationPredicate(AnimationState<BigFairyEntity> state) {
        return state.isMoving() ? state.setAndContinue(FLY_ANIM) : state.setAndContinue(IDLE_ANIM);
    }

    /** 发射符卡时触发一次 cast 动画（服务端调用会同步到客户端）。 */
    public void triggerCast() {
        this.triggerAnim("controller", "cast");
    }

    @Override
    public String toString() {
        return "BigFairyEntity";
    }
}
