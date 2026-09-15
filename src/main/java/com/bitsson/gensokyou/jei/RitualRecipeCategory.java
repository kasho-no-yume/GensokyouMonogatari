package com.bitsson.gensokyou.jei;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.ritual.RitualRecipe;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 仪式配方卡页签（grace-ux-and-jei-tabs D2/D3）：一页签一仪式，卡面 170×90 固定版面——
 * 标题行（配方本地化名 + 结构阶级）、原料网格（9 槽/行至多两行，不截断）、
 * 箭头 + 产物槽/效果名行、底行（灵力消耗 + 玩家阶级前置）。
 * patternId=null 的实例为兜底页签，收未登记专属页签的仪式配方。
 */
public class RitualRecipeCategory implements IRecipeCategory<RitualRecipeCardWrapper> {

    public static final int WIDTH = 170;
    public static final int HEIGHT = 90;
    private static final int TITLE_COLOR = 0xFF1F1F1F;
    private static final int SUBTLE_COLOR = 0xFF666666;
    private static final int SLOTS_PER_ROW = 9;
    private static final int MAX_SLOTS_SHOWN = SLOTS_PER_ROW * 2;
    private static final int GRID_X = 4;
    private static final int GRID_Y = 16;
    private static final int OUT_CENTER_X = WIDTH / 2 + 18;
    private static final int ARROW_Y = 56;
    private static final int ROW_FOOTER = HEIGHT - 12;

    private final RecipeType<RitualRecipeCardWrapper> type;
    private final @Nullable ResourceLocation patternId;
    private final Component title;
    private final IDrawable icon;
    private final IDrawable arrow;

    public RitualRecipeCategory(IGuiHelper guiHelper, RecipeType<RitualRecipeCardWrapper> type,
                                @Nullable ResourceLocation patternId, IDrawable icon) {
        this.type = type;
        this.patternId = patternId;
        this.title = patternId == null
                ? Component.translatable("jei." + Gensokyou.MODID + ".ritual_recipe_other")
                : Component.translatable("jei." + patternId.getNamespace() + ".ritual." + patternId.getPath());
        this.icon = icon;
        this.arrow = guiHelper.getRecipeArrow();
    }

    /** RecipeType 缓存：RecipeType 以实例身份为键，全生命周期每 uid 恰一个实例。 */
    private static final java.util.concurrent.ConcurrentHashMap<String,
            RecipeType<RitualRecipeCardWrapper>> TYPES = new java.util.concurrent.ConcurrentHashMap<>();

    public static RecipeType<RitualRecipeCardWrapper> typeFor(@Nullable ResourceLocation patternId) {
        return TYPES.computeIfAbsent(patternId == null ? "other" : patternId.toString(), uid ->
                RecipeType.create(Gensokyou.MODID, "ritual_recipe_"
                        + (uid.equals("other") ? "other"
                                : ResourceLocation.parse(uid).getPath()),
                        RitualRecipeCardWrapper.class));
    }

    @Override
    public RecipeType<RitualRecipeCardWrapper> getRecipeType() {
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
    public void setRecipe(IRecipeLayoutBuilder builder, RitualRecipeCardWrapper recipe, IFocusGroup focuses) {
        RitualRecipe definition = recipe.definition();
        List<RitualRecipe.Ingredient> ingredients = definition.ingredients();
        int shown = Math.min(ingredients.size(), MAX_SLOTS_SHOWN);
        for (int i = 0; i < shown; i++) {
            RitualRecipe.Ingredient ingredient = ingredients.get(i);
            ItemStack rep = ingredient.tag() != null ? tagRepresentative(ingredient)
                    : new ItemStack(ingredient.item());
            if (rep.isEmpty()) {
                continue; // 空标签成员不占位
            }
            builder.addSlot(RecipeIngredientRole.INPUT, slotX(i), slotY(i))
                    .addItemStack(rep.copyWithCount(ingredient.count()));
        }
        if (definition.resultStack() != null) {
            builder.addSlot(RecipeIngredientRole.OUTPUT, OUT_CENTER_X - 9, ARROW_Y - 4)
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
        Component tier = Component.translatable(
                "jei." + Gensokyou.MODID + ".recipe.tier", definition.minTier());
        graphics.drawString(font, tier, WIDTH - 4 - font.width(tier), 2, SUBTLE_COLOR, false);
        int arrowX = OUT_CENTER_X - 9 - 9 - arrow.getWidth();
        arrow.draw(graphics, arrowX, ARROW_Y);
        if (definition.resultStack() == null && definition.effect() != null) {
            ResourceLocation effectId = ResourceLocation.parse(definition.effect());
            Component effect = Component.translatableWithFallback(
                    "jei." + effectId.getNamespace() + ".effect." + effectId.getPath(),
                    effectId.getPath());
            graphics.drawString(font, effect, OUT_CENTER_X - font.width(effect) / 2,
                    ARROW_Y + 4, TITLE_COLOR, false);
        }
        graphics.drawString(font,
                Component.translatable("jei." + Gensokyou.MODID + ".recipe.spirit_cost",
                        definition.spCost()),
                4, ROW_FOOTER, SUBTLE_COLOR, false);
        if (definition.minPlayerTier() > 0) {
            Component need = Component.translatable(
                    "jei." + Gensokyou.MODID + ".recipe.player_tier", definition.minPlayerTier());
            graphics.drawString(font, need, WIDTH - 4 - font.width(need), ROW_FOOTER,
                    SUBTLE_COLOR, false);
        }
        List<RitualRecipe.Ingredient> ingredients = definition.ingredients();
        if (ingredients.size() > MAX_SLOTS_SHOWN) {
            graphics.drawString(font, "+" + (ingredients.size() - MAX_SLOTS_SHOWN),
                    slotX(MAX_SLOTS_SHOWN - 1) + 12, slotY(MAX_SLOTS_SHOWN - 1) - 1,
                    TITLE_COLOR, true); // 防御角标：现网配方最多 10 原料，理论不可达
        }
    }

    @Override
    public ResourceLocation getRegistryName(RitualRecipeCardWrapper recipe) {
        return recipe.definition().id();
    }

    private static int slotX(int index) {
        return GRID_X + (index % SLOTS_PER_ROW) * 18;
    }

    private static int slotY(int index) {
        return GRID_Y + (index / SLOTS_PER_ROW) * 18;
    }

    private static ItemStack tagRepresentative(RitualRecipe.Ingredient ingredient) {
        var holders = BuiltInRegistries.ITEM.getTag(ingredient.tag());
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
