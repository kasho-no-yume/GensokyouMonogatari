package com.bitsson.gensokyou.ritual.editor;

import com.bitsson.gensokyou.ritual.RitualPattern;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** diff 捕获核心行为（地基不可动 / 本阶自由增删 / 轴上归并 / key 复用与标签反导 / 自检拒坏补丁）。 */
class RitualDiffCaptureTest {

    /** 测试用标签索引：镜像真实 tag 文件（石族复数标签名；祭品台单方块成员）。 */
    private static final class FakeIndex implements BlockTagIndex {
        @Override
        public List<String> members(String tagId) {
            // gensokyou:ritual_stones / _N_plus；gensokyou:ritual_pedestals（单成员，无 _N_plus）
            if (tagId.equals("gensokyou:ritual_pedestals")) {
                return List.of("gensokyou:ritual_pedestal");
            }
            String tag = tagId;
            int min = 0;
            if (tag.endsWith("_plus")) {
                tag = tag.substring(0, tag.length() - 5);
                min = tag.charAt(tag.length() - 1) - '0';
                tag = tag.substring(0, tag.length() - 2);
            }
            if (!tag.equals("gensokyou:ritual_stones")) {
                return List.of();
            }
            String singular = tag.substring(0, tag.length() - 1);
            List<String> out = new ArrayList<>();
            for (int t = min; t <= 5; t++) {
                out.add(singular + "_" + t);
            }
            return out;
        }

        @Override
        public int tierOf(String blockId) {
            if (!blockId.startsWith("gensokyou:ritual_stone_")) {
                return -1;
            }
            char last = blockId.charAt(blockId.length() - 1);
            return last >= '0' && last <= '5' ? last - '0' : -1;
        }
    }

    private static final FakeIndex INDEX = new FakeIndex();

