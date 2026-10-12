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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;

/**
 * 露米娅（占位外观复用「傩神楽面」）——主题：到点不收束。压轴。
 *
 * <p><b>显示名已由占位的傩神楽面改为「露米娅」</b>；实体 id 仍为 {@code nomen_mask}
 * （不重命名 id，避免破坏存档与既有引用）。符卡表暂沿用傩面五张「同一招的变奏」。
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
     * <p>显示名为「露米娅」的 1 阶召唤 BOSS（占位外观复用傩面）。原傩神楽面占位的
     * 「2 阶（符卡最多故高半档）」是**已弃用的占位结论**，随本改名一并按下调为 1 阶。
     */
    @Override
    public int bossTier() {
        return 1;
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

    /** 主题信物：宵暗之核（击杀保底掉，见 {@code #gensokyou:boss_signature}）。 */
    @Override
    public Optional<Item> signatureItem() {
        return Optional.of(ModItems.NIGHTFALL_CORE.get());
    }

    /** 主题卡代表：闇符『demarcation』（低概率直掉）。 */
    @Override
    public Optional<Item> themedCard() {
        return Optional.of(ModItems.DEMARCATION.get());
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
