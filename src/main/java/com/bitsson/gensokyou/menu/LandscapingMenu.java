package com.bitsson.gensokyou.menu;

import com.bitsson.gensokyou.block.entity.LandscapingBlockEntity;
import com.bitsson.gensokyou.network.LandscapingStatePayload;
import com.bitsson.gensokyou.network.ModNetworking;
import com.bitsson.gensokyou.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/**
 * 整地器的配置界面：三个尺寸滑块（长 X / 宽 Z / 高 Y）+ 启动按钮，无物品槽。
 *
 * <p>与 {@link SpiritBombMenu} 同构：界面 {@code init()} 时请求一次状态，服务端回应即可，
 * 不做每 tick 补发（那会打断拖动）。
 */
public class LandscapingMenu extends AbstractContainerMenu {

    private static final double MAX_RANGE_SQR = 48.0D * 48.0D;

    private final BlockPos pos;
    private LandscapingStatePayload state;

    public LandscapingMenu(int containerId, Inventory inventory, BlockPos pos) {
        super(ModMenus.LANDSCAPING.get(), containerId);
        this.pos = pos;
        this.state = LandscapingStatePayload.absent(pos);
    }

    @Override
    public boolean stillValid(Player player) {
        return player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D)
                <= MAX_RANGE_SQR;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    public void acceptState(LandscapingStatePayload payload) {
        this.state = payload;
    }

    public LandscapingStatePayload state() {
        return state;
    }

    public BlockPos pos() {
        return pos;
    }

    /** 客户端 → 服务端：请求一次当前尺寸（界面 init 时调用）。 */
    public void requestState() {
        ModNetworking.sendToServer(
                new com.bitsson.gensokyou.network.LandscapingRequestPayload(pos));
    }

    /** 客户端 → 服务端：提交长 / 宽 / 高并（可选）请求启动。 */
    public void submit(boolean activate, int sizeX, int sizeZ, int height) {
        ModNetworking.sendToServer(
                new com.bitsson.gensokyou.network.LandscapingConfigPayload(
                        pos, sizeX, sizeZ, height, activate));
    }

    // ------------------------------------------------------------------ 服务端工具

    public static MenuProvider provider(BlockPos pos) {
        return new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return Component.translatable("gui.gensokyou.landscaping.title");
            }

            @Override
            public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
                return new LandscapingMenu(id, inventory, pos);
            }
        };
    }

    /** 服务端：把整地器当前长 / 宽 / 高推给指定玩家（响应请求 / 回应参数提交）。 */
    public static void sendStateTo(ServerPlayer player, LandscapingBlockEntity device) {
        ModNetworking.sendToPlayer(player, new LandscapingStatePayload(
                device.getBlockPos(), true,
                device.getSizeX(), device.getSizeZ(), device.getHeight()));
    }

    public static void sendAbsentTo(ServerPlayer player, BlockPos pos) {
        ModNetworking.sendToPlayer(player, LandscapingStatePayload.absent(pos));
    }
}
