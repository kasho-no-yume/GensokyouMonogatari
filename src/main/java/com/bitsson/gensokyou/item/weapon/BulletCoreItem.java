package com.bitsson.gensokyou.item.weapon;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** 槽1 弹幕核：注册时定死 firePattern × danmakuType 与基础乘区。 */
public class BulletCoreItem extends Item {

    private final FirePattern pattern;
    private final CoreStats stats;

    public BulletCoreItem(Properties properties, FirePattern pattern, CoreStats stats) {
        super(properties);
        this.pattern = pattern;
        this.stats = stats;
    }

    public FirePattern pattern() {
        return this.pattern;
    }

    public CoreStats stats() {
        return this.stats;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        int tier = this.stats.requiredTier().getAsInt();
        if (tier > 1) {
            tooltip.add(Component.translatable("tooltip.gensokyou.core_requires_level", tier)
                    .withStyle(ChatFormatting.GRAY));
        }
    }
}
