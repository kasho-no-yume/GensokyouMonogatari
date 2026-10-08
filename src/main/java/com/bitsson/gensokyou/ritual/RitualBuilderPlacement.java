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
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
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
 * 流程：取所选阶级（level == 品阶）的累积切片 → 冲突预检（任一格被非目标方块占据即整体中止）→
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
        /** 该方块是否没有物品形态（盆栽等）：不可作为物品获得，不参与持有统计与扣料。 */
        public boolean itemLess() {
            return RitualBuilderPlacement.itemLess(block);
        }
    }

    /** 方块是否没有对应物品（`asItem() == Items.AIR`，如各类盆栽）。 */
    public static boolean itemLess(Block block) {
        return block.asItem() == Items.AIR;
    }

    /**
     * 格位三分类：pending 待放置条目、conflicts 被非目标方块占据（含朝向不符）、
     * airConflicts AIR 谓词格被占（投影画红框、搭建同样零容忍）。
     */
    public record Classification(List<RitualPattern.BlockEntry> pending,
                                 List<Conflict> conflicts,
                                 List<Conflict> airConflicts) {
    }

    /** 冲突格：世界坐标 + 对应格位条目（坐标供红框下发，条目供投影解析目标态渲染红幽灵）。 */
    public record Conflict(BlockPos pos, RitualPattern.BlockEntry entry) {
    }

    /** 取图案中 level == 所选阶级 的累积切片；图案无该阶级层级时返回 null（非法选择，调用方提示）。 */
    @Nullable
    public static RitualPattern.LevelSlice sliceFor(RitualPattern pattern, int tier) {
        for (RitualPattern.LevelSlice slice : pattern.levels()) {
            if (slice.level() == tier) {
                return slice;
            }
        }
        return null;
    }

    /** 所选阶级在图案中是否有对应层级（杖侧分发预检用）。 */
    public static boolean hasLevel(RitualPattern pattern, int tier) {
        return sliceFor(pattern, tier) != null;
    }

    /**
     * 计算所选图案在给定阶级（品阶）下每种具体方块的需求总量（供 tooltip 与菜单展示），
     * 口径 = 裸核心建到该阶的累积切片。
     * 与 {@link #build} 解析规则一致：EXACT 用固定方块、TAG 按品阶实例化
     * （纯无阶标签回退唯一成员，如祭品台）；
     * 锚点格与无法实例化（含阶标签混入无阶成员等）的格位不计入需求；所选阶级无对应层级时返回空列表。
     */
    public static List<Requirement> requirements(RitualPattern pattern, int tier) {
        RitualPattern.LevelSlice slice = sliceFor(pattern, tier);
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
     * 格位三分类纯函数（服务端搭建预检与客户端投影渲染共用的单一事实源，纯读）：
     * 已满足谓词（含朝向）的格不入任何列表；空格入 pending；
     * 被非目标方块占据的格按谓词种类分流——AIR 谓词格入 airConflicts，其余入 conflicts。
     *
     * @param anchorPos 仪式核心坐标（图案锚点/原点）
     */
    public static Classification classify(Level level, BlockPos anchorPos,
                                          RitualPattern pattern, int tier) {
        RitualPattern.LevelSlice slice = sliceFor(pattern, tier);
        if (slice == null) {
            return new Classification(List.of(), List.of(), List.of());
        }
        List<RitualPattern.BlockEntry> pending = new ArrayList<>();
        List<Conflict> conflicts = new ArrayList<>();
        List<Conflict> airConflicts = new ArrayList<>();
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
            } else if (predicate.kind() == RitualPattern.Kind.AIR) {
                airConflicts.add(new Conflict(target.immutable(), entry)); // AIR 谓词格被占：投影画红框
            } else {
                conflicts.add(new Conflict(target.immutable(), entry)); // 被占且不满足谓词（含朝向不符）
            }
        }
        return new Classification(pending, conflicts, airConflicts);
    }

    /**
     * 执行一键搭建。调用方保证 {@code selection} 对应图案存在（{@link #build} 内二次防御）。
     *
     * @param anchorPos 仪式核心坐标（图案锚点/原点）
     */
    public static Result build(ServerLevel level, BlockPos anchorPos,
                               ServerPlayer player, BuilderSelection selection) {
        return build(level, anchorPos, player, selection, null);
    }

    /**
     * 执行一键搭建（带绑定无尽藏兜底）。背包不足时若 {@code bind} 闸门满足，从绑定仓储补料。
     *
     * @param anchorPos 仪式核心坐标（图案锚点/原点）
     * @param bind      绑定的无尽藏核心（null = 不补料，仅用背包）
     */
    public static Result build(ServerLevel level, BlockPos anchorPos, ServerPlayer player,
                               BuilderSelection selection,
                               @Nullable com.bitsson.gensokyou.item.BuilderBind bind) {
        Optional<RitualPattern> patternOpt = RitualPatternLoader.byId(selection.patternId());
        if (patternOpt.isEmpty()) {
            return Result.empty();
        }
        RitualPattern pattern = patternOpt.get();
        int tier = selection.tier();

        // 1. 冲突预检（共用三分类；Result.conflicts 取并集，保持 AIR 占位格红框现状）
        Classification classification = classify(level, anchorPos, pattern, tier);
        List<RitualPattern.BlockEntry> pending = classification.pending();
        List<BlockPos> conflicts = new ArrayList<>();
        classification.conflicts().forEach(c -> conflicts.add(c.pos()));
        classification.airConflicts().forEach(c -> conflicts.add(c.pos()));
        if (!conflicts.isEmpty()) {
            return new Result(pending.size(), 0, true, List.copyOf(conflicts));
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
            if (!infinite && !consumeOne(inventory, desired.getBlock(), level, bind)) {
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

    /** 解析格位应放置的具体方块：EXACT 用固定 block；TAG 取标签内 tierOf==tier 的方块（纯无阶标签回退唯一成员）。 */
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

    /**
     * 标签内取所选品阶成员；无匹配品阶时，若标签全部成员不受阶（tierOf==-1，
     * 如单方块化的祭品台）则回退取唯一成员，否则 null（该格计入"无法放置"）。
     */
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
        Block soleUntiered = null;
        boolean mixed = false;
        for (Holder<Block> holder : holders.get()) {
            int memberTier = ModBlocks.tierOf(holder.value());
            if (memberTier == tier) {
                return holder.value();
            }
            if (memberTier >= 0) {
                mixed = true;
            } else if (soleUntiered == null) {
                soleUntiered = holder.value();
            }
        }
        return mixed ? null : soleUntiered;
    }

    /** 从背包扣一个该方块的物品（精确匹配，忽略 NBT）。返回是否成功。 */
    private static boolean consumeOne(Inventory inventory, Block block) {
        Item item = block.asItem();
        if (item == Items.AIR) {
            return true; // 无物品形态的方块（盆栽等）：构建杖直接生成，不参与扣料
        }
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty() && stack.is(item)) {
                inventory.removeItem(i, 1);
                return true;
            }
        }
        return false;
    }

    /**
     * 供应一格所需方块：背包优先，不足时从绑定无尽藏仓储兜底（真实消耗）。
     * 无物品形态的方块恒成功（直接放置、不扣料）。
     */
    private static boolean consumeOne(Inventory inventory, Block block, ServerLevel level,
                                      @Nullable com.bitsson.gensokyou.item.BuilderBind bind) {
        if (consumeOne(inventory, block)) {
            return true;
        }
        return BoundSupply.tryConsumeOne(level, bind, block);
    }
}
