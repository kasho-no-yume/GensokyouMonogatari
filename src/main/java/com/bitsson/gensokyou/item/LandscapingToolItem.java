package com.bitsson.gensokyou.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

import java.util.List;

/**
 * 整地器物品：本体是 {@link com.bitsson.gensokyou.block.LandscapingBlock} 的方块物品。
 *
 * <p>放置后由方块侧右键开配置界面（长/宽/高 + 启动），清除逻辑在
 * {@link com.bitsson.gensokyou.block.entity.LandscapingBlockEntity}。物品侧只负责放置与提示。
 */
public class LandscapingToolItem extends BlockItem {

    public LandscapingToolItem(Block block, Properties properties) {
        super(block, properties.stacksTo(1));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip,
                                TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.gensokyou.landscaping.usage"));
        tooltip.add(Component.translatable("tooltip.gensokyou.landscaping.protected"));
    }
}
