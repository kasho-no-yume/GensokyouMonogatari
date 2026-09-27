package com.bitsson.gensokyou.ritual;

import com.bitsson.gensokyou.support.MinecraftTestBootstrap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RitualPatternPredicateTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftTestBootstrap.ensureStarted();
    }

    @Test
    void filledCauldronsMatchCauldronPredicate() {
        RitualPattern.Predicate predicate = exact(Blocks.CAULDRON);

        assertTrue(predicate.test(Blocks.CAULDRON.defaultBlockState()));
        assertTrue(predicate.test(Blocks.LAVA_CAULDRON.defaultBlockState()));
        for (Block cauldron : List.of(Blocks.WATER_CAULDRON, Blocks.POWDER_SNOW_CAULDRON)) {
            for (int level = 1; level <= 3; level++) {
                assertTrue(predicate.test(cauldron.defaultBlockState()
                        .setValue(LayeredCauldronBlock.LEVEL, level)));
            }
        }
    }

    @Test
    void cauldronPredicateRejectsOtherBlocks() {
        assertFalse(exact(Blocks.CAULDRON).test(Blocks.IRON_BLOCK.defaultBlockState()));
    }

    @Test
    void otherExactPredicatesRemainStrict() {
        RitualPattern.Predicate predicate = exact(Blocks.STONE);

        assertTrue(predicate.test(Blocks.STONE.defaultBlockState()));
        assertFalse(predicate.test(Blocks.ANDESITE.defaultBlockState()));
    }

    private static RitualPattern.Predicate exact(Block block) {
        return new RitualPattern.Predicate(RitualPattern.Kind.EXACT, block, null);
    }
}
