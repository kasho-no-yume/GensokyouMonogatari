package com.bitsson.gensokyou.jei;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.ritual.RitualPattern;
import com.mojang.blaze3d.platform.InputConstants;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.inputs.IJeiInputHandler;
import mezz.jei.api.gui.inputs.IJeiUserInput;
import mezz.jei.api.gui.widgets.IRecipeWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenPosition;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

public final class StructureViewWidget implements IRecipeWidget, IJeiInputHandler {

    public static final int VIEW_WIDTH = RitualCategory.VIEW_WIDTH;
    public static final int VIEW_HEIGHT = RitualCategory.VIEW_HEIGHT;
    private static final int ANCHOR_COLOR = 0xFFE0A030;
    private static final int GUIDE_LINE_COLOR = 0x30E0A030;
    private static final int GUIDE_CENTER_COLOR = 0x90E0A030;
    private static final int ARROW_COLOR = 0x80FFFFFF;
    private static final int PAGER_BG = 0xA0000000;
    private static final int PAGER_FG = 0xFFE8E8E8;
    private static final int ARROW_WIDTH = 10;
    private static final int PAGER_HEIGHT = 13;
    private static final int PAGER_MARGIN_BOTTOM = 2;
    private static final int PAGER_STRIP_TOTAL = PAGER_HEIGHT + PAGER_MARGIN_BOTTOM * 2;
    private static final int EDGE_ARROW_SIZE = 5;

    private final RitualGrid[] levels;
    private final IDrawable slotBg;
    private final Font font = Minecraft.getInstance().font;
    private final boolean paginated;
    private final int contentHeight;
    private int index;
    private double panX;
    private double panY;
    private boolean positioned;

