package com.bitsson.gensokyou.client.book;

import com.bitsson.gensokyou.client.ritual.ClientRitualData;
import com.bitsson.gensokyou.config.GensokyouConfig;
import com.bitsson.gensokyou.ritual.RitualPattern;
import com.bitsson.gensokyou.ritual.RitualRecipe;
import com.bitsson.gensokyou.ritual.RitualScaling;
import com.bitsson.gensokyou.ritual.behavior.SunakoScaling;
import com.bitsson.gensokyou.ritual.BousenLanterns;
import com.bitsson.gensokyou.ritual.behavior.BousenBehavior;
import com.bitsson.gensokyou.ritual.behavior.ReiyokuBehavior;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import vazkii.patchouli.api.ICustomComponent;
import vazkii.patchouli.api.IComponentRenderContext;
import vazkii.patchouli.api.IVariable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * 仪式「阶级详情」组件：读取 {@code ritual} + {@code tier}，渲染该阶的**具体参数**
 * （按该阶从 {@link GensokyouConfig} 计算，不写公式、不带类型标签）与该阶配方/供品。
 * 页面本身挂对应阶级门槛（世界进度：下界/末地/幻想乡；4/5 阶为过渡）。
 *
 * <p>complete-ritual-book-entries D3：不再渲染「搭建材料」段；全部文本经
 * {@link #drawWrapped} 自动换行（{@code Font.split}），MUST NOT 因超宽裁切。
 */
public class RitualTierComponent implements ICustomComponent {

    private static final int HEADER_COLOR = 0xFF4A2B6B;
    private static final int BODY_COLOR = 0xFF2A2430;
    private static final int SUBTLE_COLOR = 0xFF666666;
    private static final int WRAP_WIDTH = 116;
    private static final int LINE_HEIGHT = 10;

    private ResourceLocation ritualId;
    private int tier;
    private boolean showRecipes = true;

    @Override
    public void onVariablesAvailable(UnaryOperator<IVariable> lookup, HolderLookup.Provider registries) {
        String raw = lookup.apply(IVariable.wrap("#ritual#", registries)).asString("");
        if (raw != null && !raw.isEmpty() && !raw.equals("#ritual#")) {
            ritualId = ResourceLocation.tryParse(raw);
        }
        String tierRaw = lookup.apply(IVariable.wrap("#tier#", registries)).asString("0");
        try {
            tier = Integer.parseInt(tierRaw.trim());
        } catch (NumberFormatException ignored) {
            tier = 0;
        }
        String showRaw = lookup.apply(IVariable.wrap("#show_recipes#", registries)).asString("true");
        showRecipes = showRaw == null || !showRaw.trim().equalsIgnoreCase("false");
    }

    @Override
    public void build(int x, int y, int pageNum) {
    }

    @Override
    public void render(GuiGraphics graphics, IComponentRenderContext context,
                       float partialTicks, int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        int x = 0;
        int y = 6;
        graphics.drawString(mc.font,
                Component.translatable("gensokyou.book.ritual.tier_header", tier),
                x, y, HEADER_COLOR, false);
        y += 13;
        if (ritualId == null || !ClientRitualData.hasData()) {
            graphics.drawString(mc.font, Component.translatable(
                    ritualId == null ? "gensokyou.book.ritual.missing" : "gensokyou.book.ritual.syncing"),
                    x, y, BODY_COLOR, false);
            return;
        }
        var patternOpt = ClientRitualData.pattern(ritualId);
        if (patternOpt.isEmpty()) {
            graphics.drawString(mc.font, Component.translatable("gensokyou.book.ritual.no_structure"),
                    x, y, BODY_COLOR, false);
            return;
        }
        RitualPattern pattern = patternOpt.get();

        List<Component> params = paramLines(ritualId, tier);
        if (showRecipes) {
            for (RitualRecipe recipe : ClientRitualData.recipesFor(ritualId)) {
                if (recipe.minTier() != tier) {
                    continue;
                }
                Component line = recipe.displayName().copy()
                        .append("  ")
                        .append(Component.translatable("jei.gensokyou.recipe.spirit_cost", recipe.spCost()));
                if (recipe.minPlayerTier() > 0) {
                    line = line.copy().append("  ")
                            .append(Component.translatable("jei.gensokyou.recipe.player_tier", recipe.minPlayerTier()));
                }
                params.add(line);
            }
        }
        if (!params.isEmpty()) {
            y = drawWrapped(graphics, mc, Component.translatable("gensokyou.book.ritual.params"),
                    x, y, SUBTLE_COLOR);
            for (Component line : params) {
                y = drawWrapped(graphics, mc, line, x, y, BODY_COLOR);
            }
        }

        List<Component> offers = new ArrayList<>();
        for (RitualPattern.Offering offering : pattern.requirements()) {
            String itemName = offering.item().item() != null
                    ? new net.minecraft.world.item.ItemStack(offering.item().item())
                            .getHoverName().getString()
                    : "#" + offering.item().tag().location();
            Component consume = switch (offering.consume()) {
                case NONE -> Component.translatable("gensokyou.book.ritual.consume.none");
                case ON_ACTIVATE -> Component.translatable("gensokyou.book.ritual.consume.activate");
                case PERIODIC -> Component.translatable("gensokyou.book.ritual.consume.periodic",
                        offering.period() / 20);
            };
            offers.add(Component.translatable("gensokyou.book.ritual.offering", itemName, consume));
        }
        if (!offers.isEmpty()) {
            if (!params.isEmpty()) {
                y += 3;
            }
            y = drawWrapped(graphics, mc, Component.translatable("gensokyou.book.ritual.offerings_label"),
                    x, y, SUBTLE_COLOR);
            for (Component line : offers) {
                y = drawWrapped(graphics, mc, line, x, y, BODY_COLOR);
            }
        }
    }

    /** 该阶具体参数（从 config 现算，避免在书里写公式）。 */
    private static List<Component> paramLines(ResourceLocation id, int tier) {
        List<Component> out = new ArrayList<>();
        int L = Math.max(0, tier);
        switch (id.getPath()) {
            case "kagutsuchi_flame_circle" -> {
                add(out, "产灵", secs(GensokyouConfig.KAGUTSUICHI_BASE_RATE_PER_SECOND.get() * pow(4, L)));
                add(out, "输出上限", secs(GensokyouConfig.KAGUTSUICHI_BASE_OUT_RATE_PER_SECOND.get() * pow(4, L)));
                add(out, "缓存", compact(GensokyouConfig.KAGUTSUICHI_BASE_CAPACITY.get() * pow(10, L)));
            }
            case "yumewatari_circle" -> {
                add(out, "每次睡觉产灵", compact(GensokyouConfig.YUMEWATARI_PRODUCTION_PER_SLEEPER.get() * pow(4, L)));
                add(out, "输出上限", compact(GensokyouConfig.YUMEWATARI_OUT_RATE_PER_SECOND.get()) + " /秒");
                add(out, "缓存", compact(GensokyouConfig.YUMEWATARI_BASE_CAPACITY.get() * pow(4, L)));
            }
            case "haniyasu_circle", "kukunochi_circle", "kaya_no_hime_circle", "oyamatsumi_circle" -> {
                add(out, "受灵上限", compact(GensokyouConfig.SACRIFICE_SPIRIT_IN_RATE.get()) + " /秒");
                add(out, "缓存", compact(GensokyouConfig.SACRIFICE_BASE_CAPACITY.get() * pow(4, L)));
                add(out, "每次结算", "耗灵 " + compact(GensokyouConfig.SACRIFICE_BASE_SP_COST.get()
                        * pow(GensokyouConfig.SACRIFICE_SP_COST_MULT.get(), L))
                        + "，产出 " + compact(GensokyouConfig.SACRIFICE_BASE_COUNT.get()
                        * pow(GensokyouConfig.SACRIFICE_COUNT_MULT.get(), L)) + " 件");
                add(out, "冷却", (GensokyouConfig.SACRIFICE_COOLDOWN_TICKS.get() / 20) + " 秒");
            }
            case "watatsumi_circle" -> {
                add(out, "受灵上限", compact(GensokyouConfig.SACRIFICE_SPIRIT_IN_RATE.get()) + " /秒");
                add(out, "缓存", compact(GensokyouConfig.SACRIFICE_BASE_CAPACITY.get() * pow(4, L)));
                add(out, "每次结算", "耗灵 " + compact(GensokyouConfig.SACRIFICE_BASE_SP_COST.get()
                        * pow(GensokyouConfig.SACRIFICE_SP_COST_MULT.get(), L))
                        + "，产出 " + compact(GensokyouConfig.WATATSUMI_BASE_COUNT.get()
                        * pow(GensokyouConfig.WATATSUMI_COUNT_MULT.get(), L)) + " 件");
                String cd = "冷却 " + (GensokyouConfig.WATATSUMI_BASE_COOLDOWN_TICKS.get() / 20) + " 秒";
                if (L >= 2) {
                    cd += "；宝藏冷却 " + (GensokyouConfig.WATATSUMI_BONUS_COOLDOWN_TICKS.get() / 20) + " 秒";
                }
                add(out, "冷却", cd);
            }
            case "shujou_yoroku_circle" -> {
                add(out, "缓存", compact((long) GensokyouConfig.SHUJOU_BASE_CAPACITY.get()
                        * pow(GensokyouConfig.SHUJOU_CAPACITY_MULT.get(), L)));
                add(out, "受灵上限", compact(GensokyouConfig.SHUJOU_SPIRIT_IN_RATE.get()) + " /秒");
                add(out, "周期", (GensokyouConfig.SHUJOU_CYCLE_TICKS.get() / 20) + " 秒");
                add(out, "每种耗灵", compact((long) GensokyouConfig.SHUJOU_BASE_SP_COST.get()
                        * pow(GensokyouConfig.SHUJOU_SP_COST_MULT.get(), L)));
                String loot = "模拟抢夺 " + GensokyouConfig.SHUJOU_LOOTING_LEVEL.get();
                if (L >= 2) {
                    loot += "；产出 ×" + GensokyouConfig.SHUJOU_L2_OUTPUT_MULT.get();
                }
                add(out, "产出", loot);
            }
            case "houjouno_teihou_circle" -> {
                int slots = switch (L) {
                    case 1 -> 8;
                    case 2 -> 12;
                    default -> 4;
                };
                long capacity = RitualScaling.scale(
                        GensokyouConfig.HOUJOUNO_TEIHOU_BASE_CAPACITY.get(),
                        GensokyouConfig.HOUJOUNO_TEIHOU_CAPACITY_MULTIPLIER.get(), L);
                long inRate = RitualScaling.scale(
                        GensokyouConfig.HOUJOUNO_TEIHOU_BASE_IN_RATE_PER_SECOND.get(),
                        GensokyouConfig.HOUJOUNO_TEIHOU_IN_RATE_MULTIPLIER.get(), L);
                long unitCost = RitualScaling.scale(
                        GensokyouConfig.HOUJOUNO_TEIHOU_BASE_COST_PER_PEDESTAL.get(),
                        GensokyouConfig.HOUJOUNO_TEIHOU_COST_MULTIPLIER.get(), L);
                long totalCost = RitualScaling.saturatingMultiply(unitCost, slots);
                long samples = RitualScaling.scale(
                        GensokyouConfig.HOUJOUNO_TEIHOU_BASE_SAMPLE_COUNT.get(),
                        GensokyouConfig.HOUJOUNO_TEIHOU_SAMPLE_COUNT_MULTIPLIER.get(), L);
                add(out, "缓存", compact(capacity));
                add(out, "受灵上限", compact(inRate) + " /秒");
                add(out, "单台耗灵", compact(unitCost));
                add(out, "满台耗灵", compact(totalCost));
                add(out, "每台采样", compact(samples) + " 次");
                add(out, "冷却", ((GensokyouConfig.HOUJOUNO_TEIHOU_CYCLE_TICKS.get() + 19) / 20) + " 秒");
            }
            case "sunako_circle" -> {
                // 祭品台数取自 sunako_circle pattern 的四重展开结果（4 / 4 / 8），与结构同源；
                // 数值一律走 SunakoScaling（3 阶容量与受灵都是显式值，不能用 base×mult^L 外推）
                int slots = switch (L) {
                    case 1 -> 4;
                    case 2 -> 4;
                    default -> 8;
                };
                long capacity = SunakoScaling.capacityOf(L);
                long inRate = SunakoScaling.inRateOf(L);
                long unitCost = SunakoScaling.unitCostOf(L);
                add(out, "缓存", compact(capacity));
                add(out, "受灵上限", compact(inRate) + " /秒");
                add(out, "单瓶耗灵", compact(unitCost));
                add(out, "满台耗灵", compact(SunakoScaling.batchCost(slots, L)));
                add(out, "满缓存可炼", compact(capacity / Math.max(1L, unitCost)) + " 瓶");
                add(out, "祭品台", slots + " 台");
            }
            case "nichirin_circle" -> {
                add(out, "峰值产灵", secs(GensokyouConfig.NICHIRIN_BASE_RATE_PER_SECOND.get() * pow(4, L)));
                add(out, "输出上限", compact(GensokyouConfig.NICHIRIN_OUT_RATE_PER_SECOND.get()) + " /秒");
                add(out, "缓存", compact(GensokyouConfig.NICHIRIN_BASE_CAPACITY.get() * pow(4, L)));
            }
            case "tsukikage_circle" -> {
                add(out, "峰值产灵", secs(GensokyouConfig.TSUKIKAGE_BASE_RATE_PER_SECOND.get() * pow(4, L)));
                add(out, "输出上限", compact(GensokyouConfig.TSUKIKAGE_OUT_RATE_PER_SECOND.get()) + " /秒");
                add(out, "缓存", compact(GensokyouConfig.TSUKIKAGE_BASE_CAPACITY.get() * pow(4, L)));
            }
            case "wujinzang_circle" -> {
                add(out, "缓存", compact((long) GensokyouConfig.WUJINZANG_BASE_CAPACITY.get()
                        * pow(GensokyouConfig.WUJINZANG_MULT.get(), L)));
                add(out, "受灵上限", compact(GensokyouConfig.WUJINZANG_IN_RATE.get()) + " /秒");
                add(out, "运行耗灵", compact((long) GensokyouConfig.WUJINZANG_BASE_DRAIN.get()
                        * pow(GensokyouConfig.WUJINZANG_MULT.get(), L)) + " /秒");
            }
            case "kanayamahiko_circle" -> {
                long divisor = Math.max(1L, pow(GensokyouConfig.KANAYAMAHIKO_DURATION_LEVEL_DIVISOR.get(), L));
                long durationTicks = Math.max(1L, (GensokyouConfig.KANAYAMAHIKO_BASE_DURATION_SECONDS.get() * 20L
                        + divisor - 1L) / divisor);
                add(out, "熔炼时间", String.format(java.util.Locale.ROOT, "%.1f", durationTicks / 20.0D) + " 秒");
                add(out, "单件耗灵", compact((long) GensokyouConfig.KANAYAMAHIKO_BASE_DRAIN_PER_SECOND.get()
                        * pow(GensokyouConfig.KANAYAMAHIKO_POWER_MULTIPLIER.get(), L)) + " /秒");
                add(out, "缓存", compact((long) GensokyouConfig.KANAYAMAHIKO_BASE_CAPACITY.get()
                        * pow(GensokyouConfig.KANAYAMAHIKO_CAPACITY_MULTIPLIER.get(), L)));
                add(out, "受灵上限", compact((long) GensokyouConfig.KANAYAMAHIKO_BASE_ROUTED_INPUT_PER_SECOND.get()
                        * pow(GensokyouConfig.KANAYAMAHIKO_IN_RATE_MULTIPLIER.get(), L)) + " /秒");
            }
            case "zaohua_circle" -> add(out, "受灵上限", compact((long) GensokyouConfig.ZAOHUA_SPIRIT_IN_RATE_BASE.get()
                    * pow(GensokyouConfig.ZAOHUA_SPIRIT_IN_RATE_MULT.get(), L)) + " /秒");
            case "kami_no_megumi_circle" -> add(out, "受灵上限", compact(GensokyouConfig.GRACE_SPIRIT_IN_RATE.get()) + " /秒");
            case "bafang_guiyuan_circle" -> add(out, "可托管核心", "tier ≤ " + L + " 的灵力核心");
            case "reiyoku_circle" -> {
                // 数值全部现算自 config（与行为侧同一组静态函数口径），不写公式。
                add(out, "缓存", compact(ReiyokuBehavior.capacity(L)));
                add(out, "受灵上限", compact(ReiyokuBehavior.inRate(L)) + " /秒");
                add(out, "注灵速率", compact((long) Math.round(ReiyokuBehavior.chargePerSecond(L)))
                        + " /秒");
                add(out, "兑换比", ReiyokuBehavior.cachePerSpirit() + " 灵力 : 1");
            }
            case "bousen_circle" -> {
                // 数值全部现算自 config（与行为侧同一组静态函数口径），不写公式。
                // 三档均非等比序列，故一律走分阶表而非 base × 4^L。
                add(out, "产灵", compact(BousenBehavior.produceRatePerSecond(L)) + " /秒");
                add(out, "缓存", compact(BousenBehavior.capacityOf(L)));
                add(out, "供灵上限", compact(BousenBehavior.outRateOf(L)) + " /秒");
                add(out, "蜡烛", bousenCandleCount(id, L) + " 盏");
                add(out, "单盏熄灭率", BousenBehavior.trimPercent(BousenBehavior.extinguishChance(L))
                        + "% / " + (BousenBehavior.extinguishPeriodTicks() / 20) + " 秒");
            }
            case "resonance_relay" -> {
                long scale = pow(2, Math.max(0, L - 2));
                add(out, "输入连接", String.valueOf(GensokyouConfig.RESONANCE_BASE_IN_QUOTA.get() * scale));
                add(out, "输出连接", String.valueOf(GensokyouConfig.RESONANCE_BASE_OUT_QUOTA.get() * scale));
                add(out, "连接半径", (GensokyouConfig.RESONANCE_BASE_RADIUS.get() * scale) + " 格");
            }
            case "barrier_break_circle" -> {
                // 单一阶级，故各项不随 L 变化；数值全部现算自 config，不写公式。
                // 受灵上限一行刻意不填数值——它等于八方归元的聚合输出，随托管核而变，
                // 写死任何数字都是谎报；且在阶门槛（末地）之前尚无可用的二阶灵核。
                add(out, "结界缓存", compact(GensokyouConfig.BARRIER_CAPACITY.get()));
                add(out, "自然流失", compact(GensokyouConfig.BARRIER_DRAIN_PER_SECOND.get()) + " /秒");
                add(out, "受灵上限", "视归元托管数而定");
                add(out, "建议备料", compact(GensokyouConfig.BARRIER_SUPPLY_HINT.get()));
            }
            default -> {
            }
        }
        return out;
    }

    /** 忘川该阶蜡烛数（从已同步的 pattern 切片本地枚举，零世界访问）。 */
    private static int bousenCandleCount(ResourceLocation id, int level) {
        return ClientRitualData.pattern(id)
                .map(pattern -> BousenLanterns.offsets(pattern, level).size())
                .orElse(0);
    }

    private static void add(List<Component> list, String label, String value) {        list.add(Component.literal(label + "：" + value));
    }

    private static String secs(double v) {
        return compact((long) v) + " /秒";
    }

    private static long pow(long base, int exp) {
        long r = 1L;
        for (int i = 0; i < exp; i++) {
            r *= base;
        }
        return r;
    }

    /** 大数紧凑显示：1.2 万 / 3.4 亿 / 1.2 万亿。 */
    private static String compact(long v) {
        if (v < 10_000L) {
            return Long.toString(v);
        }
        String[] units = {"万", "亿", "万亿"};
        double d = v;
        int u = -1;
        while (d >= 10_000D && u < units.length - 1) {
            d /= 10_000D;
            u++;
        }
        return String.format("%.1f%s", d, units[u]);
    }

    /** 按页宽自动换行绘制文本，返回下一行的 y。 */
    private static int drawWrapped(GuiGraphics graphics, Minecraft mc, Component text,
                                   int x, int y, int color) {
        List<FormattedCharSequence> lines = mc.font.split(text, WRAP_WIDTH);
        for (FormattedCharSequence line : lines) {
            graphics.drawString(mc.font, line, x, y, color, false);
            y += LINE_HEIGHT;
        }
        return y;
    }
}
