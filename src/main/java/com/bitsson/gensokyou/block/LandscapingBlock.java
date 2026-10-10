package com.bitsson.gensokyou.block;

import com.bitsson.gensokyou.block.entity.LandscapingBlockEntity;
import com.bitsson.gensokyou.menu.LandscapingMenu;
import com.bitsson.gensokyou.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;

/**
 * 整地器方块：像方块一样可放置、有碰撞箱与贴图，右键开配置界面设定长/宽/高并启动。
 *
 * <p>与 {@link SpiritBombBlock} 同构：右键恒开界面并抢占手持物品的 {@code useOn}，
 * 避免拿着整地器右键它时在旁边叠放第二个。破坏时掉回物品。
 *
 * <p><b>形状贴合模型</b>：模型只是底部工具头 + 上立手柄，故碰撞箱 / 选取框按模型轮廓给，
 * 而非默认整块 1×1×1；否则一小截模型却占满整格，四周会出现「整个方块都是阴影」的错觉。
 */
public class LandscapingBlock extends Block implements EntityBlock {

    /** 与 {@code models/block/landscaping_tool.json} 的 two elements 对齐。 */
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(2.0, 0.0, 6.0, 14.0, 4.0, 10.0),
            Block.box(7.0, 4.0, 7.0, 9.0, 15.0, 9.0));

    public LandscapingBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LandscapingBlockEntity(pos, state);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                               CollisionContext context) {
        return SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                        CollisionContext context) {
        return SHAPE;
    }

    /** 不遮挡邻面（配合 {@code noOcclusion()}），避免整格级别的环境光遮蔽/投影。 */
    @Override
    public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Shapes.empty();
    }

    /** 不挡光：小模型不该在地面投下整格方形暗影。 */
    @Override
    protected int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
        return 0;
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
                                              BlockPos pos, Player player, InteractionHand hand,
                                              BlockHitResult hitResult) {
        open(level, pos, player);
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hitResult) {
        open(level, pos, player);
        return InteractionResult.SUCCESS;
    }

    private void open(Level level, BlockPos pos, Player player) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof LandscapingBlockEntity device) {
            // 先发开屏包、再发状态包：同一连接保序，客户端先建界面再收状态（否则状态被丢弃）
            serverPlayer.openMenu(LandscapingMenu.provider(pos), pos);
            LandscapingMenu.sendStateTo(serverPlayer, device);
        }
    }

    /** 破坏即掉回整地器物品（可重复使用的设备）。 */
    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        return List.of(new ItemStack(ModItems.LANDSCAPING_TOOL.get()));
    }
}
