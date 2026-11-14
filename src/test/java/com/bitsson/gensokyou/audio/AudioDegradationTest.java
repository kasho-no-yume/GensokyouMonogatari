package com.bitsson.gensokyou.audio;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AudioDegradationTest {

    private static final SoundEvent PLACEHOLDER =
            SoundEvent.createVariableRangeEvent(ResourceLocation.parse("test:placeholder"));

    @Test
    void missingTargetFallsBackToTheDeclaredPlaceholder() {
        List<String> logs = new ArrayList<>();
        AudioResolver resolver = new AudioResolver(
                id -> id.equals(AudioCue.SHOT_NORMAL.placeholderId())
                        ? Optional.of(PLACEHOLDER) : Optional.empty(),
                logs::add);

        assertSame(PLACEHOLDER, resolver.resolve(AudioCue.SHOT_NORMAL));
        assertTrue(logs.isEmpty(), "落到占位是正常降级，不应留日志");
    }

    @Test
    void targetWinsWhenBothAreAvailable() {
        SoundEvent target = SoundEvent.createVariableRangeEvent(
                ResourceLocation.parse("test:target"));
        AudioResolver resolver = new AudioResolver(
                id -> Optional.of(id.equals(AudioCue.KNIFE_THROW.eventId()) ? target : PLACEHOLDER),
                message -> {
                    throw new AssertionError("不应有降级日志: " + message);
                });
        assertSame(target, resolver.resolve(AudioCue.KNIFE_THROW));
    }

    @Test
    void bothMissingIsSilentAndLoggedExactlyOnce() {
        List<String> logs = new ArrayList<>();
        AudioResolver resolver = new AudioResolver(id -> Optional.empty(), logs::add);

        assertNull(resolver.resolve(AudioCue.BOSS_BURST));
        assertNull(resolver.resolve(AudioCue.BOSS_BURST));
        assertNull(resolver.resolve(AudioCue.BOSS_BURST));

        assertEquals(1, logs.size(), "同一 cue 的缺失只报告一次");
        assertTrue(logs.get(0).contains(AudioCue.BOSS_BURST.eventId().toString()),
                "日志必须包含 cue 名，便于诊断: " + logs.get(0));
    }

    @Test
    void everyCueDeclaresAVanillaPlaceholder() {
        for (AudioCue cue : AudioCues.all()) {
            assertEquals("minecraft", cue.placeholderId().getNamespace(),
                    cue.eventId() + " 的占位必须是原版事件");
        }
    }
}
