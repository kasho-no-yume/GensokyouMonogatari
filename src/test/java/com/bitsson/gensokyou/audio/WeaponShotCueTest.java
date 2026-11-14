package com.bitsson.gensokyou.audio;

import com.bitsson.gensokyou.item.weapon.DanmakuKind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeaponShotCueTest {

    @Test
    void mapsEveryCoreKindToItsCue() {
        assertEquals(WeaponShotCue.NORMAL, WeaponShotCue.forKind(DanmakuKind.SPHERE, false, 0));
        assertEquals(WeaponShotCue.FAST, WeaponShotCue.forKind(DanmakuKind.CONE, false, 0));
        assertEquals(WeaponShotCue.KNIFE, WeaponShotCue.forKind(DanmakuKind.KNIFE, false, 0));
        assertEquals(WeaponShotCue.NORMAL, WeaponShotCue.forKind(DanmakuKind.TALISMAN, false, 0));
    }

    @Test
    void splitsShortPulseFromSustainedLaser() {
        assertEquals(WeaponShotCue.LASER, WeaponShotCue.forKind(DanmakuKind.LASER, true, 0.5D));
        assertEquals(WeaponShotCue.LASER_BEAM, WeaponShotCue.forKind(DanmakuKind.LASER, true, 3.0D));
        assertFalse(WeaponShotCue.LASER.sustained());
        assertTrue(WeaponShotCue.LASER_BEAM.sustained());
        assertTrue(WeaponShotCue.LASER.laser());
        assertTrue(WeaponShotCue.LASER_BEAM.laser());
        assertFalse(WeaponShotCue.NORMAL.laser());
    }

    @Test
    void cueMappingIsStableAndRoundTripsThroughSyncId() {
        assertEquals(AudioCue.SHOT_NORMAL, WeaponShotCue.NORMAL.cue());
        assertEquals(AudioCue.SHOT_FAST, WeaponShotCue.FAST.cue());
        assertEquals(AudioCue.KNIFE_THROW, WeaponShotCue.KNIFE.cue());
        assertEquals(AudioCue.LASER_SHOOT, WeaponShotCue.LASER.cue());
        assertEquals(AudioCue.LASER_BEAM, WeaponShotCue.LASER_BEAM.cue());
        for (WeaponShotCue value : WeaponShotCue.values()) {
            assertEquals(value, WeaponShotCue.byId(value.id()));
        }
        assertNull(WeaponShotCue.byId(0));
        assertNull(WeaponShotCue.byId(99));
    }
}
