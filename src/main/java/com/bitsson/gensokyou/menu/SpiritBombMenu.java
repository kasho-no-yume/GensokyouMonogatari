package com.bitsson.gensokyou.menu;

import com.bitsson.gensokyou.config.GensokyouConfig;
import com.bitsson.gensokyou.entity.SpiritBombEntity;
import com.bitsson.gensokyou.network.ModNetworking;
import com.bitsson.gensokyou.network.SpiritBombStatePayload;
import com.bitsson.gensokyou.registry.ModMenus;
import com.bitsson.gensokyou.spirit.ModAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
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
 * <p>界面本身没有任何物品槽——这是配置面板，不是背包。
 * {@code quickMoveStack} 恒返回空栈正是这个原因。
 */
public class SpiritBombMenu extends AbstractContainerMenu {

    /** 打开后玩家与引爆器的最大距离，超过即判定界面失效。 */
    private static final double MAX_RANGE_SQR = 48.0D * 48.0D;

    private final BlockPos bombPos;
    private SpiritBombStatePayload state;

    /** 服务端构造：立即把当前状态推给该玩家（客户端等这一包来渲染）。 */
    public SpiritBombMenu(int containerId, Inventory inventory, BlockPos bombPos) {
        super(ModMenus.SPIRIT_BOMB.get(), containerId);
        this.bombPos = bombPos;
        this.state = SpiritBombStatePayload.absent(bombPos);
        Player player = inventory.player;
        if (player instanceof ServerPlayer serverPlayer
                && player.level() instanceof ServerLevel level) {
            SpiritBombEntity bomb = findBomb(level, bombPos, player);
            if (bomb != null) {
                sendStateTo(serverPlayer, bomb);
            } else {
                sendAbsentTo(serverPlayer, bombPos);
            }
        }
    }

    /** 客户端构造：初始为空，等 {@link #acceptState} 填。 */
    public SpiritBombMenu(int containerId, Inventory inventory) {
        super(ModMenus.SPIRIT_BOMB.get(), containerId);
        this.bombPos = BlockPos.ZERO;
        this.state = SpiritBombStatePayload.absent(BlockPos.ZERO);
    }

    /**
     * 界面是否仍对玩家有效。
     *
     * <p><b>客户端恒返回 true</b>：客户端实例的 {@link #bombPos} 只能是 {@code BlockPos.ZERO}
     * （{@code MenuType} 工厂签名拿不到 {@code openMenu} 的 extraData），照距离判会把界面秒关。
     * 客户端关不关由屏幕收到 "absent" 状态时自行决定；距离判定只在服务端做，那是权威侧。
     */
    @Override
    public boolean stillValid(Player player) {
        if (player.level().isClientSide) {
            return true;
        }
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

    /** 客户端 → 服务端：提交参数并（可选）请求启动。 */
    public void submit(boolean arm, int fuseTicks, float power, int radius) {
        // 用服务端回发状态里的位置，而不是本地 bombPos：客户端构造时 bombPos 是
        // BlockPos.ZERO（MenuType 工厂拿不到 extraData），只有回发包才知道真位置。
        BlockPos target = state != null && state.present() ? state.bombPos() : bombPos;
        ModNetworking.sendToServer(
                new com.bitsson.gensokyou.network.SpiritBombConfigPayload(
                        target, arm, fuseTicks, power, radius));
    }

    // ------------------------------------------------------------------ 服务端工具

    /** 打开菜单的 MenuProvider 工厂（供物品侧复用）。 */
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

    private static SpiritBombEntity findBomb(ServerLevel level, BlockPos pos, Player player) {
        for (SpiritBombEntity bomb : level.getEntitiesOfClass(SpiritBombEntity.class,
                player.getBoundingBox().inflate(48.0D))) {
            if (bomb.blockPosition().equals(pos)) {
                return bomb;
            }
        }
        return null;
    }

    /** 服务端：把引爆器当前状态推给开界面的玩家。 */
    public static void sendStateTo(ServerPlayer player, SpiritBombEntity bomb) {
        long cost = bomb.estimatedCost();
        boolean affordable = ModAttachments.get(player).current() >= cost;
        ModNetworking.sendToPlayer(player, new SpiritBombStatePayload(
                bomb.blockPosition(), true, bomb.getBombState() == SpiritBombEntity.STATE_ARMED,
                bomb.getFuseTicks(), bomb.getPower(), bomb.getBlastRadius(),
                cost, affordable));
    }

    /** 服务端：目标已消失。 */
    public static void sendAbsentTo(ServerPlayer player, BlockPos pos) {
        ModNetworking.sendToPlayer(player, SpiritBombStatePayload.absent(pos));
    }

    /** 新放置引爆器的默认引信（服务端在放置时写入实体）。 */
    public static int defaultFuseTicks() {
        return GensokyouConfig.SPIRIT_BOMB_DEFAULT_FUSE_TICKS.get();
    }
}
