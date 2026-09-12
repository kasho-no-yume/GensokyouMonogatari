package com.bitsson.gensokyou.menu;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

/**
 * 可切换可见性的单槽：加具土命之外的仪式界面把它置 shown=false，
 * 则不渲染也不参与放置（isActive 仅客户端消费，服务端放置由 handler 保证）。
 */
public class BatterySlot extends SlotItemHandler {

    private boolean shown = true;

    public BatterySlot(IItemHandler handler, int index, int x, int y) {
        super(handler, index, x, y);
    }

    public void setShown(boolean shown) {
        this.shown = shown;
    }

    @Override
    public boolean isActive() {
        return shown;
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return shown && super.mayPlace(stack);
    }
}
