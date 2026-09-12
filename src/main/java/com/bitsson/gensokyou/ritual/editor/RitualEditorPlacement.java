package com.bitsson.gensokyou.ritual.editor;

import com.bitsson.gensokyou.ritual.RitualBuilderPlacement;
import com.bitsson.gensokyou.ritual.RitualPattern;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 编辑杖的力建 + sweep（design D2）：把所选阶级累积切片**强制**建于锚点核心——
 * EXACT/TAG 格覆盖放置（含朝向），AIR/IGNORE 格确保为空，工作区内非切片格清扫为空气。
 * 不扣材料、不做冲突中止；与构建杖生存路径（{@link RitualBuilderPlacement}）语义互不侵犯。
 * 纯计算（{@link #plan}）供两段式预览与执行共用；执行（{@link #apply}）先清后建。
 */
public final class RitualEditorPlacement {

    /** 三色格操作：绿=新放置 / 橙=覆盖 / 品红=清扫。 */
    public enum OpKind { PLACE, OVERWRITE, SWEEP }

    public record Op(BlockPos pos, OpKind kind, @Nullable BlockState desired) {
    }

    public record Plan(Map<BlockPos, Op> ops) {
        public int count(OpKind kind) {
            return (int) ops.values().stream().filter(o -> o.kind() == kind).count();
        }
    }

    private RitualEditorPlacement() {
    }

    /** 阶级默认工作区 = 累积切片 AABB（y 偏移 = minY）；无该层退化为单格。 */
    public static Workspace defaultWorkspace(RitualPattern pattern, int tier) {
        RitualPattern.LevelSlice slice = RitualBuilderPlacement.sliceFor(pattern, tier);
        return slice == null ? Workspace.of(1, 1, 1, 0) : Workspace.defaultFor(slice.blocks());
    }

    /**
     * 计算力建+sweep 计划（纯读）。目标切片 = 图案中 level==tier 的累积切片（无则空 plan）。
     *
     * @param workspace 工作区（sweep 唯一视野；切片格位不受工作区约束，区外照建）
     */
    public static Plan plan(Level level, BlockPos anchor, RitualPattern pattern, int tier,
                            Workspace workspace) {
        RitualPattern.LevelSlice slice = RitualBuilderPlacement.sliceFor(pattern, tier);
        Map<BlockPos, Op> ops = new LinkedHashMap<>();
        Set<BlockPos> sliceCells = new LinkedHashSet<>();
        if (slice == null) {
            return new Plan(ops); // 非法阶级：调用方已先行拦截，这里纯防御
        }
        for (RitualPattern.BlockEntry entry : slice.blocks()) {
            BlockPos target = anchor.offset(entry.x(), entry.y(), entry.z());
            if (entry.key() == pattern.anchorKey()) {
                sliceCells.add(target); // 锚点豁免：永不改动
                continue;
            }
            sliceCells.add(target);
            RitualPattern.Predicate predicate = pattern.palette().get(entry.key());
            if (predicate == null) {
                continue;
            }
            BlockState current = level.getBlockState(target);
            switch (predicate.kind()) {
                case AIR, IGNORE -> {
                    if (!current.isAir()) {
                        ops.put(target, new Op(target, OpKind.SWEEP, null));
                    }
                }
                default -> {
                    BlockState desired = RitualBuilderPlacement.resolveState(pattern, entry, tier);
                    if (desired == null) {
                        continue; // 无法实例化（同构建杖跳过规则）
                    }
                    if (current.equals(desired)) {
                        continue; // 已精确满足（同块同状态）
                    }
                    ops.put(target, new Op(target, current.isAir() ? OpKind.PLACE : OpKind.OVERWRITE, desired));
                }
            }
        }
        // 工作区清扫：区 AABB − 切片格 → 空气
        int hx = workspace.halfX();
        int hz = workspace.halfZ();
        for (int dx = -hx; dx <= hx; dx++) {
            for (int dz = -hz; dz <= hz; dz++) {
                for (int dy = workspace.minY(); dy <= workspace.maxY(); dy++) {
                    BlockPos pos = anchor.offset(dx, dy, dz);
                    if (sliceCells.contains(pos)) {
                        continue;
                    }
                    if (!level.getBlockState(pos).isAir()) {
                        ops.put(pos, new Op(pos, OpKind.SWEEP, null));
                    }
                }
            }
        }
        return new Plan(ops);
    }

    /** 执行结果计数（回执用）。 */
    public record ExecResult(int placed, int overwritten, int swept) {
    }

    /** 先清后建（同一次确认原子执行）。调用方保证创造玩家。 */
    public static ExecResult apply(ServerLevel level, Plan plan, BlockPos anchor) {
        int placed = 0;
        int overwritten = 0;
        int swept = 0;
        for (Op op : plan.ops().values()) {
            if (op.kind() == OpKind.SWEEP) {
                level.destroyBlock(op.pos(), false, null);
                swept++;
            }
        }
        for (Op op : plan.ops().values()) {
            if (op.kind() == OpKind.SWEEP || op.desired() == null) {
                continue;
            }
            // 锚点核心防御：任何情况下不覆盖锚点位（plan 已豁免，双保险）
            if (op.pos().equals(anchor)) {
                continue;
            }
            level.setBlockAndUpdate(op.pos(), op.desired());
            level.playSound(null, op.pos(), op.desired().getSoundType().getPlaceSound(),
                    SoundSource.BLOCKS, 1.0F, 1.0F);
            if (op.kind() == OpKind.PLACE) {
                placed++;
            } else {
                overwritten++;
            }
        }
        return new ExecResult(placed, overwritten, swept);
    }

    /** 世界扫格：把工作区 AABB 内非空气格扫成 blockId+朝向常量（diff 捕获输入，纯读）。 */
    public static Map<BlockPos3, RitualDiffCapture.Cell> scanWorkspace(Level level, BlockPos anchor,
                                                                       Workspace workspace) {
        Map<BlockPos3, RitualDiffCapture.Cell> cells = new LinkedHashMap<>();
        int hx = workspace.halfX();
        int hz = workspace.halfZ();
        for (int dx = -hx; dx <= hx; dx++) {
            for (int dz = -hz; dz <= hz; dz++) {
                for (int dy = workspace.minY(); dy <= workspace.maxY(); dy++) {
                    BlockPos pos = anchor.offset(dx, dy, dz);
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir()) {
                        continue;
                    }
                    String id = net.minecraft.core.registries.BuiltInRegistries.BLOCK
                            .getKey(state.getBlock()).toString();
                    int orientation = com.bitsson.gensokyou.ritual.Orientation.detect(state);
                    cells.put(new BlockPos3(dx, dy, dz), new RitualDiffCapture.Cell(id, orientation));
                }
            }
        }
        return cells;
    }
}
