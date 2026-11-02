package com.bitsson.gensokyou.balance;

import java.util.ArrayList;
import java.util.List;
import com.bitsson.gensokyou.spirit.SpiritPowerData;

/** Deterministic reconstruction of a missing legacy ledger without rerolling player totals. */
public final class GraceLedgerMigrationMath {
    private GraceLedgerMigrationMath() {
    }

    public static List<Float> distribute(float total, List<Double> standardIncrements) {
        if (!Float.isFinite(total) || total < 0F || standardIncrements.isEmpty()) {
            throw new IllegalArgumentException("Invalid legacy total or weights");
        }
        double weight = 0D;
        for (double increment : standardIncrements) {
            if (!Double.isFinite(increment) || increment < 0D) {
                throw new IllegalArgumentException("Invalid standard increment");
            }
            weight += increment;
        }
        if (!Double.isFinite(weight) || weight <= 0D) {
            throw new IllegalArgumentException("Standard weights must have a finite positive sum");
        }
        List<Float> result = new ArrayList<>();
        float assigned = 0F;
        for (int i = 0; i < standardIncrements.size(); i++) {
            float value = i == standardIncrements.size() - 1
                    ? Math.max(0F, total - assigned)
                    : Math.min(Math.max(0F, total - assigned),
                            (float) (total * standardIncrements.get(i) / weight));
            result.add(value);
            assigned += value;
        }
        return List.copyOf(result);
    }

    /** Reconstruct only missing history; an existing ledger is the authoritative saved record. */
    public static SpiritPowerData reconstruct(SpiritPowerData saved, List<Double> maxWeights,
                                               List<Double> powerWeights) {
        if (!saved.graceLedger().isEmpty()) {
            return saved;
        }
        int tiers = Math.min(SpiritPowerData.MAX_TIER, saved.temperLevel());
        if (tiers <= 0 || maxWeights.size() != tiers || powerWeights.size() != tiers) {
            throw new IllegalArgumentException("Migration weights must match saved tier");
        }
        List<Float> maxGains = distribute(saved.max(), maxWeights);
        List<Float> powerGains = distribute(saved.spiritDamage(), powerWeights);
        List<Float> ledger = new ArrayList<>();
        for (int tier = 0; tier < tiers; tier++) {
            ledger.add(maxGains.get(tier));
            ledger.add(powerGains.get(tier));
        }
        return saved.withRecomputedPool(ledger).withCurrent(saved.current());
    }
}
