package com.bitsson.gensokyou.event;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.config.GensokyouConfig;
import com.bitsson.gensokyou.registry.ModItems;
import com.bitsson.gensokyou.spirit.ModAttachments;
import com.bitsson.gensokyou.spirit.SpiritPowerData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * 灵铁 / 星银盔甲的行为侧：灵力自然回复加成。
 *
 * <p><b>实现口径</b>：既有自然回复挂在 {@code PlayerAttributes.regenPerSecond} 上，由
 * {@code ModAttachments.tickRegen} 每 20 tick 按"速率 → buffer → 整数部分入池"结算。
 * 本类沿用同一套 buffer 语义，把加成写成"额外往 buffer 追加一份等效速率"，
 * 让它与原版回复走同一条入池路径——这样既有回复的取整/截断行为完全不变，
 * 也不会出现"每 tick 直接改 current"导致的小数累积漂移。
 *
 * <p><b>绝不碰 {@code max} / {@code spiritDamage}</b>：两者是阶级台账求和的单写事实来源，
 * 任何旁路写入都会让玩家属性永久漂移。
 *
 * <p><b>件数折算</b>：{@code bonus = 1 + (mult - 1) × pieces / 4}。穿一件给 1/4 份、
 * 穿满四件给满份，避免"四件就翻四倍"的指数错觉，也让单件胸甲有可见但不失衡的收益。
 */
@EventBusSubscriber(modid = Gensokyou.MODID)
public final class GensokyouArmorEvents {

    private static final EquipmentSlot[] SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private GensokyouArmorEvents() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 20 != 0) {
            return;
        }
        int pieces = countArmorPieces(player);
        if (pieces == 0) {
            return;
        }
        double multiplier = (double) GensokyouConfig.ARMOR_SPIRIT_REGEN_BONUS.get();
        if (multiplier <= 1.0D) {
            return;
        }
        double basePerSecond = com.bitsson.gensokyou.spirit.attr.PlayerAttributes
                .regenPerSecond(player);
        if (basePerSecond <= 0.0D) {
            return;
        }
        // 加成份额 = 基础速率 × (mult − 1) × 件数/4，按同一秒口径追加进 buffer
        float extra = (float) (basePerSecond * (multiplier - 1.0D) * (pieces / 4.0D));
        SpiritPowerData data = ModAttachments.get(player);
        ModAttachments.set(player,
                data.withRegenBuffer(data.regenBuffer() + extra));
    }

    private static int countArmorPieces(ServerPlayer player) {
        int count = 0;
        for (EquipmentSlot slot : SLOTS) {
            if (isOurs(player.getItemBySlot(slot))) {
                count++;
            }
        }
        return count;
    }

    private static boolean isOurs(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        return stack.is(ModItems.SPIRIT_IRON_HELMET.get())
                || stack.is(ModItems.SPIRIT_IRON_CHESTPLATE.get())
                || stack.is(ModItems.SPIRIT_IRON_LEGGINGS.get())
                || stack.is(ModItems.SPIRIT_IRON_BOOTS.get())
                || stack.is(ModItems.STAR_SILVER_HELMET.get())
                || stack.is(ModItems.STAR_SILVER_CHESTPLATE.get())
                || stack.is(ModItems.STAR_SILVER_LEGGINGS.get())
                || stack.is(ModItems.STAR_SILVER_BOOTS.get());
    }
}
