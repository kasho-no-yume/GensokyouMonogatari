package com.bitsson.gensokyou.item;

import com.bitsson.gensokyou.item.codex.BossResidualData;
import com.bitsson.gensokyou.registry.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;
import java.util.Optional;

/**
 * 寄宿残魂的众生典籍：以一件主题信物为凭依，让众生典籍记下它本记不下的强敌。
 *
 * <p>本物品是「镇魂返照之仪」的供品（独立类，<b>不是</b> {@link CodexOfBeingsItem} 的实例，
 * 故众生余录天然不识别它）。其寄宿对象与品阶由数据组件 {@link ModDataComponents#BOSS_RESIDUAL_DATA}
 * 编码。物品无右键交互——用途全在仪式侧。
 */
public class BossResidualCodexItem extends Item {

    public BossResidualCodexItem(Properties properties) {
        super(properties);
    }

    // ---- 只读访问器（供镇魂返照之仪调用）----

    public static Optional<BossResidualData> data(ItemStack stack) {
        return Optional.ofNullable(stack.get(ModDataComponents.BOSS_RESIDUAL_DATA.get()));
    }

    public static Optional<ResourceLocation> getBoss(ItemStack stack) {
        return data(stack).map(BossResidualData::boss);
    }

    public static int getTier(ItemStack stack) {
        return data(stack).map(BossResidualData::tier).orElse(0);
    }

    /** 有效供品：本类物品且携带寄宿组件。 */
    public static boolean isValid(ItemStack stack) {
        return stack.getItem() instanceof BossResidualCodexItem && data(stack).isPresent();
    }

    /** 寄宿 BOSS 的本地化名（未知 id 回落为 id 串）。 */
    public static Component bossDisplayName(ResourceLocation boss) {
        return BuiltInRegistries.ENTITY_TYPE.getOptional(boss)
                .<Component>map(EntityType::getDescription)
                .orElseGet(() -> Component.literal(boss.toString()));
    }

    // ---- 表现 ----

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip,
                                TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        Optional<ResourceLocation> boss = getBoss(stack);
        if (boss.isEmpty()) {
            tooltip.add(Component.translatable("tooltip.gensokyou.boss_residual.empty")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }
        tooltip.add(Component.translatable("tooltip.gensokyou.boss_residual.desc",
                bossDisplayName(boss.get())).withStyle(ChatFormatting.LIGHT_PURPLE));
    }
}
