package com.bitsson.gensokyou.ritual.behavior;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.block.entity.RitualCoreBlockEntity;
import com.bitsson.gensokyou.block.entity.RitualPedestalBlockEntity;
import com.bitsson.gensokyou.block.entity.SukimaBlockEntity;
import com.bitsson.gensokyou.config.GensokyouConfig;
import com.bitsson.gensokyou.network.InfoLine;
import com.bitsson.gensokyou.network.ModNetworking;
import com.bitsson.gensokyou.registry.ModBlocks;
import com.bitsson.gensokyou.ritual.RitualBehavior;
import com.bitsson.gensokyou.ritual.RitualBehaviors;
import com.bitsson.gensokyou.ritual.RitualCoreRegistry;
import com.bitsson.gensokyou.ritual.RitualMatch;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 结界破坏：无需启停的被动充能仪式，充盈且祭品齐备即闩锁开启隙间传送门（永久）。
 *
 * <p><b>供灵拓扑（唯一通路）</b>：产灵仪式 → 万象共鸣 → 八方归元 → 万象共鸣 → 本核心。
 * 本核心不暴露灵力核心槽（{@link #usesCoreSocket()}），且受灵上限恒等于附近八方归元的
 * 聚合输出——八方归元不存在时返回 0，而万象共鸣的受灵汇筛选要求受灵上限 &gt; 0
 * （{@code ResonanceRelayBehavior} 的 sink 过滤），故此时本核心对路由器完全不可见。
 * 两条叠加即结构性封死其他一切注灵途径。
 *
 * <p><b>不设额外硬门槛</b>：除「缓存充盈至 {@code getCapacity()} 且祭品齐备」外，本类
 * MUST NOT 引入任何其他准入判定——托管核数量/品阶、归元阶位、供灵链拓扑与距离均只作
 * 信息呈现。核数不足表现为增速为零或为负（物理事实），而非「被拒绝」。
 *
 * <p><b>闩锁</b>：独立于 {@code enabled} 的持久化字段。框架在 activeMatch 失效时会把
 * {@code enabled} 归零，而需求要求「拆普通方块又补回来，传送门必须仍是开的」。
 * {@link #onStructureLost} 依据 {@code core.activeMatch()} 分流：null = 结构暂时拆毁
 * → 保留闩锁与门；非 null = 变成别的仪式 → 清闩锁并请求关门。
 */
public class BarrierBreakBehavior implements RitualBehavior {

    /** 扣费周期（tick）。1 秒一次，故配置以「每秒」为单位且无需定点进位。 */
    private static final int DRAIN_PERIOD_TICKS = 20;

    /** 祭品轮询周期（tick）→ 5 Hz。祭品台放置物品不产生方块更新，核心收不到通知。 */
    private static final int OFFERING_POLL_TICKS = 4;

    /** 主世界侧门相对核心的偏移：核心正上方 2 格（pattern 在 (0,2..7,0) 留了 6 格空气井即裂口笼）。 */
    public static final int PORTAL_UP = 2;

    /** 幻想乡侧孪生门相对该维度地表落点的水平偏移。必须 &gt; 0，否则玩家一落地即被弹回。 */
    public static final int TWIN_OFFSET = 4;

    /** 孪生门落点外扩搜索半径（格），用于避开水面/熔岩/深坑。 */
    private static final int TWIN_SEARCH_RADIUS = 12;

    /** 启动爆发的击退半径（1× 眼基准，实际乘以尺寸标量）与力度。 */
    private static final double BURST_KNOCKBACK_RADIUS = 4.0D;
    /** 击退力度；{@code public} 是因为门体在爆炸那一 tick 调用它（见 {@link #burstKnockbackRadius}）。 */
    public static final double BURST_KNOCKBACK_STRENGTH = 0.85D;

    private static final String KEY_STATE = "gui.gensokyou.ritual.barrier.state.";
    private static final String KEY_SUPPLY = "gui.gensokyou.ritual.barrier.supply";
    private static final String KEY_SUPPLY_TIP = "gui.gensokyou.ritual.barrier.supply_tip";
    private static final String KEY_INTAKE = "gui.gensokyou.ritual.barrier.intake";
    private static final String KEY_INTAKE_TIP = "gui.gensokyou.ritual.barrier.intake_tip";
    private static final String KEY_NO_NETWORK = "gui.gensokyou.ritual.barrier.no_network";
    private static final String KEY_DRAIN = "gui.gensokyou.ritual.barrier.drain";
    private static final String KEY_DRAIN_TIP = "gui.gensokyou.ritual.barrier.drain_tip";
    private static final String KEY_HINT = "gui.gensokyou.ritual.barrier.hint";
    private static final String KEY_TWIN_SKIPPED = "gui.gensokyou.ritual.barrier.twin_skipped";
    private static final String KEY_OFFERING = "gui.gensokyou.ritual.barrier.offering";
    private static final String KEY_OFFERING_TIP = "gui.gensokyou.ritual.barrier.offering_tip";

    private static final int COLOR_OK = 0xFF2E8B57;
    private static final int COLOR_BAD = 0xFFB22222;
    private static final int COLOR_WARN = 0xFFE8912A;
    private static final int COLOR_PURPLE = 0xFFB39DDB;

    /**
     * 供灵诊断快照：界面状态行、进度条与受灵上限共用同一份计算，杜绝口径分叉。
     *
     * <p>两条<b>互相独立</b>的供灵途径：
     * <ul>
     *   <li><b>路由</b>（{@code bankOutRate}）——八方归元经万象共鸣送来。这是唯一的「网络」
     *       途径，也是 {@link #spiritInRatePerSecond} 的口径：<b>不</b>把 socket 速率算进去，
     *       否则路由器的汇端预算会被本地供电虚增。</li>
     *   <li><b>槽核</b>（{@code socketRate}）——核心 GUI 槽内的灵力核心直接注入缓存
     *       （电池→缓存方向）。不经路由器，故不计入受灵上限。</li>
     * </ul>
     * 两者之和才是缓存的实际增速来源，故 {@link #inRate()} 为二者之和。
     */
    public record Supply(int bankCount, long bankStored, long bankOutRate,
                         long socketRate, long socketStored, long drainPerSecond) {

        /** 实际总入流：路由 + 槽核。 */
        public long inRate() {
            long sum = bankOutRate + socketRate;
            return sum < 0L ? Long.MAX_VALUE : sum;
        }

        public long net() {
            return inRate() - drainPerSecond;
        }

        /** 供灵完全缺失：既无合格归元，也无带电的槽核。 */
        public boolean missing() {
            return inRate() <= 0L;
        }

        /** 有供灵但追不上流失：进度条会倒退，必须显式提示。 */
        public boolean insufficient() {
            return !missing() && net() <= 0L;
        }

        public long deficit() {
            return Math.max(0L, drainPerSecond() - inRate());
        }

        /** 已闩锁时的零快照（闩锁后不再耗灵，供灵数据无意义）。 */
        public static Supply idle() {
            return new Supply(0, 0L, 0L, 0L, 0L, 0L);
        }
    }

    public enum State { NO_NETWORK, INSUFFICIENT, CHARGING, AWAITING, OPEN }

    // ------------------------------------------------------------------ 供灵

    /**
     * 汇总两条供灵途径：附近八方归元的路由输出 + 槽内灵力核心的直注速率。
     *
     * <p>扫描半径取共鸣塔的最大档半径（{@link ResonanceRelayBehavior#radius(int)} 的 level 5），
     * 即「任何一座共鸣塔能覆盖到的归元，本核心都看得见」。取更大值是安全的：真实搬运量始终
     * 由源端 {@code extractRouted} 与汇端 {@code receiveRouted} 双向限速，受灵上限高估只会
     * 让本端账本少一道节流，不会凭空造出灵力。
     *
     * <p><b>存量也一并统计</b>：归元的 {@code spiritOutRatePerSecond} 只看托管核的
     * {@code fillRatePerSecond}，不看存量——四颗空核照样报 256,000/秒，但路由器会以
     * {@code getStored() > 0} 把该归元判为不可供灵。界面必须同时给出存量，否则玩家会盯着
     * 一个满速却永不上涨的进度条。
     */
    public static Supply supply(ServerLevel level, BlockPos corePos,
                                @Nullable RitualCoreBlockEntity core) {
        long drain = RitualCoreBlockEntity.barrierDrainPerSecond();
        int radius = Math.max(1, ResonanceRelayBehavior.radius(5));
        int count = 0;
        long out = 0L;
        long bankStored = 0L;
        for (RitualCoreBlockEntity bank : RitualCoreRegistry.formedWithin(
                level, corePos, radius, RitualBehaviors.BARRIER_BREAK)) {
            if (bank.activeMatch() == null
                    || !bank.activeMatch().patternId()
                            .equals(RitualBehaviors.BAFANG_GUIYUAN)) {
                continue;
            }
            long rate = RitualBehaviors.get(RitualBehaviors.BAFANG_GUIYUAN)
                    .map(b -> b.spiritOutRatePerSecond(level, bank.getBlockPos(),
                            bank.activeMatch(), bank))
                    .orElse(0L);
            if (rate > 0L) {
                count++;
                out = saturatedAdd(out, rate);
                bankStored = saturatedAdd(bankStored, bank.getStored());
            }
        }
        long socketRate = 0L;
        long socketStored = 0L;
        if (core != null && core.batteryStack().getItem()
                instanceof com.bitsson.gensokyou.spirit.SpiritCoreItem socket) {
            socketStored = com.bitsson.gensokyou.spirit.SpiritCoreItem.getStored(core.batteryStack());
            // 与路由器的源筛选（getStored() > 0）同口径：空核不报速率
            if (socketStored > 0L) {
                socketRate = socket.fillRatePerSecond();
            }
        }
        return new Supply(count, bankStored, out, socketRate, socketStored, drain);
    }

    private static long saturatedAdd(long a, long b) {
        long sum = a + b;
        return sum < 0L ? Long.MAX_VALUE : sum;
    }

    /**
     * 受灵上限（<b>路由口径</b>）= 附近八方归元的聚合输出；无归元时为 0。
     *
     * <p>刻意<b>不</b>含槽核速率：槽核走电池→缓存的本地直注，不经路由器；把它算进受灵上限
     * 会虚增汇端预算，让别的受灵汇抢不到额度。
     *
     * <p>动态 inRate 的安全性见 {@code ritual-power-attributes}：该红线只约束
     * {@code spiritOutRatePerSecond}（源闪断会断链），本项为 in 方向——路由每结算周期重建
     * inRates map 重算，速率归零仅使本核心当期不进 sinks；定点进位只在改链时清零。
     */
    @Override
    public long spiritInRatePerSecond(ServerLevel level, BlockPos corePos, RitualMatch match,
                                      RitualCoreBlockEntity core) {
        return supply(level, corePos, core).bankOutRate();
    }

    /** 开放灵力核心槽：允许在本核心直接塞一颗灵核（见类注释「两条供灵途径」）。 */
    @Override
    public boolean usesCoreSocket() {
        return true;
    }

    /**
     * 电池→缓存方向：槽内灵核每 tick 按其 {@code fillRatePerSecond} 补入缓存。
     *
     * <p>与 {@code tickBatteryAutoFill()}（缓存→电池）互斥——二者只应取一。本行为选前者，
     * 故本核心不会把缓存倒回槽核，不存在「缓存↔槽核」闭环空转。
     */
    @Override
    public boolean refillsCacheFromSocket() {
        return true;
    }

    // ------------------------------------------------------------------ 主循环

    @Override
    public void serverPassiveTick(ServerLevel level, BlockPos corePos, RitualMatch match,
                                  RitualCoreBlockEntity core) {
        if (core.isBarrierLatched()) {
            // 闩锁后：永久开启。不耗灵、不校验祭品，只保证两扇门在位。
            if (core.ageTicks() % DRAIN_PERIOD_TICKS == 0L) {
                ensurePortals(level, corePos, core);
            }
            return;
        }
        long capacity = core.getCapacity();
        // 顺序要紧：先让槽核补料，再扣费。同一 tick 内先扣后补会让 net 短暂为负一档。
        core.tickBatteryToCacheFill();
        // 扣费：仅在缓存未满时。满即停——这同时实现了「待献祭状态不再被动耗灵」，
        // 无需为该状态单开分支（缓存已满，路由器再灌也只会撞容量上限被截）。
        if (core.ageTicks() % DRAIN_PERIOD_TICKS == 0L && core.getStored() < capacity) {
            core.extract(RitualCoreBlockEntity.barrierDrainPerSecond());
        }
        // 祭品轮询 + 开启判定：5 Hz
        if (core.ageTicks() % OFFERING_POLL_TICKS == 0L
                && core.getStored() >= capacity
                && offeringsSatisfied(level, match)) {
            open(level, corePos, match, core);
        }
    }

    private static boolean offeringsSatisfied(ServerLevel level, RitualMatch match) {
        return offerings(level, match).satisfied();
    }

    /**
     * 祭品快照：记录两类祭品<b>分别位于哪些台位</b>，列表长度即件数。
     *
     * <p><b>刻意做成无序计数而非 per-slot 绑定</b>：pattern 的 {@code requirements} 只能一条
     * 绑一个 slot（schema 限制，天然有序），若用它表达「4 星银 + 4 潮汐晶」，玩家就必须
     * 搞清 8 个台位里哪个是第几号——而祭品台本来就该无序，那等于把实现细节漏给玩家。
     * 规则因此改为：<b>8 台上任意 4 台放星银、任意 4 台放潮汐晶，摆哪都行。</b>
     *
     * <p>记录位置而非只记件数，是因为<b>祭品在开门时被消耗</b>——需要知道从哪几台扣。
     *
     * <p>代价是本仪式<b>不使用</b> {@code pattern.requirements}（「全项目首个真实使用者」
     * 的说法作废），规则与消耗均由本类持有；换来的是界面能显示「已奉 3/4」这类<b>带计数
     * </b>的两行，而不是 8 行匿名 ✓/✗。
     */
    public record Offerings(List<BlockPos> starSilverAt, List<BlockPos> tideCrystalAt,
                            int required) {

        public int starSilver() {
            return starSilverAt.size();
        }

        public int tideCrystal() {
            return tideCrystalAt.size();
        }

        public boolean satisfied() {
            return starSilver() >= required && tideCrystal() >= required;
        }

        public int total() {
            return starSilver() + tideCrystal();
        }
    }

    /** 每类祭品的需求件数（亦为开门时的扣除件数）。 */
    public static final int OFFERING_REQUIRED = 4;

    private static final String ITEM_STAR_SILVER = "gensokyou:star_silver";
    private static final String ITEM_TIDE_CRYSTAL = "gensokyou:tide_crystal";

    /** 扫 8 个祭品台，记录两类祭品各位于哪些台位（与台位次序无关）。 */
    public static Offerings offerings(ServerLevel level, RitualMatch match) {
        List<BlockPos> silver = new ArrayList<>();
        List<BlockPos> crystal = new ArrayList<>();
        for (BlockPos pos : match.positionsOf('P')) {
            if (!(level.getBlockEntity(pos) instanceof RitualPedestalBlockEntity pedestal)) {
                continue;
            }
            ItemStack held = pedestal.getHeld();
            if (held.isEmpty()) {
                continue;
            }
            String id = BuiltInRegistries.ITEM.getKey(held.getItem()).toString();
            if (ITEM_STAR_SILVER.equals(id)) {
                silver.add(pos.immutable());
            } else if (ITEM_TIDE_CRYSTAL.equals(id)) {
                crystal.add(pos.immutable());
            }
        }
        return new Offerings(List.copyOf(silver), List.copyOf(crystal), OFFERING_REQUIRED);
    }

    /**
     * 扣除祭品：每类各扣 {@link #OFFERING_REQUIRED} 件（单件不变量，逐台扣 1 件）。
     *
     * <p>祭品台为永久消耗——玩家把供品交出去了，传送门是唯一剩下的东西。多放的份数不动
     * （如放 6 颗星银只扣 4 颗），扣完若台面仍有货则保留剩余。
     */
    private static void consumeOfferings(ServerLevel level, Offerings o) {
        for (List<BlockPos> group : List.of(o.starSilverAt(), o.tideCrystalAt())) {
            for (int i = 0; i < o.required() && i < group.size(); i++) {
                BlockPos pos = group.get(i);
                if (level.getBlockEntity(pos) instanceof RitualPedestalBlockEntity pedestal) {
                    ItemStack held = pedestal.getHeld();
                    int left = Math.max(0, held.getCount() - 1);
                    pedestal.setHeld(left == 0 ? ItemStack.EMPTY : held.copyWithCount(left));
                }
            }
        }
    }

    // ------------------------------------------------------------------ 开门 / 关门

    /**
     * 闩锁开启：置闩锁 → <b>扣除祭品</b> → 放置主世界侧门与幻想乡侧孪生门 → 击退 + 音效 + 播报。
     *
     * <p>祭品在开门这一刻被消耗（每类 4 件）。玩家把供品交出去了，传送门是唯一剩下的东西。
     * 闩锁既成后不再校验祭品，故日后拆结构重建也无需重新奉上。
     *
     * <p>爆发粒子与眼睛动画均由门体自身的动画计时器在客户端驱动（{@code SukimaBlockEntity}），
     * 故此处除一次音效广播外不产生任何网络包。
     */
    private static void open(ServerLevel level, BlockPos corePos, RitualMatch match,
                             RitualCoreBlockEntity core) {
        core.setBarrierLatched(true);
        consumeOfferings(level, offerings(level, match));
        placePortals(level, corePos, core);
        core.setPortalPos(mainPortalPos(corePos));
        // 这里只播"门被召唤"的提示音。<b>击退不在此刻</b>——击退落在爆炸那一 tick，
        // 由门体自身（SukimaBlockEntity#playShatterCues）依演出锚点触发：击退是玩法、
        // 必须在服务端权威执行，而只有门体知道爆炸发生在第几 tick。
        burstSound(level, corePos.getX() + 0.5D, corePos.getY() + PORTAL_UP, corePos.getZ() + 0.5D);
        BlockPos twin = twinPortalPos(level, false);
        if (twin != null) {
            burstSound(level, twin.getX() + 0.5D, twin.getY() + 0.5D, twin.getZ() + 0.5D);
        }
        ModNetworking.sendRitualInfoToViewers(level, corePos);
        // 戏剧性时刻：向附近玩家播报（不限于正在看界面的人）
        Component opened = Component.translatable("msg.gensokyou.barrier_latch");
        for (ServerPlayer viewer : level.players()) {
            if (viewer.distanceToSqr(Vec3.atCenterOf(corePos)) <= 4096.0D) {
                viewer.displayClientMessage(opened, false);
            }
        }
    }

    /**
     * 爆炸当刻的击退半径（格）。随眼形尺寸同比放大（2× 眼 = 2× 波及半径），
     * 与烟环的 15 格<b>刻意不同</b>：击退半径调大会把玩家从祭坛上掀飞，
     * 且与既有调好的手感冲突。
     */
    public static double burstKnockbackRadius(float portalScale) {
        return BURST_KNOCKBACK_RADIUS * portalScale;
    }

    /**
     * 让已开启的门重播整段「结界崩解」演出（双门同帧）。
     *
     * <p><b>仅供调试/实机验收</b>。仪式是<b>永久闩锁</b>的，且开启瞬间祭品已被消耗，
     * 因此闩锁态下没有任何办法让演出再跑一遍——不加这个入口，表现就只能盲写。
     * 它刻意<b>不</b>触碰闩锁、不重跑需求判定、不消耗祭品。
     *
     * @return 实际重播的门数（0 = 该核心没有已开启的门）
     */
    public static int replayShatter(ServerLevel level, BlockPos corePos, RitualCoreBlockEntity core) {
        int burst = GensokyouConfig.SUKIMA_PORTAL_BURST_TICKS.get();
        int replayed = 0;
        BlockPos main = core != null && core.portalPos() != null
                ? core.portalPos() : mainPortalPos(corePos);
        if (level.getBlockEntity(main) instanceof SukimaBlockEntity portal) {
            portal.requestOpen(portal.scale(), burst);
            replayed++;
        }
        BlockPos twin = twinPortalPos(level, false);
        // ⚠️ 孪生门在**幻想乡**，replay 也必须去那边找（原来误用 level，
        // 于是 /gs_debug barrier replay 只能刷到主世界那扇，主世界之外的孪生门永远不重播）
        ServerLevel gensokyo = twin == null ? null : gensokyoLevel(level);
        if (gensokyo != null && gensokyo.getBlockEntity(twin) instanceof SukimaBlockEntity twinPortal) {
            twinPortal.requestOpen(twinPortal.scale(), burst);
            replayed++;
        }
        return replayed;
    }

    /**
     * 启动爆发的击退：一次性、非破坏性（MUST NOT 摧毁方块——仪式结构就围在核心周围，
     * 破坏性爆炸会炸掉自己的外环、立刻把结构打成失配）。
     *
     * <p>力度取自眼形尺寸，故放大后的门体波及范围同比变大。
     *
     * <p>{@code public}：调用点在门体自身（爆炸那一 tick，见 {@code SukimaBlockEntity}），
     * 而击退是<b>玩法</b>不是表现，MUST 由服务端权威执行。
     */
    public static void knockback(ServerLevel level, BlockPos center, double radius, double strength) {
        if (radius <= 0.0D || strength <= 0.0D) {
            return;
        }
        var box = new net.minecraft.world.phys.AABB(
                center.getX() - radius, center.getY() - radius, center.getZ() - radius,
                center.getX() + radius, center.getY() + radius, center.getZ() + radius);
        for (var entity : level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class, box,
                e -> e.isPushable() && !e.isSpectator())) {
            Vec3 away = entity.position().subtract(Vec3.atCenterOf(center));
            if (away.lengthSqr() < 1.0E-4D) {
                away = new Vec3(0.0D, 1.0D, 0.0D);
            }
            Vec3 dir = away.normalize();
            // 距离越近推得越狠，但留一个向上分量以免把人按在地上
            double falloff = 1.0D - Math.min(1.0D, away.length() / radius);
            entity.push(dir.x * strength * falloff,
                    (Math.abs(dir.y) * 0.35D + 0.65D) * strength * falloff,
                    dir.z * strength * falloff);
            entity.hasImpulse = true;
        }
    }

    /**
     * 启动爆发音效。1.21.1 的 {@code Level} 没有带 minVolume 的 9 参重载，故用 8 参形态。
     * 爆发粒子与眼睛动画都由门体自身的动画计时器在客户端驱动，此处只补一次音效广播。
     */
    private static void burstSound(ServerLevel level, double x, double y, double z) {
        level.playSound(null, x, y, z,
                SoundEvents.END_GATEWAY_SPAWN, SoundSource.BLOCKS, 4.0F, 0.35F);
    }

    /** 主世界侧门位：核心正上方 2 格，落在 pattern 自带的裂口笼空气井内。 */
    public static BlockPos mainPortalPos(BlockPos corePos) {
        return corePos.above(PORTAL_UP);
    }

    /**
     * 幻想乡侧孪生门位：维度地表落点 (0, 地表, 0) 外扩 {@link #TWIN_OFFSET} 格的水平偏移。
     * 返回 null 表示幻想乡维度未加载，或该处区块当前<b>未加载</b>。
     *
     * <p>刻意不强制加载：{@code getHeightmapPos} 会触发区块加载，而闩锁后的
     * {@code ensurePortals} 每秒都会问一次孪生门位——若不设闸，无玩家在幻想乡时该区块会
     * 在「加载→卸载→再加载」之间反复抖动。开门那一刻才允许付这次加载代价（单次事件），
     * 之后只在区块本就加载时才复查。
     */
    @Nullable
    public static BlockPos twinPortalPos(ServerLevel overworld) {
        return twinPortalPos(overworld, false);
    }

    @Nullable
    private static BlockPos twinPortalPos(ServerLevel overworld, boolean allowChunkLoad) {
        ServerLevel gensokyo = gensokyoLevel(overworld);
        if (gensokyo == null) {
            return null;
        }
        BlockPos landing = gensokyo.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, BlockPos.ZERO);
        BlockPos probe = new BlockPos(landing.getX() + TWIN_OFFSET, landing.getY(), landing.getZ());
        if (!allowChunkLoad && !gensokyo.isLoaded(probe)) {
            return null;
        }
        return safeSurfaceNear(gensokyo, probe.getX(), probe.getZ());
    }

    /**
     * 方形环外扩的候选偏移序列（世界无关纯函数，可单测）。
     *
     * <p>顺序：先中心 {@code (0,0)}，再按 r=1..maxRadius 逐圈给出方形环。
     * 环上只取 {@code |dx|==r 或 |dz|==r} 的格，避免重复扫描内部。
     *
     * <p><b>为什么必须是纯函数</b>：本方法初版是内联的三重 for 循环，环半径既是循环变量
     * 又是步长（{@code dx += r}），而 r 从 0 起——{@code r == 0} 时步长为 0，内层循环永不
     * 推进。它在服务端主线程的开门路径上被调用，故一旦触发即<b>冻结整个世界</b>（存档亦
     * 无法写出，因为主线程从未返回）。抽出为「先生成有限序列、再消费」的形式后，
     * 「必须终止」成为可断言的性质，由 {@code BarrierOfferingSlotTest} 钉住。
     */
    public static List<int[]> surfaceProbeOffsets(int maxRadius) {
        int rMax = Math.max(0, maxRadius);
        // r 圈环上的格数 = (2r+1)² - (2r-1)² = 8r；加中心一格
        int total = 1 + 4 * rMax * (rMax + 1);
        List<int[]> out = new ArrayList<>(total);
        out.add(new int[]{0, 0});
        for (int r = 1; r <= rMax; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.abs(dx) != r && Math.abs(dz) != r) {
                        continue; // 只走方形环
                    }
                    out.add(new int[]{dx, dz});
                }
            }
        }
        return out;
    }

    /**
     * 在给定列附近找一处可安全立门的地表：脚下实心、头顶两格空气、非流体系、最低高度以上。
     * 找不到则回退到落点正上方（宁可门悬空，也不要门塞进熔岩或深坑）。
     */
    private static BlockPos safeSurfaceNear(ServerLevel level, int x, int z) {
        BlockPos best = new BlockPos(x,
                level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z);
        for (int[] off : surfaceProbeOffsets(TWIN_SEARCH_RADIUS)) {
            int px = x + off[0];
            int pz = z + off[1];
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, px, pz);
            if (y <= level.getMinBuildHeight() + 2) {
                continue;
            }
            BlockPos pos = new BlockPos(px, y, pz);
            if (isSafePortalSpot(level, pos)) {
                return pos;
            }
            if (off[0] == 0 && off[1] == 0) {
                best = pos;
            }
        }
        return best;
    }

    private static boolean isSafePortalSpot(ServerLevel level, BlockPos pos) {
        BlockState below = level.getBlockState(pos.below());
        if (!below.isSolidRender(level, pos.below())) {
            return false;
        }
        return level.getBlockState(pos).isAir()
                && level.getBlockState(pos.above()).isAir()
                && !level.getFluidState(pos).isSource()
                && pos.getY() > level.getMinBuildHeight() + 2;
    }

    @Nullable
    public static ServerLevel gensokyoLevel(ServerLevel anyLevel) {
        if (anyLevel.getServer() == null) {
            return null;
        }
        ResourceLocation id = Gensokyou.id("gensokyo");
        return anyLevel.getServer().getLevel(
                ResourceKey.create(Registries.DIMENSION, id));
    }

    /** 放置主世界侧门（必有）与幻想乡侧孪生门（冲突则跳过）。返回孪生门是否真的放下了。 */
    private static boolean placePortals(ServerLevel level, BlockPos corePos,
                                        RitualCoreBlockEntity core) {
        placeAt(level, mainPortalPos(corePos));
        core.setPortalPos(mainPortalPos(corePos));
        // 开门这一刻允许付一次跨维度区块加载的代价
        BlockPos twin = twinPortalPos(level, true);
        if (twin == null) {
            return false;
        }
        // ⚠️ 孪生门位是**幻想乡**里的坐标（twinPortalPos 内部走 gensokyoLevel），
        // 所以放置与"先到先得"检查都 MUST 落在幻想乡侧。
        // 曾经两处都误用 level（仪式的维度），后果有三条：
        //   ① 孪生门被放进仪式的维度 ⇒ 同一维度出现两扇门，仪式靠近 (0,地表,0) 时
        //      读作"旁边凭空多了一扇同步的门"（实机反馈）；
        //   ② entityInside 把玩家传到**幻想乡**的 (0,地表,0)，那里根本没有门
        //      ⇒ 过不去也回不来，"成对开门好让人原路返回"的意图彻底落空；
        //   ③ 先到先得守卫查错维度，形同虚设。
        ServerLevel gensokyo = gensokyoLevel(level);
        if (gensokyo == null) {
            return false;
        }
        if (gensokyo.getBlockState(twin).is(ModBlocks.SUKIMA.get())) {
            return false; // 先到先得：不覆盖既有孪生门
        }
        placeAt(gensokyo, twin);
        return true;
    }

    /**
     * 闩锁后的每 tick 维护：保证主世界侧门在位（它就在已加载的核心上方，必然可查），
     * 孪生门仅在该区块已加载时复查——见 {@link #twinPortalPos} 的强载说明。
     */
    private static boolean ensurePortals(ServerLevel level, BlockPos corePos,
                                         RitualCoreBlockEntity core) {
        boolean ok = true;
        BlockPos main = core.portalPos() != null ? core.portalPos() : mainPortalPos(corePos);
        if (!level.getBlockState(main).is(ModBlocks.SUKIMA.get())) {
            placeAt(level, main);
            ok = false;
        }
        BlockPos twin = twinPortalPos(level, false);
        // ⚠️ 同 placePortals：孪生门位属于**幻想乡**，放置 MUST 用幻想乡的 level。
        // 这里原本只在"检查"上用了 gensokyoLevel(level)，"放置"却漏了。
        ServerLevel gensokyo = twin == null ? null : gensokyoLevel(level);
        if (gensokyo != null && !gensokyo.getBlockState(twin).is(ModBlocks.SUKIMA.get())) {
            placeAt(gensokyo, twin);
        }
        return ok;
    }

    /**
     * 放置并写入尺寸标量、置为开启态。
     *
     * <p>传入 {@code BARRIER_PORTAL_BURST_TICKS} 作为<b>张眼前置延迟</b>：先播满一段「结界崩解」
     * 演出，再让眼形张开。两个演出各占独立时间，玩家才既看得清爆炸、也看得清「隙间张开」。
     */
    private static void placeAt(ServerLevel level, BlockPos pos) {
        level.setBlock(pos, ModBlocks.SUKIMA.get().defaultBlockState(), 3);
        if (level.getBlockEntity(pos)
                instanceof SukimaBlockEntity portal) {
            portal.requestOpen(GensokyouConfig.BARRIER_PORTAL_SCALE.get().floatValue(),
                    GensokyouConfig.SUKIMA_PORTAL_BURST_TICKS.get());
        }
    }

    /**
     * 关闭两扇门（请求关闭 → 播闭眼动画 → 门自行倒数移除），幂等。
     *
     * <p>主世界侧门位优先取核心记录的 {@code portalPos}；幻想乡侧门位是
     * {@link #twinPortalPos} 的纯函数派生，与开门路径必然一致，无需在核心 BE 上另存一份
     * （也就不会与实际门位漂移）。
     *
     * @param core 可为 null（核心 BE 已随方块移除而消失的路径）
     */
    public static void removePortals(ServerLevel level, @Nullable BlockPos corePos,
                                     @Nullable RitualCoreBlockEntity core) {
        BlockPos main = core != null && core.portalPos() != null
                ? core.portalPos()
                : (corePos != null ? mainPortalPos(corePos) : null);
        if (main != null) {
            requestClose(level, main);
        }
        ServerLevel gensokyo = gensokyoLevel(level);
        if (gensokyo == null) {
            return;
        }
        // 关门必须能找到孪生门，故此处允许那一次跨维度区块加载——门本来就在那，不加载
        // 就等于漏关门，会留下永久孤儿门
        BlockPos twin = twinPortalPos(level, true);
        if (twin != null) {
            requestClose(gensokyo, twin);
        }
    }

    private static void requestClose(ServerLevel level, BlockPos pos) {
        if (!level.getBlockState(pos).is(ModBlocks.SUKIMA.get())) {
            return;
        }
        if (level.getBlockEntity(pos)
                instanceof SukimaBlockEntity portal) {
            portal.requestClose(GensokyouConfig.SUKIMA_PORTAL_OPEN_TICKS.get());
        } else {
            level.removeBlock(pos, false);
        }
    }

    @Override
    public void onStructureLost(ServerLevel level, BlockPos corePos) {
        if (!(level.getBlockEntity(corePos) instanceof RitualCoreBlockEntity core)) {
            return;
        }
        // activeMatch 已被重扫更新：null = 结构暂时拆毁（保留闩锁与门）；非 null = 变成别的仪式。
        if (core.activeMatch() == null) {
            return;
        }
        // 已闩锁 = 「永久开启」。结构重扫是<b>瞬时</b>判定，可能因为方块加载/顺序抖动而短暂不匹配；
        // 若此时拆门，闩锁被清空、门被移除，下一秒供给判定又会把门放回去并把 fxTicks 归零——
        // 崩解演出于是陷入「重置→播 4 帧→再重置」的循环（实测周期约 14s，玩家只看得见一闪）。
        // 永久闩锁不应被瞬时失配撤销：只有核心方块本身被拆除才关门（走 RitualCoreBlock#onRemove）。
        if (core.isBarrierLatched()) {
            return;
        }
        core.setBarrierLatched(false);
        removePortals(level, corePos, core);
    }

    // ------------------------------------------------------------------ 界面

    @Override
    public List<InfoLine> uiInfo(ServerLevel level, BlockPos corePos, RitualMatch match,
                                  RitualCoreBlockEntity core) {
        List<InfoLine> lines = new ArrayList<>();
        Supply supply = core.isBarrierLatched()
                ? Supply.idle() : supply(level, corePos, core);
        long stored = core.getStored();
        long capacity = core.getCapacity();

        // 状态行：供灵不足 MUST 显式表达，否则玩家把进度条倒退误判为缺陷
        State state = stateOf(core, supply);
        String stateKey = KEY_STATE + state.name().toLowerCase(Locale.ROOT);
        int stateColor = switch (state) {
            case OPEN -> COLOR_OK;
            case INSUFFICIENT, NO_NETWORK -> COLOR_BAD;
            default -> COLOR_WARN;
        };
        String[] stateArgs = state == State.INSUFFICIENT
                ? new String[]{InfoLine.compact(supply.deficit())}
                : new String[0];
        lines.add(new InfoLine(stateKey, stateArgs, "", stateColor, -1F, null,
                0, InfoLine.CONTROL_NONE, InfoLine.LINK_NONE, "", new String[0]));

        if (state == State.NO_NETWORK) {
            lines.add(new InfoLine(KEY_NO_NETWORK, new String[0], "", COLOR_BAD, -1F, null,
                    0, InfoLine.CONTROL_NONE, InfoLine.LINK_NONE, "", new String[0]));
        }

        // 蓄能进度条：可见行紧凑、提示行原始值
        float progress = capacity > 0L ? Math.min(1F, (float) stored / (float) capacity) : -1F;
        lines.add(new InfoLine(KEY_SUPPLY,
                new String[]{InfoLine.compact(stored), InfoLine.compact(capacity)},
                "", COLOR_PURPLE, progress, null,
                0, InfoLine.CONTROL_NONE, InfoLine.LINK_NONE, KEY_SUPPLY_TIP,
                new String[]{String.valueOf(stored), String.valueOf(capacity),
                        String.valueOf(supply.bankCount()), String.valueOf(supply.bankOutRate()),
                        String.valueOf(supply.inRate())}));

        // 供灵来源：路由（归元）与槽核分列，玩家一眼看出是哪条不够
        lines.add(new InfoLine(KEY_INTAKE,
                new String[]{InfoLine.compact(supply.bankOutRate()),
                        InfoLine.compact(supply.socketRate())},
                "", supply.insufficient() ? COLOR_BAD : COLOR_PURPLE, -1F, null,
                0, InfoLine.CONTROL_NONE, InfoLine.LINK_NONE, KEY_INTAKE_TIP,
                new String[]{String.valueOf(supply.bankCount()),
                        String.valueOf(supply.bankStored()),
                        String.valueOf(supply.bankOutRate()),
                        String.valueOf(supply.socketStored()),
                        String.valueOf(supply.socketRate())}));

        if (!core.isBarrierLatched()) {
            lines.add(new InfoLine(KEY_DRAIN,
                    new String[]{InfoLine.compact(RitualCoreBlockEntity.barrierDrainPerSecond())},
                    "", COLOR_BAD, -1F, null,
                    0, InfoLine.CONTROL_NONE, InfoLine.LINK_NONE, KEY_DRAIN_TIP,
                    new String[]{String.valueOf(RitualCoreBlockEntity.barrierDrainPerSecond()),
                            String.valueOf(supply.inRate()), String.valueOf(supply.net())}));
            long hint = GensokyouConfig.BARRIER_SUPPLY_HINT.get();
            if (hint > 0L) {
                lines.add(new InfoLine(KEY_HINT, new String[]{InfoLine.compact(hint)},
                        "", COLOR_WARN, -1F, null,
                        0, InfoLine.CONTROL_NONE, InfoLine.LINK_NONE, "", new String[0]));
            }
        }
        if (core.isBarrierLatched() && !hasTwin(level)) {
            lines.add(new InfoLine(KEY_TWIN_SKIPPED, new String[0], "", COLOR_WARN, -1F, null,
                    0, InfoLine.CONTROL_NONE, InfoLine.LINK_NONE, "", new String[0]));
        }
        lines.addAll(offeringRows(level, match));
        lines.addAll(RitualBehavior.defaultUiInfo(level, corePos, match, core));
        return lines;
    }

    /**
     * 祭品核对行：<b>两行带计数</b>（而非 8 行匿名 ✓/✗）。
     *
     * <p>规则无序，故只报「已奉 3/4」——玩家不必知道哪台对应哪行，把 4 颗星银和 4 颗潮汐晶
     * 任意摆进 8 台即可。祭品在开门时被消耗，故提示里写明。
     */
    private static List<InfoLine> offeringRows(ServerLevel level, RitualMatch match) {
        Offerings o = offerings(level, match);
        List<InfoLine> rows = new ArrayList<>(2);
        rows.add(offeringRow(ITEM_STAR_SILVER, o.starSilver(), o.required(),
                o.starSilver() >= o.required()));
        rows.add(offeringRow(ITEM_TIDE_CRYSTAL, o.tideCrystal(), o.required(),
                o.tideCrystal() >= o.required()));
        return rows;
    }

    private static InfoLine offeringRow(String itemId, int have, int need, boolean ok) {
        return new InfoLine(KEY_OFFERING, new String[]{String.valueOf(have), String.valueOf(need)},
                itemId, ok ? COLOR_OK : COLOR_BAD, -1F, ok,
                0, InfoLine.CONTROL_ITEM, InfoLine.LINK_NONE, KEY_OFFERING_TIP,
                new String[]{itemId, String.valueOf(have), String.valueOf(need)});
    }

    private static boolean hasTwin(ServerLevel level) {
        ServerLevel gensokyo = gensokyoLevel(level);
        if (gensokyo == null) {
            return false;
        }
        // 不强载：区块未加载时按「无孪生门」呈现，玩家进幻想乡后自然转为有
        BlockPos twin = twinPortalPos(level, false);
        return twin != null && gensokyo.getBlockState(twin).is(ModBlocks.SUKIMA.get());
    }

    public static State stateOf(RitualCoreBlockEntity core, Supply supply) {
        if (core.isBarrierLatched()) {
            return State.OPEN;
        }
        if (supply.missing()) {
            return State.NO_NETWORK;
        }
        if (supply.insufficient()) {
            return State.INSUFFICIENT;
        }
        return core.getStored() >= core.getCapacity() ? State.AWAITING : State.CHARGING;
    }

    // ------------------------------------------------------------------ 调试

    /**
     * 祭品转储：因规则无序，逐台位明细只作参考，判据是末尾的计数行。
     *
     * <p>存在的理由：{@code star_silver} / {@code star_silver_ore} / {@code rough_star_silver_ore}
     * 三者 id 相近、只认精确匹配，放错时界面上只显示「星银 2/4」而看不出是哪台放了矿石。
     */
    public static List<String> debugOfferings(ServerLevel level, RitualMatch match,
                                               RitualCoreBlockEntity core) {
        List<String> rows = new ArrayList<>();
        for (BlockPos pos : match.positionsOf('P')) {
            BlockPos rel = pos.subtract(core.getBlockPos());
            int r = (int) Math.round(Math.sqrt(rel.getX() * rel.getX() + rel.getZ() * rel.getZ()));
            String held = "MISSING";
            if (level.getBlockEntity(pos) instanceof RitualPedestalBlockEntity pedestal
                    && !pedestal.getHeld().isEmpty()) {
                held = BuiltInRegistries.ITEM.getKey(pedestal.getHeld().getItem()).toString();
            }
            rows.add("pedestal at=" + rel.getX() + "," + rel.getZ() + " r=" + r
                    + " held=" + held);
        }
        Offerings o = offerings(level, match);
        rows.add("offering star_silver=" + o.starSilver() + "/" + o.required()
                + " tide_crystal=" + o.tideCrystal() + "/" + o.required()
                + " total=" + o.total() + "/" + (match.positionsOf('P').size())
                + " satisfied=" + o.satisfied());
        return rows;
    }

    public static String debugSummary(ServerLevel level, BlockPos corePos, RitualMatch match,
                                      RitualCoreBlockEntity core) {
        Supply s = supply(level, corePos, core);
        BlockPos main = core.portalPos() != null ? core.portalPos() : mainPortalPos(corePos);
        return "state=" + stateOf(core, s)
                + " stored=" + core.getStored()
                + " cap=" + core.getCapacity()
                + " drainPerSec=" + RitualCoreBlockEntity.barrierDrainPerSecond()
                + " bank=" + s.bankCount()
                + " bankStored=" + s.bankStored()
                + " bankOut=" + s.bankOutRate()
                + " socketStored=" + s.socketStored()
                + " socketOut=" + s.socketRate()
                + " inRate=" + s.inRate()
                + " net=" + s.net()
                + " latch=" + core.isBarrierLatched()
                + " portal=" + (level.getBlockState(main).is(ModBlocks.SUKIMA.get()) ? "yes" : "no")
                + " twin=" + (hasTwin(level) ? "yes" : "no");
    }
}
