package com.bitsson.gensokyou.client.book;

import com.bitsson.gensokyou.client.ritual.ClientRitualData;
import com.bitsson.gensokyou.ritual.RitualRecipe;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import vazkii.patchouli.api.ICustomComponent;
import vazkii.patchouli.api.IComponentRenderContext;
import vazkii.patchouli.api.IVariable;

import java.util.List;
import java.util.function.UnaryOperator;

/**
 * 仪式配方卡组件（一页一配方）：读取 {@code ritual} + {@code recipe_index}，
 * 渲染该仪式的第 recipe_index 个配方（原料×数量 / 产物或本地化效果名 / spCost / minTier）。
 * 数据未同步时显示占位。
 */
public class RitualPageComponent implements ICustomComponent {

    private static final int HEADER_COLOR = 0xFF4A2B6B;
    private static final int BODY_COLOR = 0xFF2A2430;

    private ResourceLocation ritualId;
    private int recipeIndex;

    @Override
    public void onVariablesAvailable(UnaryOperator<IVariable> lookup, HolderLookup.Provider registries) {
        String raw = lookup.apply(IVariable.wrap("#ritual#", registries)).asString("");
        if (raw != null && !raw.isEmpty() && !raw.equals("#ritual#")) {
            ritualId = ResourceLocation.tryParse(raw);
        }
        String index = lookup.apply(IVariable.wrap("#recipe_index#", registries)).asString("0");
        try {
            recipeIndex = Math.max(0, Integer.parseInt(index.trim()));
        } catch (NumberFormatException ignored) {
            recipeIndex = 0;
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
        if (ritualId == null) {
            graphics.drawString(mc.font, Component.translatable("gensokyou.book.ritual.missing"),
                    x, y, BODY_COLOR, false);
            return;
        }
        if (!ClientRitualData.hasData()) {
            graphics.drawString(mc.font, Component.translatable("gensokyou.book.ritual.syncing"),
                    x, y, BODY_COLOR, false);
            return;
        }
        List<RitualRecipe> recipes = ClientRitualData.recipesFor(ritualId);
        if (recipeIndex >= recipes.size()) {
            return;
        }
        RitualRecipe recipe = recipes.get(recipeIndex);
        graphics.drawString(mc.font,
                Component.translatable("gensokyou.book.ritual.recipe_header", recipeIndex + 1, recipes.size())
                        .copy().append("  ").append(recipe.displayName()),
                x, y, HEADER_COLOR, false);
        y += 14;
        int col = 0;
        for (RitualRecipe.Ingredient ingredient : recipe.ingredients()) {
            if (col >= 9) {
                break;
            }
            ItemStack stack = ingredient.tag() != null
                    ? tagRepresentative(ingredient) : new ItemStack(ingredient.item());
            if (!stack.isEmpty()) {
                context.renderItemStack(graphics, x + col * 16, y, mouseX, mouseY,
                        stack.copyWithCount(ingredient.count()));
            }
            col++;
        }
        y += 22;
        graphics.drawString(mc.font,
                Component.translatable("jei.gensokyou.recipe.spirit_cost", recipe.spCost()),
                x, y, BODY_COLOR, false);
        y += 11;
        graphics.drawString(mc.font,
                Component.translatable("jei.gensokyou.recipe.tier", recipe.minTier()),
                x, y, BODY_COLOR, false);
        if (recipe.minPlayerTier() > 0) {
            y += 11;
            graphics.drawString(mc.font,
                    Component.translatable("jei.gensokyou.recipe.player_tier", recipe.minPlayerTier()),
                    x, y, BODY_COLOR, false);
        }
        y += 14;
        if (recipe.resultStack() != null) {
            context.renderItemStack(graphics, x, y + 4, mouseX, mouseY, recipe.resultStack());
        } else if (recipe.effect() != null) {
            ResourceLocation effectId = ResourceLocation.tryParse(recipe.effect());
            if (effectId != null) {
                Component effect = Component.translatableWithFallback(
                        "jei." + effectId.getNamespace() + ".effect." + effectId.getPath(),
                        effectId.getPath());
                graphics.drawString(mc.font, effect, x + 4, y + 6, BODY_COLOR, false);
            }
        }
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
