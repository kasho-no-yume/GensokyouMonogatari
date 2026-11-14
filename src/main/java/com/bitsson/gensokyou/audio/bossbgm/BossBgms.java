package com.bitsson.gensokyou.audio.bossbgm;

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
 * BOSS 战 BGM 的<b>唯一真值表</b>：内置曲目、阶级缺省映射、BOSS 名册、测试实体豁免。
 *
 * <h2>两层映射</h2>
 * <pre>
 *   实体 id ──(名册)──▶ 事件 id ──(sounds.json)──▶ 音频文件
 *                        │
 *                        └─ 名册外：回落到 bgm/tier{n}
 * </pre>
 *
 * 每个名册内 BOSS 都有自己的事件 id，但缺省<b>指向与它阶级相同的音频文件</b>。
 * 于是「N 个事件 id → 5 个文件」不产生任何额外体积，而资源包仍可按单只 BOSS 替换：
 * 覆盖同名 `.ogg` 换整阶，`sounds.json` + {@code "replace": true} 换单只。
 *
 * <p><b>为什么不在运行时读配置</b>：BGM 是表现层的最低要求项，不应因为玩家没配置而静音；
 * 阶级映射是编译期事实，写成表即可被离线校验。
 */
public final class BossBgms {

    /** 名册条目：实体注册名（同时是实体 id 的 path）、实现类简名、阶级。 */
    public record BossEntry(String entityPath, String className, int tier) {
        public ResourceLocation entityId() {
            return Gensokyou.id(entityPath);
        }

        public ResourceLocation eventId() {
            return Gensokyou.id(eventPath());
        }

        public String eventPath() {
            return "bgm/boss/" + entityPath;
        }
    }

    /** 豁免条目：测试 / 调试实体，永不出声；理由必填（棘轮要求）。 */
    public record Excluded(String className, String reason) {
    }

    private static final String SOUNDS_JSON = "/assets/gensokyou/sounds.json";
    private static final String SOUND_ROOT = "/assets/gensokyou/sounds/";

    /** BOSS 名册（新增 BOSS 必须入册或在 {@link #EXCLUDED} 里写理由）。 */
    public static final List<BossEntry> ROSTER = List.of(
            new BossEntry("big_fairy", "BigFairyEntity", 1),
            new BossEntry("rumia", "RumiaEntity", 1),
            new BossEntry("yamame", "YamameEntity", 1),
            new BossEntry("yakumo_ran", "YakumoRanEntity", 2),
            new BossEntry("flandre", "FlandreEntity", 5),
            new BossEntry("fake_flandre", "FakeFlandreEntity", 5));

    /** 测试 / 调试实体：被召唤时 MUST NOT 触发 BGM。 */
    public static final List<Excluded> EXCLUDED = List.of(
            new Excluded("BloodBarProbeBoss", "血条造型探针，只用于验收咒符条外观"));

    private static final Map<ResourceLocation, BossEntry> BY_ENTITY = indexEntities();
    private static final Map<ResourceLocation, BgmTrack> BY_EVENT = indexEvents();
    private static final Map<String, BossEntry> BY_CLASS = indexClasses();
    private static final Set<String> EXCLUDED_CLASSES = excludedClasses();

    private BossBgms() {
    }

    /** 阶级缺省曲目（越界回落 1 阶）。 */
    public static BgmTrack forTier(int tier) {
        return BgmTrack.forTier(tier);
    }

    /** 名册内 BOSS 的曲目（按它自己的阶级，而不是事件 id 反查）。 */
    public static BgmTrack trackFor(ResourceLocation entityId) {
        BossEntry entry = BY_ENTITY.get(entityId);
        return entry == null ? null : BgmTrack.forTier(entry.tier());
    }

    /** 该实体是否属于「永不出声」的测试实体。 */
    public static boolean excluded(ResourceLocation entityId) {
        BossEntry entry = BY_ENTITY.get(entityId);
        if (entry != null) {
            return false;
        }
        // 豁免是按实现类维护的；实体 id 层面用「名册外 + 已知测试前缀」兜底。
        return entityId != null && EXCLUDED_ENTITY_PATHS.contains(entityId.getPath());
    }

    /**
     * 实体 → 实际播放的事件 id。
     *
     * <p>名册内用自己的事件 id（可被资源包单只替换）；名册外回落到阶级事件。
     * 返回的事件 id 一定在 {@link #musicEventPaths()} 内。
     */
    public static ResourceLocation eventFor(ResourceLocation entityId, int tier) {
        BossEntry entry = entityId == null ? null : BY_ENTITY.get(entityId);
        if (entry != null) {
            return entry.eventId();
        }
        return BgmTrack.forTier(tier).eventId();
    }

    /** 事件 id → 曲目（决定音量与淡变参数）。未知返回 {@code null}。 */
    public static BgmTrack trackForEvent(ResourceLocation eventId) {
        return eventId == null ? null : BY_EVENT.get(eventId);
    }

    /** 全部音乐事件 id 的 path（阶 5 条 + 名册每只一条）。 */
    public static List<String> musicEventPaths() {
        List<String> paths = new ArrayList<>();
        for (BgmTrack track : BgmTrack.values()) {
            paths.add(track.eventId().getPath());
        }
        for (BossEntry entry : ROSTER) {
            paths.add(entry.eventPath());
        }
        return List.copyOf(paths);
    }

