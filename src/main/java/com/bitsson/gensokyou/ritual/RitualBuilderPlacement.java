package com.bitsson.gensokyou.ritual;

import com.bitsson.gensokyou.item.BuilderSelection;
import com.bitsson.gensokyou.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 仪式构建器的一键搭建算法（纯服务端）。
 * 流程：取最高 level 切片 → 冲突预检（任一格被非目标方块占据即整体中止）→
 * 规范序尽力放置（缺料跳过该格，不降品阶、不重排）。锚点即被点击的核心，
 * 图案加载期已按对称展开为全量，故旋转固定 0。
 */
public final class RitualBuilderPlacement {

    private RitualBuilderPlacement() {
    }

    /** 搭建结果：total 待放置格数、placed 实放格数、blocked 是否因冲突整体中止、conflicts 冲突坐标。 */
    public record Result(int total, int placed, boolean blocked, List<BlockPos> conflicts) {
        public static Result empty() {
            return new Result(0, 0, false, List.of());
        }
    }

    /** 一条材料需求：具体方块 + 全图案所需总数。 */
    public record Requirement(Block block, int count) {
    }

    /** 取图案最高 level 的切片（当前图案均只有 level 1）。 */
    @Nullable
    private static RitualPattern.LevelSlice topSlice(RitualPattern pattern) {
        List<RitualPattern.LevelSlice> levels = pattern.levels();
        return levels.isEmpty() ? null : levels.get(levels.size() - 1);
    }

    /**
     * 计算所选图案在给定品阶下每种具体方块的需求总量（供 tooltip 与菜单展示）。
     * 与 {@link #build} 解析规则一致：EXACT 用固定方块、TAG 按品阶实例化；
     * 锚点格与无法实例化（非品阶标签）的格位不计入需求。
     */
    public static List<Requirement> requirements(RitualPattern pattern, int tier) {
        RitualPattern.LevelSlice slice = topSlice(pattern);
        if (slice == null) {
            return List.of();
        }
        Map<Block, Integer> counts = new LinkedHashMap<>();
        for (RitualPattern.BlockEntry entry : slice.blocks()) {
            if (entry.key() == pattern.anchorKey()) {
                continue;
            }
            RitualPattern.Predicate predicate = pattern.palette().get(entry.key());
            if (predicate == null || !predicate.tracked()) {
                continue;
            }
            Block block = resolveBlock(predicate, tier);
            if (block != null) {
                counts.merge(block, 1, Integer::sum);
            }
        }
        List<Requirement> out = new ArrayList<>();
        counts.forEach((block, count) -> out.add(new Requirement(block, count)));
        return out;
    }

