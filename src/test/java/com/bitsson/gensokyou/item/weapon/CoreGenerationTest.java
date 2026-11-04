package com.bitsson.gensokyou.item.weapon;

import com.bitsson.gensokyou.balance.BuildNumbers;
import com.bitsson.gensokyou.balance.CoreAffixCatalog;
import com.bitsson.gensokyou.ritual.SeiiSession;
import com.bitsson.gensokyou.spirit.attr.AttributeKey;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 新版增幅核生成器回归（rune-affix-pool / seii-reroll-ritual）。
 *
 * <p>2.1 统一池、2.2 数量/唯一性/显式失败、2.3 低位偏置+组合预算+有限回退、
 * 2.4 软保底有界、3.3 跨版本预览采纳决策。确定性测试优先；统计测试用固定种子。
 */
class CoreGenerationTest {

    private static List<CoreAffixCatalog.Definition> defaults() {
        return CoreAffixCatalog.defaults();
    }

    private static Map<String, CoreAffixCatalog.Definition> byId() {
        return defaults().stream().collect(Collectors.toMap(CoreAffixCatalog.Definition::id, d -> d));
    }

    private static boolean isAttrId(String id) {
        return AttributeKey.byId(id) != null;
    }

    @Test
    void firstRollAndRerollShareUnifiedPool() {
        // 回归：首次生成不得只有武器域、玩家属性域必须可出现在两条路径上
        RandomSource random = RandomSource.create(20261104L);
        Set<String> firstPathAttr = new HashSet<>();
        Set<String> firstPathWeapon = new HashSet<>();
        Set<String> rerollPathAttr = new HashSet<>();
        Set<String> rerollPathWeapon = new HashSet<>();
        for (int i = 0; i < 400; i++) {
            for (RuneAffix a : SeiiNumbers.roll(3, 0, random)) {
                (isAttrId(a.affixId()) ? firstPathAttr : firstPathWeapon).add(a.affixId());
            }
            for (RuneAffix a : SeiiNumbers.roll(3, 12, random)) {
                (isAttrId(a.affixId()) ? rerollPathAttr : rerollPathWeapon).add(a.affixId());
            }
        }
        assertTrue(firstPathAttr.size() >= 4, "首次生成应能Roll出玩家属性域词条，got " + firstPathAttr);
        assertTrue(firstPathWeapon.size() >= 2, "首次生成应能Roll出武器域词条，got " + firstPathWeapon);
        assertTrue(rerollPathAttr.size() >= 4, "洗练应能Roll出玩家属性域词条，got " + rerollPathAttr);
        assertTrue(rerollPathWeapon.size() >= 2, "洗练应能Roll出武器域词条，got " + rerollPathWeapon);
    }

    @Test
    void countsPerTierAndUniquenessBothPaths() {
        RandomSource random = RandomSource.create(20261105L);
        int[] expected = {3, 4, 5};
        for (int tier = 1; tier <= 3; tier++) {
            for (int i = 0; i < 200; i++) {
                for (int rerolls : new int[]{0, 12}) {
                    List<RuneAffix> rolled = SeiiNumbers.roll(tier, rerolls, random);
                    assertEquals(expected[tier - 1], rolled.size(),
                            "T" + tier + " 应生成 " + expected[tier - 1] + " 条（rerolls=" + rerolls + "）");
                    Set<String> ids = rolled.stream().map(RuneAffix::affixId).collect(Collectors.toSet());
                    assertEquals(expected[tier - 1], ids.size(), "同枚核词条 id 不应重复");
                }
            }
        }
    }

    @Test
    void rerollUsesOwnTierCountNotT1Count() {
        // 回归：洗练误用 T1 条数的 bug —— T3 洗练必须 5 条
        RandomSource random = RandomSource.create(20261106L);
        for (int i = 0; i < 100; i++) {
            assertEquals(5, SeiiNumbers.roll(3, 3, random).size(), "T3 洗练应生成 5 条");
            assertEquals(4, SeiiNumbers.roll(2, 3, random).size(), "T2 洗练应生成 4 条");
        }
    }

    @Test
    void lawfulBandsAndCountsHoldUnderMaxPity() {
        // 保底不越界：t=cap 时所有值仍在带内、条数不变、且整核合法
        Map<String, CoreAffixCatalog.Definition> defs = byId();
        RandomSource random = RandomSource.create(20261107L);
        for (int tier = 1; tier <= 3; tier++) {
            for (int i = 0; i < 100; i++) {
                List<RuneAffix> rolled = SeiiNumbers.roll(tier, 1000, random);
                assertEquals(new int[]{3, 4, 5}[tier - 1], rolled.size());
                assertTrue(BuildNumbers.legalCore(SeiiNumbers.budgetMap(rolled), tier),
                        "满保底的核也必须通过组合预算");
                for (RuneAffix a : rolled) {
                    CoreAffixCatalog.Definition def = defs.get(a.affixId());
                    double v = a.value();
                    switch (def.kind()) {
                        case WEAPON_ABS, ATTR_ABS -> assertTrue(v <= Math.max(def.loOf(tier), def.hiOf(tier)) + 1e-6,
                                a.affixId() + " 超过带上沿: " + v);
                        case WARD_MIT -> assertTrue(v <= SeiiNumbers.wardExponentFor(def.hiOf(tier)) + 1e-6,
                                a.affixId() + " 超过护壁带上沿: " + v);
                    }
                }
            }
        }
    }

