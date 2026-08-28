package com.bitsson.gensokyou.ritual;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 仪式配方（数据驱动，一文件一条）。
 * 匹配语义：成型结构内全部祭品台持有物构成多重集，与 ingredients 做**严格等值**无序比对；
 * 扣减采用「精确物品条目优先于标签条目」的贪心分配，全有全无。
 */
public record RitualRecipe(ResourceLocation id, ResourceLocation patternId, Mode mode, int minTier,
                           List<Ingredient> ingredients, int spCost,
                           @Nullable ResultHolder result, @Nullable String effect) {

    public enum Mode { ACTIVATION, PASSIVE }

    /** 原料条目：精确物品或物品标签。 */
    public record Ingredient(@Nullable Item item, @Nullable TagKey<Item> tag, int count) {

        public boolean matches(ItemStack stack) {
            if (stack.isEmpty()) {
                return false;
            }
            if (tag != null) {
                return stack.is(tag);
            }
            return item != null && stack.is(item);
        }
    }

    /** 实物产物。 */
    public record ResultHolder(Item item, int count) {

        public ItemStack stack() {
            return new ItemStack(item, count);
        }
    }

    public boolean activation() {
        return mode == Mode.ACTIVATION;
    }

    public boolean passive() {
        return mode == Mode.PASSIVE;
    }

    /** 展示名语言键（回退 id 路径）。 */
    public Component displayName() {
        return Component.translatableWithFallback(
                "jei." + id.getNamespace() + ".recipe." + id.getPath(),
                id.getPath().replace('_', ' '));
    }

    @Nullable
    public ItemStack resultStack() {
        return result == null ? null : result.stack();
    }

    public static Item itemOrThrow(String id) {
        return BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(id))
                .orElseThrow(() -> new IllegalArgumentException("unknown item: " + id));
    }

    public static TagKey<Item> tagOf(String id) {
        return TagKey.create(Registries.ITEM, ResourceLocation.parse(id.substring(1)));
    }
}
