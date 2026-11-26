package com.bitsson.gensokyou.client.screen;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.client.ClientQuests;
import com.bitsson.gensokyou.client.SakuraPalette;
import com.bitsson.gensokyou.client.widget.SakuraButton;
import com.bitsson.gensokyou.network.QuestTrackPayload;
import com.bitsson.gensokyou.quest.QuestDefinition;
import com.bitsson.gensokyou.quest.QuestSnapshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Fixed logical-pixel panel, paginated accepted tasks, and independently scrollable details. */
public final class QuestBookScreen extends Screen {
    private static final int W = 300, H = 200, DETAIL_X = 122, DETAIL_W = 164;
    private static final ResourceLocation BACKGROUND = Gensokyou.id("textures/gui/quest_book.png");
    private String selected = "";
    private int page;
    private int scroll;
    private List<QuestSnapshot.Entry> entries = List.of();
    private final List<Line> lines = new ArrayList<>();
    private record Line(FormattedCharSequence text, ItemStack icon) { int height() { return icon.isEmpty() ? 12 : 20; } }
    public QuestBookScreen() { super(Component.translatable("gui.gensokyou.quest.book_title")); }
    public void refresh() { if (font != null) rebuildWidgets(); }
    @Override protected void init() {
        entries = ClientQuests.snapshot().entries().stream().sorted(Comparator
                .comparing((QuestSnapshot.Entry e) -> e.definition().kind())
                .thenComparing(e -> e.definition().id().toString())).toList();
        if (entries.stream().noneMatch(e -> e.progress().instance().equals(selected))) selected = entries.isEmpty() ? "" : entries.getFirst().progress().instance();
        page = Math.clamp(page, 0, Math.max(0, (entries.size() - 1) / 3));
        int left = (width - W) / 2, top = (height - H) / 2;
        for (int n = page * 3; n < Math.min(page * 3 + 3, entries.size()); n++) {
            var entry = entries.get(n);
            int y = top + 42 + (n % 3) * 42;
            var label = Component.literal(entry.tracked() ? "◆ " : "").append(Component.translatable(entry.definition().title()));
            addRenderableWidget(SakuraButton.of(label, b -> {
                selected = entry.progress().instance(); scroll = 0; rebuildWidgets();
            }, left + 10, y, 102, 20));
        }
        var prev = addRenderableWidget(SakuraButton.of(Component.literal("◀"), b -> { page--; rebuildWidgets(); }, left + 10, top + 176, 22, 16));
        var next = addRenderableWidget(SakuraButton.of(Component.literal("▶"), b -> { page++; rebuildWidgets(); }, left + 90, top + 176, 22, 16));
        prev.active = page > 0; next.active = (page + 1) * 3 < entries.size();
        addRenderableWidget(SakuraButton.of(Component.literal("×"), b -> onClose(), left + W - 25, top + 5, 18, 16));
        var entry = selected();
        if (entry != null) addRenderableWidget(SakuraButton.of(Component.translatable(entry.tracked()
                        ? "gui.gensokyou.quest.untrack" : "gui.gensokyou.quest.track"),
                b -> PacketDistributor.sendToServer(new QuestTrackPayload(entry.tracked() ? "" : entry.progress().instance())),
                left + DETAIL_X, top + 176, DETAIL_W, 16));
        buildLines(entry);
        scroll = Math.clamp(scroll, 0, maxScroll());
    }
    private QuestSnapshot.Entry selected() { return entries.stream().filter(e -> e.progress().instance().equals(selected)).findFirst().orElse(null); }
    private void text(Component text) {
        for (var line : font.split(text, DETAIL_W - 6)) lines.add(new Line(line, ItemStack.EMPTY));
    }
    private void buildLines(QuestSnapshot.Entry entry) {
        lines.clear();
        if (entry == null) return;
        var def = entry.definition(); var q = entry.progress();
        text(Component.translatable(def.title()));
        text(Component.translatable(def.description()));
        text(Component.translatable(q.ready() ? "gui.gensokyou.quest.ready" : "gui.gensokyou.quest.active"));
        text(Component.translatable("gui.gensokyou.quest.stage", q.stage() + 1, def.stages().size()));
        var stage = def.stages().get(q.stage());
        text(Component.translatable(stage.title()));
        for (int n = 0; n < stage.objectives().size(); n++) {
            var goal = stage.objectives().get(n);
            text(Component.translatable("gui.gensokyou.quest.objective", Component.translatable(goal.description()), q.progress().get(n), goal.required()));
        }
        if (entry.tracked()) {
            ClientQuests.snapshot().navigation().ifPresentOrElse(destination -> {
                if (minecraft != null && minecraft.level != null && !minecraft.level.dimension().location().equals(destination.dimension()))
                    text(Component.translatable("gui.gensokyou.quest.other_dimension", dimension(destination.dimension())));
            }, () -> text(Component.translatable("gui.gensokyou.quest.no_navigation")));
        }
        if (!def.rewards().isEmpty()) {
            text(Component.translatable("gui.gensokyou.quest.rewards"));
            for (var reward : def.rewards()) {
                var stack = new ItemStack(BuiltInRegistries.ITEM.get(reward.item()));
                var wrapped = font.split(Component.empty().append(stack.getHoverName()).append(" × " + reward.count()), DETAIL_W - 26);
                for (int n = 0; n < wrapped.size(); n++) lines.add(new Line(wrapped.get(n), n == 0 ? stack : ItemStack.EMPTY));
            }
        }
    }
    private static Component dimension(ResourceLocation id) {
        if (id.equals(Gensokyou.id("gensokyo"))) return Component.translatable("gui.gensokyou.quest.dimension_gensokyo");
        if (id.equals(ResourceLocation.withDefaultNamespace("overworld"))) return Component.translatable("gui.gensokyou.quest.dimension_overworld");
        if (id.equals(ResourceLocation.withDefaultNamespace("the_nether"))) return Component.translatable("gui.gensokyou.quest.dimension_nether");
        if (id.equals(ResourceLocation.withDefaultNamespace("the_end"))) return Component.translatable("gui.gensokyou.quest.dimension_end");
        return Component.translatableWithFallback("dimension." + id.getNamespace() + "." + id.getPath().replace('/', '.'), id.toString());
    }
    private int maxScroll() { return Math.max(0, lines.stream().mapToInt(Line::height).sum() - 136); }
    @Override public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        int left = (width - W) / 2;
        if (mx >= left + DETAIL_X && mx <= left + W - 10) {
            scroll = Math.clamp(scroll - (int) (sy * 24), 0, maxScroll()); return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }
    @Override public void render(GuiGraphics g, int mx, int my, float partialTick) {
        renderBackground(g, mx, my, partialTick);
        int left = (width - W) / 2, top = (height - H) / 2;
        g.blit(BACKGROUND, left, top, 0, 0, W, H, W, H);
        g.drawString(font, title, left + 12, top + 10, SakuraPalette.ROSE, false);
        g.fill(left + 117, top + 30, left + 118, top + 166, SakuraPalette.PINK_SOFT);
        if (entries.isEmpty()) g.drawString(font, Component.translatable("gui.gensokyou.quest.empty"), left + 12, top + 40, SakuraPalette.INK, false);
        for (int n = page * 3; n < Math.min(page * 3 + 3, entries.size()); n++) {
            var entry = entries.get(n);
            int y = top + 30 + (n % 3) * 42;
            String kind = entry.definition().kind().name().toLowerCase(java.util.Locale.ROOT);
            g.drawString(font, Component.translatable("gui.gensokyou.quest.category." + kind), left + 12, y, SakuraPalette.PINK_DARK, false);
            if (entry.progress().instance().equals(selected)) g.fill(left + 7, y + 12, left + 9, y + 32, SakuraPalette.GOLD);
        }
        g.drawString(font, (page + 1) + "/" + Math.max(1, (entries.size() + 2) / 3), left + 48, top + 181, SakuraPalette.INK, false);
        g.enableScissor(left + DETAIL_X, top + 30, left + W - 10, top + 166);
        int y = top + 30 - scroll;
        ItemStack hovered = ItemStack.EMPTY;
        for (var line : lines) {
            if (y + line.height() > top + 30 && y < top + 166) {
                int x = left + DETAIL_X;
                if (!line.icon().isEmpty()) {
                    g.renderItem(line.icon(), x, y);
                    if (mx >= x && mx < x + 16 && my >= Math.max(y, top + 30) && my < Math.min(y + 16, top + 166)) hovered = line.icon();
                    x += 20;
                }
                g.drawString(font, line.text(), x, y + (line.icon().isEmpty() ? 0 : 4), SakuraPalette.INK, false);
            }
            y += line.height();
        }
        g.disableScissor();
        for (var widget : renderables) widget.render(g, mx, my, partialTick);
        if (!hovered.isEmpty()) g.renderTooltip(font, hovered, mx, my);
    }
    @Override public boolean isPauseScreen() { return false; }
}
