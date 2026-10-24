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
 * 露米娅 —— T1 最终 BOSS（add-rumia-tier1-final-boss）。
 *
 * <p>她取代了旧的 `nomen_mask` / 傩神楽面「無終」占位：不再是残影 billboard，也不再走
 * `UnmigratedTouhouBoss` 旧后端。四张官方卡（月光射线 / 夜鸟 / 境界线 / 月之暗面）由
 * {@link com.bitsson.gensokyou.danmaku.program.content.RumiaPrograms} 登记。
 *
 * <p>数值定位是「T1 强度上限」，不是 1→2 的进度门：`referenceTier` 保持 1，
 * 秒数/挨弹数取 240/7，`bossTier` 仍为 1（血条造型）。
 */
public class RumiaEntity extends AbstractTouhouBoss implements GeoEntity {

    private static final RawAnimation IDLE_ANIM =
            RawAnimation.begin().thenLoop("animation.rumia.idle");
    private static final RawAnimation WALK_ANIM =
            RawAnimation.begin().thenLoop("animation.rumia.walk");
    private static final RawAnimation CAST_ANIM =
            RawAnimation.begin().thenPlay("animation.rumia.cast");

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    public RumiaEntity(EntityType<? extends RumiaEntity> type, Level level) {
        super(type, level);
        this.xpReward = 300;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return bossAttributes(400.0D, 12.0D);
    }

    /** 正式程序内容 id。 */
    @Override
    protected java.util.Optional<String> programContentId() {
        return java.util.Optional.of("rumia");
    }

    @Override
    protected double configSeconds() {
        return BalanceNumbers.RUMIA_BOSS_SECONDS;
    }

    @Override
    protected int configHits() {
        return BalanceNumbers.RUMIA_BOSS_HITS;
    }

    /** T1 最终：血条造型仍为 1 阶；强度由 referenceTier=1 + 240/7 表达。 */
    @Override
    public int bossTier() {
        return 1;
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

    /** 主题信物：宵暗之核（击杀保底掉，见 {@code #gensokyou:boss_signature}）。 */
    @Override
    public Optional<Item> signatureItem() {
        return Optional.of(ModItems.NIGHTFALL_CORE.get());
    }

    /** 主题卡代表：暗符『划界』（低概率直掉）。 */
    @Override
    public Optional<Item> themedCard() {
        return Optional.of(ModItems.DEMARCATION.get());
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        // 不额外掉整颗 spellcard_star：它是召唤钥匙，掉落整颗等于退还钥匙。
        this.spawnAtLocation(new ItemStack(ModItems.YEN.get(), 24 + this.getRandom().nextInt(25)));
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

    private PlayState animationPredicate(AnimationState<RumiaEntity> state) {
        return state.isMoving() ? state.setAndContinue(WALK_ANIM) : state.setAndContinue(IDLE_ANIM);
    }

    /** 换符卡时触发一次 cast 动画（服务端调用会同步到客户端）。 */
    public void triggerCast() {
        this.triggerAnim("controller", "cast");
    }

    @Override
    public String toString() {
        return "RumiaEntity";
    }
}
