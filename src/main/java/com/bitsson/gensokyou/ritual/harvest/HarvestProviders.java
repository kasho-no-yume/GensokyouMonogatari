package com.bitsson.gensokyou.ritual.harvest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.PitcherCropBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

import java.util.Optional;

public final class HarvestProviders {
    private HarvestProviders() {
    }

    static {
        HarvestProviderRegistry.registerBuiltIn(Items.MELON_SEEDS, fixed(Items.MELON));
        HarvestProviderRegistry.registerBuiltIn(Items.PUMPKIN_SEEDS, fixed(Items.PUMPKIN));
        HarvestProviderRegistry.registerBuiltIn(Items.PITCHER_POD, drops(Blocks.PITCHER_CROP.defaultBlockState()
                .setValue(PitcherCropBlock.AGE, PitcherCropBlock.MAX_AGE)
                .setValue(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER)));
        HarvestProviderRegistry.registerBuiltIn(Items.NETHER_WART, drops(Blocks.NETHER_WART.defaultBlockState()
                .setValue(NetherWartBlock.AGE, NetherWartBlock.MAX_AGE)));
        HarvestProviderRegistry.registerBuiltIn(Items.SWEET_BERRIES, drops(Blocks.SWEET_BERRY_BUSH.defaultBlockState()
                .setValue(SweetBerryBushBlock.AGE, SweetBerryBushBlock.MAX_AGE)));
        HarvestProviderRegistry.registerBuiltIn(Items.COCOA_BEANS, drops(Blocks.COCOA.defaultBlockState()
                .setValue(CocoaBlock.AGE, CocoaBlock.MAX_AGE)
                .setValue(CocoaBlock.FACING, Direction.NORTH)));
        HarvestProviderRegistry.registerBuiltIn(Items.SUGAR_CANE, fixed(Items.SUGAR_CANE));
        HarvestProviderRegistry.registerBuiltIn(Items.CACTUS, fixed(Items.CACTUS));
    }

    public static Optional<HarvestProvider> resolve(ServerLevel level, BlockPos origin, ItemStack input) {
        if (input == null || input.isEmpty()) {
            return Optional.empty();
        }
        Optional<HarvestProvider> registered = HarvestProviderRegistry.find(input);
        if (registered.isPresent()) {
            return registered;
        }
        return standard(level, origin, input);
    }

    public static Optional<Block> standardCrop(ItemStack input) {
        if (input == null || input.isEmpty() || !(input.getItem() instanceof BlockItem blockItem)) {
            return Optional.empty();
        }
        Block block = blockItem.getBlock();
        return block instanceof CropBlock ? Optional.of(block) : Optional.empty();
    }

    public static Optional<HarvestProvider> standard(ServerLevel level, BlockPos origin, ItemStack input) {
        Optional<Block> block = standardCrop(input);
        if (block.isEmpty() || !(block.get() instanceof CropBlock crop)) {
            return Optional.empty();
        }
        BlockState identityState = crop.getStateForAge(0);
        ItemStack clone = block.get().getCloneItemStack(level, origin, identityState);
        if (!seedIdentityMatches(input, clone)) {
            return Optional.empty();
        }
        BlockState matureState = crop.getStateForAge(crop.getMaxAge());
        return Optional.of(safe((context, ignored, output) ->
                output.offerAll(Block.getDrops(matureState, context.level(), context.pedestalPos(), null))));
    }

    public static boolean seedIdentityMatches(ItemStack input, ItemStack clone) {
        return input != null && !input.isEmpty() && clone != null && !clone.isEmpty()
                && ItemStack.isSameItem(input, clone);
    }

    private static HarvestProvider fixed(Item result) {
        return safe((context, input, output) -> output.offer(new ItemStack(result)));
    }

    private static HarvestProvider drops(BlockState state) {
        return safe((context, input, output) ->
                output.offerAll(Block.getDrops(state, context.level(), context.pedestalPos(), null)));
    }

    private static HarvestProvider safe(HarvestProvider delegate) {
        return new HarvestProvider() {
            @Override
            public void sample(HarvestContext context, ItemStack input, HarvestOutputSink output) {
                delegate.sample(context, input, output);
            }

            @Override
            public boolean preflightSafe() {
                return true;
            }
        };
    }
}
