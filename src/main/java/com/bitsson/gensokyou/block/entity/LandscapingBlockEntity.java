package com.bitsson.gensokyou.block.entity;

import com.bitsson.gensokyou.config.GensokyouConfig;
import com.bitsson.gensokyou.registry.ModBlockEntities;
import com.bitsson.gensokyou.registry.ModBlocks;
import com.bitsson.gensokyou.spirit.ModAttachments;
import com.bitsson.gensokyou.spirit.SpiritPowerData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 整地器的方块实体：保存「长(X) / 宽(Z) / 高(Y)」与冷却，并在「启动」时按盒体清除地形白名单、
 * 把盒体底层铺成泥土。
 *
 * <p><b>范围语义</b>：长(X) / 宽(Z) 各自独立可调，均以放置的方块为<b>中心</b>（跨
 * {@code size} 格，偶数向下取整偏一侧）；高(Y) 以放置的方块为<b>底</b>，向上跨 {@code height} 格。
 *
 * <p><b>三条硬保护</b>（比"清得快"更重要）：一切 TileEntity、基岩/黑曜石、矿石；此外还保护
 * 传送门框与全部 mod 素材方块。白名单用正向列举，将来新增的危险方块默认落在"不动"这侧。
 */
public class LandscapingBlockEntity extends BlockEntity {

    private static final String TAG_X = "SizeX";
    private static final String TAG_Z = "SizeZ";
    private static final String TAG_HEIGHT = "Height";
    private static final String TAG_COOLDOWN = "CooldownUntil";

    private int sizeX;
    private int sizeZ;
    private int height;
    private long cooldownUntil;

