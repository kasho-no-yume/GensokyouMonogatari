package com.bitsson.gensokyou.block.entity;

import com.bitsson.gensokyou.block.TreasureBasinBlock;
import com.bitsson.gensokyou.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.joml.Vector3f;

import java.util.List;

/**
 * 聚宝盆方块实体：9 格容器 + 7×7×7 范围内的掉落物牵引。
 *
 * <p><b>为什么不用自定义飞行实体</b>：项目里的 {@code ZaohuaFlightItem} 是为仪式会话准备的
 * （服务端不移动实体、双端按确定性曲线解算、重载续算）。普通掉落物只需要每 tick 覆盖一次
 * 朝盆口的速度：客户端本地物理会自动复现轨迹，不需要任何新的实体协议。
 *
 * <p><b>只 set 不 add</b>：两座盆相邻时"谁最后算谁赢"，不会叠加加速把物品甩出去；
 * 也<b>不碰重力</b>——0.10~0.52 的覆盖速度远大于每 tick −0.04 的重力，
 * 不设 {@code setNoGravity(true)} 就没有"盆被破坏后要恢复重力"的清理账。
 *
 * <p><b>满仓即停</b>：全满时 tick 开头直接 return（连 AABB 扫描都省）；未满时逐物品判
 * 是否有槽位能收，收不下的不牵引，避免在盆口反复弹跳。
 */
public class TreasureBasinBlockEntity extends BlockEntity {

    public static final int SLOTS = 9;

    /** 吸附范围：以方块为中心的 7×7×7 立方（各轴 ±3）。 */
    private static final double RANGE = 3.0D;
    /** 盆口目标点相对方块底面的高度（高于立耳 15/16，物品从上方落入）。 */
    private static final double MOUTH_Y = 1.0D;
    /** 距盆口小于该距离即入槽。 */
    private static final double ABSORB_DIST = 0.55D;
    /** 牵引速度：远端 ~ 近端。 */
    private static final double PULL_BASE = 0.10D;
    private static final double PULL_MAX = 0.52D;
    /** 速度爬升参考距离（超过该距离按最慢处理）。 */
    private static final double PULL_RAMP = 4.0D;
    /** 远距离时的上扬分量，让物品以弧线越过盆沿而不是贴地平移。 */
    private static final double LIFT_BIAS = 0.12D;
    /** 每 tick 最多处理的候选数，防极端场景爆量。 */
    private static final int MAX_CANDIDATES = 32;

    private static final String TAG_INVENTORY = "Inventory";
    private static final Vector3f GOLD_DUST = new Vector3f(1.0F, 0.76F, 0.16F);

