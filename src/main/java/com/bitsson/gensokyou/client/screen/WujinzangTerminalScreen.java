package com.bitsson.gensokyou.client.screen;

import com.bitsson.gensokyou.client.ClientCrystalStorageState;
import com.bitsson.gensokyou.client.ClientRitualState;
import com.bitsson.gensokyou.menu.WujinzangTerminalMenu;
import com.bitsson.gensokyou.network.CrystalStorageClickPayload;
import com.bitsson.gensokyou.network.CrystalStorageNavPayload;
import com.bitsson.gensokyou.network.CrystalStoragePagePayload;
import com.bitsson.gensokyou.network.InfoLine;
import com.bitsson.gensokyou.network.RitualInfoPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * 无尽藏终端界面（批 1）。
 *
 * <pre>
 *   ┌──────────┬────────────────────┐
 *   │ 仪式信息  │  仓库格子（搜索/排序/翻页）│
 *   │ 灵力核心槽 │  合成区（3×3 + 结果）│
 *   │ 启停      │  物品栏（主仓 + 快捷栏）│
 *   └──────────┴────────────────────┘
 * </pre>
 * 左列约占 1/4；存储区自绘、点击经 C2S 手势；合成格为纯原版合成，不自动抽料。
 */
public class WujinzangTerminalScreen extends AbstractContainerScreen<WujinzangTerminalMenu> {

    private static final int COLOR_PANEL = 0xFFC6C6C6;
    private static final int COLOR_SLOT = 0xFF8B8B8B;
    private static final int COLOR_EDGE = 0xFF373737;
    private static final int COLOR_TEXT = 0xFF404040;
    private static final int COLOR_OK = 0xFF2E8B57;
    private static final int COLOR_BAD = 0xFFB22222;
    private static final int COLOR_COUNT = 0xFFFFFFFF;
    private static final int COLOR_BAR_TRACK = 0xFF5A5A5A;
    private static final int COLOR_BAR_THUMB = 0xFFB0B0B0;
    private static final int COLOR_DIVIDER = 0xFF8B8B8B;

    private static final int LEFT_W = WujinzangTerminalMenu.LEFT_WIDTH;

    private static final int SEARCH_X = WujinzangTerminalMenu.GRID_X;
    private static final int ROW_Y = 6;
    private static final int SEARCH_W = 86;
    private static final int SEARCH_H = 14;
    private static final int SORT_X = SEARCH_X + SEARCH_W + 4;
    private static final int SORT_W = 46;
    private static final int NAV_W = 14;
    private static final int NAV_H = 16;
    private static final int PREV_X = SORT_X + SORT_W + 4;
    private static final int NEXT_X = PREV_X + NAV_W + 2;
    private static final int SCROLLBAR_X = WujinzangTerminalMenu.GRID_X + 9 * 18 + 2;
    private static final int SCROLLBAR_W = 6;

    private EditBox search;
    @Nullable
    private Button sortButton;
    @Nullable
    private Button prevButton;
    @Nullable
    private Button nextButton;
    @Nullable
    private Button startButton;
    @Nullable
    private Button stopButton;
    @Nullable
    private Button returnCraftButton;

    private static final int RETURN_W = 12;
    private static final int RETURN_X = WujinzangTerminalMenu.RESULT_X - 8;
    private static final int RETURN_Y = WujinzangTerminalMenu.RESULT_Y - 15;

