package com.bitsson.gensokyou.ritual.behavior;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class KanayamahikoSmeltSession {

    public static final int VERSION = 1;
    private static final String TAG_ROOT = "KanayamahikoSmeltSession";
    private static final String TAG_VERSION = "Version";
    private static final String TAG_NEXT_SEQUENCE = "NextSequence";
    private static final String TAG_JOBS = "Jobs";
    private static final String TAG_SEQUENCE = "Sequence";
    private static final String TAG_PRIMARY = "Primary";
    private static final String TAG_EXPECTED_PRIMARY = "ExpectedPrimary";
    private static final String TAG_RECIPE = "Recipe";
    private static final String TAG_SOURCE = "Source";
    private static final String TAG_RESULT = "Result";
    private static final String TAG_LEVEL = "Level";
    private static final String TAG_DURATION = "Duration";
    private static final String TAG_DRAIN = "Drain";
    private static final String TAG_BLOCK_ORE = "BlockOre";
    private static final String TAG_ELAPSED = "Elapsed";
    private static final String TAG_POWER_CARRY = "PowerCarry";
    private static final String TAG_STATE = "State";
    private static final String TAG_SUPPORTS = "Supports";
    private static final String TAG_POS = "Pos";
    private static final String TAG_EXPECTED = "Expected";

    private long nextSequence;
    private final Map<Long, Job> jobs = new LinkedHashMap<>();

    public enum State { WAITING, RUNNING, PAUSED }

    public enum Source { SPECIAL, SMELTING, BLASTING, SMOKING }

    public static final class Auxiliary {
        private final BlockPos pos;
        private final ItemStack expected;

        public Auxiliary(BlockPos pos, ItemStack expected) {
            this.pos = pos.immutable();
            this.expected = expected.copyWithCount(1);
        }

        public BlockPos pos() {
            return pos;
        }

        public ItemStack expected() {
            return expected.copy();
        }

        CompoundTag save(HolderLookup.Provider registries) {
            CompoundTag tag = new CompoundTag();
            tag.putLong(TAG_POS, pos.asLong());
            tag.put(TAG_EXPECTED, expected.save(registries));
            return tag;
        }

        static Auxiliary load(CompoundTag tag, HolderLookup.Provider registries) {
            if (!tag.contains(TAG_POS) || !tag.contains(TAG_EXPECTED)) {
                return null;
            }
            ItemStack expected = ItemStack.parseOptional(registries, tag.getCompound(TAG_EXPECTED));
            if (expected.isEmpty()) {
                return null;
            }
            return new Auxiliary(BlockPos.of(tag.getLong(TAG_POS)), expected);
        }
    }

    public static final class Job {
        private final long sequence;
        private final BlockPos primary;
        private final ItemStack expectedPrimary;
        private final ResourceLocation recipeId;
        private final Source source;
        private final ItemStack result;
        private final int lockedLevel;
        private final int durationTicks;
        private final long drainPerSecond;
        private final boolean blockOre;
        private final List<Auxiliary> supports = new ArrayList<>();
        private State state = State.WAITING;
        private int elapsedTicks;
        private long powerCarry;

        public Job(long sequence, BlockPos primary, ItemStack expectedPrimary,
                   ResourceLocation recipeId, Source source, ItemStack result,
                   int lockedLevel, int durationTicks, long drainPerSecond,
                   boolean blockOre) {
            this.sequence = sequence;
            this.primary = primary.immutable();
            this.expectedPrimary = expectedPrimary.copyWithCount(1);
            this.recipeId = recipeId;
            this.source = source;
            this.result = result.copy();
            this.lockedLevel = lockedLevel;
            this.durationTicks = Math.max(1, durationTicks);
            this.drainPerSecond = Math.max(0L, drainPerSecond);
            this.blockOre = blockOre;
        }

        public long sequence() {
            return sequence;
        }

        public BlockPos primary() {
            return primary;
        }

        public ItemStack expectedPrimary() {
            return expectedPrimary.copy();
        }

        public ResourceLocation recipeId() {
            return recipeId;
        }

        public Source source() {
            return source;
        }

        public ItemStack result() {
            return result.copy();
        }

        public int lockedLevel() {
            return lockedLevel;
        }

        public int durationTicks() {
            return durationTicks;
        }

        public long drainPerSecond() {
            return drainPerSecond;
        }

        public boolean blockOre() {
            return blockOre;
        }

        public State state() {
            return state;
        }

        public int elapsedTicks() {
            return elapsedTicks;
        }

        public long powerCarry() {
            return powerCarry;
        }

        public List<Auxiliary> supports() {
            return List.copyOf(supports);
        }

        public void setState(State state) {
            this.state = state;
        }

        public void setPowerCarry(long powerCarry) {
            this.powerCarry = Math.max(0L, powerCarry);
        }

        public void addPower(long amount) {
            powerCarry = Math.max(0L, powerCarry + amount);
        }

        public void advanceTick() {
            if (elapsedTicks < durationTicks) {
                elapsedTicks++;
            }
        }

        public void addSupport(BlockPos pos, ItemStack expected) {
            for (Auxiliary support : supports) {
                if (support.pos().equals(pos)) {
                    return;
                }
            }
            supports.add(new Auxiliary(pos, expected));
        }

        public boolean samePrimary(ItemStack stack) {
            return sameItem(stack, expectedPrimary);
        }

        public boolean sameSupport(Auxiliary support, ItemStack stack) {
            return sameItem(stack, support.expected());
        }

        public int remainingTicks() {
            return Math.max(0, durationTicks - elapsedTicks);
        }

        public double progress() {
            return Math.min(1D, (double) elapsedTicks / durationTicks);
        }

        CompoundTag save(HolderLookup.Provider registries) {
            CompoundTag tag = new CompoundTag();
            tag.putLong(TAG_SEQUENCE, sequence);
            tag.putLong(TAG_PRIMARY, primary.asLong());
            tag.put(TAG_EXPECTED_PRIMARY, expectedPrimary.save(registries));
            tag.putString(TAG_RECIPE, recipeId.toString());
            tag.putString(TAG_SOURCE, source.name());
            tag.put(TAG_RESULT, result.save(registries));
            tag.putInt(TAG_LEVEL, lockedLevel);
            tag.putInt(TAG_DURATION, durationTicks);
            tag.putLong(TAG_DRAIN, drainPerSecond);
            tag.putBoolean(TAG_BLOCK_ORE, blockOre);
            tag.putInt(TAG_ELAPSED, elapsedTicks);
            tag.putLong(TAG_POWER_CARRY, powerCarry);
            tag.putString(TAG_STATE, state.name());
            if (!supports.isEmpty()) {
                ListTag list = new ListTag();
                for (Auxiliary support : supports) {
                    list.add(support.save(registries));
                }
                tag.put(TAG_SUPPORTS, list);
            }
            return tag;
        }

        static Job load(CompoundTag tag, HolderLookup.Provider registries) {
            if (!tag.contains(TAG_PRIMARY) || !tag.contains(TAG_EXPECTED_PRIMARY)
                    || !tag.contains(TAG_RECIPE) || !tag.contains(TAG_RESULT)) {
                return null;
            }
            ResourceLocation recipe = ResourceLocation.tryParse(tag.getString(TAG_RECIPE));
            if (recipe == null) {
                return null;
            }
            Source source;
            try {
                source = Source.valueOf(tag.getString(TAG_SOURCE));
            } catch (IllegalArgumentException exception) {
                return null;
            }
            ItemStack expected = ItemStack.parseOptional(registries,
                    tag.getCompound(TAG_EXPECTED_PRIMARY));
            ItemStack result = ItemStack.parseOptional(registries,
                    tag.getCompound(TAG_RESULT));
            if (expected.isEmpty() || result.isEmpty()) {
                return null;
            }
            Job job = new Job(tag.getLong(TAG_SEQUENCE), BlockPos.of(tag.getLong(TAG_PRIMARY)),
                    expected, recipe, source, result, tag.getInt(TAG_LEVEL), tag.getInt(TAG_DURATION),
                    tag.getLong(TAG_DRAIN), tag.getBoolean(TAG_BLOCK_ORE));
            try {
                job.state = State.valueOf(tag.getString(TAG_STATE));
            } catch (IllegalArgumentException exception) {
                job.state = State.WAITING;
            }
            job.elapsedTicks = Math.max(0, Math.min(job.durationTicks, tag.getInt(TAG_ELAPSED)));
            job.powerCarry = Math.max(0L, tag.getLong(TAG_POWER_CARRY));
            for (var value : tag.getList(TAG_SUPPORTS, CompoundTag.TAG_COMPOUND)) {
                Auxiliary support = Auxiliary.load((CompoundTag) value, registries);
                if (support != null) {
                    job.supports.add(support);
                }
            }
            return job;
        }

        private static boolean sameItem(ItemStack actual, ItemStack expected) {
            return !actual.isEmpty() && ItemStack.isSameItemSameComponents(actual, expected);
        }
    }

    public List<Job> jobs() {
        return jobs.values().stream()
                .sorted(Comparator.comparingLong(Job::sequence))
                .toList();
    }

    public boolean contains(BlockPos primary) {
        return jobs.containsKey(primary.asLong());
    }

    public Job create(long sequence, BlockPos primary, ItemStack expectedPrimary,
                      ResourceLocation recipeId, Source source, ItemStack result,
                      int lockedLevel, int durationTicks, long drainPerSecond,
                      boolean blockOre) {
        Job job = new Job(sequence, primary, expectedPrimary, recipeId, source, result,
                lockedLevel, durationTicks, drainPerSecond, blockOre);
        jobs.put(primary.asLong(), job);
        return job;
    }

    public long allocateSequence() {
        return nextSequence++;
    }

    public void remove(Job job) {
        jobs.remove(job.primary().asLong(), job);
    }

    public void clear() {
        jobs.clear();
        nextSequence = 0L;
    }

    public void save(CompoundTag parent, HolderLookup.Provider registries) {
        if (jobs.isEmpty()) {
            return;
        }
        CompoundTag tag = new CompoundTag();
        tag.putInt(TAG_VERSION, VERSION);
        tag.putLong(TAG_NEXT_SEQUENCE, nextSequence);
        ListTag list = new ListTag();
        for (Job job : jobs()) {
            list.add(job.save(registries));
        }
        tag.put(TAG_JOBS, list);
        parent.put(TAG_ROOT, tag);
    }

    public void load(CompoundTag parent, HolderLookup.Provider registries) {
        clear();
        if (!parent.contains(TAG_ROOT)) {
            return;
        }
        CompoundTag tag = parent.getCompound(TAG_ROOT);
        if (tag.getInt(TAG_VERSION) > VERSION) {
            return;
        }
        nextSequence = Math.max(0L, tag.getLong(TAG_NEXT_SEQUENCE));
        for (var value : tag.getList(TAG_JOBS, CompoundTag.TAG_COMPOUND)) {
            Job job = Job.load((CompoundTag) value, registries);
            if (job != null) {
                jobs.put(job.primary().asLong(), job);
                nextSequence = Math.max(nextSequence, job.sequence() + 1L);
            }
        }
    }
}
