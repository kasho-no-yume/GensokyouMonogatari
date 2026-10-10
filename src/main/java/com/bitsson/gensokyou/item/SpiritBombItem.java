package com.bitsson.gensokyou.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

import java.util.List;

/**
 * 灵力引爆器物品：本体是 {@link com.bitsson.gensokyou.block.SpiritBombBlock} 的方块物品。
 *
 * <p>放置即由原版 {@code BlockItem} 完成；右键已放置的引爆器由方块侧
 * {@code useItemOn}/{@code useWithoutItem} 打开配置界面，物品侧不再需要探测实体。
 * 只有 1 个堆叠：它是可重复使用的设备，不是消耗品。
 */
public class SpiritBombItem extends BlockItem {

    public SpiritBombItem(Block block, Properties properties) {
        super(block, properties.stacksTo(1));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip,
                                TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.gensokyou.spirit_bomb.reusable"));
        tooltip.add(Component.translatable("tooltip.gensokyou.spirit_bomb.configurable"));
    }
}
