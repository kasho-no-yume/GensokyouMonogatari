package com.bitsson.gensokyou.item;

import com.bitsson.gensokyou.config.GensokyouConfig;
import com.bitsson.gensokyou.item.equipment.GensokyouAxeItem;
import com.bitsson.gensokyou.item.equipment.GensokyouHoeItem;
import com.bitsson.gensokyou.item.equipment.GensokyouPickaxeItem;
import com.bitsson.gensokyou.item.equipment.GensokyouShovelItem;
import com.bitsson.gensokyou.item.equipment.GensokyouSwordItem;
import com.bitsson.gensokyou.registry.ModItems;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;

/**
 * 灵铁 / 星银工具家族：两套各 5 件，特技按档位分化。
 *
 * <p><b>特技落点</b>：
 * <ul>
 *   <li><b>灵铁</b>：挖「常见方块」零耐久。白名单是石系 / 土系 / 木系 / 叶 / 沙 / 植物
 *       的交集，与 {@code mineable/*} 标签取交后排除矿石、mod 素材方块与自定义名方块。
 *       挖矿石照常掉耐久——否则它同时是施工工具与采矿工具，星银就没有存在意义。</li>
 *   <li><b>星银</b>：自带精准采集 + 挖 mod 原矿概率追加掉落 1 个原矿方块。
 *       精准采集不走附魔通道（附魔的 SilkTouch 只影响掉落表，会把 mod 矿石的
 *       「矿石方块」变成「材料」，与「追加掉原矿」的设计冲突）。</li>
 * </ul>
 */
public final class GensokyouTools {

    /** 灵铁：钻石级采集、常见方块零耐久。 */
    public static final Tier SPIRIT_IRON = new GensokyouTier(
            512, 8.0F, 3.0F,
            BlockTags.INCORRECT_FOR_DIAMOND_TOOL, 10,
            () -> Ingredient.of(ModItems.SPIRIT_IRON.get()),
            false, false) {
        @Override
        public boolean isCommonBlockDurabilityFree() {
            return true;
        }
    };

    /** 星银：下界合金级采集、自带精准采集 + 原矿追加掉落。 */
    public static final Tier STAR_SILVER = new GensokyouTier(
            2048, 12.0F, 4.0F,
            BlockTags.INCORRECT_FOR_NETHERITE_TOOL, 18,
            () -> Ingredient.of(ModItems.STAR_SILVER.get()),
            true, true);

    private GensokyouTools() {
    }

    /**
     * 某方块是否算「常见方块」——灵铁工具挖它零耐久。
     *
     * <p>白名单策略取正向列举而不是「不是矿石就算」：后者会把基岩、刷怪笼、
     * 黑曜石、门/活版门之类的高价值或功能性方块也纳入，让工具变成万能无损铲。
     *
     * @param state 被挖的方块状态
     * @param customNamed 该物品是否带自定义名（带名的方块一律照常掉耐久）
     */
    public static boolean isDurabilityFree(net.minecraft.world.level.block.state.BlockState state,
                                           Item item, boolean customNamed) {
        if (!GensokyouConfig.SPIRIT_IRON_FREE_DURABILITY.get()) {
            return false;
        }
        if (customNamed || state == null) {
            return false;
        }
        Block block = state.getBlock();
        // 矿石 / 需要正确工具才掉落的东西一律照常
        if (state.requiresCorrectToolForDrops()) {
            return false;
        }
        // mod 素材方块（辰砂、灵铁矿、星银矿、鬼石、三种木、土产、植物）照常
        if (block instanceof com.bitsson.gensokyou.block.TieredBlock
                || com.bitsson.gensokyou.registry.ModBlocks.tierOf(block) >= 0) {
            return false;
        }
        return state.is(BlockTags.MINEABLE_WITH_SHOVEL)
                || state.is(BlockTags.MINEABLE_WITH_PICKAXE)
                || state.is(BlockTags.MINEABLE_WITH_AXE)
                || state.is(BlockTags.DIRT)
                || state.is(BlockTags.LEAVES)
                || state.is(BlockTags.SAND)
                || state.is(BlockTags.LOGS)
                || state.is(BlockTags.TALL_FLOWERS)
                || state.is(BlockTags.SMALL_FLOWERS)
                || state.is(BlockTags.SAPLINGS);
    }

    /** 供贴图 / GUI 读取的占位：全部由 {@code ModItems} 侧注册。 */
    public static Item placeholder() {
        return ModItems.GUIDE_BOOK.get();
    }
}
