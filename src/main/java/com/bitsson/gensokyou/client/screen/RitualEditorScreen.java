package com.bitsson.gensokyou.client.screen;

import com.bitsson.gensokyou.item.BuilderSelection;
import com.bitsson.gensokyou.menu.RitualEditorMenu;
import com.bitsson.gensokyou.network.EditorCommandPayload;
import com.bitsson.gensokyou.registry.ModDataComponents;
import com.bitsson.gensokyou.ritual.RitualPattern;
import com.bitsson.gensokyou.ritual.RitualPatternLoader;
import com.bitsson.gensokyou.ritual.editor.EditorState;
import com.bitsson.gensokyou.ritual.editor.RitualEditorPlacement;
import com.bitsson.gensokyou.ritual.editor.Workspace;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * 仪式编辑杖界面（纯自绘）：左列图案列表，右上阶级按钮（图案 levels 驱动），
 * 右中工作区 4 轴 ± 控件（针对当前阶级），右下三动作按钮（捕获存阶 / 仪式保存 / 清锚）。
 * 一切点击经 {@link EditorCommandPayload} 回服务端权威执行；本帧显示读乐观态 + 手上组件回同步。
 */
public class RitualEditorScreen extends AbstractContainerScreen<RitualEditorMenu> {

    private static final int W = 268;
    private static final int H = 208;
    private static final int LIST_X = 8;
    private static final int LIST_Y = 24;
    private static final int LIST_W = 110;
    private static final int ROW_H = 16;
    private static final int LIST_ROWS = 8;
    private static final int RIGHT_X = 126;
    private static final int INK = 0xFF404040;
    private static final int LABEL = 0xFF808080;

    private int scroll;
    /** 乐观选择态（零槽菜单不同步手持 stack，点击即更新，服务端为权威）。 */
    private BuilderSelection selection;
    private EditorState session = EditorState.EMPTY;

    public RitualEditorScreen(RitualEditorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = W;
        this.imageHeight = H;
        this.inventoryLabelY = H + 100;
    }

    @Override
    protected void init() {
        super.init();
        this.session = readComponent();
        this.selection = session.selection();
    }

    private EditorState readComponent() {
        if (minecraft == null || minecraft.player == null) {
            return EditorState.EMPTY;
        }
        EditorState st = minecraft.player.getItemInHand(menu.hand())
                .get(ModDataComponents.RITUAL_EDITOR_STATE.get());
        return st == null ? EditorState.EMPTY : st;
    }

    private List<RitualPattern> patterns() {
        return RitualPatternLoader.all();
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        g.fill(leftPos, topPos, leftPos + W, topPos + H, 0xFFF0F0F0);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, 8, 6, INK, false);
        // 锚定状态行
        String anchorText = session.anchored()
                ? "⚓ " + session.anchor().toShortString()
                : "⚠ no anchor (right-click a core)";
        g.drawString(font, Component.literal(anchorText), RIGHT_X, 6,
                session.anchored() ? 0xFF2E8B57 : 0xFFB22222, false);

        // 左列图案列表
        g.drawString(font, Component.translatable("gui.gensokyou.editor.patterns"), LIST_X, LIST_Y - 11, INK, false);
        List<RitualPattern> list = patterns();
        int maxScroll = Math.max(0, list.size() - LIST_ROWS);
        scroll = Math.max(0, Math.min(scroll, maxScroll));
        for (int i = 0; i < LIST_ROWS && i + scroll < list.size(); i++) {
            RitualPattern pattern = list.get(i + scroll);
            int y = LIST_Y + i * ROW_H;
            boolean selected = selection != null && selection.patternId().equals(pattern.id());
            boolean hover = inRect(mouseX, mouseY, LIST_X - 2, y - 1, LIST_W + 2, ROW_H);
            if (selected) {
                g.fill(LIST_X - 2, y - 1, LIST_X + LIST_W, y + ROW_H - 1, 0xFFB0C4DE);
            } else if (hover) {
                g.fill(LIST_X - 2, y - 1, LIST_X + LIST_W, y + ROW_H - 1, 0xFFD8D8D8);
            }
            g.drawString(font, patternName(pattern), LIST_X + 2, y + 4, INK, false);
        }

