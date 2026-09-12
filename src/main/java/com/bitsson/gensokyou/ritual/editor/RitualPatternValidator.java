package com.bitsson.gensokyou.ritual.editor;

import com.bitsson.gensokyou.ritual.Orientation;
import com.bitsson.gensokyou.ritual.RitualPattern;
import com.bitsson.gensokyou.ritual.RitualPatternLoader;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 仪式 pattern 合规性校验器——{@code tools/validate_ritual_pattern.py} 五条规则的 Java 对等移植（design D4）：
 * ① 增量与低级累积切片格位相交 = ERROR；② level 号重复 = ERROR；③ 锚点三条件 = ERROR；
 * ④ 品阶下限（key 首现层 L 须有标签下限 ≥ L）= WARN；⑤ 跨 pattern 劫持 = ERROR。
 *
 * <p>纯 JSON 级实现，不触碰注册表——标签成员/品阶经 {@link BlockTagIndex} 注入，
 * 因此对表测试与游戏内保存链路共用同一份实现。消息文案与 python 侧逐字对齐
 * （单行 {@code ERROR/WARN: <定位>: <原因>}，{@link Issue#line()}），漂移由
 * {@code RitualPatternValidatorParityTest} 兜底。python 侧为规则设计权威。
 */
public final class RitualPatternValidator {

    /** 一条校验结论；warn=true 为提示（不拦保存）。 */
    public record Issue(boolean warn, String message) {
        public String line() {
            return (warn ? "WARN: " : "ERROR: ") + message;
        }
    }

    private RitualPatternValidator() {
    }

    /**
     * 校验全体 pattern（与 python main 的遍历序一致）：
     * 解析期规则①②（格式错误即该文件 ERROR）、锚点③、品阶下限④、全员解析成功后的劫持⑤。
     * 返回未加前缀的消息集合（{@link Issue} 标注 ERROR/WARN），顺序不做承诺（对表按集合比对）。
     */
    public static List<Issue> validateAll(List<JsonObject> raws, BlockTagIndex index) {
        List<Issue> issues = new ArrayList<>();
        List<ParsedPattern> parsed = new ArrayList<>();
        int parseErrors = 0;
        for (JsonObject raw : raws) {
            String pid = raw.has("id") ? raw.get("id").getAsString() : "?";
            try {
                parsed.add(parsePattern(raw, index, issues));
            } catch (FormatException exception) {
                issues.add(new Issue(false, pid + ": " + exception.getMessage()));
                parseErrors++;
            }
        }
        for (ParsedPattern pattern : parsed) {
            validatePattern(pattern, issues);
        }
        // python 侧同款门槛：仅当全员解析成功才跑跨 pattern 劫持
        if (parseErrors == 0) {
            crossPatternHazards(parsed, issues);
        }
        return issues;
    }

    /** 与目标 pattern 相关的结论（保存闸用）：消息含目标 id 即相关。 */
    public static List<Issue> relevantTo(List<Issue> issues, String patternId) {
        return issues.stream().filter(i -> i.message().contains(patternId)).toList();
    }

    // ---------------------------------------------------------------- 模型（包内共享：RitualDiffCapture 复用解析结果）

    record RawEntry(String key, int x, int y, int z, Integer o) {
    }

    record ParsedLevel(int level, List<RawEntry> addsRaw, List<RawEntry> expanded) {
    }

    record PaletteEntry(boolean tag, String value, List<String> members, Integer floorTier) {
    }

    record ParsedPattern(String id, String anchor, Map<String, PaletteEntry> palette,
                         List<ParsedLevel> levels) {
    }

    static final class FormatException extends RuntimeException {
        FormatException(String message) {
            super(message);
        }
    }

    record Pos(int x, int y, int z) {
        @Override
        public String toString() {
            return "(" + x + ", " + y + ", " + z + ")";
        }
    }

    // ---------------------------------------------------------------- 解析（规则①② + 格式）

    static ParsedPattern parsePattern(JsonObject raw, BlockTagIndex index, List<Issue> issues) {
        if (!raw.has("levels") || !raw.get("levels").isJsonArray() || raw.getAsJsonArray("levels").isEmpty()) {
            throw new FormatException("缺少 \"levels\" 数组");
        }
        JsonElement anchorElement = raw.get("anchorKey");
        String anchor = anchorElement != null && anchorElement.isJsonPrimitive()
                && anchorElement.getAsString().length() == 1 ? anchorElement.getAsString() : null;
        if (anchor == null) {
            throw new FormatException("anchorKey 须为单字符，得到 " + quote(anchorElement));
        }
        if (!raw.has("palette") || !raw.get("palette").isJsonObject()) {
            throw new FormatException("缺少 \"palette\" 对象");
        }
        Map<String, PaletteEntry> palette = parsePalette(raw.getAsJsonObject("palette"), index, issues);
        List<ParsedLevel> levels = parseLevels(raw.getAsJsonArray("levels"));
        return new ParsedPattern(raw.has("id") ? raw.get("id").getAsString() : "?", anchor, palette, levels);
    }

    private static Map<String, PaletteEntry> parsePalette(JsonObject raw, BlockTagIndex index, List<Issue> issues) {
        Map<String, PaletteEntry> palette = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : raw.entrySet()) {
            String value = entry.getValue().getAsString();
            if (value.startsWith("#")) {
                String tagId = value.substring(1);
                List<String> members = index.members(tagId);
                if (members.isEmpty()) {
                    issues.add(new Issue(true, "未加载到标签 " + value + "（成员视为空）"));
                }
                Integer floor = null;
                for (String member : members) {
                    int tier = index.tierOf(member);
                    if (tier >= 0 && (floor == null || tier < floor)) {
                        floor = tier;
                    }
                }
                palette.put(entry.getKey(), new PaletteEntry(true, value, members, floor));
            } else {
                palette.put(entry.getKey(), new PaletteEntry(false, value, List.of(value), null));
            }
        }
        return palette;
    }

    private static List<ParsedLevel> parseLevels(JsonArray levelsJson) {
        List<ParsedLevel> levels = new ArrayList<>();
        Map<Pos, RawEntry> cumulative = new LinkedHashMap<>();
        Map<Pos, Integer> origins = new HashMap<>();
        Map<Integer, Integer> levelIndex = new HashMap<>();
        for (int idx = 0; idx < levelsJson.size(); idx++) {
            JsonElement levelElement = levelsJson.get(idx);
            if (!levelElement.isJsonObject()) {
                throw new FormatException("levels[" + idx + "]: 缺少/非法 level 号");
            }
            JsonObject levelJson = levelElement.getAsJsonObject();
            JsonElement numberElement = levelJson.get("level");
            if (numberElement == null || !numberElement.isJsonPrimitive() || !numberElement.getAsJsonPrimitive().isNumber()) {
                throw new FormatException("levels[" + idx + "]: 缺少/非法 level 号");
            }
            int n = numberElement.getAsInt();
            if (levelJson.has("blocks")) {
                throw new FormatException("level " + n + ": 使用已废弃的 v4 全量快照字段 \"blocks\"，"
                        + "请运行 python tools/validate_ritual_pattern.py --convert-v4 迁移");
            }
            if (!levelJson.has("adds")) {
                throw new FormatException("level " + n + ": 缺少 \"adds\" 增量字段（v5 格式）");
            }
            if (levelIndex.containsKey(n)) {
                throw new FormatException("level 号 " + n + " 重复出现（levels[" + levelIndex.get(n)
                        + "] 与 levels[" + idx + "]）");
            }
            levelIndex.put(n, idx);
            List<RawEntry> addsRaw = new ArrayList<>();
            List<RawEntry> delta = new ArrayList<>();
            JsonArray adds = levelJson.getAsJsonArray("adds");
            for (int j = 0; j < adds.size(); j++) {
                RawEntry entry = parseEntry(adds.get(j), "level " + n + " adds[" + j + "]");
                addsRaw.add(entry);
                for (RitualPattern.BlockEntry expanded : expand(entry)) {
                    Pos pos = new Pos(expanded.x(), expanded.y(), expanded.z());
                    Integer previousLevel = origins.get(pos);
                    if (previousLevel != null) {
                        RawEntry previous = cumulative.get(pos);
                        if (previousLevel == n) {
                            throw new FormatException("level " + n + ": 层内重复/冲突 " + pos + ": '"
                                    + previous.key() + "' vs '" + expanded.key() + "'");
                        }
                        throw new FormatException("level " + n + ": 增量与 level " + previousLevel
                                + " 累积切片在 " + pos + " 相交: '" + previous.key() + "' vs '"
                                + expanded.key() + "'（对低级结构的重复登记或改写）");
                    }
                    RawEntry cell = new RawEntry(String.valueOf(expanded.key()), expanded.x(),
                            expanded.y(), expanded.z(), expanded.orientation());
                    cumulative.put(pos, cell);
                    origins.put(pos, n);
                    delta.add(cell);
                }
            }
            levels.add(new ParsedLevel(n, addsRaw, sortExpanded(cumulative)));
        }
        return levels;
    }

    static RawEntry parseEntry(JsonElement element, String where) {
        if (element.isJsonObject()) {
            throw new FormatException(where + ": 条目为 v3 对象格式 " + element
                    + "，请改为位置式数组 [\"key\",x,y,z(,o)?]");
        }
        if (!element.isJsonArray() || element.getAsJsonArray().size() < 4 || element.getAsJsonArray().size() > 5) {
            throw new FormatException(where + ": 条目须为 4/5 元素位置式数组 [\"key\",x,y,z(,o)?]，得到 " + element);
        }
        JsonArray entry = element.getAsJsonArray();
        JsonElement keyElement = entry.get(0);
        if (!keyElement.isJsonPrimitive() || !keyElement.getAsJsonPrimitive().isString()
                || keyElement.getAsString().length() != 1) {
            throw new FormatException(where + ": key 须为单字符，得到 " + quote(keyElement));
        }
        int x = coordinate(entry.get(1));
        int y = coordinate(entry.get(2));
        int z = coordinate(entry.get(3));
        Integer o = null;
        if (entry.size() == 5) {
            JsonElement token = entry.get(4);
            if (token.isJsonPrimitive() && token.getAsJsonPrimitive().isString()) {
                o = Orientation.byName(token.getAsString());
                if (o == null) {
                    throw new FormatException(where + ": 非法朝向名 " + quote(token));
                }
            } else {
                if (token.isJsonPrimitive() && token.getAsJsonPrimitive().isNumber()) {
                    int id = token.getAsInt();
                    if (!Orientation.valid(id)) {
                        throw new FormatException(where + ": 朝向 id 超出 1-26：" + id);
                    }
                    o = id;
                } else {
                    throw new FormatException(where + ": 朝向须为整数常量 id，得到 " + quote(token));
                }
            }
        }
        return new RawEntry(keyElement.getAsString(), x, y, z, o);
    }

    private static int coordinate(JsonElement element) {
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new FormatException("坐标须为整数，得到 " + element);
        }
        return element.getAsInt();
    }

    private static String quote(JsonElement element) {
        if (element == null) {
            return "null";
        }
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            return "'" + element.getAsString() + "'";
        }
        return element.toString();
    }

    private static List<RitualPattern.BlockEntry> expand(RawEntry entry) {
        List<RitualPattern.BlockEntry> out = new ArrayList<>();
        RitualPatternLoader.expandInto(entry.key().charAt(0), entry.x(), entry.y(), entry.z(),
                entry.o(), out);
        return out;
    }

    private static List<RawEntry> sortExpanded(Map<Pos, RawEntry> cumulative) {
        List<RawEntry> out = new ArrayList<>(cumulative.values());
        out.sort(Comparator.comparingInt(RawEntry::y).thenComparingInt(RawEntry::z)
                .thenComparingInt(RawEntry::x));
        return out;
    }

    // ---------------------------------------------------------------- 规则③④

    private static void validatePattern(ParsedPattern pattern, List<Issue> issues) {
        validateAnchor(pattern, issues);
        validateTierFloors(pattern, issues);
    }

    private static void validateAnchor(ParsedPattern pattern, List<Issue> issues) {
        String pid = pattern.id();
        if (!pattern.palette().containsKey(pattern.anchor())) {
            issues.add(new Issue(false, pid + ": palette 缺少锚点键 " + pattern.anchor()));
            return;
        }
        List<Object[]> hits = new ArrayList<>();
        for (ParsedLevel level : pattern.levels()) {
            for (RawEntry entry : level.addsRaw()) {
                if (entry.key().equals(pattern.anchor())) {
                    hits.add(new Object[]{level.level(), entry});
                }
            }
        }
        if (hits.size() != 1) {
            issues.add(new Issue(false, pid + ": anchorKey 全文件出现 " + hits.size()
                    + " 次（须恰一次，且只写在最低级增量中）"));
            return;
        }
        RawEntry hit = (RawEntry) hits.get(0)[1];
        if (hit.x() != 0 || hit.y() != 0 || hit.z() != 0) {
            issues.add(new Issue(false, pid + ": anchorKey 须位于原点 (0,0,0)，当前在 level "
                    + hits.get(0)[0] + " 的 (" + hit.x() + "," + hit.y() + "," + hit.z() + ")"));
            return;
        }
        int lowest = pattern.levels().stream().mapToInt(ParsedLevel::level).min().orElseThrow();
        int levelNo = (int) hits.get(0)[0];
        if (levelNo != lowest) {
            issues.add(new Issue(false, pid + ": anchorKey 须只写在最低级增量（level " + lowest
                    + "），当前写在 level " + levelNo));
        }
    }

    private static void validateTierFloors(ParsedPattern pattern, List<Issue> issues) {
        List<ParsedLevel> ordered = new ArrayList<>(pattern.levels());
        ordered.sort(Comparator.comparingInt(ParsedLevel::level));
        for (Map.Entry<String, PaletteEntry> entry : pattern.palette().entrySet()) {
            PaletteEntry palette = entry.getValue();
            if (!palette.tag() || palette.floorTier() == null) {
                continue;
            }
            Integer first = null;
            for (ParsedLevel level : ordered) {
                if (first == null && level.expanded().stream().anyMatch(b -> b.key().equals(entry.getKey()))) {
                    first = level.level();
                }
            }
            if (first != null && palette.floorTier() < first) {
                issues.add(new Issue(true, pattern.id() + ": key " + entry.getKey() + " ("
                        + palette.value() + ") 首次出现于 level " + first + "，但品阶下限仅 "
                        + palette.floorTier()));
            }
        }
    }

    // ---------------------------------------------------------------- 规则⑤

    private static void crossPatternHazards(List<ParsedPattern> patterns, List<Issue> issues) {
        List<ParsedPattern> ranked = new ArrayList<>(patterns);
        ranked.sort(Comparator.comparingInt(RitualPatternValidator::specificity).reversed());
        for (int bIndex = 0; bIndex < ranked.size(); bIndex++) {
            ParsedPattern b = ranked.get(bIndex);
            ParsedLevel top = b.levels().stream()
                    .max(Comparator.comparingInt(ParsedLevel::level)).orElseThrow();
            Map<Pos, String> bTop = new HashMap<>();
            for (RawEntry entry : top.expanded()) {
                bTop.put(new Pos(entry.x(), entry.y(), entry.z()), entry.key());
            }
            for (ParsedPattern a : ranked.subList(0, bIndex)) {
                for (ParsedLevel level : a.levels()) {
                    if (covers(level, a, b, bTop)) {
                        issues.add(new Issue(false, "劫持: " + b.id() + " 的建筑会被先尝试的 "
                                + a.id() + " (level " + level.level() + ") 认领"));
                    }
                }
            }
        }
    }

    private static int specificity(ParsedPattern pattern) {
        int total = 0;
        for (ParsedLevel level : pattern.levels()) {
            total += level.expanded().size();
        }
        return total;
    }

    private static boolean covers(ParsedLevel level, ParsedPattern a, ParsedPattern b, Map<Pos, String> bTop) {
        Set<String> cache = new HashSet<>();
        for (RawEntry entry : level.expanded()) {
            if (!cache.add(entry.key() + "→" + bTop.get(new Pos(entry.x(), entry.y(), entry.z())))) {
                continue; // 同 key 同宿主已判定过，跳过集合求交
            }
            String host = bTop.get(new Pos(entry.x(), entry.y(), entry.z()));
            if (host == null) {
                return false;
            }
            PaletteEntry aPalette = a.palette().get(entry.key());
            PaletteEntry bPalette = b.palette().get(host);
            if (aPalette == null || bPalette == null || disjoint(aPalette.members(), bPalette.members())) {
                return false;
            }
        }
        return true;
    }

    private static boolean disjoint(List<String> left, List<String> right) {
        Set<String> rightSet = new HashSet<>(right);
        return left.stream().noneMatch(rightSet::contains);
    }
}
