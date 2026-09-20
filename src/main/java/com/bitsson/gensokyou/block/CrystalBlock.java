package com.bitsson.gensokyou.block;

import com.bitsson.gensokyou.block.entity.CrystalBlockEntity;
import com.bitsson.gensokyou.menu.CrystalStorageMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 虹彩水晶：独立装饰方块。方块本体不可见（空模型），世界中的
 * 1.5 格高正菱形水晶完全由 {@code CrystalRenderer}（BER）绘制。
 *
 * <p>选取/准星判定用 {@link #SHAPE}（细柱），但碰撞体为空——水晶是悬浮装饰，
 * 可穿行；玩家无法破坏（方块属性不可破坏）。
 *
 * <p>{@link #CONCEALED}：停机隐藏预留状态（默认 false）。为 true 时不渲染、不可交互；
 * 仅依赖方块类型做结构匹配的仪式 pattern 不受该属性影响。当前无任何逻辑主动置 true。
 */
public class CrystalBlock extends Block implements EntityBlock {

    /** 选取/hitbox：0.5 格宽、1.5 格高（与水晶等高），便于完整点选。 */
    private static final VoxelShape SHAPE = Block.box(4.0D, 0.0D, 4.0D, 12.0D, 24.0D, 12.0D);

    /** 停机隐藏预留：true 时不渲染、不可交互。 */
    public static final BooleanProperty CONCEALED = BooleanProperty.create("concealed");

    public CrystalBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(CONCEALED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CONCEALED);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CrystalBlockEntity(pos, state);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(CONCEALED) ? Shapes.empty() : SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                           CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hitResult) {
        if (state.getValue(CONCEALED)) {
            return InteractionResult.PASS;
        }
        // 被仪式绑定（有 owner）的晶块禁止玩家直接开箱，读写必须经仪式核心
        if (level.getBlockEntity(pos) instanceof CrystalBlockEntity bound && bound.hasOwner()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof CrystalBlockEntity) {
            serverPlayer.openMenu(new SimpleMenuProvider(
                    (id, inventory, p) -> new CrystalStorageMenu(id, inventory, pos),
                    Component.translatable("container.gensokyou.crystal_storage")), pos);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
