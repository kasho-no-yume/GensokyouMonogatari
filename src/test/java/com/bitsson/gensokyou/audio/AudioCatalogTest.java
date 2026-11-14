package com.bitsson.gensokyou.audio;

import com.bitsson.gensokyou.Gensokyou;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 注册表 ↔ {@code sounds.json} ↔ {@code ModSounds} ↔ {@code .ogg} 的四方一致性。
 *
 * <p>与 {@code AudioBootstrap} 的加载期校验走同一个 {@link AudioCues#validate()}：
 * 加载期把问题逐条记 ERROR（不打断游戏），这里把同一批问题变成 CI 的红。
 */
class AudioCatalogTest {

    @Test
    void catalogAndResourcesAreConsistent() {
        List<String> problems = AudioCues.validate();
        assertTrue(problems.isEmpty(),
                "音频注册表与资源不一致：\n  " + String.join("\n  ", problems));
    }

    @Test
    void everyCueIsAddressableById() {
        assertEquals(8, AudioCues.all().size(), "cue 数量变更时必须同步 sounds.json 与资源");
        for (AudioCue cue : AudioCues.all()) {
            assertEquals(Optional.of(cue), AudioCues.byId(cue.eventId()),
                    "byId 必须能反查 " + cue.eventId());
            assertEquals(Gensokyou.MODID, cue.eventId().getNamespace(),
                    "自制 cue 的事件 id 必须在本模组命名空间下");
            assertTrue(cue.eventId().getPath().equals(cue.eventId().getPath().toLowerCase()),
                    "事件 id 必须小写: " + cue.eventId());
        }
        assertEquals(Optional.empty(), AudioCues.byId("gensokyou:no_such_cue"));
        assertEquals(Optional.empty(), AudioCues.byId((String) null));
    }

    @Test
    void registeredSoundNamesMatchCueEventIds() {
        Set<String> registered = new LinkedHashSet<>(SoundIds.NAMES);
        Set<String> declared = AudioCues.all().stream()
                .map(cue -> cue.eventId().getPath())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        assertEquals(registered, declared,
                "SoundIds 的注册名与 AudioCues 的事件 id path 必须一一对应");
    }

    @Test
    void subtitleKeysExistInBothLanguages() throws IOException {
        Set<String> zh = langKeys("zh_cn.json");
        Set<String> en = langKeys("en_us.json");
        for (AudioCue cue : AudioCues.all()) {
            assertTrue(zh.contains(cue.subtitleKey()),
                    "zh_cn 缺少字幕键: " + cue.subtitleKey());
            assertTrue(en.contains(cue.subtitleKey()),
                    "en_us 缺少字幕键: " + cue.subtitleKey());
        }
    }

    private static Set<String> langKeys(String fileName) throws IOException {
        Path path = Path.of("src", "main", "resources", "assets", "gensokyou", "lang", fileName);
        assertTrue(Files.isRegularFile(path), "缺少语言文件: " + path);
        JsonObject json = JsonParser.parseString(
                Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
        return new LinkedHashSet<>(json.keySet());
    }

    @Test
    void everyCueDeclaresMixParametersAndPlaceholder() {
        for (AudioCue cue : AudioCues.all()) {
            assertTrue(cue.baseVolume() > 0.0F && cue.baseVolume() <= 1.0F,
                    cue.eventId() + " 的 baseVolume 必须在 (0,1]");
            assertTrue(cue.basePitch() > 0.0F, cue.eventId() + " 的 basePitch 必须为正");
            assertTrue(cue.pitchJitter() >= 0.0F, cue.eventId() + " 的 pitchJitter 不得为负");
            assertTrue(cue.minIntervalTicks() >= 0, cue.eventId() + " 的 minInterval 不得为负");
            assertTrue(cue.subtitleKey().startsWith("subtitles.gensokyou."),
                    cue.eventId() + " 的 subtitle 键必须在本模组命名空间下");
            assertEquals("minecraft", cue.placeholderId().getNamespace(),
                    cue.eventId() + " 的占位音效必须是原版事件（降级才可用）");
        }
    }
}
