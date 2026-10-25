package com.bitsson.gensokyou.item.equipment;

import com.bitsson.gensokyou.registry.ModItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * 人鱼鳞铠套装判定：四件齐全才给「水下呼吸 + 仅水下夜视」。
 *
 * <p><b>为什么不在物品上写效果</b>：{@code ArmorItem} 单件只能带属性修饰，功能效果必须
 * 由事件每 tick 复算；写法对齐既有的 {@link StarSilverSetBonus}（读时判定、脱下即失效、
 * 存档不留痕）。
 *
 * <p><b>夜视的「仅水下」口径</b>：只在 {@code player.isUnderWater()} 时续期一份
 * 5 秒（{@value #NIGHT_VISION_TICKS} tick）的夜视，出水后自然在 5 秒内消退——
 * 这正是 design 允许的降级实现（「出水后保留不超过 5 秒」），
 * 同时避免为区分「自己给的夜视」与「药水给的夜视」而引入额外状态位。
 */
public final class MermaidScaleSetBonus {

    private static final EquipmentSlot[] SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    /** 水下续期给的海豚式夜视时长（tick）= 5 秒，即允许的出水保留上限。 */
    public static final int NIGHT_VISION_TICKS = 100;
    /** 水下呼吸续期时长（tick）；远小于续期间隔即可无缝，不必每 tick 重挂。 */
    public static final int WATER_BREATHING_TICKS = 200;
    /** 套装判定的复算间隔（tick）。 */
    public static final int TICK_INTERVAL = 20;

    private MermaidScaleSetBonus() {
    }

    /** 是否四件人鱼鳞铠齐全。 */
    public static boolean hasFullSet(ServerPlayer player) {
        for (EquipmentSlot slot : SLOTS) {
            ItemStack stack = player.getItemBySlot(slot);
            if (stack == null || stack.isEmpty()) {
                return false;
            }
            boolean ours = stack.is(ModItems.MERMAID_SCALE_HELMET.get())
                    || stack.is(ModItems.MERMAID_SCALE_CHESTPLATE.get())
                    || stack.is(ModItems.MERMAID_SCALE_LEGGINGS.get())
                    || stack.is(ModItems.MERMAID_SCALE_BOOTS.get());
            if (!ours) {
                return false;
            }
        }
        return true;
    }
}
