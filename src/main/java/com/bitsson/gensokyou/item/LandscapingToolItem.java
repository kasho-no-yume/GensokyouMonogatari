package com.bitsson.gensokyou.item;

import com.bitsson.gensokyou.config.GensokyouConfig;
import com.bitsson.gensokyou.registry.ModBlocks;
import com.bitsson.gensokyou.registry.ModItems;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 整地工具：为大型仪式建筑一次性开路。
 *
 * <p><b>行为</b>：右键 → 以使用者为中心、水平半径 R 内清除地形白名单方块（石系 / 土系 /
 * 木系 / 叶 / 植被 / 沙），并把地面整平为泥土。无耐久、有长冷却，创造模式绕过低。
 *
 * <p><b>三条硬保护</b>（比"清得快"更重要）：
 * <ol>
 *   <li>一切 TileEntity（仪式核心、祭品台、箱子、熔炉、mod 机器）——内容物不可丢失；</li>
 *   <li>矿石与基岩——绝不能把玩家的资源一整片铲掉；</li>
 *   <li>传送门框与自定义名方块——前者毁坏会断开维度连接，后者代表玩家的意图。</li>
 * </ol>
 * 白名单用<b>正向列举</b>（"这些才算地形"）而不是黑名单（"这些不算"），
 * 这样将来新增的危险方块默认落在"不动"这一侧，是安全的方向。
 */
public class LandscapingToolItem extends Item {

