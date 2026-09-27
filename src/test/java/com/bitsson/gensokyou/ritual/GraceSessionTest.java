package com.bitsson.gensokyou.ritual;

import com.bitsson.gensokyou.block.entity.RitualCoreBlockEntity;
import com.bitsson.gensokyou.ritual.behavior.YaoyorozuGraceService;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 八百万神恩会话状态核（世界无关）：触发决策表、initiator 锁定、先入账后演出的
 * 阶段序列、NBT 持久化语义（仅 PAYING 复活；PERFORM/REVIEW 清退——效果已入账/预览当场制）。
 * 对应 spec yaoyorozu-grace-ritual 全部事务性要求。
 */
class GraceSessionTest {

    private static final ResourceLocation RECIPE =
            ResourceLocation.parse("gensokyou:grace_advance_1");
    private static final UUID WHO = UUID.randomUUID();

    private static RitualCoreBlockEntity.GraceSession begin() {
        RitualCoreBlockEntity.GraceSession s = new RitualCoreBlockEntity.GraceSession();
        s.begin(RECIPE, 200L, WHO, 1, false);
        return s;
    }

    @Test
    void triggerDecisionTable() {
        assertEquals(YaoyorozuGraceService.TriggerDecision.START,
                YaoyorozuGraceService.decisionFor(RitualCoreBlockEntity.GracePhase.IDLE));
        assertEquals(YaoyorozuGraceService.TriggerDecision.CANCEL,
                YaoyorozuGraceService.decisionFor(RitualCoreBlockEntity.GracePhase.PAYING));
        assertEquals(YaoyorozuGraceService.TriggerDecision.IGNORE,
                YaoyorozuGraceService.decisionFor(RitualCoreBlockEntity.GracePhase.PERFORM));
        assertEquals(YaoyorozuGraceService.TriggerDecision.START,
                YaoyorozuGraceService.decisionFor(RitualCoreBlockEntity.GracePhase.REVIEW),
                "REVIEW 再启动=作废旧预览执行新配方");
    }

    @Test
    void beginLocksInitiatorTierAndCost() {
        RitualCoreBlockEntity.GraceSession s = begin();
        assertEquals(RitualCoreBlockEntity.GracePhase.PAYING, s.phase());
        assertEquals(WHO, s.initiator());
        assertEquals(1, s.tier());
        assertFalse(s.refine());
        assertEquals(200L, s.cost());
        assertEquals(1L, s.sessionId());
    }

    @Test
    void advanceThenPerformThenClearSequence() {
        RitualCoreBlockEntity.GraceSession s = begin();
        s.addCollected(120L);
        s.addCollected(80L);
        assertEquals(200L, s.collected());
        s.enterPerform();
        assertEquals(RitualCoreBlockEntity.GracePhase.PERFORM, s.phase());
        assertEquals(0, s.ticks());
        s.advancePerform();
        assertEquals(1, s.ticks());
        s.clear();
        assertEquals(RitualCoreBlockEntity.GracePhase.IDLE, s.phase());
        assertNull(s.initiator());
        assertEquals(0L, s.cost());
    }