    private final ItemStackHandler inventory = new ItemStackHandler(SLOTS) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            refreshFillState();
        }
    };

    /** 更新 fill 状态期间的防重入标志（setBlock 不应再触发一轮内容变更）。 */
    private boolean updatingFill;

    public TreasureBasinBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TREASURE_BASIN.get(), pos, state);
    }

    public IItemHandler itemHandler() {
        return inventory;
    }

    // ---------------- 服务端 tick ----------------

    public static void serverTick(Level level, BlockPos pos, BlockState state,
                                  TreasureBasinBlockEntity basin) {
        if (level.isClientSide || basin.isFull()) {
            return;
        }
        basin.pullItems(level, pos);
    }

    /** 7×7×7 立方内逐物品牵引；无视遮挡（财气自聚，v1 明确取舍）。 */
    private void pullItems(Level level, BlockPos pos) {
        AABB area = new AABB(
                pos.getX() - RANGE, pos.getY() - RANGE, pos.getZ() - RANGE,
                pos.getX() + 1.0D + RANGE, pos.getY() + 1.0D + RANGE, pos.getZ() + 1.0D + RANGE);
        List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, area);
        if (items.isEmpty()) {
            return;
        }
        Vec3 mouth = new Vec3(pos.getX() + 0.5D, pos.getY() + MOUTH_Y, pos.getZ() + 0.5D);
        int budget = MAX_CANDIDATES;
        for (ItemEntity item : items) {
            if (budget-- <= 0) {
                break;
            }
            if (!item.isAlive() || item.isRemoved()) {
                continue;
            }
            ItemStack stack = item.getItem();
            if (stack.isEmpty() || !canAccept(stack)) {
                continue;
            }
            Vec3 delta = mouth.subtract(item.position());
            double dist = delta.length();
            if (dist < ABSORB_DIST) {
                absorb(item, mouth);
                continue;
            }
            if (delta.lengthSqr() < 1.0E-6D) {
                continue;
            }
            double t = Mth.clamp(1.0D - dist / PULL_RAMP, 0.0D, 1.0D);
            double speed = PULL_BASE + (PULL_MAX - PULL_BASE) * smoothstep(t);
            Vec3 velocity = delta.normalize().scale(speed);
            velocity = velocity.add(0.0D,
                    LIFT_BIAS * Mth.clamp((dist - 1.0D) / 2.0D, 0.0D, 1.0D), 0.0D);
            item.setDeltaMovement(velocity);
            item.hasImpulse = true;
        }
    }

    private static double smoothstep(double x) {
        return x * x * (3.0D - 2.0D * x);
    }

    /** 尝试把整堆塞进容器；全部收下则移除实体，只收下一部分则把余量留在实体上。 */
    private boolean absorb(ItemEntity item, Vec3 mouth) {
        ItemStack stack = item.getItem();
        ItemStack rest = stack.copy();
        for (int i = 0; i < SLOTS && !rest.isEmpty(); i++) {
            rest = inventory.insertItem(i, rest, false);
        }
        int inserted = stack.getCount() - rest.getCount();
        if (inserted <= 0) {
            return false;
        }
        if (rest.isEmpty()) {
            item.discard();
            playAbsorbFx(mouth);
        } else {
            item.setItem(rest);
            item.setDeltaMovement(Vec3.ZERO);
        }
        setChanged();
        return true;
    }

    private void playAbsorbFx(Vec3 mouth) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        serverLevel.sendParticles(new DustParticleOptions(GOLD_DUST, 0.8F),
                mouth.x, mouth.y, mouth.z, 6, 0.15D, 0.08D, 0.15D, 0.01D);
        serverLevel.sendParticles(ParticleTypes.END_ROD,
                mouth.x, mouth.y, mouth.z, 2, 0.08D, 0.05D, 0.08D, 0.01D);
        serverLevel.playSound(null, worldPosition, SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.BLOCKS, 0.45F, 0.6F + level.getRandom().nextFloat() * 0.2F);
    }

    // ---------------- 容器语义 ----------------

    public boolean isFull() {
        for (int i = 0; i < SLOTS; i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            if (stack.isEmpty() || stack.getCount() < inventory.getSlotLimit(i)) {
                return false;
            }
        }
        return true;
    }

    /** 容器的任意一格能否收下这件物品（同类型未满堆叠或空槽）。 */
    public boolean canAccept(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        for (int i = 0; i < SLOTS; i++) {
            ItemStack current = inventory.getStackInSlot(i);
            if (current.isEmpty()) {
                return true;
            }
            if (ItemStack.isSameItemSameComponents(current, stack)
                    && current.getCount() < inventory.getSlotLimit(i)) {
                return true;
            }
        }
        return false;
    }

    /** 潜行右键：按槽位顺序倒入玩家背包，放不下的留在盆内。 */
    public void drainTo(ServerPlayer player) {
        if (level == null || level.isClientSide) {
            return;
        }
        boolean previous = updatingFill;
        updatingFill = true;
        try {
            for (int i = 0; i < SLOTS; i++) {
                ItemStack stack = inventory.getStackInSlot(i);
                if (stack.isEmpty()) {
                    continue;
                }
                ItemStack moving = stack.copy();
                player.getInventory().add(moving);
                inventory.setStackInSlot(i, moving.isEmpty() ? ItemStack.EMPTY : moving);
            }
        } finally {
            updatingFill = previous;
        }
        setChanged();
        refreshFillState();
    }

    /** 破坏时倒出全部内容物（逐槽生成真实掉落物，不走 loot table）。 */
    public void dropContents() {
        if (level == null || level.isClientSide) {
            return;
        }
        boolean previous = updatingFill;
        updatingFill = true;
        try {
            for (int i = 0; i < SLOTS; i++) {
                ItemStack stack = inventory.getStackInSlot(i);
                if (stack.isEmpty()) {
                    continue;
                }
                ItemStack drop = stack.copy();
                inventory.setStackInSlot(i, ItemStack.EMPTY);
                ItemEntity entity = new ItemEntity(level,
                        worldPosition.getX() + 0.5D,
                        worldPosition.getY() + 0.4D,
                        worldPosition.getZ() + 0.5D, drop);
                entity.setDefaultPickUpDelay();
                level.addFreshEntity(entity);
            }
        } finally {
            updatingFill = previous;
        }
        setChanged();
    }

    // ---------------- fill 状态 ----------------

    /** 占用格数 0→0 / 1~3→1 / 4~6→2 / 7~9→3；变化时只下发客户端。 */
    private void refreshFillState() {
        if (level == null || level.isClientSide || updatingFill) {
            return;
        }
        BlockState state = level.getBlockState(worldPosition);
        if (!(state.getBlock() instanceof TreasureBasinBlock)) {
            return;
        }
        int occupied = 0;
        for (int i = 0; i < SLOTS; i++) {
            if (!inventory.getStackInSlot(i).isEmpty()) {
                occupied++;
            }
        }
        int fill = occupied == 0 ? 0 : Math.min(3, (occupied + 2) / 3);
        if (state.getValue(TreasureBasinBlock.FILL) == fill) {
            return;
        }
        updatingFill = true;
        try {
            level.setBlock(worldPosition, state.setValue(TreasureBasinBlock.FILL, fill),
                    Block.UPDATE_CLIENTS);
        } finally {
            updatingFill = false;
        }
    }

    /** 区块加载完成后补一次状态刷新（NBT 反序列化时方块可能还没就位）。 */
    @Override
    public void onLoad() {
        super.onLoad();
        refreshFillState();
    }

    // ---------------- 持久化 ----------------

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put(TAG_INVENTORY, inventory.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        boolean previous = updatingFill;
        updatingFill = true;
        try {
            super.loadAdditional(tag, registries);
            if (tag.contains(TAG_INVENTORY)) {
                inventory.deserializeNBT(registries, tag.getCompound(TAG_INVENTORY));
            }
        } finally {
            updatingFill = previous;
        }
    }
}
