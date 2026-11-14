package com.bitsson.gensokyou.audio;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.audio.SoundIds;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * cue 注册表 + 资源一致性校验。
 *
 * <p>参数表的「唯一真值」是 {@link AudioCue}；本类只做两件事：
 * <ol>
 *   <li>按事件 id 反查 cue（供数据驱动的消费方使用）；</li>
 *   <li>加载期做四方一致性校验（cue ↔ sounds.json ↔ ModSounds ↔ .ogg）。</li>
 * </ol>
 *
 * <p>校验刻意<b>返回问题列表而不是抛异常</b>：缺资源是表现层问题，
 * 对齐既有 requirement「MUST NOT 打断」。调用方（{@code AudioBootstrap}）
 * 逐条记 ERROR；测试直接断言列表为空。
 */
public final class AudioCues {

    private static final String SOUNDS_JSON = "/assets/gensokyou/sounds.json";
    private static final String SOUND_ROOT = "/assets/gensokyou/sounds/";

    private static final List<AudioCue> ALL = List.of(AudioCue.values());
    private static final Map<ResourceLocation, AudioCue> BY_ID = index();

    private AudioCues() {
    }

    public static List<AudioCue> all() {
        return ALL;
    }

    public static Optional<AudioCue> byId(ResourceLocation eventId) {
        return eventId == null ? Optional.empty() : Optional.ofNullable(BY_ID.get(eventId));
    }

    public static Optional<AudioCue> byId(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            return Optional.empty();
        }
        return byId(ResourceLocation.tryParse(eventId));
    }

    private static Map<ResourceLocation, AudioCue> index() {
        Map<ResourceLocation, AudioCue> byId = new LinkedHashMap<>();
        for (AudioCue cue : ALL) {
            AudioCue previous = byId.put(cue.eventId(), cue);
            if (previous != null) {
                throw new IllegalStateException("duplicate cue event id: " + cue.eventId());
            }
        }
        return Collections.unmodifiableMap(byId);
    }

    /**
     * 四方一致性校验。
     *
     * @return 问题列表；空 = 一致。任何问题都不抛异常。
     */
    public static List<String> validate() {
        List<String> problems = new ArrayList<>();
        JsonObject json;
        try {
            json = readSoundsJson();
        } catch (IOException | RuntimeException failure) {
            problems.add("sounds.json 不可读: " + failure.getMessage());
            return problems;
        }

        Set<String> declared = new LinkedHashSet<>();
        for (AudioCue cue : ALL) {
            String key = cue.eventId().getPath();
            declared.add(key);
            validateEntry(cue, key, json.get(key), problems);
        }

        Set<String> music = new LinkedHashSet<>(SoundIds.MUSIC_NAMES);
        for (String key : json.keySet()) {
            if (!declared.contains(key) && !music.contains(key)) {
                problems.add("sounds.json 存在孤儿条目（注册表未声明）: " + key);
            }
        }

        Set<String> registered = new LinkedHashSet<>(SoundIds.NAMES);
        if (!registered.equals(declared)) {
            Set<String> missing = new LinkedHashSet<>(declared);
            missing.removeAll(registered);
            Set<String> extra = new LinkedHashSet<>(registered);
            extra.removeAll(declared);
            if (!missing.isEmpty()) {
                problems.add("ModSounds 未注册以下 cue 事件: " + missing);
            }
            if (!extra.isEmpty()) {
                problems.add("ModSounds 注册了注册表未声明的声音事件: " + extra);
            }
        }
        return problems;
    }

    private static void validateEntry(AudioCue cue, String key, JsonElement element,
                                      List<String> problems) {
        if (element == null || !element.isJsonObject()) {
            problems.add("缺少 sounds.json 条目: " + key);
            return;
        }
        JsonElement sounds = element.getAsJsonObject().get("sounds");
        if (sounds == null || !sounds.isJsonArray() || sounds.getAsJsonArray().isEmpty()) {
            problems.add("sounds.json 条目至少需要一个 variant: " + key);
            return;
        }
        JsonElement subtitle = element.getAsJsonObject().get("subtitle");
        if (subtitle == null || subtitle.isJsonNull() || subtitle.getAsString().isBlank()) {
            problems.add("sounds.json 条目缺少 subtitle: " + key);
        } else if (!subtitle.getAsString().equals(cue.subtitleKey())) {
            problems.add("subtitle 与注册表不一致: " + key + " -> " + subtitle.getAsString()
                    + "（期望 " + cue.subtitleKey() + "）");
        }
        JsonArray variants = sounds.getAsJsonArray();
        for (JsonElement variant : variants) {
            String name = variantName(variant);
            if (name == null || name.isBlank()) {
                problems.add("sounds.json 条目含无名 variant: " + key);
                continue;
            }
            ResourceLocation location = ResourceLocation.tryParse(
                    name.indexOf(':') >= 0 ? name : Gensokyou.MODID + ":" + name);
            if (location == null) {
                problems.add("sounds.json 条目含非法 variant: " + key + " -> " + name);
                continue;
            }
            if (!location.getNamespace().equals(Gensokyou.MODID)) {
                // 允许引用原版/其它模组的文件；那是它的资源，不在本次校验范围。
                continue;
            }
            String path = SOUND_ROOT + location.getPath() + ".ogg";
            if (AudioCues.class.getResource(path) == null) {
                problems.add("缺少音频文件（cue " + cue.eventId() + "）: " + path);
            }
        }
    }

    private static String variantName(JsonElement variant) {
        if (variant.isJsonPrimitive()) {
            return variant.getAsString();
        }
        if (variant.isJsonObject()) {
            JsonElement name = variant.getAsJsonObject().get("name");
            return name == null || name.isJsonNull() ? null : name.getAsString();
        }
        return null;
    }

    private static JsonObject readSoundsJson() throws IOException {
        try (InputStream in = AudioCues.class.getResourceAsStream(SOUNDS_JSON)) {
            if (in == null) {
                throw new IOException("classpath 上不存在 " + SOUNDS_JSON);
            }
            try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(reader).getAsJsonObject();
            }
        }
    }
}