    private static RitualDiffCapture.PatternView viewOf(String... keyValues) {
        Map<Character, String> values = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            values.put(keyValues[i].charAt(0), keyValues[i + 1]);
        }
        return new RitualDiffCapture.PatternView('C', values);
    }

    private static RitualPattern.BlockEntry cell(char key, int x, int y, int z) {
        return new RitualPattern.BlockEntry(key, x, y, z, null);
    }

    private static Map<BlockPos3, RitualDiffCapture.Cell> ws(Object... tokens) {
        Map<BlockPos3, RitualDiffCapture.Cell> map = new LinkedHashMap<>();
        for (int i = 0; i < tokens.length; i += 4) {
            int x = (Integer) tokens[i];
            int y = (Integer) tokens[i + 1];
            int z = (Integer) tokens[i + 2];
            map.put(new BlockPos3(x, y, z), new RitualDiffCapture.Cell((String) tokens[i + 3], 0));
        }
        return map;
    }

    @Test
    void minimalFoundationIsAnchorOnly() {
        // 最低阶：地基=锚点；工作区加一圈石，反导 0 阶全量标签
        RitualDiffCapture.Patch patch = RitualDiffCapture.capture(
                ws(0, 0, 1, "gensokyou:ritual_stone_0",
                        0, 0, -1, "gensokyou:ritual_stone_0",
                        1, 0, 0, "gensokyou:ritual_stone_0",
                        -1, 0, 0, "gensokyou:ritual_stone_0"),
                0, List.of(cell('C', 0, 0, 0)),
                viewOf("C", "gensokyou:ritual_core"), INDEX);
        assertTrue(patch.clean(), patch.violations().toString());
        assertEquals(1, patch.adds().size(), "四支轴上归并为一条");
        RitualDiffCapture.AddsEntry e = patch.adds().get(0);
        assertEquals(0, e.x());
        assertEquals(1, e.z());
        assertEquals("#gensokyou:ritual_stones", patch.paletteAdditions().get(e.key()));
    }

    @Test
    void groundCellTamperedIsViolation() {
        // 地基 (0,0,1) 是 'B'(=stone_1)，工作区该位置被换成圆石 → 低级格被改动违规
        RitualDiffCapture.Patch patch = RitualDiffCapture.capture(
                ws(0, 0, 1, "minecraft:cobblestone"),
                1, List.of(cell('C', 0, 0, 0), cell('B', 0, 0, 1)),
                viewOf("C", "gensokyou:ritual_core", "B", "gensokyou:ritual_stone_1"), INDEX);
        assertFalse(patch.clean());
        assertTrue(patch.violations().get(0).contains("低级格被改动"), patch.violations().toString());
    }

    @Test
    void groundCellPreservedNotReEmitted() {
        // 地基 (0,0,1)=B 仍被同块满足 → 保留、不进 adds、无违规
        RitualDiffCapture.Patch patch = RitualDiffCapture.capture(
                ws(0, 0, 1, "gensokyou:ritual_stone_1"),
                1, List.of(cell('C', 0, 0, 0), cell('B', 0, 0, 1)),
                viewOf("C", "gensokyou:ritual_core", "B", "gensokyou:ritual_stone_1"), INDEX);
        assertTrue(patch.clean(), patch.violations().toString());
        assertEquals(0, patch.adds().size());
        assertEquals(2, patch.keptGroundCells());
    }

    @Test
    void newTierRingDerivesFloorTag() {
        // 阶级 3 新增一圈 stone_3，仪式原无此石标签 key → 反导 #..._3_plus
        RitualDiffCapture.Patch patch = RitualDiffCapture.capture(
                ws(0, 0, 3, "gensokyou:ritual_stone_3",
                        0, 0, -3, "gensokyou:ritual_stone_3",
                        3, 0, 0, "gensokyou:ritual_stone_3",
                        -3, 0, 0, "gensokyou:ritual_stone_3"),
                3, List.of(cell('C', 0, 0, 0)),
                viewOf("C", "gensokyou:ritual_core"), INDEX);
        assertTrue(patch.clean(), patch.violations().toString());
        assertEquals(1, patch.adds().size());
        assertEquals("#gensokyou:ritual_stones_3_plus",
                patch.paletteAdditions().get(patch.adds().get(0).key()));
    }

    @Test
    void newPedestalDerivesFullTagAtAnyLevel() {
        // 阶级 3 新增一圈祭品台（单方块），仪式原无台标签 key → 恒反导全量标签、无 _N_plus
        RitualDiffCapture.Patch patch = RitualDiffCapture.capture(
                ws(0, 0, 4, "gensokyou:ritual_pedestal",
                        0, 0, -4, "gensokyou:ritual_pedestal",
                        4, 0, 0, "gensokyou:ritual_pedestal",
                        -4, 0, 0, "gensokyou:ritual_pedestal"),
                3, List.of(cell('C', 0, 0, 0)),
                viewOf("C", "gensokyou:ritual_core"), INDEX);
        assertTrue(patch.clean(), patch.violations().toString());
        assertEquals(1, patch.adds().size());
        assertEquals("#gensokyou:ritual_pedestals",
                patch.paletteAdditions().get(patch.adds().get(0).key()));
    }

    @Test
    void reusesExistingExactKeyForSameBlock() {
        // 新增格与既有 EXACT key 'D'(=lapis) 同块 → 复用 D，不膨胀新 key
        RitualDiffCapture.Patch patch = RitualDiffCapture.capture(
                ws(0, 0, 2, "gensokyou:ritual_stone_2",
                        2, 0, 0, "gensokyou:ritual_stone_2",
                        0, 0, -2, "gensokyou:ritual_stone_2",
                        -2, 0, 0, "gensokyou:ritual_stone_2"),
                2, List.of(cell('C', 0, 0, 0)),
                viewOf("C", "gensokyou:ritual_core", "D", "#gensokyou:ritual_stones_2_plus"), INDEX);
        assertTrue(patch.clean(), patch.violations().toString());
        assertEquals('D', patch.adds().get(0).key());
        assertTrue(patch.paletteAdditions().isEmpty());
    }

    @Test
    void offAxisQuadrantMergeProducesOneEntry() {
        // 离轴 (2,0,3) 四象限 → 单条规范 (2,0,3)
        RitualDiffCapture.Patch patch = RitualDiffCapture.capture(
                ws(2, 1, 3, "minecraft:glowstone",
                        -2, 1, 3, "minecraft:glowstone",
                        2, 1, -3, "minecraft:glowstone",
                        -2, 1, -3, "minecraft:glowstone"),
                1, List.of(cell('C', 0, 0, 0)),
                viewOf("C", "gensokyou:ritual_core"), INDEX);
        assertTrue(patch.clean(), patch.violations().toString());
        assertEquals(1, patch.adds().size());
        RitualDiffCapture.AddsEntry e = patch.adds().get(0);
        assertEquals(2, e.x());
        assertEquals(3, e.z());
        assertEquals("minecraft:glowstone", patch.paletteAdditions().get(e.key()));
    }

    @Test
    void symmetryConflictWithinClassIsViolation() {
        // 同一对称类两支方块不同 → 违规，不出条目
        RitualDiffCapture.Patch patch = RitualDiffCapture.capture(
                ws(2, 0, 3, "minecraft:glowstone",
                        -2, 0, 3, "minecraft:sea_lantern",
                        2, 0, -3, "minecraft:glowstone",
                        -2, 0, -3, "minecraft:glowstone"),
                1, List.of(cell('C', 0, 0, 0)),
                viewOf("C", "gensokyou:ritual_core"), INDEX);
        assertFalse(patch.clean());
        assertTrue(patch.violations().stream().anyMatch(v -> v.contains("各支方块")), patch.violations().toString());
    }

    @Test
    void addsIntersectingGroundIsRefusedBySelfCheck() {
        // 直接构造与地基相交的 adds，验证 selfCheck 拦截（绕过 capture 归并）
        List<RitualPattern.BlockEntry> ground = List.of(cell('C', 0, 0, 0), cell('B', 0, 0, 1));
        RitualDiffCapture.Patch bad = new RitualDiffCapture.Patch(
                List.of(new RitualDiffCapture.AddsEntry('A', 0, 0, 1, null)),
                Map.of(), List.of(), 1);
        List<String> errors = RitualDiffCapture.selfCheck(bad, ground);
        assertFalse(errors.isEmpty());
        assertTrue(errors.get(0).contains("与地基相交"), errors.toString());
    }

    @Test
    void axisPairCollisionRefusedBySelfCheck() {
        // (0,1) 与 (1,0) 同层重复登记 → 展开重叠 → 自检拦截
        RitualDiffCapture.Patch bad = new RitualDiffCapture.Patch(
                List.of(new RitualDiffCapture.AddsEntry('A', 0, 0, 1, null),
                        new RitualDiffCapture.AddsEntry('B', 1, 0, 0, null)),
                Map.of(), List.of(), 1);
        List<String> errors = RitualDiffCapture.selfCheck(bad, List.of(cell('C', 0, 0, 0)));
        assertTrue(errors.stream().anyMatch(e -> e.contains("重复登记")), errors.toString());
    }
}
