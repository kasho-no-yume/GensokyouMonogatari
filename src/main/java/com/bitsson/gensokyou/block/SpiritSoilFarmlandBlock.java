package com.bitsson.gensokyou.block;

import com.mojang.serialization.MapCodec;
import com.bitsson.gensokyou.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 灵土耕地：由锄头右键 {@link ModBlocks#SPIRIT_SOIL} 得到（见 {@link SpiritSoilBlock#getToolModifiedState}）。
 *
 * <p>完全复用 {@link FarmBlock} 的全部行为：湿度状态机、邻水保湿、践踏塌陷、{@code 15/16} 高度、
 * 无寻路、随机刻湿度衰减。相对原版只改一件事——<b>失效塌陷时退回 {@link ModBlocks#SPIRIT_SOIL}
 * 而不是 {@code Blocks.DIRT}</b>（原版 {@code FarmBlock} 把 {@code Blocks.DIRT} 硬编码在三处：
 * {@link #tick}、{@link #fallOn}、{@link #getStateForPlacement}，本类逐一覆写）。
 *
 * <p><b>生长加速不在这里</b>：加速由 {@link SpiritCropBlock} 读下方基底自行实施，
 * 这样「种在彼岸土上的彼岸花」也能拿到加速。本类只负责"是耕地"这一身份。
 */
public class SpiritSoilFarmlandBlock extends FarmBlock {

    /**
     * 注意签名：{@link FarmBlock} 把 {@code codec()} 收窄成 {@code MapCodec<FarmBlock>}
     * （不像 {@code BushBlock}/{@code CropBlock} 用 {@code ? extends} 宽签名），
     * 故这里必须逐字一致；{@code SpiritSoilFarmlandBlock::new} 可适配成
     * {@code Function<Properties, FarmBlock>}，因此推断出的 {@code T} 就是 {@code FarmBlock}。
     */
    public static final MapCodec<FarmBlock> CODEC =
            BlockBehaviour.simpleCodec(SpiritSoilFarmlandBlock::new);

    /** 塌陷/失效时退回的方块（原版是 {@code Blocks.DIRT}）。 */
    private static final BlockState FALLBACK = ModBlocks.SPIRIT_SOIL.get().defaultBlockState();

    private static final VoxelShape SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 15.0, 16.0);

    public SpiritSoilFarmlandBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<FarmBlock> codec() {
        return CODEC;
    }

    // ------------------------------------------------------------------ 失效路径：退回灵土

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.canSurvive(level, pos)) {
            turnBackToSoil(level, pos);
        }
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction facing, BlockState facingState,
                                     LevelAccessor level, BlockPos currentPos, BlockPos facingPos) {
        if (facing == Direction.UP && !state.canSurvive(level, currentPos)) {
            level.scheduleTick(currentPos, this, 1);
        }
        return super.updateShape(state, facing, facingState, level, currentPos, facingPos);
    }

    @Override
    public void fallOn(net.minecraft.world.level.Level level, BlockState state, BlockPos pos,
                       Entity entity, float fallDistance) {
        if (!level.isClientSide) {
            turnBackToSoil(level, pos);
        }
        super.fallOn(level, state, pos, entity, fallDistance);
    }

    /** 锄灵土得到耕地：{@link SpiritSoilBlock#getToolModifiedState} 已产出本方块，无需额外路径。 */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // 不提供物品形态放置：玩家只能通过"锄灵土"得到耕地。手持物品直接放置时退回灵土。
        return FALLBACK;
    }

    private static void turnBackToSoil(net.minecraft.world.level.Level level, BlockPos pos) {
        BlockState current = level.getBlockState(pos);
        if (current.is(ModBlocks.SPIRIT_SOIL_FARMLAND.get())) {
            level.setBlockAndUpdate(pos, FALLBACK);
            level.gameEvent(GameEvent.BLOCK_CHANGE, pos,
                    GameEvent.Context.of(null, FALLBACK));
        }
    }

    // ------------------------------------------------------------------ 形状

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(MOISTURE);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                  CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockState above = level.getBlockState(pos.above());
        return !above.isSolid()
                || above.getBlock() instanceof net.minecraft.world.level.block.FenceGateBlock
                || above.getBlock() instanceof net.minecraft.world.level.block.piston.MovingPistonBlock;
    }
}
