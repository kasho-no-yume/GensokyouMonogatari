package com.bitsson.gensokyou.client.screen;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.client.ClientRitualState;
import com.bitsson.gensokyou.menu.RitualCoreMenu;
import com.bitsson.gensokyou.network.RitualInfoPayload;
import com.bitsson.gensokyou.network.RitualTogglePayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 仪式界面：信息面板 + 祭品核对清单 + 启动/停止按钮。
 * 数据来自服务端推送的 RitualInfoPayload（ClientRitualState 暂存）。
 */
public class RitualCoreScreen extends AbstractContainerScreen<RitualCoreMenu> {

    private static final int PANEL_WIDTH = 200;
    private static final int PANEL_HEIGHT = 150;
    private static final int COLOR_TEXT = 0xFF404040;
    private static final int COLOR_OK = 0xFF2E8B57;
    private static final int COLOR_BAD = 0xFFB22222;
    /** 背景贴图：替换 assets/gensokyou/textures/gui/ritual_core.png 即可整体换肤。 */
    private static final ResourceLocation BACKGROUND =
            Gensokyou.id("textures/gui/ritual_core.png");

    @Nullable
    private Button startButton;
    @Nullable
    private Button stopButton;
    private final Button[] actionButtons = new Button[3];

    public RitualCoreScreen(RitualCoreMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = PANEL_WIDTH;
        this.imageHeight = PANEL_HEIGHT;
    }

    @Override
    protected void init() {
        super.init();
        int x = leftPos + 8;
        int y = topPos + PANEL_HEIGHT - 26;
        startButton = addRenderableWidget(Button.builder(
                        Component.translatable("gui.gensokyou.ritual.start"), b -> send(0))
                .bounds(x, y, 84, 20).build());
        stopButton = addRenderableWidget(Button.builder(
                        Component.translatable("gui.gensokyou.ritual.stop"), b -> send(1))
                .bounds(x + 92, y, 84, 20).build());
        int ax = x + 176;
        for (int i = 0; i < actionButtons.length; i++) {
            final int index = i;
            actionButtons[i] = addRenderableWidget(Button.builder(Component.literal("?"),
                            b -> send(RitualCoreMenu.BUTTON_ACTION_BASE + index))
                    .bounds(ax, topPos + 8 + i * 22, 20, 20).build());
            actionButtons[i].visible = false;
        }
    }

    private void send(int buttonId) {
        if (this.minecraft != null && this.minecraft.gameMode != null) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, buttonId);
        }
    }

    @Override
    public void containerTick() {
        super.containerTick();
        RitualInfoPayload info = ClientRitualState.latest();
        boolean ours = info != null && info.blockPos().equals(menu.pos());
        boolean showButtons = ours && info.toggleable();
        if (startButton != null) {
            startButton.visible = showButtons && !(ours && info.enabled());
        }
        if (stopButton != null) {
            stopButton.visible = showButtons && ours && info.enabled();
        }
        // 行为注入的自定义操作按钮：右侧竖排，随 payload 动态显隐
        for (int i = 0; i < actionButtons.length; i++) {
            Button button = actionButtons[i];
            if (button == null) {
                continue;
            }
            RitualInfoPayload.Action action = ours && i < info.actions().size()
                    ? info.actions().get(i) : null;
            if (action != null) {
                button.setMessage(Component.translatable(action.labelKey()));
                button.visible = true;
            } else {
                button.visible = false;
            }
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(BACKGROUND, leftPos, topPos, 0, 0, PANEL_WIDTH, PANEL_HEIGHT,
                PANEL_WIDTH, PANEL_HEIGHT);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        RitualInfoPayload info = ClientRitualState.latest();
        if (info == null || !info.blockPos().equals(menu.pos())) {
            return;
        }
        var font = this.font;
        int y = 10;
        if (info.patternId().isEmpty()) {
            graphics.drawString(font, Component.translatable("gui.gensokyou.ritual.no_pattern"), 8, y, COLOR_BAD, false);
            return;
        }
        ResourceLocation id = ResourceLocation.parse(info.patternId());
        graphics.drawString(font,
                Component.translatableWithFallback("jei." + id.getNamespace() + ".ritual." + id.getPath(),
                        id.getPath().replace('_', ' ')), 8, y, COLOR_TEXT, false);
        y += 12;
        graphics.drawString(font, Component.translatable("gui.gensokyou.ritual.tier", info.tier()), 8, y, COLOR_TEXT, false);
        graphics.drawString(font,
                info.enabled() ? Component.translatable("gui.gensokyou.ritual.running")
                        : Component.translatable("gui.gensokyou.ritual.idle"),
                PANEL_WIDTH - 60, y, info.enabled() ? COLOR_OK : COLOR_BAD, false);
        y += 12;
        graphics.drawString(font, Component.translatable("gui.gensokyou.ritual.sp", info.stored(), info.capacity()),
                8, y, COLOR_TEXT, false);
        y += 14;
        List<RitualInfoPayload.Entry> entries = info.entries();
        if (!entries.isEmpty()) {
            graphics.drawString(font, Component.translatable("gui.gensokyou.ritual.offerings"), 8, y, COLOR_TEXT, false);
            y += 14;
        }
        for (int i = 0; i < entries.size() && y < PANEL_HEIGHT - 30; i++) {
            RitualInfoPayload.Entry entry = entries.get(i);
            var stack = ClientRitualState.stackFor(entry.itemId());
            if (!stack.isEmpty()) {
                graphics.renderItem(stack, 8, y);
            }
            String label = "×" + entry.count();
            graphics.drawString(font, label, 28, y + 4, COLOR_TEXT, false);
            graphics.drawString(font, entry.satisfied() ? "✓" : "✗",
                    PANEL_WIDTH - 18, y + 4, entry.satisfied() ? COLOR_OK : COLOR_BAD, true);
            y += 18;
        }
        if (!info.recipes().isEmpty() && y < PANEL_HEIGHT - 30) {
            graphics.drawString(font, Component.translatable("gui.gensokyou.ritual.recipes"), 8, y, COLOR_TEXT, false);
            y += 12;
        }
        for (RitualInfoPayload.RecipeInfo recipe : info.recipes()) {
            if (y >= PANEL_HEIGHT - 30) {
                break;
            }
            ResourceLocation recipeId = ResourceLocation.parse(recipe.recipeId());
            Component name = Component.translatableWithFallback(
                    "jei." + recipeId.getNamespace() + ".recipe." + recipeId.getPath(),
                    recipeId.getPath().replace('_', ' '));
            boolean active = !info.activeRecipeId().isEmpty()
                    && info.activeRecipeId().equals(recipe.recipeId());
            int color = recipe.satisfied() ? COLOR_OK : COLOR_BAD;
            graphics.drawString(font, (recipe.satisfied() ? "✓ " : "✗ ") + name.getString(), 8, y, color, false);
            y += 11;
            if (!recipe.satisfied() && !recipe.missingText().isEmpty()) {
                graphics.drawString(font, "  " + recipe.missingText(), 8, y, COLOR_TEXT, false);
                y += 11;
            } else if (active) {
                graphics.drawString(font, "  " + translatableActive(), 8, y, COLOR_OK, false);
                y += 11;
            }
        }
        if (!info.statusKey().isEmpty()) {
            graphics.drawString(font, Component.translatable(info.statusKey()), 8, PANEL_HEIGHT - 34,
                    COLOR_TEXT, false);
        }
    }

    private static Component translatableActive() {
        return Component.translatable("gui.gensokyou.ritual.active_recipe");
    }
}
