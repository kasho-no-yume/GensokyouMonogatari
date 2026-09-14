package com.bitsson.gensokyou.ritual;

import com.bitsson.gensokyou.block.entity.RitualCoreBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 会话状态核（世界无关）：触发决策、聚灵累积、足额进飞行、清态，
 * 及 NBT 往返（区块卸载重载续跑）。对应 spec zaohua-crafting 的执行序列与恢复要求。
 */
class CraftSessionTest {

    private static final ResourceLocation RECIPE = ResourceLocation.parse("gensokyou:zaohua_stone_t1");

    private static RitualCoreBlockEntity.CraftSession begin() {
        RitualCoreBlockEntity.CraftSession session = new RitualCoreBlockEntity.CraftSession();
        session.begin(RECIPE, 2000L);
        return session;
    }

    @Test
    void beginLocksRecipeCostAndEntersPaying() {
        var s = begin();
        assertEquals(RitualCoreBlockEntity.CraftPhase.PAYING, s.phase());
        assertEquals(RECIPE, s.recipeId());
        assertEquals(0L, s.collected());
        assertEquals(2000L, s.cost(), "会话容量口径 = 锁定配方 spCost");
        assertEquals(1L, s.sessionId());
    }

    @Test
    void collectedAccumulatesMonotonically() {
        var s = begin();
        s.addCollected(300L);
        s.addCollected(700L);
        assertEquals(1000L, s.collected());
    }

    @Test
    void enterFlightResetsTicksTracksIdsAndBumpsNotSession() {
        var s = begin();
        long firstId = s.sessionId();
        s.addCollected(2000L);
        s.enterFlight(List.of(11, 22, 33));
        assertEquals(RitualCoreBlockEntity.CraftPhase.FLIGHT, s.phase());
        assertEquals(0, s.ticks());
        assertEquals(List.of(11, 22, 33), s.flightIds());
        assertTrue(s.holdsFlight(firstId), "飞行代际应被本会话保护");
    }

    @Test
    void clearReturnsToIdleAndDropsLock() {
        var s = begin();
        s.enterFlight(List.of(5));
        s.clear();
        assertEquals(RitualCoreBlockEntity.CraftPhase.IDLE, s.phase());
        assertEquals(null, s.recipeId());
        assertEquals(0L, s.collected());
        assertTrue(s.flightIds().isEmpty());
        assertFalse(s.holdsFlight(s.sessionId()));
    }

    @Test
    void reBeginBumpsSessionIdSoOldFlightsBecomeOrphans() {
        var s = begin();
        long oldId = s.sessionId();
        s.enterFlight(List.of(1));
        s.clear();
        long newerId = s.begin(RECIPE, 2000L);
        assertTrue(newerId > oldId);
        assertFalse(s.holdsFlight(oldId), "上一代飞行实体应失去会话保护（自弃掉落）");
    }

    @Test
    void nbtRoundTripRestoresPayingProgress() {
        var s = begin();
        s.addCollected(1234L);
        CompoundTag tag = new CompoundTag();
        s.save(tag);

        var restored = new RitualCoreBlockEntity.CraftSession();
        restored.load(tag);
        assertEquals(RitualCoreBlockEntity.CraftPhase.PAYING, restored.phase());
        assertEquals(RECIPE, restored.recipeId());
        assertEquals(1234L, restored.collected());
        assertEquals(2000L, restored.cost(), "容量口径须随存档往返");
        assertEquals(s.sessionId(), restored.sessionId());
    }

    @Test
    void nbtRoundTripRestoresFlightIds() {
        var s = begin();
        s.enterFlight(List.of(7, 8));
        s.advanceFlight();
        s.advanceFlight();
        CompoundTag tag = new CompoundTag();
        s.save(tag);

        var restored = new RitualCoreBlockEntity.CraftSession();
        restored.load(tag);
        assertEquals(RitualCoreBlockEntity.CraftPhase.FLIGHT, restored.phase());
        assertEquals(2, restored.ticks());
        assertEquals(List.of(7, 8), restored.flightIds());
    }

    @Test
    void idleSessionWritesNothingToTag() {
        CompoundTag tag = new CompoundTag();
        new RitualCoreBlockEntity.CraftSession().save(tag);
        assertTrue(tag.isEmpty(), "空闲会话不得污染核心存档");
    }

    @Test
    void triggerDecisionTable() {
        assertEquals(com.bitsson.gensokyou.ritual.behavior.ZaohuaCraftingService.TriggerDecision.START,
                com.bitsson.gensokyou.ritual.behavior.ZaohuaCraftingService
                        .decisionFor(RitualCoreBlockEntity.CraftPhase.IDLE));
        assertEquals(com.bitsson.gensokyou.ritual.behavior.ZaohuaCraftingService.TriggerDecision.CANCEL,
                com.bitsson.gensokyou.ritual.behavior.ZaohuaCraftingService
                        .decisionFor(RitualCoreBlockEntity.CraftPhase.PAYING));
        assertEquals(com.bitsson.gensokyou.ritual.behavior.ZaohuaCraftingService.TriggerDecision.IGNORE,
                com.bitsson.gensokyou.ritual.behavior.ZaohuaCraftingService
                        .decisionFor(RitualCoreBlockEntity.CraftPhase.FLIGHT));
    }
}
