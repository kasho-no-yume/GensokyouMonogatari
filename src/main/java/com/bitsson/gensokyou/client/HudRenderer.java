package com.bitsson.gensokyou.client;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.spirit.SkillStateData;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.AirItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

@EventBusSubscriber(modid = Gensokyou.MODID, value = Dist.CLIENT)
public final class HudRenderer {

    private HudRenderer() {
    }

    @SubscribeEvent
    public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(Gensokyou.id("spirit_power_bar"), HudRenderer::renderSpiritBar);
        event.registerAboveAll(Gensokyou.id("skill_slots"), HudRenderer::renderSkillSlots);
    }

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
        int width = 91;
        int x = graphics.guiWidth() / 2 - width;
        int y = graphics.guiHeight() - 49;

        graphics.fill(x, y, x + width, y + 5, 0xAA000000);
        float ratio = max <= 0 ? 0F : (float) cur / (float) max;
        int fill = Math.round(width * Math.clamp(ratio, 0F, 1F));
        if (fill > 0) {
            graphics.fill(x, y, x + fill, y + 5, 0xFF55CCFF);
        }
        String text = cur + " / " + max;
        graphics.drawString(mc.font, text,
                x + width - mc.font.width(text), y - 10, 0xFF88DDFF, false);
    }

    /** 技能槽排（skill-slots-hud）：显示槽数 = max(0, 阶级-2)（0 槽整排隐藏），图标按配装卡解析。 */
    private static void renderSkillSlots(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        int slotCount = SkillStateData.slotCountForTier(SpiritPowerClientState.temper());
        if (mc.player == null || mc.options.hideGui || slotCount <= 0) {
            return;
        }
        int size = 20;
        int gap = 2;
        int margin = 6;
        int totalWidth = slotCount * size + (slotCount - 1) * gap;
        int startX = graphics.guiWidth() - totalWidth - margin;
        int y = graphics.guiHeight() - size - margin;
        String modLabel = ClientKeyBindings.SKILL_MODIFIER.getTranslatedKeyMessage().getString();

        for (int i = 0; i < slotCount; i++) {
            int x = startX + i * (size + gap);
            boolean learned = ClientSkillState.learned(i);
            int remaining = ClientSkillState.remaining(i);

            graphics.fill(x, y, x + size, y + size,
                    learned ? 0xAA000000 : 0x88333333);

            if (learned) {
                ItemStack icon = iconForCard(ClientSkillState.equipped(i));
                if (!icon.isEmpty()) {
                    graphics.renderItem(icon, x + 1, y + 1);
                }
                if (remaining > 0) {
                    graphics.fill(x, y, x + size, y + size, 0xAA000088);
                    String seconds = String.valueOf((remaining + 19) / 20);
                    graphics.drawCenteredString(mc.font, seconds,
                            x + size / 2, y + size / 2 - 4, 0xFFFFFFFF);
                }
                graphics.drawString(mc.font, modLabel + "+" + (i + 1), x + 1, y + 1, 0xFFFFFF88, false);
            } else {
                graphics.drawCenteredString(mc.font, "—",
                        x + size / 2, y + size / 2 - 4, 0xFF666666);
            }
        }
        Component title = Component.translatable("hud.gensokyou.skills");
        graphics.drawString(mc.font, title, startX, y - 10, 0xFF88DDFF, false);
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
