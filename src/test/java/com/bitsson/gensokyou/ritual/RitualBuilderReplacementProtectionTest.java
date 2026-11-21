package com.bitsson.gensokyou.ritual;

import com.bitsson.gensokyou.support.MinecraftTestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RitualBuilderReplacementProtectionTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftTestBootstrap.ensureStarted();
    }

    @Test
    void preservesVanillaProtectedBlocks() {
        assertTrue(isProtected(Blocks.BEDROCK));
        assertTrue(isProtected(Blocks.OBSIDIAN));
        assertTrue(isProtected(Blocks.REINFORCED_DEEPSLATE));
        assertFalse(isProtected(Blocks.STONE));
    }

    @Test
    void honorsImmovableBlockFlagWithoutProtectingOrdinaryContainersOrPlants() {
        assertTrue(isProtected(Blocks.GRINDSTONE));
        assertFalse(isProtected(Blocks.CHEST));
        assertFalse(isProtected(Blocks.DANDELION));
    }

    private static boolean isProtected(Block block) {
        return RitualBuilderPlacement.isProtectedFromReplacement(
                EmptyBlockGetter.INSTANCE, BlockPos.ZERO, block.defaultBlockState());
    }
}
