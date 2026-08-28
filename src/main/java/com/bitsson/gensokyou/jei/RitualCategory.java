package com.bitsson.gensokyou.jei;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.registry.ModItems;
import com.bitsson.gensokyou.ritual.RitualPattern;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IIngredientAcceptor;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.gui.widgets.IRecipeWidget;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.helpers.IJeiHelpers;
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

import java.util.List;

public class RitualCategory implements IRecipeCategory<RitualRecipeWrapper> {

    public static final RecipeType<RitualRecipeWrapper> TYPE =
            RecipeType.create(Gensokyou.MODID, "ritual", RitualRecipeWrapper.class);

    public static final int VIEW_WIDTH = 170;
    public static final int VIEW_HEIGHT = 130;
    public static final int TITLE_HEIGHT = 13;
    private static final int TITLE_COLOR = 0xFF1F1F1F;
    private static final int PAGE_COLOR = 0xFF555555;

    private final IDrawable icon;
    private final IDrawable slotBg;
    private final CanvasSize canvas = new CanvasSize(VIEW_WIDTH, TITLE_HEIGHT + VIEW_HEIGHT);

    private record CanvasSize(int width, int height) {
    }

    public RitualCategory(IJeiHelpers helpers) {
        IGuiHelper guiHelper = helpers.getGuiHelper();
        this.icon = guiHelper.createDrawableIngredient(
                VanillaTypes.ITEM_STACK, new ItemStack(ModItems.RITUAL_CORE_ITEM.get()));
        this.slotBg = guiHelper.getSlotDrawable();
    }

    @Override
    public RecipeType<RitualRecipeWrapper> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei." + Gensokyou.MODID + ".ritual");
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public int getWidth() {
        return canvas.width();
    }

    @Override
    public int getHeight() {
        return canvas.height();
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, RitualRecipeWrapper recipe, IFocusGroup focuses) {
        IIngredientAcceptor<?> inputs = builder.addInvisibleIngredients(RecipeIngredientRole.INPUT);
        inputs.addItemStacks(RitualGrid.distinctStacks(recipe.pattern()));

        List<ItemStack> catalysts = RitualCatalysts.forPattern(recipe.pattern().id());
        if (!catalysts.isEmpty()) {
            IIngredientAcceptor<?> invisible = builder.addInvisibleIngredients(RecipeIngredientRole.CATALYST);
            invisible.addItemStacks(catalysts);
        }
    }

    @Override
    public void createRecipeExtras(IRecipeExtrasBuilder builder, RitualRecipeWrapper recipe, IFocusGroup focuses) {
        StructureViewWidget widget = new StructureViewWidget(recipe.pattern(), slotBg);
        builder.addWidget(widget);
        builder.addInputHandler(widget);
    }

    @Override
    public void draw(RitualRecipeWrapper recipe, mezz.jei.api.gui.ingredient.IRecipeSlotsView recipeSlotsView,
                     GuiGraphics guiGraphics, double mouseX, double mouseY) {
        guiGraphics.drawString(Minecraft.getInstance().font,
                displayName(recipe.pattern().id()), 4, 2, TITLE_COLOR, false);
    }

    @Override
    public ResourceLocation getRegistryName(RitualRecipeWrapper recipe) {
        return recipe.pattern().id();
    }

    private static Component displayName(ResourceLocation id) {
        return Component.translatableWithFallback(
                "jei." + id.getNamespace() + ".ritual." + id.getPath(),
                id.getPath().replace('_', ' '));
    }
}
