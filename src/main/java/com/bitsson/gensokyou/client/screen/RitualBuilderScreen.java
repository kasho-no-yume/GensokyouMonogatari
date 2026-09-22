package com.bitsson.gensokyou.client.screen;

import com.bitsson.gensokyou.client.MaterialIcons;
import com.bitsson.gensokyou.item.BuilderSelection;
import com.bitsson.gensokyou.menu.RitualBuilderMenu;
import com.bitsson.gensokyou.network.RitualSelectPayload;
import com.bitsson.gensokyou.registry.ModDataComponents;
import com.bitsson.gensokyou.registry.TierPalette;
import com.bitsson.gensokyou.ritual.RitualBuilderPlacement;
import com.bitsson.gensokyou.ritual.RitualPattern;
import com.bitsson.gensokyou.ritual.RitualPatternLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * 仪式构建器选择界面（纯自绘，无背景贴图）：左列图案滚动列表，右侧品阶按钮 + 材料需求对比。
 * 图案数据读客户端 {@code RitualPatternLoader.all()}，持有数读本地背包；
 * 点击经 {@link RitualSelectPayload} 回写服务端组件，下一帧从手上物品组件读回高亮。
 */
public class RitualBuilderScreen extends AbstractContainerScreen<RitualBuilderMenu> {

    private static final int PANEL_WIDTH = 264;
    private static final int PANEL_HEIGHT = 196;
    private static final int LIST_X = 8;
    private static final int LIST_Y = 24;
    private static final int LIST_W = 116;
    private static final int ROW_H = 20;
    private static final int LIST_ROWS = 7;
    private static final int RIGHT_X = 132;
    private static final int TIER_Y = 26;
    private static final int TIER_SIZE = 18;
    private static final int MAT_Y = 60;
    /** 材料视口：MAT_Y .. 面板底-4；可视行数 = 视口高/18 向下取整。 */
    private static final int MAT_VIEW_H = PANEL_HEIGHT - 4 - MAT_Y;
    private static final int MAT_ROWS = MAT_VIEW_H / 18;
    private static final int MAT_BAR_X = PANEL_WIDTH - 10;
    private static final int COLOR_TEXT = 0xFF404040;
    private static final int COLOR_OK = 0xFF2E8B57;
    private static final int COLOR_BAD = 0xFFB22222;

    private int scroll;
    /** 右列材料需求区滚动偏移（与左列 scroll 同款机制）。 */
    private int matScroll;
    /**
     * 客户端乐观选择态：打开时从物品组件初始化，点击时立即更新并照常发 C2S。
     * 零槽菜单不同步手持 stack，故不能每帧回读组件（否则点了不刷新）；
     * 服务端仍是权威，关菜单后组件回同步，二者因点击目标恒为合法图案而必然收敛。
     */
    private BuilderSelection current;
    /** 本帧被悬浮的材料方块名（renderLabels 写入，render 于最上层绘制 tooltip）。 */
    private Component hoveredMaterial;

    public RitualBuilderScreen(RitualBuilderMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = PANEL_WIDTH;
        this.imageHeight = PANEL_HEIGHT;
        this.inventoryLabelY = PANEL_HEIGHT + 100; // 隐藏"物品栏"标签（零槽菜单无物品栏区）
    }

    @Override
    protected void init() {
        super.init();
        this.current = readComponent();
    }

    private List<RitualPattern> patterns() {
        int maxTier = menu.maxTier();
        return RitualPatternLoader.all().stream()
                .filter(p -> p.tiers().stream().anyMatch(t -> t <= maxTier))
                .toList();
    }

    /** 图案在当前进度上限内可选的品阶（保持图案声明顺序）。 */
    private List<Integer> visibleTiers(RitualPattern pattern) {
        int maxTier = menu.maxTier();
        return pattern.tiers().stream().filter(t -> t <= maxTier).toList();
    }

