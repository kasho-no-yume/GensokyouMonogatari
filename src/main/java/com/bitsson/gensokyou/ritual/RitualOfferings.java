package com.bitsson.gensokyou.ritual;

import com.bitsson.gensokyou.block.entity.RitualPedestalBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * 祭品门槛层：独立于结构匹配的声明式要求校验与消耗。
 * slot 寻址使用匹配结果的规范序（层自下而上、z 自北向南、x 自西向东）。
 */
public final class RitualOfferings {

    private RitualOfferings() {
    }

    public record SlotStatus(RitualPattern.Offering requirement, ItemStack held, boolean satisfied) {
    }

    public record Result(List<SlotStatus> slots) {

        public boolean satisfied() {
            return slots.stream().allMatch(SlotStatus::satisfied);
        }

        public long missingCount() {
            return slots.stream().filter(s -> !s.satisfied()).count();
        }
    }

    public static Result check(RitualPattern pattern, RitualMatch match, Level level) {
        List<SlotStatus> list = new ArrayList<>();
        for (RitualPattern.Offering requirement : pattern.requirements()) {
            ItemStack held = heldAt(match, requirement, level);
            boolean ok = held.getCount() >= requirement.count() && requirement.item().matches(held);
            list.add(new SlotStatus(requirement, held, ok));
        }
        return new Result(List.copyOf(list));
    }

    /** 启动瞬间扣减全部 on_activate 要求；任一不足则整体不动（全有全无）。 */
    public static boolean consumeActivations(RitualPattern pattern, RitualMatch match, Level level) {
        Result result = check(pattern, match, level);
        if (!result.satisfied()) {
            return false;
        }
        for (SlotStatus status : result.slots()) {
            if (status.requirement().consume() != RitualPattern.ConsumeMode.ON_ACTIVATE) {
                continue;
            }
            shrink(level, match, status.requirement());
        }
        return true;
    }

    /**
     * 周期供给：本 tick 到期的 PERIODIC 要求逐一足额扣减；
     * 存在到期且无法满足时返回 false（调用方应停机），无到期条目返回 true。
     */
    public static boolean upkeepTick(long ageTicks, RitualPattern pattern, RitualMatch match, Level level) {
        List<RitualPattern.Offering> due = pattern.requirements().stream()
                .filter(RitualPattern.Offering::periodic)
                .filter(requirement -> ageTicks % requirement.period() == 0)
                .toList();
        if (due.isEmpty()) {
            return true;
        }
        for (RitualPattern.Offering requirement : due) {
            ItemStack held = heldAt(match, requirement, level);
            if (held.getCount() < requirement.count() || !requirement.item().matches(held)) {
                return false;
            }
        }
        for (RitualPattern.Offering requirement : due) {
            shrink(level, match, requirement);
        }
        return true;
    }

    private static ItemStack heldAt(RitualMatch match, RitualPattern.Offering requirement, Level level) {
        BlockPos pos = slotPos(match, requirement);
        if (pos == null) {
            return ItemStack.EMPTY;
        }
        if (level.getBlockEntity(pos) instanceof RitualPedestalBlockEntity pedestal) {
            return pedestal.getHeld();
        }
        return ItemStack.EMPTY;
    }

    private static void shrink(Level level, RitualMatch match, RitualPattern.Offering requirement) {
        BlockPos pos = slotPos(match, requirement);
        if (pos == null || !(level.getBlockEntity(pos) instanceof RitualPedestalBlockEntity pedestal)) {
            return;
        }
        ItemStack held = pedestal.getHeld();
        int remaining = Math.max(0, held.getCount() - requirement.count());
        pedestal.setHeld(remaining == 0 ? ItemStack.EMPTY : held.copyWithCount(remaining));
    }

    @Nullable
    private static BlockPos slotPos(RitualMatch match, RitualPattern.Offering requirement) {
        List<BlockPos> positions = match.positionsOf(requirement.key());
        if (requirement.slot() < 0 || requirement.slot() >= positions.size()) {
            return null;
        }
        return positions.get(requirement.slot());
    }
}
