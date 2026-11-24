package com.bitsson.gensokyou.mixin;

import com.bitsson.gensokyou.landmark.LandmarkSample;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DoubleHighBlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BlockItem.class)
abstract class BlockItemPlacementMixin {
    @WrapOperation(method = "place", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/item/BlockItem;placeBlock(Lnet/minecraft/world/item/context/BlockPlaceContext;Lnet/minecraft/world/level/block/state/BlockState;)Z"))
    private boolean gensokyou$checkLandmarkPlacement(BlockItem item, BlockPlaceContext context,
                                                     BlockState state, Operation<Boolean> placeBlock) {
        Level level = context.getLevel();
        if (level.dimension().equals(LandmarkSample.DIMENSION)) {
            BlockPos pos = context.getClickedPos();
            if (LandmarkSample.contains(level, pos) || gensokyou$secondaryPositionProtected(item, state, level, pos)) {
                if (level.isClientSide() && context.getPlayer() != null) {
                    context.getPlayer().displayClientMessage(Component.translatable("message.gensokyou.landmark.no_placement"), true);
                }
                return false;
            }
        }
        return placeBlock.call(item, context, state);
    }

    @Unique
    private static boolean gensokyou$secondaryPositionProtected(BlockItem item, BlockState state, Level level, BlockPos pos) {
        if (state.getBlock() instanceof BedBlock) {
            return LandmarkSample.contains(level, pos.relative(state.getValue(BedBlock.FACING)));
        }
        return (item instanceof DoubleHighBlockItem || state.getBlock() instanceof DoorBlock
                || state.getBlock() instanceof DoublePlantBlock) && LandmarkSample.contains(level, pos.above());
    }
}
