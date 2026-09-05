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

    private record Session(int npcId, DialogueGraph graph, String nodeId) {
    }

    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();

    private DialogueManager() {
    }

    public static void open(ServerPlayer player, TouhouNpcEntity npc, DialogueGraph graph) {
        if (graph.node(graph.entry()) == null) {
            return;
        }
        SESSIONS.put(player.getUUID(), new Session(npc.getId(), graph, graph.entry()));
        sendNode(player, npc.getId(), graph, graph.entry());
    }

    /** C2S 选项回报：校验会话存在、实体匹配、距离合法、索引在界内后推进。 */
    public static void handleAction(ServerPlayer player, int entityId, int choiceIndex) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || session.npcId() != entityId) {
            return;
        }
        if (!(player.level().getEntity(entityId) instanceof TouhouNpcEntity npc)
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
        if (choice.action().isPresent()) {
            DialogueAction action = choice.action().get();
            SESSIONS.remove(player.getUUID());
            sendClose(player, entityId);
            if (action == DialogueAction.OPEN_TRADE) {
                npc.openTrade(player);
            }
            return;
        }
        if (choice.next().isEmpty()) {
            SESSIONS.remove(player.getUUID());
            sendClose(player, entityId);
            return;
        }
        String next = choice.next().get();
        if (session.graph().node(next) == null) {
            return;
        }
        SESSIONS.put(player.getUUID(), new Session(session.npcId(), session.graph(), next));
        sendNode(player, entityId, session.graph(), next);
    }

    /** C2S 关闭回报：仅当会话绑定的 npcId 匹配才销毁。 */
    public static void handleClose(ServerPlayer player, int entityId) {
        Session session = SESSIONS.get(player.getUUID());
        if (session != null && session.npcId() == entityId) {
            SESSIONS.remove(player.getUUID());
        }
    }

    public static void invalidate(UUID playerId) {
        SESSIONS.remove(playerId);
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        invalidate(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        invalidate(event.getEntity().getUUID());
    }

    private static void sendNode(ServerPlayer player, int entityId, DialogueGraph graph, String nodeId) {
        DialogueNode node = graph.node(nodeId);
        List<DialogSyncPayload.Option> options = node.options().stream()
                .map(o -> new DialogSyncPayload.Option(
                        o.label(),
                        o.next().orElse(""),
                        o.action().map(a -> a.name().toLowerCase(Locale.ROOT)).orElse("")))
                .toList();
        PacketDistributor.sendToPlayer(player,
                new DialogSyncPayload(entityId, nodeId, node.text(), options));
    }

    private static void sendClose(ServerPlayer player, int entityId) {
        PacketDistributor.sendToPlayer(player,
                new DialogSyncPayload(entityId, "", Component.empty(), List.of()));
    }
}
