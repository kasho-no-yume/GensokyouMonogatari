package com.bitsson.gensokyou.ritual;

import com.bitsson.gensokyou.Gensokyou;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class RitualPatternLoader extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new Gson();
    private static final List<RitualPattern> PATTERNS = new ArrayList<>();
    /** id → 原始 v5 JSON（编辑杖回写/校验的基文件；与 PATTERNS 同生命周期重建）。 */
    private static final Map<ResourceLocation, JsonObject> RAWS = new LinkedHashMap<>();

    public RitualPatternLoader() {
        super(GSON, "rituals");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager resourceManager,
                         net.minecraft.util.profiling.ProfilerFiller profiler) {
        List<RitualPattern> parsed = new ArrayList<>();
        Map<ResourceLocation, JsonObject> raws = new LinkedHashMap<>();
        for (var file : files.entrySet()) {
            try {
                JsonObject json = GsonHelper.convertToJsonObject(file.getValue(), "ritual");
                parsed.add(parse(file.getKey(), json));
                raws.put(file.getKey(), json);
            } catch (Exception exception) {
                Gensokyou.LOGGER.warn("Rejected ritual pattern {}: {}", file.getKey(), exception.getMessage());
            }
        }
        synchronized (PATTERNS) {
            // 约束越多的结构越具体，优先匹配：避免子集结构（如普通召唤环）抢占超集结构（如带祭品台的结界仪式）
            parsed.sort(java.util.Comparator.comparingInt(RitualPatternLoader::specificity).reversed());
            PATTERNS.clear();
            PATTERNS.addAll(parsed);
            RAWS.clear();
            RAWS.putAll(raws);
        }
        Gensokyou.LOGGER.info("Loaded {} ritual patterns", PATTERNS.size());
    }

    /** 该结构的展开方块总数，作为"具体度"权重。 */
    private static int specificity(RitualPattern pattern) {
        int count = 0;
        for (RitualPattern.LevelSlice level : pattern.levels()) {
            count += level.blocks().size();
        }
        return count;
    }

    private static RitualPattern parse(ResourceLocation id, JsonObject json) {
        char anchorKey = GsonHelper.getAsString(json, "anchorKey").charAt(0);
        Map<Character, RitualPattern.Predicate> palette = new HashMap<>();
        JsonObject paletteJson = GsonHelper.getAsJsonObject(json, "palette");
        for (Map.Entry<String, JsonElement> entry : paletteJson.entrySet()) {
            palette.put(entry.getKey().charAt(0), parsePredicate(entry.getValue().getAsString()));
        }
        if (!palette.containsKey(anchorKey)) {
            throw new IllegalArgumentException("palette lacks anchorKey '" + anchorKey + "'");
        }
        List<RitualPattern.LevelSlice> levels = new ArrayList<>();
        // v5 增量语义：逐级 adds 四重展开后累积为全量切片；增量与低级累积切片任意相交 / level 号重复即拒载
        Map<Pos, RitualPattern.BlockEntry> cumulative = new HashMap<>();
        Map<Pos, Integer> origins = new HashMap<>();
        List<int[]> anchorHits = new ArrayList<>();
        Set<Integer> levelNumbers = new HashSet<>();
        for (JsonElement levelElement : GsonHelper.getAsJsonArray(json, "levels")) {
            JsonObject levelJson = GsonHelper.convertToJsonObject(levelElement, "level");
            int levelNumber = GsonHelper.getAsInt(levelJson, "level");
            if (!levelNumbers.add(levelNumber)) {
                throw new IllegalArgumentException("level " + levelNumber + " appears more than once");
            }
            if (levelJson.has("blocks")) {
                throw new IllegalArgumentException("level " + levelNumber
                        + ": v4 全量快照字段 \"blocks\" 已废弃，请运行迁移工具 "
                        + "(python tools/validate_ritual_pattern.py --convert-v4) 转为 v5 增量 \"adds\"");
            }
            List<RitualPattern.BlockEntry> delta = new ArrayList<>();
            mergeAdds(anchorKey, levelNumber, palette,
                    GsonHelper.getAsJsonArray(levelJson, "adds"), cumulative, origins, delta, anchorHits);
            List<RitualPattern.BlockEntry> full = new ArrayList<>(cumulative.values());
            full.sort(RitualPatternLoader::compareCanonical);
            levels.add(new RitualPattern.LevelSlice(levelNumber, List.copyOf(full)));
        }
        // 锚点全文件级校验：恰一次、位于原点 (0,0,0)、且只写在最低级增量
        if (anchorHits.size() != 1) {
            throw new IllegalArgumentException("anchorKey appears " + anchorHits.size()
                    + " times in the whole file (must be exactly 1, lowest level only)");
        }
        int[] anchor = anchorHits.get(0);
        if (anchor[1] != 0 || anchor[2] != 0 || anchor[3] != 0) {
            throw new IllegalArgumentException("anchorKey must sit at origin (0,0,0), got ("
                    + anchor[1] + "," + anchor[2] + "," + anchor[3] + ") in level " + anchor[0]);
        }
        int lowest = levels.stream().mapToInt(RitualPattern.LevelSlice::level).min().orElseThrow();
        if (anchor[0] != lowest) {
            throw new IllegalArgumentException("anchorKey may only be declared in the lowest level ("
                    + lowest + ") increment, found in level " + anchor[0]);
        }
        levels.sort(java.util.Comparator.comparingInt(RitualPattern.LevelSlice::level));
        List<RitualPattern.Offering> requirements = new ArrayList<>();
        if (json.has("requirements")) {
            for (JsonElement element : GsonHelper.getAsJsonArray(json, "requirements")) {
                requirements.add(parseOffering(GsonHelper.convertToJsonObject(element, "requirement")));
            }
        }
        boolean toggleable = GsonHelper.getAsBoolean(json, "toggleable", false);
        List<Integer> tiers = RitualPattern.ALL_TIERS;
        if (json.has("tiers")) {
            List<Integer> parsed = new ArrayList<>();
            for (JsonElement t : GsonHelper.getAsJsonArray(json, "tiers")) {
                int tier = t.getAsInt();
                if (tier < 0 || tier > 5) {
                    throw new IllegalArgumentException("tier out of range 0..5: " + tier);
                }
                parsed.add(tier);
            }
            if (parsed.isEmpty()) {
                throw new IllegalArgumentException("tiers array must not be empty");
            }
            tiers = List.copyOf(parsed);
        }
        return new RitualPattern(id, anchorKey, Map.copyOf(palette), List.copyOf(levels),
                List.copyOf(requirements), toggleable, tiers);
    }

    /** 累积注册表键：全量格位。 */
    private record Pos(int x, int y, int z) {
    }

    /** 解析一层级的 v5 增量条目（位置式数组）并四重展开并入累积注册表；
     *  增量与已累积切片任意格位相交（含同 key 重复登记）即拒载，报明冲突格位与来源层级。 */
    private static void mergeAdds(char anchorKey, int levelNumber,
                                  Map<Character, RitualPattern.Predicate> palette,
                                  JsonArray addsJson,
                                  Map<Pos, RitualPattern.BlockEntry> cumulative,
                                  Map<Pos, Integer> origins,
                                  List<RitualPattern.BlockEntry> deltaOut,
                                  List<int[]> anchorHits) {
        List<RitualPattern.BlockEntry> expanded = new ArrayList<>();
        for (JsonElement element : addsJson) {
            if (!element.isJsonArray()) {
                throw new IllegalArgumentException("level " + levelNumber
                        + ": block entry must be an array [key,x,y,z(,o)?]（v3 对象式条目不再接受）");
            }
            JsonArray entry = element.getAsJsonArray();
            if (entry.size() < 4 || entry.size() > 5) {
                throw new IllegalArgumentException("level " + levelNumber
                        + ": block entry must be [key,x,y,z,o?] (4 or 5 elements), got " + entry.size());
            }
            String keyToken = entry.get(0).getAsString();
            if (keyToken.length() != 1) {
                throw new IllegalArgumentException("level " + levelNumber
                        + ": block key must be a single character: '" + keyToken + "'");
            }
            char key = keyToken.charAt(0);
            int x = entry.get(1).getAsInt();
            int y = entry.get(2).getAsInt();
            int z = entry.get(3).getAsInt();
            Integer orientation = parseOrientation(entry, levelNumber, key, x, y, z);
            if (orientation != null) {
                validateOrientation(anchorKey, levelNumber, palette, key, x, y, z, orientation);
            }
            if (key == anchorKey) {
                anchorHits.add(new int[]{levelNumber, x, y, z});
            }
            expanded.clear();
            expandInto(key, x, y, z, orientation, expanded);
            for (RitualPattern.BlockEntry block : expanded) {
                Pos pos = new Pos(block.x(), block.y(), block.z());
                RitualPattern.BlockEntry previous = cumulative.get(pos);
                if (previous != null) {
                    if (origins.get(pos) == levelNumber) {
                        throw new IllegalArgumentException("level " + levelNumber
                                + ": duplicate/conflicting block at (" + block.x() + "," + block.y()
                                + "," + block.z() + "): '" + previous.key() + "' vs '" + block.key() + "'");
                    }
                    throw new IllegalArgumentException("level " + levelNumber
                            + ": adds conflict with cumulative slice of level " + origins.get(pos)
                            + " at (" + block.x() + "," + block.y() + "," + block.z() + "): '"
                            + previous.key() + "' vs '" + block.key() + "'（增量须只声明该级新增格位）");
                }
                cumulative.put(pos, block);
                origins.put(pos, levelNumber);
                deltaOut.add(block);
            }
        }
    }

    /** 解析条目第 5 位（可选）：int id 或字符串名双解析；非法值拒载。 */
    private static @Nullable Integer parseOrientation(JsonArray entry, int levelNumber,
                                                      char key, int x, int y, int z) {
        if (entry.size() == 4) {
            return null;
        }
        JsonElement token = entry.get(4);
        Integer id = null;
        if (token.isJsonPrimitive()) {
            com.google.gson.JsonPrimitive primitive = token.getAsJsonPrimitive();
            if (primitive.isNumber()) {
                int value = primitive.getAsInt();
                id = Orientation.valid(value) ? value : null;
            } else if (primitive.isString()) {
                id = Orientation.byName(primitive.getAsString());
            }
        }
        if (id == null) {
            throw new IllegalArgumentException("tier " + levelNumber + ": invalid orientation constant "
                    + token + " at (" + x + "," + y + "," + z + ") key '" + key + "'");
        }
        return id;
    }

    /** 朝向相关加载期校验（D6）：锚点/AIR/IGNORE 不带朝向；EXACT 块须满足常量属性需求。 */
    private static void validateOrientation(char anchorKey, int levelNumber,
                                            Map<Character, RitualPattern.Predicate> palette,
                                            char key, int x, int y, int z, int orientation) {
        String where = "tier " + levelNumber + " (" + x + "," + y + "," + z + ") key '" + key + "'";
        if (key == anchorKey) {
            throw new IllegalArgumentException("anchor entry must not carry orientation: " + where);
        }
        RitualPattern.Predicate predicate = palette.get(key);
        if (predicate == null) {
            throw new IllegalArgumentException("unknown palette key with orientation: " + where);
        }
        if (predicate.kind() == RitualPattern.Kind.AIR || predicate.kind() == RitualPattern.Kind.IGNORE) {
            throw new IllegalArgumentException("AIR/IGNORE cell must not carry orientation: " + where);
        }
        if (predicate.kind() == RitualPattern.Kind.EXACT
                && !Orientation.supports(predicate.block().defaultBlockState(), orientation)) {
            throw new IllegalArgumentException("block "
                    + net.minecraft.core.registries.BuiltInRegistries.BLOCK
                            .getKey(predicate.block())
                    + " does not support orientation constant " + orientation + ": " + where);
        }
    }

    /** 单条目的对称展开：off-axis 四象限镜像；轴上四方成套（坐标互换）；朝向随位置同复合变换。 */
    public static void expandInto(char key, int x, int y, int z, @Nullable Integer orientation,
                                  List<RitualPattern.BlockEntry> out) {
        if (x == 0 && z == 0) {
            out.add(new RitualPattern.BlockEntry(key, 0, y, 0, orientation));
        } else if (x == 0) {
            out.add(new RitualPattern.BlockEntry(key, 0, y, z, orientation));
            out.add(new RitualPattern.BlockEntry(key, 0, y, -z, apply(orientation, Orientation::mirrorZ)));
            out.add(new RitualPattern.BlockEntry(key, z, y, 0, apply(orientation, Orientation::diagSwap)));
            out.add(new RitualPattern.BlockEntry(key, -z, y, 0, apply(orientation, Orientation::diagAnti)));
        } else if (z == 0) {
            out.add(new RitualPattern.BlockEntry(key, x, y, 0, orientation));
            out.add(new RitualPattern.BlockEntry(key, -x, y, 0, apply(orientation, Orientation::mirrorX)));
            out.add(new RitualPattern.BlockEntry(key, 0, y, x, apply(orientation, Orientation::diagSwap)));
            out.add(new RitualPattern.BlockEntry(key, 0, y, -x, apply(orientation, Orientation::rot270)));
        } else {
            out.add(new RitualPattern.BlockEntry(key, x, y, z, orientation));
            out.add(new RitualPattern.BlockEntry(key, -x, y, z, apply(orientation, Orientation::mirrorX)));
            out.add(new RitualPattern.BlockEntry(key, x, y, -z, apply(orientation, Orientation::mirrorZ)));
            out.add(new RitualPattern.BlockEntry(key, -x, y, -z, apply(orientation, Orientation::rot180)));
        }
    }

    private static @Nullable Integer apply(@Nullable Integer orientation,
                                           java.util.function.IntUnaryOperator op) {
        return orientation == null ? null : op.applyAsInt(orientation);
    }

    /** 规范序比较器：层自下而上、z 自北向南、x 自西向东。 */
    static int compareCanonical(RitualPattern.BlockEntry a, RitualPattern.BlockEntry b) {
        if (a.y() != b.y()) {
            return Integer.compare(a.y(), b.y());
        }
        if (a.z() != b.z()) {
            return Integer.compare(a.z(), b.z());
        }
        return Integer.compare(a.x(), b.x());
    }

    private static RitualPattern.Offering parseOffering(JsonObject json) {
        char key = GsonHelper.getAsString(json, "key").charAt(0);
        int slot = GsonHelper.getAsInt(json, "slot", 0);
        RitualPattern.ItemRequirement item = parseItemRequirement(GsonHelper.getAsString(json, "item"));
        int count = GsonHelper.getAsInt(json, "count", 1);
        String consumeStr = GsonHelper.getAsString(json, "consume", "none");
        RitualPattern.ConsumeMode consume = switch (consumeStr) {
            case "none" -> RitualPattern.ConsumeMode.NONE;
            case "on_activate" -> RitualPattern.ConsumeMode.ON_ACTIVATE;
            case "periodic" -> RitualPattern.ConsumeMode.PERIODIC;
            default -> throw new IllegalArgumentException("unknown consume mode: " + consumeStr);
        };
        int period = Math.max(1, GsonHelper.getAsInt(json, "period", 1200));
        return new RitualPattern.Offering(key, slot, item, Math.max(1, count), consume, period);
    }

    private static RitualPattern.ItemRequirement parseItemRequirement(String value) {
        if (value.startsWith("#")) {
            TagKey<Item> tag = TagKey.create(Registries.ITEM, ResourceLocation.parse(value.substring(1)));
            return new RitualPattern.ItemRequirement(null, tag);
        }
        Item item = BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(value))
                .orElseThrow(() -> new IllegalArgumentException("unknown item: " + value));
        return new RitualPattern.ItemRequirement(item, null);
    }

    private static RitualPattern.Predicate parsePredicate(String value) {
        if (value.equals("_ignore")) {
            return RitualPattern.Predicate.IGNORE;
        }
        if (value.equals("minecraft:air") || value.equals("air")) {
            return RitualPattern.Predicate.AIR;
        }
        if (value.startsWith("#")) {
            TagKey<Block> tag = TagKey.create(Registries.BLOCK, ResourceLocation.parse(value.substring(1)));
            return new RitualPattern.Predicate(RitualPattern.Kind.TAG, null, tag);
        }
        Block block = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(value));
        return new RitualPattern.Predicate(RitualPattern.Kind.EXACT, block, null);
    }

    public static List<RitualPattern> all() {
        synchronized (PATTERNS) {
            return List.copyOf(PATTERNS);
        }
    }

    /** 原始 v5 JSON 副本（编辑杖回写基文件）；未加载/被拒载返回 empty。 */
    public static Optional<JsonObject> rawOf(ResourceLocation id) {
        synchronized (PATTERNS) {
            JsonObject json = RAWS.get(id);
        return json == null ? Optional.empty() : Optional.of(json.deepCopy().getAsJsonObject());
        }
    }

    /** 编辑期校验用：以 loader 同规则解析给定 JSON（非法即抛 IllegalArgumentException）。 */
    public static RitualPattern parseForEdit(ResourceLocation id, JsonObject json) {
        return parse(id, json);
    }

    public static Optional<RitualPattern> byId(ResourceLocation id) {
        synchronized (PATTERNS) {
            return PATTERNS.stream().filter(p -> p.id().equals(id)).findFirst();
        }
    }
}
