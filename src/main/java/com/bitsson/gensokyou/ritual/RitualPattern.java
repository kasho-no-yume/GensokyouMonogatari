package com.bitsson.gensokyou.ritual;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 仪式结构定义（schema v3，稀疏偏移格式）。
 * 每层级仅存对称规范四分之一的方块偏移，加载期展开为全量：
 * off-axis (x,z) → (±x,y,±z)；axis (0,d) → (0,±d) 与 (±d,0)。
 * 锚点核心即原点；展开条目按 (y,z,x) 排序，该顺序即祭品台 slot 寻址的规范序。
 */
public record RitualPattern(ResourceLocation id, char anchorKey,
                            Map<Character, Predicate> palette,
                            List<LevelSlice> levels,
                            List<Offering> requirements,
                            boolean toggleable,
                            List<Integer> tiers) {

    /** 全品阶 0-5（图案未显式声明 tiers 时的缺省，保持向后兼容）。 */
    public static final List<Integer> ALL_TIERS = List.of(0, 1, 2, 3, 4, 5);

    /** 该仪式是否含受品阶影响的格位（标签谓词）——决定构建器是否显示品阶选择。 */
    public boolean hasTieredSlots() {
        return palette.values().stream().anyMatch(p -> p.kind() == Kind.TAG);
    }

    public enum Kind { EXACT, TAG, AIR, IGNORE }

    public record Predicate(Kind kind, @Nullable Block block, @Nullable TagKey<Block> tag) {

        public static final Predicate IGNORE = new Predicate(Kind.IGNORE, null, null);
        public static final Predicate AIR = new Predicate(Kind.AIR, null, null);

        public boolean test(BlockState state) {
            return switch (kind) {
                case IGNORE -> true;
                case AIR -> state.isAir();
                case EXACT -> block != null && state.is(block);
                case TAG -> tag != null && state.is(tag);
            };
        }

        public boolean tracked() {
            return kind != Kind.IGNORE;
        }
    }

    /** 单个必需方块：相对锚点的完整偏移（已展开）。orientation 为 {@link Orientation} 常量 id，null = 无朝向要求。 */
    public record BlockEntry(char key, int x, int y, int z, @Nullable Integer orientation) {
    }

    /** 一个层级（等级）：blocks 为展开并排序后的全量偏移表。 */
    public record LevelSlice(int level, List<BlockEntry> blocks) {

        public int maxY() {
            return blocks.stream().mapToInt(BlockEntry::y).max().orElse(0);
        }
    }

    public enum ConsumeMode { NONE, ON_ACTIVATE, PERIODIC }

    /**
     * 单个祭品台要求。slot 为该 key 全部格位在规范序下的序号
     * （规范序 = 层自下而上、z 自北向南、x 自西向东）。
     */
    public record Offering(char key, int slot, ItemRequirement item, int count,
                           ConsumeMode consume, int period) {

        public boolean periodic() {
            return consume == ConsumeMode.PERIODIC;
        }
    }

    /** 物品要求：精确物品或 #物品标签。 */
    public record ItemRequirement(@Nullable Item item, @Nullable TagKey<Item> tag) {

        public boolean matches(ItemStack stack) {
            if (stack.isEmpty()) {
                return false;
            }
            if (tag != null) {
                return stack.is(tag);
            }
            return item != null && stack.is(item);
        }

        /** 代表物品（标签取首个有效成员），用于 UI/JEI 展示。 */
        public ItemStack representative() {
            if (item != null) {
                return new ItemStack(item);
            }
            if (tag != null) {
                Optional<HolderSet.Named<Item>> holders = BuiltInRegistries.ITEM.getTag(tag);
                if (holders.isPresent()) {
                    for (Holder<Item> holder : holders.get()) {
                        return new ItemStack(holder.value());
                    }
                }
            }
            return ItemStack.EMPTY;
        }
    }
}
