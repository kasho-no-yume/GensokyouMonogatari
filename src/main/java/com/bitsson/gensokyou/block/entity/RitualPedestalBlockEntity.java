package com.bitsson.gensokyou.block.entity;

import com.bitsson.gensokyou.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class RitualPedestalBlockEntity extends BlockEntity {
    private static final String TAG_HELD = "Held";

    /**
     * 台面持有物（单件不变量：常规上限 1 个物品，经 {@link #setHeld} 强制）。
     * 加载路径不 clamp——历史超限栈视为满槽（不可再插入），随消耗/抽出逐件自然回落。
     */
    private ItemStack held = ItemStack.EMPTY;
    /** 所属仪式是否处于激活态（纯渲染态，由核心广播；MUST NOT 持久化——唯一事实源为核心）。 */
    private boolean ritualActive;

    public RitualPedestalBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.RITUAL_PEDESTAL.get(), pos, state);
    }

    public boolean isRituallyActive() {
        return ritualActive;
    }

    public void setRituallyActive(boolean active) {
        if (ritualActive != active) {
            ritualActive = active;
            setChanged();
            syncToClients();
        }
    }

    public ItemStack getHeld() {
        return held;
    }

    public void setHeld(ItemStack stack) {
        if (stack.getCount() > 1 && level != null && !level.isClientSide) {
            // 单件不变量：台面截留 1 件，余量当场掉落（不凭空消失）
            ItemStack overflow = stack.split(stack.getCount() - 1);
            level.addFreshEntity(new ItemEntity(level,
                    worldPosition.getX() + 0.5D, worldPosition.getY() + 0.5D, worldPosition.getZ() + 0.5D,
                    overflow));
        }
        this.held = stack;
        setChanged();
        syncToClients();
    }

    public ItemStack takeHeld() {
        ItemStack taken = held;
        held = ItemStack.EMPTY;
        setChanged();
        syncToClients();
        return taken;
    }

    /** 托管侧就地改写 held 数据组件后的持久化标记（显示态无变化，不广播方块更新）。 */
    public void markHeldChanged() {
        setChanged();
    }

    private void syncToClients() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /**
     * NeoForge 默认实现（{@code IBlockEntityExtension#onDataPacket}）在 update tag 为**空**时
     * 直接跳过 {@code loadWithComponents}。本 BE 的「台面清空」正对应空 tag（Held 不写入），
     * 若沿用默认实现，清空后客户端会残留上一件物品的渲染（重登/区块重载才消失）。
     * 故此处无条件载入，保证空 tag 也把 held 清成 EMPTY。
     */
    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket packet,
                             HolderLookup.Provider registries) {
        loadWithComponents(packet.getTag(), registries);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!held.isEmpty()) {
            tag.put(TAG_HELD, held.save(registries));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        held = tag.contains(TAG_HELD)
                ? ItemStack.parse(registries, tag.getCompound(TAG_HELD)).orElse(ItemStack.EMPTY)
                : ItemStack.EMPTY;
        // ritualActive 不持久化（旧档多余键静默忽略）：由所属核心在成型/启停时广播为唯一事实源
    }
}
