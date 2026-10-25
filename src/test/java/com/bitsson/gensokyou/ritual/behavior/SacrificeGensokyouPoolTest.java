package com.bitsson.gensokyou.ritual.behavior;

import com.bitsson.gensokyou.ritual.RitualLootTable;
import com.bitsson.gensokyou.ritual.RitualMatch;
import com.bitsson.gensokyou.support.MinecraftTestBootstrap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 幻想乡信物带抽取池组装（世界无关纯内核）：无信物 / 仅低阶带 / 仅中阶带 / 双带四种情形。
 * 对应 add-gensokyo-material-ladder 的「幻想乡信物条件池」与
 * rebalance-early-progression 的「中阶带信物改为花精之滴」要求。
 *
 * <p>池组装只校验权重与条数（内核不触达 {@code Item} 方法，故物品置 null）。
 * 信物的<b>物品身份</b>由 {@link ToolSacrificeBehavior#HIGH_BAND_TOKEN_ID} 与反射守卫锁定——
 * 纯 JUnit 环境没有 mod 注册器，{@code ModItems} 的 DeferredHolder 不可解引用。
 */
class SacrificeGensokyouPoolTest {

    private static final double COMMONS_WEIGHT = 100.0;
    private static final double LOW_WEIGHT = 7.0;
    private static final double HIGH_WEIGHT = 11.0;

    @BeforeAll
    static void bootstrap() {
        MinecraftTestBootstrap.ensureStarted();
    }

    private static List<RitualLootTable.Weighted> weights(double... values) {
        List<RitualLootTable.Weighted> list = new ArrayList<>();
        for (double v : values) {
            list.add(new RitualLootTable.Weighted(null, v));
        }
        return list;
    }

    private static RitualLootTable table() {
        RitualLootTable.TierTable tier = new RitualLootTable.TierTable(
                "wood", List.of(), List.of(), List.of());
        return new RitualLootTable(ResourceLocation.parse("gensokyou:test"), null, 100, 0, 0,
                weights(1.0), weights(LOW_WEIGHT), weights(HIGH_WEIGHT), List.of(tier));
    }

    private static List<Double> poolWeights(boolean low, boolean high) {
        List<Double> out = new ArrayList<>();
        for (RitualLootTable.Weighted w
                : ToolSacrificeBehavior.poolFor(table(), "wood", false, false, low, high)) {
            out.add(w.weight());
        }
        out.sort(Double::compareTo);
        return out;
    }

    @Test
    void noTokenYieldsBaseOnly() {
        assertEquals(List.of(COMMONS_WEIGHT), poolWeights(false, false));
    }

    @Test
    void guideBookUnlocksLowBand() {
        assertEquals(List.of(LOW_WEIGHT, COMMONS_WEIGHT), poolWeights(true, false));
    }

    @Test
    void fairyNectarUnlocksHighBand() {
        assertEquals(List.of(HIGH_WEIGHT, COMMONS_WEIGHT), poolWeights(false, true));
    }

    @Test
    void bothTokensUnlockBothBands() {
        assertEquals(List.of(LOW_WEIGHT, HIGH_WEIGHT, COMMONS_WEIGHT), poolWeights(true, true));
    }

    /** 中阶带钥匙是花精之滴；隙间碎片退出信物位（仅保留蓝的主题信物身份）。 */
    @Test
    void highBandTokenIsFairyNectarNotSukimaFragment() {
        assertEquals(ResourceLocation.parse("gensokyou:fairy_nectar"),
                ToolSacrificeBehavior.HIGH_BAND_TOKEN_ID);
        assertTrue(!ToolSacrificeBehavior.HIGH_BAND_TOKEN_ID.equals(
                ResourceLocation.parse("gensokyou:sukima_fragment")));
    }

    /** 旧入口 {@code countSukimaFragments} 必须消失，防止有调用点漏改回隙间碎片。 */
    @Test
    void sukimaCountHelperIsGone() {
        assertThrows(NoSuchMethodException.class, () -> ToolSacrificeBehavior.class
                .getDeclaredMethod("countSukimaFragments", ServerLevel.class, RitualMatch.class));
    }

    /** 信物计数是纯读：多次结算后件数不变，且未被从台面取走。 */
    @Test
    void tokenCountingIsReadOnlyAndStable() {
        List<ItemStack> held = List.of(new ItemStack(Items.STICK), new ItemStack(Items.DIAMOND),
                new ItemStack(Items.STICK));
        assertEquals(2, ToolSacrificeBehavior.countTokens(held, Items.STICK));
        assertEquals(2, ToolSacrificeBehavior.countTokens(held, Items.STICK));
        assertEquals(3, held.size(), "计数不得移除台面上的栈");
        assertEquals(1, held.get(0).getCount(), "计数不得改动栈数量");
    }
}
