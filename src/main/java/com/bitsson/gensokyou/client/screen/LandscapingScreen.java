package com.bitsson.gensokyou.client.screen;

import com.bitsson.gensokyou.config.GensokyouConfig;
import com.bitsson.gensokyou.menu.LandscapingMenu;
import com.bitsson.gensokyou.network.LandscapingStatePayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

/**
 * 整地器配置界面：三个滑块（长 X / 宽 Z / 高 Y）+ 启动按钮。
 *
 * <p>范围语义：长/宽以方块为中心，高以方块为底向上。线框由方块实体渲染器绘制，
 * 参数经 {@code sendBlockUpdated} 同步到客户端 BE。
 *
 * <p>与 {@link SpiritBombScreen} 同样重写 {@link #mouseDragged} 以恢复滑块拖动。
 */
public class LandscapingScreen extends AbstractContainerScreen<LandscapingMenu> {

    private static final int SLIDER_WIDTH = 140;

    private ParamSlider sizeXSlider;
    private ParamSlider sizeZSlider;
    private ParamSlider heightSlider;
    private Button activateButton;
    private LandscapingStatePayload state;
    /** 是否已收到过服务端状态：未收到时按钮中性显示，不误报「已不在世界上」。 */
    private boolean received;
    /** 服务端已生效参数：仅在服务端值真正变化时才回写滑块。 */
    private boolean hasApplied;
    private int appliedX;
    private int appliedZ;
    private int appliedH;

    public LandscapingScreen(LandscapingMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 150;
        this.titleLabelY = 6;
        this.inventoryLabelY = this.imageHeight + 4;
        this.state = menu.state();
    }

    private static int maxSize() {
        return GensokyouConfig.LANDSCAPING_MAX_SIZE.get();
    }

    private static int maxHeight() {
        return GensokyouConfig.LANDSCAPING_MAX_HEIGHT.get();
    }

    @Override
    protected void init() {
        super.init();
        int left = this.leftPos + 16;
        int top = this.topPos + 18;
        boolean present = state != null && state.present();
        int defSize = GensokyouConfig.LANDSCAPING_DEFAULT_SIZE.get();
        int defHeight = GensokyouConfig.LANDSCAPING_DEFAULT_HEIGHT.get();

        this.sizeXSlider = addRenderableWidget(new ParamSlider(left, top, SLIDER_WIDTH, 20,
                "gui.gensokyou.landscaping.size_x", 1, maxSize(),
                present ? state.sizeX() : defSize));
        this.sizeZSlider = addRenderableWidget(new ParamSlider(left, top + 26, SLIDER_WIDTH, 20,
                "gui.gensokyou.landscaping.size_z", 1, maxSize(),
                present ? state.sizeZ() : defSize));
        this.heightSlider = addRenderableWidget(new ParamSlider(left, top + 52, SLIDER_WIDTH, 20,
                "gui.gensokyou.landscaping.size_y", 1, maxHeight(),
                present ? state.height() : defHeight));

        this.activateButton = addRenderableWidget(Button.builder(
                        Component.translatable("gui.gensokyou.landscaping.activate"),
                        button -> menu.submit(true, sizeX(), sizeZ(), height()))
                .bounds(left, top + 84, SLIDER_WIDTH, 20)
                .build());

        // 界面已存在，此时请求一次状态（一次性，不 tick）
        menu.requestState();
        syncButton();
    }

    /**
     * 绕过 {@code AbstractContainerScreen.mouseDragged} 的「恒吞且不转发」缺陷，
     * 把拖动交给聚焦控件（滑块），使滑块可正常拖动。
     */
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.getFocused() != null && this.isDragging() && button == 0) {
            return this.getFocused().mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    private int sizeX() {
        return (int) Math.round(sizeXSlider.paramValue());
    }

    private int sizeZ() {
        return (int) Math.round(sizeZSlider.paramValue());
    }

    private int height() {
        return (int) Math.round(heightSlider.paramValue());
    }

    public void onState(LandscapingStatePayload payload) {
        this.received = true;
        this.state = payload;
        if (sizeXSlider == null || !payload.present()) {
            syncButton();
            return;
        }
        if (!hasApplied || payload.sizeX() != appliedX
                || payload.sizeZ() != appliedZ || payload.height() != appliedH) {
            hasApplied = true;
            appliedX = payload.sizeX();
            appliedZ = payload.sizeZ();
            appliedH = payload.height();
            sizeXSlider.setParamValue(payload.sizeX());
            sizeZSlider.setParamValue(payload.sizeZ());
            heightSlider.setParamValue(payload.height());
        }
        syncButton();
    }

    private void syncButton() {
        if (activateButton == null) {
            return;
        }
        boolean present = received && state != null && state.present();
        if (!present) {
            activateButton.active = false;
            activateButton.setMessage(Component.translatable("gui.gensokyou.landscaping.gone"));
            return;
        }
        boolean affordable = this.minecraft != null && this.minecraft.player != null
                && (this.minecraft.player.isCreative()
                    || com.bitsson.gensokyou.client.SpiritPowerClientState.current() >= estimatedCost());
        activateButton.active = affordable;
        activateButton.setMessage(Component.translatable(affordable
                ? "gui.gensokyou.landscaping.activate"
                : "gui.gensokyou.landscaping.short"));
    }

    /** 当前滑块参数下的灵力消耗（与服务端同公式）。 */
    private long estimatedCost() {
        return com.bitsson.gensokyou.block.entity.LandscapingBlockEntity
                .costFor(sizeX(), sizeZ(), height());
    }

    /** 每次松手提交一次参数（不请求启动），让服务端与线框即时刷新。 */
    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        boolean handled = super.mouseReleased(mouseX, mouseY, button);
        if (state != null && state.present()) {
            menu.submit(false, sizeX(), sizeZ(), height());
        }
        return handled;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(this.leftPos, this.topPos, this.leftPos + this.imageWidth,
                this.topPos + this.imageHeight, 0xC0101010);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        syncButton();
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(this.font,
                Component.translatable("gui.gensokyou.landscaping.cost",
                        String.format("%,d", estimatedCost())),
                this.leftPos + 14, this.topPos + this.imageHeight - 18, 0xE0E0E0, false);
        this.renderTooltip(graphics, mouseX, mouseY);
    }

    /** 每档 1 格取整的整数滑块。 */
    public static class ParamSlider extends AbstractSliderButton {

        private final String labelKey;
        private final int min;
        private final int max;

        public ParamSlider(int x, int y, int width, int height, String labelKey,
                           int min, int max, int current) {
            super(x, y, width, height, Component.empty(), toProgress(current, min, max));
            this.labelKey = labelKey;
            this.min = min;
            this.max = max;
            updateMessage();
        }

        private static double toProgress(int value, int min, int max) {
            return max <= min ? 0.0D : Mth.clamp((value - min) / (double) (max - min), 0.0D, 1.0D);
        }

        public double paramValue() {
            return Math.round(min + this.value * (max - min));
        }

        public void setParamValue(int value) {
            this.value = toProgress(value, min, max);
            this.updateMessage();
        }

        @Override
        protected void applyValue() {
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.translatable(labelKey, (int) paramValue()));
        }
    }
}
