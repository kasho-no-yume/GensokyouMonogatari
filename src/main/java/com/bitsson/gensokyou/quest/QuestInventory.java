package com.bitsson.gensokyou.quest;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Work on copies, then commit once. Failed aggregate debits or rewards leave the real inventory untouched. */
public final class QuestInventory implements QuestObjectives.Context {
    private final List<ItemStack> stacks;
    public QuestInventory(List<ItemStack> source) { stacks = new java.util.ArrayList<>(source.stream().map(ItemStack::copy).toList()); }
    public List<ItemStack> stacks() { return stacks; }
    @Override public int count(ResourceLocation item) {
        return stacks.stream().filter(s -> !s.isEmpty() && BuiltInRegistries.ITEM.getKey(s.getItem()).equals(item))
                .mapToInt(ItemStack::getCount).sum();
    }
    public boolean debit(List<QuestDefinition.ItemAmount> costs) {
        var totals = new java.util.HashMap<ResourceLocation, Integer>();
        costs.forEach(c -> totals.merge(c.item(), c.count(), Math::addExact));
        if (totals.entrySet().stream().anyMatch(e -> count(e.getKey()) < e.getValue())) return false;
        totals.forEach((item, amount) -> {
            int remaining = amount;
            for (ItemStack stack : stacks) if (!stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(item)) {
                int taken = Math.min(remaining, stack.getCount());
                stack.shrink(taken);
                remaining -= taken;
            }
        });
        return true;
    }
    public boolean reward(List<QuestDefinition.ItemAmount> rewards) {
        for (var reward : rewards) {
            ItemStack sample = new ItemStack(BuiltInRegistries.ITEM.get(reward.item()));
            int remaining = reward.count();
            for (ItemStack stack : stacks) if (!stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, sample)) {
                int add = Math.min(remaining, Math.max(0, stack.getMaxStackSize() - stack.getCount()));
                stack.grow(add);
                remaining -= add;
            }
            for (int n = 0; n < stacks.size() && remaining > 0; n++) if (stacks.get(n).isEmpty()) {
                int add = Math.min(remaining, sample.getMaxStackSize());
                // The list is fixed-size but its entries must be replaceable.
                stacks.set(n, sample.copyWithCount(add));
                remaining -= add;
            }
            if (remaining > 0) return false;
        }
        return true;
    }
}
