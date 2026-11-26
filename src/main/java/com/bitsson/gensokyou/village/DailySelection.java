package com.bitsson.gensokyou.village;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/** Stable selection over an already sorted candidate list, independent of clocks and global RNG. */
public final class DailySelection {
    public static long seed(long worldSeed, UUID player, long day, String service) {
        long value = worldSeed ^ player.getMostSignificantBits() ^ Long.rotateLeft(player.getLeastSignificantBits(), 23);
        value ^= day * 0x9E3779B97F4A7C15L ^ service.hashCode();
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        return (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
    }
    public static <T> List<T> pick(List<T> candidates, int count, long seed) {
        var shuffled = new ArrayList<>(candidates);
        Collections.shuffle(shuffled, new Random(seed));
        return List.copyOf(shuffled.subList(0, Math.min(Math.max(0, count), shuffled.size())));
    }
    private DailySelection() {}
}
