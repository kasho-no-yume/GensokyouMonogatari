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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * 狐火「無序」——主题：起手即峰值、没有预备拍。
 *
 * <p>这张符<b>起手就是峰值、没有预备拍</b>，预警窗口被压到极限。
 *
 * <p>可读性：起手即峰值 MUST NOT 变成「在你背后凭空刷弹」。读不出是因为没时间，
 * 不是因为看不见，故全部轨道仍锁在玩家朝向的包络内（R1 前向威胁）。
 */
public class KitsuneBiEntity extends AbstractTouhouBoss {

    public KitsuneBiEntity(EntityType<? extends KitsuneBiEntity> type, Level level) {
        super(type, level);
        this.xpReward = 220;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return bossAttributes(160.0D, 8.0D);
    }

    @Override
    protected List<SpellCard> spellCards() {
        return BossCards.kitsuneBi();
    }

    @Override
    protected SignaturePalette palette() {
        return BossCards.KITSUNEBI_PALETTE;
    }

    @Override
    protected double configSeconds() {
        return GensokyouConfig.BOSS_SECONDS_T1.get() * 1.5D;
    }

    @Override
    protected int configHits() {
        return 6;
    }

    /**
     * {@inheritDoc}
     *
     * <p><b>占位待定</b>。狐火是百鬼夜行召唤的低阶残影，暂定 1 阶。
     * 正式阶级表随寝宫 BOSS / 高阶内容落地后重定（见
     * {@code openspec/changes/boss-bar-tier-and-spellcard-name} design D3）。
     */
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
        return 4;
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        this.spawnAtLocation(new ItemStack(ModItems.YEN.get(), 16 + this.getRandom().nextInt(17)));
    }

    @Override
    public String toString() {
        return "KitsuneBiEntity";
    }
}
