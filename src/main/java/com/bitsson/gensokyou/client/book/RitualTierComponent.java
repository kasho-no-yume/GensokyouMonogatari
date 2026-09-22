package com.bitsson.gensokyou.client.book;

import com.bitsson.gensokyou.client.ritual.ClientRitualData;
import com.bitsson.gensokyou.ritual.RitualPattern;
import com.bitsson.gensokyou.ritual.RitualRecipe;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import vazkii.patchouli.api.ICustomComponent;
import vazkii.patchouli.api.IComponentRenderContext;
import vazkii.patchouli.api.IVariable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * 仪式「阶级详情」组件：读取 {@code ritual} + {@code tier}，
 * 渲染该阶的仪式参数（配方 spCost/minPlayerTier、供品消耗）与搭建到该阶所需的累计材料。
 * 页面本身挂 `gensokyou:guide/tier_N` 门槛，故玩家只会看到 ≤ 自己阶级的详情。
 */
public class RitualTierComponent implements ICustomComponent {

    private static final int HEADER_COLOR = 0xFF4A2B6B;
    private static final int BODY_COLOR = 0xFF2A2430;
    private static final int SUBTLE_COLOR = 0xFF666666;
    private static final int MAX_MATERIAL_LINES = 9;
    private static final int MAX_RECIPE_LINES = 5;

    private ResourceLocation ritualId;
    private int tier;

    @Override
    public void onVariablesAvailable(UnaryOperator<IVariable> lookup, HolderLookup.Provider registries) {
        String raw = lookup.apply(IVariable.wrap("#ritual#", registries)).asString("");
        if (raw != null && !raw.isEmpty() && !raw.equals("#ritual#")) {
            ritualId = ResourceLocation.tryParse(raw);
        }
        String tierRaw = lookup.apply(IVariable.wrap("#tier#", registries)).asString("0");
        try {
            tier = Integer.parseInt(tierRaw.trim());
        } catch (NumberFormatException ignored) {
            tier = 0;
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
        graphics.drawString(mc.font,
                Component.translatable("gensokyou.book.ritual.tier_header", tier),
                x, y, HEADER_COLOR, false);
        y += 13;
        if (ritualId == null || !ClientRitualData.hasData()) {
            graphics.drawString(mc.font, Component.translatable(
                    ritualId == null ? "gensokyou.book.ritual.missing" : "gensokyou.book.ritual.syncing"),
                    x, y, BODY_COLOR, false);
            return;
        }
        var patternOpt = ClientRitualData.pattern(ritualId);
        if (patternOpt.isEmpty()) {
            graphics.drawString(mc.font, Component.translatable("gensokyou.book.ritual.no_structure"),
                    x, y, BODY_COLOR, false);
            return;
        }
        RitualPattern pattern = patternOpt.get();

        graphics.drawString(mc.font, Component.translatable("gensokyou.book.ritual.params"),
                x, y, SUBTLE_COLOR, false);
        y += 11;
        y = renderParams(graphics, mc, ritualId, x, y);
        y += 3;
        graphics.drawString(mc.font, Component.translatable("gensokyou.book.ritual.materials"),
                x, y, SUBTLE_COLOR, false);
        y += 11;
        renderMaterials(graphics, mc, pattern, x, y);
    }

    private int renderParams(GuiGraphics graphics, Minecraft mc, ResourceLocation ritualId, int x, int y) {
        int lines = 0;
        for (RitualRecipe recipe : ClientRitualData.recipesFor(ritualId)) {
            if (recipe.minTier() > tier || lines >= MAX_RECIPE_LINES) {
                continue;
            }
            Component line = recipe.displayName().copy()
                    .append("  ")
                    .append(Component.translatable("jei.gensokyou.recipe.spirit_cost", recipe.spCost()));
            if (recipe.minPlayerTier() > 0) {
                line = line.copy().append("  ")
                        .append(Component.translatable("jei.gensokyou.recipe.player_tier", recipe.minPlayerTier()));
            }
            graphics.drawString(mc.font, line, x, y, BODY_COLOR, false);
            y += 10;
            lines++;
        }
        var patternOpt = ClientRitualData.pattern(ritualId);
        if (patternOpt.isPresent()) {
            for (RitualPattern.Offering offering : patternOpt.get().requirements()) {
                String itemName = offering.item().item() != null
                        ? new net.minecraft.world.item.ItemStack(offering.item().item())
                                .getHoverName().getString()
                        : "#" + offering.item().tag().location();
                Component consume = switch (offering.consume()) {
                    case NONE -> Component.translatable("gensokyou.book.ritual.consume.none");
                    case ON_ACTIVATE -> Component.translatable("gensokyou.book.ritual.consume.activate");
                    case PERIODIC -> Component.translatable("gensokyou.book.ritual.consume.periodic",
                            offering.period() / 20);
                };
                graphics.drawString(mc.font,
                        Component.translatable("gensokyou.book.ritual.offering", itemName, consume),
                        x, y, BODY_COLOR, false);
                y += 10;
            }
        }
        if (lines == 0) {
            y += 2;
        }
        return y;
    }

    private void renderMaterials(GuiGraphics graphics, Minecraft mc, RitualPattern pattern, int x, int y) {
        List<RitualPattern.BlockEntry> blocks = List.of();
        for (RitualPattern.LevelSlice slice : pattern.levels()) {
            if (slice.level() <= tier) {
                blocks = slice.blocks();
            }
        }
        Map<String, Integer> agg = new LinkedHashMap<>();
        for (RitualPattern.BlockEntry entry : blocks) {
            RitualPattern.Predicate predicate = pattern.palette().get(entry.key());
            if (predicate == null) {
                continue;
            }
            String name = switch (predicate.kind()) {
                case EXACT -> predicate.block().getName().getString();
                case TAG -> "#" + predicate.tag().location();
                case AIR, IGNORE -> null;
            };
            if (name != null) {
                agg.merge(name, 1, Integer::sum);
            }
        }
        int shown = 0;
        int total = 0;
        for (Map.Entry<String, Integer> material : agg.entrySet()) {
            total++;
            if (shown >= MAX_MATERIAL_LINES) {
                continue;
            }
            graphics.drawString(mc.font, Component.literal(material.getKey() + " ×" + material.getValue()),
                    x, y, BODY_COLOR, false);
            y += 10;
            shown++;
        }
        if (total > shown) {
            graphics.drawString(mc.font,
                    Component.translatable("gensokyou.book.ritual.materials_more", total - shown),
                    x, y, SUBTLE_COLOR, false);
        }
    }
}