    /**
     * 客户端已加载的整地器实例（弱引用，避免泄漏）。线框渲染器在关卡渲染阶段遍历它，
     * 而不是走 BER——BER 的渲染阶段早于半透明地形（水），线框会被水盖住。
     */
    private static final java.util.Set<LandscapingBlockEntity> CLIENT_ACTIVE =
            java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());

    public static java.util.Collection<LandscapingBlockEntity> clientActive() {
        return CLIENT_ACTIVE;
    }

    public LandscapingBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LANDSCAPING.get(), pos, state);
        this.sizeX = GensokyouConfig.LANDSCAPING_DEFAULT_SIZE.get();
        this.sizeZ = GensokyouConfig.LANDSCAPING_DEFAULT_SIZE.get();
        this.height = GensokyouConfig.LANDSCAPING_DEFAULT_HEIGHT.get();
    }

    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
        if (level != null && level.isClientSide) {
            CLIENT_ACTIVE.add(this);
        }
    }

    @Override
    public void setRemoved() {
        CLIENT_ACTIVE.remove(this);
        super.setRemoved();
    }

    // ------------------------------------------------------------------ 参数

    public int getSizeX() {
        return sizeX;
    }

    public int getSizeZ() {
        return sizeZ;
    }

    public int getHeight() {
        return height;
    }

    public void setParams(int sizeX, int sizeZ, int height) {
        this.sizeX = clamp(sizeX, 1, GensokyouConfig.LANDSCAPING_MAX_SIZE.get());
        this.sizeZ = clamp(sizeZ, 1, GensokyouConfig.LANDSCAPING_MAX_SIZE.get());
        this.height = clamp(height, 1, GensokyouConfig.LANDSCAPING_MAX_HEIGHT.get());
        setChanged();
        // 推给客户端：线框渲染器读客户端 BE 的参数，不推则绿框不更新
        syncToClients();
    }

    public long getCooldownUntil() {
        return cooldownUntil;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    // ------------------------------------------------------------------ 盒体范围

    /** 盒体最小角（含）：长/宽以方块为中心；高以方块<b>下面一格</b>为底（地板铺在这一层）。 */
    public BlockPos boxMin() {
        int halfX = (sizeX - 1) / 2;
        int halfZ = (sizeZ - 1) / 2;
        return worldPosition.offset(-halfX, -1, -halfZ);
    }

    /** 盒体最大角（含）。 */
    public BlockPos boxMax() {
        BlockPos min = boxMin();
        return new BlockPos(min.getX() + sizeX - 1, min.getY() + height - 1,
                min.getZ() + sizeZ - 1);
    }

    // ------------------------------------------------------------------ 启动

    /**
     * 执行筑基。返回聊天栏应报的「已清除数」；负数表示被拒（-1 非服务端 / -2 冷却 / -3 灵力不足）。
     *
     * <p>消耗 = {@code base × 长 × 宽 × 高}（拉满约 10 万），走玩家灵力池，全有全无。
     * 创造模式绕过冷却、灵力与泥土门槛。
     */
    public int activate(ServerPlayer player) {
        if (!(level instanceof ServerLevel server)) {
            return -1;
        }
        if (!player.isCreative() && server.getGameTime() < cooldownUntil) {
            player.displayClientMessage(Component.translatable(
                    "message.gensokyou.landscaping.cooldown"), true);
            return -2;
        }
        long cost = estimatedCost();
        if (!player.isCreative()) {
            SpiritPowerData data = ModAttachments.get(player);
            if (data.current() < cost) {
                player.displayClientMessage(Component.translatable(
                        "message.gensokyou.landscaping.short", cost), true);
                return -3;
            }
            ModAttachments.set(player, data.withAddedCurrent(-cost));
        }
        int cleared = clearTerrain(server, player);
        int filled = (player.isCreative() || hasDirt(player)) ? paveBottom(server, player) : 0;
        if (!player.isCreative()) {
            cooldownUntil = server.getGameTime() + GensokyouConfig.LANDSCAPING_COOLDOWN_TICKS.get();
        }
        setChanged();
        syncToClients();
        player.displayClientMessage(Component.translatable(
                "message.gensokyou.landscaping.cleared", cleared, filled), false);
        return cleared;
    }

    /** 本次启动的灵力消耗：{@code base × 长 × 宽 × 高}。 */
    public long estimatedCost() {
        return costFor(sizeX, sizeZ, height);
    }

    /** 按长/宽/高估算消耗（供 GUI 与服务端共用）。 */
    public static long costFor(int sizeX, int sizeZ, int height) {
        long base = GensokyouConfig.LANDSCAPING_BASE_COST.get();
        return base * (long) sizeX * sizeZ * height;
    }

    /** 删除盒体内一切「非保留」方块（保留 = 仪式结构 + 基岩 + 本设备自身）。无掉落。 */
    private int clearTerrain(ServerLevel server, Player player) {
        int cleared = 0;
        for (BlockPos pos : BlockPos.betweenClosed(boxMin(), boxMax())) {
            BlockState state = server.getBlockState(pos);
            if (state.isAir() || isKept(state)) {
                continue;
            }
            server.destroyBlock(pos, false, player);
            cleared++;
        }
        return cleared;
    }

    /** 把盒体底层铺成泥土；保留方块（基岩/仪式结构/本设备）不动；背包无泥土时跳过（只清不填）。 */
    private int paveBottom(ServerLevel server, Player player) {
        int filled = 0;
        BlockPos min = boxMin();
        BlockPos max = boxMax();
        int y = min.getY();
        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int z = min.getZ(); z <= max.getZ(); z++) {
                BlockPos pos = new BlockPos(x, y, z);
                BlockState state = server.getBlockState(pos);
                if (state.is(Blocks.DIRT) || isKept(state)) {
                    continue;
                }
                if (!player.isCreative() && !consumeDirt(player)) {
                    return filled;
                }
                server.setBlockAndUpdate(pos, Blocks.DIRT.defaultBlockState());
                filled++;
            }
        }
        return filled;
    }

    // ------------------------------------------------------------------ 保留判定

    /**
     * 保留判定：命中即不删。保留 = 仪式结构（仪式石族 / 仪式核心 / 祭品台 / 归元晶 / 隙间）
     * + 基岩 + 本设备自身。其余一切（地形、树木、容器、矿石、mod 素材……）一律平等删除。
     */
    private static boolean isKept(BlockState state) {
        if (state.is(Blocks.BEDROCK)) {
            return true;
        }
        Block block = state.getBlock();
        if (block == ModBlocks.RITUAL_CORE.get()
                || block == ModBlocks.RITUAL_PEDESTAL.get()
                || block == ModBlocks.CRYSTAL.get()
                || block == ModBlocks.SUKIMA.get()
                || block == ModBlocks.LANDSCAPING.get()) {
            return true;
        }
        return ModBlocks.tierOf(block) >= 0;
    }

    // ------------------------------------------------------------------ 泥土

    private static boolean hasDirt(Player player) {
        return player.getInventory().items.stream()
                .anyMatch(stack -> stack.is(Blocks.DIRT.asItem()));
    }

    private static boolean consumeDirt(Player player) {
        if (player.isCreative()) {
            return true;
        }
        for (var stack : player.getInventory().items) {
            if (stack.is(Blocks.DIRT.asItem())) {
                stack.shrink(1);
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ 同步

    public void syncToClients() {
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

    // ------------------------------------------------------------------ 持久化

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt(TAG_X, sizeX);
        tag.putInt(TAG_Z, sizeZ);
        tag.putInt(TAG_HEIGHT, height);
        tag.putLong(TAG_COOLDOWN, cooldownUntil);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        int defSize = GensokyouConfig.LANDSCAPING_DEFAULT_SIZE.get();
        sizeX = tag.contains(TAG_X) ? tag.getInt(TAG_X) : defSize;
        sizeZ = tag.contains(TAG_Z) ? tag.getInt(TAG_Z) : defSize;
        height = tag.contains(TAG_HEIGHT) ? tag.getInt(TAG_HEIGHT)
                : GensokyouConfig.LANDSCAPING_DEFAULT_HEIGHT.get();
        cooldownUntil = tag.getLong(TAG_COOLDOWN);
    }
}