    /**
     * 渲染/命中用的有效选择：图案被进度隐藏（无可见品阶）→ 视为未选；
     * 品阶高于进度上限或不在图案声明内 → 夹取到可见集最高项。
     */
    private BuilderSelection effectiveSelection() {
        BuilderSelection sel = this.current;
        if (sel == null) {
            return null;
        }
        RitualPattern pattern = RitualPatternLoader.byId(sel.patternId()).orElse(null);
        if (pattern == null) {
            return null;
        }
        List<Integer> tiers = visibleTiers(pattern);
        if (tiers.isEmpty()) {
            return null;
        }
        if (tiers.contains(sel.tier())) {
            return sel;
        }
        int highest = tiers.stream().mapToInt(Integer::intValue).max().orElse(tiers.get(0));
        return new BuilderSelection(sel.patternId(), highest);
    }

    private BuilderSelection readComponent() {
        if (this.minecraft == null || this.minecraft.player == null) {
            return null;
        }
        return this.minecraft.player.getItemInHand(menu.hand())
                .get(ModDataComponents.RITUAL_BUILDER_SELECTION.get());
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + PANEL_WIDTH, topPos + PANEL_HEIGHT, 0xFFF0F0F0);
        graphics.fill(leftPos, topPos, leftPos + PANEL_WIDTH, topPos + 1, 0xFFC6C6C6);
        graphics.fill(leftPos, topPos + PANEL_HEIGHT - 1, leftPos + PANEL_WIDTH, topPos + PANEL_HEIGHT, 0xFF555555);
        graphics.fill(leftPos, topPos, leftPos + 1, topPos + PANEL_HEIGHT, 0xFFC6C6C6);
        graphics.fill(leftPos + PANEL_WIDTH - 1, topPos, leftPos + PANEL_WIDTH, topPos + PANEL_HEIGHT, 0xFF555555);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(this.font, this.title, 8, 6, COLOR_TEXT, false);
        this.hoveredMaterial = null;
        BuilderSelection sel = effectiveSelection();
        int currentTier = sel == null ? 0 : sel.tier();
        var currentId = sel == null ? null : sel.patternId();

        // 左列：图案滚动列表
        graphics.drawString(this.font, Component.translatable("gui.gensokyou.builder.patterns"),
                LIST_X, LIST_Y - 11, COLOR_TEXT, false);
        List<RitualPattern> list = patterns();
        int maxScroll = Math.max(0, list.size() - LIST_ROWS);
        this.scroll = Math.max(0, Math.min(this.scroll, maxScroll));
        for (int i = 0; i < LIST_ROWS && i + scroll < list.size(); i++) {
            RitualPattern pattern = list.get(i + scroll);
            int y = LIST_Y + i * ROW_H;
            boolean selected = currentId != null && currentId.equals(pattern.id());
            boolean hover = mouseX >= leftPos + LIST_X && mouseX < leftPos + LIST_X + LIST_W
                    && mouseY >= topPos + y && mouseY < topPos + y + ROW_H;
            if (selected) {
                graphics.fill(LIST_X - 2, y - 2, LIST_X + LIST_W, y + ROW_H - 2, 0xFFB0C4DE);
            } else if (hover) {
                graphics.fill(LIST_X - 2, y - 2, LIST_X + LIST_W, y + ROW_H - 2, 0xFFD8D8D8);
            }
            ItemStack icon = patternIcon(pattern);
            if (!icon.isEmpty()) {
                graphics.renderItem(icon, LIST_X, y);
            }
            graphics.drawString(this.font, patternName(pattern), LIST_X + 20, y + 6, COLOR_TEXT, false);
        }
        renderScrollbar(graphics, LIST_X + LIST_W + 2, LIST_Y - 2, LIST_ROWS * ROW_H,
                list.size(), LIST_ROWS, this.scroll);