    /** 名册内的实体 id 集合。 */
    public static Set<ResourceLocation> rosterEntityIds() {
        return Collections.unmodifiableSet(BY_ENTITY.keySet());
    }

    public static List<BossEntry> roster() {
        return ROSTER;
    }

    public static List<Excluded> excludedEntries() {
        return EXCLUDED;
    }

    /** 类简名是否已在名册或豁免表中（名册棘轮用）。 */
    public static boolean knownClass(String simpleName) {
        return BY_CLASS.containsKey(simpleName) || EXCLUDED_CLASSES.contains(simpleName);
    }

    /**
     * 目录 ↔ {@code sounds.json} ↔ 注册表 ↔ 音频文件 的一致性校验。
     *
     * @return 问题列表；空 = 一致。任何问题都不抛异常（对齐「MUST NOT 打断」）。
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

        List<String> declared = musicEventPaths();
        if (!SoundIds.MUSIC_NAMES.equals(declared)) {
            problems.add("SoundIds.MUSIC_NAMES 与 BossBgms.musicEventPaths() 不一致：注册名 "
                    + SoundIds.MUSIC_NAMES + " / 目录 " + declared);
        }
        if (new LinkedHashSet<>(declared).size() != declared.size()) {
            problems.add("音乐事件 id 存在重复: " + declared);
        }

        for (String key : declared) {
            JsonElement element = json.get(key);
            if (element == null || !element.isJsonObject()) {
                problems.add("缺少 sounds.json 音乐条目: " + key);
                continue;
            }
            JsonElement sounds = element.getAsJsonObject().get("sounds");
            if (sounds == null || !sounds.isJsonArray() || sounds.getAsJsonArray().isEmpty()) {
                problems.add("音乐条目至少需要一个 variant: " + key);
                continue;
            }
            JsonArray variants = sounds.getAsJsonArray();
            for (JsonElement variant : variants) {
                if (!variant.isJsonObject()) {
                    problems.add("音乐条目必须写成对象（需要 stream 标志）: " + key);
                    continue;
                }
                JsonObject sound = variant.getAsJsonObject();
                if (!sound.has("stream") || !sound.get("stream").getAsBoolean()) {
                    problems.add("音乐条目必须声明 \"stream\": true: " + key);
                }
                JsonElement name = sound.get("name");
                if (name == null || name.isJsonNull() || name.getAsString().isBlank()) {
                    problems.add("音乐条目含无名 variant: " + key);
                    continue;
                }
                ResourceLocation location = ResourceLocation.parse(
                        name.getAsString().indexOf(':') >= 0
                                ? name.getAsString()
                                : Gensokyou.MODID + ":" + name.getAsString());
                String path = SOUND_ROOT + location.getPath() + ".ogg";
                if (BossBgms.class.getResource(path) == null) {
                    problems.add("缺少音频文件（" + key + "）: " + path);
                }
            }
        }
        return problems;
    }

    private static JsonObject readSoundsJson() throws IOException {
        try (InputStream in = BossBgms.class.getResourceAsStream(SOUNDS_JSON)) {
            if (in == null) {
                throw new IOException("classpath 上不存在 " + SOUNDS_JSON);
            }
            try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(reader).getAsJsonObject();
            }
        }
    }

    private static Map<ResourceLocation, BossEntry> indexEntities() {
        Map<ResourceLocation, BossEntry> map = new LinkedHashMap<>();
        for (BossEntry entry : ROSTER) {
            BossEntry previous = map.put(entry.entityId(), entry);
            if (previous != null) {
                throw new IllegalStateException("名册里实体重复: " + entry.entityId());
            }
        }
        return Collections.unmodifiableMap(map);
    }

    private static Map<ResourceLocation, BgmTrack> indexEvents() {
        Map<ResourceLocation, BgmTrack> map = new LinkedHashMap<>();
        for (BgmTrack track : BgmTrack.values()) {
            map.put(track.eventId(), track);
        }
        for (BossEntry entry : ROSTER) {
            BgmTrack previous = map.put(entry.eventId(), BgmTrack.forTier(entry.tier()));
            if (previous != null) {
                throw new IllegalStateException("音乐事件 id 重复: " + entry.eventId());
            }
        }
        return Collections.unmodifiableMap(map);
    }

    private static Map<String, BossEntry> indexClasses() {
        Map<String, BossEntry> map = new LinkedHashMap<>();
        for (BossEntry entry : ROSTER) {
            map.put(entry.className(), entry);
        }
        return Collections.unmodifiableMap(map);
    }

    private static Set<String> excludedClasses() {
        Set<String> set = new LinkedHashSet<>();
        for (Excluded entry : EXCLUDED) {
            set.add(entry.className());
        }
        return Collections.unmodifiableSet(set);
    }

    /**
     * 测试实体的注册名。
     *
     * <p>放在这里而不是从 {@code ModEntityTypes} 反查：那会把
     * {@code DeferredHolder} / 注册表静态初始化拖进纯 JUnit。
     */
    private static final Set<String> EXCLUDED_ENTITY_PATHS =
            Set.of("blood_bar_probe_t3", "blood_bar_probe_t4", "balance_test_boss");

    /** 名册里的曲目集合（测试用）。 */
    public static Optional<BgmTrack> optionalTrackForEvent(ResourceLocation eventId) {
        return Optional.ofNullable(trackForEvent(eventId));
    }
}
