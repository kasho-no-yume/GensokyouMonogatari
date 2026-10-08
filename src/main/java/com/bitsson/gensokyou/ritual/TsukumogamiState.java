package com.bitsson.gensokyou.ritual;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 付丧之冢的 per-core 状态：单个在烧批次记倒计时与燃料图标（仪式串行点火，至多一条）。
 *
 * <p>槽位身份 = 祭品台规范序下标（{@code match.positionsOf('P')} 的顺序下标）。
 * 结构失效/图案切换时清退（{@link #clear()}）。燃料实体已在点火瞬间销毁，
 * {@code fuelIcon} 仅供 GUI 显示，不参与计数与回流。
 */
public final class TsukumogamiState extends RitualBehaviorState {

    /** 单个燃烧批次（串行：至多一条在烧）。 */
    public static final class Slot {
        public final int slotIndex;
        public int remainingTicks;
        public int totalTicks;
        public ItemStack fuelIcon;

        public Slot(int slotIndex, int remainingTicks, int totalTicks, ItemStack fuelIcon) {
            this.slotIndex = slotIndex;
            this.remainingTicks = remainingTicks;
            this.totalTicks = totalTicks;
            this.fuelIcon = fuelIcon;
        }
    }

    private final List<Slot> slots = new ArrayList<>();

    public List<Slot> slots() {
        return slots;
    }

    /** 「是否有批次在燃烧」= movingMask bit0。烟雾是整座仪式统一冒，故不按台位分址。 */
    public long burningMask() {
        return slots.isEmpty() ? 0L : 1L;
    }

    @Override
    public void clear() {
        slots.clear();
    }

    @Override
    public boolean isEmpty() {
        return slots.isEmpty();
    }

    @Override
    public void save(CompoundTag tag, HolderLookup.Provider registries) {
        if (slots.isEmpty()) {
            return;
        }
        ListTag list = new ListTag();
        for (Slot slot : slots) {
            CompoundTag entry = new CompoundTag();
            entry.putInt("I", slot.slotIndex);
            entry.putInt("R", slot.remainingTicks);
            entry.putInt("T", slot.totalTicks);
            if (!slot.fuelIcon.isEmpty()) {
                entry.put("F", slot.fuelIcon.save(registries));
            }
            list.add(entry);
        }
        tag.put("TsukumogamiSlots", list);
    }

    @Override
    public void load(CompoundTag tag, HolderLookup.Provider registries) {
        slots.clear();
        if (!tag.contains("TsukumogamiSlots")) {
            return;
        }
        ListTag list = tag.getList("TsukumogamiSlots", Tag.TAG_COMPOUND);
        for (Tag raw : list) {
            CompoundTag entry = (CompoundTag) raw;
            ItemStack icon = entry.contains("F")
                    ? ItemStack.parseOptional(registries, entry.getCompound("F"))
                    : ItemStack.EMPTY;
            slots.add(new Slot(entry.getInt("I"), entry.getInt("R"), entry.getInt("T"), icon));
        }
    }
}
