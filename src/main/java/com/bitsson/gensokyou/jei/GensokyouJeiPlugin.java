package com.bitsson.gensokyou.jei;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.ritual.RitualPattern;
import com.bitsson.gensokyou.ritual.RitualPatternLoader;
import com.bitsson.gensokyou.ritual.RitualRecipeLoader;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@JeiPlugin
public class GensokyouJeiPlugin implements IModPlugin {

    private static volatile IJeiRuntime runtime;
    private static volatile Map<ResourceLocation, RitualPattern> syncedPatterns = Map.of();
    private static volatile Map<ResourceLocation, RitualRecipeWrapper> syncedWrappers = Map.of();
    private static volatile Map<ResourceLocation, RitualRecipeCardWrapper> syncedRecipes = Map.of();

    @Override
    public ResourceLocation getPluginUid() {
        return Gensokyou.id("jei_plugin");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new RitualCategory(registration.getJeiHelpers()));
        registration.addRecipeCategories(new RitualRecipeCategory(registration.getJeiHelpers()));
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
        syncFromLoader(RitualPatternLoader.all());
    }

    static void syncFromLoader(List<RitualPattern> current) {
        Map<ResourceLocation, RitualPattern> previousPatterns = syncedPatterns;
        if (previousPatterns.size() == current.size()) {
            boolean identical = true;
            for (RitualPattern pattern : current) {
                if (previousPatterns.get(pattern.id()) != pattern) {
                    identical = false;
                    break;
                }
            }
            if (identical) {
                return;
            }
        }

        IJeiRuntime rt = runtime;
        if (rt == null) {
            return;
        }

        Map<ResourceLocation, RitualPattern> desiredPatterns = new HashMap<>();
        Map<ResourceLocation, RitualRecipeWrapper> desiredWrappers = new HashMap<>();
        Map<ResourceLocation, RitualRecipeCardWrapper> desiredRecipes = new HashMap<>();
        for (RitualPattern pattern : current) {
            desiredPatterns.put(pattern.id(), pattern);
            desiredWrappers.put(pattern.id(), new RitualRecipeWrapper(pattern));
        }
        for (var recipe : RitualRecipeLoader.all()) {
            desiredRecipes.put(recipe.id(), new RitualRecipeCardWrapper(recipe));
        }

        IRecipeManager manager = rt.getRecipeManager();
        List<RitualRecipeWrapper> removed = new ArrayList<>();
        List<RitualRecipeWrapper> added = new ArrayList<>();
        for (Map.Entry<ResourceLocation, RitualRecipeWrapper> entry : syncedWrappers.entrySet()) {
            RitualPattern desiredPattern = desiredPatterns.get(entry.getKey());
            if (desiredPattern == null || desiredPattern != previousPatterns.get(entry.getKey())) {
                removed.add(entry.getValue());
            }
        }
        for (Map.Entry<ResourceLocation, RitualRecipeWrapper> entry : desiredWrappers.entrySet()) {
            RitualPattern previousPattern = previousPatterns.get(entry.getKey());
            if (previousPattern == null || previousPattern != desiredPatterns.get(entry.getKey())) {
                added.add(entry.getValue());
            }
        }
        if (!removed.isEmpty()) {
            manager.hideRecipes(RitualCategory.TYPE, removed);
        }
        if (!added.isEmpty()) {
            manager.addRecipes(RitualCategory.TYPE, added);
        }

        List<RitualRecipeCardWrapper> recipesRemoved = new ArrayList<>();
        List<RitualRecipeCardWrapper> recipesAdded = new ArrayList<>();
        for (Map.Entry<ResourceLocation, RitualRecipeCardWrapper> entry : syncedRecipes.entrySet()) {
            if (!desiredRecipes.containsKey(entry.getKey())) {
                recipesRemoved.add(entry.getValue());
            }
        }
        for (Map.Entry<ResourceLocation, RitualRecipeCardWrapper> entry : desiredRecipes.entrySet()) {
            if (!syncedRecipes.containsKey(entry.getKey())
                    || !entry.getValue().definition().equals(syncedRecipes.get(entry.getKey()).definition())) {
                recipesAdded.add(entry.getValue());
            }
        }
        if (!recipesRemoved.isEmpty()) {
            manager.hideRecipes(RitualRecipeCategory.TYPE, recipesRemoved);
        }
        if (!recipesAdded.isEmpty()) {
            manager.addRecipes(RitualRecipeCategory.TYPE, recipesAdded);
        }

        syncedPatterns = Map.copyOf(desiredPatterns);
        syncedWrappers = Map.copyOf(desiredWrappers);
        syncedRecipes = Map.copyOf(desiredRecipes);
    }
}