    @Test
    void nbtRoundTripRestoresOnlyPaying() {
        RitualCoreBlockEntity.GraceSession s = begin();
        s.addCollected(50L);
        CompoundTag tag = new CompoundTag();
        s.save(tag);
        RitualCoreBlockEntity.GraceSession loaded = new RitualCoreBlockEntity.GraceSession();
        loaded.load(tag);
        assertEquals(RitualCoreBlockEntity.GracePhase.PAYING, loaded.phase());
        assertEquals(50L, loaded.collected());
        assertEquals(WHO, loaded.initiator());
        assertEquals(RECIPE, loaded.recipeId());
        assertEquals(1, loaded.tier());

        RitualCoreBlockEntity.GraceSession performing = begin();
        performing.enterPerform();
        CompoundTag performTag = new CompoundTag();
        performing.save(performTag);
        RitualCoreBlockEntity.GraceSession revived = new RitualCoreBlockEntity.GraceSession();
        revived.load(performTag);
        assertEquals(RitualCoreBlockEntity.GracePhase.IDLE, revived.phase(),
                "PERFORM 存档恢复视为演出已结束（效果已入账，不回滚不重演）");

        RitualCoreBlockEntity.GraceSession review = begin();
        review.stageRefine(new com.bitsson.gensokyou.spirit.grace.GraceNumbers.GraceRoll(
                1, 200F, 8F,
                java.util.Map.of(com.bitsson.gensokyou.spirit.attr.AttributeKey.DANMAKU_REDUCE, 0.1F)));
        review.promoteReview();
        CompoundTag reviewTag = new CompoundTag();
        review.save(reviewTag);
        assertFalse(reviewTag.isEmpty(),
                "REVIEW 入存档（待决永久存续，关界面/重启都不作废）");
        RitualCoreBlockEntity.GraceSession restored = new RitualCoreBlockEntity.GraceSession();
        restored.load(reviewTag);
        assertEquals(RitualCoreBlockEntity.GracePhase.REVIEW, restored.phase(),
                "REVIEW 永久存续：重启后仍在待决态");
        assertEquals(WHO, restored.initiator(), "决策权仍归原发起者");
        // 待决 roll 本身也必须落盘：否则重启后 REVIEW 回来了却无可决策内容，
        // 玩家既看不到结果也无法"全收 / 保留"。
        assertNotNull(restored.pendingRefine(), "REVIEW 的待决 roll 必须随存档恢复");
        assertEquals(1, restored.pendingRefine().tier());
        assertEquals(200F, restored.pendingRefine().maxGain());
        assertEquals(8F, restored.pendingRefine().powerGain());
        assertEquals(Map.of(
                        com.bitsson.gensokyou.spirit.attr.AttributeKey.DANMAKU_REDUCE, 0.1F),
                restored.pendingRefine().contributions(),
                "待决 roll 的词条贡献必须逐条保真");
    }

    @Test
    void reviewWithoutPendingRollFallsBackToIdle() {
        // 老存档（REVIEW 相位已写盘但没有 GracePending）不得停在"有相位、无内容"的死状态
        CompoundTag legacy = new CompoundTag();
        legacy.putString("GracePhase", RitualCoreBlockEntity.GracePhase.REVIEW.name());
        legacy.putLong("GraceSession", 7L);
        RitualCoreBlockEntity.GraceSession loaded = new RitualCoreBlockEntity.GraceSession();
        loaded.load(legacy);
        assertEquals(RitualCoreBlockEntity.GracePhase.IDLE, loaded.phase());
        assertNull(loaded.pendingRefine());
    }

    @Test
    void effectParsingAcceptsOnlyGraceFormat() {
        assertEquals(new YaoyorozuGraceService.EffectInfo(3, false),
                YaoyorozuGraceService.effectOf(recipe("grace:advance_3")));
        assertEquals(new YaoyorozuGraceService.EffectInfo(5, true),
                YaoyorozuGraceService.effectOf(recipe("grace:refine_5")));
        assertNull(YaoyorozuGraceService.effectOf(recipe("grace:advance_0")), "阶级越界拒收");
        assertNull(YaoyorozuGraceService.effectOf(recipe("grace:advance_6")), "阶级越界拒收");
        assertNull(YaoyorozuGraceService.effectOf(recipe("gensokyou:something")));
        assertNull(YaoyorozuGraceService.effectOf(recipe(null)));
    }

    private static RitualRecipe recipe(String effect) {
        return new RitualRecipe(
                ResourceLocation.parse("gensokyou:t"),
                ResourceLocation.parse("gensokyou:kami_no_megumi_circle"),
                RitualRecipe.Mode.ACTIVATION,
                RitualRecipe.MatchMode.EXACT,
                1, 0,
                List.of(new RitualRecipe.Ingredient(null,
                        net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM,
                                ResourceLocation.parse("minecraft:planks")), 1)),
                100, null, effect);
    }
}
