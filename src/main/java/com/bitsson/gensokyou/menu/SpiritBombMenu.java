package com.bitsson.gensokyou.menu;

import com.bitsson.gensokyou.block.entity.SpiritBombBlockEntity;
import com.bitsson.gensokyou.config.GensokyouConfig;
import com.bitsson.gensokyou.network.ModNetworking;
import com.bitsson.gensokyou.network.SpiritBombStatePayload;
import com.bitsson.gensokyou.registry.ModMenus;
import com.bitsson.gensokyou.spirit.ModAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/**
 * 灵力引爆器的配置界面。
 *
 * <p><b>为什么是容器而不是纯 Screen</b>：三个参数里有两个（强度 / 半径）会改变服务端的
 * 计费与爆炸范围，必须服务端权威。走 {@link AbstractContainerMenu} 可以白拿
 * "打开即建立服务端会话"的生命周期，不必自己维护一张"谁开着哪个引爆器"的表。
 *
 * <p><b>状态怎么到客户端</b>：界面 {@code init()} 时用本菜单的坐标向服务端<b>请求一次</b>
 * （{@link #requestState()}）。请求由已存在的界面发出，回应必然在界面存活时到达——不像
 * 「开屏同 tick 发状态包」那样会早于界面建立被丢弃。刻意<b>不做</b>每 tick 补发：那会让
 * 滑块被反复回写、界面闪烁。
 */
public class SpiritBombMenu extends AbstractContainerMenu {

    /** 打开后玩家与引爆器的最大距离，超过即判定界面失效。 */
    private static final double MAX_RANGE_SQR = 48.0D * 48.0D;

    private final BlockPos bombPos;
    private SpiritBombStatePayload state;

    public SpiritBombMenu(int containerId, Inventory inventory, BlockPos bombPos) {
        super(ModMenus.SPIRIT_BOMB.get(), containerId);
        this.bombPos = bombPos;
        this.state = SpiritBombStatePayload.absent(bombPos);
    }

    @Override
    public boolean stillValid(Player player) {
        return player.distanceToSqr(bombPos.getX() + 0.5D, bombPos.getY() + 0.5D,
                bombPos.getZ() + 0.5D) <= MAX_RANGE_SQR;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    /** 客户端：应用服务端回发的状态。 */
    public void acceptState(SpiritBombStatePayload payload) {
        this.state = payload;
    }

    public SpiritBombStatePayload state() {
        return state;
    }

    public BlockPos bombPos() {
        return bombPos;
    }

    /** 客户端 → 服务端：请求一次当前状态（界面 init 时调用）。 */
    public void requestState() {
        ModNetworking.sendToServer(
                new com.bitsson.gensokyou.network.SpiritBombRequestPayload(bombPos));
    }

    /** 客户端 → 服务端：提交参数并（可选）请求启动。 */
    public void submit(boolean arm, int fuseTicks, float power, int radius) {
        ModNetworking.sendToServer(
                new com.bitsson.gensokyou.network.SpiritBombConfigPayload(
                        bombPos, arm, fuseTicks, power, radius));
    }

    // ------------------------------------------------------------------ 服务端工具

    /** 打开菜单的 MenuProvider 工厂（供方块侧复用）。 */
    public static MenuProvider provider(BlockPos pos) {
        return new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return Component.translatable("gui.gensokyou.spirit_bomb.title");
            }

            @Override
            public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
                return new SpiritBombMenu(id, inventory, pos);
            }
        };
    }

    /** 服务端：把引爆器当前状态推给指定玩家（响应请求 / 回应参数提交）。 */
    public static void sendStateTo(ServerPlayer player, SpiritBombBlockEntity bomb) {
        long cost = bomb.estimatedCost();
        boolean affordable = ModAttachments.get(player).current() >= cost;
        ModNetworking.sendToPlayer(player, new SpiritBombStatePayload(
                bomb.getBlockPos(), true, bomb.isArmed(),
                bomb.getFuseTicks(), bomb.getPower(), bomb.getBlastRadius(),
                cost, affordable));
    }

    /** 服务端：目标已消失。 */
    public static void sendAbsentTo(ServerPlayer player, BlockPos pos) {
        ModNetworking.sendToPlayer(player, SpiritBombStatePayload.absent(pos));
    }

    /** 新放置引爆器的默认引信（服务端在放置时写入）。 */
    public static int defaultFuseTicks() {
        return GensokyouConfig.SPIRIT_BOMB_DEFAULT_FUSE_TICKS.get();
    }
}
