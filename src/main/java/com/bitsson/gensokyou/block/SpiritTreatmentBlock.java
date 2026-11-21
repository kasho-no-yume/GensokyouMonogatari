package com.bitsson.gensokyou.block;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.block.entity.SpiritTreatmentBlockEntity;
import com.bitsson.gensokyou.registry.ModBlockEntities;
import com.bitsson.gensokyou.registry.ModBlocks;
import com.bitsson.gensokyou.spirit.SpiritCoreItem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(modid = Gensokyou.MODID)
public final class SpiritTreatmentBlock extends Block implements EntityBlock {
    private static final VoxelShape SHAPE = box(1, 0, 1, 15, 12, 15);

    public SpiritTreatmentBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SpiritTreatmentBlockEntity(pos, state);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != ModBlockEntities.SPIRIT_TREATMENT.get()) {
            return null;
        }
        boolean oxidizer = state.is(ModBlocks.SPIRIT_OXIDIZER.get());
        return (world, pos, current, entity) -> ((SpiritTreatmentBlockEntity) entity).tick(world, oxidizer);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
                                              BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof SpiritTreatmentBlockEntity device)) {
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }
        if (stack.getItem() instanceof SpiritCoreItem && !player.isShiftKeyDown()) {
            if (!level.isClientSide) {
                device.insertCore(stack, player.hasInfiniteMaterials());
            }
        } else if (stack.isEmpty()) {
            // Let vanilla try the offhand core before an empty main hand tries to take a core.
            if (!player.isShiftKeyDown() && (device.core().isEmpty() || (hand == InteractionHand.MAIN_HAND
                    && player.getOffhandItem().getItem() instanceof SpiritCoreItem))) {
                return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
            }
            if (!level.isClientSide) {
                if (player.isShiftKeyDown()) {
                    device.cycleMode();
                    player.displayClientMessage(modeMessage(state, device.mode()), true);
                } else {
                    ItemStack taken = device.removeCore();
                    if (!taken.isEmpty() && !player.getInventory().add(taken)) {
                        player.drop(taken, false);
                    }
                }
            }
        } else {
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @SubscribeEvent
    public static void allowEmptyHandModeSwitch(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        // Vanilla bypasses block use on sneak clicks if either hand holds an item.
        if (event.getUseBlock().isDefault() && player.isShiftKeyDown()
                && player.getItemInHand(event.getHand()).isEmpty()
                && event.getLevel().getBlockState(event.getPos()).getBlock() instanceof SpiritTreatmentBlock) {
            event.setUseBlock(TriState.TRUE);
        }
    }

    private static Component modeMessage(BlockState state, int mode) {
        if (state.is(ModBlocks.SPIRIT_OXIDIZER.get())) {
            Block sample = switch (mode) {
                case 1 -> Blocks.EXPOSED_COPPER;
                case 2 -> Blocks.WEATHERED_COPPER;
                default -> Blocks.OXIDIZED_COPPER;
            };
            return Component.translatable("message.gensokyou.spirit_oxidizer.mode", mode, sample.getName());
        }
        var water = SpiritTreatmentRecipes.freezing(Blocks.WATER, mode);
        var lava = SpiritTreatmentRecipes.freezing(Blocks.LAVA, mode);
        return Component.translatable("message.gensokyou.spirit_freezer.mode", mode,
                water.output().getName(), lava.output().getName());
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!level.isClientSide && !state.is(newState.getBlock())
                && level.getBlockEntity(pos) instanceof SpiritTreatmentBlockEntity device) {
            Block.popResource(level, pos, device.removeCore());
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
