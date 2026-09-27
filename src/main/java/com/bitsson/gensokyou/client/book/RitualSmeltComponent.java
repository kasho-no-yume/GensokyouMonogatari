package com.bitsson.gensokyou.client.book;

import com.bitsson.gensokyou.client.ritual.ClientRitualData;
import com.bitsson.gensokyou.ritual.RitualSmeltRule;
import com.bitsson.gensokyou.ritual.behavior.KanayamahikoSmelting;
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
 * 煅炉配方卡组件（一页一产物）：读取 {@code ritual} + {@code result}，
 * 列出该煅炉里能熔出这件产物的全部规则——原料 / 灵炭配比 / 产物数量。
 * 规则取自 {@link ClientRitualData}（服务端快照），与 JEI 煅炉页签同源，
 * 故书里写的配比与游戏里真正生效的配比不会走样；联机上客户端不加载 reload
 * listener，快照是唯一的数据来源。
 * 方块原矿的产物翻倍由 {@link KanayamahikoSmelting#isBlockOre} 判定，与煅炉运行时同一口径。
 */
public class RitualSmeltComponent implements ICustomComponent {

    private static final int HEADER_COLOR = 0xFF4A2B6B;
    private static final int SUBTLE_COLOR = 0xFF666666;
    private static final int WRAP_WIDTH = 116;
    private static final int LINE_HEIGHT = 11;
    private static final int ROW_STEP = 20;
    private static final int PRIMARY_X = 0;
    private static final int AUX_X = 38;
    private static final int RESULT_X = 86;

    private ResourceLocation ritualId;
    private ResourceLocation resultId;

    @Override
    public void onVariablesAvailable(UnaryOperator<IVariable> lookup, HolderLookup.Provider registries) {
        ritualId = variable(lookup, registries, "#ritual#");
        resultId = variable(lookup, registries, "#result#");
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
        List<RitualSmeltRule> rules = selectRules();
        if (ritualId == null) {
            graphics.drawString(mc.font, Component.translatable("gensokyou.book.ritual.missing"),
                    x, y, SUBTLE_COLOR, false);
            return;
        }
        y = drawWrapped(graphics, mc, ritualName(), x, y, HEADER_COLOR);
        y += 2;
        if (rules.isEmpty()) {
            drawWrapped(graphics, mc, Component.translatable("gensokyou.book.smelt.missing"),
                    x, y, SUBTLE_COLOR);
            return;
        }
        for (RitualSmeltRule rule : rules) {
            context.renderItemStack(graphics, PRIMARY_X, y, mouseX, mouseY,
                    new ItemStack(rule.primary()));
            context.renderItemStack(graphics, AUX_X, y, mouseX, mouseY,
                    new ItemStack(rule.auxiliary(), rule.auxiliaryCount()));
            // 灵炭配比必须写出来：renderItemDecorations 只在 count>1 时画数字，
            // 配比为 1 时图标上不显示任何数量，只剩一个光秃秃的灵炭。
            graphics.drawString(mc.font, times(rule.auxiliaryCount()), AUX_X + 18, y + 4,
                    SUBTLE_COLOR, false);
            graphics.drawString(mc.font, "→", 72, y + 4, SUBTLE_COLOR, false);
            // 产物数量交给图标自身的角标，不再另写「×N」——两处都标会看着像翻了两倍。
            context.renderItemStack(graphics, RESULT_X, y, mouseX, mouseY,
                    new ItemStack(rule.result(), outputCount(rule)));
            y += ROW_STEP;
        }
        y += 2;
        drawWrapped(graphics, mc, Component.translatable("gensokyou.book.smelt.ore_double"),
                x, y, SUBTLE_COLOR);
    }

    /** 该煅炉里能熔出本页产物的规则；未指定 result 时列出全部规则。 */
    private List<RitualSmeltRule> selectRules() {
        if (ritualId == null || !ClientRitualData.hasData()) {
            return List.of();
        }
        List<RitualSmeltRule> out = new ArrayList<>();
        for (RitualSmeltRule rule : ClientRitualData.smeltsFor(ritualId)) {
            if (resultId == null || rule.result().equals(itemOf(resultId))) {
                out.add(rule);
            }
        }
        return out;
    }

    private static int outputCount(RitualSmeltRule rule) {
        return KanayamahikoSmelting.isBlockOre(new ItemStack(rule.primary()))
                ? rule.resultCount() * 2
                : rule.resultCount();
    }

    private Component ritualName() {
        return Component.translatable("jei." + ritualId.getNamespace()
                + ".ritual." + ritualId.getPath());
    }

    private static Component times(int count) {
        return Component.translatable("gensokyou.book.smelt.times", count);
    }

    private static ResourceLocation variable(UnaryOperator<IVariable> lookup,
                                             HolderLookup.Provider registries, String name) {
        String raw = lookup.apply(IVariable.wrap(name, registries)).asString("");
        if (raw == null || raw.isEmpty() || raw.equals(name)) {
            return null;
        }
        return ResourceLocation.tryParse(raw);
    }

    private static Item itemOf(ResourceLocation id) {
        return BuiltInRegistries.ITEM.get(id);
    }

    /** 按页宽自动换行绘制文本，返回下一行的 y。 */
    private static int drawWrapped(GuiGraphics graphics, Minecraft mc, Component text,
                                   int x, int y, int color) {
        List<FormattedCharSequence> lines = mc.font.split(text, WRAP_WIDTH);
        for (FormattedCharSequence line : lines) {
            graphics.drawString(mc.font, line, x, y, color, false);
            y += LINE_HEIGHT;
        }
        return y;
    }
}