    private record HitRect(int x, int y, int width, int height) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + width && my >= y && my < y + height;
        }
    }

    private HitRect stripRect;
    private HitRect prevRect;
    private HitRect nextRect;

    public StructureViewWidget(RitualPattern pattern, IDrawable slotBg) {
        this.slotBg = slotBg;
        RitualPattern.LevelSlice[] slices = pattern.levels().toArray(new RitualPattern.LevelSlice[0]);
        this.levels = new RitualGrid[slices.length];
        for (int i = 0; i < slices.length; i++) {
            this.levels[i] = RitualGrid.build(pattern, i);
        }
        this.paginated = slices.length > 1;
        this.contentHeight = paginated ? VIEW_HEIGHT - PAGER_STRIP_TOTAL : VIEW_HEIGHT;
    }

    @Override
    public ScreenPosition getPosition() {
        return new ScreenPosition(0, RitualCategory.TITLE_HEIGHT);
    }

    @Override
    public ScreenRectangle getArea() {
        return new ScreenRectangle(0, RitualCategory.TITLE_HEIGHT, VIEW_WIDTH, VIEW_HEIGHT);
    }

    private RitualGrid grid() {
        return levels[index];
    }

    @Override
    public boolean handleInput(double mouseX, double mouseY, IJeiUserInput input) {
        if (!paginated || !isLeftClick(input)) {
            return false;
        }
        computePagerRects();
        if (nextRect.contains(mouseX, mouseY)) {
            if (!input.isSimulate()) {
                index = (index + 1) % levels.length;
                centerOnAnchor();
            }
            return true;
        }
        if (prevRect.contains(mouseX, mouseY)) {
            if (!input.isSimulate()) {
                index = (index + levels.length - 1) % levels.length;
                centerOnAnchor();
            }
            return true;
        }
        return false;
    }

    private static boolean isLeftClick(IJeiUserInput input) {
        return input.getKey().getType() == InputConstants.Type.MOUSE
                && input.getKey().getValue() == GLFW.GLFW_MOUSE_BUTTON_LEFT;
    }

    @Override
    public boolean handleMouseDragged(double mouseX, double mouseY, InputConstants.Key mouseKey,
                                      double dragX, double dragY) {
        panX += dragX;
        panY += dragY;
        clamp();
        return true;
    }

    @Override
    public boolean handleMouseScrolled(double mouseX, double mouseY, double scrollDeltaX, double scrollDeltaY) {
        panX -= scrollDeltaX * RitualGrid.CELL;
        panY -= scrollDeltaY * RitualGrid.CELL;
        clamp();
        return true;
    }

    @Override
    public void drawWidget(GuiGraphics guiGraphics, double mouseX, double mouseY) {
        ensurePositioned();
        Matrix4f matrix = guiGraphics.pose().last().pose();
        int originX = Math.round(matrix.m30());
        int originY = Math.round(matrix.m31());

        guiGraphics.enableScissor(originX, originY,
                originX + VIEW_WIDTH, originY + contentHeight);
        drawGuides(guiGraphics);
        drawCells(guiGraphics);
        guiGraphics.disableScissor();

        drawEdgeArrows(guiGraphics);
        if (paginated) {
            drawPager(guiGraphics);
        }
    }

    private void drawGuides(GuiGraphics guiGraphics) {
        RitualGrid grid = grid();
        int eyeX = effectiveCenterX(grid);
        int eyeY = effectiveCenterY(grid);
        int cx = (int) Math.round(eyeX + panX);
        int cy = (int) Math.round(eyeY + panY);
        guiGraphics.fill(cx, -CELL_MARGIN, cx + 1, contentHeight + CELL_MARGIN, GUIDE_LINE_COLOR);
        guiGraphics.fill(-CELL_MARGIN, cy, VIEW_WIDTH + CELL_MARGIN, cy + 1, GUIDE_LINE_COLOR);
        guiGraphics.fill(cx - 3, cy, cx + 4, cy + 1, GUIDE_CENTER_COLOR);
        guiGraphics.fill(cx, cy - 3, cx + 1, cy + 4, GUIDE_CENTER_COLOR);
    }

    private int effectiveCenterX(RitualGrid grid) {
        return grid.anchorX() != RitualGrid.NO_ANCHOR
                ? grid.anchorX() + RitualGrid.CELL / 2
                : (grid.minX() + grid.maxX()) / 2;
    }

    private int effectiveCenterY(RitualGrid grid) {
        return grid.anchorY() != RitualGrid.NO_ANCHOR
                ? grid.anchorY() + RitualGrid.CELL / 2
                : (grid.minY() + grid.maxY()) / 2;
    }

    private static final int CELL_MARGIN = RitualGrid.CELL;

    private void drawCells(GuiGraphics guiGraphics) {
        RitualGrid grid = grid();
        for (RitualGrid.GridCell cell : grid.cells()) {
            int sx = (int) Math.round(cell.x() + panX);
            int sy = (int) Math.round(cell.y() + panY);
            if (sx > VIEW_WIDTH || sy > contentHeight
                    || sx + RitualGrid.CELL < 0 || sy + RitualGrid.CELL < 0) {
                continue;
            }
            slotBg.draw(guiGraphics, sx - 1, sy - 1);
            if (!cell.stack().isEmpty()) {
                guiGraphics.renderFakeItem(cell.stack(), sx, sy);
            }
            if (cell.anchor()) {
                guiGraphics.renderOutline(sx - 1, sy - 1, RitualGrid.CELL, RitualGrid.CELL, ANCHOR_COLOR);
            }
        }
    }

    private void drawEdgeArrows(GuiGraphics guiGraphics) {
        RitualGrid grid = grid();
        int midX = VIEW_WIDTH / 2;
        int midY = contentHeight / 2;
        if (grid.minX() + panX < 0) {
            drawArrow(guiGraphics, Direction.LEFT, EDGE_ARROW_SIZE, midY);
        }
        if (grid.maxX() + panX > VIEW_WIDTH) {
            drawArrow(guiGraphics, Direction.RIGHT, VIEW_WIDTH - EDGE_ARROW_SIZE, midY);
        }
        if (grid.minY() + panY < 0) {
            drawArrow(guiGraphics, Direction.UP, midX, EDGE_ARROW_SIZE);
        }
        if (grid.maxY() + panY > contentHeight) {
            drawArrow(guiGraphics, Direction.DOWN, midX, contentHeight - EDGE_ARROW_SIZE);
        }
    }

    private enum Direction {LEFT, RIGHT, UP, DOWN}

    private void drawArrow(GuiGraphics guiGraphics, Direction direction, int cx, int cy) {
        for (int i = 0; i < EDGE_ARROW_SIZE; i++) {
            int span = EDGE_ARROW_SIZE - i;
            switch (direction) {
                case LEFT -> fillClipped(guiGraphics, cx + i, cy - span, cx + i + 1, cy + span, ARROW_COLOR);
                case RIGHT -> fillClipped(guiGraphics, cx + i, cy - i - 1, cx + i + 1, cy + i + 1, ARROW_COLOR);
                case UP -> fillClipped(guiGraphics, cx - span, cy + i, cx + span, cy + i + 1, ARROW_COLOR);
                case DOWN -> fillClipped(guiGraphics, cx - i - 1, cy + i, cx + i + 1, cy + i + 1, ARROW_COLOR);
            }
        }
    }

    private void fillClipped(GuiGraphics guiGraphics, int x1, int y1, int x2, int y2, int color) {
        int cx1 = Math.max(0, x1);
        int cy1 = Math.max(0, y1);
        int cx2 = Math.min(VIEW_WIDTH, x2);
        int cy2 = Math.min(contentHeight, y2);
        if (cx1 < cx2 && cy1 < cy2) {
            guiGraphics.fill(cx1, cy1, cx2, cy2, color);
        }
    }

    private void drawPager(GuiGraphics guiGraphics) {
        computePagerRects();
        guiGraphics.fill(stripRect.x(), stripRect.y(),
                stripRect.x() + stripRect.width(), stripRect.y() + stripRect.height(), PAGER_BG);
        Component label = Component.translatable("jei." + Gensokyou.MODID + ".ritual.page",
                index + 1, levels.length);
        int textX = stripRect.x() + (stripRect.width() - font.width(label)) / 2;
        guiGraphics.drawString(font, label, textX, stripRect.y() + 3, PAGER_FG, false);
        guiGraphics.drawCenteredString(font, "<", prevRect.x() + ARROW_WIDTH / 2,
                stripRect.y() + 3, PAGER_FG);
        guiGraphics.drawCenteredString(font, ">", nextRect.x() + ARROW_WIDTH / 2,
                stripRect.y() + 3, PAGER_FG);
    }

    private void computePagerRects() {
        if (stripRect != null) {
            return;
        }
        String sample = "< " + Component.translatable("jei." + Gensokyou.MODID + ".ritual.page",
                levels.length, levels.length).getString() + " >";
        int width = font.width(sample) + 8;
        int x = (VIEW_WIDTH - width) / 2;
        int y = VIEW_HEIGHT - PAGER_HEIGHT - PAGER_MARGIN_BOTTOM;
        stripRect = new HitRect(x, y, width, PAGER_HEIGHT);
        prevRect = new HitRect(x, y, ARROW_WIDTH, PAGER_HEIGHT);
        nextRect = new HitRect(x + width - ARROW_WIDTH, y, ARROW_WIDTH, PAGER_HEIGHT);
    }

    @Override
    public void getTooltip(ITooltipBuilder tooltip, double mouseX, double mouseY) {
        ensurePositioned();
        if (mouseY < 0 || mouseX < 0 || mouseX >= VIEW_WIDTH || mouseY >= contentHeight) {
            return;
        }
        int gx = (int) Math.floor(mouseX - panX);
        int gy = (int) Math.floor(mouseY - panY);
        for (RitualGrid.GridCell cell : grid().cells()) {
            if (gx >= cell.x() && gx < cell.x() + RitualGrid.CELL
                    && gy >= cell.y() && gy < cell.y() + RitualGrid.CELL) {
                addCellTooltip(tooltip, cell);
                return;
            }
        }
    }

    private void addCellTooltip(ITooltipBuilder tooltip, RitualGrid.GridCell cell) {
        if (!cell.stack().isEmpty()) {
            tooltip.add(cell.stack().getHoverName());
        }
        if (cell.anchor()) {
            tooltip.add(Component.translatable("jei." + Gensokyou.MODID + ".ritual.anchor"));
        } else if (cell.missingTag() && cell.pred().tag() != null) {
            tooltip.add(Component.translatable("jei." + Gensokyou.MODID + ".ritual.tag_missing",
                    cell.pred().tag().location().toString()));
        }
    }

    private void ensurePositioned() {
        if (!positioned) {
            positioned = true;
            centerOnAnchor();
        }
    }

    private void centerOnAnchor() {
        RitualGrid grid = grid();
        panX = (double) VIEW_WIDTH / 2 - effectiveCenterX(grid);
        panY = (double) contentHeight / 2 - effectiveCenterY(grid);
        clamp();
    }

    private void clamp() {
        RitualGrid grid = grid();
        panX = clampAxis(panX, grid.minX(), grid.maxX(), VIEW_WIDTH);
        panY = clampAxis(panY, grid.minY(), grid.maxY(), contentHeight);
    }

    private static double clampAxis(double pan, int contentMin, int contentMax, int viewSize) {
        double lower = (double) viewSize - contentMax;
        double upper = -contentMin;
        if (lower > upper) {
            return (lower + upper) / 2;
        }
        return Math.min(upper, Math.max(lower, pan));
    }
}
