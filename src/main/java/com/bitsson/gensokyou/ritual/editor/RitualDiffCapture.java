package com.bitsson.gensokyou.ritual.editor;

import com.bitsson.gensokyou.ritual.RitualPattern;
import com.bitsson.gensokyou.ritual.RitualPatternLoader;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * diff 捕获：把工作区扫描结果重导出为某阶 {@code adds} 补丁（design D3）。
 *
 * <p>与 {@link com.bitsson.gensokyou.ritual.RitualCapture} 的"从零骨架"不同，本类以
 * 低级累积切片 {@code ground = cumulative(N-1)} 为**不可动地基**：地基格谓词仍满足则原样保留、
 * 不进 adds；被换/挖则报"低级格被改动"违规。工作区内非地基的新增格按 key 判定次序重导出本阶 adds，
 * 完全替换旧本阶草稿。四重对称归并复用与 loader 一致的本原变换（{@link RitualPatternLoader#expandInto}），
 * 落盘前 {@link #selfCheck} 复展开比对地基相交，相交即拒产。
 *
 * <p>纯函数，不触碰 {@code Level}/注册表：世界由调用方扫成 {@code Map<BlockPos, Cell>}（相对锚点，blockId + 朝向常量），
 * palette 传 {@code keyChar → 谓词值字符串}，标签成员/品阶经 {@link BlockTagIndex} 注入——可在纯 JVM 下测试。
 */
public final class RitualDiffCapture {

    /** 工作区一格：方块 registry id + 朝向常量（0 = 无朝向属性）。 */
    public record Cell(String blockId, int orientation) {
    }

    /** 一条规范四分之一 adds 条目（位置式）。 */
    public record AddsEntry(char key, int x, int y, int z, @Nullable Integer o) {
    }

    /** 捕获产物：本阶 adds 草稿条目 + 新 palette 键值 + 违规清单 + 保留的地基格数。 */
    public record Patch(List<AddsEntry> adds, Map<Character, String> paletteAdditions,
                        List<String> violations, int keptGroundCells) {
        public boolean clean() {
            return violations.isEmpty();
        }
    }

    /** 被编辑仪式的可序列化视图（从已加载 pattern 或 JSON 构造）。 */
    public record PatternView(char anchorKey, Map<Character, String> paletteValues) {
    }

    /** 受阶石族方块（祭品台已单方块化、不参与品阶反导）。 */
    private static final Pattern TIERED_BLOCK =
            Pattern.compile("gensokyou:(ritual_stone)_([0-5])");

    private RitualDiffCapture() {
    }

    /**
     * @param workspace   工作区内全部非空气格（相对锚点坐标），调用方扫描世界得到
     * @param levelNumber 正在捕获的阶级号（= 石反导标签下限）
     * @param ground      低级累积切片 cumulative(N-1) 全量展开（相对锚点）；最低阶传仅含锚点 (0,0,0) 的切片
     * @param view        既有 palette（keyChar → 谓词值）
     * @param index       标签成员 / 品阶查询
     */
    public static Patch capture(Map<BlockPos3, Cell> workspace, int levelNumber,
                                List<RitualPattern.BlockEntry> ground,
                                PatternView view, BlockTagIndex index) {
        List<String> violations = new ArrayList<>();
        Map<Character, String> paletteAdditions = new LinkedHashMap<>();
        Set<Character> usedKeys = new LinkedHashSet<>(view.paletteValues().keySet());

        Map<BlockPos3, RitualPattern.BlockEntry> groundMap = new LinkedHashMap<>();
        for (RitualPattern.BlockEntry entry : ground) {
            groundMap.put(new BlockPos3(entry.x(), entry.y(), entry.z()), entry);
        }
        BlockPos3 origin = new BlockPos3(0, 0, 0);

        // 1. 地基格判定（保留 / 被改动违规）
        int kept = 0;
        for (Map.Entry<BlockPos3, RitualPattern.BlockEntry> g : groundMap.entrySet()) {
            BlockPos3 pos = g.getKey();
            if (pos.equals(origin)) {
                kept++; // 锚点核心恒保留
                continue;
            }
            Cell cell = workspace.get(pos);
            if (cell == null) {
                continue; // 地基格在工作区外不判定
            }
            if (satisfies(g.getValue().key(), cell, view, index)) {
                kept++;
            } else {
                violations.add("低级格被改动 " + pos + ": key '" + g.getValue().key()
                        + "' 谓词不再被 " + cell.blockId() + " 满足（须先回退本阶改动或重锚低级）");
            }
        }

        // 2. 新增格：地基未声明、世界有方块，按规范对称类归并
        Map<String, List<BlockPos3>> classes = new LinkedHashMap<>();
        for (Map.Entry<BlockPos3, Cell> e : workspace.entrySet()) {
            BlockPos3 pos = e.getKey();
            if (pos.equals(origin) || groundMap.containsKey(pos)) {
                continue;
            }
            classes.computeIfAbsent(canonicalKey(pos.x(), pos.y(), pos.z()), k -> new ArrayList<>())
                    .add(pos);
        }

        List<AddsEntry> adds = new ArrayList<>();
        for (Map.Entry<String, List<BlockPos3>> cls : classes.entrySet()) {
            BlockPos3 quarter = quarterSlot(cls.getKey());
            Cell rep = workspace.get(quarter);
            if (rep == null) {
                rep = workspace.get(cls.getValue().get(0));
            }
            if (rep == null) {
                continue;
            }
            if (isAsymmetric(cls.getValue(), quarter, workspace, rep)) {
                violations.add("对称类 " + cls.getKey() + " 各支方块/朝向不一致（同类须同一 key）");
                continue;
            }
            Character key = resolveKey(rep, usedKeys, paletteAdditions, view, index, levelNumber);
            if (key == null) {
                violations.add("对称类 " + cls.getKey() + " 无可用 palette key（字符耗尽）");
                continue;
            }
            adds.add(new AddsEntry(key, quarter.x(), quarter.y(), quarter.z(),
                    rep.orientation() != 0 ? rep.orientation() : null));
        }

        adds.sort((a, b) -> {
            if (a.y() != b.y()) {
                return Integer.compare(a.y(), b.y());
            }
            if (a.z() != b.z()) {
                return Integer.compare(a.z(), b.z());
            }
            return Integer.compare(a.x(), b.x());
        });

        Patch patch = new Patch(adds, paletteAdditions, List.copyOf(violations), kept);
        List<String> internal = selfCheck(patch, ground);
        if (!internal.isEmpty()) {
            violations.addAll(internal);
            return new Patch(List.of(), paletteAdditions, List.copyOf(violations), kept);
        }
        return patch;
    }

    /** 地基格谓词是否仍被世界方块满足。 */
    private static boolean satisfies(char groundKey, Cell cell, PatternView view, BlockTagIndex index) {
        String value = view.paletteValues().get(groundKey);
        if (value == null) {
            return false;
        }
        if (value.startsWith("#")) {
            return index.members(value.substring(1)).contains(cell.blockId());
        }
        if (isIgnoreOrAir(value)) {
            return true; // AIR/IGNORE 地基格不参与世界判定（工作区不扫空气）
        }
        return value.equals(cell.blockId());
    }

    private static boolean isIgnoreOrAir(String value) {
        return value.equals("_ignore") || value.equals("air") || value.equals("minecraft:air");
    }

    /** key 判定次序（D3 ①②③）；paletteAdditions 记录需新增的键值。 */
    private static Character resolveKey(Cell cell, Set<Character> usedKeys,
                                        Map<Character, String> paletteAdditions, PatternView view,
                                        BlockTagIndex index, int levelNumber) {
        // ① 既有 palette 命中即复用（排除锚点键；插入序首个命中，确定性）
        for (Map.Entry<Character, String> e : view.paletteValues().entrySet()) {
            char key = e.getKey();
            if (key == view.anchorKey()) {
                continue;
            }
            String value = e.getValue();
            boolean tag = value.startsWith("#");
            boolean hit = tag
                    ? index.members(value.substring(1)).contains(cell.blockId())
                    : value.equals(cell.blockId());
            if (hit && !isIgnoreOrAir(value)) {
                return key;
            }
        }
        // ② 石反导 _N_plus 标签（含 N=0 全量特例）、台恒全量标签；③ 其余新 EXACT
        String derived = deriveTagValue(cell.blockId(), levelNumber);
        String value = derived != null ? derived : cell.blockId();
        Character ch = nextKey(usedKeys);
        if (ch == null) {
            return null;
        }
        usedKeys.add(ch);
        paletteAdditions.put(ch, value);
        return ch;
    }

    private static boolean isTieredFamily(String blockId) {
        return TIERED_BLOCK.matcher(blockId).matches();
    }

    private static @Nullable String deriveTagValue(String blockId, int levelNumber) {
        // 祭品台单方块无品阶：任意阶级新增台恒反导全量标签
        if (blockId.equals("gensokyou:ritual_pedestal")) {
            return "#gensokyou:ritual_pedestals";
        }
        Matcher m = TIERED_BLOCK.matcher(blockId);
        if (!m.matches()) {
            return null;
        }
        String ns = "gensokyou:";
        return levelNumber == 0 ? "#" + ns + "ritual_stones"
                : "#" + ns + "ritual_stones_" + levelNumber + "_plus";
    }

    private static @Nullable Character nextKey(Set<Character> used) {
        for (char k = 'A'; k <= 'Z'; k++) {
            if (k != 'C' && !used.contains(k)) {
                return k;
            }
        }
        return null;
    }

    /** 对称类各支世界方块/朝向是否与代表一致（同类须同一 key）。 */
    private static boolean isAsymmetric(List<BlockPos3> members, BlockPos3 quarter,
                                        Map<BlockPos3, Cell> workspace, Cell rep) {
        for (RitualPattern.BlockEntry branch : expand(quarter, rep)) {
            Cell cell = workspace.get(new BlockPos3(branch.x(), branch.y(), branch.z()));
            if (cell == null) {
                continue;
            }
            if (!cell.blockId().equals(rep.blockId())) {
                return true;
            }
            int want = branch.orientation() == null ? 0 : branch.orientation();
            if (cell.orientation() != want) {
                return true;
            }
        }
        return false;
    }

    /** adds 四重展开并集须与地基不相交、自身不重叠（否则改写低级足迹 / 坏补丁）。 */
    static List<String> selfCheck(Patch patch, List<RitualPattern.BlockEntry> ground) {
        List<String> errors = new ArrayList<>();
        Set<BlockPos3> groundPos = new LinkedHashSet<>();
        for (RitualPattern.BlockEntry g : ground) {
            groundPos.add(new BlockPos3(g.x(), g.y(), g.z()));
        }
        Set<BlockPos3> seen = new LinkedHashSet<>();
        for (AddsEntry entry : patch.adds()) {
            for (RitualPattern.BlockEntry expanded : expand(entry)) {
                BlockPos3 pos = new BlockPos3(expanded.x(), expanded.y(), expanded.z());
                if (groundPos.contains(pos)) {
                    errors.add("内部冲突: adds '" + entry.key() + "' 展开与地基相交于 " + pos);
                }
                if (!seen.add(pos)) {
                    errors.add("内部冲突: adds 展开重复登记于 " + pos);
                }
            }
        }
        return errors;
    }

    // ---------------------------------------------------------------- 几何（与 loader 同规则，纯 int）

    private static List<RitualPattern.BlockEntry> expand(AddsEntry entry) {
        List<RitualPattern.BlockEntry> out = new ArrayList<>();
        RitualPatternLoader.expandInto(entry.key(), entry.x(), entry.y(), entry.z(), entry.o(), out);
        return out;
    }

    private static List<RitualPattern.BlockEntry> expand(BlockPos3 quarter, Cell rep) {
        List<RitualPattern.BlockEntry> out = new ArrayList<>();
        RitualPatternLoader.expandInto('_', quarter.x(), quarter.y(), quarter.z(),
                rep.orientation() != 0 ? rep.orientation() : null, out);
        return out;
    }

    private static String canonicalKey(int x, int y, int z) {
        if (x == 0 && z == 0) {
            return "O," + y;
        }
        if (x == 0 || z == 0) {
            return "A," + y + "," + Math.max(Math.abs(x), Math.abs(z));
        }
        return "Q," + y + "," + Math.abs(x) + "," + Math.abs(z);
    }

    private static BlockPos3 quarterSlot(String classKey) {
        String[] parts = classKey.split(",");
        int y = Integer.parseInt(parts[1]);
        if (parts[0].equals("O")) {
            return new BlockPos3(0, y, 0);
        }
        if (parts[0].equals("A")) {
            return new BlockPos3(0, y, Integer.parseInt(parts[2]));
        }
        return new BlockPos3(Integer.parseInt(parts[2]), y, Integer.parseInt(parts[3]));
    }
}
