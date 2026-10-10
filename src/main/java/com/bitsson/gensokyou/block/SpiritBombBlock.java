package com.bitsson.gensokyou.block;

import com.bitsson.gensokyou.block.entity.SpiritBombBlockEntity;
import com.bitsson.gensokyou.menu.SpiritBombMenu;
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
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;

/**
 * 灵力引爆器方块：可放置、有碰撞箱与方块贴图，右键开配置界面。
 *
 * <p>它同时是「设备」与「爆炸物」——放置后处于沉睡态，配置并启动后由
 * {@link SpiritBombBlockEntity} 自行倒计时起爆。右键恒开界面（潜行无特殊语义），
 * 并抢占手持物品的 {@code useOn}，避免拿着引爆器右键它时误在旁边叠放一个新的。
 *
 * <p><b>形状贴合模型</b>：模型是收分圆弹体，碰撞箱 / 选取框按弹体轮廓给（略小于整格），
 * 既像普通方块一样挡住玩家，又不至于让整格都显阴影/描边。
 */
public class SpiritBombBlock extends Block implements EntityBlock {

    /** 与 {@code models/block/spirit_bomb.json} 的弹体轮廓对齐（引信不计入碰撞）。 */
    private static final VoxelShape SHAPE = Block.box(2.0, 0.0, 2.0, 14.0, 16.0, 14.0);

    public SpiritBombBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SpiritBombBlockEntity(pos, state);
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

    /** 不挡光：异形小模型不该在地面投下整格方形暗影。 */
    @Override
    protected int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
        return 0;
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return (lvl, pos, st, be) -> {
            if (be instanceof SpiritBombBlockEntity bomb) {
                SpiritBombBlockEntity.serverTick(lvl, pos, st, bomb);
            }
        };
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
                                              BlockPos pos, Player player, InteractionHand hand,
                                              BlockHitResult hitResult) {
        open(level, pos, player);
        // 恒吞交互：手持引爆器右键不会在旁边叠放第二个
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
                && level.getBlockEntity(pos) instanceof SpiritBombBlockEntity bomb) {
            // 先发开屏包、再发状态包：同一连接保序，客户端先建界面再收状态（否则状态被丢弃）
            serverPlayer.openMenu(SpiritBombMenu.provider(pos), pos);
            SpiritBombMenu.sendStateTo(serverPlayer, bomb);
        }
    }

    /**
     * 只掉「沉睡」态的引爆器；已启动者被破坏不掉落。
     *
     * <p>走 {@link #getDrops} 而不是掉落表：掉落与否取决于运行时 BE 状态，
     * 静态 loot table 表达不了。已启动者通常由 {@code detonate} 自行无声移除，
     * 此分支只兜住「被第三方爆炸波及」的边角情形。
     */
    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        if (params.getOptionalParameter(LootContextParams.BLOCK_ENTITY)
                instanceof SpiritBombBlockEntity bomb && bomb.isArmed()) {
            return List.of();
        }
        return List.of(new ItemStack(ModItems.SPIRIT_BOMB.get()));
    }
}
