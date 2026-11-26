package com.bitsson.gensokyou.quest;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.entity.TouhouNpcEntity;
import com.bitsson.gensokyou.network.QuestSyncPayload;
import com.bitsson.gensokyou.spirit.ModAttachments;
import com.bitsson.gensokyou.worldgen.human_village.AkyuuResidence;
import com.mojang.serialization.JsonOps;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Server-thread adapter. All real inventory writes occur after a successful plan on copies. */
public final class QuestService {
    private static final Map<UUID, String> LAST_SENT = new HashMap<>();
    public static WorldQuestData world(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(WorldQuestData.FACTORY, WorldQuestData.NAME);
    }
    public static PlayerQuestData data(ServerPlayer player) {
        var old = player.getData(ModAttachments.QUESTS.get());
        var next = old.forEpoch(world(player.getServer()).epoch());
        if (!old.equals(next)) player.setData(ModAttachments.QUESTS.get(), next);
        return next;
    }
    private static void store(ServerPlayer player, PlayerQuestData data) { player.setData(ModAttachments.QUESTS.get(), data); }
    public static void forget(UUID player) { LAST_SENT.remove(player); }
    public static void clear() { LAST_SENT.clear(); }
    public static void reload(MinecraftServer server) {
        world(server).reload();
        diagnoseMainline(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            com.bitsson.gensokyou.dialogue.DialogueManager.close(player);
            sync(player, false);
        }
    }
    public static void diagnoseMainline(MinecraftServer server) {
        int count = QuestDefinitionLoader.mainline().size();
        if (world(server).cursor() > count) Gensokyou.LOGGER.error(
                "Saved mainline cursor {} exceeds {} loaded stages; preserving cursor without rewinding", world(server).cursor(), count);
    }
    public static Optional<QuestDefinitionLoader.MainStage> main(MinecraftServer server) {
        return QuestMainline.current(world(server), QuestDefinitionLoader.mainline());
    }
    private static QuestProgress mainProgress(MinecraftServer server, QuestDefinitionLoader.MainStage stage) {
        return QuestMainline.progress(world(server), stage);
    }
    public static QuestInventory inventory(ServerPlayer player) {
        // Main inventory + hotbar only. Armor, offhand and nested containers are not quest storage.
        return new QuestInventory(player.getInventory().items);
    }
    public static void event(ServerPlayer player, QuestObjectives.Event event) {
        if (!player.isAlive() || player.isSpectator()) return;
        var state = data(player);
        var inventory = inventory(player);
        for (QuestProgress quest : state.quests()) {
            var def = QuestDefinitionLoader.all().get(quest.definition());
            if (def != null) state = state.replace(quest.apply(def, event, inventory));
        }
        store(player, state);
        var active = main(player.getServer());
        if (active.isPresent()) {
            QuestMainline.apply(world(player.getServer()), active.get(), event, inventory);
        }
    }
    public static void track(ServerPlayer player, String instance) {
        var state = data(player);
        boolean valid = instance.isEmpty() || instance.equals(PlayerQuestData.MAINLINE) && main(player.getServer()).isPresent()
                || state.quests().stream().anyMatch(q -> q.instance().equals(instance) && !q.complete()
                && QuestDefinitionLoader.all().containsKey(q.definition()));
        if (!valid) return;
        store(player, new PlayerQuestData(state.epoch(), state.quests(), instance));
        sync(player, false);
    }
    public static void act(ServerPlayer player, TouhouNpcEntity npc, QuestDialogueAction action) {
        if (!player.isAlive() || player.isSpectator() || !npc.isAlive() || player.level() != npc.level()
                || player.distanceToSqr(npc) > 64D) return;
        var state = data(player);
        if (action.kind().equals("accept")) {
            var def = QuestProviders.available(npc.questRole(), state).stream()
                    .filter(d -> d.id().toString().equals(action.target())).findFirst().orElse(null);
            if (def == null || state.quests().stream().anyMatch(q -> !q.complete() && q.definition().equals(def.id()))) return;
            var quests = new ArrayList<>(state.quests());
            quests.add(QuestProgress.start(def).apply(def, new QuestObjectives.Poll(), inventory(player)));
            store(player, new PlayerQuestData(state.epoch(), quests, state.tracked()));
        } else if (action.target().equals(PlayerQuestData.MAINLINE)) {
            actMain(player, npc, action);
        } else {
            var quest = state.quests().stream().filter(q -> q.instance().equals(action.target())).findFirst().orElse(null);
            if (quest == null || quest.complete() || quest.stage() != action.stage()) return;
            var def = QuestDefinitionLoader.all().get(quest.definition());
            if (def == null || !quest.compatible(def)) return;
            var planned = inventory(player);
            QuestProgress next = quest;
            if (action.kind().equals("talk")) {
                var id = ResourceLocation.tryParse(action.action());
                if (id == null || !hasPrompt(def, quest, npc.questRole(), id)) return;
                next = quest.apply(def, new QuestObjectives.Talk(npc.questRole(), id), planned);
                if (!next.equals(quest)) store(player, state.replace(next));
            } else if (action.kind().equals("submit") || action.kind().equals("claim")) {
                var result = QuestTurnIn.plan(def, quest, npc.questRole(), action.kind().equals("submit"), player.getInventory().items);
                if (!result.success()) {
                    result.failure().ifPresent(f -> {
                        if (f == QuestTurnIn.Failure.MATERIALS) message(player, "msg.gensokyou.quest.materials");
                        if (f == QuestTurnIn.Failure.INVENTORY_FULL) message(player, "msg.gensokyou.quest.inventory_full");
                    });
                    return;
                }
                for (int n = 0; n < result.inventory().size(); n++) player.getInventory().items.set(n, result.inventory().get(n));
                player.getInventory().setChanged();
                player.containerMenu.broadcastChanges();
                store(player, state.replace(result.progress()));
            } else return;
        }
        syncAll(player.getServer());
    }
    private static boolean hasPrompt(QuestDefinition def, QuestProgress progress, ResourceLocation npc, ResourceLocation action) {
        if (progress.ready()) return false;
        var goals = def.stages().get(progress.stage()).objectives();
        for (int n = 0; n < goals.size(); n++) if (progress.progress().get(n) < goals.get(n).required()
                && QuestObjectives.handler(goals.get(n)).prompt(goals.get(n), npc).filter(p -> p.action().equals(action)).isPresent()) return true;
        return false;
    }
    private static void actMain(ServerPlayer player, TouhouNpcEntity npc, QuestDialogueAction action) {
        var active = main(player.getServer());
        if (active.isEmpty() || action.stage() != world(player.getServer()).cursor()) return;
        var stage = active.get();
        var quest = mainProgress(player.getServer(), stage);
        var planned = inventory(player);
        QuestProgress next;
        if (action.kind().equals("talk")) {
            var id = ResourceLocation.tryParse(action.action());
            if (id == null || !hasPrompt(stage.definition(), quest, npc.questRole(), id)) return;
            next = quest.apply(stage.definition(), new QuestObjectives.Talk(npc.questRole(), id), planned);
        } else if (action.kind().equals("submit")) {
            var costs = quest.costs(stage.definition(), npc.questRole());
            if (costs.isEmpty() || !planned.debit(costs)) { message(player, "msg.gensokyou.quest.materials"); return; }
            next = quest.apply(stage.definition(), new QuestObjectives.Submit(npc.questRole()), planned);
            for (int n = 0; n < planned.stacks().size(); n++) player.getInventory().items.set(n, planned.stacks().get(n));
            player.getInventory().setChanged();
            player.containerMenu.broadcastChanges();
        } else return;
        world(player.getServer()).update(next.progress(), next.ready() || next.stage() != stage.index());
    }
    public static void message(ServerPlayer player, String key) { player.displayClientMessage(Component.translatable(key), false); }
    public static QuestSnapshot snapshot(ServerPlayer player) {
        var state = data(player);
        var entries = new ArrayList<QuestSnapshot.Entry>();
        var markers = new LinkedHashMap<ResourceLocation, String>();
        var activeMain = main(player.getServer());
        if (activeMain.isPresent()) {
            var stage = activeMain.get();
            entries.add(new QuestSnapshot.Entry(stage.definition(), mainProgress(player.getServer(), stage), state.tracked().equals(PlayerQuestData.MAINLINE)));
        } else if (state.tracked().equals(PlayerQuestData.MAINLINE)) {
            state = new PlayerQuestData(state.epoch(), state.quests(), "");
            store(player, state);
        }
        for (var q : state.quests()) {
            var def = QuestDefinitionLoader.all().get(q.definition());
            if (def != null && !q.complete() && q.compatible(def)) entries.add(new QuestSnapshot.Entry(def, q, state.tracked().equals(q.instance())));
        }
        for (var provider : QuestDefinitionLoader.all().values().stream().map(QuestDefinition::provider).distinct().toList())
            if (!QuestProviders.available(provider, state).isEmpty()) markers.put(provider, "question");
        for (var entry : entries) {
            var q = entry.progress();
            if (q.ready()) markers.put(entry.definition().returnNpc(), "question");
        }
        // Mainline ! always wins over a private ? regardless of entry ordering.
        for (var entry : entries) if (entry.definition().scope() == QuestDefinition.Scope.WORLD) {
            var goals = entry.definition().stages().get(entry.progress().stage()).objectives();
            for (int n = 0; n < goals.size(); n++) if (entry.progress().progress().get(n) < goals.get(n).required())
                for (var role : QuestObjectives.handler(goals.get(n)).dialogueNpcs(goals.get(n)))
                    markers.put(role, "exclamation");
        }
        Optional<QuestSnapshot.Destination> destination = entries.stream().filter(QuestSnapshot.Entry::tracked).findFirst()
                .flatMap(entry -> {
                    var q = entry.progress();
                    var navigation = q.ready() ? Optional.of(QuestDefinition.Navigation.npc(entry.definition().returnNpc()))
                            : entry.definition().stages().get(q.stage()).navigation();
                    return navigation.flatMap(n -> resolve(player, n, entry.definition().title()));
                });
        return new QuestSnapshot(entries, destination, markers);
    }
    private static Optional<QuestSnapshot.Destination> resolve(ServerPlayer player, QuestDefinition.Navigation nav, String label) {
        if (nav.dimension().isPresent()) return Optional.of(new QuestSnapshot.Destination(nav.dimension().get(), nav.x(), nav.y(), nav.z(), label));
        ResourceLocation role = nav.npc().orElseThrow();
        var residence = AkyuuResidence.destination(player.getServer(), role, label);
        if (residence.isPresent()) return residence;
        for (var level : player.getServer().getAllLevels()) {
            var matches = level.getEntities(net.minecraft.world.level.entity.EntityTypeTest.forClass(TouhouNpcEntity.class),
                    npc -> npc.questRole().equals(role) && npc.isAlive());
            if (!matches.isEmpty()) {
                var npc = matches.getFirst();
                return Optional.of(new QuestSnapshot.Destination(level.dimension().location(), npc.getX(), npc.getY() + 1D, npc.getZ(), label));
            }
        }
        if (role.equals(Gensokyou.id("reimu"))) {
            var pos = com.bitsson.gensokyou.landmark.HakureiShrineLandmark.REIMU_SPAWN;
            return Optional.of(new QuestSnapshot.Destination(com.bitsson.gensokyou.landmark.HakureiShrineLandmark.GENSOKYO_DIMENSION.location(),
                    pos.getX() + 0.5D, pos.getY() + 1D, pos.getZ() + 0.5D, label));
        }
        return Optional.empty();
    }
    public static void sync(ServerPlayer player, boolean openBook) {
        String json = QuestSnapshot.CODEC.encodeStart(JsonOps.INSTANCE, snapshot(player)).getOrThrow().toString();
        String previous = LAST_SENT.put(player.getUUID(), json);
        if (openBook || !json.equals(previous)) PacketDistributor.sendToPlayer(player, new QuestSyncPayload(json, openBook));
    }
    public static void syncAll(MinecraftServer server) { for (var player : server.getPlayerList().getPlayers()) sync(player, false); }
    private QuestService() {}
}
