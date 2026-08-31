package com.bitsson.gensokyou.item.weapon;

import com.bitsson.gensokyou.registry.ModDataComponents;
import com.bitsson.gensokyou.registry.TierPalette;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;
import java.util.function.IntSupplier;

/** 槽3 增幅核：属性由 RuneGenerator 程序化生成写入 rune_affixes 组件。 */
public class AmpCoreItem extends Item {

    private final IntSupplier tier;

    public AmpCoreItem(Properties properties, IntSupplier tier) {
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
    public void inventoryTick(ItemStack stack, net.minecraft.world.level.Level level,
                              net.minecraft.world.entity.Entity entity, int slotId, boolean isSelected) {
        super.inventoryTick(stack, level, entity, slotId, isSelected);
        if (!level.isClientSide) {
            RuneGenerator.ensureGenerated(stack, this.tier());
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.gensokyou.amp_core_tier", this.tier())
                .withStyle(ChatFormatting.GRAY));
        List<RuneAffix> affixes = stack.getOrDefault(ModDataComponents.RUNE_AFFIXES.get(), List.of());
        for (RuneAffix affix : affixes) {
            tooltip.add(Component.translatable(
                            "affix.gensokyou." + affix.affixId(),
                            Math.round(affix.value() * 100F))
                    .withStyle(ChatFormatting.BLUE));
        }
    }
}
