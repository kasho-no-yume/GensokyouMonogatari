package com.bitsson.gensokyou.ritual.behavior;

import com.bitsson.gensokyou.support.MinecraftTestBootstrap;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.BlastingRecipe;
import net.minecraft.world.item.crafting.CookingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.item.crafting.SmokingRecipe;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KanayamahikoScalingTest {

    @BeforeAll
    static void bootstrap() {
        MinecraftTestBootstrap.ensureStarted();
    }

    @Test
    void durationUsesBaseSecondsAndLevelDivisor() {
        assertEquals(160, KanayamahikoSmelting.durationTicks(0, 8, 2));
        assertEquals(80, KanayamahikoSmelting.durationTicks(1, 8, 2));
        assertEquals(40, KanayamahikoSmelting.durationTicks(2, 8, 2));
    }

    @Test
    void powerCapacityAndInputScaleByFour() {
        assertEquals(200L, KanayamahikoSmelting.scaled(200L, 4L, 0));
        assertEquals(800L, KanayamahikoSmelting.scaled(200L, 4L, 1));
        assertEquals(3200L, KanayamahikoSmelting.scaled(200L, 4L, 2));
        assertEquals(640000L, KanayamahikoSmelting.scaled(40000L, 4L, 2));
        assertEquals(64000L, KanayamahikoSmelting.scaled(4000L, 4L, 2));
    }

    @Test
    void supportedRecipePriorityIsStable() {
        assertEquals("smelting", KanayamahikoSmelting.firstSupported(
                Optional.of("smelting"), Optional.of("blasting"), Optional.of("smoking")).orElseThrow());
        assertEquals("blasting", KanayamahikoSmelting.firstSupported(
                Optional.empty(), Optional.of("blasting"), Optional.of("smoking")).orElseThrow());
        assertEquals("smoking", KanayamahikoSmelting.firstSupported(
                Optional.empty(), Optional.empty(), Optional.of("smoking")).orElseThrow());
    }

    @Test
    void changedRecipeResultInvalidatesLock() {
        var expected = new KanayamahikoSmelting.RecipeLock(
                ResourceLocation.parse("minecraft:test"), KanayamahikoSmeltSession.Source.SMELTING,
                new ItemStack(Items.IRON_INGOT));
        var changed = new KanayamahikoSmelting.RecipeLock(
                ResourceLocation.parse("minecraft:test"), KanayamahikoSmeltSession.Source.SMELTING,
                new ItemStack(Items.GOLD_INGOT));
        assertTrue(KanayamahikoSmelting.sameLock(expected, expected));
        assertFalse(KanayamahikoSmelting.sameLock(expected, changed));
    }

    /**
     * 原版标签型配方（{@code #minecraft:logs_that_burn} → 木炭）必须能被查到。
     *
     * <p>这是玩家最容易踩的一条：原木在原版炉里能烧成木炭，煅炉也必须能。
     */
    @Test
    void vanillaTagRecipeResolvesToCharcoal() {
        RecipeManager recipes = new RecipeManager(RegistryAccess.EMPTY);
        recipes.replaceRecipes(List.of(new RecipeHolder<>(
                ResourceLocation.parse("minecraft:charcoal_from_log"),
                new SmeltingRecipe("", CookingBookCategory.MISC,
                        Ingredient.of(Items.OAK_LOG), new ItemStack(Items.CHARCOAL), 0.1F, 200))));

        var lock = KanayamahikoSmelting.resolveFromRecipes(
                recipes, RegistryAccess.EMPTY, new ItemStack(Items.OAK_LOG));

        assertTrue(lock.isPresent(), "oak log must resolve to a smelting recipe");
        assertEquals(KanayamahikoSmeltSession.Source.SMELTING, lock.orElseThrow().source());
        assertEquals(Items.CHARCOAL, lock.orElseThrow().result().getItem());
        assertEquals(1, lock.orElseThrow().result().getCount());
    }

    /** 同一原木同时有熔炼/高炉/烟熏配方时，锁定结果必须来自 SMELTING。 */
    @Test
    void duplicateRecipePrefersSmeltingOverBlastingAndSmoking() {
        RecipeManager recipes = new RecipeManager(RegistryAccess.EMPTY);
        recipes.replaceRecipes(List.of(
                new RecipeHolder<>(ResourceLocation.parse("minecraft:probe_smoking"),
                        new SmokingRecipe("", CookingBookCategory.MISC,
                                Ingredient.of(Items.IRON_ORE), new ItemStack(Items.GOLD_NUGGET),
                                0.1F, 100)),
                new RecipeHolder<>(ResourceLocation.parse("minecraft:probe_blasting"),
                        new BlastingRecipe("", CookingBookCategory.MISC,
                                Ingredient.of(Items.IRON_ORE), new ItemStack(Items.IRON_NUGGET),
                                0.1F, 100)),
                new RecipeHolder<>(ResourceLocation.parse("minecraft:probe_smelting"),
                        new SmeltingRecipe("", CookingBookCategory.MISC,
                                Ingredient.of(Items.IRON_ORE), new ItemStack(Items.IRON_INGOT),
                                0.1F, 200))));

        var lock = KanayamahikoSmelting.resolveFromRecipes(
                recipes, RegistryAccess.EMPTY, new ItemStack(Items.IRON_ORE));

        assertEquals(KanayamahikoSmeltSession.Source.SMELTING, lock.orElseThrow().source());
        assertEquals(Items.IRON_INGOT, lock.orElseThrow().result().getItem());
    }

    /** 没有这三类配方时不得凭空造出结果（空栈与无配方都要落空）。 */
    @Test
    void unsupportedAndEmptyInputsResolveToNothing() {
        RecipeManager recipes = new RecipeManager(RegistryAccess.EMPTY);
        assertTrue(KanayamahikoSmelting.resolveFromRecipes(
                recipes, RegistryAccess.EMPTY, new ItemStack(Items.DIAMOND)).isEmpty());
        assertTrue(KanayamahikoSmelting.resolveFromRecipes(
                recipes, RegistryAccess.EMPTY, ItemStack.EMPTY).isEmpty());
    }
}