        if (selection == null) {
            g.drawString(font, Component.translatable("gui.gensokyou.builder.none"),
                    RIGHT_X, 40, 0xFFB22222, false);
            return;
        }
        RitualPattern pattern = RitualPatternLoader.byId(selection.patternId()).orElse(null);
        if (pattern == null) {
            g.drawString(font, Component.translatable("msg.gensokyou.editor_pattern_gone"),
                    RIGHT_X, 40, 0xFFB22222, false);
            return;
        }

        // 阶级按钮（levels 驱动）
        g.drawString(font, Component.translatable("gui.gensokyou.editor.level"), RIGHT_X, 24, INK, false);
        List<Integer> levels = pattern.levels().stream().map(RitualPattern.LevelSlice::level).toList();
        for (int idx = 0; idx < levels.size(); idx++) {
            int lv = levels.get(idx);
            int x = RIGHT_X + idx * 20;
            boolean active = lv == selection.tier();
            g.fill(x, 34, x + 18, 34 + 16, active ? 0xFF333333 : 0xFFAAAAAA);
            g.drawString(font, Component.literal(String.valueOf(lv)), x + 6, 38, 0xFFFFFFFF, true);
        }

        // 工作区 4 轴 ± 控件（当前阶级）
        int level = selection.tier();
        Workspace ws = session.workspaces().get(level);
        if (ws == null) {
            ws = RitualEditorPlacement.defaultWorkspace(pattern, level);
        }
        renderAxis(g, 60, "X", ws.sizeX(), level);
        renderAxis(g, 74, "Z", ws.sizeZ(), level);
        renderAxis(g, 88, "H", ws.height(), level);
        renderAxis(g, 102, "Y", ws.yOffset(), level);

        // 动作按钮
        renderButton(g, 122, "capture", Component.translatable("gui.gensokyou.editor.capture"), true);
        renderButton(g, 138, "save", Component.translatable("gui.gensokyou.editor.save"), true);
        renderButton(g, 154, "clear", Component.translatable("gui.gensokyou.editor.clear_anchor"), session.anchored());

