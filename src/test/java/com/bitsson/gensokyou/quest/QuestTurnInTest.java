package com.bitsson.gensokyou.quest;

import com.bitsson.gensokyou.support.MinecraftTestBootstrap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.bitsson.gensokyou.quest.QuestProgressTest.*;
import static org.junit.jupiter.api.Assertions.*;

class QuestTurnInTest {
    @BeforeAll static void bootstrap() { MinecraftTestBootstrap.ensureStarted(); }
    private QuestDefinition delivery(ResourceLocation recipient, int needed) {
        return definition("delivery", List.of(stage("one", new QuestObjectives.SubmitItems(PAPER, needed, recipient, "paper"))),
                List.of(new QuestDefinition.ItemAmount(ResourceLocation.parse("minecraft:diamond"), 1)));
    }
    @Test void sameNpcDeliveryAndRewardAreAtomicAndCannotBeRepeated() {
        var def = delivery(NPC, 4);
        var source = List.of(new ItemStack(Items.PAPER, 4));
        var result = QuestTurnIn.plan(def, QuestProgress.start(def), NPC, true, source);
        assertTrue(result.success());
        assertTrue(result.progress().complete());
        assertTrue(result.inventory().getFirst().is(Items.DIAMOND), "debit frees the slot needed for reward");
        assertEquals(4, source.getFirst().getCount(), "planning cannot mutate real inventory");
        assertFalse(QuestTurnIn.plan(def, result.progress(), NPC, true, result.inventory()).success());
        assertFalse(QuestTurnIn.plan(def, result.progress(), NPC, false, result.inventory()).success());
    }
    @Test void fullRewardInventoryLeavesBothMaterialsAndProgressUntouched() {
        var def = delivery(NPC, 4);
        var quest = QuestProgress.start(def);
        var source = List.of(new ItemStack(Items.PAPER, 5));
        var result = QuestTurnIn.plan(def, quest, NPC, true, source);
        assertEquals(QuestTurnIn.Failure.INVENTORY_FULL, result.failure().orElseThrow());
        assertEquals(quest, result.progress());
        assertEquals(5, result.inventory().getFirst().getCount());
        assertEquals(5, source.getFirst().getCount());
    }
    @Test void remoteRecipientDoesNotGrantRewardsAndReturningDoesNotDebitAgain() {
        var def = delivery(OTHER, 4);
        var q = QuestProgress.start(def);
        assertFalse(QuestTurnIn.plan(def, q, NPC, true, List.of(new ItemStack(Items.PAPER, 4))).success());
        var delivered = QuestTurnIn.plan(def, q, OTHER, true, List.of(new ItemStack(Items.PAPER, 4)));
        assertTrue(delivered.progress().ready());
        assertFalse(delivered.progress().complete());
        assertTrue(delivered.inventory().getFirst().isEmpty());
        var blocked = QuestTurnIn.plan(def, delivered.progress(), NPC, false, List.of(new ItemStack(Items.STONE, 64)));
        assertEquals(QuestTurnIn.Failure.INVENTORY_FULL, blocked.failure().orElseThrow());
        assertTrue(blocked.progress().ready());
        var claimed = QuestTurnIn.plan(def, delivered.progress(), NPC, false, List.of(ItemStack.EMPTY));
        assertTrue(claimed.progress().complete());
        assertTrue(claimed.inventory().getFirst().is(Items.DIAMOND));
    }
    @Test void parallelDebitsAggregateRepeatedItemsBeforeAnyConsumption() {
        var def = definition("aggregate", List.of(stage("one", new QuestObjectives.SubmitItems(PAPER, 3, NPC, "one"),
                new QuestObjectives.SubmitItems(PAPER, 3, NPC, "two"))), List.of());
        var source = List.of(new ItemStack(Items.PAPER, 5));
        var q = QuestProgress.start(def);
        var result = QuestTurnIn.plan(def, q, NPC, true, source);
        assertEquals(QuestTurnIn.Failure.MATERIALS, result.failure().orElseThrow());
        assertEquals(q, result.progress());
        assertEquals(5, source.getFirst().getCount());
    }
}
