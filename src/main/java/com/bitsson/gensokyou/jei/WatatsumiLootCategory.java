package com.bitsson.gensokyou.jei;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.ritual.RitualLootTable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 绵津见献祭卡页签：页签内按仪式等级 0/1/2 翻页（3 张卡）；每张卡展示钓鱼池与海洋特产池的掷数与
 * 全部产物，悬浮给出「约 X%」与所属池。钓鱼池为原版静态权重，特产池来自数据文件。
 */
public class WatatsumiLootCategory implements IRecipeCategory<WatatsumiLootCardWrapper> {

    public static final int WIDTH = 176;
    public static final int HEIGHT = 156;
    private static final int TITLE_COLOR = 0xFF1F1F1F;
    private static final int SUBTLE_COLOR = 0xFF4A6B7A;
    private static final int SLOTS_PER_ROW = 9;
    private static final int TOOL_X = WIDTH - 22;
    private static final int TOOL_Y = 10;
    private static final int GRID_X = 4;
    private static final int GRID_Y = 36;
    private static final int ROW_STEP = 18;
    private static final int FOOTER_Y = HEIGHT - 11;

    private static final ConcurrentHashMap<String, RecipeType<WatatsumiLootCardWrapper>> TYPES =
            new ConcurrentHashMap<>();

    private final RecipeType<WatatsumiLootCardWrapper> type;
    private final ResourceLocation patternId;
    private final Component title;
    private final IDrawable icon;

    public WatatsumiLootCategory(IGuiHelper guiHelper, ResourceLocation patternId, IDrawable icon) {
        this.type = typeFor(patternId);
        this.patternId = patternId;
        this.title = Component.translatable("jei." + patternId.getNamespace()
                + ".ritual." + patternId.getPath());
        this.icon = icon;
    }

    public static RecipeType<WatatsumiLootCardWrapper> typeFor(ResourceLocation patternId) {
        return TYPES.computeIfAbsent(patternId.toString(), uid -> RecipeType.create(
                Gensokyou.MODID, "ritual_watatsumi_" + ResourceLocation.parse(uid).getPath(),
                WatatsumiLootCardWrapper.class));
    }

    @Override
    public RecipeType<WatatsumiLootCardWrapper> getRecipeType() {
        return type;
    }

    @Override
    public Component getTitle() {
        return title;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public int getWidth() {
        return WIDTH;
    }

    @Override
    public int getHeight() {
        return HEIGHT;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, WatatsumiLootCardWrapper recipe,
                          IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.INPUT, TOOL_X, TOOL_Y)
                .addItemStack(new ItemStack(Items.FISHING_ROD));
        List<RitualLootTable.Weighted> entries = recipe.allEntries();
        for (int i = 0; i < entries.size(); i++) {
            RitualLootTable.Weighted entry = entries.get(i);
            double denom = recipe.denominatorFor(i);
            double percent = denom > 0.0D ? entry.weight() / denom * 100.0D : 0.0D;
            int section = recipe.sectionFor(i);
            builder.addSlot(RecipeIngredientRole.OUTPUT, slotX(i), slotY(i))
                    .addItemStack(new ItemStack(entry.item()))
                    .addRichTooltipCallback((view, tooltip) -> {
                        tooltip.add(Component.translatable("jei." + Gensokyou.MODID
                                + ".watatsumi.weight", formatPercent(percent)));
                        tooltip.add(Component.translatable(sectionKey(section)));
                    });
        }
    }

    @Override
    public void draw(WatatsumiLootCardWrapper recipe, IRecipeSlotsView recipeSlotsView,
                     GuiGraphics graphics, double mouseX, double mouseY) {
        Font font = Minecraft.getInstance().font;
        graphics.drawString(font, title, 4, 2, TITLE_COLOR, false);
        graphics.drawString(font, Component.translatable("jei." + Gensokyou.MODID
                + ".watatsumi.level", recipe.level()), 4, 12, SUBTLE_COLOR, false);
        graphics.drawString(font, Component.translatable("jei." + Gensokyou.MODID
                        + ".watatsumi.counts", recipe.fishingRolls(), recipe.specialRolls()),
                4, 23, SUBTLE_COLOR, false);
        graphics.drawString(font, Component.translatable("jei." + Gensokyou.MODID + ".watatsumi.hint"),
                4, FOOTER_Y, SUBTLE_COLOR, false);
    }

    @Override
    public ResourceLocation getRegistryName(WatatsumiLootCardWrapper recipe) {
        return ResourceLocation.fromNamespaceAndPath(patternId.getNamespace(),
                patternId.getPath() + "_lvl_" + recipe.level());
    }

    private static String sectionKey(int section) {
        return switch (section) {
            case 1 -> "jei." + Gensokyou.MODID + ".watatsumi.section.treasure";
            case 2 -> "jei." + Gensokyou.MODID + ".watatsumi.section.special";
            case 3 -> "jei." + Gensokyou.MODID + ".loot.gensokyou_low";
            case 4 -> "jei." + Gensokyou.MODID + ".loot.gensokyou_high";
            default -> "jei." + Gensokyou.MODID + ".watatsumi.section.fish";
        };
    }

    private static int slotX(int index) {
        return GRID_X + (index % SLOTS_PER_ROW) * 18;
    }

    private static int slotY(int index) {
        return GRID_Y + (index / SLOTS_PER_ROW) * ROW_STEP;
    }

    private static String formatPercent(double percent) {
        if (percent >= 10.0D) {
            return String.format(java.util.Locale.ROOT, "%.0f", percent);
        }
        if (percent >= 1.0D) {
            return String.format(java.util.Locale.ROOT, "%.1f", percent);
        }
        return String.format(java.util.Locale.ROOT, "%.2f", percent);
    }
}
