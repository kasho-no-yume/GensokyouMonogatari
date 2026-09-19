package com.bitsson.gensokyou.ritual.behavior;

import com.bitsson.gensokyou.block.entity.RitualCoreBlockEntity;
import com.bitsson.gensokyou.block.entity.RitualPedestalBlockEntity;
import com.bitsson.gensokyou.config.GensokyouConfig;
import com.bitsson.gensokyou.item.CodexOfBeingsItem;
import com.bitsson.gensokyou.network.InfoLine;
import com.bitsson.gensokyou.network.ModNetworking;
import com.bitsson.gensokyou.ritual.RitualBehavior;
import com.bitsson.gensokyou.ritual.RitualMatch;
import com.bitsson.gensokyou.ritual.RitualPedestals;
import com.bitsson.gensokyou.spirit.SpiritPowerHelper;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 众生余录（{@code shujou_yoroku_circle}）：满态典籍 → 对应实体原版死亡掉落 的持续转化仪式。
 *
 * <p>启停型（pattern {@code toggleable:true}）：每 {@code shujouCycleTicks} 结算一次。
 * 祭品台仅识别<b>满 20</b>（{@link CodexOfBeingsItem#isFull}）的典籍，按 species 去重、
 * <b>不消耗</b>；成本 = 去重 species 数 × {@code shujouBaseSpCost × shujouSpCostMult^L}，
 * <b>全有全无</b>（不足则整周期不执行）。
 *
 * <p>逐 species 取其 {@code EntityType.getDefaultLootTable()} 掷一次，上下文为<b>玩家击杀</b>：
 * 用一个专用 {@link FakePlayer} 凭证（从不加入世界、从不攻击）充当
 * {@code LAST_DAMAGE_PLAYER} 与 {@code ATTACKING/DIRECT_ATTACKING_ENTITY}；1 阶及以上给凭证主手
 * 装配抢夺附魔（默认 3），0 阶空手。2 阶在抢夺结果上把本次全部产物 ×{@code shujouL2OutputMult}。
 *
 * <p>特殊击杀掉落<b>天然排除</b>：代码驱动（苦力怕头/装备）因从不 {@code die()} 不产；表内
 * attacker 条件（如骷髅杀苦力怕的唱片）因凭证为玩家不命中。产物聚合同名物品、拆叠后复用
 * {@link WatatsumiBehavior#dropStacks} 空投，并在结算瞬间触发光柱（色索引 5）。
 */
public class ShujouYorokuBehavior implements RitualBehavior {

    /** 专用假玩家凭证：稳定 UUID + 可辨识名，从不加入世界。 */
    private static final GameProfile TOKEN_PROFILE = new GameProfile(
            UUID.nameUUIDFromBytes("gensokyou:shujou_yoroku".getBytes(StandardCharsets.UTF_8)),
            "[ShujouYoroku]");

    private static final int ACCENT = 0xFF9C6BD6;

    // ---- 世界无关纯内核（调试/UI 可直调）----

    /** 单 species 灵力消耗 = 基值 × 倍率^等级。 */
    public static long speciesCost(int level) {
        long value = GensokyouConfig.SHUJOU_BASE_SP_COST.get();
        long mult = GensokyouConfig.SHUJOU_SP_COST_MULT.get();
        for (int i = 0; i < level; i++) {
            value *= mult;
        }
        return Math.max(0L, value);
    }

    /** 单次总消耗 = species 数 × 单 species 消耗（饱和乘）。 */
    public static long totalCost(int level, int speciesCount) {
        long unit = speciesCost(level);
        if (unit <= 0L || speciesCount <= 0) {
            return 0L;
        }
        if (unit > Long.MAX_VALUE / speciesCount) {
            return Long.MAX_VALUE;
        }
        return unit * speciesCount;
    }

    /** 缓存容量 = 基值 × 容量倍率^等级。 */
    public static long capacity(int level) {
        long cap = GensokyouConfig.SHUJOU_BASE_CAPACITY.get();
        long mult = GensokyouConfig.SHUJOU_CAPACITY_MULT.get();
        for (int i = 0; i < level; i++) {
            cap *= mult;
        }
        return Math.max(0L, cap);
    }

    /** 2 阶产物倍率；低阶为 1。 */
    public static int l2Multiplier(int level) {
        return level >= 2 ? GensokyouConfig.SHUJOU_L2_OUTPUT_MULT.get() : 1;
    }

    /** tick → 秒（向上取整），供 GUI 显示。 */
    public static int toSeconds(int ticks) {
        return Math.max(0, (ticks + 19) / 20);
    }

    /** 有效典籍：众生典籍 + 满 20 + 已记录 species。 */
    public static boolean isValidCodex(ItemStack stack) {
        return stack.getItem() instanceof CodexOfBeingsItem
                && CodexOfBeingsItem.isFull(stack)
                && CodexOfBeingsItem.getSpecies(stack).isPresent();
    }

    // ---- 世界侧扫描 ----

    /** 合法典籍数（满态 + 可解析 species）。 */
    public static int countValidCodexes(ServerLevel level, RitualMatch match) {
        int count = 0;
        for (BlockPos pos : RitualPedestals.positions(match)) {
            if (level.getBlockEntity(pos) instanceof RitualPedestalBlockEntity pedestal
                    && isValidCodex(pedestal.getHeld())) {
                count++;
            }
        }
        return count;
    }

    /** 去重后的有效 species（按祭品台规范序，未知实体类型跳过）。 */
    public static List<ResourceLocation> scanSpecies(ServerLevel level, RitualMatch match) {
        LinkedHashSet<ResourceLocation> out = new LinkedHashSet<>();
        for (BlockPos pos : RitualPedestals.positions(match)) {
            if (!(level.getBlockEntity(pos) instanceof RitualPedestalBlockEntity pedestal)) {
                continue;
            }
            ItemStack held = pedestal.getHeld();
            if (!isValidCodex(held)) {
                continue;
            }
            CodexOfBeingsItem.getSpecies(held)
                    .filter(id -> BuiltInRegistries.ENTITY_TYPE.getOptional(id).isPresent())
                    .ifPresent(out::add);
        }
        return new ArrayList<>(out);
    }

    // ---- 玩家击杀上下文 ----

    private static FakePlayer token(ServerLevel level) {
        return FakePlayerFactory.get(level, TOKEN_PROFILE);
    }

    /** 按等级同步凭证主手：1 阶及以上携带抢夺附魔，0 阶空手。 */
    private static void equipToken(ServerLevel level, FakePlayer killer, int ritualLevel) {
        int looting = ritualLevel >= 1 ? GensokyouConfig.SHUJOU_LOOTING_LEVEL.get() : 0;
        if (looting <= 0) {
            killer.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            return;
        }
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        Holder<Enchantment> holder = level.registryAccess()
                .registryOrThrow(Registries.ENCHANTMENT)
                .getHolderOrThrow(Enchantments.LOOTING);
        sword.enchant(holder, looting);
        killer.setItemInHand(InteractionHand.MAIN_HAND, sword);
    }

    /** 掷单个 species 的默认死亡战利品表（玩家击杀上下文；无世界副作用）。 */
    public static List<ItemStack> rollSpecies(ServerLevel level, BlockPos corePos,
                                              ResourceLocation species, int ritualLevel) {
        Optional<EntityType<?>> typeOpt = BuiltInRegistries.ENTITY_TYPE.getOptional(species);
        if (typeOpt.isEmpty()) {
            return List.of();
        }
        EntityType<?> type = typeOpt.get();
        Entity victim = type.create(level);
        if (victim == null) {
            return List.of();
        }
        Vec3 origin = Vec3.atCenterOf(corePos);
        victim.moveTo(origin.x, origin.y, origin.z);

        FakePlayer killer = token(level);
        equipToken(level, killer, ritualLevel);

        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.THIS_ENTITY, victim)
                .withParameter(LootContextParams.ORIGIN, origin)
                .withParameter(LootContextParams.DAMAGE_SOURCE, level.damageSources().playerAttack(killer))
                .withParameter(LootContextParams.ATTACKING_ENTITY, killer)
                .withParameter(LootContextParams.DIRECT_ATTACKING_ENTITY, killer)
                .withParameter(LootContextParams.LAST_DAMAGE_PLAYER, killer)
                .create(LootContextParamSets.ENTITY);

        LootTable table = level.getServer().reloadableRegistries().getLootTable(type.getDefaultLootTable());
        return new ArrayList<>(table.getRandomItems(params, level.random));
    }

    // ---- RitualBehavior ----

    @Override
    public long spiritInRatePerSecond(ServerLevel level, BlockPos corePos, RitualMatch match,
                                      RitualCoreBlockEntity core) {
        return GensokyouConfig.SHUJOU_SPIRIT_IN_RATE.get().longValue();
    }

    @Override
    public void serverTick(ServerLevel level, BlockPos corePos, RitualMatch match,
                           RitualCoreBlockEntity core) {
        if (core.actionCooldown() > 0) {
            return;
        }
        List<ResourceLocation> species = scanSpecies(level, match);
        if (species.isEmpty()) {
            return;
        }
        int ritualLevel = match.level();
        long cost = totalCost(ritualLevel, species.size());
        if (!SpiritPowerHelper.canCover(level, corePos, core, cost)) {
            return;
        }
        if (!SpiritPowerHelper.payCost(level, corePos, core, cost)) {
            return;
        }

        Map<Item, Integer> agg = new LinkedHashMap<>();
        int mult = l2Multiplier(ritualLevel);
        for (ResourceLocation id : species) {
            for (ItemStack stack : rollSpecies(level, corePos, id, ritualLevel)) {
                if (stack.isEmpty()) {
                    continue;
                }
                agg.merge(stack.getItem(), stack.getCount(), Integer::sum);
            }
        }
        if (mult > 1) {
            agg.replaceAll((item, count) ->
                    (int) Math.min(Integer.MAX_VALUE, (long) count * mult));
        }
        dropAggregated(level, corePos, agg);

        core.setActionCooldown(GensokyouConfig.SHUJOU_CYCLE_TICKS.get());
        core.triggerSacrificeFx(GensokyouConfig.FX_PILLAR_TICKS.get());
        ModNetworking.sendRitualInfoToViewers(level, corePos);
    }

    /** 聚合同名物品，按最大堆叠拆栈后复用绵津见空投（XZ 圆盘 + 献祭家族落点）。 */
    private static void dropAggregated(ServerLevel level, BlockPos corePos, Map<Item, Integer> agg) {
        List<ItemStack> stacks = new ArrayList<>();
        for (Map.Entry<Item, Integer> entry : agg.entrySet()) {
            int remaining = entry.getValue();
            int max = Math.max(1, new ItemStack(entry.getKey()).getMaxStackSize());
            while (remaining > 0) {
                int n = Math.min(remaining, max);
                stacks.add(new ItemStack(entry.getKey(), n));
                remaining -= n;
            }
        }
        WatatsumiBehavior.dropStacks(level, corePos, stacks);
    }

    // ---- UI ----

    /** 状态行键显式列举（拼接前缀会被 lang_audit 当字面量误报）。 */
    private static final String KEY_DISABLED = "gui.gensokyou.ritual.shujou.disabled";
    private static final String KEY_COOLING = "gui.gensokyou.ritual.shujou.cooling";
    private static final String KEY_NO_CODEX = "gui.gensokyou.ritual.shujou.no_codex";
    private static final String KEY_NO_POWER = "gui.gensokyou.ritual.shujou.no_power";
    private static final String KEY_READY = "gui.gensokyou.ritual.shujou.ready";
    private static final String KEY_CYCLE = "gui.gensokyou.ritual.shujou.cycle";
    private static final String KEY_SPECIES = "gui.gensokyou.ritual.shujou.species";
    private static final String KEY_COST = "gui.gensokyou.ritual.shujou.cost";
    private static final String KEY_COST_TIP = "gui.gensokyou.ritual.shujou.cost.tip";
    private static final String KEY_LEVEL = "gui.gensokyou.ritual.shujou.level";
    private static final String KEY_L2_ON = "gui.gensokyou.ritual.shujou.l2_on";
    private static final String KEY_L2_OFF = "gui.gensokyou.ritual.shujou.l2_off";

    @Override
    public List<InfoLine> uiInfo(ServerLevel level, BlockPos corePos, RitualMatch match,
                                 RitualCoreBlockEntity core) {
        List<InfoLine> lines = new ArrayList<>();
        int ritualLevel = match.level();
        List<ResourceLocation> species = scanSpecies(level, match);

        String stateKey;
        String[] stateArgs = new String[0];
        if (!core.isEnabled()) {
            stateKey = KEY_DISABLED;
        } else if (core.actionCooldown() > 0) {
            stateKey = KEY_COOLING;
            stateArgs = new String[]{String.valueOf(toSeconds(core.actionCooldown()))};
        } else if (species.isEmpty()) {
            stateKey = KEY_NO_CODEX;
        } else if (!SpiritPowerHelper.canCover(level, corePos, core,
                totalCost(ritualLevel, species.size()))) {
            stateKey = KEY_NO_POWER;
        } else {
            stateKey = KEY_READY;
        }
        lines.add(new InfoLine(stateKey, stateArgs, "", ACCENT, -1F, null));
        lines.add(new InfoLine(KEY_CYCLE,
                new String[]{String.valueOf(toSeconds(GensokyouConfig.SHUJOU_CYCLE_TICKS.get()))},
                "", ACCENT, -1F, null));
        lines.add(new InfoLine(KEY_SPECIES,
                new String[]{String.valueOf(species.size()),
                        String.valueOf(RitualPedestals.positions(match).size())},
                "", ACCENT, -1F, null));
        long cost = totalCost(ritualLevel, species.size());
        lines.add(InfoLine.tipped(KEY_COST,
                new String[]{InfoLine.compact(cost)}, ACCENT,
                KEY_COST_TIP, new String[]{String.valueOf(cost)}));
        lines.add(new InfoLine(KEY_LEVEL,
                new String[]{String.valueOf(ritualLevel)}, "", ACCENT, -1F, null));
        lines.add(new InfoLine(l2Multiplier(ritualLevel) > 1 ? KEY_L2_ON : KEY_L2_OFF,
                new String[0], "", ACCENT, -1F, null));
        return lines;
    }

    // ---- 调试 ----

    /** 世界无关 + 世界读的单行摘要（供 /gs_debug shujou 探针）。 */
    public static String debugSummary(ServerLevel level, RitualMatch match) {
        int ritualLevel = match.level();
        List<ResourceLocation> species = scanSpecies(level, match);
        String list = species.stream().map(ResourceLocation::toString)
                .collect(Collectors.joining(","));
        return "level=" + ritualLevel
                + " ped=" + RitualPedestals.positions(match).size()
                + " validCodex=" + countValidCodexes(level, match)
                + " species=" + species.size()
                + " unitCost=" + speciesCost(ritualLevel)
                + " totalCost=" + totalCost(ritualLevel, species.size())
                + " capacity=" + capacity(ritualLevel)
                + " l2Mult=" + l2Multiplier(ritualLevel)
                + " list=[" + list + "]";
    }

    /** 试掷第一个 species 的单行摘要（探针用；无 world 副作用，仅消耗随机数）。 */
    public static String debugRoll(ServerLevel level, BlockPos corePos, RitualMatch match) {
        List<ResourceLocation> species = scanSpecies(level, match);
        if (species.isEmpty()) {
            return "roll=<no species>";
        }
        ResourceLocation first = species.get(0);
        List<ItemStack> out = rollSpecies(level, corePos, first, match.level());
        Map<String, Integer> agg = new LinkedHashMap<>();
        for (ItemStack stack : out) {
            if (!stack.isEmpty()) {
                agg.merge(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),
                        stack.getCount(), Integer::sum);
            }
        }
        String joined = agg.entrySet().stream()
                .map(e -> e.getKey() + "x" + e.getValue())
                .collect(Collectors.joining(" "));
        return String.format(Locale.ROOT, "roll(%s)=%s", first, joined.isEmpty() ? "<empty>" : joined);
    }
}
