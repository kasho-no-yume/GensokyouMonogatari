package com.bitsson.gensokyou.jei;

import com.bitsson.gensokyou.Gensokyou;
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

import java.util.concurrent.ConcurrentHashMap;

/**
 * 炼药配方卡页签（sunako-brew-ritual）：一张卡 = 一个「试剂 → 药水」。
 *
 * <p>卡面 170×86：标题行（仪式名）、试剂槽、箭头、<b>三个阶的产物槽</b>、底行提示。
 * 三个产物平铺而非折叠成"按阶切换"，是为了让玩家一眼看到"同一张药水的品质阶梯"——
 * 这正是本仪式的卖点（原版造不出"长时效 + 高品质"同施）。
 */
public class RitualBrewCategory implements IRecipeCategory<BrewRecipeCardWrapper> {

    public static final int WIDTH = 170;
    public static final int HEIGHT = 86;
    private static final int TITLE_COLOR = 0xFF1F1F1F;
    private static final int SUBTLE_COLOR = 0xFF666666;
    private static final int REAGENT_X = 10;
    private static final int SLOT_Y = 26;
    private static final int ARROW_Y = SLOT_Y + 9 - 6;
    private static final int FIRST_OUT_X = 78;
    private static final int OUT_GAP = 20;
    private static final int FOOTER_Y = HEIGHT - 12;

    private static final ConcurrentHashMap<String, RecipeType<BrewRecipeCardWrapper>> TYPES =
            new ConcurrentHashMap<>();

    private final RecipeType<BrewRecipeCardWrapper> type;
    private final ResourceLocation patternId;
    private final Component title;
    private final IDrawable icon;
    private final IDrawable arrow;

    public RitualBrewCategory(IGuiHelper guiHelper, ResourceLocation patternId, IDrawable icon) {
        this.type = typeFor(patternId);
        this.patternId = patternId;
        this.title = Component.translatable("jei." + patternId.getNamespace()
                + ".ritual." + patternId.getPath());
        this.icon = icon;
        this.arrow = guiHelper.getRecipeArrow();
    }

    /** RecipeType 缓存：RecipeType 以实例身份为键，全生命周期每 uid 恰一个实例。 */
    public static RecipeType<BrewRecipeCardWrapper> typeFor(ResourceLocation patternId) {
        return TYPES.computeIfAbsent(patternId.toString(), uid -> RecipeType.create(
                Gensokyou.MODID, "ritual_brew_" + ResourceLocation.parse(uid).getPath(),
                BrewRecipeCardWrapper.class));
    }

    @Override
    public RecipeType<BrewRecipeCardWrapper> getRecipeType() {
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
    public void setRecipe(IRecipeLayoutBuilder builder, BrewRecipeCardWrapper recipe,
                          IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.INPUT, REAGENT_X, SLOT_Y)
                .addItemStack(recipe.reagentStack());
        for (int tier = 0; tier < recipe.results().size(); tier++) {
            final int shownTier = tier + 1;
            ItemStack result = recipe.results().get(tier);
            builder.addSlot(RecipeIngredientRole.OUTPUT, FIRST_OUT_X + tier * OUT_GAP, SLOT_Y)
                    .addItemStack(result)
                    .addRichTooltipCallback((view, tooltip) -> {
                        tooltip.add(Component.translatable("jei." + Gensokyou.MODID
                                + ".brew.tier", shownTier));
                        tooltip.add(result.getHoverName());
                    });
        }
    }

    @Override
    public void draw(BrewRecipeCardWrapper recipe, IRecipeSlotsView recipeSlotsView,
                     GuiGraphics graphics, double mouseX, double mouseY) {
        Font font = Minecraft.getInstance().font;
        graphics.drawString(font, title, 4, 2, TITLE_COLOR, false);
        arrow.draw(graphics, FIRST_OUT_X - 32 - arrow.getWidth(), SLOT_Y + 3);
        graphics.drawString(font,
                Component.translatable("jei." + Gensokyou.MODID + ".brew.hint"),
                4, FOOTER_Y, SUBTLE_COLOR, false);
    }

    @Override
    public ResourceLocation getRegistryName(BrewRecipeCardWrapper recipe) {
        return recipe.id();
    }
}