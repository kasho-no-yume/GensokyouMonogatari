package com.bitsson.gensokyou.client.screen;

import com.bitsson.gensokyou.client.ClientCrystalStorageState;
import com.bitsson.gensokyou.menu.CrystalStorageMenu;
import com.bitsson.gensokyou.network.CrystalStorageClickPayload;
import com.bitsson.gensokyou.network.CrystalStorageNavPayload;
import com.bitsson.gensokyou.network.CrystalStoragePagePayload;
import com.bitsson.gensokyou.network.InfoLine;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * 无尽藏晶界面：自绘 9×6 网格（承载超堆叠长计数）+ 搜索框 + 排序按钮 + 滚动条。
 * 存储区不注册原版 Slot，点击经 C2S 手势包驱动；玩家背包仍是原版槽。
 */
public class CrystalStorageScreen extends AbstractContainerScreen<CrystalStorageMenu> {

    private static final int PANEL_WIDTH = 184;
    private static final int PANEL_HEIGHT = 250;
    private static final int COLOR_PANEL = 0xFFC6C6C6;
    private static final int COLOR_SLOT = 0xFF8B8B8B;
    private static final int COLOR_EDGE = 0xFF373737;
    private static final int COLOR_TEXT = 0xFF404040;
    private static final int COLOR_COUNT = 0xFFFFFFFF;
    private static final int COLOR_BAR_TRACK = 0xFF5A5A5A;
    private static final int COLOR_BAR_THUMB = 0xFFB0B0B0;

    private static final int SEARCH_X = 8;
    private static final int ROW_Y = 19;
    private static final int SEARCH_W = 76;
    private static final int SEARCH_H = 14;
    private static final int SORT_X = 86;
    private static final int SORT_W = 46;
    private static final int PREV_X = 134;
    private static final int NEXT_X = 152;
    private static final int NAV_W = 16;
    private static final int NAV_H = 16;
    private static final int SCROLLBAR_X = 174;
    private static final int SCROLLBAR_W = 5;

    private EditBox search;
    @Nullable
    private Button sortButton;
    @Nullable
    private Button prevButton;
    @Nullable
    private Button nextButton;

    public CrystalStorageScreen(CrystalStorageMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = PANEL_WIDTH;
        this.imageHeight = PANEL_HEIGHT;
    }

