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
 * 傩神楽面「無終」——主题：到点不收束。压轴。
 *
 * <p>这张符<b>到点不收束</b>：节拍无限延续，玩家找不到一个可以喘口气的终点。
 *
 * <p>五张符卡全是「同一招的变奏」——連射、乱連、急連、互不同步——这不是内容量，
 * 而是「無終」这个主题本身。
 *
 * <p>生命远超原版 {@code MAX_HEALTH} 的 1024 上限，以伤害除数承载（见基类）。
 */
public class NomenMaskEntity extends AbstractTouhouBoss {

    public NomenMaskEntity(EntityType<? extends NomenMaskEntity> type, Level level) {
        super(type, level);
        this.xpReward = 400;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return bossAttributes(400.0D, 12.0D);
    }

    @Override
    protected List<SpellCard> spellCards() {
        return BossCards.nomenMask();
    }

    @Override
    protected SignaturePalette palette() {
        return BossCards.NOMEN_PALETTE;
    }

    @Override
    protected double configSeconds() {
        return GensokyouConfig.BOSS_SECONDS_T2.get();
    }

    @Override
    protected int configHits() {
        return 5;
    }

    /**
     * {@inheritDoc}
     *
     * <p><b>占位待定</b>。暂定 2 阶——四只召唤 BOSS 里它符卡最多（5 张），
     * 故比其他三只高半档，但**远不是设计结论**：四只全是百鬼夜行的
     * 低阶召唤物，5 阶造型目前只有这一档会被真正用到，属基础设施预付。
     * 正式阶级表随寝宫 BOSS / 高阶内容落地后重定（见
     * {@code openspec/changes/boss-bar-tier-and-spellcard-name} design D3）。
     */
    @Override
    public int bossTier() {
        return 2;
    }


    @Override
    protected double moveMin() {
        return 6.0D;
    }

    @Override
    protected double moveMax() {
        return 22.0D;
    }

    @Override
    protected int starDropCount() {
        return 6;
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        this.spawnAtLocation(new ItemStack(ModItems.YEN.get(), 24 + this.getRandom().nextInt(25)));
        this.spawnAtLocation(new ItemStack(ModItems.SPELLCARD_STAR.get(), 1));
    }

    @Override
    public String toString() {
        return "NomenMaskEntity";
    }
}
