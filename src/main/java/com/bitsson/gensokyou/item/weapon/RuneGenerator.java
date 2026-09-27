package com.bitsson.gensokyou.item.weapon;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.config.GensokyouConfig;
import com.bitsson.gensokyou.registry.ModDataComponents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 增幅核词条生成：按核阶过滤受控词条池（config），权重 roll 词条、区间取值，
 * 写入 rune_affixes 组件。
 *
 * <p>词条分两域：<b>武器专有</b>（本类的 config 表，id 形如 {@code damage_pct}）与
 * <b>玩家属性</b>（id 直接等于 {@link com.bitsson.gensokyou.spirit.attr.AttributeKey#id()}，
 * 由 {@link SeiiNumbers} 按"参考玩家阶标准值的百分之几"取带）。本类只负责武器专有域的解析与首次生成；
 * 洗练路径走 {@code SeiiNumbers.roll}（含软保底平移）。
 *
 * <p><b>config 合并范式</b>：NeoForge 不会把新的 list 默认值写进已存在的配置文件，
 * 故"改了代码里的默认表"对老配置无效（表现为 T1 核仍 roll 2 条）。解法照
 * {@code GraceNumbers.effectiveRows()}：运行时有效值 = config 行 + 内置默认行，
 * config 在前（玩家显式调过则尊重），缺项由默认补齐。
 */
public final class RuneGenerator {

    /** 武器专有词条池条目：id,min,max,weight,tier（tier 为精确核阶）。 */
    public record AffixDef(String id, float min, float max, int weight, int tier) {
    }

    /**
     * 已被 spec 退役的武器侧 id（暴击已迁到玩家属性域，退役原因见 rune-affix-pool 的
     * REMOVED 章节：两者在同一个加区、同一个 cap，量级差 4~6 倍）。
     *
     * <p>这些 id <b>无条件</b>丢弃，不走"config 优先"：配置里留着它们不是偏好而是陈旧数据，
     * 放行会导致同一效果两个 id 同时进池。
     */
    public static final Set<String> RETIRED_IDS = Set.of("crit_chance_pct", "crit_damage_pct");

    /** 内置默认武器词条池（不读 config，纯常量；供 {@link #pool()} 合并补缺）。 */
    private static final List<String> DEFAULT_POOL_ROWS = List.of(
            "damage_pct,0.03,0.06,10,1", "damage_pct,0.07,0.12,10,2", "damage_pct,0.12,0.20,10,3",
            "attack_rate_pct,0.03,0.06,8,1", "attack_rate_pct,0.06,0.10,8,2", "attack_rate_pct,0.10,0.15,8,3",
            "spirit_cost_pct,-0.08,-0.03,8,1", "spirit_cost_pct,-0.14,-0.06,8,2", "spirit_cost_pct,-0.22,-0.10,8,3",
            "range_pct,0.03,0.06,6,1", "range_pct,0.06,0.10,6,2", "range_pct,0.08,0.12,6,3");

    /** 内置默认词条条数（不读 config，纯常量）。 */
    private static final List<Integer> DEFAULT_COUNTS = List.of(1, 3, 5);

    private RuneGenerator() {
    }

    /** 内置默认池行（供纯逻辑单测与 config 合并）。 */
    public static List<String> poolDefaults() {
        return DEFAULT_POOL_ROWS;
    }

    /** 内置默认条数（供纯逻辑单测与 config 合并）。 */
    public static List<Integer> countDefaults() {
        return DEFAULT_COUNTS;
    }

    /**
     * 运行时有效武器词条池 = config 行（退役 id 剔除）+ 内置默认行补缺，
     * 按 {@code (id,tier)} 去重（同一 id 的不同核阶是不同条目，MUST NOT 相互覆盖）。
     */
    public static List<AffixDef> pool() {
        return pool(GensokyouConfig.RUNE_AFFIX_POOL.get(), DEFAULT_POOL_ROWS);
    }

    /** 注入版（纯逻辑单测）。 */
    public static List<AffixDef> pool(List<? extends String> configRows, List<? extends String> defaults) {
        List<AffixDef> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String raw : configRows) {
            AffixDef def = parse(raw);
            if (def == null) {
                Gensokyou.LOGGER.warn("[danmaku-weapon] malformed rune affix entry skipped: {}", raw);
                continue;
            }
            if (RETIRED_IDS.contains(def.id())) {
                Gensokyou.LOGGER.warn("[danmaku-weapon] retired affix id '{}' in config is ignored"
                        + " (moved to the player-attribute domain as crit_chance/crit_damage);"
                        + " delete that line from your config", def.id());
                continue;
            }
            if (seen.add(def.id() + "#" + def.tier())) {
                out.add(def);
            }
        }
        for (String raw : defaults) {
            AffixDef def = parse(raw);
            if (def != null && !RETIRED_IDS.contains(def.id())
                    && seen.add(def.id() + "#" + def.tier())) {
                out.add(def);
            }
        }
        return List.copyOf(out);
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

    /**
     * 首次获得增幅核时懒生成（取出/合成/掉落统一覆盖）：词条 + 晶石随机色，各掷一次。
     *
     * <p>创造物品栏<b>刻意不调</b>本方法：那里的核保持未 roll 状态（tooltip 全空），
     * 玩家取出后才在此处 roll。
     */
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
        return roll(pool(), tier, SeiiNumbers.affixCount(tier), random);
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

    /** 该 tier 的词条数量（config 逐位优先、内置默认补缺；越界取末档 / 至少 1）。 */
    public static int affixCountForTier(int tier) {
        return SeiiNumbers.affixCount(tier);
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

    /**
     * 洗练重写（星移之仪调用）：按软保底平移后的采样带重 roll 整组词条。
     * 调用方 MUST 先把结果暂存，只在玩家"全部采纳"时才写回核组件。
     */
    public static List<RuneAffix> rollForReroll(int tier, int rerolls, RandomSource random) {
        return SeiiNumbers.roll(tier, rerolls, random);
    }

    /** 读洗练度（缺省 0）。 */
    public static int rerollsOf(ItemStack core) {
        return core.getOrDefault(ModDataComponents.RUNE_REROLLS.get(), 0);
    }

    /** 写洗练度（负值归零）。 */
    public static void setRerolls(ItemStack core, int rerolls) {
        core.set(ModDataComponents.RUNE_REROLLS.get(), Math.max(0, rerolls));
    }

    /** 保留一次洗练：洗练度 +1。 */
    public static void bumpRerollKeep(ItemStack core) {
        setRerolls(core, rerollsOf(core) + 1);
    }

    /** 采纳一次洗练：洗练度减半（不清零，让"这枚核被洗过多次"成为可见履历）。 */
    public static void bumpRerollAccept(ItemStack core) {
        setRerolls(core, rerollsOf(core) / 2);
    }
}
