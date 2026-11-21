package com.bitsson.gensokyou.jei;

import com.bitsson.gensokyou.Gensokyou;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;

final class SpiritTreatmentCategory implements IRecipeCategory<SpiritTreatmentCategory.Card> {
    static final RecipeType<Card> OXIDATION = RecipeType.create(Gensokyou.MODID, "spirit_oxidizer", Card.class);
    static final RecipeType<Card> FREEZING = RecipeType.create(Gensokyou.MODID, "spirit_freezer", Card.class);

    record Card(ResourceLocation id, Block input, Block output, int firstMode, int lastMode, long cost) {}

    private final RecipeType<Card> type;
    private final Component title;
    private final IDrawable icon;
    private final IDrawable arrow;
    private final IDrawable slot;

    SpiritTreatmentCategory(IGuiHelper helper, RecipeType<Card> type, Block machine) {
        this.type = type;
        title = machine.getName();
        icon = helper.createDrawableIngredient(VanillaTypes.ITEM_STACK, new ItemStack(machine));
        arrow = helper.getRecipeArrow();
        slot = helper.getSlotDrawable();
    }

    @Override public RecipeType<Card> getRecipeType() { return type; }
    @Override public Component getTitle() { return title; }
    @Override public IDrawable getIcon() { return icon; }
    @Override public int getWidth() { return 150; }
    @Override public int getHeight() { return type == FREEZING ? 58 : 46; }
    @Override public ResourceLocation getRegistryName(Card recipe) { return recipe.id(); }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, Card recipe, IFocusGroup focuses) {
        var input = builder.addSlot(RecipeIngredientRole.INPUT, 10, 5).setBackground(slot, -1, -1);
        if (type == FREEZING) {
            input.addFluidStack(recipe.input() == Blocks.WATER ? Fluids.WATER : Fluids.LAVA, 1000)
                    .setFluidRenderer(1000, false, 16, 16);
        } else {
            input.addItemStack(new ItemStack(recipe.input()));
        }
        builder.addSlot(RecipeIngredientRole.OUTPUT, 71, 5).setBackground(slot, -1, -1)
                .addItemStack(new ItemStack(recipe.output()));
    }

    @Override
    public void draw(Card card, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX, double mouseY) {
        var font = Minecraft.getInstance().font;
        arrow.draw(graphics, 38, 5);
        String modes = card.firstMode() == card.lastMode() ? Integer.toString(card.firstMode())
                : card.firstMode() + "–" + card.lastMode();
        graphics.drawString(font, Component.translatable("jei.gensokyou.spirit_treatment.mode", modes), 9, 25, 0xFF333333, false);
        graphics.drawString(font, Component.translatable("jei.gensokyou.spirit_treatment.cost", card.cost()), 9, 36, 0xFF333333, false);
        if (type == FREEZING) {
            graphics.drawString(font, Component.translatable("jei.gensokyou.spirit_treatment.source"), 9, 47, 0xFF555555, false);
        }
    }
}
