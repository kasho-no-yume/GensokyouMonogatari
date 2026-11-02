package com.bitsson.gensokyou.balance;

import org.junit.jupiter.api.Test;

import java.util.List;
import com.bitsson.gensokyou.spirit.SpiritPowerData;

import static org.junit.jupiter.api.Assertions.*;

class GraceLedgerMigrationMathTest {
    @Test
    void missingLedgerReconstructionPreservesTotalsAndIsDeterministic() {
        var weights = List.of(1D, 9D, 80D, 683D, 5750D);
        var first = GraceLedgerMigrationMath.distribute(6523F, weights);
        assertEquals(List.of(1F, 9F, 80F, 683F, 5750F), first);
        assertEquals(first, GraceLedgerMigrationMath.distribute(6523F, weights));
        assertEquals(6523F, sum(first));
    }

    @Test
    void unusualLegacyTotalsAreNotReplacedWithStandardTotals() {
        var result = GraceLedgerMigrationMath.distribute(173.25F, List.of(1000D, 9000D, 90000D));
        assertEquals(173.25F, sum(result), .00002F);
        assertTrue(result.stream().allMatch(v -> v >= 0F));
        assertEquals(List.of(0F, 0F), GraceLedgerMigrationMath.distribute(0F, List.of(1D, 9D)));
    }

    @Test
    void malformedInputDoesNotProduceRandomReplacement() {
        assertThrows(IllegalArgumentException.class,
                () -> GraceLedgerMigrationMath.distribute(Float.NaN, List.of(1D)));
        assertThrows(IllegalArgumentException.class,
                () -> GraceLedgerMigrationMath.distribute(1F, List.of(0D)));
    }

    @Test
    void reconstructionPreservesSavedPoolBuffersAndSecondPassIdentity() {
        var saved = new SpiritPowerData(75F, 173.25F, 3, .75F, 24.5F,
                List.of(), .25F, true);
        var result = GraceLedgerMigrationMath.reconstruct(saved, List.of(1000D, 9000D, 90000D),
                List.of(1D, 9D, 80D));
        assertEquals(saved.max(), result.max(), .00002F);
        assertEquals(saved.spiritDamage(), result.spiritDamage(), .00002F);
        assertEquals(saved.current(), result.current());
        assertEquals(saved.regenBuffer(), result.regenBuffer());
        assertEquals(saved.flightBuffer(), result.flightBuffer());
        assertEquals(saved.flightInertia(), result.flightInertia());
        assertEquals(6, result.graceLedger().size());
        assertSame(result, GraceLedgerMigrationMath.reconstruct(result, List.of(), List.of()));
    }

    @Test
    void oldSmallLedgerIsNotMistakenForPermissionToReroll() {
        var saved = new SpiritPowerData(100F, 200F, 1, 0F, 7F,
                List.of(200F, 7F), 0F, false);
        assertSame(saved, GraceLedgerMigrationMath.reconstruct(saved, List.of(1000D), List.of(1D)));
    }

    private static float sum(List<Float> values) {
        float result = 0F;
        for (float value : values) result += value;
        return result;
    }
}
