package com.bitsson.gensokyou.ritual;

import com.bitsson.gensokyou.block.entity.RitualCoreBlockEntity;
import net.minecraft.world.item.ItemStack;

/**
 * 核心 GUI 的**泛化额外物品槽**：任一 {@link RitualBehavior} 实现本接口即可声明 0..N 格
 * 面板物品槽，无需修改菜单类或客户端屏幕。
 *
 * <p><b>取代 {@code usesTargetSlot()}</b>：旧契约是"1 格 + 客户端硬编码
 * {@code instanceof AmpCoreItem}"，每加一个仪式都要改 {@code RitualCoreMenu} 与
 * {@code RitualCoreScreen}。本接口把槽数、校验、坐标、标注全部交给行为声明。
 *
 * <p><b>槽位上限与协议</b>：菜单在服务端与客户端**两侧都按固定上限
 * {@link RitualCoreBlockEntity#MAX_EXTRA_SLOTS} 注册**槽位，可见子集由行为声明的
 * {@link #slotCount()} 经同步载荷收敛。之所以不按真实数量动态注册：菜单在
 * {@code ClientboundOpenScreenPacket} 之后构造，而该包的附加数据只有 {@code BlockPos}
 * 一个字段——客户端拿不到"本仪式有几格"。两侧注册数量一旦不一致，
 * {@code ContainerSetContent} 就会抛 {@code Slot N not in valid range} 并把玩家踢下线。
 *
 * <p><b>校验归属</b>：{@link #isSlotValid} 只在<b>服务端</b>执行（客户端放行）。
 * 客户端拿不到服务端的行为实例与数据包映射表，硬编码校验器必然成为每加一个仪式就要
 * 同步维护的缺陷源；非法物由服务端 {@code SlotItemHandler} 权威拒收并回滚。
 *
 * <p><b>与祭品台分离</b>：本机制<b>不占用</b>任何祭品台位。祭品台一台一件且是配方
 * 催化剂的载体（1 阶常只有 4 台），槽位若占台就会挤掉催化剂。
 */
public interface RitualExtraSlots {

    /** 本仪式声明的可见槽数（0 = 不声明）。 */
    int slotCount();

    /**
     * 服务端权威物品校验。
     *
     * @param slot 槽下标（0 起）
     */
    boolean isSlotValid(int slot, ItemStack stack);

    /** 槽体标注的 lang 键；返回 {@code ""} 表示不画标注。 */
    default String labelKey() {
        return "gui.gensokyou.ritual.extra_slot";
    }

    /**
     * 槽在面板上的物品原点坐标（18px 见方）。
     *
     * <p>缺省从 {@link RitualCoreBlockEntity#EXTRA_SLOT_X} 起每 {@link #EXTRA_SLOT_SPACING}
     * 一格横排一行——4 格恰好铺满信息区首行且不与右侧按钮列（x≥120）相撞。
     * 行为 MAY 覆写以自定义布局（如多行）。
     */
    default int slotX(int index) {
        return RitualCoreBlockEntity.EXTRA_SLOT_X + index * RitualCoreBlockEntity.EXTRA_SLOT_SPACING;
    }

    default int slotY(int index) {
        return RitualCoreBlockEntity.EXTRA_SLOT_Y;
    }
}