package com.bitsson.gensokyou.menu;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

/**
 * 仪式核心 GUI 的额外物品槽（泛化机制，见 {@link com.bitsson.gensokyou.ritual.RitualExtraSlots}）。
 *
 * <p>与 {@link BatterySlot} 同一套显隐范式：面板开启时 {@code shown=false}，显隐由服务端
 * payload 经 {@link RitualCoreMenu#syncExtraSlotsVisible} 收敛；隐藏时 {@code isActive}
 * 为 false 使槽不渲染、{@code mayPlace} 拒收。
 *
 * <p>允许取出 —— 玩家要把东西拿回去；若有待决会话，服务侧在采纳时会复验物品是否仍在槽内。
 *
 * <p><b>索引注意</b>：构造参数 {@code index} 是 handler <b>内部</b>索引，<b>不是</b> menu 槽位索引
 * （后者由 {@code addSlot} 的调用序决定）。
 *
 * <p><b>客户端不校验物品类型</b>：客户端分支的 dummy handler 一律放行，由服务端
 * {@code IItemHandler#isItemValid} 权威拒收。原因见 {@link RitualExtraSlots} 的类注释。
 */
public class ExtraSlot extends SlotItemHandler {

    private boolean shown = true;

    public ExtraSlot(IItemHandler handler, int index, int x, int y) {
        super(handler, index, x, y);
    }

    public void setShown(boolean shown) {
        this.shown = shown;
    }

    @Override
    public boolean isActive() {
        return shown;
    }

    /** 隐藏槽同步拒收（isActive 为 false 时 mayPlace 亦须拒，防幽灵投料）。 */
    @Override
    public boolean mayPlace(ItemStack stack) {
        return shown && super.mayPlace(stack);
    }
}