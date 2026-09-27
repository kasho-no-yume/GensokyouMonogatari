package com.bitsson.gensokyou.ritual.behavior;

import com.bitsson.gensokyou.config.GensokyouConfig;
import com.bitsson.gensokyou.ritual.RitualScaling;
import com.bitsson.gensokyou.ritual.harvest.HarvestContext;
import com.bitsson.gensokyou.ritual.harvest.HarvestProvider;
import com.bitsson.gensokyou.ritual.harvest.HarvestProviderRegistry;
import com.bitsson.gensokyou.ritual.harvest.HarvestProviders;
import com.bitsson.gensokyou.support.MinecraftTestBootstrap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.ItemStackMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HoujounoTeihouBehaviorTest {
    @BeforeAll
    static void bootstrap() {
        MinecraftTestBootstrap.ensureStarted();
        HarvestProviders.standardCrop(new ItemStack(Items.WHEAT_SEEDS));
    }

    @AfterEach
    void clearCustomProviders() {
        HarvestProviderRegistry.clearCustomRegistrations();
    }

    @Test
    void formulasUseConfiguredDefaultsAndSaturate() {
        long capacityBase = GensokyouConfig.HOUJOUNO_TEIHOU_BASE_CAPACITY.getDefault();
        long capacityMultiplier = GensokyouConfig.HOUJOUNO_TEIHOU_CAPACITY_MULTIPLIER.getDefault();
        long inRateBase = GensokyouConfig.HOUJOUNO_TEIHOU_BASE_IN_RATE_PER_SECOND.getDefault();
        long inRateMultiplier = GensokyouConfig.HOUJOUNO_TEIHOU_IN_RATE_MULTIPLIER.getDefault();
        long costBase = GensokyouConfig.HOUJOUNO_TEIHOU_BASE_COST_PER_PEDESTAL.getDefault();
        long costMultiplier = GensokyouConfig.HOUJOUNO_TEIHOU_COST_MULTIPLIER.getDefault();
        long sampleBase = GensokyouConfig.HOUJOUNO_TEIHOU_BASE_SAMPLE_COUNT.getDefault();
        long sampleMultiplier = GensokyouConfig.HOUJOUNO_TEIHOU_SAMPLE_COUNT_MULTIPLIER.getDefault();
        assertEquals(40_000L, capacityBase);
        assertEquals(12L, capacityMultiplier);
        assertEquals(40_000L, inRateBase);
        assertEquals(12L, inRateMultiplier);
        assertEquals(4_000L, costBase);
        assertEquals(4L, costMultiplier);
        assertEquals(1L, sampleBase);
        assertEquals(4L, sampleMultiplier);
        assertEquals(40_000L, HoujounoTeihouBehavior.capacity(0, capacityBase, capacityMultiplier));
        assertEquals(480_000L, HoujounoTeihouBehavior.capacity(1, capacityBase, capacityMultiplier));
        assertEquals(5_760_000L, HoujounoTeihouBehavior.capacity(2, capacityBase, capacityMultiplier));
        assertEquals(40_000L, HoujounoTeihouBehavior.inRate(0, inRateBase, inRateMultiplier));
        assertEquals(480_000L, HoujounoTeihouBehavior.inRate(1, inRateBase, inRateMultiplier));
        assertEquals(5_760_000L, HoujounoTeihouBehavior.inRate(2, inRateBase, inRateMultiplier));
        assertEquals(4_000L, HoujounoTeihouBehavior.costPerPedestal(0, costBase, costMultiplier));
        assertEquals(16_000L, HoujounoTeihouBehavior.costPerPedestal(1, costBase, costMultiplier));
        assertEquals(64_000L, HoujounoTeihouBehavior.costPerPedestal(2, costBase, costMultiplier));
        assertEquals(1L, HoujounoTeihouBehavior.samplesPerPedestal(0, sampleBase, sampleMultiplier));
        assertEquals(4L, HoujounoTeihouBehavior.samplesPerPedestal(1, sampleBase, sampleMultiplier));
        assertEquals(16L, HoujounoTeihouBehavior.samplesPerPedestal(2, sampleBase, sampleMultiplier));
        assertEquals(16_000L, HoujounoTeihouBehavior.totalCost(
                HoujounoTeihouBehavior.costPerPedestal(0, costBase, costMultiplier), 4L));
        assertEquals(128_000L, HoujounoTeihouBehavior.totalCost(
                HoujounoTeihouBehavior.costPerPedestal(1, costBase, costMultiplier), 8L));
        assertEquals(768_000L, HoujounoTeihouBehavior.totalCost(
                HoujounoTeihouBehavior.costPerPedestal(2, costBase, costMultiplier), 12L));
        assertEquals(Long.MAX_VALUE, RitualScaling.scale(Long.MAX_VALUE, 12L, 2));
        assertEquals(Long.MAX_VALUE, RitualScaling.saturatingMultiply(Long.MAX_VALUE, 12L));
        assertEquals(Long.MAX_VALUE, RitualScaling.saturatingAdd(Long.MAX_VALUE, 1L));
    }

    @Test
    void standardCropFixtureAcceptsSeedIdentityAndRejectsMatureItem() {
        assertEquals(Blocks.WHEAT, HarvestProviders.standardCrop(new ItemStack(Items.WHEAT_SEEDS)).orElseThrow());
        assertTrue(HarvestProviders.seedIdentityMatches(
                new ItemStack(Items.WHEAT_SEEDS), new ItemStack(Items.WHEAT_SEEDS)));
        assertFalse(HarvestProviders.seedIdentityMatches(
                new ItemStack(Items.WHEAT), new ItemStack(Items.WHEAT_SEEDS)));
        assertTrue(HarvestProviders.standardCrop(new ItemStack(Items.DIAMOND)).isEmpty());
    }

    @Test
    void allVanillaSpecialProvidersAreRegistered() {
        List<ItemStack> inputs = List.of(
                new ItemStack(Items.MELON_SEEDS),
                new ItemStack(Items.PUMPKIN_SEEDS),
                new ItemStack(Items.PITCHER_POD),
                new ItemStack(Items.NETHER_WART),
                new ItemStack(Items.SWEET_BERRIES),
                new ItemStack(Items.COCOA_BEANS),
                new ItemStack(Items.SUGAR_CANE),
                new ItemStack(Items.CACTUS));
        for (ItemStack input : inputs) {
            assertTrue(HarvestProviderRegistry.find(input).isPresent(), input.getItem().toString());
            assertTrue(HarvestProviderRegistry.find(input).orElseThrow().preflightSafe());
        }
        assertEquals(Items.MELON, sampleOne(Items.MELON_SEEDS).getItem());
        assertEquals(Items.PUMPKIN, sampleOne(Items.PUMPKIN_SEEDS).getItem());
        assertEquals(Items.SUGAR_CANE, sampleOne(Items.SUGAR_CANE).getItem());
        assertEquals(Items.CACTUS, sampleOne(Items.CACTUS).getItem());
    }

    @Test
    void explicitProviderOverridesAndCanBeUnregistered() {
        HarvestProvider provider = (context, input, output) -> output.offer(new ItemStack(Items.EMERALD));
        HarvestProviderRegistry.register(Items.WHEAT_SEEDS, provider);
        assertTrue(HarvestProviderRegistry.find(new ItemStack(Items.WHEAT_SEEDS)).isPresent());
        assertFalse(HarvestProviderRegistry.find(new ItemStack(Items.WHEAT_SEEDS)).orElseThrow().preflightSafe());
        assertEquals(Items.EMERALD, sampleOne(Items.WHEAT_SEEDS).getItem());
        HarvestProviderRegistry.unregister(Items.WHEAT_SEEDS);

        ResourceLocation diamondId = ResourceLocation.withDefaultNamespace("diamond");
        HarvestProviderRegistry.register(diamondId, provider);
        assertEquals(Items.EMERALD, sampleOne(Items.DIAMOND).getItem());
        HarvestProviderRegistry.unregister(diamondId);
        assertTrue(HarvestProviderRegistry.find(new ItemStack(Items.DIAMOND)).isEmpty());
        assertTrue(HarvestProviderRegistry.find(new ItemStack(Items.MELON_SEEDS)).isPresent());
    }

    @Test
    void independentSamplesAreIsolatedFromProviderFailure() {
        AtomicInteger calls = new AtomicInteger();
        Map<ItemStack, Long> totals = ItemStackMap.createTypeAndTagLinkedMap();
        boolean success = HoujounoTeihouBehavior.sampleInto(null,
                (context, input, output) -> {
                    calls.incrementAndGet();
                    output.offer(new ItemStack(Items.WHEAT));
                },
                new ItemStack(Items.WHEAT_SEEDS), 16L, totals, null);
        assertTrue(success);
        assertEquals(16, calls.get());
        assertEquals(16L, totals.values().iterator().next());

        AtomicReference<RuntimeException> failure = new AtomicReference<>();
        Map<ItemStack, Long> failedTotals = ItemStackMap.createTypeAndTagLinkedMap();
        boolean failed = HoujounoTeihouBehavior.sampleInto(null,
                (context, input, output) -> {
                    output.offer(new ItemStack(Items.CARROT));
                    throw new IllegalStateException("boom");
                },
                new ItemStack(Items.WHEAT_SEEDS), 4L, failedTotals, failure::set);
        assertFalse(failed);
        assertTrue(failedTotals.isEmpty());
        assertEquals("boom", failure.get().getMessage());
    }

    @Test
    void emptyLootStillCompletesTheUnit() {
        Map<ItemStack, Long> totals = ItemStackMap.createTypeAndTagLinkedMap();
        boolean success = HoujounoTeihouBehavior.sampleInto(null,
                (context, input, output) -> { },
                new ItemStack(Items.WHEAT_SEEDS), 4L, totals, null);
        assertTrue(success);
        assertTrue(totals.isEmpty());
    }

    @Test
    void aggregationPreservesComponentsAndSplitsStacks() {
        Map<ItemStack, Long> totals = ItemStackMap.createTypeAndTagLinkedMap();
        ItemStack named = new ItemStack(Items.WHEAT);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("A"));
        HoujounoTeihouBehavior.aggregate(totals, named);
        HoujounoTeihouBehavior.aggregate(totals, new ItemStack(Items.WHEAT));
        HoujounoTeihouBehavior.aggregate(totals, new ItemStack(Items.WHEAT));
        assertEquals(2, totals.size());

        Map<ItemStack, Long> diamonds = ItemStackMap.createTypeAndTagLinkedMap();
        HoujounoTeihouBehavior.aggregate(diamonds, new ItemStack(Items.DIAMOND, 64));
        HoujounoTeihouBehavior.aggregate(diamonds, new ItemStack(Items.DIAMOND, 64));
        HoujounoTeihouBehavior.aggregate(diamonds, new ItemStack(Items.DIAMOND, 2));
        List<ItemStack> split = HoujounoTeihouBehavior.splitAggregates(diamonds);
        assertEquals(3, split.size());
        assertEquals(List.of(64, 64, 2), split.stream().map(ItemStack::getCount).toList());
    }

    @Test
    void uiStateKeepsShortageEnabledAndDebugLineIsMachineReadable() {
        HoujounoTeihouBehavior.InputScan noSeeds = new HoujounoTeihouBehavior.InputScan(4, 0, 0, 0, 0);
        HoujounoTeihouBehavior.InputScan valid = new HoujounoTeihouBehavior.InputScan(4, 4, 4, 0, 0);
        assertEquals("gui.gensokyou.ritual.houjouno.no_seeds",
                HoujounoTeihouBehavior.stateKey(true, 0, noSeeds, true));
        assertEquals("gui.gensokyou.ritual.houjouno.no_power",
                HoujounoTeihouBehavior.stateKey(true, 0, valid, false));
        assertEquals("gui.gensokyou.ritual.houjouno.cooling",
                HoujounoTeihouBehavior.stateKey(true, 600, valid, false));
        String debug = HoujounoTeihouBehavior.debugLine(
                2, true, 0, valid, 64_000L, 768_000L, 16L, 192L, 1_000L, 5_760_000L, 5_760_000L);
        for (String field : List.of("enabled=true", "cooldown=0", "pedestals=4", "valid=4",
                "unitCost=64000", "totalCost=768000", "totalSamples=192",
                "stored=1000", "capacity=5760000", "inRate=5760000")) {
            assertTrue(debug.contains(field), field);
        }
    }

    @Test
    void tierTwoFullPedestalsPerformTwoHundredTwelveSamples() {
        AtomicInteger calls = new AtomicInteger();
        Map<ItemStack, Long> totals = ItemStackMap.createTypeAndTagLinkedMap();
        long start = System.nanoTime();
        for (int pedestal = 0; pedestal < 12; pedestal++) {
            assertTrue(HoujounoTeihouBehavior.sampleInto(null,
                    (context, input, output) -> {
                        calls.incrementAndGet();
                        output.offer(new ItemStack(Items.WHEAT));
                    },
                    new ItemStack(Items.WHEAT_SEEDS), 16L, totals, null));
        }
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000L;
        assertEquals(192, calls.get());
        assertEquals(1, totals.size());
        assertEquals(192L, totals.values().iterator().next());
        Map<ItemStack, Long> componentTotals = ItemStackMap.createTypeAndTagLinkedMap();
        for (int sample = 0; sample < 192; sample++) {
            ItemStack wheat = new ItemStack(Items.WHEAT);
            wheat.set(DataComponents.CUSTOM_NAME, Component.literal(sample % 2 == 0 ? "A" : "B"));
            HoujounoTeihouBehavior.aggregate(componentTotals, wheat);
        }
        assertEquals(2, componentTotals.size());
        assertEquals(List.of(96L, 96L), componentTotals.values().stream().sorted().toList());
        assertTrue(elapsedMillis < 5_000L, "192 direct provider samples took " + elapsedMillis + "ms");
    }

    private static ItemStack sampleOne(net.minecraft.world.item.Item input) {
        HarvestProvider provider = HarvestProviderRegistry.find(new ItemStack(input)).orElseThrow();
        List<ItemStack> outputs = new ArrayList<>();
        HarvestContext context = null;
        provider.sample(context, new ItemStack(input), outputs::add);
        return outputs.getFirst();
    }
}
