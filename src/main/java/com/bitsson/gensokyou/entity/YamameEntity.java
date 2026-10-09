package com.bitsson.gensokyou.entity;

import com.bitsson.gensokyou.config.GensokyouConfig;
import com.bitsson.gensokyou.danmaku.DanmakuEmitter;
import com.bitsson.gensokyou.danmaku.track.Behaviour;
import com.bitsson.gensokyou.danmaku.track.BossCards;
import com.bitsson.gensokyou.danmaku.track.Geometry;
import com.bitsson.gensokyou.danmaku.track.Projectile;
import com.bitsson.gensokyou.danmaku.track.Shape;
import com.bitsson.gensokyou.danmaku.track.SignaturePalette;
import com.bitsson.gensokyou.danmaku.track.SpellCard;
import com.bitsson.gensokyou.danmaku.track.Track;
import com.bitsson.gensokyou.registry.ModItems;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

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

    @Override
    protected List<SpellCard> spellCards() {
        return BossCards.yamame();
    }

    @Override
    protected SignaturePalette palette() {
        return BossCards.YAMAME_PALETTE;
    }

    @Override
    protected double configSeconds() {
        return GensokyouConfig.YAMAME_BOSS_SECONDS.get();
    }

    @Override
    protected int configHits() {
        return GensokyouConfig.YAMAME_BOSS_HITS.get();
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

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        // 保底：解锁 T2 材料带的唯一钥匙，也是召唤 L2 祭坛的前置。
        this.spawnAtLocation(new ItemStack(ModItems.SUKIMA_FRAGMENT.get()));
        this.spawnAtLocation(new ItemStack(ModItems.YEN.get(), 8 + this.getRandom().nextInt(9)));
    }

    @Override
    protected void onCardChanged(SpellCard card) {
        this.triggerCast();
    }

    // ------------------------------------------------------------------
    // 默认攻击（蛛丝扇）
    //
    // <p>刻意<b>不</b>进 {@code BossCards}：那张表是按血量阈值切分的内容，而默认攻击
    // 是全程在符卡底下继续跑的底噪。符卡之间永不叠加，符卡与默认攻击则叠加。
    //
    // <p>全场一份（不按人复制）：底噪不该要求玩家做新决策。锥口朝最近的那名玩家。
    // ------------------------------------------------------------------

    private int webCooldown;

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.level().isClientSide) {
            return;
        }
        tickDefaultAttack();
    }

    private void tickDefaultAttack() {
        int interval = Math.max(5, GensokyouConfig.YAMAME_WEB_INTERVAL.get());
        if (this.webCooldown > 0) {
            this.webCooldown--;
            return;
        }
        this.webCooldown = interval;
        List<Player> locked = lockedTargets();
        if (locked.isEmpty()) {
            return;
        }
        Vec3 anchor = locked.get(0).position();
        Vec3 forward = anchor.subtract(this.getEyePosition());
        if (forward.lengthSqr() < 1.0E-6D) {
            return;
        }
        forward = forward.normalize();
        Shape.Params params = Shape.Params.defaults()
                .count(GensokyouConfig.YAMAME_WEB_COUNT.get())
                .spread(GensokyouConfig.YAMAME_WEB_SPREAD_DEG.get())
                .speed(GensokyouConfig.YAMAME_WEB_SPEED.get())
                .size(GensokyouConfig.YAMAME_WEB_SIZE.get());
        for (Geometry.Shot shot : Geometry.build(Shape.FAN, this.getEyePosition(),
                forward, anchor, new Vec3(0, 1, 0), params, 0.0D, 0.0D,
                this.level().getRandom(), 1.0D, 1.0D)) {
            DanmakuEmitter.emit(this, shot, Behaviour.NONE, palette().at(0),
                    danmakuDamage(), null, null, Projectile.SPHERE,
                    Track.Beat.SpawnAnchor.NONE, 0, 0);
        }
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
