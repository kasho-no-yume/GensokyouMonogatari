package com.bitsson.gensokyou.client.widget;

import com.bitsson.gensokyou.client.SakuraPalette;
import com.bitsson.gensokyou.quest.QuestDefinition;
import com.bitsson.gensokyou.quest.QuestSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/** Category branches with quest leaves; native list scrolling keeps rendering and hit testing clipped together. */
public final class QuestTreeList extends ObjectSelectionList<QuestTreeList.Row> {
    private static final int ROW_HEIGHT = 20;
    private final EnumSet<QuestDefinition.Kind> collapsed;
    private final Consumer<QuestSnapshot.Entry> onSelect;
    private List<QuestSnapshot.Entry> quests = List.of();
    private String selectedInstance = "";

    public QuestTreeList(Minecraft minecraft, int x, int y, int width, int height,
                         EnumSet<QuestDefinition.Kind> collapsed, Consumer<QuestSnapshot.Entry> onSelect) {
        super(minecraft, width, height, y, ROW_HEIGHT);
        setX(x);
        centerListVertically = false;
        this.collapsed = collapsed;
        this.onSelect = onSelect;
    }

    public void setQuests(List<QuestSnapshot.Entry> quests, String selectedInstance) {
        this.quests = List.copyOf(quests);
        this.selectedInstance = selectedInstance;
        rebuildRows();
    }

    private void rebuildRows() {
        double previousScroll = getScrollAmount();
        Row previousFocus = getFocused();
        setFocused(null);
        clearEntries();
        Row focus = null;
        for (var kind : QuestDefinition.Kind.values()) {
            var group = quests.stream().filter(q -> q.definition().kind() == kind).toList();
            if (group.isEmpty()) continue;
            Row header = new Row(kind, null, group.size(), false);
            addEntry(header);
            if (previousFocus != null && previousFocus.kind == kind) focus = header;
            if (collapsed.contains(kind)) continue;
            for (int n = 0; n < group.size(); n++) {
                Row row = new Row(kind, group.get(n), 0, n == group.size() - 1);
                addEntry(row);
                if (previousFocus != null && previousFocus.quest != null
                        && previousFocus.quest.progress().instance().equals(row.quest.progress().instance())) focus = row;
                if (row.quest.progress().instance().equals(selectedInstance)) setSelected(row);
            }
        }
        if (focus != null) setFocused(focus);
        setScrollAmount(previousScroll);
    }

    private void activate(Row row) {
        if (row.quest == null) {
            if (!collapsed.remove(row.kind)) collapsed.add(row.kind);
            rebuildRows();
        } else {
            selectedInstance = row.quest.progress().instance();
            setSelected(row);
            onSelect.accept(row.quest);
        }
    }

    @Override public int getRowWidth() { return getWidth() - 18; }
    @Override protected int getScrollbarPosition() { return getX() + getWidth() - 6; }
    @Override protected void renderListBackground(GuiGraphics graphics) {}
    @Override protected void renderListSeparators(GuiGraphics graphics) {}

    @Override protected void renderSelection(GuiGraphics graphics, int y, int width, int height, int border, int fill) {
        // Quest selection is drawn by the row; keyboard focus and the active quest are separate states.
    }

    @Override protected void renderDecorations(GuiGraphics graphics, int mouseX, int mouseY) {
        if (scrollbarVisible()) {
            int x = getScrollbarPosition();
            int height = getHeight();
            int thumbHeight = Math.clamp((int) ((float) height * height / getMaxPosition()), 32, height - 8);
            int thumbY = getY() + (int) getScrollAmount() * (height - thumbHeight) / getMaxScroll();
            graphics.fill(x, getY(), x + 6, getY() + height, SakuraPalette.SCROLL_TRACK);
            graphics.fill(x + 1, thumbY, x + 5, thumbY + thumbHeight, SakuraPalette.SCROLL_THUMB);
        }
        if (isMouseOver(mouseX, mouseY) && mouseX < getScrollbarPosition()) {
            Row hovered = getHovered();
            if (hovered != null && hovered.quest != null
                    && minecraft.font.width(hovered.label()) > textWidth(hovered))
                graphics.renderTooltip(minecraft.font, hovered.label(), mouseX, mouseY);
        }
    }