    /**
     * 执行一键搭建。调用方保证 {@code selection} 对应图案存在（{@link #build} 内二次防御）。
     *
     * @param anchorPos 仪式核心坐标（图案锚点/原点）
     */
    public static Result build(ServerLevel level, BlockPos anchorPos,
                               ServerPlayer player, BuilderSelection selection) {
        Optional<RitualPattern> patternOpt = RitualPatternLoader.byId(selection.patternId());
        if (patternOpt.isEmpty()) {
            return Result.empty();
        }
        RitualPattern pattern = patternOpt.get();
        RitualPattern.LevelSlice slice = topSlice(pattern);
        if (slice == null) {
            return Result.empty();
        }
        int tier = selection.tier();

        // 1. 冲突预检：区分"已满足（跳过）""待放置""被非目标方块占据（冲突）"
        List<RitualPattern.BlockEntry> pending = new ArrayList<>();
        List<BlockPos> conflicts = new ArrayList<>();
        for (RitualPattern.BlockEntry entry : slice.blocks()) {
            RitualPattern.Predicate predicate = pattern.palette().get(entry.key());
            if (predicate == null || predicate.kind() == RitualPattern.Kind.IGNORE) {
                continue;
            }
            BlockPos target = anchorPos.offset(entry.x(), entry.y(), entry.z());
            BlockState state = level.getBlockState(target);
            if (predicate.test(state) && orientationSatisfied(entry, state)) {
                continue; // 已满足（含锚点核心、已摆对的石/台、AIR 位为空气）
            }
            if (state.isAir()) {
                pending.add(entry); // 空格，待放置
            } else {
                conflicts.add(target.immutable()); // 被占且不满足谓词（含朝向不符）
            }
        }
        if (!conflicts.isEmpty()) {
            return new Result(pending.size(), 0, true, conflicts);
        }

        // 2. 规范序尽力放置（slice.blocks() 已按 (y,z,x) 排序，pending 保序）
        boolean infinite = player.hasInfiniteMaterials();
        Inventory inventory = player.getInventory();
        int placed = 0;
        for (RitualPattern.BlockEntry entry : pending) {
            BlockState desired = resolveState(pattern, entry, tier);
            if (desired == null) {
                continue; // 无法实例化（非品阶标签或方块不支持朝向常量），跳过该格
            }
            BlockPos target = anchorPos.offset(entry.x(), entry.y(), entry.z());
            if (!infinite && !consumeOne(inventory, desired.getBlock())) {
                continue; // 缺料，跳过该格
            }
            level.setBlockAndUpdate(target, desired);
            level.playSound(null, target, desired
                    .getSoundType()
                    .getPlaceSound(), SoundSource.BLOCKS, 1.0F, 1.0F);
            placed++;
        }
        return new Result(pending.size(), placed, false, List.of());
    }

    /**
     * 解析格位的目标 BlockState（种类 + 品阶 + 朝向一次到位）：
     * EXACT 用固定方块、TAG 按品阶实例化，条目带朝向常量时应用之。
     * 无法解析（谓词缺失/标签无对应品阶成员/方块不支持常量）返回 null。
     */
    @Nullable
    public static BlockState resolveState(RitualPattern pattern,
                                          RitualPattern.BlockEntry entry, int tier) {
        Block block = resolveBlock(pattern.palette().get(entry.key()), tier);
        if (block == null) {
            return null;
        }
        BlockState state = block.defaultBlockState();
        Integer orientation = entry.orientation();
        if (orientation == null) {
            return state;
        }
        return Orientation.supports(state, orientation)
                ? Orientation.apply(state, orientation) : null;
    }

    /** 条目无朝向要求恒满足；有则世界状态须满足期望常量（构建器旋转恒 0）。 */
    private static boolean orientationSatisfied(RitualPattern.BlockEntry entry, BlockState state) {
        return entry.orientation() == null || Orientation.matches(state, entry.orientation());
    }

    /** 解析格位应放置的具体方块：EXACT 用固定 block；TAG 取标签内 tierOf==tier 的方块。 */
    @Nullable
    private static Block resolveBlock(@Nullable RitualPattern.Predicate predicate, int tier) {
        if (predicate == null) {
            return null;
        }
        return switch (predicate.kind()) {
            case EXACT -> predicate.block();
            case TAG -> blockOfTier(predicate, tier);
            default -> null;
        };
    }

    @Nullable
    private static Block blockOfTier(RitualPattern.Predicate predicate, int tier) {
        if (predicate.tag() == null) {
            return null;
        }
        Optional<HolderSet.Named<Block>> holders =
                BuiltInRegistries.BLOCK.getTag(predicate.tag());
        if (holders.isEmpty()) {
            return null;
        }
        for (Holder<Block> holder : holders.get()) {
            if (ModBlocks.tierOf(holder.value()) == tier) {
                return holder.value();
            }
        }
        return null;
    }

    /** 从背包扣一个该方块的物品（精确匹配，忽略 NBT）。返回是否成功。 */
    private static boolean consumeOne(Inventory inventory, Block block) {
        Item item = block.asItem();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty() && stack.is(item)) {
                inventory.removeItem(i, 1);
                return true;
            }
        }
        return false;
    }
}
