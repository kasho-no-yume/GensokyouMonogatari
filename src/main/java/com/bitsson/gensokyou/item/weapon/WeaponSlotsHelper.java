package com.bitsson.gensokyou.item.weapon;

import com.bitsson.gensokyou.config.GensokyouConfig;
import com.bitsson.gensokyou.registry.ModDataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 武器三槽读写与等级闸门的统一入口。
 *
 * <p>等级事实来源 = 槽2 等级核（无冗余 weapon_level 字段）。
 * 每次写回都做等级校验：超出新等级的槽1/槽3核自动弹出返还玩家（背包满则掉落）。
 */
public final class WeaponSlotsHelper {

    private WeaponSlotsHelper() {
    }

    public static WeaponSlots read(ItemStack weapon) {
        return weapon.getOrDefault(ModDataComponents.WEAPON_SLOTS.get(), WeaponSlots.DEFAULT);
    }

    public static void write(ItemStack weapon, WeaponSlots slots) {
        weapon.set(ModDataComponents.WEAPON_SLOTS.get(), slots);
    }

    /** 武器等级：slot2 等级核的 tier，未装为 0。 */
    public static int weaponLevel(ItemStack weapon) {
        ItemStack levelCore = read(weapon).slot2();
        if (levelCore.getItem() instanceof WeaponLevelCoreItem core) {
            return core.tier();
        }
        return 0;
    }

    /** 槽2等级对应的伤害增强系数（配置表，等级越高越大）。 */
    public static float weaponLevelMult(ItemStack weapon) {
        List<? extends Double> table = GensokyouConfig.WEAPON_LEVEL_MULT.get();
        int level = Math.max(1, weaponLevel(weapon));
        int index = Math.min(level - 1, table.size() - 1);
        return index < 0 ? 1.0F : table.get(index).floatValue();
    }

    /** 核是否能装入该武器（等级闸门）。 */
    public static boolean canFit(ItemStack weapon, ItemStack core) {
        Integer required = requiredTier(core);
        return required == null || required <= weaponLevel(weapon);
    }

    /** 非 0 表示要求武器等级；null 表示不是可装核。 */
    @Nullable
    public static Integer requiredTier(ItemStack core) {
        Item item = core.getItem();
        if (item instanceof BulletCoreItem bulletCore) {
            return bulletCore.stats().requiredTier().getAsInt();
        }
        if (item instanceof AmpCoreItem ampCore) {
            return ampCore.tier();
        }
        return null;
    }

    /**
     * 写回三槽并执行等级回落校验：超出当前等级的槽1/槽3核弹出返还玩家。
     * 槽2 核在传入的 slots 中已是最新（等级随之变化）。
     */
    public static WeaponSlots writeBack(ServerPlayer player, ItemStack weapon, WeaponSlots slots) {
        int level = weaponLevelOf(slots);
        WeaponSlots adjusted = slots;
        for (int index : new int[]{0, 2}) {
            ItemStack core = adjusted.slot(index);
            Integer required = requiredTier(core);
            if (required != null && required > level) {
                giveOrDrop(player, core);
                adjusted = adjusted.with(index, ItemStack.EMPTY);
            }
        }
        write(weapon, adjusted);
        return adjusted;
    }

    private static int weaponLevelOf(WeaponSlots slots) {
        ItemStack levelCore = slots.slot2();
        if (levelCore.getItem() instanceof WeaponLevelCoreItem core) {
            return core.tier();
        }
        return 0;
    }

    /** 返还玩家：背包优先（死亡状态下不进背包直接掉落），满则原地掉落。 */
    public static void giveOrDrop(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        if (player.isAlive() && player.getInventory().add(stack)) {
            return;
        }
        player.drop(stack, false);
        player.displayClientMessage(Component.translatable("msg.gensokyou.weapon_core_dropped"), true);
    }
}
