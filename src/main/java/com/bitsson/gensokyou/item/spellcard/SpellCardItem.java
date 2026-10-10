package com.bitsson.gensokyou.item.spellcard;

import com.bitsson.gensokyou.registry.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

public class SpellCardItem extends Item {

    /** 道具符卡缺省品质（无组件视为品 1）。 */
    public static final int DEFAULT_QUALITY = 1;

    /** 对应 SpellCardEffects 注册 id（驱动主题色染色）。 */
    private final String cardId;

    public SpellCardItem(Properties properties, String cardId) {
        super(properties.stacksTo(1));
        this.cardId = cardId;
    }

    public String cardId() {
        return this.cardId;
    }

    /** 读取道具上的符卡品质（缺省品 1）。 */
    public static int qualityOf(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.SPELLCARD_QUALITY.get(), DEFAULT_QUALITY);
    }

    /** 写入符卡品质（掉落/生成入口用）。 */
    public static void setQuality(ItemStack stack, int quality) {
        stack.set(ModDataComponents.SPELLCARD_QUALITY.get(),
                com.bitsson.gensokyou.spirit.SpellCardScaling.clampQuality(quality));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            performEffect(level, player, qualityOf(stack));
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    protected void performEffect(Level level, Player player, int quality) {
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.gensokyou.spellcard_quality", qualityOf(stack))
                .withStyle(ChatFormatting.GRAY));
    }
}
