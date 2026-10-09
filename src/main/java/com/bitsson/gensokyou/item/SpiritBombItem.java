package com.bitsson.gensokyou.item;

import com.bitsson.gensokyou.entity.SpiritBombEntity;
import com.bitsson.gensokyou.menu.SpiritBombMenu;
import com.bitsson.gensokyou.registry.ModEntityTypes;
import com.bitsson.gensokyou.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 灵力引爆器物品：放置 / 打开配置 GUI。
 *
 * <p><b>交互分流</b>：
 * <ul>
 *   <li>右键地面且该处能放置 → 生成一个沉睡的引爆器实体；</li>
 *   <li>右键空气且准星附近有引爆器 → 打开它的配置 GUI；</li>
 *   <li>右键方块而该方块位置/相邻位置上有引爆器 → 打开 GUI。</li>
 * </ul>
 * 只有 1 个堆叠（{@code stacksTo(1)}）：它是可重复使用的设备，不是消耗品。
 */
public class SpiritBombItem extends Item {

    /** 客户端右键开 GUI 的最大选取距离。 */
    private static final double PICK_RANGE = 6.0D;

    public SpiritBombItem(Item.Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        if (!(player.level() instanceof ServerLevel server)) {
            return InteractionResult.SUCCESS;
        }

        // 优先：点到已有引爆器就开界面，不要再放一个
        SpiritBombEntity existing = findNearbyBomb(server, player, context.getClickedPos());
        if (existing != null) {
            openGui(player, existing);
            return InteractionResult.SUCCESS;
        }

        BlockPos target = context.getClickedPos().relative(context.getClickedFace());
        if (!server.getBlockState(target).canBeReplaced()) {
            return InteractionResult.FAIL;
        }
        SpiritBombEntity bomb = new SpiritBombEntity(ModEntityTypes.SPIRIT_BOMB.get(), server);
        bomb.setPos(target.getX() + 0.5D, target.getY(), target.getZ() + 0.5D);
        if (player instanceof ServerPlayer serverPlayer) {
            bomb.setOwner(serverPlayer.getUUID());
        }
        server.addFreshEntity(bomb);
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer sp)) {
            return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
        }
        if (sp.pick(PICK_RANGE, 1.0F, false) instanceof BlockHitResult hit
                && !server.getBlockState(hit.getBlockPos()).canBeReplaced()) {
            // 点在实心方块上：开其相邻引爆器的界面
            SpiritBombEntity bomb = findNearbyBomb(server, player, hit.getBlockPos());
            if (bomb != null) {
                openGui(player, bomb);
                return InteractionResultHolder.success(player.getItemInHand(hand));
            }
        }
        return InteractionResultHolder.pass(player.getItemInHand(hand));
    }

    /** 在点击位置与周围 1 格内找引爆器（点到它身上或其相邻格都算）。 */
    private static SpiritBombEntity findNearbyBomb(ServerLevel level, Player player, BlockPos pos) {
        for (SpiritBombEntity bomb : level.getEntitiesOfClass(SpiritBombEntity.class,
                player.getBoundingBox().inflate(PICK_RANGE))) {
            if (bomb.blockPosition().equals(pos)
                    || bomb.blockPosition().distManhattan(pos) <= 1) {
                return bomb;
            }
        }
        return null;
    }

    private static void openGui(Player player, SpiritBombEntity bomb) {
        if (player instanceof ServerPlayer server) {
            server.openMenu(SpiritBombMenu.provider(bomb.blockPosition()));
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                java.util.List<Component> tooltip, net.minecraft.world.item.TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.gensokyou.spirit_bomb.reusable"));
        tooltip.add(Component.translatable("tooltip.gensokyou.spirit_bomb.configurable"));
    }

    /** 供其它模块读取物品形态（避免对 {@link ModItems} 的反向依赖散落各处）。 */
    public static Item item() {
        return ModItems.SPIRIT_BOMB.get();
    }
}
