package com.bitsson.gensokyou.client;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.client.widget.SakuraSkillSlot;
import com.bitsson.gensokyou.client.widget.SakuraSpiritBar;
import com.bitsson.gensokyou.spirit.SkillStateData;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.AirItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/**
 * 玩家 HUD：灵力条（竖排符箓条）+ 技能槽排（符印格）。
 *
 * <h2>为什么灵力条竖排贴左缘</h2>
 * 原版底部中央那条带子已经被占满了：选中物品名浮字（H-59）/ 状态行（H-39）/
 * 经验条（H-29）/ 快捷栏（H-22），而且**别的 mod 也常在那一带放东西**（ mods 的血条/能量条
 * 几乎都在热栏上方）。左侧中部相对干净，故灵力条竖排贴左缘，
 * 高约屏高的 1/3（{@link SakuraSpiritBar#HEIGHT}），垂直居中。
 *
 * <p>代价：原版聊天框也从 x=4 起、满屏时顶到 H-112，会盖住竖条下沿。
 * 这是<b>有意接受</b>的取舍（聊天有内容才占位，且聊天背景半透明，
 * 灵力比例仍能透出来）。
 *
 * <h2>只显示比例，不显示具体数值</h2>
 * 5 阶上限 1000 万，写成 {@code "10000000 / 10000000"} 要 116px，早就出了屏幕。
 * 比例靠填充长度 + 25/50/75% 刻度表达。
 */
@EventBusSubscriber(modid = Gensokyou.MODID, value = Dist.CLIENT)
public final class HudRenderer {

    /** 灵力条距屏幕左缘。 */
    private static final int SPIRIT_BAR_X = 4;
    /** 技能槽排距屏幕右缘。 */
    private static final int SLOT_MARGIN = 6;
    /** 技能槽间距。 */
    private static final int SLOT_GAP = 3;

    private HudRenderer() {
    }

    @SubscribeEvent
    public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(Gensokyou.id("spirit_power_bar"), HudRenderer::renderSpiritBar);
        event.registerAboveAll(Gensokyou.id("skill_slots"), HudRenderer::renderSkillSlots);
    }

    /** 灵力条：竖排符箓条，填充用品阶色，只显示比例。 */
    private static void renderSpiritBar(GuiGraphics graphics, DeltaTracker deltaTracker) {
        int cur = SpiritPowerClientState.current();
        int max = SpiritPowerClientState.max();
        if (max <= 0) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) {
            return;
        }
        int x = SPIRIT_BAR_X;
        int y = (graphics.guiHeight() - SakuraSpiritBar.HEIGHT) / 2;
        SakuraSpiritBar.render(graphics, x, y, (float) cur / (float) max,
                SpiritPowerClientState.temper());
    }

    /** 技能槽排（skill-slots-hud）：显示槽数 = max(0, 阶级-2)（0 槽整排隐藏），图标按配装卡解析。 */
    private static void renderSkillSlots(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        int slotCount = SkillStateData.slotCountForTier(SpiritPowerClientState.temper());
        if (mc.player == null || mc.options.hideGui || slotCount <= 0) {
            return;
        }
        int size = SakuraSkillSlot.SIZE;
        int totalWidth = slotCount * size + (slotCount - 1) * SLOT_GAP;
        int startX = graphics.guiWidth() - totalWidth - SLOT_MARGIN;
        // 底部留一行给键位标签（画在槽下方，不压图标）
        int y = graphics.guiHeight() - size - SLOT_MARGIN - SakuraSkillSlot.LABEL_H;
        String modLabel = ClientKeyBindings.SKILL_MODIFIER.getTranslatedKeyMessage().getString();

        for (int i = 0; i < slotCount; i++) {
            int x = startX + i * (size + SLOT_GAP);
            boolean learned = ClientSkillState.learned(i);
            int remaining = ClientSkillState.remaining(i);
            ItemStack icon = learned ? iconForCard(ClientSkillState.equipped(i)) : ItemStack.EMPTY;
            SakuraSkillSlot.render(graphics, mc.font, x, y, icon, learned, remaining,
                    keyLabel(mc, modLabel, i));
        }
    }

    /**
     * 槽下方的键位标签。
     *
     * <p>修饰键可改：默认 G（一个字符），但改成 Shift 之类会变成「左 Shift」，
     * 三个字符的标签在 24px 槽宽里会互相压 → 放不下就只显示数字。
     */
    private static String keyLabel(Minecraft mc, String modLabel, int index) {
        String full = modLabel + "+" + (index + 1);
        if (mc.font.width(full) <= SakuraSkillSlot.SIZE) {
            return full;
        }
        return String.valueOf(index + 1);
    }

    /** 卡 id → 同名符卡物品图标（注册表缺项/空气=无图标）。 */
    private static ItemStack iconForCard(String cardId) {
        if (cardId == null || cardId.isEmpty()) {
            return ItemStack.EMPTY;
        }
        Item item = BuiltInRegistries.ITEM.get(Gensokyou.id(cardId));
        return item == null || item instanceof AirItem ? ItemStack.EMPTY : new ItemStack(item);
    }
}
