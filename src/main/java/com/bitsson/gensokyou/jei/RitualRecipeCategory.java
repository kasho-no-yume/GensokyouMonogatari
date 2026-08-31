package com.bitsson.gensokyou.jei;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.registry.ModItems;
import com.bitsson.gensokyou.ritual.RitualRecipe;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
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

/** 仪式配方卡：输入=原料，输出=result 物品或效果名文本；标注所属仪式与等级门槛。 */
public class RitualRecipeCategory implements IRecipeCategory<RitualRecipeCardWrapper> {

    public static final RecipeType<RitualRecipeCardWrapper> TYPE =
            RecipeType.create(Gensokyou.MODID, "ritual_recipe", RitualRecipeCardWrapper.class);

    public static final int WIDTH = 170;
    public static final int HEIGHT = 66;
    private static final int TITLE_COLOR = 0xFF1F1F1F;

    private final IDrawable icon;
    private final IDrawable slotBg;

    public RitualRecipeCategory(IJeiHelpers helpers) {
        IGuiHelper guiHelper = helpers.getGuiHelper();
        this.icon = guiHelper.createDrawableIngredient(VanillaTypes.ITEM_STACK,
                new ItemStack(ModItems.RITUAL_PEDESTAL_ITEMS.get(0).get()));
        this.slotBg = guiHelper.getSlotDrawable();
    }

    @Override
    public RecipeType<RitualRecipeCardWrapper> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei." + Gensokyou.MODID + ".ritual_recipe");
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
    public void setRecipe(IRecipeLayoutBuilder builder, RitualRecipeCardWrapper recipe, IFocusGroup focuses) {
        RitualRecipe definition = recipe.definition();
        List<RitualRecipe.Ingredient> ingredients = definition.ingredients();
        int slots = Math.min(ingredients.size(), 8);
        int startX = (WIDTH - slots * 18) / 2;
        for (int i = 0; i < slots; i++) {
            RitualRecipe.Ingredient ingredient = ingredients.get(i);
            ItemStack rep = ingredient.tag() != null ? tagRepresentative(ingredient)
                    : new ItemStack(ingredient.item());
            if (!rep.isEmpty()) {
                builder.addSlot(RecipeIngredientRole.INPUT, startX + i * 18, 20)
                        .addItemStack(rep.copyWithCount(ingredient.count()));
            }
        }
        if (definition.resultStack() != null) {
            builder.addSlot(RecipeIngredientRole.OUTPUT, WIDTH / 2 - 9, HEIGHT - 26)
                    .addItemStack(definition.resultStack());
        }
    }

    @Override
    public void draw(RitualRecipeCardWrapper recipe,
                     mezz.jei.api.gui.ingredient.IRecipeSlotsView recipeSlotsView,
                     GuiGraphics graphics, double mouseX, double mouseY) {
        Font font = Minecraft.getInstance().font;
        RitualRecipe definition = recipe.definition();
        graphics.drawString(font, definition.displayName(), 4, 2, TITLE_COLOR, false);
        Component patternName = Component.translatableWithFallback(
                "jei." + definition.patternId().getNamespace() + ".ritual."
                        + definition.patternId().getPath(),
                definition.patternId().getPath().replace('_', ' '));
        String header = patternName.getString() + " · " + "I".repeat(Math.max(1, definition.minTier()));
        graphics.drawString(font, header, (WIDTH - font.width(header)) / 2, 44, TITLE_COLOR, false);
        if (definition.resultStack() == null && definition.effect() != null) {
            ResourceLocation effectId = ResourceLocation.parse(definition.effect());
            Component effect = Component.translatableWithFallback(
                    "jei." + effectId.getNamespace() + ".effect." + effectId.getPath(),
                    "→ " + effectId.getPath().replace('_', ' '));
            graphics.drawString(font, effect, (WIDTH - font.width(effect)) / 2, HEIGHT - 14,
                    TITLE_COLOR, false);
        }
    }

    @Override
    public ResourceLocation getRegistryName(RitualRecipeCardWrapper recipe) {
        return recipe.definition().id();
    }

    private ItemStack tagRepresentative(RitualRecipe.Ingredient ingredient) {
        var holders = net.minecraft.core.registries.BuiltInRegistries.ITEM.getTag(ingredient.tag());
        if (holders.isPresent()) {
            for (var holder : holders.get()) {
                ItemStack stack = new ItemStack(holder.value());
                if (!stack.isEmpty()) {
                    return stack;
                }
            }
        }
        return ItemStack.EMPTY;
    }
}
