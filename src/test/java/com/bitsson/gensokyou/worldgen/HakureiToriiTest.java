package com.bitsson.gensokyou.worldgen;

import com.bitsson.gensokyou.support.MinecraftTestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HakureiToriiTest {
    private static final int X = 2900;
    private static final int BASE_Y = 80;
    private static final int CENTER_Z = 128;

    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftTestBootstrap.ensureStarted();
    }

    @Test
    void stylesAgeFromLacqueredFormalToNarrowTimber() {
        assertEquals(HakureiToriiGenerator.Style.FORMAL, HakureiToriiGenerator.styleFor(2900));
        assertEquals(HakureiToriiGenerator.Style.FORMAL, HakureiToriiGenerator.styleFor(2500));
        assertEquals(HakureiToriiGenerator.Style.WEATHERED, HakureiToriiGenerator.styleFor(2499));
        assertEquals(HakureiToriiGenerator.Style.WEATHERED, HakureiToriiGenerator.styleFor(1500));
        assertEquals(HakureiToriiGenerator.Style.RURAL, HakureiToriiGenerator.styleFor(1499));

        HakureiToriiGenerator.Style formal = HakureiToriiGenerator.Style.FORMAL;
        HakureiToriiGenerator.Style weathered = HakureiToriiGenerator.Style.WEATHERED;
        HakureiToriiGenerator.Style rural = HakureiToriiGenerator.Style.RURAL;
        assertTrue(formal.tipHalfWidth() > weathered.tipHalfWidth());
        assertTrue(weathered.tipHalfWidth() > rural.tipHalfWidth());

        Map<BlockPos, BlockState> formalBlocks = HakureiToriiGenerator.build(X, BASE_Y, CENTER_Z);
        assertEquals(2, count(formalBlocks, Blocks.LANTERN), "Only the maintained shrine-side gates carry lanterns");
        assertEquals(1, count(formalBlocks, Blocks.OCHRE_FROGLIGHT), "Formal gakuzuka should have a warm paper panel");
        assertEquals(0, count(HakureiToriiGenerator.build(2260, BASE_Y, CENTER_Z), Blocks.LANTERN));
        assertEquals(0, count(HakureiToriiGenerator.build(420, BASE_Y, CENTER_Z), Blocks.LANTERN));
    }

    @Test
    void centralWalkwayKeepsAtLeastThreeBlocksOfHeadroom() {
        int clearance = HakureiToriiGenerator.NUKI_Y - HakureiToriiGenerator.POST_BOTTOM_Y;
        assertTrue(clearance >= 3, "Torii doorway must provide at least three air blocks");
        assertEquals(4, clearance, "Roadside torii should have a comfortable four-block doorway");
        for (HakureiToriiGenerator.Style style : HakureiToriiGenerator.Style.values()) {
            Map<BlockPos, BlockState> blocks = HakureiToriiGenerator.build(xFor(style), BASE_Y, CENTER_Z);
            for (int dy = HakureiToriiGenerator.POST_BOTTOM_Y; dy < HakureiToriiGenerator.NUKI_Y; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    assertFalse(blocks.containsKey(new BlockPos(xFor(style), BASE_Y + dy, CENTER_Z + dz)),
                            style + " blocks the central walkway at dy=" + dy + ", dz=" + dz);
                }
            }
            // The full space between the posts must stay open for at least the required three blocks.
            for (int dy = HakureiToriiGenerator.POST_BOTTOM_Y; dy <= 3; dy++) {
                for (int dz = -style.postHalfWidth() + 1; dz < style.postHalfWidth(); dz++) {
                    assertFalse(blocks.containsKey(new BlockPos(xFor(style), BASE_Y + dy, CENTER_Z + dz)),
                            style + " narrows the three-block-high doorway at dy=" + dy + ", dz=" + dz);
                }
            }
            for (int postDz : new int[]{-style.postHalfWidth(), style.postHalfWidth()}) {
                for (int dy = HakureiToriiGenerator.POST_BOTTOM_Y; dy < HakureiToriiGenerator.NUKI_Y; dy++) {
                    assertNotNull(blocks.get(new BlockPos(xFor(style), BASE_Y + dy, CENTER_Z + postDz)),
                            style + " post is missing below the nuki");
                }
            }
            assertNotNull(at(blocks, style, 0, HakureiToriiGenerator.NUKI_Y),
                    style + " nuki must cap the doorway at the tested clearance height");
        }
    }

    @Test
    void postFootingsAreWiderThanTheOneBlockShaftAndSitOnTheSurface() {
        for (HakureiToriiGenerator.Style style : HakureiToriiGenerator.Style.values()) {
            Map<BlockPos, BlockState> blocks = HakureiToriiGenerator.build(xFor(style), BASE_Y, CENTER_Z);
            for (int postDz : new int[]{-style.postHalfWidth(), style.postHalfWidth()}) {
                for (int dx = -1; dx <= 1; dx++) {
                    assertNotNull(blocks.get(new BlockPos(xFor(style) + dx, BASE_Y, CENTER_Z + postDz)),
                            style + " is missing its three-block-wide footing shoulder");
                }
                assertTrue(blocks.get(new BlockPos(xFor(style) + 1, BASE_Y + 1, CENTER_Z + postDz)).getBlock() instanceof SlabBlock,
                        style + " footing step should be a slab");
            }
            assertTrue(blocks.keySet().stream().noneMatch(pos -> pos.getY() < BASE_Y),
                    style + " pure geometry must not burrow into terrain; worldgen adds the foundation");
        }
    }

    @Test
    void roofHasContinuousBeamsAndRaisedTips() {
        for (HakureiToriiGenerator.Style style : HakureiToriiGenerator.Style.values()) {
            Map<BlockPos, BlockState> blocks = HakureiToriiGenerator.build(xFor(style), BASE_Y, CENTER_Z);
            for (int dz = -style.nukiHalfWidth(); dz <= style.nukiHalfWidth(); dz++) {
                assertNotNull(at(blocks, style, dz, HakureiToriiGenerator.NUKI_Y), style + " nuki has a gap at dz=" + dz);
            }
            for (int dz = -style.tipHalfWidth(); dz <= style.tipHalfWidth(); dz++) {
                assertNotNull(at(blocks, style, dz, HakureiToriiGenerator.KASAGI_Y), style + " kasagi has a gap at dz=" + dz);
            }
            assertNotNull(at(blocks, style, -style.tipHalfWidth(), HakureiToriiGenerator.TIP_CAP_Y), style + " north roof tip is not raised");
            assertNotNull(at(blocks, style, style.tipHalfWidth(), HakureiToriiGenerator.TIP_CAP_Y), style + " south roof tip is not raised");
            assertNull(at(blocks, style, 0, HakureiToriiGenerator.TIP_CAP_Y), style + " roof center should stay below the raised tips");

            BlockState northTip = at(blocks, style, -style.tipHalfWidth(), HakureiToriiGenerator.SHIMAKI_Y);
            BlockState southTip = at(blocks, style, style.tipHalfWidth(), HakureiToriiGenerator.SHIMAKI_Y);
            assertTrue(northTip.getBlock() instanceof StairBlock);
            assertTrue(southTip.getBlock() instanceof StairBlock);
            assertEquals(Half.TOP, northTip.getValue(StairBlock.HALF));
            assertEquals(Half.TOP, southTip.getValue(StairBlock.HALF));
            assertEquals(Direction.SOUTH, northTip.getValue(StairBlock.FACING));
            assertEquals(Direction.NORTH, southTip.getValue(StairBlock.FACING));
            assertEquals(SlabType.BOTTOM, at(blocks, style, -style.tipHalfWidth(), HakureiToriiGenerator.TIP_CAP_Y).getValue(SlabBlock.TYPE));
        }
    }

    @Test
    void ruralPostsRunVerticallyAndTimberRoofsSpanTheRoad() {
        Map<BlockPos, BlockState> ruralBlocks = HakureiToriiGenerator.build(420, BASE_Y, CENTER_Z);
        HakureiToriiGenerator.Style rural = HakureiToriiGenerator.Style.RURAL;
        assertEquals(Direction.Axis.Y,
                at(ruralBlocks, rural, -rural.postHalfWidth(), 2).getValue(RotatedPillarBlock.AXIS));
        for (HakureiToriiGenerator.Style style : new HakureiToriiGenerator.Style[]{
                HakureiToriiGenerator.Style.WEATHERED, HakureiToriiGenerator.Style.RURAL}) {
            Map<BlockPos, BlockState> blocks = HakureiToriiGenerator.build(xFor(style), BASE_Y, CENTER_Z);
            assertEquals(Direction.Axis.Z, at(blocks, style, 0, HakureiToriiGenerator.KASAGI_Y).getValue(RotatedPillarBlock.AXIS),
                    style + " roof axis");
        }
    }

    @Test
    void formalLanternsHangFromTheNukiInsteadOfFloatingUnsupported() {
        Map<BlockPos, BlockState> blocks = HakureiToriiGenerator.build(X, BASE_Y, CENTER_Z);
        int checked = 0;
        for (Map.Entry<BlockPos, BlockState> entry : blocks.entrySet()) {
            if (!entry.getValue().is(Blocks.LANTERN)) {
                continue;
            }
            assertTrue(entry.getValue().getValue(LanternBlock.HANGING), "Gate lantern must use its hanging state");
            assertTrue(blocks.get(entry.getKey().above()).is(Blocks.RED_CONCRETE),
                    "Gate lantern must be attached directly to the nuki");
            checked++;
        }
        assertEquals(2, checked);
    }

    @Test
    void translatingTheGateByOneChunkPreservesEverySlice() {
        for (HakureiToriiGenerator.Style style : HakureiToriiGenerator.Style.values()) {
            Map<BlockPos, BlockState> first = HakureiToriiGenerator.build(xFor(style), BASE_Y, 3);
            Map<BlockPos, BlockState> translated = new HashMap<>();
            HakureiToriiGenerator.build(xFor(style), BASE_Y, 19).forEach(
                    (pos, state) -> translated.put(pos.offset(0, 0, -16), state));
            assertEquals(first, translated, style + " geometry must not depend on chunk borders");
        }
    }

    private static int xFor(HakureiToriiGenerator.Style style) {
        return switch (style) {
            case FORMAL -> X;
            case WEATHERED -> 2260;
            case RURAL -> 420;
        };
    }

    private static BlockState at(Map<BlockPos, BlockState> blocks, HakureiToriiGenerator.Style style, int dz, int dy) {
        return blocks.get(new BlockPos(xFor(style), BASE_Y + dy, CENTER_Z + dz));
    }

    private static long count(Map<BlockPos, BlockState> blocks, net.minecraft.world.level.block.Block block) {
        return blocks.values().stream().filter(state -> state.is(block)).count();
    }
}
