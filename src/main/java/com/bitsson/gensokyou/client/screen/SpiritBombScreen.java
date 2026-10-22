package com.bitsson.gensokyou.client.screen;

import com.bitsson.gensokyou.client.widget.SakuraButton;
import com.bitsson.gensokyou.client.widget.SakuraSlider;
import com.bitsson.gensokyou.config.GensokyouServerConfig;

import com.bitsson.gensokyou.config.GensokyouClientConfig;

import com.bitsson.gensokyou.menu.SpiritBombMenu;
import com.bitsson.gensokyou.network.SpiritBombStatePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

/**
 * 灵力引爆器配置界面：三个滑块 + 消耗预估 + 启动按钮。
 *
 * <p><b>为什么重写 {@link #mouseDragged}</b>：本版本 {@code AbstractContainerScreen.mouseDragged}
 * 恒返回 true 且<b>不转发</b>给聚焦控件，滑块因此只能点不能拖。本界面无物品槽，直接把拖动
 * 转给聚焦控件即可（这也是原版选项界面用普通 {@code Screen} 能拖、而容器界面不能的原因）。
 */
public class SpiritBombScreen extends AbstractContainerScreen<SpiritBombMenu> {

    private static final int SLIDER_WIDTH = 140;
    /** 覆盖窗底色：深紫粉半透明（0xC0 = 75% 不透明，方块仍隐约可见）。 */
    private static final int OVERLAY_BG = 0xC03A2630;
    /** 覆盖窗金边。 */
    private static final int OVERLAY_EDGE = 0xFFD8BA84;
    /** 覆盖窗上的文字色（米白）。 */
    private static final int OVERLAY_TEXT = 0xFFF0DCE2;
    /** 覆盖窗内的凹槽（提示区）底色：深樱红半透明。 */
    private static final int OVERLAY_WELL = 0x80301828;

    private ParamSlider fuseSlider;
    private ParamSlider powerSlider;
    private ParamSlider radiusSlider;
    private Button armButton;
    private SpiritBombStatePayload state;
    /** 是否已收到过服务端状态：未收到时按钮中性显示，不误报「已不在世界上」。 */
    private boolean received;
    /** 服务端已生效参数：仅在服务端值真正变化时才回写滑块。 */
    private boolean hasApplied;
    private int appliedFuse;
    private float appliedPower;
    private int appliedRadius;

    public SpiritBombScreen(SpiritBombMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 152;
        this.titleLabelY = 6;
        this.inventoryLabelY = this.imageHeight + 4;
        this.state = menu.state();
    }

