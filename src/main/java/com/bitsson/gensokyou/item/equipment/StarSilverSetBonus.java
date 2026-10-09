package com.bitsson.gensokyou.item.equipment;

import com.bitsson.gensokyou.config.GensokyouConfig;
import com.bitsson.gensokyou.registry.ModItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * 星银铠套装加成：四件齐全时给一份「灵力强度」乘数。
 *
 * <p><b>为什么不写进 {@code SpiritPowerData.spiritDamage}</b>：该字段是阶级台账求和的
 * 单写事实来源（{@code withRecomputedPool} 之外的任何写入都会让玩家属性永久漂移，
 * 且进阶/洗练会把它冲掉）。因此加成做成「读时乘算」——由
 * {@code ModAttachments.spiritDamage} 在唯一读取入口叠加，装备脱下即时失效、
 * 存档里不留任何痕迹。
 *
 * <p><b>数量级刻意克制</b>：默认 +8%（0.08）。用户红线是"盔甲不得有过高的弹幕防御能力"，
 * 这里给的又是攻击向的小加成，量级再高一格就会让星银铠成为必穿而非可选。
 */
public final class StarSilverSetBonus {

    private static final EquipmentSlot[] SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private StarSilverSetBonus() {
    }

    /** 全套穿戴返回 {@code 1 + bonus}，否则返回 {@code 1}。 */
    public static float multiplier(ServerPlayer player) {
        double configured = GensokyouConfig.STAR_SILVER_SET_SPIRIT_DAMAGE_BONUS.get();
        if (configured <= 0.0D || !hasFullSet(player)) {
            return 1.0F;
        }
        return (float) (1.0D + configured);
    }

    /** 是否四件星银铠齐全。 */
    public static boolean hasFullSet(ServerPlayer player) {
        for (EquipmentSlot slot : SLOTS) {
            ItemStack stack = player.getItemBySlot(slot);
            if (!isStarSilverArmor(stack)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isStarSilverArmor(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        return stack.is(ModItems.STAR_SILVER_HELMET.get())
                || stack.is(ModItems.STAR_SILVER_CHESTPLATE.get())
                || stack.is(ModItems.STAR_SILVER_LEGGINGS.get())
                || stack.is(ModItems.STAR_SILVER_BOOTS.get());
    }
}
