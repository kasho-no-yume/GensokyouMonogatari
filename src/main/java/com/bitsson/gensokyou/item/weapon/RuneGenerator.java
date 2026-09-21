package com.bitsson.gensokyou.item.weapon;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.config.GensokyouConfig;
import com.bitsson.gensokyou.registry.ModDataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 增幅核词条生成：按 tier 过滤受控词条池（config），权重 roll 词条、区间取值，
 * 写入 rune_affixes 组件。重投/洗练本期不做，reroll 为预留的重写接口。
 */
public final class RuneGenerator {

    /** 词条池条目：id,min,max,weight,tier（config 内以逗号字符串声明；tier 为精确档位）。 */
    public record AffixDef(String id, float min, float max, int weight, int tier) {
    }

    private RuneGenerator() {
    }

    public static List<AffixDef> pool() {
        List<AffixDef> defs = new ArrayList<>();
        for (String raw : GensokyouConfig.RUNE_AFFIX_POOL.get()) {
            AffixDef def = parse(raw);
            if (def != null) {
                defs.add(def);
            } else {
                Gensokyou.LOGGER.warn("[danmaku-weapon] malformed rune affix entry skipped: {}", raw);
            }
        }
        return defs;
    }

    private static AffixDef parse(String raw) {
        String[] parts = raw.split(",");
        if (parts.length != 5) {
            return null;
        }
        try {
            return new AffixDef(parts[0].trim(), Float.parseFloat(parts[1].trim()),
                    Float.parseFloat(parts[2].trim()), Integer.parseInt(parts[3].trim()),
                    Integer.parseInt(parts[4].trim()));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    /** 首次获得增幅核时懒生成（creative/合成/掉落统一覆盖）：词条 + 晶石随机色，各掷一次。 */
    public static void ensureGenerated(ItemStack core, int tier) {
        if (core.isEmpty()) {
            return;
        }
        if (!core.has(ModDataComponents.RUNE_AFFIXES.get())) {
            core.set(ModDataComponents.RUNE_AFFIXES.get(),
                    roll(tier, RandomSource.create()));
        }
        if (!core.has(ModDataComponents.CRYSTAL_COLOR.get())) {
            core.set(ModDataComponents.CRYSTAL_COLOR.get(), rollCrystalColor(RandomSource.create()));
        }
    }

    /**
     * 晶石随机色：全 RGB（2^24）均匀随机，仅拒绝过暗样本
     * （乘法 tint 下近黑色会让晶石在托座里不可辨）。ARGB，alpha=0xFF。
     */
    public static int rollCrystalColor(RandomSource random) {
        int r, g, b;
        do {
            int rgb = random.nextInt(1 << 24);
            r = (rgb >> 16) & 0xFF;
            g = (rgb >> 8) & 0xFF;
            b = rgb & 0xFF;
        } while (Math.max(r, Math.max(g, b)) < 72);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    public static List<RuneAffix> roll(int tier, RandomSource random) {
        return roll(pool(), tier, affixCountForTier(tier), random);
    }

    /** 注入池版本（可单测）：同 id 唯一、按权重不重复抽、区间内取值。 */
    static List<RuneAffix> roll(List<AffixDef> pool, int tier, int count, RandomSource random) {
        List<AffixDef> candidates = pool.stream()
                .filter(def -> def.tier() == tier)
                .toList();
        if (candidates.isEmpty()) {
            return List.of();
        }
        int n = Math.min(count, candidates.size());
        Set<String> usedIds = new HashSet<>();
        List<RuneAffix> result = new ArrayList<>(n);
        int guard = 0;
        while (result.size() < n && guard++ < 100) {
            AffixDef def = weightedPick(candidates, random);
            if (def == null || !usedIds.add(def.id())) {
                continue;
            }
            float value = def.min() + random.nextFloat() * (def.max() - def.min());
            result.add(new RuneAffix(def.id(), value));
        }
        return List.copyOf(result);
    }

    /** 该 tier 的词条数量（config 列表，index = tier-1；越界取末档 / 至少 1）。 */
    public static int affixCountForTier(int tier) {
        List<? extends Integer> table = GensokyouConfig.RUNE_AFFIX_COUNT.get();
        if (table.isEmpty()) {
            return 1;
        }
        int index = Math.min(Math.max(1, tier), table.size()) - 1;
        return Math.max(1, table.get(index));
    }

    private static AffixDef weightedPick(List<AffixDef> candidates, RandomSource random) {
        int total = candidates.stream().mapToInt(AffixDef::weight).sum();
        if (total <= 0) {
            return null;
        }
        int roll = random.nextInt(total);
        for (AffixDef def : candidates) {
            roll -= def.weight();
            if (roll < 0) {
                return def;
            }
        }
        return null;
    }

    /** 预留：组件重写接口（重投/洗练后续在此实现）。 */
    public static void reroll(ServerPlayer player, ItemStack core) {
        int tier = core.getItem() instanceof AmpCoreItem ampCore ? ampCore.tier() : 1;
        core.set(ModDataComponents.RUNE_AFFIXES.get(), roll(tier, player.getRandom()));
    }
}