    @Override
    protected void init() {
        super.init();
        this.search = new EditBox(this.font, leftPos + SEARCH_X, topPos + ROW_Y,
                SEARCH_W, SEARCH_H, Component.translatable("gui.gensokyou.crystal_storage.search"));
        this.search.setMaxLength(64);
        this.search.setHint(Component.translatable("gui.gensokyou.crystal_storage.search_hint"));
        this.search.setResponder(value -> sendNav(0, -1));
        addRenderableWidget(this.search);

        this.sortButton = addRenderableWidget(Button.builder(sortLabel(),
                        b -> sendNav(0, (currentSort() + 1) % CrystalStorageMenu.SORT_MODE_COUNT))
                .bounds(leftPos + SORT_X, topPos + ROW_Y - 1, SORT_W, NAV_H).build());
        this.sortButton.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                Component.translatable("gui.gensokyou.crystal_storage.sort.tip")));
        this.prevButton = addRenderableWidget(Button.builder(Component.literal("\u25B2"),
                        b -> sendNav(-CrystalStorageMenu.PAGE_SIZE, -1))
                .bounds(leftPos + PREV_X, topPos + ROW_Y - 1, NAV_W, NAV_H).build());
        this.prevButton.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                Component.translatable("gui.gensokyou.crystal_storage.page_up")));
        this.nextButton = addRenderableWidget(Button.builder(Component.literal("\u25BC"),
                        b -> sendNav(CrystalStorageMenu.PAGE_SIZE, -1))
                .bounds(leftPos + NEXT_X, topPos + ROW_Y - 1, NAV_W, NAV_H).build());
        this.nextButton.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                Component.translatable("gui.gensokyou.crystal_storage.page_down")));
    }

    @Override
    public void containerTick() {
        super.containerTick();
        if (this.sortButton != null) {
            this.sortButton.setMessage(sortLabel());
        }
        CrystalStoragePagePayload page = page();
        int entryCount = page == null ? 0 : page.entryCount();
        int offset = page == null ? 0 : page.offset();
        if (this.prevButton != null) {
            this.prevButton.active = offset > 0;
        }
        if (this.nextButton != null) {
            this.nextButton.active = offset + CrystalStorageMenu.PAGE_SIZE < entryCount;
        }
    }

    private CrystalStoragePagePayload page() {
        return ClientCrystalStorageState.pageFor(this.menu.containerId);
    }

    private int currentSort() {
        CrystalStoragePagePayload page = page();
        return page == null ? CrystalStorageMenu.SORT_COUNT : page.sortMode();
    }

    private Component sortLabel() {
        String key = switch (currentSort()) {
            case CrystalStorageMenu.SORT_NAME -> "gui.gensokyou.crystal_storage.sort.name";
            case CrystalStorageMenu.SORT_ID -> "gui.gensokyou.crystal_storage.sort.id";
            default -> "gui.gensokyou.crystal_storage.sort.count";
        };
        return Component.translatable("gui.gensokyou.crystal_storage.sort", Component.translatable(key));
    }

    private void sendNav(int scrollDelta, int sortMode) {
        PacketDistributor.sendToServer(new CrystalStorageNavPayload(
                this.menu.containerId,
                this.search == null ? "" : this.search.getValue(),
                scrollDelta, sortMode));
    }

    // ---- 背景与内容 ----

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, COLOR_PANEL);
        for (int i = 0; i < CrystalStorageMenu.PAGE_SIZE; i++) {
            drawCellFrame(graphics, cellX(i), cellY(i));
        }
        for (var slot : this.menu.slots) {
            drawCellFrame(graphics, leftPos + slot.x, topPos + slot.y);
        }
        // 图标与计数必须在 renderBg 阶段落笔：此刻尚未分配文本批（在 super.render 的 labels 才分配），
        // 物品批先于文本批 → 文本绘制在上层，避免图标盖住数字。
        renderGridIcons(graphics);
    }

    private void drawCellFrame(GuiGraphics graphics, int x, int y) {
        graphics.fill(x - 1, y - 1, x + 17, y + 17, COLOR_EDGE);
        graphics.fill(x, y, x + 16, y + 16, COLOR_SLOT);
    }

    private int cellX(int i) {
        return leftPos + CrystalStorageMenu.GRID_X + (i % CrystalStorageMenu.COLUMNS) * 18;
    }

    private int cellY(int i) {
        return topPos + CrystalStorageMenu.GRID_Y + (i / CrystalStorageMenu.COLUMNS) * 18;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderGridTooltip(graphics, mouseX, mouseY);
        renderHoveredSlotTooltip(graphics, mouseX, mouseY);
        renderScrollbar(graphics);
    }

    /** 玩家背包槽的物品 tips：原版在 render() 内并不调用 renderTooltip，需自行补上。 */
    private void renderHoveredSlotTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (this.menu.getCarried().isEmpty() && this.hoveredSlot != null && this.hoveredSlot.hasItem()) {
            ItemStack stack = this.hoveredSlot.getItem();
            graphics.renderTooltip(this.font, this.getTooltipFromContainerItem(stack),
                    stack.getTooltipImage(), stack, mouseX, mouseY);
        }
    }

    /** 逐格绘制图标 + 紧凑计数（在 renderBg 阶段调用）。 */
    private void renderGridIcons(GuiGraphics graphics) {
        CrystalStoragePagePayload page = page();
        if (page == null) {
            return;
        }
        var views = page.views();
        for (int i = 0; i < CrystalStorageMenu.PAGE_SIZE && i < views.size(); i++) {
            CrystalStoragePagePayload.View view = views.get(i);
            int x = cellX(i);
            int y = cellY(i);
            graphics.renderItem(view.stack(), x, y);
            String text = InfoLine.compact(view.count());
            int width = this.font.width(text);
            // 物品在 z=150 绘制（renderItem 内部 translate + flush）；计数须抬到 z=200 才能压在其上，
            // 与 renderItemDecorations 的官方做法一致。数字按长度收缩并锚定格子右下角，杜绝超框。
            float scale = switch (text.length()) {
                case 1, 2 -> 1.0F;
                case 3 -> 0.85F;
                case 4 -> 0.7F;
                default -> 0.58F;
            };
            graphics.pose().pushPose();
            graphics.pose().translate(x + 17.0F, y + 16.0F, 200.0F);
            graphics.pose().scale(scale, scale, 1.0F);
            graphics.drawString(this.font, text, -width, -this.font.lineHeight, COLOR_COUNT, true);
            graphics.pose().popPose();
        }
    }

    /** 悬浮 tips：完整物品提示 + 精确数量（在 render 末尾调用，保证在最上层）。 */
    private void renderGridTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        CrystalStoragePagePayload page = page();
        if (page == null) {
            return;
        }
        var views = page.views();
        for (int i = 0; i < CrystalStorageMenu.PAGE_SIZE && i < views.size(); i++) {
            int x = cellX(i);
            int y = cellY(i);
            if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
                List<Component> tooltip = new ArrayList<>(
                        getTooltipFromItem(this.minecraft, views.get(i).stack()));
                tooltip.add(Component.translatable("gui.gensokyou.crystal_storage.tooltip_count",
                        views.get(i).count()));
                graphics.renderComponentTooltip(this.font, tooltip, mouseX, mouseY);
                return;
            }
        }
    }

    private void renderScrollbar(GuiGraphics graphics) {
        CrystalStoragePagePayload page = page();
        if (page == null) {
            return;
        }
        int totalRows = (page.entryCount() + CrystalStorageMenu.COLUMNS - 1) / CrystalStorageMenu.COLUMNS;
        int visibleRows = CrystalStorageMenu.ROWS;
        int height = CrystalStorageMenu.ROWS * 18 - 2;
        int top = topPos + CrystalStorageMenu.GRID_Y;
        int left = leftPos + SCROLLBAR_X;
        graphics.fill(left, top, left + SCROLLBAR_W, top + height, COLOR_BAR_TRACK);
        if (totalRows <= visibleRows) {
            graphics.fill(left, top, left + SCROLLBAR_W, top + height, COLOR_BAR_THUMB);
            return;
        }
        int thumbH = Math.max(12, height * visibleRows / totalRows);
        int rowStart = page.offset() / CrystalStorageMenu.COLUMNS;
        int maxRowStart = totalRows - visibleRows;
        int thumbY = top + (height - thumbH) * rowStart / maxRowStart;
        graphics.fill(left, thumbY, left + SCROLLBAR_W, thumbY + thumbH, COLOR_BAR_THUMB);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        CrystalStoragePagePayload page = page();
        graphics.drawString(this.font, this.title, 8, 6, COLOR_TEXT, false);
        Component pageText = Component.translatable("gui.gensokyou.crystal_storage.page",
                page == null ? 1 : (page.offset() / CrystalStorageMenu.PAGE_SIZE) + 1,
                Math.max(1, page == null ? 1
                        : (page.entryCount() + CrystalStorageMenu.PAGE_SIZE - 1) / CrystalStorageMenu.PAGE_SIZE));
        graphics.drawString(this.font, pageText,
                imageWidth - 8 - this.font.width(pageText), 6, COLOR_TEXT, false);
        Component counts = Component.translatable("gui.gensokyou.crystal_storage.count",
                page == null ? 0 : page.totalCount(), page == null ? 0 : page.capacity());
        graphics.drawString(this.font, counts, 8, PANEL_HEIGHT - 14, COLOR_TEXT, false);
    }

    // ---- 交互 ----

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 || button == 1) {
            int localX = (int) (mouseX - (leftPos + CrystalStorageMenu.GRID_X));
            int localY = (int) (mouseY - (topPos + CrystalStorageMenu.GRID_Y));
            if (localX >= 0 && localX < CrystalStorageMenu.COLUMNS * 18
                    && localY >= 0 && localY < CrystalStorageMenu.ROWS * 18) {
                int cell = (localY / 18) * CrystalStorageMenu.COLUMNS + (localX / 18);
                CrystalStoragePagePayload page = page();
                ItemStack key = page != null && cell < page.views().size()
                        ? page.views().get(cell).stack() : ItemStack.EMPTY;
                boolean shift = hasShiftDown();
                boolean carrying = !this.menu.getCarried().isEmpty();
                int action;
                if (shift) {
                    action = button == 1
                            ? CrystalStorageClickPayload.TAKE_ALL_INV
                            : CrystalStorageClickPayload.TAKE_STACK_INV;
                } else if (carrying) {
                    action = button == 1
                            ? CrystalStorageClickPayload.PUT_ONE
                            : CrystalStorageClickPayload.PUT_ALL;
                } else {
                    if (key.isEmpty()) {
                        return true;
                    }
                    action = button == 1
                            ? CrystalStorageClickPayload.TAKE_HALF
                            : CrystalStorageClickPayload.TAKE_STACK;
                }
                PacketDistributor.sendToServer(new CrystalStorageClickPayload(
                        this.menu.containerId, action, key));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int gridLeft = leftPos + CrystalStorageMenu.GRID_X - 1;
        int gridRight = leftPos + CrystalStorageMenu.GRID_X + CrystalStorageMenu.COLUMNS * 18 + 1;
        int gridTop = topPos + CrystalStorageMenu.GRID_Y - 1;
        int gridBottom = topPos + CrystalStorageMenu.GRID_Y + CrystalStorageMenu.ROWS * 18 + 1;
        if (mouseX >= gridLeft && mouseX < gridRight && mouseY >= gridTop && mouseY < gridBottom) {
            sendNav(scrollY > 0 ? -CrystalStorageMenu.COLUMNS : CrystalStorageMenu.COLUMNS, -1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
}
