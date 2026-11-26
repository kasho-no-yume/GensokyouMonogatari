package com.bitsson.gensokyou.quest;

import com.bitsson.gensokyou.support.MinecraftTestBootstrap;
import com.mojang.serialization.JsonOps;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class QuestProgressTest {
    static final ResourceLocation NPC = id("akyuu"), OTHER = id("reimu"), PAPER = ResourceLocation.parse("minecraft:paper");
    static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("gensokyou", path); }
    @BeforeAll static void bootstrap() { MinecraftTestBootstrap.ensureStarted(); }
    static QuestDefinition definition(String name, List<QuestDefinition.Stage> stages, List<QuestDefinition.ItemAmount> rewards) {
        return new QuestDefinition(id(name), QuestDefinition.Kind.SIDE, QuestDefinition.Scope.PLAYER,
                "title", "description", NPC, NPC, stages, rewards, -1, false);
    }
    static QuestDefinition.Stage stage(String name, QuestObjectives.Objective... goals) {
        return new QuestDefinition.Stage(name, "stage", List.of(goals), Optional.empty());
    }
    @Test void completionLatchesAndParallelGoalsWaitForOneAnother() {
        var def = definition("parallel", List.of(stage("one", new QuestObjectives.HaveItems(PAPER, 4, "paper"),
                new QuestObjectives.TalkToNpc(NPC, id("ready"), "talk", "question", "yes"))), List.of());
        var quest = QuestProgress.start(def);
        quest = quest.apply(def, new QuestObjectives.Poll(), item -> 4);
        assertEquals(List.of(4, 0), quest.progress());
        assertFalse(quest.ready());
        quest = quest.apply(def, new QuestObjectives.Poll(), item -> 0);
        assertEquals(4, quest.progress().getFirst());
        assertFalse(quest.apply(def, new QuestObjectives.Talk(OTHER, id("ready")), item -> 0).ready());
        assertFalse(quest.apply(def, new QuestObjectives.Talk(NPC, id("no")), item -> 0).ready());
        quest = quest.apply(def, new QuestObjectives.Talk(NPC, id("ready")), item -> 0);
        assertTrue(quest.ready());
        assertEquals(quest, quest.apply(def, new QuestObjectives.Poll(), item -> 0));
    }
    @Test void priorEventsDoNotLeakIntoNewInstancesOrLaterStages() {
        var zombie = ResourceLocation.parse("minecraft:zombie");
        var def = definition("linear", List.of(stage("first", new QuestObjectives.KillEntities(zombie, 1, "kill")),
                stage("second", new QuestObjectives.KillEntities(zombie, 1, "kill"))), List.of());
        var old = QuestProgress.start(def).apply(def, new QuestObjectives.Kill(zombie), item -> 0);
        var fresh = QuestProgress.start(def);
        assertNotEquals(old.instance(), fresh.instance());
        assertEquals(0, fresh.progress().getFirst());
        assertEquals(1, old.stage());
        assertEquals(0, old.progress().getFirst(), "the same kill cannot complete two stages");
        assertTrue(old.apply(def, new QuestObjectives.Kill(zombie), item -> 0).ready());
    }
    @Test void persistentPlayerStateIsIndependentAndInvalidatesOnlyAtANewResourceEpoch() {
        var def = definition("persistent", List.of(stage("one", new QuestObjectives.HaveItems(PAPER, 4, "paper"))), List.of());
        var q = QuestProgress.start(def).apply(def, new QuestObjectives.Poll(), item -> 4);
        var first = new PlayerQuestData(3, List.of(q), q.instance());
        var second = new PlayerQuestData(3, List.of(), "");
        var nbt = PlayerQuestData.CODEC.encodeStart(NbtOps.INSTANCE, first).getOrThrow();
        var restored = PlayerQuestData.CODEC.parse(NbtOps.INSTANCE, nbt).getOrThrow();
        assertEquals(first, restored.forEpoch(3));
        assertTrue(second.quests().isEmpty());
        assertTrue(restored.forEpoch(4).quests().isEmpty());
        assertTrue(restored.forEpoch(4).tracked().isEmpty());
        var completed = restored.replace(q.finish());
        assertTrue(completed.tracked().isEmpty());
        assertTrue(completed.quests().getFirst().complete());
    }
    @Test void mainlineCursorSurvivesRestartAndReloadAndMovesThroughTheOrderedSequence() {
        var first = new QuestDefinition(id("main_one"), QuestDefinition.Kind.MAIN, QuestDefinition.Scope.WORLD,
                "title", "description", NPC, NPC, List.of(stage("first", new QuestObjectives.TalkToNpc(NPC, id("yes"), "talk", "question", "yes")),
                stage("second", new QuestObjectives.HaveItems(PAPER, 4, "paper"))), List.of(), 0, false);
        var next = new QuestDefinition(id("main_two"), QuestDefinition.Kind.MAIN, QuestDefinition.Scope.WORLD,
                "title", "description", NPC, NPC, List.of(stage("next", new QuestObjectives.HaveItems(PAPER, 1, "paper"))), List.of(), 1, false);
        var stages = List.of(new QuestDefinitionLoader.MainStage(first, 0), new QuestDefinitionLoader.MainStage(first, 1),
                new QuestDefinitionLoader.MainStage(next, 0));
        var world = new WorldQuestData();
        QuestMainline.apply(world, QuestMainline.current(world, stages).orElseThrow(), new QuestObjectives.Talk(NPC, id("yes")), item -> 0);
        assertEquals(1, world.cursor());
        world = WorldQuestData.load(world.save(new net.minecraft.nbt.CompoundTag(), null), null);
        world.reload();
        assertEquals(1, world.cursor());
        assertEquals("second", QuestMainline.current(world, stages).orElseThrow().definition().stages().get(1).id());
        QuestMainline.apply(world, QuestMainline.current(world, stages).orElseThrow(), new QuestObjectives.Poll(), item -> 4);
        assertEquals(2, world.cursor());
        assertEquals(next.id(), QuestMainline.current(world, stages).orElseThrow().definition().id());
        assertTrue(QuestMainline.current(world, List.of()).isEmpty());
        assertEquals(2, world.cursor(), "missing resources cannot rewind the world");
    }
    @Test void snapshotKeepsPersonalMarkersAndTrackingIndependentFromSharedProgress() {
        var def = definition("views", List.of(stage("one", new QuestObjectives.HaveItems(PAPER, 1, "paper"))), List.of());
        var q = QuestProgress.start(def);
        var a = new QuestSnapshot(List.of(new QuestSnapshot.Entry(def, q, true)), Optional.empty(), Map.of(NPC, "question"));
        var b = new QuestSnapshot(List.of(new QuestSnapshot.Entry(def, q, false)), Optional.empty(), Map.of());
        assertNotEquals(a.markers(), b.markers());
        assertFalse(b.entries().getFirst().tracked());
        assertEquals(a, QuestSnapshot.CODEC.parse(JsonOps.INSTANCE, QuestSnapshot.CODEC.encodeStart(JsonOps.INSTANCE, a).getOrThrow()).getOrThrow());
    }
}
