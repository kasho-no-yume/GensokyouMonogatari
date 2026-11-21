package com.bitsson.gensokyou.block.entity;

import com.bitsson.gensokyou.block.SpiritTreatmentRecipes;
import com.bitsson.gensokyou.registry.ModBlockEntities;
import com.bitsson.gensokyou.spirit.SpiritCoreItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

public final class SpiritTreatmentBlockEntity extends BlockEntity {
    private static final int[][] RING = {{-1, -1}, {0, -1}, {1, -1}, {1, 0},
            {1, 1}, {0, 1}, {-1, 1}, {-1, 0}};
    private static final int WORK_INTERVAL = 20;
    private ItemStack core = ItemStack.EMPTY;
    private int mode = 1;
    private int nextTarget;

    public SpiritTreatmentBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SPIRIT_TREATMENT.get(), pos, state);
    }

    public ItemStack core() {
        return core;
    }

    public int mode() {
        return mode;
    }

    public void cycleMode() {
        mode = mode % 3 + 1;
        syncState();
    }

    public void insertCore(ItemStack held, boolean infiniteMaterials) {
        if (core.isEmpty() && held.getItem() instanceof SpiritCoreItem) {
            core = infiniteMaterials ? held.copyWithCount(1) : held.split(1);
            syncState();
        }
    }

    public ItemStack removeCore() {
        ItemStack taken = core;
        if (!taken.isEmpty()) {
            core = ItemStack.EMPTY;
            syncState();
        }
        return taken;
    }

    public void tick(Level world, boolean oxidizer) {
        if (core.isEmpty() || world.getGameTime() % WORK_INTERVAL != Math.floorMod(worldPosition.asLong(), WORK_INTERVAL)) {
            return;
        }
        long stored = SpiritCoreItem.getStored(core);
        if (stored == 0) {
            return;
        }
        for (int i = 0; i < RING.length; i++) {
            int index = (nextTarget + i) % RING.length;
            BlockPos target = worldPosition.offset(RING[index][0], 0, RING[index][1]);
            if (!world.hasChunkAt(target)) {
                continue;
            }
            BlockState state = world.getBlockState(target);
            long cost = oxidizer ? oxidize(world, target, state, stored) : freeze(world, target, state, stored);
            if (cost > 0) {
                SpiritCoreItem.extract(core, cost);
                syncState();
                nextTarget = (index + 1) % RING.length;
                return;
            }
        }
    }

    private long freeze(Level world, BlockPos pos, BlockState state, long stored) {
        var recipe = SpiritTreatmentRecipes.freezing(state.getBlock(), mode);
        if (recipe == null || stored < recipe.cost() || !state.getFluidState().isSource()) {
            return 0;
        }
        return world.setBlockAndUpdate(pos, recipe.output().defaultBlockState()) ? recipe.cost() : 0;
    }

    private long oxidize(Level world, BlockPos pos, BlockState state, long stored) {
        var recipe = SpiritTreatmentRecipes.oxidation(state.getBlock());
        if (recipe == null || mode < recipe.minimumMode() || stored < recipe.cost()) {
            return 0;
        }
        BlockState result = recipe.output().withPropertiesOf(state);
        boolean changed = state.getBlock() instanceof DoorBlock
                ? oxidizeDoor(world, pos, state, result) : world.setBlockAndUpdate(pos, result);
        return changed ? recipe.cost() : 0;
    }

    private static boolean oxidizeDoor(Level world, BlockPos pos, BlockState state, BlockState result) {
        DoubleBlockHalf half = state.getValue(DoorBlock.HALF);
        BlockPos otherPos = half == DoubleBlockHalf.LOWER ? pos.above() : pos.below();
        if (!world.hasChunkAt(otherPos)) {
            return false;
        }
        BlockState other = world.getBlockState(otherPos);
        if (!other.is(state.getBlock()) || other.getValue(DoorBlock.HALF) == half) {
            return false;
        }
        // Defer neighbor shape propagation until both halves have been written.
        int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
        if (!world.setBlock(pos, result, flags)) {
            return false;
        }
        if (!world.setBlock(otherPos, result.getBlock().withPropertiesOf(other), flags)) {
            world.setBlock(pos, state, flags);
            return false;
        }
        world.updateNeighborsAt(pos, result.getBlock());
        world.updateNeighborsAt(otherPos, result.getBlock());
        return true;
    }

    private void syncState() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Mode", mode);
        if (!core.isEmpty()) {
            tag.put("Core", core.save(registries));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        mode = Math.clamp(tag.getInt("Mode"), 1, 3);
        core = !tag.contains("Core") ? ItemStack.EMPTY : ItemStack.parse(registries, tag.getCompound("Core"))
                .filter(stack -> stack.getItem() instanceof SpiritCoreItem)
                .map(stack -> stack.copyWithCount(1)).orElse(ItemStack.EMPTY);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
