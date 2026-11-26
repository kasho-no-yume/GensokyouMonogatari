package com.bitsson.gensokyou.village;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.dialogue.DialogueAction;
import com.bitsson.gensokyou.dialogue.DialogueGraph;
import com.bitsson.gensokyou.dialogue.DialogueNode;
import com.bitsson.gensokyou.dialogue.DialogueOption;
import com.bitsson.gensokyou.entity.VillageNpcEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class VillageDialogue {
    public static DialogueGraph base(VillageRole role) {
        var function = role.shop()
                ? new DialogueOption(Component.translatable("dialogue.gensokyou.village.wares"), Optional.empty(), Optional.of(DialogueAction.OPEN_TRADE))
                : new DialogueOption(Component.translatable("gui.gensokyou.quest.tasks"), Optional.of("quests/root"), Optional.empty());
        var leave = new DialogueOption(Component.translatable("gui.gensokyou.quest.farewell"), Optional.empty(), Optional.empty());
        return new DialogueGraph(Gensokyou.id("village/" + role.key()), Map.of("greet", new DialogueNode(
                Component.translatable("dialogue.gensokyou.village." + role.key() + ".daily.0"), List.of(function, leave))), "greet");
    }
    public static boolean owns(DialogueGraph graph) { return graph != null && graph.id().getPath().startsWith("village/"); }
    public static DialogueGraph forPlayer(ServerPlayer player, VillageNpcEntity npc, DialogueGraph graph) {
        if (!owns(graph) || !graph.entry().equals("greet")) return graph;
        var state = VillageServices.data(player);
        var nodes = new LinkedHashMap<>(graph.nodes());
        var greet = nodes.get("greet");
        nodes.put("greet", new DialogueNode(Component.translatable("dialogue.gensokyou.village." + npc.villageRole().key()
                + ".daily." + Math.floorMod(state.day(), 3)), greet.options()));
        if (state.knows(npc.villageRole())) return new DialogueGraph(graph.id(), nodes, "greet");
        nodes.put("intro", new DialogueNode(Component.translatable("dialogue.gensokyou.village." + npc.villageRole().key() + ".intro"), List.of(
                new DialogueOption(Component.translatable("dialogue.gensokyou.village.understood"), Optional.empty(), Optional.of(DialogueAction.COMPLETE_VILLAGE_INTRO)),
                greet.options().getLast())));
        return new DialogueGraph(graph.id(), nodes, "intro");
    }
    private VillageDialogue() {}
}
