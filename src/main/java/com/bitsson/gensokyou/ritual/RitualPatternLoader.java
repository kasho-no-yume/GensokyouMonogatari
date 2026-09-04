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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class RitualPatternLoader extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new Gson();
    private static final List<RitualPattern> PATTERNS = new ArrayList<>();

    public RitualPatternLoader() {
        super(GSON, "rituals");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager resourceManager,
                         net.minecraft.util.profiling.ProfilerFiller profiler) {
        List<RitualPattern> parsed = new ArrayList<>();
        for (var file : files.entrySet()) {
            try {
                parsed.add(parse(file.getKey(), GsonHelper.convertToJsonObject(file.getValue(), "ritual")));
            } catch (Exception exception) {
                Gensokyou.LOGGER.warn("Rejected ritual pattern {}: {}", file.getKey(), exception.getMessage());
            }
        }
        synchronized (PATTERNS) {
            // 约束越多的结构越具体，优先匹配：避免子集结构（如普通召唤环）抢占超集结构（如带祭品台的结界仪式）
            parsed.sort(java.util.Comparator.comparingInt(RitualPatternLoader::specificity).reversed());
            PATTERNS.clear();
            PATTERNS.addAll(parsed);
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
        for (JsonElement levelElement : GsonHelper.getAsJsonArray(json, "levels")) {
            JsonObject levelJson = GsonHelper.convertToJsonObject(levelElement, "level");
            int levelNumber = GsonHelper.getAsInt(levelJson, "level");
            levels.add(new RitualPattern.LevelSlice(levelNumber,
                    parseBlocks(anchorKey, levelNumber, GsonHelper.getAsJsonArray(levelJson, "blocks"))));
        }
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

    /** 解析并展开一个层级的方块表：仅存规范四分之一，输出全量并按 (y,z,x) 规范序排序。 */
    private static List<RitualPattern.BlockEntry> parseBlocks(char anchorKey, int levelNumber,
                                                              JsonArray blocksJson) {
        List<RitualPattern.BlockEntry> expanded = new ArrayList<>();
        int anchorEntries = 0;
        for (JsonElement element : blocksJson) {
            JsonObject entry = GsonHelper.convertToJsonObject(element, "block");
            char key = GsonHelper.getAsString(entry, "key").charAt(0);
            int x = GsonHelper.getAsInt(entry, "x");
            int y = GsonHelper.getAsInt(entry, "y");
            int z = GsonHelper.getAsInt(entry, "z");
            if (key == anchorKey) {
                anchorEntries++;
                if (x != 0 || z != 0) {
                    throw new IllegalArgumentException(
                            "tier " + levelNumber + ": anchor must sit at x=0,z=0");
                }
            }
            expandInto(key, x, y, z, expanded);
        }
        if (anchorEntries != 1) {
            throw new IllegalArgumentException("tier " + levelNumber + ": anchorKey appears "
                    + anchorEntries + " times (must be exactly 1)");
        }
        record Pos(int x, int y, int z) {
        }
        Map<Pos, Character> seen = new HashMap<>();
        for (RitualPattern.BlockEntry block : expanded) {
            Character previous = seen.put(new Pos(block.x(), block.y(), block.z()), block.key());
            if (previous != null) {
                throw new IllegalArgumentException("tier " + levelNumber
                        + ": conflicting/duplicate block at (" + block.x() + "," + block.y()
                        + "," + block.z() + "): '" + previous + "' vs '" + block.key() + "'");
            }
        }
        expanded.sort(RitualPatternLoader::compareCanonical);
        return List.copyOf(expanded);
    }

    /** 单条目的对称展开：off-axis 四象限镜像；轴上四方成套（坐标互换）。 */
    static void expandInto(char key, int x, int y, int z, List<RitualPattern.BlockEntry> out) {
        if (x == 0 && z == 0) {
            out.add(new RitualPattern.BlockEntry(key, 0, y, 0));
        } else if (x == 0) {
            out.add(new RitualPattern.BlockEntry(key, 0, y, z));
            out.add(new RitualPattern.BlockEntry(key, 0, y, -z));
            out.add(new RitualPattern.BlockEntry(key, z, y, 0));
            out.add(new RitualPattern.BlockEntry(key, -z, y, 0));
        } else if (z == 0) {
            out.add(new RitualPattern.BlockEntry(key, x, y, 0));
            out.add(new RitualPattern.BlockEntry(key, -x, y, 0));
            out.add(new RitualPattern.BlockEntry(key, 0, y, x));
            out.add(new RitualPattern.BlockEntry(key, 0, y, -x));
        } else {
            out.add(new RitualPattern.BlockEntry(key, x, y, z));
            out.add(new RitualPattern.BlockEntry(key, -x, y, z));
            out.add(new RitualPattern.BlockEntry(key, x, y, -z));
            out.add(new RitualPattern.BlockEntry(key, -x, y, -z));
        }
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

    public static Optional<RitualPattern> byId(ResourceLocation id) {
        synchronized (PATTERNS) {
            return PATTERNS.stream().filter(p -> p.id().equals(id)).findFirst();
        }
    }
}
