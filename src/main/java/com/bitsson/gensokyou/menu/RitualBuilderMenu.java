package com.bitsson.gensokyou.menu;

import com.bitsson.gensokyou.item.RitualBuilderItem;
import com.bitsson.gensokyou.registry.ModDataComponents;
import com.bitsson.gensokyou.registry.ModMenus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/**
 * 仪式构建器选择菜单：零槽，仅作开屏握手（服务端可追踪/防作弊）。
 * 图案列表与材料计数由客户端自绘（{@code RitualPatternLoader.all()} + 本地背包），
 * 选择经 C2S {@code RitualSelectPayload} 写回手上构建器组件。
 */
public class RitualBuilderMenu extends AbstractContainerMenu {

    public static final int REPLACEMENT_OFF = 0;
    public static final int REPLACEMENT_ON = 1;

    private final InteractionHand hand;
    /** 玩家世界进度上限（客户端经开屏握手数据获得；服务端直构默认 5，不参与渲染）。 */
    private int maxTier = 5;
    private boolean replaceBlocks;

    public RitualBuilderMenu(int windowId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(windowId, inventory, InteractionHand.values()[buf.readByte()]);
        this.maxTier = buf.readVarInt();
        this.replaceBlocks = buf.readBoolean();
    }

    public RitualBuilderMenu(int windowId, Inventory inventory, InteractionHand hand) {
        super(ModMenus.RITUAL_BUILDER.get(), windowId);
        this.hand = hand;
    }

    public InteractionHand hand() {
        return this.hand;
    }

    /** 玩家世界进度阶梯上限（供客户端菜单过滤显示）。 */
    public int maxTier() {
        return this.maxTier;
    }

    public boolean replaceBlocks() {
        return this.replaceBlocks;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!(player instanceof ServerPlayer) || !stillValid(player)
                || (id != REPLACEMENT_OFF && id != REPLACEMENT_ON)) {
            return false;
        }
        ItemStack stack = player.getItemInHand(this.hand);
        if (id == REPLACEMENT_ON) {
            stack.set(ModDataComponents.RITUAL_BUILDER_REPLACE_BLOCKS.get(), true);
        } else {
            stack.remove(ModDataComponents.RITUAL_BUILDER_REPLACE_BLOCKS.get());
        }
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        // 构建器不离手：换手/丢弃/死亡即关闭
        return player.isAlive() && player.getItemInHand(this.hand).getItem() instanceof RitualBuilderItem;
    }
}
