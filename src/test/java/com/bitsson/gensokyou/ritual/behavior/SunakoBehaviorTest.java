package com.bitsson.gensokyou.ritual.behavior;

import com.bitsson.gensokyou.ritual.RitualBehaviors;
import com.bitsson.gensokyou.ritual.RitualExtraSlots;
import com.bitsson.gensokyou.ritual.brew.BrewReagentIndex;
import com.bitsson.gensokyou.support.MinecraftTestBootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 少名的行为契约：注册、一次性而非启停、额外槽校验、端点声明。
 *
 * <p>这些是"接错线就会静默失效"的那几根线——未注册 → 缓存回落 10000；声明了 out 速率
 * → 被路由选为供灵源；额外槽校验过宽 → 玩家能把石头当试剂塞进去。
 */
class SunakoBehaviorTest {

    private static SunakoBehavior behavior;

    @BeforeAll
    static void bootstrap() {
        MinecraftTestBootstrap.ensureStarted();
        behavior = new SunakoBehavior();
    }

    @Test
    void patternIsRegisteredWithABehavior() {
        assertTrue(RitualBehaviors.get(RitualBehaviors.SUNAKO)
                        .filter(SunakoBehavior.class::isInstance).isPresent(),
                "未注册行为的 pattern 会静默回落到 DEFAULT_CORE_CAPACITY=10000");
    }

    @Test
    void isOneShotNotToggleType() {
        assertTrue(behavior.handlesStartViaUiAction(), "批次型必须独占启停通道");
        assertEquals(1, behavior.uiActions(null, null, null, null).size(),
                "只注入一个「开始炼药」按钮");
        assertEquals(SunakoBehavior.ACTION_BREW,
                behavior.uiActions(null, null, null, null).getFirst().id());
    }

    @Test
    void respondsToRedstoneByDefault() {
        // 手动触发型 → 框架代管红石上升沿，玩家不在场也能起批
        assertTrue(behavior.redstoneTriggersUiAction());
        assertFalse(com.bitsson.gensokyou.block.RitualCoreBlock
                        .declaresOwnRedstoneHandler(behavior),
                "少名未覆写 onRedstonePulse，故框架代管而非自身实现");
    }

    @Test
    void isSinkOnlyNeverASpiritSource() {
        // out > 0 会让本仪式进入路由的候选来源，动态/错误声明都会让供灵网络出岔子
        assertEquals(0L, behavior.spiritOutRatePerSecond(null, null, null, null),
                "少名不产灵，out 速率必须为 0");
    }

    @Test
    void declaresOneExtraSlotForTheReagent() {
        assertTrue(behavior instanceof RitualExtraSlots);
        assertEquals(1, behavior.slotCount());
        assertEquals("gui.gensokyou.ritual.sunako.reagent_slot", behavior.labelKey());
        assertEquals(SunakoBrewing.REAGENT_SLOT, 0, "试剂占额外槽第 0 格");
    }

    @Test
    void reagentSlotAcceptsOnlyRealReagents() {
        assertTrue(behavior.isSlotValid(0, new ItemStack(Items.BLAZE_POWDER)));
        assertTrue(behavior.isSlotValid(0, new ItemStack(Items.PHANTOM_MEMBRANE)));
        assertTrue(behavior.isSlotValid(0, new ItemStack(Items.SPIDER_EYE)));
        assertTrue(behavior.isSlotValid(0, new ItemStack(Items.STONE)),
                "石头恰是原版 addStartMix 试剂（→ 寄生），是合法试剂");
        assertFalse(behavior.isSlotValid(0, new ItemStack(Items.DIRT)), "泥土不是炼药试剂");
        assertFalse(behavior.isSlotValid(0, new ItemStack(Items.COBBLESTONE)));
        assertFalse(behavior.isSlotValid(0, ItemStack.EMPTY));
        assertFalse(behavior.isSlotValid(0, null));
    }

    @Test
    void slotsBeyondTheDeclaredCountAreRejected() {
        assertFalse(behavior.isSlotValid(1, new ItemStack(Items.BLAZE_POWDER)),
                "只声明了 1 格，第 2 格不得收任何东西");
    }

    @Test
    void acceptsBatteryRefillFromSocket() {
        // 默认口径即"电池 → 缓存"；不覆写即成立
        assertTrue(behavior.refillsCacheFromSocket());
    }

    @Test
    void everyVanillaStartMixReagentIsAcceptedByTheSlot() {
        // 数据包声明的试剂与槽校验共用同一份映射；纯 JUnit 下数据包未加载，
        // 故此处只断言原版 startMix 集（发酵蜘蛛眼等纯数据包项由
        // BrewReagentIndexTest 的打包资源断言覆盖）。
        // 注：本 mod 物品（gensokyou:sanzu_flask）在纯 JUnit 下未进注册表，
        // 故"三途川水被槽校验拒绝"与"物品已注册"只能实机验证（见 tasks 第 11 组）。
        for (var reagent : BrewReagentIndex.vanillaCandidates()) {
            assertTrue(behavior.isSlotValid(0, new ItemStack(reagent)),
                    reagent + " 是原版 startMix 试剂，槽校验不得拒收");
        }
    }
}