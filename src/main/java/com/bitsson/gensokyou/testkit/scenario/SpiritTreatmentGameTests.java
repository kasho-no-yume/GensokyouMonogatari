package com.bitsson.gensokyou.testkit.scenario;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.block.entity.SpiritTreatmentBlockEntity;
import com.bitsson.gensokyou.registry.ModBlocks;
import com.bitsson.gensokyou.registry.ModItems;
import com.bitsson.gensokyou.spirit.SpiritCoreItem;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.StairsShape;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

@GameTestHolder(Gensokyou.MODID)
@PrefixGameTestTemplate(false)
public final class SpiritTreatmentGameTests {
    private static final String TEMPLATE = "test/spirit_treatment";
    private static final BlockPos DEVICE = new BlockPos(3, 1, 3);
    private static final BlockPos NW = DEVICE.north().west();
    private static final BlockPos NE = DEVICE.north().east();
    private static final BlockPos SW = DEVICE.south().west();
    private static final BlockPos SE = DEVICE.south().east();

    private SpiritTreatmentGameTests() {}

    @GameTest(template = TEMPLATE)
    public static void coreInteractionAndReload(GameTestHelper test) {
        var device = placeDevice(test, ModBlocks.SPIRIT_FREEZER.get());
        var level = test.getLevel();
        var position = test.absolutePos(DEVICE);
        var actionBar = new AtomicReference<Component>();
        var player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "treatment_test")) {
            @Override
            public void displayClientMessage(Component message, boolean overlay) {
                if (overlay) {
                    actionBar.set(message);
                }
            }
        };
        player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
        var hit = new BlockHitResult(Vec3.atCenterOf(position), Direction.UP, position, false);
        var main = InteractionHand.MAIN_HAND;
        var off = InteractionHand.OFF_HAND;
        test.assertValueEqual(device.mode(), 1, "default mode");
        test.assertFalse(click(player, main, hit), "there is nothing to take from an empty device");
        player.setItemInHand(main, new ItemStack(Items.STICK));
        test.assertFalse(click(player, main, hit), "non-core main-hand item is passed through");
        test.assertTrue(device.core().isEmpty(), "non-core item must not enter the slot");

        player.setItemInHand(main, ItemStack.EMPTY);
        player.setItemInHand(off, chargedCore(1643));
        test.assertFalse(click(player, main, hit), "empty main hand yields to offhand core");
        test.assertTrue(click(player, off, hit), "offhand core inserts");
        test.assertTrue(player.getOffhandItem().isEmpty(), "survival insertion spends one core");
        player.setItemInHand(main, chargedCore(57));
        click(player, main, hit);
        test.assertValueEqual(stored(device), 1643L, "full slot preserves the first core");
        test.assertValueEqual(SpiritCoreItem.getStored(player.getMainHandItem()), 57L, "second core is held");

        player.setItemInHand(main, new ItemStack(Items.STICK));
        player.setShiftKeyDown(true);
        test.assertFalse(click(player, main, hit), "sneaking with a non-core item passes through");
        test.assertTrue(click(player, off, hit), "empty offhand can shift-click despite held main-hand item");
        player.setItemInHand(main, ItemStack.EMPTY);
        player.setItemInHand(off, new ItemStack(Items.STICK));
        test.assertTrue(click(player, main, hit), "empty main hand can shift-click despite held offhand item");
        test.assertValueEqual(device.mode(), 3, "sneak clicks cycle modes without removing core");
        test.assertTrue(actionBar.get() != null, "mode switch sends an action-bar message");

        var state = device.getBlockState();
        var saved = device.saveWithFullMetadata(level.registryAccess());
        test.setBlock(DEVICE, Blocks.AIR);
        test.killAllEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class);
        test.setBlock(DEVICE, state);
        var restored = (SpiritTreatmentBlockEntity) BlockEntity.loadStatic(position, state, saved, level.registryAccess());
        test.assertTrue(restored != null, "saved block entity can be reconstructed");
        level.setBlockEntity(restored);
        test.assertValueEqual(restored.mode(), 3, "saved mode");
        test.assertValueEqual(stored(restored), 1643L, "saved remainder");

        player.setShiftKeyDown(false);
        test.assertTrue(click(player, main, hit), "ordinary empty click takes the core with stick in offhand");
        test.assertTrue(restored.core().isEmpty(), "taking clears the only slot");
        var returnedCore = player.getMainHandItem();
        test.assertValueEqual(SpiritCoreItem.getStored(returnedCore), 1643L, "taking preserves the remainder");
        player.setShiftKeyDown(true);
        player.setItemInHand(off, ItemStack.EMPTY);
        test.assertTrue(click(player, off, hit), "empty shift-click wraps mode");
        test.assertValueEqual(restored.mode(), 1, "mode wraps to 1");
        player.setShiftKeyDown(false);
        click(player, main, hit);
        test.destroyBlock(DEVICE);
        var drops = test.getEntities(EntityType.ITEM, DEVICE, 1);
        test.assertValueEqual(drops.size(), 1, "device releases exactly one core");
        test.assertValueEqual(SpiritCoreItem.getStored(drops.getFirst().getItem()), 1643L, "dropped core remainder");
        test.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 150)
    public static void oxidationRingAndCadence(GameTestHelper test) {
        var device = placeDevice(test, ModBlocks.SPIRIT_OXIDIZER.get());
        var stairs = Blocks.CUT_COPPER_STAIRS.defaultBlockState()
                .setValue(StairBlock.FACING, Direction.WEST).setValue(StairBlock.SHAPE, StairsShape.STRAIGHT);
        var door = Blocks.COPPER_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.EAST);
        test.setBlock(NW, stairs);
        test.setBlock(SE.below(), Blocks.STONE);
        test.setBlock(SE, door);
        test.setBlock(SE.above(), door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        test.setBlock(DEVICE.east(), Blocks.WAXED_COPPER_BLOCK);
        test.setBlock(DEVICE.west(), Blocks.OXIDIZED_COPPER);
        test.setBlock(DEVICE.north(2), Blocks.COPPER_BLOCK);
        test.setBlock(DEVICE.above(), Blocks.COPPER_BLOCK);
        test.setBlock(DEVICE.below(), Blocks.COPPER_BLOCK);
        device.cycleMode();
        device.insertCore(chargedCore(249), false);

        test.runAfterDelay(20, () -> {
            test.assertBlockPresent(Blocks.EXPOSED_CUT_COPPER_STAIRS, NW);
            test.assertBlockPresent(Blocks.COPPER_DOOR, SE);
            test.assertValueEqual(stored(device), 199L, "one step per 20 ticks");
        });
        test.runAfterDelay(40, () -> {
            test.assertBlockPresent(Blocks.EXPOSED_CUT_COPPER_STAIRS, NW);
            test.assertBlockPresent(Blocks.EXPOSED_COPPER_DOOR, SE);
            test.assertBlockPresent(Blocks.EXPOSED_COPPER_DOOR, SE.above());
            test.assertValueEqual(stored(device), 149L, "round robin and one charge for both door halves");
        });
        test.runAfterDelay(100, () -> {
            test.assertBlockPresent(Blocks.WEATHERED_CUT_COPPER_STAIRS, NW);
            test.assertBlockProperty(NW, StairBlock.FACING, Direction.WEST);
            test.assertBlockPresent(Blocks.WEATHERED_COPPER_DOOR, SE);
            test.assertBlockProperty(SE.above(), DoorBlock.HALF, DoubleBlockHalf.UPPER);
            test.assertBlockPresent(Blocks.WAXED_COPPER_BLOCK, DEVICE.east());
            test.assertBlockPresent(Blocks.OXIDIZED_COPPER, DEVICE.west());
            test.assertBlockPresent(Blocks.COPPER_BLOCK, DEVICE.north(2));
            test.assertBlockPresent(Blocks.COPPER_BLOCK, DEVICE.above());
            test.assertBlockPresent(Blocks.COPPER_BLOCK, DEVICE.below());
            test.assertValueEqual(stored(device), 49L, "idle work leaves balance untouched");
            device.cycleMode();
        });
        test.runAfterDelay(120, () -> {
            test.assertBlockPresent(Blocks.WEATHERED_CUT_COPPER_STAIRS, NW);
            test.assertValueEqual(stored(device), 49L, "insufficient balance cannot process an eligible target");
            device.removeCore();
            test.setBlock(NW, Blocks.COPPER_BLOCK);
        });
        test.runAfterDelay(141, () -> {
            test.assertBlockPresent(Blocks.COPPER_BLOCK, NW);
            test.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 160)
    public static void freezingModesAndAffordableTarget(GameTestHelper test) {
        var device = placeDevice(test, ModBlocks.SPIRIT_FREEZER.get());
        device.insertCore(chargedCore(15850), false);
        setSources(test);
        test.runAfterDelay(40, () -> {
            assertProducts(test, device, Blocks.ICE, Blocks.MAGMA_BLOCK, 15600);
            device.cycleMode();
            setSources(test);
        });
        test.runAfterDelay(80, () -> {
            assertProducts(test, device, Blocks.PACKED_ICE, Blocks.OBSIDIAN, 14200);
            test.setBlock(DEVICE.east(), Blocks.PACKED_ICE);
            test.setBlock(NE, Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.WATERLOGGED, true));
            test.setBlock(SW, Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, 1));
            device.cycleMode();
            setSources(test);
            // Keep the flowing-water fixture stable while leaving normal fluid rules enabled elsewhere.
            var flowing = test.absolutePos(SW);
            test.getLevel().getFluidTicks().clearArea(new BoundingBox(flowing));
            test.getLevel().getFluidTicks().clearArea(new BoundingBox(test.absolutePos(NE)));
        });
        test.runAfterDelay(120, () -> {
            assertProducts(test, device, Blocks.BLUE_ICE, Blocks.CRYING_OBSIDIAN, 100);
            test.assertBlockPresent(Blocks.PACKED_ICE, DEVICE.east());
            test.assertBlockProperty(NE, SlabBlock.WATERLOGGED, true);
            test.assertBlockProperty(SW, LiquidBlock.LEVEL, 1);
            device.removeCore();
            device.insertCore(chargedCore(6000), false);
            setSources(test);
        });
        test.runAfterDelay(140, () -> {
            assertProducts(test, device, Blocks.WATER, Blocks.CRYING_OBSIDIAN, 0);
            test.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 190)
    public static void tierZeroFullCoreYield(GameTestHelper test) {
        BlockPos lavaDevice = new BlockPos(5, 1, 5);
        BlockPos waterSource = DEVICE.north();
        BlockPos lavaSource = lavaDevice.east();
        var water = placeDevice(test, ModBlocks.SPIRIT_FREEZER.get());
        test.setBlock(lavaDevice, ModBlocks.SPIRIT_FREEZER.get());
        SpiritTreatmentBlockEntity lava = test.getBlockEntity(lavaDevice);
        for (var device : new SpiritTreatmentBlockEntity[]{water, lava}) {
            device.cycleMode();
            device.cycleMode();
            device.insertCore(chargedCore(Long.MAX_VALUE), false);
            test.assertValueEqual(stored(device), 50_000L, "full tier-zero core");
        }
        test.setBlock(waterSource.below(), Blocks.STONE);
        test.setBlock(lavaSource.below(), Blocks.STONE);
        stationarySource(test, waterSource, Blocks.WATER);
        stationarySource(test, lavaSource, Blocks.LAVA);

        for (int round = 1; round <= 9; round++) {
            int elapsedRounds = round;
            test.runAfterDelay(round * 20L, () -> {
                test.assertBlockPresent(elapsedRounds <= 6 ? Blocks.BLUE_ICE : Blocks.WATER, waterSource);
                test.assertBlockPresent(elapsedRounds <= 8 ? Blocks.CRYING_OBSIDIAN : Blocks.LAVA, lavaSource);
                test.assertValueEqual(stored(water), 50_000L - Math.min(elapsedRounds, 6) * 8100L, "blue ice yield");
                test.assertValueEqual(stored(lava), 50_000L - Math.min(elapsedRounds, 8) * 6000L, "crying obsidian yield");
                if (elapsedRounds <= 6) {
                    stationarySource(test, waterSource, Blocks.WATER);
                }
                if (elapsedRounds <= 8) {
                    stationarySource(test, lavaSource, Blocks.LAVA);
                }
                if (elapsedRounds == 9) {
                    test.succeed();
                }
            });
        }
    }

    private static SpiritTreatmentBlockEntity placeDevice(GameTestHelper test, Block block) {
        test.setBlock(DEVICE, block);
        return test.getBlockEntity(DEVICE);
    }

    private static boolean click(FakePlayer player, InteractionHand hand, BlockHitResult hit) {
        return player.gameMode.useItemOn(player, player.level(), player.getItemInHand(hand), hand, hit).consumesAction();
    }

    private static ItemStack chargedCore(long charge) {
        var stack = new ItemStack(ModItems.SPIRIT_CORES.getFirst().get());
        SpiritCoreItem.receive(stack, charge);
        return stack;
    }

    private static long stored(SpiritTreatmentBlockEntity device) {
        return SpiritCoreItem.getStored(device.core());
    }

    private static void setSources(GameTestHelper test) {
        test.setBlock(NW.below(), Blocks.STONE);
        test.setBlock(SE.below(), Blocks.STONE);
        stationarySource(test, NW, Blocks.WATER);
        stationarySource(test, SE, Blocks.LAVA);
    }

    private static void stationarySource(GameTestHelper test, BlockPos pos, Block source) {
        test.setBlock(pos, source);
        // Provide stationary isolated sources so their normal spreading cannot create extra candidates.
        test.getLevel().getFluidTicks().clearArea(new BoundingBox(test.absolutePos(pos)));
    }

    private static void assertProducts(GameTestHelper test, SpiritTreatmentBlockEntity device,
                                       Block waterProduct, Block lavaProduct, long balance) {
        test.assertBlockPresent(waterProduct, NW);
        test.assertBlockPresent(lavaProduct, SE);
        test.assertValueEqual(stored(device), balance, "frozen products' cost");
    }
}
