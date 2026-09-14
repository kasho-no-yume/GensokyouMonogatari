package com.bitsson.gensokyou.ritual;

import com.bitsson.gensokyou.block.entity.RitualPedestalBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 配方无序匹配内核：收集成型结构内全部祭品台持有栈，与配方 ingredients 做
 * 「精确物品条目优先于标签条目」的贪心分配；全有全无应用。
 * 比对语义按配方 match 模式分派：EXACT 严格等值（多余即 fail），MAX 子集命中
 * （多余留台不动）；跨候选的 MAX 选择见 {@link #matchMax}。
 */
public final class RitualRecipeMatcher {

    /** 一次成功匹配的扣减账本（pos 按规范序：y↑、z↓、x→）。 */
    public record Take(BlockPos pos, int amount) {
    }

    public record Match(RitualRecipe recipe, List<Take> takes) {
    }

    private RitualRecipeMatcher() {
    }

    /** 收集台面池：规范序排序的 (位置, 可变副本) 对。 */
    public static List<Pool> collectPools(RitualMatch match, Level level) {
        List<BlockPos> positions = new ArrayList<>();
        match.keyedPositions().values().forEach(positions::addAll);
        positions.sort((a, b) -> {
            if (a.getY() != b.getY()) {
                return Integer.compare(a.getY(), b.getY());
            }
            if (a.getZ() != b.getZ()) {
                return Integer.compare(a.getZ(), b.getZ());
            }
            return Integer.compare(a.getX(), b.getX());
        });
        List<Pool> pools = new ArrayList<>();
        for (BlockPos pos : positions) {
            if (level.getBlockEntity(pos) instanceof RitualPedestalBlockEntity pedestal
                    && !pedestal.getHeld().isEmpty()) {
                pools.add(new Pool(pos.immutable(), pedestal.getHeld()));
            }
        }
        return pools;
    }

    /** 尝试匹配；成功返回含账本的 Match，失败返回 empty（不做任何修改）。 */
    public static Optional<Match> match(RitualRecipe recipe, RitualMatch match, Level level) {
        return match(recipe, collectPools(match, level));
    }

    /**
     * 跨候选最大匹配（max 模式）：取消耗原料总量（Σcount）最大者；平局取候选参数序
     * 靠前者（未定义行为，仅需确定）。无命中返回 empty。
     */
    public static Optional<Match> matchMax(List<RitualRecipe> candidates,
                                           RitualMatch match, Level level) {
        return matchMax(candidates, collectPools(match, level));
    }

    /** 跨候选最大匹配（台面池已收集口径）。 */
    public static Optional<Match> matchMax(List<RitualRecipe> candidates, List<Pool> pools) {
        Optional<Match> best = Optional.empty();
        int bestTotal = -1;
        for (RitualRecipe recipe : candidates) {
            if (recipe.match() != RitualRecipe.MatchMode.MAX) {
                continue;
            }
            Optional<Match> attempt = match(recipe, pools);
            if (attempt.isPresent() && recipe.totalCount() > bestTotal) {
                best = attempt;
                bestTotal = recipe.totalCount();
            }
        }
        return best;
    }

    /** 单配方匹配（台面池已收集口径）：按 recipe.match 模式分派严格等值 / 子集命中。 */
    public static Optional<Match> match(RitualRecipe recipe, List<Pool> pools) {
        if (recipe.ingredients().isEmpty()) {
            return Optional.empty();
        }
        List<Take> takes = allocate(recipe, pools);
        if (takes == null) {
            return Optional.empty();
        }
        // 严格等值（EXACT）：不允许存在未被消耗的多余物品；子集（MAX）：多余留台
        if (recipe.match() == RitualRecipe.MatchMode.EXACT) {
            for (Pool pool : pools) {
                if (pool.stack().getCount() - takenAmount(takes, pool.pos()) > 0) {
                    return Optional.empty();
                }
            }
        }
        return Optional.of(new Match(recipe, takes));
    }

    /** 贪心分配原料到台面池（真实物品栈适配层，核心算法见 {@link RitualMatchKernel}）；不足返回 null。 */
    @Nullable
    private static List<Take> allocate(RitualRecipe recipe, List<Pool> pools) {
        // 精确物品条目在前，标签条目在后
        List<RitualRecipe.Ingredient> ordered = recipe.ingredients().stream()
                .sorted((a, b) -> Boolean.compare(a.tag() != null, b.tag() != null))
                .toList();
        int[] required = new int[ordered.size()];
        int[] poolCounts = new int[pools.size()];
        boolean[][] accepts = new boolean[ordered.size()][pools.size()];
        for (int j = 0; j < pools.size(); j++) {
            poolCounts[j] = pools.get(j).stack().getCount();
        }
        for (int i = 0; i < ordered.size(); i++) {
            RitualRecipe.Ingredient ingredient = ordered.get(i);
            required[i] = ingredient.count();
            for (int j = 0; j < pools.size(); j++) {
                accepts[i][j] = ingredient.matches(pools.get(j).stack());
            }
        }
        int[] taken = RitualMatchKernel.allocate(required, poolCounts, accepts);
        if (taken == null) {
            return null;
        }
        List<Take> takes = new ArrayList<>();
        for (int j = 0; j < pools.size(); j++) {
            if (taken[j] > 0) {
                takes.add(new Take(pools.get(j).pos().immutable(), taken[j]));
            }
        }
        return takes;
    }

    /** 应用账本：逐台扣减并同步客户端。 */
    public static void apply(Level level, List<Take> takes) {
        for (Take take : takes) {
            if (level.getBlockEntity(take.pos()) instanceof RitualPedestalBlockEntity pedestal) {
                ItemStack held = pedestal.getHeld();
                int remaining = Math.max(0, held.getCount() - take.amount());
                pedestal.setHeld(remaining == 0 ? ItemStack.EMPTY : held.copyWithCount(remaining));
            }
        }
    }

    private static int takenAmount(List<Take> takes, BlockPos pos) {
        for (Take take : takes) {
            if (take.pos().equals(pos)) {
                return take.amount();
            }
        }
        return 0;
    }

    /** 台面池条目。 */
    public record Pool(BlockPos pos, ItemStack stack) {
    }
}
