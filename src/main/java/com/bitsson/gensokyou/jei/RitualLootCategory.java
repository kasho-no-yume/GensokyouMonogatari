package com.bitsson.gensokyou.jei;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.ritual.RitualLootTable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

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
 * 献祭权重卡页签（tool-sacrifice-rituals）：一仪式一页签，页签内按工具材质翻页；
 * 每张卡展示该材质的输入工具与全部产物条目，条目悬浮给出「约 X%」及隐藏条件。
 */
public class RitualLootCategory implements IRecipeCategory<RitualLootCardWrapper> {

    public static final int WIDTH = 170;
    public static final int HEIGHT = 128;
    private static final int TITLE_COLOR = 0xFF1F1F1F;
    private static final int SUBTLE_COLOR = 0xFF666666;
    private static final int SLOTS_PER_ROW = 9;
    private static final int TOOL_X = WIDTH - 22;
    private static final int TOOL_Y = 10;
    private static final int GRID_X = 4;
    private static final int GRID_Y = 34;
    private static final int ROW_STEP = 20;
    private static final int FOOTER_Y = HEIGHT - 12;

    private static final java.util.concurrent.ConcurrentHashMap<String, RecipeType<RitualLootCardWrapper>>
            TYPES = new ConcurrentHashMap<>();

    private final RecipeType<RitualLootCardWrapper> type;
    private final ResourceLocation patternId;
    private final Component title;
    private final IDrawable icon;

    public RitualLootCategory(IGuiHelper guiHelper, ResourceLocation patternId, IDrawable icon) {
        this.type = typeFor(patternId);
        this.patternId = patternId;
        this.title = Component.translatable("jei." + patternId.getNamespace()
                + ".ritual." + patternId.getPath());
        this.icon = icon;
    }

    public static RecipeType<RitualLootCardWrapper> typeFor(ResourceLocation patternId) {
        return TYPES.computeIfAbsent(patternId.toString(), uid -> RecipeType.create(
                Gensokyou.MODID, "ritual_loot_" + ResourceLocation.parse(uid).getPath(),
                RitualLootCardWrapper.class));
    }

    @Override
    public RecipeType<RitualLootCardWrapper> getRecipeType() {
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
    public void setRecipe(IRecipeLayoutBuilder builder, RitualLootCardWrapper recipe,
                          IFocusGroup focuses) {
        RitualLootTable.representativeForTier(recipe.toolTag(), recipe.tier())
                .ifPresent(tool -> builder.addSlot(RecipeIngredientRole.INPUT, TOOL_X, TOOL_Y)
                        .addItemStack(new ItemStack(tool)));
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
                                + ".loot.weight", formatPercent(percent)));
                        if (section == 1) {
                            tooltip.add(Component.translatable("jei." + Gensokyou.MODID + ".loot.nether"));
                        } else if (section == 2) {
                            tooltip.add(Component.translatable("jei." + Gensokyou.MODID + ".loot.end"));
                        }
                    });
        }
    }

    @Override
    public void draw(RitualLootCardWrapper recipe, IRecipeSlotsView recipeSlotsView,
                     GuiGraphics graphics, double mouseX, double mouseY) {
        Font font = Minecraft.getInstance().font;
        graphics.drawString(font, title, 4, 2, TITLE_COLOR, false);
        Component tier = Component.translatable("jei." + Gensokyou.MODID
                + ".loot.tier." + recipe.tier());
        graphics.drawString(font, tier, 4, 12, SUBTLE_COLOR, false);
        graphics.drawString(font, Component.translatable("jei." + Gensokyou.MODID + ".loot.hint"),
                4, FOOTER_Y, SUBTLE_COLOR, false);
    }

    @Override
    public ResourceLocation getRegistryName(RitualLootCardWrapper recipe) {
        return ResourceLocation.fromNamespaceAndPath(patternId.getNamespace(),
                patternId.getPath() + "_" + recipe.tier());
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