    @Override
    protected void init() {
        super.init();
        int left = this.leftPos + 16;
        int top = this.topPos + 18;

        boolean present = state != null && state.present();
        this.fuseSlider = addRenderableWidget(new ParamSlider(left, top, SLIDER_WIDTH, 20,
                "gui.gensokyou.spirit_bomb.fuse", 0.1D, 600.0D,
                present ? state.fuseTicks() / 20.0D : defaultFuseSeconds(), 0.1D));
        this.powerSlider = addRenderableWidget(new ParamSlider(left, top + 26, SLIDER_WIDTH, 20,
                "gui.gensokyou.spirit_bomb.power", 1.0D, 12.0D,
                present ? state.power() : 4.0D, 0.1D));
        this.radiusSlider = addRenderableWidget(new ParamSlider(left, top + 52, SLIDER_WIDTH, 20,
                "gui.gensokyou.spirit_bomb.radius", 1.0D, 24.0D,
                present ? state.radius() : 6.0D, 1.0D));

        this.armButton = addRenderableWidget(SakuraButton.of(
                        Component.translatable("gui.gensokyou.spirit_bomb.arm"),
                        button -> menu.submit(true, fuseTicks(), powerValue(), radiusValue()),
                left, top + 82, SLIDER_WIDTH, 20));

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

    private static double defaultFuseSeconds() {
        return GensokyouServerConfig.SPIRIT_BOMB_DEFAULT_FUSE_TICKS.get() / 20.0D;
    }

    private int fuseTicks() {
        return (int) Math.round(fuseSlider.paramValue() * 20.0D);
    }

    private float powerValue() {
        return (float) powerSlider.paramValue();
    }

    private int radiusValue() {
        return (int) Math.round(radiusSlider.paramValue());
    }

    /** 客户端收到服务端状态：仅在服务端参数<b>真正变化</b>时才回写滑块。 */
    public void onState(SpiritBombStatePayload payload) {
        this.received = true;
        this.state = payload;
        if (fuseSlider == null || !payload.present()) {
            syncButton();
            return;
        }
        if (!hasApplied || payload.fuseTicks() != appliedFuse
                || payload.power() != appliedPower || payload.radius() != appliedRadius) {
            hasApplied = true;
            appliedFuse = payload.fuseTicks();
            appliedPower = payload.power();
            appliedRadius = payload.radius();
            fuseSlider.setParamValue(payload.fuseTicks() / 20.0D);
            powerSlider.setParamValue(payload.power());
            radiusSlider.setParamValue(payload.radius());
        }
        syncButton();
    }

    private void syncButton() {
        if (armButton == null) {
            return;
        }
        if (!received) {
            armButton.active = false;
            armButton.setMessage(Component.translatable("gui.gensokyou.spirit_bomb.arm"));
            return;
        }
        if (state == null || !state.present()) {
            armButton.active = false;
            armButton.setMessage(Component.translatable("gui.gensokyou.spirit_bomb.gone"));
            return;
        }
        if (state.armed()) {
            armButton.active = false;
            armButton.setMessage(Component.translatable("gui.gensokyou.spirit_bomb.armed"));
            return;
        }
        armButton.active = state.affordable();
        armButton.setMessage(Component.translatable(state.affordable()
                ? "gui.gensokyou.spirit_bomb.arm"
                : "gui.gensokyou.spirit_bomb.short"));
    }

    /** 每次松手都把参数提交一次（不请求启动），让消耗预估即时刷新。 */
    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        boolean handled = super.mouseReleased(mouseX, mouseY, button);
        if (state != null && state.present() && !state.armed()) {
            menu.submit(false, fuseTicks(), powerValue(), radiusValue());
        }
        return handled;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        // 覆盖窗（叠在方块界面上）：保留半透明语义，但换成樱符的深紫粉 + 金边。
        // 刻意**不**做成不透光的和纸面板 —— 那会挡住玩家正在配置的方块。
        graphics.fill(this.leftPos, this.topPos, this.leftPos + this.imageWidth,
                this.topPos + this.imageHeight, OVERLAY_BG);
        graphics.fill(this.leftPos, this.topPos, this.leftPos + this.imageWidth,
                this.topPos + 1, OVERLAY_EDGE);
        graphics.fill(this.leftPos, this.topPos + this.imageHeight - 1,
                this.leftPos + this.imageWidth, this.topPos + this.imageHeight, OVERLAY_EDGE);
        graphics.fill(this.leftPos, this.topPos, this.leftPos + 1,
                this.topPos + this.imageHeight, OVERLAY_EDGE);
        graphics.fill(this.leftPos + this.imageWidth - 1, this.topPos,
                this.leftPos + this.imageWidth, this.topPos + this.imageHeight, OVERLAY_EDGE);
        // 底部提示凹槽
        graphics.fill(this.leftPos + 8, this.topPos + this.imageHeight - 26,
                this.leftPos + this.imageWidth - 8, this.topPos + this.imageHeight - 8, OVERLAY_WELL);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        syncButton();
        super.render(graphics, mouseX, mouseY, partialTick);
        if (state != null && state.present()) {
            graphics.drawString(this.font,
                    Component.translatable("gui.gensokyou.spirit_bomb.cost",
                            String.format("%,d", state.estimatedCost())),
                    this.leftPos + 14, this.topPos + this.imageHeight - 22, 0xE0E0E0, false);
        }
        this.renderTooltip(graphics, mouseX, mouseY);
    }

    /** 把一个 double 参数映射到 0..1 的滑块进度，按 step 取整。 */
    public static class ParamSlider extends AbstractSliderButton {

        private final String labelKey;
        private final double min;
        private final double max;
        private final double step;

        public ParamSlider(int x, int y, int width, int height, String labelKey,
                           double min, double max, double current, double step) {
            super(x, y, width, height, Component.empty(), toProgress(current, min, max));
            this.labelKey = labelKey;
            this.min = min;
            this.max = max;
            this.step = step;
            updateMessage();
        }

        private static double toProgress(double value, double min, double max) {
            return Mth.clamp((value - min) / (max - min), 0.0D, 1.0D);
        }

        /** 当前参数值（已按 step 取整并钳制）。 */
        public double paramValue() {
            double raw = min + this.value * (max - min);
            double snapped = Math.round(raw / step) * step;
            return Mth.clamp(snapped, min, max);
        }

        /** 由服务端回发值驱动（钳制后对齐）。 */
        public void setParamValue(double value) {
            this.value = toProgress(value, min, max);
            updateMessage();
        }

        /** 拖动过程中被原版调用。刻意什么都不做：改参数是松手才提交的服务端权威动作。 */
        @Override
        protected void applyValue() {
        }

        @Override
        public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            // 原版是两张灰调贴图（widget/slider + widget/slider_handle），在樱符面板上脱节 -> 自绘
            SakuraSlider.render(g, Minecraft.getInstance().font, getX(), getY(), getWidth(), getHeight(),
                    this.value, getMessage(), OVERLAY_TEXT);
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.translatable(labelKey, formatValue(paramValue())));
        }

        private String formatValue(double value) {
            return step >= 1.0D
                    ? String.valueOf((int) Math.round(value))
                    : String.format("%.1f", value);
        }
    }
}
