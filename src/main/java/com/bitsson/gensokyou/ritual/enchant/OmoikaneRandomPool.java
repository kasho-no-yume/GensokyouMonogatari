package com.bitsson.gensokyou.ritual.enchant;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.enchantment.Enchantment;

import java.util.ArrayList;
import java.util.List;

/**
 * 思兼神封书模式的随机词条池（世界无关纯静态内核 + 注册表薄壳）。
 *
 * <p><b>按阶池过滤</b>（标签驱动，{@code EnchantmentTags.CURSE / TREASURE}，
 * 对 mod 附魔比 {@code isCurse()/isTreasureOnly()} 更稳）：
 * 1 阶含诅咒·不含宝藏；2 阶皆不含；3 阶不含诅咒·含宝藏。
 *
 * <p><b>抽取</b>：不放回等概率抽 N 个（天然无重复），N ≤ 池大小。
 *
 * <p><b>等级</b>：1 阶全 1 级；2 阶 1~maxLevel 等概率；3 阶固定 maxLevel+1。
 */
public final class OmoikaneRandomPool {

    private OmoikaneRandomPool() {
    }

    public static boolean tierAllowsCurse(int ritualTier) {
        return ritualTier <= 1;
    }

    public static boolean tierAllowsTreasure(int ritualTier) {
        return ritualTier >= 3;
    }

    /** 按阶过滤后的候选池（注册表全量，含 mod 附魔）。 */
    public static List<Holder<Enchantment>> pool(RegistryAccess access, int ritualTier) {
        Registry<Enchantment> registry = access.registryOrThrow(Registries.ENCHANTMENT);
        List<Holder<Enchantment>> out = new ArrayList<>();
        for (Holder<Enchantment> holder : registry.holders().toList()) {
            boolean curse = holder.is(EnchantmentTags.CURSE);
            boolean treasure = holder.is(EnchantmentTags.TREASURE);
            if (curse && !tierAllowsCurse(ritualTier)) {
                continue;
            }
            if (treasure && !tierAllowsTreasure(ritualTier)) {
                continue;
            }
            out.add(holder);
        }
        return out;
    }

    /** 不放回等概率抽取（池内顺序无意义；count 钳到池大小）。 */
    public static <T> List<T> draw(List<T> pool, int count, RandomSource random) {
        List<T> remaining = new ArrayList<>(pool);
        List<T> out = new ArrayList<>();
        int n = Math.min(count, remaining.size());
        for (int i = 0; i < n; i++) {
            out.add(remaining.remove(random.nextInt(remaining.size())));
        }
        return out;
    }

    /** 按阶决定词条等级。 */
    public static int rollLevel(int ritualTier, int maxLevel, RandomSource random) {
        if (ritualTier <= 1) {
            return 1;
        }
        if (ritualTier >= 3) {
            return maxLevel + 1;
        }
        return 1 + random.nextInt(Math.max(1, maxLevel));
    }
}
