package com.bitsson.gensokyou.spirit;

import com.bitsson.gensokyou.registry.ModDataComponents;
import com.bitsson.gensokyou.registry.TierPalette;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * 灵力核心（电池）：容量与注灵速率为构造定值（仿 BulletCoreItem 范式），
 * 分品阶 = 新物品新定值——仪式与传输系统只消费本类的 long 存取原语。
 */
public class SpiritCoreItem extends Item {

    private final int tier;
    private final long capacity;
    private final int fillRatePerSecond;

    public SpiritCoreItem(int tier, long capacity, int fillRatePerSecond) {
        super(new Properties().stacksTo(1));
        this.tier = tier;
        this.capacity = capacity;
        this.fillRatePerSecond = fillRatePerSecond;
    }

    public int tier() {
        return tier;
    }

    public long capacity() {
        return capacity;
    }

    public int fillRatePerSecond() {
        return fillRatePerSecond;
    }

    public static long getStored(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.SPIRIT_CORE_POWER.get(), SpiritCoreData.EMPTY).stored();
    }

    /** 注入至多 maxAmount，返回实际注入量。超容量部分不收。 */
    public static long receive(ItemStack stack, long maxAmount) {
        if (!(stack.getItem() instanceof SpiritCoreItem core) || maxAmount <= 0L) {
            return 0L;
        }
        SpiritCoreData data = stack.getOrDefault(ModDataComponents.SPIRIT_CORE_POWER.get(),
                SpiritCoreData.EMPTY);
        long added = Math.min(maxAmount, core.capacity() - data.stored());
        if (added > 0L) {
            stack.set(ModDataComponents.SPIRIT_CORE_POWER.get(), data.withStored(data.stored() + added));
        }
        return added;
    }

    /** 取出至多 maxAmount，返回实际取出量。 */
    public static long extract(ItemStack stack, long maxAmount) {
        if (!(stack.getItem() instanceof SpiritCoreItem) || maxAmount <= 0L) {
            return 0L;
        }
        SpiritCoreData data = stack.getOrDefault(ModDataComponents.SPIRIT_CORE_POWER.get(),
                SpiritCoreData.EMPTY);
        long taken = Math.min(maxAmount, data.stored());
        if (taken > 0L) {
            stack.set(ModDataComponents.SPIRIT_CORE_POWER.get(), data.withStored(data.stored() - taken));
        }
        return taken;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.gensokyou.spirit_core",
                        getStored(stack), capacity)
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.gensokyou.spirit_core_rate", fillRatePerSecond)
                .withStyle(style -> style.withColor(TierPalette.textColor(tier))));
    }
}
