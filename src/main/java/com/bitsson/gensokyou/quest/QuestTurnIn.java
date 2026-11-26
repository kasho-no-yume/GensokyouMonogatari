package com.bitsson.gensokyou.quest;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;

/** Plans a delivery/claim against a private inventory copy; callers commit only a successful result. */
public final class QuestTurnIn {
    public enum Failure { INVALID, MATERIALS, INVENTORY_FULL }
    public record Result(QuestProgress progress, List<ItemStack> inventory, Optional<Failure> failure) {
        public boolean success() { return failure.isEmpty(); }
    }
    public static Result plan(QuestDefinition def, QuestProgress quest, ResourceLocation npc,
                              boolean submit, List<ItemStack> source) {
        if (quest.complete() || !quest.compatible(def)) return failed(quest, source, Failure.INVALID);
        var inventory = new QuestInventory(source);
        var next = quest;
        if (submit) {
            var costs = quest.costs(def, npc);
            if (costs.isEmpty()) return failed(quest, source, Failure.INVALID);
            if (!inventory.debit(costs)) return failed(quest, source, Failure.MATERIALS);
            next = quest.apply(def, new QuestObjectives.Submit(npc), inventory);
        }
        if (next.ready() && def.returnNpc().equals(npc)) {
            if (!inventory.reward(def.rewards())) return failed(quest, source, Failure.INVENTORY_FULL);
            next = next.finish();
        } else if (!submit) return failed(quest, source, Failure.INVALID);
        return new Result(next, inventory.stacks(), Optional.empty());
    }
    private static Result failed(QuestProgress quest, List<ItemStack> source, Failure failure) {
        return new Result(quest, source.stream().map(ItemStack::copy).toList(), Optional.of(failure));
    }
    private QuestTurnIn() {}
}
