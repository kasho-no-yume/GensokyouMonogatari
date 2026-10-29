package com.bitsson.gensokyou.block;

import com.bitsson.gensokyou.block.entity.TreasureBasinBlockEntity;
import com.bitsson.gensokyou.menu.TreasureBasinMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 聚宝盆：把 7×7×7 范围内的掉落物牵引进鼎，9 格容量，满仓即停。
 *
 * <p>外形是**方鼎**：四足着地、深腹、外扩的鼎口、两侧立耳；总高 15/16。
 * 碰撞箱只覆盖足+腹+口（0..12），立耳不挡路；选取箱覆盖到耳顶。
 * {@code fill} 四档由容器占用格数派生，驱动鼎腹内壁符文贴图与 0~3 微光。
 *
 * <p>交互：右键开 3×3 界面（手持物品恒吞交互，避免误在旁侧叠放）；
 * 潜行右键把内容物一键倒入玩家背包，放不下的留在鼎内。
 */
public class TreasureBasinBlock extends Block implements EntityBlock {

    /** 存量档位：0 空 / 1 (1~3格) / 2 (4~6格) / 3 (7~9格)。 */
    public static final IntegerProperty FILL = IntegerProperty.create("fill", 0, 3);

    /** 选取箱：含立耳（0..15 高）。 */
    private static final VoxelShape SHAPE = Block.box(1.0, 0.0, 1.0, 15.0, 15.0, 15.0);
    /** 碰撞箱：足 + 腹 + 鼎口，立耳不参与碰撞。 */
    private static final VoxelShape COLLISION = Block.box(2.0, 0.0, 2.0, 14.0, 12.0, 14.0);

    public TreasureBasinBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FILL, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FILL);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TreasureBasinBlockEntity(pos, state);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                               CollisionContext context) {
        return SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                        CollisionContext context) {
        return COLLISION;
    }

    /** 不遮挡邻面（配合 {@code noOcclusion()}），避免整格级别的环境光遮蔽/投影。 */
    @Override
    public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Shapes.empty();
    }

    /** 不挡光：异形小模型不该在地面投下整格方形暗影。 */
    @Override
    protected int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
        return 0;
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    /** 吸附只在服务端进行；客户端不挂 ticker。 */
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return (lvl, pos, st, be) -> {
            if (be instanceof TreasureBasinBlockEntity basin) {
                TreasureBasinBlockEntity.serverTick(lvl, pos, st, basin);
            }
        };
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
                                              BlockPos pos, Player player, InteractionHand hand,
                                              BlockHitResult hitResult) {
        interact(level, pos, player);
        // 恒吞交互：手持方块右键不会在旁边叠放第二个
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hitResult) {
        interact(level, pos, player);
        return InteractionResult.SUCCESS;
    }

    private void interact(Level level, BlockPos pos, Player player) {
        if (level.isClientSide || !(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        if (!(level.getBlockEntity(pos) instanceof TreasureBasinBlockEntity basin)) {
            return;
        }
        if (player.isShiftKeyDown()) {
            basin.drainTo(serverPlayer);
        } else {
            serverPlayer.openMenu(new SimpleMenuProvider(
                    (id, inventory, p) -> new TreasureBasinMenu(id, inventory, pos),
                    Component.translatable("container.gensokyou.treasure_basin")), pos);
        }
    }

    /** 破坏/移除时先释放盆内物品，本体掉落由掉落表负责。 */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos,
                            BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())
                && !level.isClientSide
                && level.getBlockEntity(pos) instanceof TreasureBasinBlockEntity basin) {
            basin.dropContents();
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
