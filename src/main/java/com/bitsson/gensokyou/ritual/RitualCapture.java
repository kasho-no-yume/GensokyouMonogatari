package com.bitsson.gensokyou.ritual;

import com.bitsson.gensokyou.Gensokyou;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 仪式捕获管线：扫描 AABB → 以区域内唯一祭仪核心为锚点原点 →
 * 按对称类归并（同类不同字符即违规）→ 输出规范四分之一的稀疏偏移 JSON 骨架。
 * 构造仗与 /gs_ritual_capture 共用。
 */
public final class RitualCapture {

    public record Result(String json, List<String> violations, int blocksCaptured) {
    }

    private record Slot(int x, int y, int z) {
    }

    private RitualCapture() {
    }

    public static Result capture(Level level, String name, BlockPos min, BlockPos max) {
        List<String> violations = new ArrayList<>();

        // 锚点：区域内恰一个祭仪核心，作为原点
        BlockPos anchor = null;
        int coreCount = 0;
        for (BlockPos pos : (Iterable<BlockPos>) BlockPos.betweenClosed(min, max)) {
            if (isRitualCore(level, pos)) {
                coreCount++;
                anchor = pos.immutable();
            }
        }
        if (coreCount == 0) {
            violations.add("no ritual core in region");
        } else if (coreCount > 1) {
            violations.add("multiple ritual cores (" + coreCount + "), using the first found");
        }
        BlockPos origin = anchor != null ? anchor : min;

        // 全量扫描：对称类 → 字符（核心记 'C'，其余方块聚类分配 A,B,D,E,...）
        Map<Block, Character> keys = new HashMap<>();
        java.util.Set<Character> usedKeys = new java.util.HashSet<>();
        usedKeys.add('C');
        Map<String, Character> classes = new HashMap<>();
        List<String> conflicts = new ArrayList<>();
        int count = 0;
        for (BlockPos pos : (Iterable<BlockPos>) BlockPos.betweenClosed(min, max)) {
            Block block = level.getBlockState(pos).getBlock();
            if (block.defaultBlockState().isAir()) {
                continue;
            }
            count++;
            char key = isRitualCore(level, pos) ? 'C'
                    : keys.computeIfAbsent(block, b -> nextKey(usedKeys));
            if (!isRitualCore(level, pos)) {
                usedKeys.add(key);
            }
            String classKey = canonicalKey(pos.getX() - origin.getX(), pos.getY() - origin.getY(),
                    pos.getZ() - origin.getZ());
            Character previous = classes.putIfAbsent(classKey, key);
            if (previous != null && previous != key) {
                conflicts.add("symmetry conflict: '" + previous + "' vs '" + key
                        + "' on class " + classKey);
            }
        }
        // 同类冲突去重后并入违规列表
        java.util.LinkedHashSet<String> deduped = new java.util.LinkedHashSet<>(conflicts);
        violations.addAll(deduped);
        int effective = count - deduped.size();

        StringBuilder palette = new StringBuilder();
        palette.append("\n    \"C\": \"").append(Gensokyou.MODID).append(":ritual_core\"");
        for (Map.Entry<Block, Character> entry : keys.entrySet()) {
            ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(entry.getKey());
            palette.append(",\n    \"").append(entry.getValue()).append("\": \"")
                    .append(blockId).append('"');
        }

        // 规范四分之一输出：off-axis 取绝对值象限；axis 归并为北位 (0,d)；核心固定在原点
        List<Slot> slots = new ArrayList<>();
        Map<Slot, Character> quarter = new HashMap<>();
        for (Map.Entry<String, Character> entry : classes.entrySet()) {
            String[] parts = entry.getKey().split(",");
            int y = Integer.parseInt(parts[1]);
            Slot slot;
            if (parts[0].equals("O")) {
                slot = new Slot(0, y, 0);
            } else if (parts[0].equals("A")) {
                slot = new Slot(0, y, Integer.parseInt(parts[2]));
            } else {
                slot = new Slot(Integer.parseInt(parts[2]), y, Integer.parseInt(parts[3]));
            }
            quarter.put(slot, entry.getValue());
        }
        quarter.entrySet().stream()
                .sorted((a, b) -> {
                    if (a.getKey().y() != b.getKey().y()) {
                        return Integer.compare(a.getKey().y(), b.getKey().y());
                    }
                    if (a.getKey().z() != b.getKey().z()) {
                        return Integer.compare(a.getKey().z(), b.getKey().z());
                    }
                    return Integer.compare(a.getKey().x(), b.getKey().x());
                })
                .forEachOrdered(entry -> slots.add(entry.getKey()));

        StringBuilder blocksBuilder = new StringBuilder();
        boolean first = true;
        for (Slot slot : slots) {
            blocksBuilder.append(first ? "" : ",\n")
                    .append("      { \"key\": \"").append(quarter.get(slot))
                    .append("\", \"x\": ").append(slot.x())
                    .append(", \"y\": ").append(slot.y())
                    .append(", \"z\": ").append(slot.z()).append(" }");
            first = false;
        }
        String json = "{\n"
                + "  \"id\": \"" + Gensokyou.MODID + ":" + name + "\",\n"
                + "  \"anchorKey\": \"C\",\n"
                + "  \"palette\": {" + palette + "\n  },\n"
                + "  \"levels\": [\n    { \"level\": 1, \"blocks\": [\n" + blocksBuilder
                + "\n    ] }\n  ]\n}";
        return new Result(json, List.copyOf(violations), Math.max(0, effective));
    }

    /** 分配下一个未占用且非 'C' 的字符键。 */
    private static char nextKey(java.util.Set<Character> used) {
        for (char k = 'A'; k <= 'Z'; k++) {
            if (k != 'C' && !used.contains(k)) {
                return k;
            }
        }
        throw new IllegalStateException("too many distinct blocks");
    }

    /** 规范对称键：off-axis 取绝对值象限；axis 归并为北位；核心为 O。 */
    private static String canonicalKey(int x, int y, int z) {
        if (x == 0 && z == 0) {
            return "O," + y;
        }
        if (x == 0 || z == 0) {
            return "A," + y + "," + Math.max(Math.abs(x), Math.abs(z));
        }
        return "Q," + y + "," + Math.abs(x) + "," + Math.abs(z);
    }

    private static boolean isRitualCore(Level level, BlockPos pos) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());
        return Gensokyou.MODID.equals(id.getNamespace()) && id.getPath().equals("ritual_core");
    }
}
