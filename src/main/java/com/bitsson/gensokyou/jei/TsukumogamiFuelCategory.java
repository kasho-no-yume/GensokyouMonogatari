package com.bitsson.gensokyou.jei;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.config.GensokyouConfig;
import com.bitsson.gensokyou.ritual.TsukumogamiFuelLoader;
import com.bitsson.gensokyou.ritual.behavior.TsukumogamiBehavior;
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
 * 付丧之冢燃料卡页签：整级三张卡片（0/1/2），每张以 9 列网格展示全部陶片/唱片燃料，
 * 悬浮提示单件总量与燃烧时长。产灵速率与缓存随等级 ×5^level。
 */
public class TsukumogamiFuelCategory implements IRecipeCategory<TsukumogamiFuelCardWrapper> {

    public static final int WIDTH = 176;
    public static final int HEIGHT = 156;
    private static final int TITLE_COLOR = 0xFF1F1F1F;
    private static final int SUBTLE_COLOR = 0xFF4A6B7A;
    private static final int SLOTS_PER_ROW = 9;
    private static final int GRID_X = 4;
    private static final int GRID_Y = 36;
    private static final int ROW_STEP = 18;
    private static final int FOOTER_Y = HEIGHT - 11;

    private static final ConcurrentHashMap<String, RecipeType<TsukumogamiFuelCardWrapper>> TYPES =
            new ConcurrentHashMap<>();

    private final RecipeType<TsukumogamiFuelCardWrapper> type;
    private final ResourceLocation patternId;
    private final Component title;
    private final IDrawable icon;

    public TsukumogamiFuelCategory(IGuiHelper guiHelper, ResourceLocation patternId, IDrawable icon) {
        this.type = typeFor(patternId);
        this.patternId = patternId;
        this.title = Component.translatable("jei." + patternId.getNamespace()
                + ".ritual." + patternId.getPath());
        this.icon = icon;
    }

    public static RecipeType<TsukumogamiFuelCardWrapper> typeFor(ResourceLocation patternId) {
        return TYPES.computeIfAbsent(patternId.toString(), uid -> RecipeType.create(
                Gensokyou.MODID, "ritual_tsukumogami_fuel_" + ResourceLocation.parse(uid).getPath(),
                TsukumogamiFuelCardWrapper.class));
    }

    @Override
    public RecipeType<TsukumogamiFuelCardWrapper> getRecipeType() {
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
    public void setRecipe(IRecipeLayoutBuilder builder, TsukumogamiFuelCardWrapper recipe,
                          IFocusGroup focuses) {
        List<TsukumogamiFuelLoader.Entry> entries = recipe.entries();
        for (int i = 0; i < entries.size(); i++) {
            TsukumogamiFuelLoader.Entry entry = entries.get(i);
            long points = entry.points() * multOf(recipe.level());
            long seconds = TsukumogamiBehavior.burnSeconds(entry.points(), recipe.level());
            builder.addSlot(RecipeIngredientRole.INPUT, slotX(i), slotY(i))
                    .addItemStack(new ItemStack(entry.item()))
                    .addRichTooltipCallback((view, tooltip) -> {
                        tooltip.add(Component.translatable("jei." + Gensokyou.MODID
                                + ".tsukumogami.fuel_points", points));
                        tooltip.add(Component.translatable("jei." + Gensokyou.MODID
                                + ".tsukumogami.burn_seconds", seconds));
                    });
        }
    }

    @Override
    public void draw(TsukumogamiFuelCardWrapper recipe, IRecipeSlotsView recipeSlotsView,
                     GuiGraphics graphics, double mouseX, double mouseY) {
        Font font = Minecraft.getInstance().font;
        graphics.drawString(font, title, 4, 2, TITLE_COLOR, false);
        graphics.drawString(font, Component.translatable("jei." + Gensokyou.MODID
                + ".tsukumogami.level", recipe.level()), 4, 12, SUBTLE_COLOR, false);
        graphics.drawString(font, Component.translatable("jei." + Gensokyou.MODID
                + ".tsukumogami.rate", TsukumogamiBehavior.ratePerSlotPerSecond(recipe.level())),
                4, 23, SUBTLE_COLOR, false);
        graphics.drawString(font, Component.translatable("jei." + Gensokyou.MODID + ".tsukumogami.hint"),
                4, FOOTER_Y, SUBTLE_COLOR, false);
    }

    @Override
    public ResourceLocation getRegistryName(TsukumogamiFuelCardWrapper recipe) {
        return ResourceLocation.fromNamespaceAndPath(patternId.getNamespace(),
                patternId.getPath() + "_lvl_" + recipe.level());
    }

    private static long multOf(int level) {
        long value = 1L;
        int m = GensokyouConfig.TSUKUMOGAMI_LEVEL_MULT.get();
        for (int i = 0; i < level; i++) {
            value *= m;
        }
        return value;
    }

    private static int slotX(int index) {
        return GRID_X + (index % SLOTS_PER_ROW) * 18;
    }

    private static int slotY(int index) {
        return GRID_Y + (index / SLOTS_PER_ROW) * ROW_STEP;
    }
}
