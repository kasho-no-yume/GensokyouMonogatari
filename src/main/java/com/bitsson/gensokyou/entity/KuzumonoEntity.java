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
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * 鬼蛛「堅牢」——缺「破」的符卡成了精。
 *
 * <p>「破」是东方符卡唯一必须写符名的地方。缺了它，这张符就<b>不会主动攻击</b>：
 * 它的轨道里没有瞄准型节拍，只剩封锁轨。弹幕只出现在规则位置上，
 * 玩家必须主动破局——打掉悬停节点、绕开溜め、从交差笼的空格子穿出去。
 *
 * <p>它是<b>隙间碎片的持有者</b>：幻想乡的钥匙来自幻想乡的守门人，而它是第一道门。
 * 掉落保底而非概率——它是终局门，不该卡在 RNG 上。
 */
public class KuzumonoEntity extends AbstractTouhouBoss {

    public KuzumonoEntity(EntityType<? extends KuzumonoEntity> type, Level level) {
        super(type, level);
        this.xpReward = 160;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return bossAttributes(120.0D, 6.0D, 48.0D);
    }

    @Override
    protected List<SpellCard> spellCards() {
        return BossCards.kuzumono();
    }

    @Override
    protected SignaturePalette palette() {
        return BossCards.KUZUMONO_PALETTE;
    }

    @Override
    protected double configSeconds() {
        return GensokyouConfig.KUZUMONO_BOSS_SECONDS.get();
    }

    @Override
    protected int configHits() {
        return GensokyouConfig.KUZUMONO_BOSS_HITS.get();
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
    public String toString() {
        return "KuzumonoEntity";
    }
}
