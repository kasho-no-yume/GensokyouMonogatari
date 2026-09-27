package com.bitsson.gensokyou.item.weapon;

import com.bitsson.gensokyou.registry.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * 星弹幕铳：单发射击；不蓄力、不走近战。
 * 三个核槽（弹核 / 等级核 / 增幅核）决定弹幕形态、倍率与词条；装核后手持本武器开火。
 */
public class DanmakuWeaponItem extends Item {

    public DanmakuWeaponItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            WeaponFiring.tryFire(serverPlayer, stack);
        }
        return InteractionResultHolder.consume(stack);
    }

    /**
     * 武器 tooltip：概览三个核槽的装配结果。
     *
     * <p>形态与弹幕类型取自 1 号弹核；倍率/射速/耗灵等详细数值仍应看弹核自身 tooltip，
     * 这里只给"当前装的是什么类型 + 增幅核带来了哪些词条"的速览。
     *
     * <p>词条行 MUST 复用 {@link RuneAffix#tooltipLine}，与增幅核自身 tooltip 保持同格式。
     */
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        WeaponSlots slots = stack.getOrDefault(ModDataComponents.WEAPON_SLOTS.get(), WeaponSlots.DEFAULT);

        appendBulletCoreLine(tooltip, slots.slot1());
        appendLevelCoreLine(tooltip, slots.slot2());
        appendAmpCoreLines(tooltip, slots.slot3());

        if (slots.slot1().isEmpty() && slots.slot2().isEmpty() && slots.slot3().isEmpty()) {
            tooltip.add(Component.translatable("tooltip.gensokyou.weapon_no_cores")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    /**
     * 弹幕类型 + 弹核的战斗数值（伤害倍率 / 射速 / 耗灵 / 形态专属 / 有效 DPS）。
     *
     * <p>数值行 MUST 走 {@link BulletCoreItem#appendCombatLines} 与核自身 tooltip 共用，
     * 否则两处会各自漂移。紧凑模式省略分隔线 —— 打开武器 GUI 手续繁琐，这里是唯一能
     * 快速确认装配结果的地方，MUST NOT 因为"字段太多"就只挑几项显示。
     */
    private static void appendBulletCoreLine(List<Component> tooltip, ItemStack core) {
        if (core.isEmpty() || !(core.getItem() instanceof BulletCoreItem bullet)) {
            return;
        }
        tooltip.add(Component.translatable("tooltip.gensokyou.weapon_danmaku_kind",
                        Component.translatable(bullet.pattern().kind().langKey()))
                .withStyle(ChatFormatting.GRAY));
        int tier = bullet.stats().requiredTier().getAsInt();
        if (tier > 0) {
            tooltip.add(Component.translatable("tooltip.gensokyou.weapon_core_tier", tier)
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        BulletCoreItem.appendCombatLines(bullet, tooltip, true);
    }

    /** 武器等级核：只报等级（它不改变弹幕形态，只改倍率档）。 */
    private static void appendLevelCoreLine(List<Component> tooltip, ItemStack core) {
        if (core.isEmpty() || !(core.getItem() instanceof WeaponLevelCoreItem levelCore)) {
            return;
        }
        tooltip.add(Component.translatable("tooltip.gensokyou.weapon_level_core",
                        levelCore.tier()).withStyle(ChatFormatting.DARK_GRAY));
    }

    /** 增幅核：阶位 + 全部词条（即"装上以后手持本武器"的属性加成）。洗练度刻意不显示。 */
    private static void appendAmpCoreLines(List<Component> tooltip, ItemStack core) {
        if (core.isEmpty() || !(core.getItem() instanceof AmpCoreItem amp)) {
            return;
        }
        int tier = amp.tier();
        if (core.has(ModDataComponents.RUNE_AFFIXES.get())) {
            tooltip.add(Component.translatable("tooltip.gensokyou.weapon_amp_core", tier)
                    .withStyle(ChatFormatting.AQUA));
            for (RuneAffix affix
                    : core.getOrDefault(ModDataComponents.RUNE_AFFIXES.get(), List.<RuneAffix>of())) {
                tooltip.add(RuneAffix.tooltipLine(affix).withStyle(ChatFormatting.BLUE));
            }
        } else {
            // 未 roll：只报阶位，不显示任何词条（与核自身 tooltip 的"未 roll 全隐藏"一致）
            tooltip.add(Component.translatable("tooltip.gensokyou.weapon_amp_core_unrolled", tier)
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    /** 供 GUI / 其它查询：当前武器的弹幕类型（未装弹核时为 null）。 */
    @javax.annotation.Nullable
    public static DanmakuKind danmakuKindOf(ItemStack weapon) {
        if (weapon.isEmpty()) {
            return null;
        }
        ItemStack core = weapon.getOrDefault(ModDataComponents.WEAPON_SLOTS.get(), WeaponSlots.DEFAULT).slot1();
        if (core.isEmpty() || !(core.getItem() instanceof BulletCoreItem bullet)) {
            return null;
        }
        return bullet.pattern().kind();
    }
}