        // 力建提示（右键核心）
        g.drawString(font, Component.translatable("gui.gensokyou.editor.build_hint")
                        .withStyle(ChatFormatting.GRAY),
                LIST_X, H - 12, LABEL, false);
    }

    private void renderAxis(GuiGraphics g, int y, String label, int value, int level) {
        int x = RIGHT_X;
        g.drawString(font, Component.literal(label), x, y, INK, false);
        int minusX = x + 14;
        int plusX = x + 44;
        g.fill(minusX, y, minusX + 12, y + 10, 0xFFAAAAAA);
        g.drawString(font, Component.literal("−"), minusX + 4, y, INK, false);
        g.fill(plusX, y, plusX + 12, y + 10, 0xFFAAAAAA);
        g.drawString(font, Component.literal("+"), plusX + 4, y, INK, false);
        g.drawString(font, Component.literal(String.valueOf(value)), x + 62, y, INK, false);
    }

    private void renderButton(GuiGraphics g, int y, String id, Component label, boolean enabled) {
        int x = RIGHT_X + 60;
        g.fill(x, y, x + 74, y + 14, enabled ? 0xFF9ACD32 : 0xFFBBBBBB);
        g.drawString(font, label, x + 6, y + 3, enabled ? 0xFF1F2F1F : LABEL, false);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        // 图案行
        List<RitualPattern> list = patterns();
        for (int i = 0; i < LIST_ROWS && i + scroll < list.size(); i++) {
            int y = LIST_Y + i * ROW_H;
            if (inRect(mx, my, LIST_X - 2, y - 1, LIST_W + 2, ROW_H)) {
                RitualPattern pattern = list.get(i + scroll);
                int tier = pattern.levels().isEmpty() ? 0
                        : pattern.levels().get(0).level();
                this.selection = new BuilderSelection(pattern.id(), tier);
                this.session = session.withSelection(selection);
                PacketDistributor.sendToServer(EditorCommandPayload.select(pattern.id(), tier));
                return true;
            }
        }
        if (selection != null) {
            // 阶级按钮
            RitualPattern pattern = RitualPatternLoader.byId(selection.patternId()).orElse(null);
            if (pattern != null) {
                List<Integer> levels = pattern.levels().stream()
                        .map(RitualPattern.LevelSlice::level).toList();
                for (int idx = 0; idx < levels.size(); idx++) {
                    int x = RIGHT_X + idx * 20;
                    if (inRect(mx, my, x, 34, 18, 16)) {
                        this.selection = new BuilderSelection(selection.patternId(), levels.get(idx));
                        this.session = session.withSelection(selection);
                        PacketDistributor.sendToServer(
                                EditorCommandPayload.select(selection.patternId(), levels.get(idx)));
                        return true;
                    }
                }
                // 工作区 ± 按钮
                int level = selection.tier();
                Workspace ws = session.workspaces().get(level);
                if (ws == null) {
                    ws = RitualEditorPlacement.defaultWorkspace(pattern, level);
                }
                ws = handleAxisClick(mx, my, ws, 60, 0);
                ws = handleAxisClick(mx, my, ws, 74, 1);
                ws = handleAxisClick(mx, my, ws, 88, 2);
                ws = handleAxisClick(mx, my, ws, 102, 3);
                if (ws != null) {
                    this.session = session.withWorkspace(level, ws);
                    PacketDistributor.sendToServer(EditorCommandPayload.setWorkspace(level, ws));
                    return true;
                }
                // 动作按钮
                int bx = RIGHT_X + 60;
                if (inRect(mx, my, bx, 122, 74, 14)) {
                    PacketDistributor.sendToServer(EditorCommandPayload.capture());
                    return true;
                }
                if (inRect(mx, my, bx, 138, 74, 14)) {
                    PacketDistributor.sendToServer(EditorCommandPayload.save());
                    return true;
                }
                if (inRect(mx, my, bx, 154, 74, 14) && session.anchored()) {
                    this.session = session.clearedAnchor();
                    this.selection = null;
                    PacketDistributor.sendToServer(
                            new EditorCommandPayload(EditorCommandPayload.ACTION_CLEAR_ANCHOR, null, 0, null));
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    /** 命中某轴 ± 则返回调整后工作区，否则 null。axis: 0=X 1=Z 2=H 3=Y。 */
    private @javax.annotation.Nullable Workspace handleAxisClick(double mx, double my, Workspace ws,
                                                                 int y, int axis) {
        int x = RIGHT_X;
        int minusX = x + 14;
        int plusX = x + 44;
        boolean minus = inRect(mx, my, minusX, y, 12, 10);
        boolean plus = inRect(mx, my, plusX, y, 12, 10);
        if (!minus && !plus) {
            return null;
        }
        int d = plus ? 2 : -2;
        int dy = plus ? 1 : -1;
        return switch (axis) {
            case 0 -> Workspace.of(ws.sizeX() + d, ws.sizeZ(), ws.height(), ws.yOffset());
            case 1 -> Workspace.of(ws.sizeX(), ws.sizeZ() + d, ws.height(), ws.yOffset());
            case 2 -> Workspace.of(ws.sizeX(), ws.sizeZ(), ws.height() + d, ws.yOffset());
            default -> Workspace.of(ws.sizeX(), ws.sizeZ(), ws.height(), ws.yOffset() + dy);
        };
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (inRect(mx, my, LIST_X, LIST_Y, LIST_W, LIST_ROWS * ROW_H)) {
            scroll += sy > 0 ? -1 : 1;
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    private boolean inRect(double mx, double my, int x, int y, int w, int h) {
        return mx >= leftPos + x && mx < leftPos + x + w && my >= topPos + y && my < topPos + y + h;
    }

    private static Component patternName(RitualPattern pattern) {
        return Component.translatableWithFallback(
                "jei." + pattern.id().getNamespace() + ".ritual." + pattern.id().getPath(),
                pattern.id().getPath().replace('_', ' '));
    }
}
