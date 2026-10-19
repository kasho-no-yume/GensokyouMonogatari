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
 * 黑谷山女（黒谷ヤマメ）——土蜘蛛妖怪本人，T1 野生召唤 BOSS。
 *
 * <p>与「残影」无关：她是完整的角色，其符卡表不受任何缺段约束。四张签名符卡全在
 * {@link BossCards#yamame()}，围绕原作地霊殿 1 面的两套母题「網」与「瘴」展开。
 *
 * <p>她是<b>隙间碎片的持有者</b>：解锁 T2 材料带与 L2 祭坛的唯一前置，掉落保底而非概率。
 */
public class YamameEntity extends AbstractTouhouBoss implements GeoEntity {

    private static final RawAnimation IDLE_ANIM =
            RawAnimation.begin().thenLoop("animation.kurodani.idle");
    private static final RawAnimation WALK_ANIM =
            RawAnimation.begin().thenLoop("animation.kurodani.walk");
    private static final RawAnimation CAST_ANIM =
            RawAnimation.begin().thenPlay("animation.kurodani.cast");

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    public YamameEntity(EntityType<? extends YamameEntity> type, Level level) {
        super(type, level);
        this.xpReward = 160;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return bossAttributes(120.0D, 6.0D);
    }

    /** 正式程序内容 id（migrate-boss-cards-to-programs）。 */
    @Override
    protected java.util.Optional<String> programContentId() {
        return java.util.Optional.of("yamame");
    }


    @Override
    protected double configSeconds() {
        return BalanceNumbers.YAMAME_BOSS_SECONDS;
    }

    @Override
    protected int configHits() {
        return BalanceNumbers.YAMAME_BOSS_HITS;
    }

    /** T1：百鬼夜行 L1 召唤，接在大妖精之后。 */
    @Override
    public int bossTier() {
        return 1;
    }

    @Override
    protected double moveMin() {
        return 8.0D;
    }

    @Override
    protected double moveMax() {
        return 26.0D;
    }


    @Override
    protected int starDropCount() {
        return 3;
    }

    /** 主题信物：土蜘蛛之丝（击杀保底掉，见 {@code #gensokyou:boss_signature}）。 */
    @Override
    public Optional<Item> signatureItem() {
        return Optional.of(ModItems.EARTH_SPIDER_SILK.get());
    }

    /** 主题卡代表：網符『蜘蛛の巣』（低概率直掉）。 */
    @Override
    public Optional<Item> themedCard() {
        return Optional.of(ModItems.SPIDER_WEB.get());
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        // 隙间碎片已移交给 T2 正主八云蓝（击杀保底掉）；山女不再掉它，其信物为土蜘蛛之丝。
        this.spawnAtLocation(new ItemStack(ModItems.YEN.get(), 8 + this.getRandom().nextInt(9)));
    }

    @Override
    protected void onProgramCardChanged(int index) {
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

    private PlayState animationPredicate(AnimationState<YamameEntity> state) {
        return state.isMoving() ? state.setAndContinue(WALK_ANIM) : state.setAndContinue(IDLE_ANIM);
    }

    /** 换符卡时触发一次 cast 动画（服务端调用会同步到客户端）。 */
    public void triggerCast() {
        this.triggerAnim("controller", "cast");
    }

    @Override
    public String toString() {
        return "YamameEntity";
    }
}
