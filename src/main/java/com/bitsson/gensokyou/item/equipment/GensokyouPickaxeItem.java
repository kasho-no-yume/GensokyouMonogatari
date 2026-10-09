package com.bitsson.gensokyou.item.equipment;

import com.bitsson.gensokyou.item.GensokyouTools;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 灵铁 / 星银镐：两套的特技都落在这一类上。
 *
 * <ul>
 *   <li><b>灵铁：常见方块零耐久</b>——{@link #mineBlock} 里对白名单方块返还这一次耐久消耗。
 *       挖矿石 / mod 素材方块照常掉耐久（否则施工与采矿合一，星银没有存在意义）。</li>
 *   <li><b>星银：自带精准采集</b>——以 {@link DataComponents#ENCHANTMENTS} 预置 SilkTouch。
 *       <b>为什么用组件而不是自己接管掉落表</b>：原版的 SilkTouch 判定发生在
 *       {@code Block.getDrops} 读 {@code LootContextParams.TOOL} 的地方，是方块侧的行为；
 *       在物品侧接管掉落要么覆写不到（{@code DiggerItem} 根本没有 {@code getDrops}），
 *       要么要自己复刻整张掉落表。预置附魔组件让原版逻辑原样跑，
 *       玩家看到的"不附魔也能精准"与"附魔位上没有 SilkTouch"同时成立。</li>
 * </ul>
 */
public class GensokyouPickaxeItem extends PickaxeItem {

    private final boolean durabilityFree;
    private final boolean builtInSilkTouch;

    public GensokyouPickaxeItem(Tier tier, Item.Properties properties, boolean durabilityFree,
                                boolean builtInSilkTouch) {
        super(tier, properties);
        this.durabilityFree = durabilityFree;
        this.builtInSilkTouch = builtInSilkTouch;
    }

    @Override
    public void onCraftedBy(ItemStack stack, Level level, net.minecraft.world.entity.player.Player player) {
        if (builtInSilkTouch) {
            ensureBuiltInSilkTouch(stack, level);
        }
    }

    @Override
    public void inventoryTick(ItemStack stack, net.minecraft.world.level.Level level,
                              net.minecraft.world.entity.Entity entity, int slotId, boolean isSelected) {
        if (builtInSilkTouch && isSelected) {
            ensureBuiltInSilkTouch(stack, level);
        }
    }

    /**
     * 补齐预置的 SilkTouch 附魔组件。
     *
     * <p>铁砧 / 砂轮能把 SilkTouch 洗掉，故每次选中都要补回——这不是"刷新随机词条"，
     * 而是把一个固定组件写死。
     *
     * <p>走 {@link EnchantmentHelper#updateEnchantments} 而不是自己拼
     * {@code ItemEnchantments}：后者是 final 类型、构造器只吃内部 map 结构，
     * {@code Mutable} 是官方给出的唯一安全写入口。
     */
    private static void ensureBuiltInSilkTouch(ItemStack stack, Level level) {
        if (level.isClientSide || level.registryAccess() == null) {
            return;
        }
        Holder<Enchantment> silk = level.registryAccess().holderOrThrow(Enchantments.SILK_TOUCH);
        if (stack.getEnchantmentLevel(silk) > 0) {
            return;
        }
        EnchantmentHelper.updateEnchantments(stack, mutable -> mutable.set(silk, 1));
    }

    @Override
    public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos,
                             LivingEntity miner) {
        if (durabilityFree && level instanceof ServerLevel) {
            boolean named = stack.get(DataComponents.CUSTOM_NAME) != null;
            if (GensokyouTools.isDurabilityFree(state, stack.getItem(), named)) {
                // 原版已在此扣除耐久，这里把它返还——净效果为零消耗
                stack.set(DataComponents.DAMAGE, Math.max(0, stack.getDamageValue() - 1));
            }
        }
        return super.mineBlock(stack, level, state, pos, miner);
    }

    /** 供 GUI / 提示读取：本镐是否自带精准采集。 */
    public boolean isSilkTouchTool() {
        return builtInSilkTouch;
    }
}
