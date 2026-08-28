package com.bitsson.gensokyou.ritual;

import com.bitsson.gensokyou.block.entity.RitualPedestalBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 配方无序匹配内核：收集成型结构内全部祭品台持有栈，
 * 与配方 ingredients 做「精确物品条目优先于标签条目」的贪心分配；
 * 严格等值——存在无法被任何条目消耗的多余物品即不匹配；全有全无应用。
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
        if (recipe.ingredients().isEmpty()) {
            return Optional.empty();
        }
        List<Pool> pools = collectPools(match, level);
        // 精确物品条目在前，标签条目在后
        List<RitualRecipe.Ingredient> ordered = recipe.ingredients().stream()
                .sorted((a, b) -> Boolean.compare(a.tag() != null, b.tag() != null))
                .toList();
        List<Take> takes = new ArrayList<>();
        for (RitualRecipe.Ingredient ingredient : ordered) {
            int remaining = ingredient.count();
            for (Pool pool : pools) {
                if (remaining <= 0) {
                    break;
                }
                int already = takenAmount(takes, pool.pos());
                int available = pool.stack().getCount() - already;
                if (available <= 0 || !ingredient.matches(pool.stack())) {
                    continue;
                }
                int take = Math.min(remaining, available);
                remaining -= take;
                merges(takes, pool.pos(), take);
            }
            if (remaining > 0) {
                return Optional.empty();
            }
        }
        // 严格等值：不允许存在未被消耗的多余物品
        for (Pool pool : pools) {
            if (pool.stack().getCount() - takenAmount(takes, pool.pos()) > 0) {
                return Optional.empty();
            }
        }
        return Optional.of(new Match(recipe, List.copyOf(takes)));
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

    private static void merges(List<Take> takes, BlockPos pos, int amount) {
        for (int i = 0; i < takes.size(); i++) {
            Take take = takes.get(i);
            if (take.pos().equals(pos)) {
                takes.set(i, new Take(pos, take.amount() + amount));
                return;
            }
        }
        takes.add(new Take(pos.immutable(), amount));
    }

    /** 台面池条目。 */
    public record Pool(BlockPos pos, ItemStack stack) {
    }
}
