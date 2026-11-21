package com.bitsson.gensokyou.testkit.scenario;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.registry.ModBlocks;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.List;

/** Opt-in smoke test against JEI's actual usage and output indexes in a dev client. */
@JeiPlugin
public final class SpiritTreatmentJeiTest implements IModPlugin {
    @Override
    public ResourceLocation getPluginUid() {
        return Gensokyou.id("spirit_treatment_test");
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        if (!Boolean.getBoolean("gensokyou.testSpiritTreatmentJei")) {
            return;
        }
        try {
            verify(runtime, "spirit_oxidizer", ModBlocks.SPIRIT_OXIDIZER.get(),
                    List.of(Blocks.EXPOSED_COPPER, Blocks.WEATHERED_COPPER_DOOR, Blocks.OXIDIZED_CUT_COPPER_STAIRS));
            verify(runtime, "spirit_freezer", ModBlocks.SPIRIT_FREEZER.get(),
                    List.of(Blocks.ICE, Blocks.PACKED_ICE, Blocks.BLUE_ICE, Blocks.MAGMA_BLOCK,
                            Blocks.OBSIDIAN, Blocks.CRYING_OBSIDIAN));
            Gensokyou.LOGGER.info("SPIRIT_TREATMENT_JEI PASS device usage and product lookup");
        } catch (RuntimeException | AssertionError failure) {
            Gensokyou.LOGGER.error("SPIRIT_TREATMENT_JEI FAIL", failure);
        } finally {
            Minecraft.getInstance().stop();
        }
    }

    private static void verify(IJeiRuntime runtime, String id, Block device, List<Block> products) {
        var manager = runtime.getRecipeManager();
        var type = manager.getRecipeType(Gensokyou.id(id)).orElseThrow();
        var focuses = runtime.getJeiHelpers().getFocusFactory();
        var usage = focuses.createFocus(RecipeIngredientRole.CATALYST, VanillaTypes.ITEM_STACK, new ItemStack(device));
        boolean categoryForDevice = manager.createRecipeCategoryLookup().limitFocus(List.of(usage)).get()
                .anyMatch(category -> category.getRecipeType().equals(type));
        if (!categoryForDevice) {
            throw new AssertionError("Device usage did not find category " + id);
        }
        for (Block product : products) {
            verifyProduct(runtime, type, product);
        }
    }

    private static <T> void verifyProduct(IJeiRuntime runtime, RecipeType<T> type, Block product) {
        var manager = runtime.getRecipeManager();
        var focuses = runtime.getJeiHelpers().getFocusFactory();
        var focus = focuses.createFocus(RecipeIngredientRole.OUTPUT, VanillaTypes.ITEM_STACK, new ItemStack(product));
        var matches = manager.createRecipeLookup(type).limitFocus(List.of(focus)).get().toList();
        if (matches.size() != 1) {
            throw new AssertionError("Expected exactly one processing recipe for " + product + ", got " + matches.size());
        }
        manager.createRecipeLayoutDrawable(manager.getRecipeCategory(type), matches.getFirst(),
                focuses.createFocusGroup(List.of(focus))).orElseThrow();
    }
}