    private int textWidth(Row row) { return Math.max(1, getRowWidth() - (row.quest == null ? 14 : 18)); }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        Row hit = isMouseOver(mouseX, mouseY) && mouseX < getScrollbarPosition()
                ? getEntryAtPosition(mouseX, mouseY) : null;
        boolean handled = super.mouseClicked(mouseX, mouseY, button);
        // Rebuild only after the native handler has assigned row focus and dragging state.
        if (handled && button == GLFW.GLFW_MOUSE_BUTTON_LEFT && hit != null) activate(hit);
        return handled;
    }

    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        Row row = getFocused();
        if (row == null) row = getSelected();
        if (row != null) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER || keyCode == GLFW.GLFW_KEY_SPACE) {
                activate(row);
                return true;
            }
            if (row.quest == null && (keyCode == GLFW.GLFW_KEY_LEFT && !collapsed.contains(row.kind)
                    || keyCode == GLFW.GLFW_KEY_RIGHT && collapsed.contains(row.kind))) {
                activate(row);
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    public final class Row extends ObjectSelectionList.Entry<Row> {
        private final QuestDefinition.Kind kind;
        private final QuestSnapshot.Entry quest;
        private final int count;
        private final boolean lastChild;

        private Row(QuestDefinition.Kind kind, QuestSnapshot.Entry quest, int count, boolean lastChild) {
            this.kind = kind;
            this.quest = quest;
            this.count = count;
            this.lastChild = lastChild;
        }

        private Component label() {
            if (quest == null) return Component.translatable("gui.gensokyou.quest.category." + kind.name().toLowerCase(Locale.ROOT))
                    .append(" (" + count + ")");
            return Component.literal(quest.tracked() ? "◆ " : "").append(Component.translatable(quest.definition().title()));
        }

        @Override public Component getNarration() {
            if (quest == null) return Component.translatable(collapsed.contains(kind)
                    ? "gui.gensokyou.quest.expand_group" : "gui.gensokyou.quest.collapse_group", label());
            return label();
        }

        @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
            return button == GLFW.GLFW_MOUSE_BUTTON_LEFT;
        }

        @Override public void render(GuiGraphics graphics, int index, int y, int x, int width, int height,
                                     int mouseX, int mouseY, boolean hovered, float partialTick) {
            boolean selected = quest != null && quest.progress().instance().equals(selectedInstance);
            if (selected || hovered || QuestTreeList.this.isFocused() && getFocused() == this)
                graphics.fill(x - 2, y, x + width, y + height, selected ? SakuraPalette.RECESS : SakuraPalette.CARD);
            int textX = x + 12;
            if (quest == null) {
                if (collapsed.contains(kind)) {
                    for (int n = 0; n < 4; n++) graphics.fill(x + 1 + n, y + 4 + n, x + 2 + n, y + 11 - n, SakuraPalette.ROSE);
                } else {
                    for (int n = 0; n < 4; n++) graphics.fill(x + n, y + 5 + n, x + 7 - n, y + 6 + n, SakuraPalette.ROSE);
                }
            } else {
                int branchX = x + 3;
                graphics.fill(branchX, y - 4, branchX + 1, lastChild ? y + 8 : y + ROW_HEIGHT, SakuraPalette.PINK_SOFT);
                graphics.fill(branchX, y + 7, x + 10, y + 8, SakuraPalette.PINK_SOFT);
                if (selected) graphics.fill(x - 2, y + 2, x, y + height - 2, SakuraPalette.GOLD);
                textX += 4;
            }
            var text = minecraft.font.substrByWidth(label(), textWidth(this));
            graphics.drawString(minecraft.font, Language.getInstance().getVisualOrder(text), textX, y + 4,
                    quest == null ? SakuraPalette.ROSE : SakuraPalette.INK, false);
        }
    }
}
