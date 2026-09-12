package com.bitsson.gensokyou.menu;

import com.bitsson.gensokyou.registry.ModMenus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/**
 * 仪式编辑杖菜单：零槽，仅作开屏握手（服务端可追踪/防作弊）。
 * 图案列表/工作区控件由客户端自绘（{@code RitualPatternLoader.all()} + 杖组件），
 * 一切动作经 {@code EditorCommandPayload} 服务端权威执行。
 */
public class RitualEditorMenu extends AbstractContainerMenu {

    private final Player player;
    private final InteractionHand hand;

    public RitualEditorMenu(int windowId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(windowId, inventory, InteractionHand.values()[buf.readByte()]);
    }

    public RitualEditorMenu(int windowId, Inventory inventory, InteractionHand hand) {
        super(ModMenus.RITUAL_EDITOR.get(), windowId);
        this.player = inventory.player;
        this.hand = hand;
    }

    public InteractionHand hand() {
        return this.hand;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        // 编辑杖不离手：换手/丢弃/死亡即关闭
        return player.isAlive() && !player.getItemInHand(this.hand).isEmpty()
                && player.getItemInHand(this.hand).getItem()
                        instanceof com.bitsson.gensokyou.item.RitualWandItem;
    }
}
