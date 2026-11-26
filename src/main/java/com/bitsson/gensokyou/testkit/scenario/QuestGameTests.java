package com.bitsson.gensokyou.testkit.scenario;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.dialogue.DialogueGraph;
import com.bitsson.gensokyou.dialogue.DialogueManager;
import com.bitsson.gensokyou.dialogue.DialogueNode;
import com.bitsson.gensokyou.dialogue.DialogueOption;
import com.bitsson.gensokyou.quest.PlayerQuestData;
import com.bitsson.gensokyou.quest.QuestDefinitionLoader;
import com.bitsson.gensokyou.quest.QuestDialogueAction;
import com.bitsson.gensokyou.quest.QuestObjectives;
import com.bitsson.gensokyou.quest.QuestProgress;
import com.bitsson.gensokyou.quest.QuestService;
import com.bitsson.gensokyou.registry.ModEntityTypes;
import com.bitsson.gensokyou.spirit.ModAttachments;
import com.bitsson.gensokyou.worldgen.human_village.AkyuuResidence;
import com.bitsson.gensokyou.worldgen.human_village.BuildingInstance;
import com.bitsson.gensokyou.worldgen.human_village.HumanVillagePlan;
import com.bitsson.gensokyou.worldgen.human_village.HumanVillageSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.neoforge.attachment.AttachmentInternals;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Real attachments, server dialogue validation, private views, and the shipped test commissions. */
@GameTestHolder(Gensokyou.MODID)
@PrefixGameTestTemplate(false)
public final class QuestGameTests {
    private static final String TEMPLATE = "test/spirit_treatment";
    @GameTest(template = TEMPLATE)
    public static void privateProgressDeathPersistenceAndConfirmedDialogue(GameTestHelper test) {
        var a = test.makeMockServerPlayerInLevel();
        var b = test.makeMockServerPlayerInLevel();
        var npc = test.spawn(ModEntityTypes.AKYUU.get(), new BlockPos(3, 1, 3));
        a.setPos(npc.getX() + 1, npc.getY(), npc.getZ());
        b.setPos(npc.getX() + 1, npc.getY(), npc.getZ() + 1);
        try {
            var def = QuestDefinitionLoader.all().get(Gensokyou.id("test/confirm_dialogue"));
            test.assertTrue(def != null, "shipped commission resource loads");
            accept(a, npc, def.id().toString());
            test.assertValueEqual(QuestService.data(a).quests().size(), 1, "duplicate accept creates one instance");
            accept(a, npc, def.id().toString());
            test.assertValueEqual(QuestService.data(a).quests().size(), 1, "duplicate accept is idempotent");
            test.assertTrue(QuestService.data(b).quests().isEmpty(), "personal tasks do not leak to a second player");
            var q = QuestService.data(a).quests().getFirst();
            var confirmation = new QuestDialogueAction("talk", q.instance(), q.stage(), "gensokyou:test/confirm_ready");
            var graph = new DialogueGraph(Gensokyou.id("isolated_dialogue"), Map.of("greet", new DialogueNode(
                    Component.literal("Ready?"), List.of(new DialogueOption(Component.literal("Yes"), Optional.empty(), Optional.empty(), Optional.of(confirmation)),
                    new DialogueOption(Component.literal("No"), Optional.empty(), Optional.empty())))), "greet");
            DialogueManager.open(a, npc, graph);
            test.assertFalse(QuestService.data(a).quests().getFirst().ready(), "opening a dialogue never completes a target");
            DialogueManager.handleAction(a, npc.getId(), Long.MIN_VALUE, 0);
            test.assertFalse(QuestService.data(a).quests().getFirst().ready(), "forged node revision is rejected");
            // Get the actual server node revision from the outgoing session view, without exposing a production completion API.
            long revision = revision(a);
            DialogueManager.handleAction(a, npc.getId(), revision, 1);
            test.assertFalse(QuestService.data(a).quests().getFirst().ready(), "No does not complete the target");
            DialogueManager.open(a, npc, graph);
            revision = revision(a);
            DialogueManager.handleAction(a, npc.getId(), revision, 0);
            test.assertTrue(QuestService.data(a).quests().getFirst().ready(), "the matching confirmed button completes the target");
            DialogueManager.handleAction(a, npc.getId(), revision, 0);
            test.assertTrue(QuestService.data(a).quests().getFirst().ready(), "replaying the consumed node has no additional effect");
            QuestService.track(a, q.instance());
            var before = a.getData(ModAttachments.QUESTS.get());
            CompoundTag saved = a.saveWithoutId(new CompoundTag());
            saved.putUUID("UUID", b.getUUID());
            b.load(saved);
            test.assertValueEqual(b.getData(ModAttachments.QUESTS.get()), before, "player NBT restores exact quest data");
            b.setData(ModAttachments.QUESTS.get(), PlayerQuestData.empty());
            AttachmentInternals.copyEntityAttachments(a, b, true);
            test.assertValueEqual(b.getData(ModAttachments.QUESTS.get()), before, "death clone keeps quest attachment and tracking");
        } finally {
            cleanup(a); cleanup(b); npc.discard();
        }
        test.succeed();
    }
    @GameTest(template = TEMPLATE)
    public static void observerMarkersJournalAndSingleTracking(GameTestHelper test) {
        var a = test.makeMockServerPlayerInLevel();
        var b = test.makeMockServerPlayerInLevel();
        var npc = test.spawn(ModEntityTypes.AKYUU.get(), new BlockPos(3, 1, 3));
        a.setPos(npc.getX() + 1, npc.getY(), npc.getZ());
        b.setPos(npc.getX() + 1, npc.getY(), npc.getZ() + 1);
        try {
            test.assertTrue(QuestService.snapshot(a).entries().isEmpty(), "journal does not reveal unaccepted commissions");
            test.assertValueEqual(QuestService.snapshot(a).markers().get(npc.questRole()), "question", "available test commissions show a personal marker");
            var finished = new ArrayList<QuestProgress>();
            for (var def : QuestDefinitionLoader.all().values()) if (def.test()) {
                var q = QuestProgress.start(def);
                finished.add(new QuestProgress(q.instance(), q.definition(), 0,
                        def.stages().getFirst().objectives().stream().map(QuestObjectives.Objective::required).toList(), true, true));
            }
            b.setData(ModAttachments.QUESTS.get(), new PlayerQuestData(QuestService.world(b.getServer()).epoch(), finished, ""));
            test.assertFalse(QuestService.snapshot(b).markers().containsKey(npc.questRole()), "a different observer has no available/pending marker");
            test.assertTrue(QuestService.snapshot(b).entries().isEmpty(), "completed receipts are hidden from the journal");
            accept(a, npc, "gensokyou:test/hold_paper");
            accept(a, npc, "gensokyou:test/confirm_dialogue");
            var quests = QuestService.data(a).quests();
            QuestService.track(a, quests.getFirst().instance());
            test.assertTrue(QuestService.snapshot(a).navigation().isEmpty(), "no stage navigation invents no direction");
            QuestService.track(a, quests.getLast().instance());
            test.assertValueEqual(QuestService.snapshot(a).entries().stream().filter(e -> e.tracked()).count(), 1L, "tracking a second quest replaces the first");
            a.getInventory().items.set(0, new ItemStack(Items.PAPER, 4));
            QuestService.event(a, new QuestObjectives.Poll());
            a.getInventory().items.set(0, ItemStack.EMPTY);
            QuestService.event(a, new QuestObjectives.Poll());
            test.assertTrue(QuestService.data(a).quests().getFirst().ready(), "completed holdings remain complete after materials disappear");
            QuestService.track(a, quests.getFirst().instance());
            var destination = QuestService.snapshot(a).navigation().orElseThrow();
            test.assertValueEqual(destination.dimension(), npc.level().dimension().location(), "pending reward replaces absent stage navigation with the actual NPC dimension");
            QuestService.track(a, "");
            test.assertTrue(QuestService.snapshot(a).navigation().isEmpty(), "cancelling tracking removes navigation");
        } finally { cleanup(a); cleanup(b); npc.discard(); }
        test.succeed();
    }
    @GameTest(template = TEMPLATE)
    public static void residenceWaitsForAllChunksAndDoesNotDuplicateAfterRestore(GameTestHelper test) {
        var target = test.absolutePos(new BlockPos(3, 1, 3));
        var building = new BuildingInstance(AkyuuResidence.TEMPLATE, BuildingInstance.BuildingCategory.SPECIAL_LANDMARK,
                target.getX() - 38, target.getY() - 2, target.getZ() - 12, 76, 28, 64, Rotation.NONE, 0,
                0, 0, 0, false, true, 1, 2);
        var data = new HumanVillageSavedData();
        data.setPlan(new HumanVillagePlan(2, 42, List.of(building), List.of(), Set.of()));
        var chunks = new ArrayList<Long>();
        for (int x = building.minX() >> 4; x <= building.maxX() >> 4; x++) for (int z = building.minZ() >> 4; z <= building.maxZ() >> 4; z++)
            chunks.add(ChunkPos.asLong(x, z));
        for (int n = chunks.size() - 1; n > 0; n--) data.markChunk(chunks.get(n));
        AkyuuResidence.ensure(test.getLevel(), data);
        test.assertTrue(test.getEntities(ModEntityTypes.AKYUU.get(), new BlockPos(3, 1, 3), 3).isEmpty(), "partial placement cannot spawn Akyuu");
        data.markChunk(chunks.getFirst());
        test.runAfterDelay(3, () -> {
            AkyuuResidence.ensure(test.getLevel(), data);
            AkyuuResidence.ensure(test.getLevel(), data);
            var residents = test.getEntities(ModEntityTypes.AKYUU.get(), new BlockPos(3, 1, 3), 3);
            test.assertValueEqual(residents.size(), 1, "full placement creates one Akyuu");
            var restored = HumanVillageSavedData.load(data.save(new CompoundTag(), null), null);
            AkyuuResidence.ensure(test.getLevel(), restored);
            var npc = residents.getFirst();
            var previous = npc.getUUID();
            npc.die(test.getLevel().damageSources().generic());
            AkyuuResidence.ensure(test.getLevel(), restored);
            var copies = test.getEntities(ModEntityTypes.AKYUU.get(), new BlockPos(3, 1, 3), 3);
            test.assertValueEqual(copies.size(), 1, "restoration and repeated ensure cannot duplicate the resident");
            test.assertFalse(copies.getFirst().getUUID().equals(previous), "restoration changed only entity identity");
            test.assertValueEqual(copies.getFirst().questRole(), AkyuuResidence.ROLE, "quest role survives restoration");
            copies.getFirst().discard();
            test.succeed();
        });
    }
    private static void accept(ServerPlayer player, com.bitsson.gensokyou.entity.AkyuuEntity npc, String definition) {
        QuestService.act(player, npc, new QuestDialogueAction("accept", definition, 0, ""));
    }
    private static long revision(ServerPlayer player) {
        try {
            var sessions = DialogueManager.class.getDeclaredField("SESSIONS"); sessions.setAccessible(true);
            Object session = ((Map<?, ?>) sessions.get(null)).get(player.getUUID());
            var accessor = session.getClass().getDeclaredMethod("revision"); accessor.setAccessible(true);
            return (long) accessor.invoke(session);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private static void cleanup(ServerPlayer player) {
        DialogueManager.invalidate(player.getUUID());
        QuestService.forget(player.getUUID());
        player.getServer().getPlayerList().remove(player);
    }
    private QuestGameTests() {}
}
