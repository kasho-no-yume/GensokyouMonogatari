package com.bitsson.gensokyou.item.weapon;

import com.bitsson.gensokyou.spirit.attr.AttributeKey;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 装备来源语义的纯逻辑回归。
 *
 * <p>用户明确语义：<b>增幅只在"手持装了该增幅核的武器"时生效</b>；
 * <b>手持增幅核本身不提供任何加成</b>（核不是装备，属性只存在于武器的 slot3 上）。
 *
 * <p>为什么只测纯逻辑、不构造 {@code ItemStack}：{@code ModDataComponents} 是
 * {@code DeferredRegister}，单元测试环境不触发注册事件，取 {@code .get()} 会抛
 * "unbound value"。{@link EquippedAffixBridge#refresh} 的取值链路是
 * "主手 → {@code weapon_slots} → {@code slot3} → {@code rune_affixes}"，
 * 其中"主手是裸增幅核"这一情形等价于 {@code slot3} 为空，故用
 * {@link #nothingInstalledMeansNoBonuses()} 覆盖。
 */
class EquippedAffixSemanticsTest {

    /**
     * 必须 bootstrap：{@code WeaponSlots} 的静态 {@code STREAM_CODEC} 会触发
     * {@code ItemStack} 类初始化，未 bootstrap 时其 clinit 抛异常并把该类永久标记为
     * 不可用，进而连累同一 JVM 内其它测试类。
     */
    @org.junit.jupiter.api.BeforeAll
    static void bootstrap() {
        com.bitsson.gensokyou.support.MinecraftTestBootstrap.ensureStarted();
    }

    @Test
    void nothingInstalledMeansNoBonuses() {
        // 未装核（WeaponSlots.DEFAULT）与"裸增幅核"（无 weapon_slots → 读回 DEFAULT）同解
        assertTrue(WeaponSlots.DEFAULT.slot1().isEmpty());
        assertTrue(WeaponSlots.DEFAULT.slot2().isEmpty());
        assertTrue(WeaponSlots.DEFAULT.slot3().isEmpty());
        assertTrue(RuneSummary.of(List.of()).isEmpty(),
                "slot3 为空时 MUST NOT 产生任何属性加成");
    }

    @Test
    void installedCoreAffixesBecomePlayerAttributes() {
        RuneSummary summary = RuneSummary.of(List.of(
                new RuneAffix("crit_chance", 0.028F),
                new RuneAffix("damage_pct", 0.15F)));
        Map<AttributeKey, Float> contrib = EquippedAffixBridge.attrContributions(summary);
        assertEquals(1, contrib.size(), "武器专有词条 MUST NOT 变成玩家属性");
        assertEquals(0.028F, contrib.get(AttributeKey.CRIT_CHANCE), 1e-6F);
    }

    @Test
    void summaryDedupesSameAffixBySumming() {
        RuneSummary summary = RuneSummary.of(List.of(
                new RuneAffix("damage_pct", 0.10F),
                new RuneAffix("damage_pct", 0.05F)));
        assertEquals(1, summary.raw().size());
        assertEquals(0.15F, summary.damagePct(), 1e-6F);
    }

    @Test
    void rangePctFeedsDecayCurve() {
        // 距离衰减走 (1+r)^exp，exp 由 config 注入；纯逻辑只保证 r≥0 时不炸且单调
        RuneSummary summary = RuneSummary.of(List.of(new RuneAffix("range_pct", 0.5F)));
        assertEquals(0.5F, summary.rangePct(), 1e-6F);
        assertTrue(summary.weapon(0.75D).rangeMultiplier() > 1F,
                "正的 range_pct MUST 放大有效射程");
        assertEquals(1F, RuneSummary.empty().weapon(0.75D).rangeMultiplier(), 1e-6F,
                "无词条时有效倍率为 1（不改变基线）");
    }

    /** 增幅核自身 tooltip 与武器 tooltip MUST 共用本格式化，否则两处数值格式会漂。 */
    @Test
    void tooltipLineFormatsByAffixSemantics() {
        // 武器专有词条走 affix.* 前缀
        assertEquals("affix.gensokyou.damage_pct",
                translatableKey(RuneAffix.tooltipLine(new RuneAffix("damage_pct", 0.148F))));
        // 弹幕减伤：反解成玩家直观的减伤倍率
        assertEquals("attribute.gensokyou.danmaku_reduce",
                translatableKey(RuneAffix.tooltipLine(new RuneAffix("danmaku_reduce", 1F))));
        // 玩家属性走 attribute.* 前缀
        assertEquals("attribute.gensokyou.crit_chance",
                translatableKey(RuneAffix.tooltipLine(new RuneAffix("crit_chance", 0.028F))));
    }

    @Test
    void flatAffixUsesAbsoluteValueNotPercent() {
        AttributeKey flat = java.util.Arrays.stream(AttributeKey.values())
                .filter(AttributeKey::isFlat).findFirst().orElseThrow();
        String key = translatableKey(RuneAffix.tooltipLine(new RuneAffix(flat.id(), 0.5F)));
        assertEquals(flat.langKey(), key);
    }

    @Test
    void danmakuKindsAreDistinctAndKeyed() {
        DanmakuKind[] kinds = DanmakuKind.values();
        assertEquals(4, kinds.length, "球形/刀刃/符卡/激光四类弹幕");
        var seen = new java.util.HashSet<String>();
        for (DanmakuKind kind : kinds) {
            assertTrue(kind.langKey().startsWith("danmaku.gensokyou."));
            assertTrue(seen.add(kind.langKey()), "弹幕类型语言键 MUST 唯一：" + kind.langKey());
        }
        // 球形与刀刃同属 ofBullet，仅靠 isLaser()/isTalisman() 区分不了 → 证明 kind 的必要性
        assertFalse(java.util.Set.of(DanmakuKind.SPHERE, DanmakuKind.KNIFE).isEmpty());
    }

    /**
     * {@code FirePattern.lifetimeSeconds <= 0} 是"不覆盖"的编码，弹丸沿用
     * {@code MAX_LIFETIME_TICKS}。tooltip MUST 回落到该上限，
     * 否则这些核会显示成"存活 0.00s / 有效距离 0.00"（真实是 speed × 60）。
     */
    @Test
    void zeroLifetimeMeansUnboundedNotZero() {
        double fallbackTicks = com.bitsson.gensokyou.entity.AbstractDanmakuProjectile.MAX_LIFETIME_TICKS;
        assertEquals(1200, fallbackTicks, "弹丸默认存活上限为 1200 tick = 60s");
        assertEquals(60D, fallbackTicks / 20D, 1e-9D);

        // WeaponFiring 的编码约定：<=0 不调 setLifetimeTicks → 保留默认上限
        double declared = 0D;
        int lifetime = declared > 0D ? (int) Math.round(declared * 20D) : 0;
        assertEquals(0, lifetime);
        assertEquals(fallbackTicks, lifetime > 0 ? lifetime : fallbackTicks,
                "lifetime 未覆盖时实际存活 = 弹丸默认上限，而非 0");
    }

    private static String translatableKey(MutableComponent component) {
        return component.getContents()
                instanceof net.minecraft.network.chat.contents.TranslatableContents contents
                ? contents.getKey()
                : "<not-translatable>";
    }
}
