package com.bitsson.gensokyou.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * 记忆残页（add-guide-book）：凑齐 9 张可拼出《幻想乡物语》，附提示 tooltip。
 */
public class MemoryFragmentItem extends Item {

    public MemoryFragmentItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.gensokyou.memory_fragment.hint")
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }
}