    @Test
    void softPityImprovesExpectedQuality() {
        // 按各词条带归一化后的平均成色分位比较：t=0 应落在 u^3 低区、t=1 退化为均匀
        RandomSource random = RandomSource.create(20261108L);
        double meanNoPity = 0D, meanMaxPity = 0D;
        int n = 1500;
        for (int i = 0; i < n; i++) {
            meanNoPity += meanFraction(SeiiNumbers.roll(3, 0, random, SeiiNumbers.tunables()), 3);
            meanMaxPity += meanFraction(SeiiNumbers.roll(3, 100, random, SeiiNumbers.tunables()), 3);
        }
        meanNoPity /= n;
        meanMaxPity /= n;
        // 注意：组合预算拒绝采样会压低两端（尤其满保底的高值端），故只要求保底显著更优，
        // 不要求退化为均匀分布的 0.5。
        assertTrue(meanMaxPity > meanNoPity + 0.08D,
                "满保底归一化成色应显著高于无保底: noPity=" + meanNoPity + " maxPity=" + meanMaxPity);
    }

    private static double meanFraction(List<RuneAffix> rolled, int tier) {
        Map<String, CoreAffixCatalog.Definition> defs = byId();
        double sum = 0D;
        for (RuneAffix a : rolled) {
            CoreAffixCatalog.Definition def = defs.get(a.affixId());
            switch (def.kind()) {
                case WEAPON_ABS, ATTR_ABS ->
                        sum += (a.value() - def.loOf(tier)) / (def.hiOf(tier) - def.loOf(tier));
                case WARD_MIT -> {
                    double lo = SeiiNumbers.wardExponentFor(def.loOf(tier));
                    double hi = SeiiNumbers.wardExponentFor(def.hiOf(tier));
                    sum += (a.value() - lo) / (hi - lo);
                }
            }
        }
        return sum / rolled.size();
    }

    @Test
    void abusiveBandsFallBackToDeterministicLegalRoll() {
        // 诱导 100% 拒绝：所有属性带被 config 抬到极值 → 64 次无合法候选 → 确定性低位回退
        List<String> hugeAttr = new ArrayList<>();
        for (String id : CoreAffixCatalog.attributeIds()) {
            if (id.equals(AttributeKey.DANMAKU_REDUCE.id())) {
                continue;
            }
            hugeAttr.add("1," + id + ",0.40,0.75,6");
            hugeAttr.add("2," + id + ",0.40,0.75,6");
            hugeAttr.add("3," + id + ",0.40,0.75,6");
        }
        List<String> hugeWeapon = List.of(
                "damage_pct,1.1,1.2,10,1", "damage_pct,1.1,1.2,10,2", "damage_pct,1.1,1.2,10,3",
                "attack_rate_pct,2.8,3.0,10,1", "attack_rate_pct,2.8,3.0,10,2", "attack_rate_pct,2.8,3.0,10,3",
                "spirit_cost_pct,-1.2,-1.1,10,1", "spirit_cost_pct,-1.2,-1.1,10,2", "spirit_cost_pct,-1.2,-1.1,10,3",
                "range_pct,1.0,1.2,10,1", "range_pct,1.0,1.2,10,2", "range_pct,1.0,1.2,10,3");
        // 护壁也抬到 ~94% 减伤：ΔP≈4.06，norm≈36 ⇒ 必超预算
        SeiiNumbers.Tunables tune = new SeiiNumbers.Tunables(
                hugeWeapon, hugeAttr, List.of(0.94D, 0.95D, 0.94D, 0.95D, 0.94D, 0.95D), 6, 10,
                new int[]{3, 4, 5});
        List<RuneAffix> first = SeiiNumbers.roll(1, 0, RandomSource.create(1L), tune);
        List<RuneAffix> second = SeiiNumbers.roll(1, 0, RandomSource.create(2L), tune);
        assertEquals(3, first.size(), "回退也必须给满条数");
        assertEquals(first, second, "回退必须是确定性的（不依赖随机源）");
        assertTrue(BuildNumbers.legalCore(SeiiNumbers.budgetMap(first), 1), "回退结果必须合法");
        // 不补极品：回退值 MUST NOT 超过带下沿（保底不放大、缩放只向 0）
        Map<String, CoreAffixCatalog.Definition> defs = byId();
        for (RuneAffix a : first) {
            assertTrue(Math.abs(a.value()) <= Math.abs(floorOf(defs.get(a.affixId()), 1)) + 1e-6F,
                    "回退值的量级不得超过带下沿: " + a.affixId() + "=" + a.value());
        }
    }

