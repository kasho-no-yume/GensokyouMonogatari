package com.bitsson.gensokyou.item.equipment;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.registry.ModItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;

/**
 * 龙鳞铠套装判定：四件齐全才给「火焰伤害全免 + 100% 抗击退」。
 *
 * <p><b>火焰全免走伤害事件而非药水效果</b>：{@code FireResistance} 的语义就是
 * 「{@code #minecraft:is_fire} 伤害归零」，直接在 {@code LivingIncomingDamageEvent} 里
 * 拦掉同一标签即可——不会在 HUD 上常驻一个药水图标，脱下任一件也不会有残留时长。
 *
 * <p><b>抗击退是属性修饰</b>：{@code KNOCKBACK_RESISTANCE = 1.0} 覆盖近战、爆炸与弹幕
 * 击退。修饰符用固定 id 幂等增删：每 tick 复算时「缺就补、多就删」，
 * 死亡/换维度导致属性被清空后也能自愈。
 */
public final class DragonScaleSetBonus {

    private static final EquipmentSlot[] SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    /** 抗击退修饰符的固定 id（幂等增删的锚点）。 */
    public static final ResourceLocation KNOCKBACK_MODIFIER =
            Gensokyou.id("attribute.dragon_scale_knockback_resistance");

    private DragonScaleSetBonus() {
    }

    /** 是否四件龙鳞铠齐全。 */
    public static boolean hasFullSet(ServerPlayer player) {
        for (EquipmentSlot slot : SLOTS) {
            ItemStack stack = player.getItemBySlot(slot);
            if (stack == null || stack.isEmpty()) {
                return false;
            }
            boolean ours = stack.is(ModItems.DRAGON_SCALE_HELMET.get())
                    || stack.is(ModItems.DRAGON_SCALE_CHESTPLATE.get())
                    || stack.is(ModItems.DRAGON_SCALE_LEGGINGS.get())
                    || stack.is(ModItems.DRAGON_SCALE_BOOTS.get());
            if (!ours) {
                return false;
            }
        }
        return true;
    }

    /** 按是否齐全幂等同步 100% 抗击退修饰符。 */
    public static void syncKnockback(ServerPlayer player, boolean fullSet) {
        AttributeInstance attribute = player.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (attribute == null) {
            return;
        }
        boolean present = attribute.getModifier(KNOCKBACK_MODIFIER) != null;
        if (fullSet && !present) {
            attribute.addTransientModifier(new AttributeModifier(
                    KNOCKBACK_MODIFIER, 1.0D, AttributeModifier.Operation.ADD_VALUE));
        } else if (!fullSet && present) {
            attribute.removeModifier(KNOCKBACK_MODIFIER);
        }
    }
}