        // 右侧：品阶按钮 + 材料需求（均针对当前选中图案）
        RitualPattern selected = currentId == null ? null
                : RitualPatternLoader.byId(currentId).orElse(null);
        if (selected == null) {
            graphics.drawString(this.font, Component.translatable("gui.gensokyou.builder.none"),
                    RIGHT_X, MAT_Y, COLOR_BAD, false);
            return;
        }
        // 品阶按钮：仅当图案含标签格位时显示，按图案声明的 tiers 渲染（不写死 0-5）
        if (selected.hasTieredSlots()) {
            graphics.drawString(this.font, Component.translatable("gui.gensokyou.builder.tier"),
                    RIGHT_X, TIER_Y - 11, COLOR_TEXT, false);
            List<Integer> tiers = visibleTiers(selected);
            for (int idx = 0; idx < tiers.size(); idx++) {
                int t = tiers.get(idx);
                int x = RIGHT_X + idx * (TIER_SIZE + 2);
                boolean active = t == currentTier;
                graphics.fill(x, TIER_Y, x + TIER_SIZE, TIER_Y + TIER_SIZE,
                        active ? 0xFF333333 : 0xFFAAAAAA);
                graphics.fill(x + 1, TIER_Y + 1, x + TIER_SIZE - 1, TIER_Y + TIER_SIZE - 1,
                        TierPalette.rgb(t) | 0xFF000000);
                graphics.drawString(this.font, Component.literal(String.valueOf(t)),
                        x + 6, TIER_Y + 6, 0xFFFFFFFF, true);
            }
        }
        // 材料需求对比（溢出滚动：可视行数 = MAT_ROWS，渲染/悬浮共用"可视下标 + matScroll"映射）
        graphics.drawString(this.font, Component.translatable("gui.gensokyou.builder.materials"),
                RIGHT_X, MAT_Y - 11, COLOR_TEXT, false);
        List<RitualBuilderPlacement.Requirement> reqs =
                RitualBuilderPlacement.requirements(selected, currentTier);
        int matMaxScroll = Math.max(0, reqs.size() - MAT_ROWS);
        this.matScroll = Math.max(0, Math.min(this.matScroll, matMaxScroll));
        for (int i = 0; i < MAT_ROWS && i + matScroll < reqs.size(); i++) {
            RitualBuilderPlacement.Requirement req = reqs.get(i + matScroll);
            boolean itemLess = req.itemLess();
            int have = itemLess ? 0
                    : this.minecraft.player.getInventory().countItem(req.block().asItem());
            boolean enough = itemLess || have >= req.count();
            int y = MAT_Y + i * 18;
            MaterialIcons.render(graphics, req.block(), RIGHT_X, y);
            graphics.drawString(this.font,
                    itemLess
                            ? Component.translatable("gui.gensokyou.builder.material_itemless",
                                    req.count())
                            : Component.translatable("gui.gensokyou.builder.material_count",
                                    req.count(), have),
                    RIGHT_X + 20, y + 6, enough ? COLOR_OK : COLOR_BAD, false);
            if (mouseX >= leftPos + RIGHT_X && mouseX < leftPos + RIGHT_X + 16
                    && mouseY >= topPos + y && mouseY < topPos + y + 16) {
                this.hoveredMaterial = req.block().getName();
            }
        }
        renderScrollbar(graphics, MAT_BAR_X, MAT_Y, MAT_ROWS * 18, reqs.size(), MAT_ROWS, matScroll);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        if (this.hoveredMaterial != null) {
            graphics.renderComponentTooltip(this.font, List.of(this.hoveredMaterial), mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        BuilderSelection sel = effectiveSelection();
        int currentTier = sel == null ? 0 : sel.tier();
        var currentId = sel == null ? null : sel.patternId();

        // 图案行
        List<RitualPattern> list = patterns();
        for (int i = 0; i < LIST_ROWS && i + scroll < list.size(); i++) {
            int y = LIST_Y + i * ROW_H;
            if (mouseX >= leftPos + LIST_X - 2 && mouseX < leftPos + LIST_X + LIST_W
                    && mouseY >= topPos + y - 2 && mouseY < topPos + y + ROW_H - 2) {
                RitualPattern pattern = list.get(i + scroll);
                List<Integer> tiers = visibleTiers(pattern);
                int tier = tiers.contains(currentTier) ? currentTier : tiers.get(0);
                this.current = new BuilderSelection(pattern.id(), tier);
                PacketDistributor.sendToServer(new RitualSelectPayload(pattern.id(), tier));
                return true;
            }
        }
        // 品阶按钮（按当前图案在进度上限内可选品阶命中）
        if (currentId != null) {
            RitualPattern selected = RitualPatternLoader.byId(currentId).orElse(null);
            if (selected != null && selected.hasTieredSlots()) {
                List<Integer> tiers = visibleTiers(selected);
                for (int idx = 0; idx < tiers.size(); idx++) {
                    int x = RIGHT_X + idx * (TIER_SIZE + 2);
                    if (mouseX >= leftPos + x && mouseX < leftPos + x + TIER_SIZE
                            && mouseY >= topPos + TIER_Y && mouseY < topPos + TIER_Y + TIER_SIZE) {
                        int t = tiers.get(idx);
                        this.current = new BuilderSelection(currentId, t);
                        PacketDistributor.sendToServer(new RitualSelectPayload(currentId, t));
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        // 左列图案列表区
        if (mouseX >= leftPos + LIST_X && mouseX <= leftPos + LIST_X + LIST_W
                && mouseY >= topPos + LIST_Y && mouseY <= topPos + LIST_Y + LIST_ROWS * ROW_H) {
            this.scroll += scrollY > 0 ? -1 : 1;
            return true;
        }
        // 右列材料需求区（互不串扰：区域按 x 分流）
        if (mouseX >= leftPos + RIGHT_X
                && mouseY >= topPos + MAT_Y && mouseY <= topPos + MAT_Y + MAT_VIEW_H) {
            this.matScroll += scrollY > 0 ? -1 : 1;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    /** 列表溢出时绘制右侧滚动条轨道+滑块，提示可滚动（x/top/h 相对面板左上角）。 */
    private void renderScrollbar(GuiGraphics graphics, int x, int top, int h, int total, int visible, int scroll) {
        int maxScroll = Math.max(0, total - visible);
        if (maxScroll <= 0) {
            return;
        }
        graphics.fill(x, top, x + 4, top + h, 0xFFC0C0C0);
        int thumbH = Math.max(12, h * visible / total);
        int thumbY = top + (int) ((h - thumbH) * (scroll / (float) maxScroll));
        graphics.fill(x, thumbY, x + 4, thumbY + thumbH, 0xFF707070);
    }

    private static Component patternName(RitualPattern pattern) {
        return Component.translatableWithFallback(
                "jei." + pattern.id().getNamespace() + ".ritual." + pattern.id().getPath(),
                pattern.id().getPath().replace('_', ' '));
    }

    private static ItemStack patternIcon(RitualPattern pattern) {
        ItemStack icon = predicateIcon(pattern.palette().get(pattern.anchorKey()));
        if (!icon.isEmpty()) {
            return icon;
        }
        for (RitualPattern.Predicate p : pattern.palette().values()) {
            icon = predicateIcon(p);
            if (!icon.isEmpty()) {
                return icon;
            }
        }
        return ItemStack.EMPTY;
    }

    private static ItemStack predicateIcon(RitualPattern.Predicate predicate) {
        if (predicate == null) {
            return ItemStack.EMPTY;
        }
        return switch (predicate.kind()) {
            case EXACT -> predicate.block() == null ? ItemStack.EMPTY : new ItemStack(predicate.block());
            case TAG -> firstTagBlockItem(predicate);
            default -> ItemStack.EMPTY;
        };
    }

    private static ItemStack firstTagBlockItem(RitualPattern.Predicate predicate) {
        if (predicate.tag() == null) {
            return ItemStack.EMPTY;
        }
        var holders = BuiltInRegistries.BLOCK.getTag(predicate.tag());
        if (holders.isEmpty()) {
            return ItemStack.EMPTY;
        }
        var iterator = holders.get().iterator();
        return iterator.hasNext() ? new ItemStack(iterator.next().value()) : ItemStack.EMPTY;
    }
}