    private static double floorOf(CoreAffixCatalog.Definition def, int tier) {
        return switch (def.kind()) {
            case WEAPON_ABS, ATTR_ABS -> def.loOf(tier);
            case WARD_MIT -> SeiiNumbers.wardExponentFor(def.loOf(tier));
        };
    }

    @Test
    void averageRollsPassCombinationBudget() {
        // 抽样证明：正常生成的核全部（而非仅回退）合法
        RandomSource random = RandomSource.create(20261109L);
        for (int i = 0; i < 300; i++) {
            List<RuneAffix> rolled = SeiiNumbers.roll(3, 0, random);
            assertTrue(BuildNumbers.legalCore(SeiiNumbers.budgetMap(rolled), 3),
                    "正常 roll 不应撞预算: " + rolled);
        }
    }

    @Test
    void acceptActionMapsVersions() {
        assertEquals(SeiiNumbers.AcceptAction.APPLY_NEW, SeiiNumbers.acceptAction(SeiiNumbers.RULE_VERSION));
        assertEquals(SeiiNumbers.AcceptAction.APPLY_LEGACY, SeiiNumbers.acceptAction(SeiiNumbers.LEGACY_RULE_VERSION));
        assertEquals(SeiiNumbers.AcceptAction.REJECT_UNKNOWN, SeiiNumbers.acceptAction(7));
        assertEquals(SeiiNumbers.AcceptAction.REJECT_UNKNOWN, SeiiNumbers.acceptAction(-1));
    }

    @Test
    void pendingRoundTripPreservesRuleVersion() {
        SeiiSession session = new SeiiSession();
        List<RuneAffix> before = List.of(new RuneAffix("damage_pct", 0.10f));
        List<RuneAffix> after = List.of(new RuneAffix("damage_pct", 0.15f), new RuneAffix("crit_chance", 0.02f));
        session.stage(new SeiiSession.Pending(2, 3, before, after, 4, SeiiNumbers.RULE_VERSION));
        session.promoteReview();
        CompoundTag tag = new CompoundTag();
        session.save(tag);

        SeiiSession loaded = new SeiiSession();
        loaded.load(tag);
        assertEquals(SeiiSession.Phase.REVIEW, loaded.phase());
        SeiiSession.Pending p = loaded.pending();
        assertEquals(SeiiNumbers.RULE_VERSION, p.ruleVersion());
        assertEquals(after, p.after());
        assertEquals(before, p.before());
        assertEquals(4, p.pityBefore());
    }

    @Test
    void legacyPendingWithoutVersionLoadsAsLegacy() {
        // 旧档（无 ruleVersion 字段）→ legacy 0 → 采纳走身份转换
        SeiiSession session = new SeiiSession();
        List<RuneAffix> affixes = List.of(new RuneAffix("attack_rate_pct", 0.10f));
        session.stage(new SeiiSession.Pending(1, 1, affixes, affixes, 0, SeiiNumbers.RULE_VERSION));
        session.promoteReview();
        CompoundTag tag = new CompoundTag();
        session.save(tag);
        // 模拟旧档：抹掉 ruleVersion 字段
        CompoundTag pending = tag.getCompound("SeiiPending");
        pending.remove("ruleVersion");
        tag.put("SeiiPending", pending);

        SeiiSession loaded = new SeiiSession();
        loaded.load(tag);
        SeiiSession.Pending p = loaded.pending();
        assertEquals(SeiiNumbers.LEGACY_RULE_VERSION, p.ruleVersion());
        assertEquals(SeiiNumbers.AcceptAction.APPLY_LEGACY, SeiiNumbers.acceptAction(p.ruleVersion()));
    }

    @Test
    void definitionTableIsSelfConsistent() {
        // 受控定义表自检 + 22 种类型每个核阶都有带且合法（validate 在 merged 内自动执行；此处显式暴露）
        List<CoreAffixCatalog.Definition> defs = defaults();
        assertEquals(CoreAffixCatalog.attributeIds().size() + CoreAffixCatalog.weaponIds().size(),
                defs.size());
        for (CoreAffixCatalog.Definition def : defs) {
            for (int tier = 1; tier <= 3; tier++) {
                assertTrue(def.loOf(tier) < def.hiOf(tier), def.id() + " 带倒置");
                assertTrue(def.weightOf(tier) > 0, def.id() + " 权重非正");
            }
            assertTrue(CoreAffixCatalog.consumerOf(def.id()) != null
                            && !CoreAffixCatalog.consumerOf(def.id()).isBlank(),
                    def.id() + " 没有登记的真实消费路径");
        }
    }
}
