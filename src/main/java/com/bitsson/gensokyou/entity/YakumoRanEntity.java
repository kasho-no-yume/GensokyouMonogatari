package com.bitsson.gensokyou.entity;

import com.bitsson.gensokyou.balance.BalanceNumbers;

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
import net.minecraft.world.item.Item;
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
import java.util.Optional;

/**
 * 八云蓝（八雲 藍 / Yakumo Ran）—— 妖妖梦 EX 面 BOSS，**T2 正主**。
 *
 * <p>由占位残影「狐火『無序』」升级而来：换正式 GeckoLib 残影模型（暗化贴图 + 狐火自发光），
 * 符卡表重写为 8 张考据符卡（见 {@link BossCards#yakumoRan()}），主题取自角色自身
 * （狐火 / 式神 / 狐狸妖怪激光 / 阴阳术），MUST NOT 依赖任何形式化的缺段装置。
 *
 * <p>她是**隙间碎片的持有者**：主题信物为 {@code gensokyou:sukima_fragment}（击杀保底掉落），
 * 隙间碎片被定位为「2→3 门槛」——由 T2 正主产出，用于 T3 层级的门控。
 */
public class YakumoRanEntity extends AbstractTouhouBoss implements GeoEntity {

    private static final RawAnimation IDLE_ANIM =
            RawAnimation.begin().thenLoop("animation.yakumo_ran.idle");

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    public YakumoRanEntity(EntityType<? extends YakumoRanEntity> type, Level level) {
        super(type, level);
        this.xpReward = 320;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return bossAttributes(360.0D, 9.0D);
    }

    @Override
    protected List<SpellCard> spellCards() {
        return BossCards.yakumoRan();
    }

    /** 正式程序内容 id（migrate-boss-cards-to-programs）；蓝无独立底噪。 */
    @Override
    protected java.util.Optional<String> programContentId() {
        return java.util.Optional.of("yakumo_ran");
    }

    @Override
    protected SignaturePalette palette() {
        return BossCards.RAN_PALETTE;
    }

    @Override
    protected double configSeconds() {
        return BalanceNumbers.RAN_BOSS_SECONDS;
    }

    @Override
    protected int configHits() {
        return BalanceNumbers.RAN_BOSS_HITS;
    }

    /** T2 正主：自身品阶 2（咒符条造型），数值参照阶 2。 */
    @Override
    public int bossTier() {
        return 2;
    }

    @Override
    protected int referenceTier() {
        return 2;
    }

    @Override
    protected double moveMin() {
        return 7.0D;
    }

    @Override
    protected double moveMax() {
        return 24.0D;
    }

    @Override
    protected int starDropCount() {
        return 6;
    }

    /** 主题信物：隙间碎片（击杀保底掉，见 {@code #gensokyou:boss_signature}）。 */
    @Override
    public Optional<Item> signatureItem() {
        return Optional.of(ModItems.SUKIMA_FRAGMENT.get());
    }

    /** 主题卡代表：式神『狐の従者』（低概率直掉）。 */
    @Override
    public Optional<Item> themedCard() {
        return Optional.of(ModItems.FOX_SERVANT.get());
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        this.spawnAtLocation(new ItemStack(ModItems.YEN.get(), 24 + this.getRandom().nextInt(17)));
    }

    // ------------------------------------------------------------------
    // 默认攻击（狐火扇）
    //
    // 刻意不进 BossCards：那是按血量阈值切分的内容表，而默认攻击是全程在符卡底下
    // 继续跑的底噪。符卡之间永不叠加，符卡与默认攻击则叠加。
    // 全场一份（不按人复制）：底噪不该要求玩家做新决策。
    // ------------------------------------------------------------------

    private int fireCooldown;

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.level().isClientSide) {
            return;
        }
        if (!programBackendActive()) tickDefaultAttack();
    }

    private void tickDefaultAttack() {
        int interval = Math.max(5, BalanceNumbers.RAN_FOXFIRE_INTERVAL);
        if (this.fireCooldown > 0) {
            this.fireCooldown--;
            return;
        }
        this.fireCooldown = interval;
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
                .count(BalanceNumbers.RAN_FOXFIRE_COUNT)
                .spread(BalanceNumbers.RAN_FOXFIRE_SPREAD_DEG)
                .speed(BalanceNumbers.RAN_FOXFIRE_SPEED)
                .size(BalanceNumbers.RAN_FOXFIRE_SIZE);
        for (Geometry.Shot shot : Geometry.build(Shape.FAN, this.getEyePosition(),
                forward, anchor, new Vec3(0, 1, 0), params, 0.0D, 0.0D,
                this.level().getRandom(), 1.0D, 1.0D)) {
            DanmakuEmitter.emit(this, shot, Behaviour.NONE, palette().at(1),
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
        // 正式模型首版只有 idle（4 秒摆尾），walk/cast 兼用它。
        controllers.add(new AnimationController<>(this, "controller", 5, this::animationPredicate));
    }

    private PlayState animationPredicate(AnimationState<YakumoRanEntity> state) {
        return state.setAndContinue(IDLE_ANIM);
    }

    @Override
    public String toString() {
        return "YakumoRanEntity";
    }
}
