package com.bitsson.gensokyou.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WeatheringCopper;

import java.util.List;

/** Processing rules shared by the devices and the JEI recipe cards. */
public final class SpiritTreatmentRecipes {
    private SpiritTreatmentRecipes() {}

    public record Oxidation(Block input, Block output, int minimumMode, long cost) {}

    public record Freezing(Block source, Block output, int mode, long cost) {}

    public static Oxidation oxidation(Block input) {
        if (!(input instanceof WeatheringCopper copper)) {
            return null;
        }
        Block next = WeatheringCopper.getNext(input).orElse(null);
        return next == null ? null : new Oxidation(input, next, copper.getAge().ordinal() + 1, 50);
    }

    public static final List<Freezing> FREEZING = List.of(
            new Freezing(Blocks.WATER, Blocks.ICE, 1, 100),
            new Freezing(Blocks.LAVA, Blocks.MAGMA_BLOCK, 1, 150),
            new Freezing(Blocks.WATER, Blocks.PACKED_ICE, 2, 900),
            new Freezing(Blocks.LAVA, Blocks.OBSIDIAN, 2, 500),
            new Freezing(Blocks.WATER, Blocks.BLUE_ICE, 3, 8100),
            new Freezing(Blocks.LAVA, Blocks.CRYING_OBSIDIAN, 3, 6000));

    public static Freezing freezing(Block source, int mode) {
        for (Freezing recipe : FREEZING) {
            if (recipe.source() == source && recipe.mode() == mode) {
                return recipe;
            }
        }
        return null;
    }
}
