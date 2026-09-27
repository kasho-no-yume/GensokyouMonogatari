package com.bitsson.gensokyou.jei;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.ritual.RitualSmeltRule;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * 煅炉配方卡页签（kanayamahiko-smelting）：一煅炉一页签，卡面 170×90——
 * 标题行（煅炉本地化名）、原料槽（矿石）、灵炭槽（按规则配比堆叠）、箭头、产物槽、
 * 底行（熔炼要诀）。产物槽悬浮说明方块原矿翻倍、粗矿不翻倍。
 * 三种矿物都不是原版烹饪配方，故此处不依赖 RecipeManager，直接由煅炉规则派生。
 */
public class RitualSmeltCategory implements IRecipeCategory<RitualSmeltCardWrapper> {

    public static final int WIDTH = 170;
    public static final int HEIGHT = 90;
    private static final int TITLE_COLOR = 0xFF1F1F1F;
    private static final int SUBTLE_COLOR = 0xFF666666;
    private static final int PRIMARY_X = 10;
    private static final int PRIMARY_Y = 14;
    private static final int AUX_Y = 40;
    private static final int ARROW_Y = 35;
    private static final int OUT_CENTER_X = WIDTH / 2 + 18;
    private static final int FOOTER_Y = HEIGHT - 12;

    private static final java.util.concurrent.ConcurrentHashMap<String,
            RecipeType<RitualSmeltCardWrapper>> TYPES = new java.util.concurrent.ConcurrentHashMap<>();

    private final RecipeType<RitualSmeltCardWrapper> type;
    private final ResourceLocation patternId;
    private final Component title;
    private final IDrawable icon;
    private final IDrawable arrow;

    public RitualSmeltCategory(IGuiHelper guiHelper, ResourceLocation patternId, IDrawable icon) {
        this.type = typeFor(patternId);
        this.patternId = patternId;
        this.title = Component.translatable("jei." + patternId.getNamespace()
                + ".ritual." + patternId.getPath());
        this.icon = icon;
        this.arrow = guiHelper.getRecipeArrow();
    }

    /** RecipeType 缓存：RecipeType 以实例身份为键，全生命周期每 uid 恰一个实例。 */
    public static RecipeType<RitualSmeltCardWrapper> typeFor(ResourceLocation patternId) {
        return TYPES.computeIfAbsent(patternId.toString(), uid -> RecipeType.create(
                Gensokyou.MODID, "ritual_smelt_" + ResourceLocation.parse(uid).getPath(),
                RitualSmeltCardWrapper.class));
    }

    @Override
    public RecipeType<RitualSmeltCardWrapper> getRecipeType() {
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
    public void setRecipe(IRecipeLayoutBuilder builder, RitualSmeltCardWrapper recipe,
                          IFocusGroup focuses) {
        RitualSmeltRule rule = recipe.rule();
        builder.addSlot(RecipeIngredientRole.INPUT, PRIMARY_X, PRIMARY_Y)
                .addItemStack(new ItemStack(rule.primary()));
        builder.addSlot(RecipeIngredientRole.INPUT, PRIMARY_X, AUX_Y)
                .addItemStack(new ItemStack(rule.auxiliary(), rule.auxiliaryCount()))
                .addRichTooltipCallback((view, tooltip) -> tooltip.add(
                        Component.translatable("jei." + Gensokyou.MODID + ".smelt.charcoal",
                                rule.auxiliaryCount())));
        String bonus = recipe.blockOre()
                ? ".smelt.ore_double" : ".smelt.rough";
        builder.addSlot(RecipeIngredientRole.OUTPUT, OUT_CENTER_X - 9, ARROW_Y - 9)
                .addItemStack(new ItemStack(rule.result(), recipe.resultCount()))
                .addRichTooltipCallback((view, tooltip) -> tooltip.add(
                        Component.translatable("jei." + Gensokyou.MODID + bonus)));
    }

    @Override
    public void draw(RitualSmeltCardWrapper recipe, IRecipeSlotsView recipeSlotsView,
                     GuiGraphics graphics, double mouseX, double mouseY) {
        Font font = Minecraft.getInstance().font;
        graphics.drawString(font, title, 4, 2, TITLE_COLOR, false);
        arrow.draw(graphics, OUT_CENTER_X - 9 - 9 - arrow.getWidth(), ARROW_Y);
        Component hint = Component.translatable("jei." + Gensokyou.MODID + ".smelt.hint");
        graphics.drawString(font, hint, 4, FOOTER_Y, SUBTLE_COLOR, false);
    }

    @Override
    public ResourceLocation getRegistryName(RitualSmeltCardWrapper recipe) {
        return recipe.id();
    }
}
