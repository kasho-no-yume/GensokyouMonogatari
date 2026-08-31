package com.bitsson.gensokyou.item.weapon;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import com.bitsson.gensokyou.registry.TierPalette;

import java.util.List;
import java.util.function.IntSupplier;

/** 槽2 武器等级核：tier 即武器等级（闸门，独立于炼体）。 */
public class WeaponLevelCoreItem extends Item {

    private final IntSupplier tier;

    public WeaponLevelCoreItem(Properties properties, IntSupplier tier) {
        super(properties);
        this.tier = tier;
    }

    public int tier() {
        return this.tier.getAsInt();
    }

    @Override
    public Component getName(ItemStack stack) {
        return TierPalette.tintName(super.getName(stack), this.tier());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.gensokyou.weapon_level_core", this.tier())
                .withStyle(ChatFormatting.GRAY));
    }
}
