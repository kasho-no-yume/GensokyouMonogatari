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
import net.minecraft.world.entity.ai.attributes.Attributes;
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
                GensokyouConfig.BIG_FAIRY_DAMAGE.getDefault());
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

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        this.spawnAtLocation(new ItemStack(ModItems.YEN.get(), 4 + this.getRandom().nextInt(9)));
    }

    @Override
    protected void onCardChanged(SpellCard card) {
        this.triggerCast();
    }

    // ------------------------------------------------------------------
    // 默认攻击（5×5 弹幕墙）
    //
    // <p>刻意<b>不</b>进 {@code BossCards}：那张表是按血量阈值切分的内容，
    // 而默认攻击是全程在符卡底下继续跑的底噪。两者语义不同——符卡之间永不叠加，
    // 符卡与默认攻击则叠加，这是设计要的。
    //
    // <p>全场一份（不按人复制）：它是底噪，不该要求玩家做新决策。中心那一发朝最近的那名玩家。
    // ------------------------------------------------------------------

    /** 距下一次齐射还有多少 tick。 */
    private int wallCooldown;

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.level().isClientSide) {
            return;
        }
        tickDefaultAttack();
    }

    private void tickDefaultAttack() {
        int interval = Math.max(5, GensokyouConfig.BIG_FAIRY_WALL_INTERVAL.get());
        if (this.wallCooldown > 0) {
            this.wallCooldown--;
            return;
        }
        this.wallCooldown = interval;
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
                .count(GensokyouConfig.BIG_FAIRY_WALL_COUNT.get())
                .spread(GensokyouConfig.BIG_FAIRY_WALL_STEP_DEG.get())
                .offsetForward(GensokyouConfig.BIG_FAIRY_WALL_OFFSET.get())
                .speed(GensokyouConfig.BIG_FAIRY_WALL_SPEED.get())
                .size(GensokyouConfig.BIG_FAIRY_WALL_SIZE.get());
        for (Geometry.Shot shot : Geometry.build(Shape.GRID_FACING, this.getEyePosition(),
                forward, anchor, new Vec3(0, 1, 0), params, 0.0D, 0.0D,
                this.level().getRandom(), 1.0D, 1.0D)) {
            DanmakuEmitter.emit(this, shot, Behaviour.NONE, palette().at(0),
                    danmakuDamage(), null, null, Projectile.SPHERE,
                    Track.Beat.SpawnAnchor.NONE, 0, 0);
        }
    }

    // ------------------------------------------------------------------
    // 移动锁
    // ------------------------------------------------------------------

    /**
     * 花符[弹幕花环]的前半段锁死位置。
     *
     * <p>环挂在 BOSS 身后 6 格的圆盘上，而该圆盘的法线是「BOSS → 玩家」——
     * BOSS 一边游走一边放环，环就会在它身后拖出一条歪斜的轨迹，构图当场失效。
     * 后半段（弹幕已全部起飞）恢复游走，让阶段切换有「动起来」的读点。
     */
    @Override
    protected boolean movementLocked() {
        if (runner() == null) {
            return false;
        }
        // 按<b>下标</b>判环卡，不按名字。原先比的是字面量 "花符[弹幕花环]"，
        // 而符卡表里从来没有这个名字（散華/旋風/落華）——该比较恒不成立，
        // 锁位行为实际上从未生效。改下标后它才真的会在环卡前半段锁住。
        //
        // 下标 0 = BossCards.bigFairy() 的第一张（ring 那张，cycle=RING_CARD_CYCLE）。
        // 名字是显示层的东西：改 lang 键、或同一张卡改中文名，都会静默改掉行为。
        return runner().currentIndex() == 0
                && runner().cycleTick() < BossCards.RING_CARD_RING_TICKS;
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
