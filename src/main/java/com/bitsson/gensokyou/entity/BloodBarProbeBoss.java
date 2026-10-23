package com.bitsson.gensokyou.entity;

import com.bitsson.gensokyou.balance.BalanceNumbers;
import com.bitsson.gensokyou.danmaku.track.BossCards;
import com.bitsson.gensokyou.danmaku.track.SignaturePalette;
import com.bitsson.gensokyou.danmaku.track.SpellCard;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * 咒符条<b>外观试作</b>用 BOSS：只提供「三阶 / 四阶」两种 {@link TouhouBoss#bossTier()}
 * 的在场实体，用来在实机里目检 {@code frame_3.png} / {@code frame_4.png} 两套血条边框。
 *
 * <p><b>这不是正式 BOSS，也不是三阶/四阶的角色占位。</b>正式 BOSS 到位后本文件连同
 * {@code ModEntityTypes} 中的 {@code blood_bar_probe_t3/t4} 注册一并删除即可——
 * 它们不进 {@code #gensokyou:bosses} 标签、不进召唤配方、不进 {@code BossCards.all()}
 * （符卡表直接复用露米娅那套已过 lint 的卡，不改任何正式内容）。
 *
 * <p>为什么需要它：现有可召唤 BOSS 只有 1 阶（大妖精 / 山女 / 露米娅）、2 阶（八云蓝）、
 * 5 阶（芙兰朵露），3、4 阶没有实体，于是这两套边框在游戏里没有出现的机会。
 *
 * <p>为什么用 {@link UnmigratedTouhouBoss}：它已经包好「按血量阈值切卡 + 广播符卡名」，
 * 血条下方的符卡行（{@code underY + 4}）也能一起看。正式内容不得走这条通路，
 * 但试作实体正是它的适用对象。
 *
 * <p><b>数值不是配平结果</b>：{@code configSeconds()} 只借用该阶秒带常量，使血条读数
 * 看起来像一场正常战斗；{@code /gs_boss spawn &lt;id&gt; &lt;秒数&gt; &lt;挨弹数&gt;} 可以
 * 现场改短，目检血条不需要真打完。
 */
public abstract class BloodBarProbeBoss extends UnmigratedTouhouBoss {

    protected BloodBarProbeBoss(EntityType<? extends BloodBarProbeBoss> type, Level level) {
        super(type, level);
        // 试作实体：不产经验、不产掉落，避免污染存档进度。
        this.xpReward = 0;
    }

    /** 与正式 BOSS 同一套属性工厂，保证伤害/击退抗性/锁定半径与正式内容一致。 */
    public static AttributeSupplier.Builder createAttributes() {
        return bossAttributes(200.0D, 8.0D);
    }

    /** 复用露米娅的符卡表：已过 {@code BossCardLintTest}，且能让血条下那行符卡名有内容。 */
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
        return bossTier() >= 4
                ? BalanceNumbers.BOSS_SECONDS_T4
                : BalanceNumbers.BOSS_SECONDS_T3;
    }

    @Override
    protected int configHits() {
        return 8;
    }

    /** 试作实体不产正式掉落（星屑 / 信物 / 主题卡一律不掉）。 */
    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        // none
    }

    @Override
    protected int starDropCount() {
        return 0;
    }

    @Override
    public String toString() {
        return "BloodBarProbeBoss[tier=" + bossTier() + "]";
    }

    /**
     * 三阶试作：血条用 {@code frame_3.png}（金箔流光）。
     *
     * <p>刻意拆成两个子类而不是构造参数：{@link TouhouBoss#bossTier()} 的契约要求
     * 是编译期常数，不能随召唤方式/存档变化。
     */
    public static final class Tier3 extends BloodBarProbeBoss {
        public Tier3(EntityType<? extends Tier3> type, Level level) {
            super(type, level);
        }

        @Override
        public int bossTier() {
            return 3;
        }
    }

    /** 四阶试作：血条用 {@code frame_4.png}（彼岸花）。 */
    public static final class Tier4 extends BloodBarProbeBoss {
        public Tier4(EntityType<? extends Tier4> type, Level level) {
            super(type, level);
        }

        @Override
        public int bossTier() {
            return 4;
        }
    }
}
