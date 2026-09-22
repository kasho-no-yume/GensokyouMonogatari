package com.bitsson.gensokyou.client.book;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import vazkii.patchouli.api.ICustomComponent;
import vazkii.patchouli.api.IComponentRenderContext;
import vazkii.patchouli.api.IVariable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * 献祭产出卡（书版 JEI）：读取 {@code tool_item}/{@code tier}/{@code mode}/{@code count}/{@code entries}，
 * 顶部摆出**献祭用的工具**，其下以**物品图标网格**列出可获得的产物（按段分池）。
 * 条目串格式：{@code itemId,pct,section} 以 {@code ;} 连接；section 0/1/2 分段；
 * 概率数据由构建期生成器按与 JEI 相同的权重归一算出后内联。
 */
public class RitualLootComponent implements ICustomComponent {

    private static final int HEADER_COLOR = 0xFF4A2B6B;
    private static final int SUBTLE_COLOR = 0xFF666666;
    private static final int COLS = 7;
    private static final int CELL = 16;
    private static final int LINE_HEIGHT = 10;

    private final List<Entry> entries = new ArrayList<>();
    private String toolItem = "";
    private String tier = "";
    private int count;
    private String mode = "tool";

    private record Entry(Item item, String pct, int section) {
    }

    @Override
    public void onVariablesAvailable(UnaryOperator<IVariable> lookup, HolderLookup.Provider registries) {
        toolItem = lookup.apply(IVariable.wrap("#tool_item#", registries)).asString("");
        tier = lookup.apply(IVariable.wrap("#tier#", registries)).asString("");
        mode = lookup.apply(IVariable.wrap("#mode#", registries)).asString("tool");
        String countRaw = lookup.apply(IVariable.wrap("#count#", registries)).asString("0");
        try {
            count = Integer.parseInt(countRaw.trim());
        } catch (NumberFormatException ignored) {
            count = 0;
        }
        entries.clear();
        String raw = lookup.apply(IVariable.wrap("#entries#", registries)).asString("");
        if (raw == null || raw.isEmpty() || raw.contains("#entries#")) {
            return;
        }
        for (String part : raw.split(";")) {
            String[] f = part.split(",");
            if (f.length < 3) {
                continue;
            }
            ResourceLocation id = ResourceLocation.tryParse(f[0]);
            if (id == null) {
                continue;
            }
            int section;
            try {
                section = Integer.parseInt(f[2]);
            } catch (NumberFormatException ignored) {
                section = 0;
            }
            entries.add(new Entry(BuiltInRegistries.ITEM.get(id), f[1], section));
        }
    }

    @Override
    public void build(int x, int y, int pageNum) {
    }

    @Override
    public void render(GuiGraphics graphics, IComponentRenderContext context,
                       float partialTicks, int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        int x = 0;
        int y = 6;

        boolean watatsumi = "watatsumi".equals(mode);
        if (watatsumi) {
            y = drawWrapped(graphics, mc,
                    Component.translatable("gensokyou.book.loot.level", tier, count), x, y, HEADER_COLOR);
        }
        Item tool = toolItem();
        if (tool != null) {
            ItemStack stack = new ItemStack(tool);
            context.renderItemStack(graphics, x, y + 1, mouseX, mouseY, stack);
            drawWrapped(graphics, mc, Component.translatable("gensokyou.book.loot.sacrifice",
                    stack.getHoverName()), x + 18, y + 4, HEADER_COLOR);
            y += 18;
        }

        List<Entry> list = new ArrayList<>();
        for (Entry entry : entries) {
            if (entry.item() != null) {
                list.add(entry);
            }
        }
        if (watatsumi) {
            // 海产：所有产物混在一起单网格显示（不分池，避免超框）。
            renderGrid(graphics, context, list, x, y, mouseX, mouseY);
        } else {
            for (int section = 0; section <= 2; section++) {
                List<Entry> sec = new ArrayList<>();
                for (Entry entry : list) {
                    if (entry.section() == section) {
                        sec.add(entry);
                    }
                }
                if (sec.isEmpty()) {
                    continue;
                }
                y = drawWrapped(graphics, mc, sectionLabel(section), x, y, SUBTLE_COLOR);
                y = renderGrid(graphics, context, sec, x, y, mouseX, mouseY) + 3;
            }
        }
    }

    private static int renderGrid(GuiGraphics graphics, IComponentRenderContext context,
                                  List<Entry> sec, int x, int y, int mouseX, int mouseY) {
        for (int i = 0; i < sec.size(); i++) {
            int cx = x + (i % COLS) * CELL;
            int cy = y + (i / COLS) * CELL;
            context.renderItemStack(graphics, cx, cy, mouseX, mouseY, new ItemStack(sec.get(i).item()));
        }
        return y + ((sec.size() + COLS - 1) / COLS) * CELL;
    }

    private Item toolItem() {
        if (toolItem == null || toolItem.isEmpty() || toolItem.contains("#tool_item#")) {
            return null;
        }
        ResourceLocation id = ResourceLocation.tryParse(toolItem);
        if (id == null) {
            return null;
        }
        Item item = BuiltInRegistries.ITEM.get(id);
        return item == net.minecraft.world.item.Items.AIR ? null : item;
    }

    private static Component sectionLabel(int section) {
        return Component.translatable(switch (section) {
            case 1 -> "jei.gensokyou.loot.nether";
            case 2 -> "jei.gensokyou.loot.end";
            default -> "gensokyou.book.loot.base";
        });
    }

    /** 按页宽自动换行绘制文本，返回下一行的 y。 */
    private static int drawWrapped(GuiGraphics graphics, Minecraft mc, Component text,
                                   int x, int y, int color) {
        List<FormattedCharSequence> lines = mc.font.split(text, 116);
        for (FormattedCharSequence line : lines) {
            graphics.drawString(mc.font, line, x, y, color, false);
            y += LINE_HEIGHT;
        }
        return y;
    }
}
