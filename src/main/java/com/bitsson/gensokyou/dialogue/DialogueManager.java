package com.bitsson.gensokyou.dialogue;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.entity.TouhouNpcEntity;
import com.bitsson.gensokyou.network.DialogSyncPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 服务端权威对话会话（design D5）。per-player 会话，界面关闭/远离 NPC 即失效。
 * 本轮为代码定义图，无数据包加载器。
 */
@EventBusSubscriber(modid = Gensokyou.MODID)
public final class DialogueManager {

    /** 交互距离平方上限（8 格）。 */
    private static final double MAX_INTERACT_DIST_SQ = 64.0D;

    private record Session(int npcId, UUID npcUuid, net.minecraft.resources.ResourceLocation dimension,
                           DialogueGraph graph, String nodeId, long revision) {
    }

    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();
    private static final java.util.concurrent.atomic.AtomicLong REVISIONS = new java.util.concurrent.atomic.AtomicLong();

    private DialogueManager() {
    }

    public static void open(ServerPlayer player, TouhouNpcEntity npc, DialogueGraph graph) {
        if (!player.isAlive() || player.isSpectator() || player.level() != npc.level() || player.distanceToSqr(npc) > MAX_INTERACT_DIST_SQ) return;
        graph = com.bitsson.gensokyou.quest.QuestDialogue.augment(player, npc, graph);
        if (graph.node(graph.entry()) == null) {
            return;
        }
        Session session = new Session(npc.getId(), npc.getUUID(), npc.level().dimension().location(),
                graph, graph.entry(), REVISIONS.incrementAndGet());
        SESSIONS.put(player.getUUID(), session);
        sendNode(player, session);
    }

    /** 行走型 NPC 在有效对话期间停步；玩家远离或离线后不再阻止散步。 */
    public static boolean isTalkingTo(TouhouNpcEntity npc) {
        var server = npc.getServer();
        if (server == null) {
            return false;
        }
        for (var entry : SESSIONS.entrySet()) {
            if (entry.getValue().npcId() == npc.getId() && entry.getValue().npcUuid().equals(npc.getUUID())) {
                ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
                if (player != null && player.isAlive() && player.level() == npc.level()
                        && player.distanceToSqr(npc) <= MAX_INTERACT_DIST_SQ) {
                    return true;
                }
            }
        }
        return false;
    }

    /** C2S 选项回报：校验会话存在、实体匹配、距离合法、索引在界内后推进。 */
    public static void handleAction(ServerPlayer player, int entityId, long revision, int choiceIndex) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || session.npcId() != entityId || session.revision() != revision) {
            return;
        }
        if (!(player.level().getEntity(entityId) instanceof TouhouNpcEntity npc)
                || !player.isAlive() || player.isSpectator() || !npc.isAlive()
                || !npc.getUUID().equals(session.npcUuid()) || !player.level().dimension().location().equals(session.dimension())
                || player.distanceToSqr(npc) > MAX_INTERACT_DIST_SQ) {
            SESSIONS.remove(player.getUUID());
            return;
        }
        DialogueNode node = session.graph().node(session.nodeId());
        if (node == null) {
            SESSIONS.remove(player.getUUID());
            return;
        }
        List<DialogueOption> options = node.options();
        if (choiceIndex < 0 || choiceIndex >= options.size()) {
            return;
        }
        DialogueOption choice = options.get(choiceIndex);
        if (choice.quest().isPresent()) {
            // Consume the exact node revision before side effects; delayed double clicks cannot advance a later stage.
            SESSIONS.remove(player.getUUID());
            com.bitsson.gensokyou.quest.QuestService.act(player, npc, choice.quest().get());
            open(player, npc, com.bitsson.gensokyou.quest.QuestDialogue.tasks(player, npc));
            return;
        }
        if (choice.action().isPresent()) {
            DialogueAction action = choice.action().get();
            SESSIONS.remove(player.getUUID());
            sendClose(player, entityId, revision);
            if (action == DialogueAction.OPEN_TRADE) {
                npc.openTrade(player);
            }
            return;
        }
        if (choice.next().isEmpty()) {
            SESSIONS.remove(player.getUUID());
            sendClose(player, entityId, revision);
            return;
        }
        String next = choice.next().get();
        if (session.graph().node(next) == null) {
            return;
        }
        Session advanced = new Session(session.npcId(), session.npcUuid(), session.dimension(), session.graph(), next, REVISIONS.incrementAndGet());
        SESSIONS.put(player.getUUID(), advanced);
        sendNode(player, advanced);
    }

    /** C2S 关闭回报：仅当会话绑定的 npcId 匹配才销毁。 */
    public static void handleClose(ServerPlayer player, int entityId, long revision) {
        Session session = SESSIONS.get(player.getUUID());
        if (session != null && session.npcId() == entityId && session.revision() == revision) {
            SESSIONS.remove(player.getUUID());
        }
    }

    public static void invalidate(UUID playerId) {
        SESSIONS.remove(playerId);
    }

    public static void close(ServerPlayer player) {
        Session session = SESSIONS.remove(player.getUUID());
        if (session != null) sendClose(player, session.npcId(), session.revision());
    }

    public static void expire(ServerPlayer player) {
        Session session = SESSIONS.get(player.getUUID());
        if (session != null && (!player.isAlive() || !player.level().dimension().location().equals(session.dimension())
                || !(player.level().getEntity(session.npcId()) instanceof TouhouNpcEntity npc)
                || !npc.isAlive() || !npc.getUUID().equals(session.npcUuid()) || player.distanceToSqr(npc) > MAX_INTERACT_DIST_SQ)) close(player);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) { invalidate(event.getEntity().getUUID()); }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        invalidate(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        invalidate(event.getEntity().getUUID());
    }

    private static void sendNode(ServerPlayer player, Session session) {
        DialogueNode node = session.graph().node(session.nodeId());
        List<DialogSyncPayload.Option> options = node.options().stream()
                .map(o -> new DialogSyncPayload.Option(
                        o.label(),
                        o.next().orElse(""),
                        o.action().map(a -> a.name().toLowerCase(Locale.ROOT)).orElse("")))
                .toList();
        PacketDistributor.sendToPlayer(player,
                new DialogSyncPayload(session.npcId(), session.revision(), session.nodeId(), node.text(), options));
    }

    private static void sendClose(ServerPlayer player, int entityId, long revision) {
        PacketDistributor.sendToPlayer(player,
                new DialogSyncPayload(entityId, revision, "", Component.empty(), List.of()));
    }
}
