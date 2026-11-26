package com.bitsson.gensokyou.quest;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.dialogue.DialogueGraph;
import com.bitsson.gensokyou.dialogue.DialogueNode;
import com.bitsson.gensokyou.dialogue.DialogueOption;
import com.bitsson.gensokyou.entity.TouhouNpcEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Builds private, paginated NPC dialogue nodes; only a confirmed detail button carries a quest action. */
public final class QuestDialogue {
    private static final String ROOT = "quests/root";
    private record Operation(Component title, Component text, Component button, QuestDialogueAction action) {}
    public static DialogueGraph augment(ServerPlayer player, TouhouNpcEntity npc, DialogueGraph base) {
        if (base != null && base.nodes().containsKey(ROOT)) return base;
        return build(player, npc, base, false);
    }
    public static boolean hasOperations(ServerPlayer player, TouhouNpcEntity npc) {
        if (!QuestProviders.available(npc.questRole(), QuestService.data(player)).isEmpty()) return true;
        for (var entry : QuestService.snapshot(player).entries()) {
            var q = entry.progress(); var def = entry.definition();
            if (q.ready() && def.returnNpc().equals(npc.questRole()) || !q.costs(def, npc.questRole()).isEmpty()) return true;
            if (!q.ready()) {
                var goals = def.stages().get(q.stage()).objectives();
                for (int n = 0; n < goals.size(); n++) if (q.progress().get(n) < goals.get(n).required()
                        && QuestObjectives.handler(goals.get(n)).prompt(goals.get(n), npc.questRole()).isPresent()) return true;
            }
        }
        return false;
    }
    public static DialogueGraph tasks(ServerPlayer player, TouhouNpcEntity npc) {
        return build(player, npc, npc.dialogueGraph(), true);
    }
    private static DialogueGraph build(ServerPlayer player, TouhouNpcEntity npc, DialogueGraph original, boolean tasks) {
        DialogueGraph base = original == null ? new DialogueGraph(Gensokyou.id("npc_tasks"), Map.of("greet",
                new DialogueNode(Component.translatable("gui.gensokyou.quest.npc_intro"), List.of())), "greet") : original;
        var nodes = new LinkedHashMap<>(base.nodes());
        var entry = base.node(base.entry());
        var intro = new ArrayList<>(entry.options());
        if (original == null && !npc.getOffers().isEmpty()) intro.add(new DialogueOption(
                Component.translatable("gui.gensokyou.quest.trade"), Optional.empty(), Optional.of(com.bitsson.gensokyou.dialogue.DialogueAction.OPEN_TRADE)));
        intro.add(link(Component.translatable("gui.gensokyou.quest.tasks"), ROOT));
        nodes.put(base.entry(), new DialogueNode(entry.text(), intro));
        var accept = new ArrayList<Operation>();
        var submit = new ArrayList<Operation>();
        var claim = new ArrayList<Operation>();
        var talk = new ArrayList<Operation>();
        var state = QuestService.data(player);
        for (var def : QuestProviders.available(npc.questRole(), state)) accept.add(new Operation(
                Component.translatable(def.title()), details(def), Component.translatable("gui.gensokyou.quest.accept"),
                new QuestDialogueAction("accept", def.id().toString(), 0, "")));
        for (var entryView : QuestService.snapshot(player).entries()) {
            var def = entryView.definition();
            var q = entryView.progress();
            int expectedStage = def.scope() == QuestDefinition.Scope.WORLD ? QuestService.world(player.getServer()).cursor() : q.stage();
            if (q.ready() && def.returnNpc().equals(npc.questRole())) claim.add(new Operation(Component.translatable(def.title()),
                    details(def), Component.translatable("gui.gensokyou.quest.claim"), new QuestDialogueAction("claim", q.instance(), expectedStage, "")));
            if (!q.costs(def, npc.questRole()).isEmpty()) submit.add(new Operation(Component.translatable(def.title()),
                    details(def), Component.translatable("gui.gensokyou.quest.submit"), new QuestDialogueAction("submit", q.instance(), expectedStage, "")));
            if (!q.ready()) {
                var goals = def.stages().get(q.stage()).objectives();
                for (int n = 0; n < goals.size(); n++) if (q.progress().get(n) < goals.get(n).required()) {
                    var goal = goals.get(n);
                    QuestObjectives.handler(goal).prompt(goal, npc.questRole()).ifPresent(prompt ->
                            talk.add(new Operation(Component.translatable(goal.description()), Component.translatable(prompt.question()),
                                    Component.translatable(prompt.confirm()), new QuestDialogueAction("talk", q.instance(), expectedStage, prompt.action().toString()))));
                }
            }
        }
        var root = new ArrayList<DialogueOption>();
        category(nodes, root, "available", accept);
        category(nodes, root, "deliveries", submit);
        category(nodes, root, "rewards", claim);
        category(nodes, root, "conversations", talk);
        root.add(link(Component.translatable("gui.gensokyou.quest.back"), base.entry()));
        nodes.put(ROOT, new DialogueNode(Component.translatable("gui.gensokyou.quest.tasks"), root));
        return new DialogueGraph(base.id(), nodes, tasks ? ROOT : base.entry());
    }
    private static Component details(QuestDefinition def) {
        var text = Component.translatable(def.title()).append("\n\n").append(Component.translatable(def.description()));
        if (!def.rewards().isEmpty()) {
            text.append("\n\n").append(Component.translatable("gui.gensokyou.quest.rewards"));
            for (var reward : def.rewards()) text.append("\n").append(BuiltInRegistries.ITEM.get(reward.item()).getDescription())
                    .append(" × " + reward.count());
        }
        return text;
    }
    private static void category(Map<String, DialogueNode> nodes, List<DialogueOption> root, String name, List<Operation> operations) {
        String prefix = "quests/" + name;
        root.add(link(Component.translatable("gui.gensokyou.quest." + name), prefix + "/0"));
        int pages = Math.max(1, (operations.size() + 2) / 3);
        for (int page = 0; page < pages; page++) {
            var choices = new ArrayList<DialogueOption>();
            for (int n = page * 3; n < Math.min(page * 3 + 3, operations.size()); n++) {
                var op = operations.get(n);
                String detail = prefix + "/detail/" + n;
                choices.add(link(op.title(), detail));
                nodes.put(detail, new DialogueNode(op.text(), List.of(
                        new DialogueOption(op.button(), Optional.empty(), Optional.empty(), Optional.of(op.action())),
                        link(Component.translatable(op.action().kind().equals("talk") ? "gui.gensokyou.quest.no" : "gui.gensokyou.quest.back"), prefix + "/" + page))));
            }
            if (page > 0) choices.add(link(Component.literal("◀"), prefix + "/" + (page - 1)));
            if (page + 1 < pages) choices.add(link(Component.literal("▶"), prefix + "/" + (page + 1)));
            choices.add(link(Component.translatable("gui.gensokyou.quest.back"), ROOT));
            nodes.put(prefix + "/" + page, new DialogueNode(Component.translatable(operations.isEmpty()
                    ? "gui.gensokyou.quest.none" : "gui.gensokyou.quest." + name), choices));
        }
    }
    private static DialogueOption link(Component text, String next) { return new DialogueOption(text, Optional.of(next), Optional.empty()); }
    private QuestDialogue() {}
}
