package com.bitsson.gensokyou.client.screen;

import com.bitsson.gensokyou.worldgen.GensokyoLayout;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Renders the shared layout without sampling or loading world chunks. */
public final class GensokyoDebugMapScreen extends Screen {
    private static final int MAP_W = 448;
    private static final int MAP_H = 448;
    private static final int CELL = 16;
    private static final int LEGEND_W = 180;
    private static final int[] REGION_COLORS = {
            0xFFE8D7A8, 0xFF7DA4D6, 0xFF4B8C50, 0xFF78B95E,
            0xFFEDE8E0, 0xFF5A5058, 0xFF984E59, 0xFFB7A166
    };

    public GensokyoDebugMapScreen() {
        super(Component.translatable("screen.gensokyou.debug_map"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xFF10151C);
        int left = (width - MAP_W - LEGEND_W - 26) / 2;
        int top = Math.max(18, (height - MAP_H) / 2);
        graphics.fill(left - 6, top - 6, left + MAP_W + LEGEND_W + 20, top + MAP_H + 6, 0xFF2C3138);

        int scale = 18;
        for (int mapX = 0; mapX < MAP_W; mapX += CELL) {
            for (int mapZ = 0; mapZ < MAP_H; mapZ += CELL) {
                int worldX = (mapX - MAP_W / 2) * scale;
                int worldZ = (mapZ - MAP_H / 2) * scale;
                GensokyoLayout.Region region = GensokyoLayout.regionAt(worldX, worldZ);
                int color = region == null ? 0xFF263F55 : REGION_COLORS[region.ordinal()];
                graphics.fill(left + mapX, top + mapZ, left + mapX + CELL,
                        top + mapZ + CELL, color);
            }
        }

        for (GensokyoLayout.Region region : GensokyoLayout.Region.values()) {
            int x = left + MAP_W / 2 + region.landmarkX() / scale;
            int z = top + MAP_H / 2 + region.landmarkZ() / scale;
            graphics.fill(x - 2, z - 2, x + 3, z + 3, 0xFF241B22);
        }
        int legendX = left + MAP_W + 16;
        graphics.drawString(font, Component.translatable("screen.gensokyou.debug_map.legend"),
                legendX, top + 4, 0xFFFFFFFF, true);
        int rowY = top + 24;
        for (GensokyoLayout.Region region : GensokyoLayout.Region.values()) {
            graphics.fill(legendX, rowY + 1, legendX + 12, rowY + 13, REGION_COLORS[region.ordinal()]);
            graphics.drawString(font, Component.translatable("biome.gensokyou." + region.biomePath()),
                    legendX + 18, rowY + 2, 0xFFFFFFFF, false);
            rowY += 22;
        }
        graphics.fill(legendX, rowY + 1, legendX + 12, rowY + 13, 0xFF263F55);
        graphics.drawString(font, Component.translatable("biome.gensokyou.sanzu_river"),
                legendX + 18, rowY + 2, 0xFFFFFFFF, false);
        graphics.drawString(font, title, left, top - 18, 0xFFFFFFFF, true);
        graphics.drawString(font, Component.translatable("screen.gensokyou.debug_map.hint"),
                left, top + MAP_H + 10, 0xFFCFD7E0, false);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (super.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (minecraft != null && minecraft.options.keyInventory.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        return false;
    }
}
