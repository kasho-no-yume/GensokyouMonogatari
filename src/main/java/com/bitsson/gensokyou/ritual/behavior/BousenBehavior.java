package com.bitsson.gensokyou.ritual.behavior;

import com.bitsson.gensokyou.balance.BalanceNumbers;
import com.bitsson.gensokyou.config.GensokyouServerConfig;

import com.bitsson.gensokyou.ritual.BousenState;
import com.bitsson.gensokyou.ritual.RitualBehaviorState;

import com.bitsson.gensokyou.ritual.SpiritPowerAccess;

import com.bitsson.gensokyou.block.entity.RitualCoreBlockEntity;
import com.bitsson.gensokyou.network.InfoLine;
import com.bitsson.gensokyou.ritual.BousenLanterns;
import com.bitsson.gensokyou.ritual.RitualBehavior;
import com.bitsson.gensokyou.ritual.RitualMatch;
import com.bitsson.gensokyou.spirit.SpiritCoreItem;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * 忘川灯坛：加速衰减产灵的仪式。
 *
 * <p><b>产灵判据 = 核心已启动 ∧ 当前产灵速率 rate > 0。</b>速率随熄灭根数 {@code d} 按
 * {@code rate = R·[1 − (d/h)^γ]} 加速衰减（{@code h} = 蜡烛数一半，γ 默认为 2）：满亮产满速 R，
 * 熄灭越多掉得越快，到 {@code d ≥ h} 归零停产。"灭一根"与"灭到阈值"不再同义。
 * 熄灭时清缓存、扣费、落物一律不做，缓存是已挣到的钱。
 * rate 归零也<b>不</b>触发停机（{@code enabled} 保持不变），故玩家补灯使能 rate>0 即自动恢复，
 * 无须再点启动。
 *
 * <h2>阈值停机与自动恢复</h2>
 * 熄灭结算与产灵同样以 {@code rate > 0} 为前置。故点亮数降到半阈值即停产并停止掷骰，点亮数冻结；
 * 玩家补灯使 rate>0 后两者自动恢复。这给玩家 11~46 分钟的自主运行余裕，而非"灭一根就全停"。
 *
 * <h2>熄灭模型：只模拟「根数」，不逐根 tick</h2>
 * 朴素实现是"每根蜡烛一个倒计时，到点掷一次 p 的骰子"。本实现改为每次结算抽
 * {@code k ~ Binomial(点亮数, p)}，再从点亮集合中均匀抽 {@code k} 根改写为熄灭。
 *
 * <p><b>二者分布等价，不是近似</b>：朴素过程对熄灭集合 {@code S} 的概率是
 * {@code p^|S|(1-p)^(n-|S|)}，只依赖 {@code |S|}，故条件于根数时熄灭集合在所有 {@code C(n,k)}
 * 个子集上均匀分布。于是"先抽 k、再均匀抽子集"与"逐根独立掷币"给出<b>完全相同的联合分布</b>——
 * 不仅根数分布相同，连"是哪几根"也相同。
 *
 * <p>实现上进一步把工作量压到 O(k)：抽 {@code k} 用几何跳跃法（{@link #sampleBinomial}，
 * 循环圈数 ≈ E[k]，本仪式 E[k] 仅 0.16~0.64），抽熄灭对象用对掩码的拒绝采样
 * （{@link #pickLitIndices}）。稳态每结算帧 ≈ 1 次采样 + 0.16~0.64 次 {@code setBlock}。
 *
 * <p>⚠️ 顺序不可简化为单步：均匀抽 k 个只在<b>条件于 k</b> 时才等价于伯努利乘积律。
 *
 * <h2>蜡烛 vs 结构</h2>
 * 熄灭（{@code LIT=false}）<b>不</b>影响结构：pattern 谓词是纯方块 id、不带状态后缀，两种状态
 * 匹配结果完全相同。打掉蜡烛则该格失配 → 走框架既有的结构失效路径（自动停机 + 清理），本仪式
 * 不开豁免。
 *
 * @see BousenLanterns 位表与跨端位序不变量
 */
public class BousenBehavior implements RitualBehavior {

    @Override
    public com.bitsson.gensokyou.ritual.RitualRenderState buildRenderState(RitualMatch match, SpiritPowerAccess core) {
        return new com.bitsson.gensokyou.ritual.RitualRenderState(com.bitsson.gensokyou.ritual.RitualRenderState.KIND_BOUSEN, core.isEnabled(), match.level(), 0, 0, 0, new long[0], 0,
                bousen(core).litMask());
    }

    private static BousenState bousen(SpiritPowerAccess core) {
        return (BousenState) core.behaviorState();
    }

    @Override
    public long capacity(int level, SpiritPowerAccess core) {
        return capacityOf(level);
    }

    @Override
    public RitualBehaviorState newState() {
        return new BousenState();
    }

    /** 全亮色 / 缺灯色（信息行用；与屏障仪式同色系）。 */
    private static final int COLOR_OK = 0xFF2E8B57;
    private static final int COLOR_WARN = 0xFFE8912A;
    private static final int COLOR_HALT = 0xFFD32F2F;
    private static final int COLOR_PURPLE = 0xFFB39DDB;

    private static final String LANG = "gui.gensokyou.ritual.bousen.";

    // ================================================================= 世界无关纯内核
    // 行为逻辑没有单测框架，故把可测部分做成纯静态函数，由 /gs_debug bousen selftest 实机断言。

    /**
     * 该阶「全部点亮」的掩码。
     *
     * <p>⚠️ <b>必须特判 64</b>：Java 移位数按 64 取模，{@code 1L << 64 == 1L}，故
     * {@code (1L << n) - 1} 在 {@code n == 64} 时得 {@code 0} 而非全 1——忘川三阶恰好 64 根蜡烛，
     * 漏掉这一支会让客户端永远判定"非全亮"，产灵铺光与产灵逻辑同时静默失效。
     */
    public static long fullMask(int candleCount) {
        if (candleCount <= 0) {
            return 0L;
        }
        return candleCount >= Long.SIZE ? -1L : (1L << candleCount) - 1L;
    }

    /** 默认衰减曲率（配置非法值回落）。γ=1 为线性，γ=2 为默认加速曲率。 */
    public static final double DEFAULT_DECAY_EXPONENT = 2.0D;

    /**
     * 归零阈值：蜡烛总数的一半（1/2/3 阶为 8/16/32）。
     *
     * <p>产灵与熄灭结算共用本方法推导阈值，MUST NOT 在各处重复写 {@code total / 2}。
     */
    public static int halfThreshold(int total) {
        return Math.max(0, total) / 2;
    }

    /** 是否仍有产出：点亮数严格大于半阈值（等价于 {@code rate > 0}）。 */
    public static boolean isProducing(int lit, int total) {
        return lit > halfThreshold(total);
    }

    /**
     * 加速衰减产灵速率（世界无关纯函数，接受显式参数便于无游戏环境自检）。
     *
     * <pre>
     *     h    = total / 2
     *     d    = total - lit
     *     rate = 0                          若 d >= h
     *          = R · [1 - (d/h)^gamma]      否则
     * </pre>
     *
     * <p>{@code gamma} 非正或非有限值时钳到 {@link #DEFAULT_DECAY_EXPONENT}；返回值恒非负。
     * {@code level} 仅为签名一致性保留，速率本身由 {@code baseRate} 直接给出。
     */
    public static long rate(int level, int lit, int total, double gamma, double baseRate) {
        int h = halfThreshold(total);
        int d = Math.max(0, total - lit);
        if (h <= 0 || d >= h || !(baseRate > 0.0D)) {
            return 0L;
        }
        if (!(gamma > 0.0D) || !Double.isFinite(gamma)) {
            gamma = DEFAULT_DECAY_EXPONENT;
        }
        double factor = 1.0D - Math.pow((double) d / (double) h, gamma);
        if (factor <= 0.0D) {
            return 0L;
        }
        return Math.round(baseRate * factor);
    }

    /** 读 config 的包装：分阶满速 R × 配置曲率 γ。 */
    public static long currentRate(int level, int lit, int total) {
        return rate(level, lit, total, decayExponent(), produceRatePerSecond(level));
    }

    /** 读取并钳制曲率配置：非正或非有限值回落默认值。 */
    public static double decayExponent() {
        double g = GensokyouServerConfig.BOUSEN_DECAY_EXPONENT.get();
        return (g > 0.0D && Double.isFinite(g)) ? g : DEFAULT_DECAY_EXPONENT;
    }

    /**
     * 精确抽 {@code Binomial(n, p)} 的一个样本，几何跳跃法：连续 {@code g} 根"存活"后是下一根熄灭。
     *
     * <p><b>游标与计数 MUST 分开维护</b>：每轮抽到 {@code g} 根存活后遇一次熄灭，
     * 这一轮<b>消耗 g+1 根</b>（游标前进 g+1）而<b>只熄灭 1 根</b>（计数 +1）。两者混用
     * （例如把 {@code extinguished += survivors + 1} 同时当游标与返回值）会让返回值变成
     * "已消耗根数"而非"已熄灭根数"——它会冲过 n，表现为 E[k] ≈ 4.9（应为 0.16）、
     * max = n（一次全灭）。这是本方法唯一的正确性要点。
     *
     * <p>循环圈数 = 熄灭根数 ≈ E[k]（本仪式 0.16~0.64），而非"翻 n 次硬币"的 O(n)。
     */
    public static int sampleBinomial(int n, double p, RandomSource rng) {
        if (n <= 0 || p <= 0.0D) {
            return 0;
        }
        if (p >= 1.0D) {
            return n;
        }
        int extinguished = 0;
        int consumed = 0;
        while (consumed < n) {
            // g = 连续存活根数 ~ Geometric(1-p)：P(g)=(1-p)^{g+1} - (1-p)^g = p(1-p)^g
            // 用对数反变换一次抽定（1-nextDouble() 落在 (0,1]，故恒有 g >= 0）
            double u = Math.max(Double.MIN_NORMAL, 1.0D - rng.nextDouble());
            int survivors = (int) Math.floor(Math.log(u) / Math.log1p(-p));
            if (survivors >= n - consumed) {
                break;
            }
            consumed += survivors + 1;   // 游标：消耗 g 根存活 + 1 根熄灭
            extinguished++;            // 计数：只 +1
        }
        return extinguished;
    }

    /**
     * 从 {@code mask} 的置位中<b>均匀随机</b>抽 {@code k} 个下标，写入 {@code out} 并返回写入数。
     *
     * <p><b>⚠️ MUST NOT 用「拒绝采样 {@code nextLong() & mask} + {@code numberOfTrailingZeros}」</b>，
     * 它<b>不是</b>均匀的：{@code nextLong() & mask} 在 2^k 个取值上均匀，但"最低置位是 j"的取值
     * 有 {@code 2^(k-1-j)} 个，故 {@code P(选中第 j 位) = 2^(-j-1)}——<b>最小下标以 1/2 中选、次小 1/4</b>，
     * 逐位指数衰减。实测（两根灯 bit3+bit60，20 万次）：旧法 0.667/0.333，
     * 三根灯抽 2 根的三个子集 0.571/0.286/0.143——全部偏向低位。
     * 现场症状是<b>每次都吹固定那一个位置</b>（永远最小下标，清掉后次小又以 1/2 中选）。
     *
     * <p>正解：先 {@code nextInt(remaining)} 均匀选"第 r 个置位"，再用 {@code m &= m-1}
     * （清最低置位）迭代 r 次把前 r 个跳过去，剩余的最低置位即第 r 个。抽中后从候选里移除，
     * 故 k 次抽取构成一个<b>均匀随机 k-子集</b>（这正是 §1.2 分布等价论证的第二半所要求的）。
     *
     * <p>开销 O(r) ≈ O(remaining/2) 次位运算，且只在真正要熄灯时才付。
     *
     * <p>返回实际写入数（{@code k} 超出可用位数时自动收敛到可用数）。
     */
    public static int pickLitIndices(long mask, int k, RandomSource rng, int[] out) {
        if (mask == 0L || k <= 0) {
            return 0;
        }
        int taken = 0;
        long work = mask;
        int remaining = Long.bitCount(work);
        for (int i = 0; i < k && remaining > 0; i++) {
            int r = rng.nextInt(remaining);
            long probe = work;
            for (int skip = 0; skip < r; skip++) {
                probe &= probe - 1L;   // 清最低置位
            }
            int idx = Long.numberOfTrailingZeros(probe);
            work &= ~(1L << idx);      // 抽中即移出候选 → 后续抽样不含它，结果无重复
            remaining--;
            if (taken < out.length) {
                out[taken] = idx;
                taken++;
            }
        }
        return taken;
    }

    /** 该位置是否是一根<b>点亮</b>的蜡烛（非蜡烛方块一律判否，故拆灯等同缺灯）。 */
    public static boolean isLit(BlockState state) {
        return state.is(Blocks.CANDLE) && state.getValue(CandleBlock.LIT);
    }

    /** 分阶表取值：索引 = 等级 - 1，越界夹到最近的有效档，MUST NOT 抛异常。 */
    public static double tableValue(List<? extends Double> table, int level) {
        if (table == null || table.isEmpty()) {
            return 0.0D;
        }
        int index = Math.max(0, level) - 1;
        if (index >= table.size()) {
            index = table.size() - 1;
        }
        Double value = table.get(index);
        return value == null ? 0.0D : Math.max(0.0D, value);
    }

    // ================================================================= 分阶数值

    /** 每秒产灵（全亮时）。 */
    public static long produceRatePerSecond(int level) {
        return Math.round(tableValue(GensokyouServerConfig.BOUSEN_PRODUCE_RATE_PER_SECOND.get(), level));
    }

    /** 缓存上限。 */
    public static long capacityOf(int level) {
        return Math.round(tableValue(GensokyouServerConfig.BOUSEN_CAPACITY.get(), level));
    }

    /** 供灵端点上限（静态，MUST NOT 随灯火明灭变化，否则路由源会闪断）。 */
    public static long outRateOf(int level) {
        return Math.round(tableValue(GensokyouServerConfig.BOUSEN_OUT_RATE_PER_SECOND.get(), level));
    }

    /** 每根蜡烛在单个结算帧的熄灭概率。 */
    public static double extinguishChance(int level) {
        return Math.min(1.0D, tableValue(GensokyouServerConfig.BOUSEN_EXTINGUISH_CHANCE.get(), level));
    }

    /** 熄灭结算周期（tick）。 */
    public static int extinguishPeriodTicks() {
        return Math.max(1, BalanceNumbers.BOUSEN_EXTINGUISH_PERIOD_TICKS);
    }

    /** 蜡烛全量重扫周期（tick）。 */
    public static int scanPeriodTicks() {
        return Math.max(1, BalanceNumbers.BOUSEN_CANDLE_SCAN_PERIOD_TICKS);
    }

    // ================================================================= tick 通道

    /** 发电仪式：能量方向是缓存 → 电池，显式豁免默认的"电池 → 缓存"。 */
    @Override
    public boolean refillsCacheFromSocket() {
        return false;
    }

    /**
     * 被动通道（成型即跑，<b>无 enabled 门控</b>）：蜡烛重扫 + 缓存回流。
     *
     * <p>重扫刻意放这里而非 {@code serverTick}：停机态的 GUI 与渲染态同样需要真实灯火数
     * （玩家正是靠它决定去补哪一根），放启停通道会让停机界面显示过期数据。
     */
    @Override
    public void serverPassiveTick(ServerLevel level, BlockPos corePos, RitualMatch match,
                                  SpiritPowerAccess core) {
        if (core.ageTicks() % scanPeriodTicks() == 0L) {
            rescan(level, match, core);
        }
        // 缓存回流不受启停与灯火门控：缓存是已挣到的钱，停机/缺灯都照常回流槽核。
        if (core.ageTicks() % 20L == 0L) {
            core.tickBatteryAutoFill();
        }
    }

    /**
     * 启动通道（仅 enabled）：逐秒按衰减速率产灵 + 定期熄灭结算，两者均以 {@code rate > 0} 为前置。
     *
     * <p>产灵不再要求全亮：点亮数越多速率越高，到半阈值归零即停产并停掷。先产后熄的时序不变。
     */
    @Override
    public void serverTick(ServerLevel level, BlockPos corePos, RitualMatch match,
                           SpiritPowerAccess core) {
        int lit = bousen(core).litCount();
        int total = bousen(core).lanternTotal();
        long produced = currentRate(match.level(), lit, total);
        if (core.ageTicks() % 20L == 0L && produced > 0L) {
            deposit(core, produced);
        }
        int period = extinguishPeriodTicks();
        if (period > 0 && core.ageTicks() % period == 0L && produced > 0L) {
            rollExtinguish(level, match, core);
        }
    }

    /** 熄灭掷骰专用的随机序列名（{@code ServerLevel.getRandomSequence} 的 key）。 */
    private static final ResourceLocation RNG_EXTINGUISH =
            ResourceLocation.fromNamespaceAndPath(com.bitsson.gensokyou.Gensokyou.MODID,
                    "bousen_extinguish");

    /**
     * 掷骰用<b>按名字独立的随机序列</b>，MUST NOT 用 {@code level.getRandom()}。
     *
     * <p>⚠️ {@code Level.getRandom()} 返回的是 {@code public final RandomSource random =
     * RandomSource.create()}——<b>固定种子 0、被天气/雷声/随机刻/熔炉共享</b>的遗留流（真正的
     * 线程安全流是另一个私有字段 {@code threadSafeRandom}，{@code getRandom()} 并不走它）。
     * 熄灭结算是<b>固定 200 tick 节拍</b>去取值的，在这条例行流上取到的是高度重复的序列；
     * 而 {@link #sampleBinomial} 的几何跳跃在 {@code survivors < n} 时会一直推进到
     * {@code extinguished >= n} 才停——RNG 一旦重复，{@code survivors} 恒定，k 直接被推到 n，
     * 表现为<b>一次把全部蜡烛吹灭</b>并反复发生。
     *
     * <p>{@code getRandomSequence} 走 {@code RandomSequences}（SavedData 支撑、逐维度持久化、
     * 每个名字一条独立且正常推进的流），是本处唯一正确的取法。
     */
    private static RandomSource extinguishRandom(ServerLevel level) {
        return level.getRandomSequence(RNG_EXTINGUISH);
    }

    /** 全量重扫蜡烛点亮态 → 瞬态掩码。唯一读取蜡烛的地方。 */
    private static void rescan(ServerLevel level, RitualMatch match, SpiritPowerAccess core) {
        List<BlockPos> candles = BousenLanterns.positions(match);
        long mask = 0L;
        for (int i = 0; i < candles.size() && i < Long.SIZE; i++) {
            if (isLit(level.getBlockState(candles.get(i)))) {
                mask |= 1L << i;
            }
        }
        bousen(core).setLanterns(mask, Long.bitCount(mask), candles.size());
    }
    /**
     * 核优先（不限速直注）→ 溢出进缓存（截到上限，余量作废）。
     *
     * <p>走普通 receive / {@link SpiritCoreItem#receive}，<b>不</b>走
     * {@code extractRouted/receiveRouted}——否则被自身 inRate=0 误截。
     */
    private static void deposit(SpiritPowerAccess core, long amount) {
        long rest = amount;
        ItemStack battery = core.batteryStack();
        if (battery.getItem() instanceof SpiritCoreItem) {
            long toCore = SpiritCoreItem.receive(battery, rest);
            if (toCore > 0L) {
                core.setBatteryStack(battery);
                rest -= toCore;
            }
        }
        if (rest > 0L) {
            core.receive(rest);
        }
    }

    /**
     * 熄灭结算：抽 {@code k ~ Binomial(点亮数, p)}，均匀抽 k 根改写为熄灭。
     *
     * <p><b>flags = 2</b>（只广播客户端、不触发邻居更新）：蜡烛的 LIT 是状态属性、不改变碰撞盒与
     * 光照几何，触发邻居更新只会白白惊动结构内 64 个祭品台与整片匹配区域的方块更新检查。
     */
    private static void rollExtinguish(ServerLevel level, RitualMatch match,
                                       SpiritPowerAccess core) {
        int lit = bousen(core).litCount();
        double p = extinguishChance(match.level());
        if (lit <= 0 || p <= 0.0D) {
            return;
        }
        List<BlockPos> candles = BousenLanterns.positions(match);
        RandomSource rng = extinguishRandom(level);
        int k = sampleBinomial(lit, p, rng);
        if (k <= 0) {
            return;
        }
        int[] picked = new int[k];
        int taken = pickLitIndices(bousen(core).litMask(), k, rng, picked);
        long mask = bousen(core).litMask();
        for (int i = 0; i < taken; i++) {
            int index = picked[i];
            if (index >= candles.size()) {
                continue;
            }
            BlockPos pos = candles.get(index);
            BlockState state = level.getBlockState(pos);
            if (!isLit(state)) {
                continue;
            }
            level.setBlock(pos, state.setValue(CandleBlock.LIT, Boolean.FALSE), 2);
            mask &= ~(1L << index);
        }
        // 立即落一次刷新，避免同一 tick 的产灵/渲染态读到过期掩码；下个 1Hz 重扫会再确认一次。
        bousen(core).setLanterns(mask, Long.bitCount(mask), candles.size());
    }

    // ================================================================= 端点

    /**
     * 供灵上限：分阶静态值。缓存向外流不外乎灯火明灭，否则路由按结算周期 memo 端点速率并以
     * {@code > 0} 筛源，动态速率会让源在明灭瞬间闪断。
     */
    @Override
    public long spiritOutRatePerSecond(ServerLevel level, BlockPos corePos, RitualMatch match,
                                       SpiritPowerAccess core) {
        return outRateOf(match.level());
    }

    // ================================================================= 结构失效

    /** 结构失效：清瞬态灯火态。字段本身不持久化，但显式清零可避免停机瞬间的 GUI 读到上一形态。 */
    @Override
    public void onStructureLost(ServerLevel level, BlockPos corePos) {
        if (level.getBlockEntity(corePos) instanceof SpiritPowerAccess core) {
            bousen(core).setLanterns(0L, 0, 0);
        }
    }

    // ================================================================= GUI

    /**
     * 全亮判定。衰减产灵后仅用于 GUI 灯火行分色（全亮绿）与 debug 探针，MUST NOT 再作为产灵前置。
     */
    private static boolean allLit(SpiritPowerAccess core) {
        return bousen(core).lanternTotal() > 0 && bousen(core).litCount() == bousen(core).lanternTotal();
    }

    /**
     * 完全覆写（<b>不</b>叠加基类默认清单）：本仪式无 requirements，叠加会渲染一个
     * 64 项的空祭品核对清单把首屏挤爆。
     */
    @Override
    public List<InfoLine> uiInfo(ServerLevel level, BlockPos corePos, RitualMatch match,
                                 SpiritPowerAccess core) {
        int lit = bousen(core).litCount();
        int total = bousen(core).lanternTotal();
        int h = halfThreshold(total);
        boolean full = allLit(core);
        long rate = currentRate(match.level(), lit, total);
        boolean producing = rate > 0L;
        int lanternColor = full ? COLOR_OK : (producing ? COLOR_WARN : COLOR_HALT);
        long outRate = outRateOf(match.level());
        double p = extinguishChance(match.level());
        int period = extinguishPeriodTicks();
        double gamma = decayExponent();

        List<InfoLine> lines = new ArrayList<>();
        // 灯火行：可见只放 N/M，明细全进 tip（宽度红线）；rate=0 标红（区别于缺灯橙）
        lines.add(InfoLine.tipped(LANG + "lanterns",
                new String[]{InfoLine.compact(lit), InfoLine.compact(total)},
                lanternColor,
                LANG + "lanterns.tip",
                new String[]{
                        String.valueOf(lit), String.valueOf(total),
                        String.valueOf(total - lit),
                        trimPercent(p) + "%",
                        String.valueOf(period),
                        expectedGapSeconds(total, p, period),
                        String.valueOf(full),
                        InfoLine.compact(rate),
                        String.valueOf(h),
                        String.valueOf(gamma)}));
        // 产灵行：显示实时速率；仅 rate=0 时给独立停产文案，玩家读作设计而非故障
        lines.add(producing
                ? InfoLine.tipped(LANG + "producing", new String[]{InfoLine.compact(rate)},
                    COLOR_OK, LANG + "producing.tip",
                    new String[]{String.valueOf(rate), InfoLine.compact(core.getStored()),
                        InfoLine.compact(core.getCapacity())})
                : new InfoLine(LANG + "halted", new String[0], "", COLOR_HALT, -1F, null));
        // 供灵行
        lines.add(InfoLine.tipped(LANG + "supply", new String[]{InfoLine.compact(outRate)},
                COLOR_PURPLE, LANG + "supply.tip", new String[]{String.valueOf(outRate)}));
        for (int i = 1; i <= 5; i++) {
            lines.add(new InfoLine(LANG + "lore_" + i, new String[0], "", COLOR_PURPLE, -1F, null));
        }
        return lines;
    }

    /** 概率按百分比展示，去掉无意义的尾零。客户端（指导书阶级页）共用同一实现，避免两处走样。 */
    public static String trimPercent(double p) {
        double pct = p * 100.0D;
        String s = String.format(java.util.Locale.ROOT, "%.4f", pct);
        while (s.endsWith("0")) {
            s = s.substring(0, s.length() - 1);
        }
        if (s.endsWith(".")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    /** 首次熄灭的期望间隔（秒）；无蜡烛或概率为 0 时给 "—"。 */
    private static String expectedGapSeconds(int total, double p, int periodTicks) {
        if (total <= 0 || p <= 0.0D) {
            return "-";
        }
        double perFrame = total * p;
        if (perFrame <= 0.0D) {
            return "-";
        }
        return String.valueOf(Math.round(periodTicks / 20.0D / perFrame));
    }

    // ================================================================= 调试探针

    /** 机读单行（供外部 harness 解析）。 */
    public static String debugSummary(ServerLevel level, BlockPos corePos, RitualMatch match,
                                      SpiritPowerAccess core) {
        int lit = bousen(core).litCount();
        int total = bousen(core).lanternTotal();
        return "BOUSEN level=" + match.level()
                + " enabled=" + core.isEnabled()
                + " candles=" + total
                + " lit=" + lit
                + " d=" + (total - lit)
                + " h=" + halfThreshold(total)
                + " allLit=" + allLit(core)
                + " mask=0x" + Long.toHexString(bousen(core).litMask())
                + " rate=" + currentRate(match.level(), lit, total)
                + " gamma=" + decayExponent()
                + " capacity=" + capacityOf(match.level())
                + " stored=" + core.getStored()
                + " outRate=" + outRateOf(match.level())
                + " p=" + extinguishChance(match.level())
                + " period=" + extinguishPeriodTicks();
    }

    /**
     * 世界无关内核自检（不依赖世界，可直调）。返回失败项列表，空 = 全通过。
     *
     * <p>重点覆盖三处最容易静默出错的地方：64 位掩码特判、二项采样均值、
     * 以及"抽中的下标必落在置位上且无重复"。
     */
    public static List<String> selftest(RandomSource rng) {
        List<String> failures = new ArrayList<>();
        // 0) RNG 健康度：连续取样 MUST 互不相同。
        //    这条守卫的是 {@link #extinguishRandom} 的取法——历史上误用 level.getRandom()
        //    （固定种子、被共享的遗留流）时，固定节拍取到高度重复的值，几何跳跃直接推出 k=n 全灭。
        //    一旦 RNG 退化成常量，sampleBinomial 的均值会同时爆掉，故这里第一道就拦。
        double first = rng.nextDouble();
        boolean distinct = false;
        for (int i = 0; i < 8; i++) {
            if (rng.nextDouble() != first) {
                distinct = true;
                break;
            }
        }
        if (!distinct) {
            failures.add("RNG degenerate: 9 consecutive nextDouble() all equal "
                    + first + " (do NOT use Level#getRandom, use getRandomSequence)");
        }
        // 1) 满掩码：64 必须特判，否则 (1L << 64) - 1 == 0
        if (fullMask(16) != 0xFFFFL) {
            failures.add("fullMask(16)=" + fullMask(16));
        }
        if (fullMask(32) != 0xFFFFFFFFL) {
            failures.add("fullMask(32)=" + fullMask(32));
        }
        if (fullMask(64) != -1L) {
            failures.add("fullMask(64)=" + fullMask(64) + " (want -1; (1L<<64)-1==0 is the trap)");
        }
        if (fullMask(0) != 0L) {
            failures.add("fullMask(0)=" + fullMask(0));
        }
        // 2) 三阶 n*p 恒等（熄灭梯度的设计前提）
        double rate1 = 16 * 0.01D;
        double rate2 = 32 * 0.005D;
        double rate3 = 64 * 0.0025D;
        if (Math.abs(rate1 - rate2) > 1e-9D || Math.abs(rate1 - rate3) > 1e-9D) {
            failures.add("n*p not constant: " + rate1 + "/" + rate2 + "/" + rate3);
        }
        // 3) 二项采样均值（10 万次）
        int n = 64;
        double p = 0.0025D;
        int trials = 100_000;
        long sum = 0L;
        for (int i = 0; i < trials; i++) {
            sum += sampleBinomial(n, p, rng);
        }
        double mean = (double) sum / trials;
        if (Math.abs(mean - n * p) > 0.02D) {
            failures.add("sampleBinomial mean=" + mean + " want " + (n * p));
        }
        // 4) 边界
        if (sampleBinomial(0, p, rng) != 0 || sampleBinomial(64, 0.0D, rng) != 0) {
            failures.add("sampleBinomial boundary");
        }
        if (sampleBinomial(64, 1.0D, rng) != 64) {
            failures.add("sampleBinomial(p=1)");
        }
        // 5) 抽样下标必须落在置位上且无重复
        long mask = fullMask(64) & ~((1L << 7) | (1L << 40));
        int[] out = new int[64];
        int taken = pickLitIndices(mask, 8, rng, out);
        if (taken != 8) {
            failures.add("pickLitIndices taken=" + taken);
        }
        boolean[] seen = new boolean[64];
        for (int i = 0; i < taken; i++) {
            int idx = out[i];
            if (idx < 0 || idx >= 64 || (mask & (1L << idx)) == 0L) {
                failures.add("picked index not lit: " + idx);
            }
            if (seen[idx]) {
                failures.add("duplicate index: " + idx);
            }
            seen[idx] = true;
        }
        if (pickLitIndices(0L, 4, rng, out) != 0) {
            failures.add("pickLitIndices on empty mask");
        }
        // 6) 均匀性回归：这是唯一能抓住"nextLong() & mask + numberOfTrailingZeros"那个
        //    非均匀实现的检查（它的 P(第 j 位) = 2^(-j-1)，2 位掩码下是 0.667/0.333）。
        //    现场症状只是"总吹固定那个位置"，根数分布完全正常，故 MUST 单独断言均匀性。
        int rounds = 200_000;
        int[] pair = new int[1];
        long twoBit = (1L << 3) | (1L << 60);
        int low = 0;
        for (int i = 0; i < rounds; i++) {
            pickLitIndices(twoBit, 1, rng, pair);
            if (pair[0] == 3) {
                low++;
            }
        }
        double lowShare = (double) low / rounds;
        if (Math.abs(lowShare - 0.5D) > 0.02D) {
            failures.add("pickLitIndices NOT uniform: bit3 share=" + lowShare
                    + " (want 0.5; 2^(-j-1) bias gives ~0.667)");
        }
        // 7) 加速衰减曲线：边界（满亮取满速 / 半阈值归零 / 越过阈值恒零）
        long r0 = rate(1, 16, 16, 2.0D, 100.0D);
        long rh = rate(1, 8, 16, 2.0D, 100.0D);
        long rOver = rate(1, 0, 16, 2.0D, 100.0D);
        if (r0 != 100L || rh != 0L || rOver != 0L) {
            failures.add("rate boundary: rate(0)=" + r0 + " rate(h)=" + rh
                    + " rate(d>h)=" + rOver + " (want 100/0/0)");
        }
        // 8) 严格单调递减（d=0..h，逐点比较）
        boolean monotone = true;
        for (int d = 0; d < 8; d++) {
            long cur = rate(1, 16 - d, 16, 2.0D, 100.0D);
            long next = rate(1, 16 - (d + 1), 16, 2.0D, 100.0D);
            if (cur <= next) {
                monotone = false;
                break;
            }
        }
        if (!monotone) {
            failures.add("rate not strictly decreasing over d=0..h");
        }
        // 9) 中间值采样（确认加速形状，容差 ±1）
        int[] at = {1, 4, 7};
        long[] want = {98L, 75L, 23L};
        for (int i = 0; i < at.length; i++) {
            long got = rate(1, 16 - at[i], 16, 2.0D, 100.0D);
            if (Math.abs(got - want[i]) > 1L) {
                failures.add("rate(d=" + at[i] + ")=" + got + " want " + want[i] + " +/-1");
            }
        }
        // 10) 曲率非法值钳到默认
        long clampZero = rate(1, 15, 16, 0.0D, 100.0D);
        long clampNaN = rate(1, 15, 16, Double.NaN, 100.0D);
        long clampDefault = rate(1, 15, 16, DEFAULT_DECAY_EXPONENT, 100.0D);
        if (clampZero != clampDefault || clampNaN != clampDefault) {
            failures.add("rate gamma clamp (<=0 / NaN must fall back to " + DEFAULT_DECAY_EXPONENT + ")");
        }
        return failures;
    }
}