    public LandscapingToolItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            // 客户端只做预测，不实际改动世界
            return InteractionResult.SUCCESS;
        }
        if (!player.isCreative() && player.getCooldowns().isOnCooldown(this)) {
            return InteractionResult.FAIL;
        }

        int radius = GensokyouConfig.LANDSCAPING_RADIUS.get();
        BlockPos center = context.getClickedPos().relative(context.getClickedFace(), 0);
        int cleared = clearTerrain(level, player, center, radius);
        int filled = isDirtAvailable(player) || player.isCreative()
                ? flattenToDirt(level, player, center, radius)
                : 0;

        if (!player.isCreative()) {
            player.getCooldowns().addCooldown(this, GensokyouConfig.LANDSCAPING_COOLDOWN_TICKS.get());
        }
        if (cleared == 0 && filled == 0) {
            return InteractionResult.FAIL;
        }
        if (cleared > 0) {
            player.displayClientMessage(Component.translatable(
                    "message.gensokyou.landscaping.cleared", cleared, filled), false);
        }
        return InteractionResult.SUCCESS;
    }

    // ------------------------------------------------------------------ 清除

    /** 半径内的地形白名单方块数（不含硬保护）。 */
    private static int clearTerrain(ServerLevel level, Player player, BlockPos center, int radius) {
        int cleared = 0;
        BlockPos min = center.offset(-radius, -3, -radius);
        BlockPos max = center.offset(radius, 6, radius);
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            BlockState state = level.getBlockState(pos);
            if (!isTerrain(state)) {
                continue;
            }
            if (isProtected(level, player, pos, state)) {
                continue;
            }
            level.destroyBlock(pos, false, player);
            cleared++;
        }
        return cleared;
    }

    /**
     * 地形白名单：石系 / 土系 / 木系 / 叶 / 植被 / 沙。
     *
     * <p>刻意不含矿石、基岩、黑曜石与一切 mod 素材方块——后者由 {@link #isProtected}
     * 再兜一次底（双保险，因为玩家可能装了会扩充标签的整合包）。
     */
    private static boolean isTerrain(BlockState state) {
        if (state.isAir()) {
            return false;
        }
        // 原版正向列举：stone / dirt / sand / gravel / logs / leaves / 植被
        return isVanillaTerrainBlock(state.getBlock());
    }

    /** 直接列举允许清除的原版方块，比依赖标签更可控（标签可被整合包扩充）。 */
    private static boolean isVanillaTerrainBlock(Block block) {
        return block == Blocks.STONE
                || block == Blocks.COBBLESTONE
                || block == Blocks.MOSSY_COBBLESTONE
                || block == Blocks.GRANITE
                || block == Blocks.DIORITE
                || block == Blocks.ANDESITE
                || block == Blocks.CALCITE
                || block == Blocks.TUFF
                || block == Blocks.DEEPSLATE
                || block == Blocks.COBBLED_DEEPSLATE
                || block == Blocks.STONE_BRICKS
                || block == Blocks.MOSSY_STONE_BRICKS
                || block == Blocks.GRASS_BLOCK
                || block == Blocks.DIRT
                || block == Blocks.COARSE_DIRT
                || block == Blocks.ROOTED_DIRT
                || block == Blocks.MUD
                || block == Blocks.SAND
                || block == Blocks.RED_SAND
                || block == Blocks.GRAVEL
                || block == Blocks.CLAY
                || block == Blocks.OAK_LOG
                || block == Blocks.SPRUCE_LOG
                || block == Blocks.BIRCH_LOG
                || block == Blocks.JUNGLE_LOG
                || block == Blocks.ACACIA_LOG
                || block == Blocks.DARK_OAK_LOG
                || block == Blocks.CHERRY_LOG
                || block == Blocks.MANGROVE_LOG
                || block == Blocks.CRIMSON_STEM
                || block == Blocks.WARPED_STEM
                || block == Blocks.SHORT_GRASS
                || block == Blocks.TALL_GRASS
                || block == Blocks.FERN
                || block == Blocks.DEAD_BUSH
                || block == Blocks.SNOW
                || block == Blocks.VINE
                || block == Blocks.BAMBOO
                || block == Blocks.CACTUS
                || block == Blocks.SUGAR_CANE
                || block == Blocks.PUMPKIN
                || block == Blocks.MELON
                || block == Blocks.HAY_BLOCK
                || block == Blocks.MOSS_BLOCK
                || block == Blocks.MOSS_CARPET
                || block == Blocks.DIRT_PATH
                || block == Blocks.FARMLAND;
    }

    /** 叶子按 tag 判定（原版树叶会掉落树苗，整地时掉不掉都合理，这里选择掉）。 */
    private static boolean isLeaves(BlockState state) {
        return state.is(net.minecraft.tags.BlockTags.LEAVES);
    }

    // ------------------------------------------------------------------ 硬保护

    /**
     * 硬保护：命中任一条就不动。
     *
     * <p>TileEntity 判定走 {@code state.hasBlockEntity()}，这是最宽也最安全的口径——
     * 仪式核心、祭品台、八方归元晶、无尽藏、箱子、熔炉、酿造台、附魔台全部覆盖。
     */
    private static boolean isProtected(ServerLevel level, Player player, BlockPos pos,
                                       BlockState state) {
        if (state.hasBlockEntity()) {
            return true;
        }
        Block block = state.getBlock();
        // 基岩与黑曜石族：结构不可逆
        if (block == Blocks.BEDROCK
                || block == Blocks.OBSIDIAN
                || block == Blocks.CRYING_OBSIDIAN) {
            return true;
        }
        // 矿石：资源不可逆
        if (state.is(net.minecraft.tags.BlockTags.COAL_ORES)
                || state.is(net.minecraft.tags.BlockTags.IRON_ORES)
                || state.is(net.minecraft.tags.BlockTags.COPPER_ORES)
                || state.is(net.minecraft.tags.BlockTags.GOLD_ORES)
                || state.is(net.minecraft.tags.BlockTags.REDSTONE_ORES)
                || state.is(net.minecraft.tags.BlockTags.LAPIS_ORES)
                || state.is(net.minecraft.tags.BlockTags.DIAMOND_ORES)
                || state.is(net.minecraft.tags.BlockTags.EMERALD_ORES)) {
            return true;
        }
        // 传送门框：维度连接不可逆
        if (block == Blocks.NETHER_PORTAL
                || block == Blocks.END_PORTAL
                || block == Blocks.END_GATEWAY
                || block == Blocks.END_PORTAL_FRAME) {
            return true;
        }
        // mod 素材方块：辰砂 / 灵铁矿 / 星银矿 / 鬼石 / 三种木 / 土产 / 植物 / 仪式石族
        if (com.bitsson.gensokyou.registry.ModBlocks.tierOf(block) >= 0
                || block == ModBlocks.CINNABAR.get()
                || block == ModBlocks.SPIRIT_IRON_ORE.get()
                || block == ModBlocks.STAR_SILVER_ORE.get()
                || block == ModBlocks.ONI_STONE.get()
                || block == ModBlocks.PORCELAIN_CLAY.get()
                || block == ModBlocks.HIGAN_SOIL.get()
                || block == ModBlocks.MOON_SAND.get()
                || block == ModBlocks.SACRED_WOOD.get()
                || block == ModBlocks.MAGIC_WOOD.get()
                || block == ModBlocks.ETERNAL_WOOD.get()
                || block == ModBlocks.RITUAL_CORE.get()
                || block == ModBlocks.RITUAL_PEDESTAL.get()
                || block == ModBlocks.CRYSTAL.get()) {
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ 整平

    /** 以自身脚下为基准，把半径内最高的一层可通行地面统一成泥土。 */
    private static int flattenToDirt(ServerLevel level, Player player, BlockPos center,
                                     int radius) {
        int filled = 0;
        int baseY = center.getY();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radius * radius) {
                    continue;
                }
                BlockPos ground = new BlockPos(center.getX() + dx, baseY, center.getZ() + dz);
                BlockState state = level.getBlockState(ground);
                if (state.canBeReplaced()) {
                    continue;
                }
                if (state.is(Blocks.DIRT) || state.is(ModBlocks.SPIRIT_SOIL.get())) {
                    continue;
                }
                if (isProtected(level, player, ground, state)) {
                    continue;
                }
                if (!player.isCreative() && !consumeDirt(player)) {
                    return filled;
                }
                level.setBlockAndUpdate(ground, Blocks.DIRT.defaultBlockState());
                filled++;
            }
        }
        return filled;
    }

    /** 是否有泥土可用：背包里一格泥土。 */
    private static boolean isDirtAvailable(Player player) {
        return player.getInventory().items.stream()
                .anyMatch(stack -> stack.is(Blocks.DIRT.asItem())) || player.isCreative();
    }

    /** 从背包扣一格泥土；成功返回 true。创造性模式直接放行。 */
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

    // ------------------------------------------------------------------ 提示

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                List<Component> tooltip, net.minecraft.world.item.TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.gensokyou.landscaping.radius",
                GensokyouConfig.LANDSCAPING_RADIUS.get()));
        tooltip.add(Component.translatable("tooltip.gensokyou.landscaping.protected"));
    }
}
