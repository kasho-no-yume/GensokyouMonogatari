package com.bitsson.gensokyou.ritual.behavior;

import com.bitsson.gensokyou.ritual.RitualRenderState;
import com.bitsson.gensokyou.support.MinecraftTestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class KanayamahikoSmeltSessionTest {

    private static final ResourceLocation RECIPE = ResourceLocation.parse("minecraft:iron_ingot_from_smelting_iron_ore");

    @BeforeAll
    static void bootstrap() {
        MinecraftTestBootstrap.ensureStarted();
    }

    private static KanayamahikoSmeltSession.Job createJob(KanayamahikoSmeltSession session,
                                                           long sequence, BlockPos pos) {
        return session.create(sequence, pos, new ItemStack(Items.IRON_ORE), RECIPE,
                KanayamahikoSmeltSession.Source.SMELTING, new ItemStack(Items.IRON_INGOT),
                1, 80, 800L, true);
    }

    /**
     * 回归：新任务默认 WAITING，而 WAITING 的唯一语义是"专属规则任务在等灵炭"。
     *
     * <p>若普通炉具配方任务以 WAITING 落进灵炭绑定通道，会因"查不到专属规则"
     * 被当 tick 删除（表现为"台面有料却永远没有任务"），故此处钉死该默认值，
     * 提醒创建路径必须显式转 RUNNING。
     */
    @Test
    void newJobStartsWaitingAndNeedsExplicitTransition() {
        KanayamahikoSmeltSession session = new KanayamahikoSmeltSession();
        KanayamahikoSmeltSession.Job job = createJob(session, 0L, new BlockPos(0, 0, 0));
        assertEquals(KanayamahikoSmeltSession.State.WAITING, job.state());
        job.setState(KanayamahikoSmeltSession.State.RUNNING);
        assertEquals(KanayamahikoSmeltSession.State.RUNNING, job.state());
    }

    /** 只有专属规则需要绑定灵炭；三类原版烹饪配方不需要辅料。 */
    @Test
    void onlySpecialRulesNeedCharcoal() {
        assertTrue(KanayamahikoSmelting.needsCharcoal(KanayamahikoSmeltSession.Source.SPECIAL));
        assertFalse(KanayamahikoSmelting.needsCharcoal(KanayamahikoSmeltSession.Source.SMELTING));
        assertFalse(KanayamahikoSmelting.needsCharcoal(KanayamahikoSmeltSession.Source.BLASTING));
        assertFalse(KanayamahikoSmelting.needsCharcoal(KanayamahikoSmeltSession.Source.SMOKING));
    }

    /** 只有 RUNNING 才算在烧：等灵炭与缺灵力暂停都不点亮火柱。 */
    @Test
    void burnMaskOnlyLightsRunningJobs() {
        BlockPos running = new BlockPos(1, 0, 0);
        BlockPos waiting = new BlockPos(2, 0, 0);
        BlockPos paused = new BlockPos(3, 0, 0);
        List<BlockPos> pedestals = List.of(running, waiting, paused);

        KanayamahikoSmeltSession session = new KanayamahikoSmeltSession();
        KanayamahikoSmeltSession.Job runningJob = session.create(0L, running,
                new ItemStack(Items.IRON_ORE),
                ResourceLocation.parse("minecraft:a"), KanayamahikoSmeltSession.Source.SMELTING,
                new ItemStack(Items.IRON_INGOT), 0, 160, 200L, true);
        runningJob.setState(KanayamahikoSmeltSession.State.RUNNING);
        session.create(1L, waiting, new ItemStack(Items.IRON_ORE),
                ResourceLocation.parse("minecraft:b"), KanayamahikoSmeltSession.Source.SPECIAL,
                new ItemStack(Items.IRON_INGOT), 0, 160, 200L, true);
        KanayamahikoSmeltSession.Job pausedJob = session.create(2L, paused,
                new ItemStack(Items.IRON_ORE),
                ResourceLocation.parse("minecraft:c"), KanayamahikoSmeltSession.Source.SMELTING,
                new ItemStack(Items.IRON_INGOT), 0, 160, 200L, true);
        pausedJob.setState(KanayamahikoSmeltSession.State.PAUSED);

        long mask = KanayamahikoSmelting.renderBurnMask(pedestals, session);
        RitualRenderState state = new RitualRenderState(
                RitualRenderState.KIND_KANAYAMAHIKO, true, 0, 0, 8, 0,
                pedestals.stream().mapToLong(BlockPos::asLong).toArray(), 0, mask);
        assertTrue(state.forgeBurning());
        assertTrue(state.forgePedestalBurning(0), "running pedestal must burn");
        assertFalse(state.forgePedestalBurning(1), "waiting-for-charcoal must not burn");
        assertFalse(state.forgePedestalBurning(2), "power-paused must not burn");
    }

    @Test
    void jobKeepsIndependentProgressAndSupportBindings() {        KanayamahikoSmeltSession session = new KanayamahikoSmeltSession();
        KanayamahikoSmeltSession.Job job = createJob(session, 4L, new BlockPos(2, 0, 3));
        job.addSupport(new BlockPos(3, 0, 3), new ItemStack(Items.CHARCOAL));
        job.setState(KanayamahikoSmeltSession.State.RUNNING);
        job.advanceTick();
        job.setPowerCarry(125L);

        assertEquals(79, job.remainingTicks());
        assertEquals(1, job.supports().size());
        assertTrue(job.samePrimary(new ItemStack(Items.IRON_ORE)));
        assertTrue(job.sameSupport(job.supports().getFirst(), new ItemStack(Items.CHARCOAL)));
    }

    @Test
    void nbtRoundTripRestoresJobsByPhysicalPosition() {
        KanayamahikoSmeltSession session = new KanayamahikoSmeltSession();
        KanayamahikoSmeltSession.Job job = createJob(session, 7L, new BlockPos(-2, 1, 5));
        job.addSupport(new BlockPos(-1, 1, 5), new ItemStack(Items.CHARCOAL));
        job.setState(KanayamahikoSmeltSession.State.PAUSED);
        job.advanceTick();
        job.setPowerCarry(777L);
        CompoundTag tag = new CompoundTag();
        session.save(tag, RegistryAccess.EMPTY);

        KanayamahikoSmeltSession restored = new KanayamahikoSmeltSession();
        restored.load(tag, RegistryAccess.EMPTY);
        assertEquals(1, restored.jobs().size());
        KanayamahikoSmeltSession.Job loaded = restored.jobs().getFirst();
        assertEquals(job.primary(), loaded.primary());
        assertEquals(job.recipeId(), loaded.recipeId());
        assertEquals(job.source(), loaded.source());
        assertEquals(job.lockedLevel(), loaded.lockedLevel());
        assertEquals(job.elapsedTicks(), loaded.elapsedTicks());
        assertEquals(job.powerCarry(), loaded.powerCarry());
        assertEquals(job.supports().size(), loaded.supports().size());
        assertEquals(job.supports().getFirst().pos(), loaded.supports().getFirst().pos());
    }

    @Test
    void sequenceContinuesAfterReload() {
        KanayamahikoSmeltSession session = new KanayamahikoSmeltSession();
        createJob(session, session.allocateSequence(), new BlockPos(0, 0, 1));
        createJob(session, session.allocateSequence(), new BlockPos(0, 0, 2));
        CompoundTag tag = new CompoundTag();
        session.save(tag, RegistryAccess.EMPTY);
        KanayamahikoSmeltSession restored = new KanayamahikoSmeltSession();
        restored.load(tag, RegistryAccess.EMPTY);
        assertEquals(2L, restored.allocateSequence());
    }

    @Test
    void resultMultiplierOnlyAppliesToMarkedBlockOre() {
        assertEquals(2, KanayamahikoSmelting.multipliedResult(
                new ItemStack(Items.IRON_INGOT), true).getCount());
        assertEquals(1, KanayamahikoSmelting.multipliedResult(
                new ItemStack(Items.IRON_INGOT), false).getCount());
    }

    /**
     * 任务行长度契约：可见区只放物品名（带图标 + 进度条），剩余秒下沉 tooltip。
     *
     * <p>带图标又带进度条的行文本只剩 50px，"名称 · 剩余 x.xs" 放不下会换行撑高，
     * 故按长度标准拆成"短名可见 + 长文本悬浮"。
     */
    @Test
    void guiLineCarriesIconProgressAndRemainingTime() {
        assumeTrue(MinecraftTestBootstrap.hasDefaultLanguage(),
                "vanilla lang file is required to resolve hover names");
        KanayamahikoSmeltSession session = new KanayamahikoSmeltSession();
        KanayamahikoSmeltSession.Job job = createJob(session, 0L, new BlockPos(0, 0, 0));
        job.setState(KanayamahikoSmeltSession.State.RUNNING);
        job.advanceTick();
        var line = KanayamahikoSmelting.jobLine(new ItemStack(Items.IRON_ORE), job, 0);
        assertEquals("minecraft:iron_ore", line.iconItemId());
        assertEquals("gui.gensokyou.ritual.item_name", line.textKey());
        assertEquals(1.0F / 80.0F, line.progress(), 0.0001F);
        // 剩余秒在 tooltip 里，且带上物品名
        assertTrue(line.tipped());
        assertEquals("gui.gensokyou.ritual.kanayamahiko.job", line.tipKey());
        assertEquals("4.0s", line.tipArgs()[1]);
    }

    /** 等灵炭的任务同样短名可见，缺料数在 tooltip。 */
    @Test
    void waitingJobLineKeepsShortVisibleLabel() {
        assumeTrue(MinecraftTestBootstrap.hasDefaultLanguage(),
                "vanilla lang file is required to resolve hover names");
        KanayamahikoSmeltSession session = new KanayamahikoSmeltSession();
        KanayamahikoSmeltSession.Job job = createJob(session, 0L, new BlockPos(0, 0, 0));
        var line = KanayamahikoSmelting.jobLine(new ItemStack(Items.IRON_ORE), job, 2);
        assertEquals("gui.gensokyou.ritual.item_name", line.textKey());
        assertEquals(-1F, line.progress(), 0.0001F);
        assertEquals("gui.gensokyou.ritual.kanayamahiko.waiting", line.tipKey());
        assertEquals("2", line.tipArgs()[1]);
    }
}
