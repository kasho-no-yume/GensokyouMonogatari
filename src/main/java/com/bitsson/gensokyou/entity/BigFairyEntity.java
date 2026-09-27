package com.bitsson.gensokyou.entity;

import com.bitsson.gensokyou.config.GensokyouConfig;
import com.bitsson.gensokyou.danmaku.track.BossCards;
import com.bitsson.gensokyou.danmaku.track.SignaturePalette;
import com.bitsson.gensokyou.danmaku.track.SpellCard;
import com.bitsson.gensokyou.registry.ModItems;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
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

import java.util.List;

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
        return bossAttributes(GensokyouConfig.BIG_FAIRY_MAX_HEALTH.getDefault(),
                GensokyouConfig.BIG_FAIRY_DAMAGE.getDefault(), 48.0D);
    }

    @Override
    protected List<SpellCard> spellCards() {
        return BossCards.bigFairy();
    }

    @Override
    protected SignaturePalette palette() {
        return BossCards.BIG_FAIRY_PALETTE;
    }

    @Override
    protected double bossSeconds() {
        return GensokyouConfig.BIG_FAIRY_BOSS_SECONDS.get();
    }

    @Override
    protected double configSeconds() {
        return GensokyouConfig.BIG_FAIRY_BOSS_SECONDS.get();
    }

    @Override
    protected int configHits() {
        return GensokyouConfig.BIG_FAIRY_BOSS_HITS.get();
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

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        this.spawnAtLocation(new ItemStack(ModItems.YEN.get(), 4 + this.getRandom().nextInt(9)));
    }

    @Override
    protected void onCardChanged(SpellCard card) {
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
