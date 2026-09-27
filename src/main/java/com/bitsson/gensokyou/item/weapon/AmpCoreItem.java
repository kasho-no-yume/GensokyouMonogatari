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

/** 武器 3 号槽：增幅核，由 RuneGenerator 首次获得时懒写 rune_affixes 词条。 */
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

    /**
     * 核自身 tooltip：<b>未 roll 状态不渲染</b>。未 roll 时 {@code rune_affixes} 组件不存在，
     * 一律不显示任何字段（物品名后由 {@code inventoryTick} 首次 roll，之后才进tooltip分支）。
     */
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        if (!stack.has(ModDataComponents.RUNE_AFFIXES.get())) {
            return; // 未 roll：物品名后无任何字段
        }
        tooltip.add(Component.translatable("tooltip.gensokyou.amp_core_tier", this.tier())
                .withStyle(ChatFormatting.GRAY));
        List<RuneAffix> affixes = stack.getOrDefault(ModDataComponents.RUNE_AFFIXES.get(), List.of());
        RuneSummary summary = RuneSummary.of(affixes);
        for (RuneAffix affix : affixes) {
            tooltip.add(RuneAffix.tooltipLine(affix).withStyle(ChatFormatting.BLUE));
        }
        // 洗练度刻意不显示：保底是暗的，不作为卖点招摇。机制仍在 RuneGenerator 内生效。
        if (!summary.isEmpty()) {
            // 措辞 MUST 明确"装进武器并手持<b>那把武器</b>"才生效——
            // 手持增幅核本身不提供任何加成（核不是装备，属性只在武器的 slot3 上生效）。
            tooltip.add(Component.translatable("tooltip.gensokyou.amp_core_attr_hint")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
