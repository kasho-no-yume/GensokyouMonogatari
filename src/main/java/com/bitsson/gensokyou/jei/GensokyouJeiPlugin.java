package com.bitsson.gensokyou.jei;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.registry.ModItems;
import com.bitsson.gensokyou.ritual.RitualBehaviors;
import com.bitsson.gensokyou.ritual.RitualRecipe;
import com.bitsson.gensokyou.ritual.RitualRecipeLoader;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JEI 插件（grace-ux-and-jei-tabs）：仪式配方按仪式分页（静态登记 + 兜底页签，方案 C——
 * JEI 19.44 无运行时 addCategories），全部数据由 ritual_recipes 派生；结构页签已删
 * （结构查看唯一入口 = 仪式构建器）。热重载经 {@link #syncFromLoader} 逐页签增删卡片、
 * 按空收敛显隐。
 */
@JeiPlugin
public class GensokyouJeiPlugin implements IModPlugin {

    /** 专属页签清单：新配方仪式要独立页签在此加一行（未登记的落兜底页签）。 */
    private static final List<ResourceLocation> DEDICATED_TABS = List.of(
            RitualBehaviors.ZAOHUA,
            RitualBehaviors.KAMI_NO_MEGUMI);
    private static final RecipeType<RitualRecipeCardWrapper> FALLBACK_TYPE =
            RitualRecipeCategory.typeFor(null);

    static boolean hasDedicatedTab(ResourceLocation patternId) {
        return DEDICATED_TABS.contains(patternId);
    }

    private static volatile IJeiRuntime runtime;
    private static volatile Map<ResourceLocation, RitualRecipeCardWrapper> syncedRecipes = Map.of();

    @Override
    public ResourceLocation getPluginUid() {
        return Gensokyou.id("jei_plugin");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        IGuiHelper guiHelper = registration.getJeiHelpers().getGuiHelper();
        List<RitualRecipeCategory> categories = new ArrayList<>();
        for (ResourceLocation patternId : DEDICATED_TABS) {
            categories.add(new RitualRecipeCategory(guiHelper,
                    RitualRecipeCategory.typeFor(patternId), patternId, tabIcon(guiHelper, patternId)));
        }
        categories.add(new RitualRecipeCategory(guiHelper, FALLBACK_TYPE, null,
                tabIcon(guiHelper, null)));
        registration.addRecipeCategories(categories.toArray(new RitualRecipeCategory[0]));
    }

    private static IDrawable tabIcon(IGuiHelper guiHelper, @Nullable ResourceLocation patternId) {
        ItemStack icon = RitualBehaviors.ZAOHUA.equals(patternId)
                ? new ItemStack(ModItems.SPELLCARD_STAR.get())
                : RitualBehaviors.KAMI_NO_MEGUMI.equals(patternId)
                        ? new ItemStack(ModItems.SPIRIT_CORES.get(0).get())
                        : new ItemStack(ModItems.RITUAL_CORE_ITEM.get());
        return guiHelper.createDrawableIngredient(VanillaTypes.ITEM_STACK, icon);
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
        syncFromLoader(RitualRecipeLoader.all());
    }

    /** 配方集变化即逐页签增删卡片（稳态零操作，由 {@link JeiClientSync} 轮询触发）。 */
    static void syncFromLoader(List<RitualRecipe> current) {
        IJeiRuntime rt = runtime;
        if (rt == null) {
            return;
        }
        Map<ResourceLocation, RitualRecipeCardWrapper> previous = syncedRecipes;
        Map<ResourceLocation, RitualRecipeCardWrapper> desired = new LinkedHashMap<>();
        for (RitualRecipe recipe : current) {
            RitualRecipeCardWrapper old = previous.get(recipe.id());
            desired.put(recipe.id(), old != null && old.definition().equals(recipe)
                    ? old : new RitualRecipeCardWrapper(recipe));
        }
        if (desired.size() == previous.size() && desired.equals(previous)) {
            return; // 稳态：无任何页签/卡片变化
        }
        IRecipeManager manager = rt.getRecipeManager();
        Map<RecipeType<RitualRecipeCardWrapper>, List<RitualRecipeCardWrapper>> keep =
                new HashMap<>();
        Map<RecipeType<RitualRecipeCardWrapper>, List<RitualRecipeCardWrapper>> drop =
                new HashMap<>();
        for (RitualRecipeCardWrapper card : desired.values()) {
            RitualRecipeCardWrapper old = previous.get(card.definition().id());
            if (old == null || old != card) {
                keep.computeIfAbsent(typeOf(card), k -> new ArrayList<>()).add(card);
            }
        }
        for (RitualRecipeCardWrapper old : previous.values()) {
            if (desired.get(old.definition().id()) != old) {
                drop.computeIfAbsent(typeOf(old), k -> new ArrayList<>()).add(old);
            }
        }
        for (Map.Entry<RecipeType<RitualRecipeCardWrapper>, List<RitualRecipeCardWrapper>> entry
                : keep.entrySet()) {
            manager.unhideRecipeCategory(entry.getKey());
            manager.addRecipes(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<RecipeType<RitualRecipeCardWrapper>, List<RitualRecipeCardWrapper>> entry
                : drop.entrySet()) {
            manager.hideRecipes(entry.getKey(), entry.getValue());
        }
        // 空页签不占侧栏（兜底页签常态下即此状态）
        for (RecipeType<RitualRecipeCardWrapper> type : allTabTypes()) {
            boolean empty = desired.values().stream().noneMatch(card -> typeOf(card) == type);
            if (empty) {
                manager.hideRecipeCategory(type);
            }
        }
        syncedRecipes = Map.copyOf(desired);
    }

    private static RecipeType<RitualRecipeCardWrapper> typeOf(RitualRecipeCardWrapper card) {
        ResourceLocation patternId = card.definition().patternId();
        return RitualRecipeCategory.typeFor(hasDedicatedTab(patternId) ? patternId : null);
    }

    private static List<RecipeType<RitualRecipeCardWrapper>> allTabTypes() {
        List<RecipeType<RitualRecipeCardWrapper>> types = new ArrayList<>();
        for (ResourceLocation patternId : DEDICATED_TABS) {
            types.add(RitualRecipeCategory.typeFor(patternId));
        }
        types.add(FALLBACK_TYPE);
        return types;
    }
}
