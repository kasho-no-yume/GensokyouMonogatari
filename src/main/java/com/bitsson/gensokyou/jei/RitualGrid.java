package com.bitsson.gensokyou.jei;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.ritual.RitualPattern;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

public record RitualGrid(List<GridCell> cells, int minX, int minY, int maxX, int maxY,
                         int anchorX, int anchorY) {

    public static final int CELL = 18;
    public static final int NO_ANCHOR = Integer.MIN_VALUE;

    public record GridCell(int x, int y, ItemStack stack, boolean anchor, boolean missingTag,
                           RitualPattern.Predicate pred) {
    }

    /** 将一个层级的稀疏偏移表铺成像素网格：按 y 分带、带内 z 纵向 x 横向。 */
    public static RitualGrid build(RitualPattern pattern, int levelIndex) {
        List<RitualPattern.LevelSlice> slices = levelIndex < 0 || levelIndex >= pattern.levels().size()
                ? pattern.levels()
                : List.of(pattern.levels().get(levelIndex));

        List<GridCell> cells = new ArrayList<>();
        int cursorY = 0;
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int anchorX = NO_ANCHOR;
        int anchorY = NO_ANCHOR;
        for (RitualPattern.LevelSlice slice : slices) {
            if (slice.blocks().isEmpty()) {
                continue;
            }
            int bandMinX = Integer.MAX_VALUE;
            int bandMinZ = Integer.MAX_VALUE;
            int bandMaxZ = Integer.MIN_VALUE;
            Map<Integer, List<RitualPattern.BlockEntry>> byLayer = new TreeMap<>();
            for (RitualPattern.BlockEntry block : slice.blocks()) {
                bandMinX = Math.min(bandMinX, block.x());
                bandMinZ = Math.min(bandMinZ, block.z());
                bandMaxZ = Math.max(bandMaxZ, block.z());
                byLayer.computeIfAbsent(block.y(), k -> new ArrayList<>()).add(block);
            }
            for (List<RitualPattern.BlockEntry> layer : byLayer.values()) {
                for (RitualPattern.BlockEntry block : layer) {
                    int px = (block.x() - bandMinX) * CELL;
                    int py = cursorY + (block.z() - bandMinZ) * CELL;
                    char key = block.key();
                    RitualPattern.Predicate pred = pattern.palette().get(key);
                    boolean missingTag = false;
                    ItemStack stack;
                    if (pred == null || !pred.tracked() || pred.kind() == RitualPattern.Kind.AIR) {
                        continue;
                    }
                    if (pred.kind() == RitualPattern.Kind.TAG) {
                        ItemStack rep = tagRepresentative(pred.tag());
                        if (rep == null) {
                            missingTag = true;
                            stack = new ItemStack(Items.BARRIER);
                        } else {
                            stack = rep;
                        }
                    } else if (pred.kind() == RitualPattern.Kind.EXACT && pred.block() != null) {
                        stack = new ItemStack(pred.block());
                    } else {
                        stack = ItemStack.EMPTY;
                    }
                    cells.add(new GridCell(px, py, stack, key == pattern.anchorKey(), missingTag, pred));
                    if (key == pattern.anchorKey()) {
                        anchorX = px;
                        anchorY = py;
                    }
                    minX = Math.min(minX, px);
                    minY = Math.min(minY, py);
                    maxX = Math.max(maxX, px + CELL);
                    maxY = Math.max(maxY, py + CELL);
                }
                cursorY += (bandMaxZ - bandMinZ + 1) * CELL;
            }
        }
        if (cells.isEmpty()) {
            return new RitualGrid(cells, 0, 0, 0, 0, NO_ANCHOR, NO_ANCHOR);
        }
        return new RitualGrid(cells, minX, minY, maxX, maxY, anchorX, anchorY);
    }

    /** 全部层级中可用于查找关联的去重物品（屏障占位不参与查找）。 */
    public static List<ItemStack> distinctStacks(RitualPattern pattern) {
        Map<Item, ItemStack> unique = new LinkedHashMap<>();
        for (GridCell cell : build(pattern, -1).cells()) {
            if (!cell.stack().isEmpty() && !cell.missingTag()) {
                unique.putIfAbsent(cell.stack().getItem(), cell.stack());
            }
        }
        return List.copyOf(unique.values());
    }

    @Nullable
    private static ItemStack tagRepresentative(@Nullable TagKey<Block> tag) {
        if (tag == null) {
            return null;
        }
        Optional<HolderSet.Named<Block>> named = BuiltInRegistries.BLOCK.getTag(tag);
        if (named.isPresent()) {
            for (Holder<Block> holder : named.get()) {
                Item item = holder.value().asItem();
                ItemStack stack = new ItemStack(item);
                if (!stack.isEmpty()) {
                    return stack;
                }
            }
        }
        return null;
    }

    static Component displayName(ResourceLocation id) {
        return Component.translatableWithFallback(
                "jei." + id.getNamespace() + ".ritual." + id.getPath(),
                id.getPath().replace('_', ' '));
    }
}
