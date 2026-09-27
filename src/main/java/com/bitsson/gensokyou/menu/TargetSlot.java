package com.bitsson.gensokyou.menu;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

/**
 * 仪式专用目标物品槽（星移之仪的增幅核）。
 *
 * <p>与 {@link BatterySlot} 同一套显隐范式：面板开启时 {@code shown=false}，显隐由服务端
 * payload 经 {@link RitualCoreMenu#syncTargetSocketVisible} 收敛；隐藏时 {@code isActive}
 * 为 false 使槽不渲染、{@code mayPlace} 拒收。
 *
 * <p>允许取出 —— 玩家要把核拿回去；但若有待决洗练，服务侧在采纳时会复验核是否仍在槽内。
 *
 * <p><b>索引注意</b>：构造参数 {@code index} 是 handler <b>内部</b>索引，<b>不是</b> menu 槽位索引
 * （后者由 {@code addSlot} 的调用序决定）。本槽在 menu 里是第 1 个，但 handler 只有 1 格，
 * 故必须传 0 —— 传 1 会让客户端在收 {@code ContainerSetContent} 同步包时抛
 * {@code "Slot 1 not in valid range - [0,1)"} 并被踢下线。
 */
public class TargetSlot extends SlotItemHandler {

    private boolean shown = true;

    public TargetSlot(IItemHandler handler, int index, int x, int y) {
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