    public WujinzangTerminalScreen(WujinzangTerminalMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = WujinzangTerminalMenu.WIDTH;
        this.imageHeight = WujinzangTerminalMenu.HEIGHT;
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
                        b -> sendNav(0, (currentSort() + 1) % WujinzangTerminalMenu.SORT_MODE_COUNT))
                .bounds(leftPos + SORT_X, topPos + ROW_Y - 1, SORT_W, NAV_H).build());
        this.prevButton = addRenderableWidget(Button.builder(Component.literal("\u25B2"),
                        b -> sendNav(-WujinzangTerminalMenu.PAGE_SIZE, -1))
                .bounds(leftPos + PREV_X, topPos + ROW_Y - 1, NAV_W, NAV_H).build());
        this.nextButton = addRenderableWidget(Button.builder(Component.literal("\u25BC"),
                        b -> sendNav(WujinzangTerminalMenu.PAGE_SIZE, -1))
                .bounds(leftPos + NEXT_X, topPos + ROW_Y - 1, NAV_W, NAV_H).build());

        this.startButton = addRenderableWidget(Button.builder(
                        Component.translatable("gui.gensokyou.ritual.start"), b -> sendButton(0))
                .bounds(leftPos + 6, topPos + 80, 108, 18).build());
        this.stopButton = addRenderableWidget(Button.builder(
                        Component.translatable("gui.gensokyou.ritual.stop"), b -> sendButton(1))
                .bounds(leftPos + 6, topPos + 100, 108, 18).build());
        this.startButton.visible = false;
        this.stopButton.visible = false;

        // 产物格左上角叉叉：把合成格内材料返回仓储
        this.returnCraftButton = addRenderableWidget(Button.builder(Component.literal("\u2715"),
                        b -> sendButton(WujinzangTerminalMenu.BUTTON_RETURN_CRAFT))
                .bounds(leftPos + RETURN_X, topPos + RETURN_Y, RETURN_W, RETURN_W).build());
        this.returnCraftButton.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                Component.translatable("gui.gensokyou.wujinzang.return_craft")));
    }

    private void sendButton(int id) {
        if (this.minecraft != null && this.minecraft.gameMode != null) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, id);
        }
    }

    private void sendNav(int scrollDelta, int sortMode) {
        PacketDistributor.sendToServer(new CrystalStorageNavPayload(
                this.menu.containerId,
                this.search == null ? "" : this.search.getValue(),
                scrollDelta, sortMode));
    }

    private CrystalStoragePagePayload page() {
        return ClientCrystalStorageState.pageFor(this.menu.containerId);
    }

    private int currentSort() {
        CrystalStoragePagePayload page = page();
        return page == null ? WujinzangTerminalMenu.SORT_COUNT : page.sortMode();
    }

    private Component sortLabel() {
        String key = switch (currentSort()) {
            case WujinzangTerminalMenu.SORT_NAME -> "gui.gensokyou.crystal_storage.sort.name";
            case WujinzangTerminalMenu.SORT_ID -> "gui.gensokyou.crystal_storage.sort.id";
            default -> "gui.gensokyou.crystal_storage.sort.count";
        };
        return Component.translatable("gui.gensokyou.crystal_storage.sort", Component.translatable(key));
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
            this.nextButton.active = offset + WujinzangTerminalMenu.PAGE_SIZE < entryCount;
        }
        RitualInfoPayload info = ClientRitualState.latest();
        boolean ours = info != null && info.blockPos().equals(menu.pos());
        boolean showButtons = ours && info.toggleable();
        menu.syncCoreSocketVisible(ours);
        if (this.startButton != null) {
            this.startButton.visible = showButtons && !(ours && info.enabled());
        }
        if (this.stopButton != null) {
            this.stopButton.visible = showButtons && ours && info.enabled();
        }
    }

    // ---- 背景 ----

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, COLOR_PANEL);
        graphics.fill(leftPos + LEFT_W, topPos, leftPos + LEFT_W + 2, topPos + imageHeight,
                COLOR_DIVIDER);
        if (menu.coreSocketShown()) {
            drawCellFrame(graphics, leftPos + WujinzangTerminalMenu.BATTERY_SLOT_X,
                    topPos + WujinzangTerminalMenu.BATTERY_SLOT_Y);
        }
        for (int i = 0; i < WujinzangTerminalMenu.PAGE_SIZE; i++) {
            drawCellFrame(graphics, cellX(i), cellY(i));
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                drawCellFrame(graphics, leftPos + WujinzangTerminalMenu.CRAFT_X + col * 18,
                        topPos + WujinzangTerminalMenu.CRAFT_Y + row * 18);
            }
        }
        drawCellFrame(graphics, leftPos + WujinzangTerminalMenu.RESULT_X,
                topPos + WujinzangTerminalMenu.RESULT_Y);
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                drawCellFrame(graphics, leftPos + WujinzangTerminalMenu.PLAYER_INV_X + col * 18,
                        topPos + WujinzangTerminalMenu.PLAYER_INV_Y + row * 18);
            }
        }
        for (int col = 0; col < 9; col++) {
            drawCellFrame(graphics, leftPos + WujinzangTerminalMenu.PLAYER_INV_X + col * 18,
                    topPos + WujinzangTerminalMenu.HOTBAR_Y);
        }
        if (storageOpen()) {
            renderGridIcons(graphics);
        } else {
            drawStorageLock(graphics);
        }
    }

    /** 仓储是否开放：仪式信息为本人终端且处于启动态。 */
    private boolean storageOpen() {
        RitualInfoPayload info = ClientRitualState.latest();
        return info != null && info.blockPos().equals(menu.pos()) && info.enabled();
    }

    /** 停止态：网格区叠灰罩并提示，内容不下发也不绘制。 */
    private void drawStorageLock(GuiGraphics graphics) {
        int x0 = leftPos + WujinzangTerminalMenu.GRID_X - 1;
        int y0 = topPos + WujinzangTerminalMenu.GRID_Y - 1;
        int w = WujinzangTerminalMenu.COLUMNS * 18 + 2;
        int h = WujinzangTerminalMenu.ROWS * 18 + 2;
        graphics.fill(x0, y0, x0 + w, y0 + h, 0xB0000000);
        Component hint = Component.translatable("gui.gensokyou.crystal_storage.locked");
        int tw = this.font.width(hint);
        graphics.drawString(this.font, hint, x0 + (w - tw) / 2, y0 + h / 2 - 4,
                0xFFE0E0E0, false);
    }

    private void drawCellFrame(GuiGraphics graphics, int x, int y) {
        graphics.fill(x - 1, y - 1, x + 17, y + 17, COLOR_EDGE);
        graphics.fill(x, y, x + 16, y + 16, COLOR_SLOT);
    }

    private int cellX(int i) {
        return leftPos + WujinzangTerminalMenu.GRID_X + (i % WujinzangTerminalMenu.COLUMNS) * 18;
    }

    private int cellY(int i) {
        return topPos + WujinzangTerminalMenu.GRID_Y + (i / WujinzangTerminalMenu.COLUMNS) * 18;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderGridTooltip(graphics, mouseX, mouseY);
        renderHoveredSlotTooltip(graphics, mouseX, mouseY);
        renderScrollbar(graphics);
    }

    private void renderHoveredSlotTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (this.menu.getCarried().isEmpty() && this.hoveredSlot != null && this.hoveredSlot.hasItem()) {
            ItemStack stack = this.hoveredSlot.getItem();
            graphics.renderTooltip(this.font, this.getTooltipFromContainerItem(stack),
                    stack.getTooltipImage(), stack, mouseX, mouseY);
        }
    }

    private void renderGridIcons(GuiGraphics graphics) {
        CrystalStoragePagePayload page = page();
        if (page == null) {
            return;
        }
        var views = page.views();
        for (int i = 0; i < WujinzangTerminalMenu.PAGE_SIZE && i < views.size(); i++) {
            CrystalStoragePagePayload.View view = views.get(i);
            int x = cellX(i);
            int y = cellY(i);
            graphics.renderItem(view.stack(), x, y);
            String text = InfoLine.compact(view.count());
            int width = this.font.width(text);
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

    private void renderGridTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        CrystalStoragePagePayload page = page();
        if (page == null || !storageOpen()) {
            return;
        }
        var views = page.views();
        for (int i = 0; i < WujinzangTerminalMenu.PAGE_SIZE && i < views.size(); i++) {
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
        int totalRows = (page.entryCount() + WujinzangTerminalMenu.COLUMNS - 1)
                / WujinzangTerminalMenu.COLUMNS;
        int visibleRows = WujinzangTerminalMenu.ROWS;
        int height = WujinzangTerminalMenu.ROWS * 18 - 2;
        int top = topPos + WujinzangTerminalMenu.GRID_Y;
        int left = leftPos + SCROLLBAR_X;
        graphics.fill(left, top, left + SCROLLBAR_W, top + height, COLOR_BAR_TRACK);
        if (totalRows <= visibleRows) {
            graphics.fill(left, top, left + SCROLLBAR_W, top + height, COLOR_BAR_THUMB);
            return;
        }
        int thumbH = Math.max(12, height * visibleRows / totalRows);
        int rowStart = page.offset() / WujinzangTerminalMenu.COLUMNS;
        int maxRowStart = totalRows - visibleRows;
        int thumbY = top + (height - thumbH) * rowStart / maxRowStart;
        graphics.fill(left, thumbY, left + SCROLLBAR_W, thumbY + thumbH, COLOR_BAR_THUMB);
    }

    // ---- 文本（面板相对坐标） ----

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        RitualInfoPayload info = ClientRitualState.latest();
        boolean ours = info != null && info.blockPos().equals(menu.pos());
        // 左列：仪式名称 + 状态
        Component name = this.title;
        if (ours && !info.patternId().isEmpty()) {
            ResourceLocation id = ResourceLocation.parse(info.patternId());
            name = Component.translatableWithFallback(
                    "jei." + id.getNamespace() + ".ritual." + id.getPath(),
                    id.getPath().replace('_', ' '));
        }
        graphics.drawString(this.font, trim(name.getString()), 6, 5, COLOR_TEXT, false);
        if (ours && !info.patternId().isEmpty()) {
            Component state = Component.translatable(info.enabled()
                    ? "gui.gensokyou.ritual.running" : "gui.gensokyou.ritual.idle");
            graphics.drawString(this.font, state, LEFT_W - 6 - this.font.width(state), 5,
                    info.enabled() ? COLOR_OK : COLOR_BAD, false);
            graphics.drawString(this.font, Component.translatable("gui.gensokyou.ritual.tier",
                    info.tier()), 6, 28, COLOR_TEXT, false);
            if (info.capacity() > 0) {
                graphics.drawString(this.font, Component.translatable("gui.gensokyou.ritual.sp",
                                InfoLine.compact(info.stored()), InfoLine.compact(info.capacity())),
                        6, 40, COLOR_TEXT, false);
            }
            if (menu.coreSocketShown()) {
                graphics.drawString(this.font,
                        Component.translatable("gui.gensokyou.ritual.spirit_core_slot"),
                        WujinzangTerminalMenu.BATTERY_SLOT_X + 20,
                        WujinzangTerminalMenu.BATTERY_SLOT_Y + 4, COLOR_TEXT, false);
            }
            renderInfoLines(graphics, info.infoLines());
        }
    }

    private void renderInfoLines(GuiGraphics graphics, List<InfoLine> lines) {
        int y = 124;
        int bottom = imageHeight - 6;
        for (InfoLine line : lines) {
            if (y >= bottom) {
                break;
            }
            int color = line.color() != 0 ? line.color() : COLOR_TEXT;
            Component text = line.textKey().isEmpty() ? null
                    : Component.translatable(line.textKey(), (Object[]) line.textArgs());
            if (text == null) {
                continue;
            }
            for (var row : this.font.split(text, LEFT_W - 12)) {
                if (y >= bottom) {
                    break;
                }
                graphics.drawString(this.font, row, 6, y, color, false);
                y += 10;
            }
            y += 2;
        }
    }

    private String trim(String text) {
        if (this.font.width(text) <= LEFT_W - 12) {
            return text;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            if (this.font.width(sb.toString() + text.charAt(i) + "…") > LEFT_W - 12) {
                break;
            }
            sb.append(text.charAt(i));
        }
        return sb + "…";
    }

    // ---- 交互 ----

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 || button == 1) {
            int localX = (int) (mouseX - (leftPos + WujinzangTerminalMenu.GRID_X));
            int localY = (int) (mouseY - (topPos + WujinzangTerminalMenu.GRID_Y));
            if (localX >= 0 && localX < WujinzangTerminalMenu.COLUMNS * 18
                    && localY >= 0 && localY < WujinzangTerminalMenu.ROWS * 18) {
                int cell = (localY / 18) * WujinzangTerminalMenu.COLUMNS + (localX / 18);
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
        int gridLeft = leftPos + WujinzangTerminalMenu.GRID_X - 1;
        int gridRight = leftPos + WujinzangTerminalMenu.GRID_X
                + WujinzangTerminalMenu.COLUMNS * 18 + 1;
        int gridTop = topPos + WujinzangTerminalMenu.GRID_Y - 1;
        int gridBottom = topPos + WujinzangTerminalMenu.GRID_Y
                + WujinzangTerminalMenu.ROWS * 18 + 1;
        if (mouseX >= gridLeft && mouseX < gridRight && mouseY >= gridTop && mouseY < gridBottom) {
            sendNav(scrollY > 0 ? -WujinzangTerminalMenu.COLUMNS
                    : WujinzangTerminalMenu.COLUMNS, -1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
}
