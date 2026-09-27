package com.bitsson.gensokyou.block.entity;

import com.bitsson.gensokyou.config.GensokyouConfig;
import com.bitsson.gensokyou.ritual.behavior.BarrierBreakBehavior;
import com.bitsson.gensokyou.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 隙间方块实体：承载传送门的**实例级**呈现状态——尺寸标量、开合动画进度、以及
 * 「结界崩解」一次性演出的<b>绝对锚点</b>。
 *
 * <p>字段的分工：
 * <ul>
 *   <li>{@code scale}——眼形几何的乘数，默认 1.0 即保持既有尺寸。逐实例而非全局常量，
 *       以便同一批 {@code sukima} 方块被不同仪式按不同尺寸复用。</li>
 *   <li>{@code openTicks}——自张眼开始起经过的 tick，客户端据此插值出 0..1 的开合进度。</li>
 *   <li>{@code closingTicks}——&gt; 0 表示已进入关门流程，倒数归零后由本实体自删方块。
 *       关门是「请求 → 播动画 → 自删」而非调用方立即 removeBlock，好让核心被破坏 /
 *       变成别的仪式时能优雅地闭眼。</li>
 *   <li>{@code fxStartGameTime}——演出开始的<b>绝对</b> gameTime 锚点，见下。</li>
 * </ul>
 *
 * <p><b>为什么演出一个 int 就够。</b>演出的阶段边界（蓄能结束 = 爆炸）由
 * {@code fxChargeEnd} 给出，而推进进度由 {@code elapsed = gameTime − fxStartGameTime}
 * 在<b>两侧各自</b>算出来。这解决了两个历史故障：
 * <ul>
 *   <li><b>逐 tick 同步进度</b>：开门一次曾要 200 个方块更新包，且客户端必须逐个收到
 *       0..200 才会播完——丢一个包就永久卡死。现在只有 1 个包。</li>
 *   <li><b>一次性演出 + 持久方块实体</b>：早前用「客户端见到新序列号就起本地钟」，
 *       而序列号是 {@code transient} 的——BE 对象在区块卸载后重建，字段回到初值，
 *       于是玩家走开再回来会把整段演出<b>完整重播</b>。绝对锚点存在持久化 NBT 里，
 *       重进世界算出来就是"早过了"，天然不重播；迟到者也直接落在正确相位。</li>
 * </ul>
 *
 * <p>同步走<b>自有</b> update tag，不复用 {@code RitualCoreBlockEntity} 的渲染态通道
 * （后者只写 {@code Rendu} 一个键）。
 */
public class SukimaBlockEntity extends BlockEntity {

    private static final String TAG_SCALE = "Scale";
    private static final String TAG_OPEN_TICKS = "OpenTicks";
    private static final String TAG_CLOSING_TICKS = "ClosingTicks";
    private static final String TAG_FX_START = "FxStart";
    private static final String TAG_FX_CHARGE_END = "FxChargeEnd";
    /**
     * 旧键名（2026-09-27 之前）：{@code FxChargeEnd} 的前身，语义完全相同。
     *
     * <p>读它是为了让<b>已有存档里已闩锁的门</b>不炸：旧档没有 {@code FxStart}，若不迁移
     * 就会得到「锚点 = -1、蓄能长度 = 1」的组合——那会让递进音效的四个相对节点全部塌成
     * tick 0，变成<b>每 tick 叠 4 声</b>的永久噪音，同时 {@code openTicks} 永远不涨。
     */
    private static final String TAG_LEGACY_FX_CHARGE_END = "OpenDelay";

    /** 尺寸标量；1.0 = 默认眼形（半宽 0.5 / 上盖 0.85 / 下弧 1.15 / 总高 2 格）。 */
    private float scale = 1.0F;

    /**
     * 客户端专用：常驻绿十字星的<b>帧率无关</b>发射累加器（存量）与上次时间戳。
     *
     * <p><b>刻意放在 BE 上而不是静态 Map。</b>原实现是
     * {@code static WeakHashMap<BlockPos, double[]>}，那有两个坑：
     * ① {@code BlockPos} 作键只被弱引用持有，条目随时可能被 GC 静默丢弃，
     * 于是 {@code elapsed} 一次性取到被 clamp 过的 2 秒 → <b>爆出四十多颗绿星挤成一团</b>
     * （实机症状：绿十字星突然在别处成堆出现）；② 静态状态在服务端 BE 上也会跟着建，
     * 纯属浪费。每块门各自持有自己的累加器，天然没有这两问题。
     *
     * <p>两个字段都 {@code transient}：不写 NBT、不参与同步、客户端专用。
     */
    private transient double moteCarry;
    private transient double moteLastTime = Double.NaN;
    private transient double inflowCarry;
    private transient double inflowLastTime = Double.NaN;

    /** 自张眼开始起经过的 tick（已饱和于 openDuration）。 */
    private int openTicks;

    /** &gt; 0 = 关门流程剩余 tick；归零时自删方块。 */
    private int closingTicks;

    /**
     * 演出起始 gameTime（绝对锚点）。&lt; 0 = 从未播放过演出。
     *
     * <p><b>持久化</b>：这正是不重播的关键——方块实体是持久的，锚点活过区块卸载与重连。
     * 早期用客户端瞬时标记判重播，那东西在 BE 重建后就没了。
     */
    private int fxStartGameTime = -1;

    /**
     * 蓄能段长度（tick），即<b>爆炸</b>发生在锚点后的第几 tick。
     *
     * <p>它是<b>请求时的不可变值</b>，必须随 tag 同步，让客户端与服务端拿到同一个数字。
     * 曾经的失败：把它换成一个每 tick 递减的倒计时并让两侧各自读配置，结果两扇门拿到
     * 不同的值（一扇 60、一扇 1），分拍边界永久漂移。单一事实源 = 服务端下发的这个 int。
     */
    private int fxChargeEnd = 1;

    /** 上一次广播给客户端的三个可变状态，用于只在它们变化时发包。 */
    private float lastSentScale = Float.NaN;
    private int lastSentOpenTicks = -1;
    private int lastSentClosingTicks = -1;
    /** 上一次广播的演出锚点：旧档迁移会在服务端补上它，需要推一次让两侧收敛。 */
    private int lastSentFxStart = Integer.MIN_VALUE;

    /**
     * 蓄能段里放提示音的相对节点。⚠️ MUST 是<b>若干固定节点</b>，MUST NOT 是「每 tick 触发」：
     * 后者在 60 fps 渲染 20 tick 时钟的 BER 里会退化成按帧闪烁（见
     * {@code docs/barrier-shatter-fx-postmortem.md} §10.3）。
     */
    private static final float[] CHARGE_CUE_FRACTIONS = {0.1F, 0.3F, 0.5F, 0.7F};

    public SukimaBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SUKIMA.get(), pos, state);
    }

    // ---------------------------------------------------------------- 状态访问

    public float scale() {
        return scale <= 0.0F ? 1.0F : scale;
    }

    public int openTicks() {
        return openTicks;
    }

    public int closingTicks() {
        return closingTicks;
    }

    // ---------------------------------------------------------------- 客户端常驻粒子累加器

    /**
     * 取本帧的常驻绿十字星发射预算，并把余量记回本 BE。
     *
     * <p>按「单调目标数 − 已达数」推进，故与帧率无关（60fps 与 144fps 每秒发出的颗数相同），
     * 且不依赖任何逐 tick 状态。
     *
     * @param perSec 每秒目标颗数（调用方已乘开合进度）
     * @param now    当前游戏时间（含小数 partial tick）
     * @return 本帧应发射的颗数；0 表示不用发射
     */
    public int takeMoteBudget(int perSec, double now) {
        if (perSec <= 0) {
            return 0;
        }
        // 首帧 / 长时间不可见后回来：clamp 到 0.25s，避免一次性补发一大团
        double elapsed = Double.isNaN(moteLastTime) ? 0.0D : Mth.clamp(now - moteLastTime, 0.0D, 0.25D);
        moteLastTime = now;
        moteCarry += perSec * elapsed / 20.0D;
        int budget = (int) moteCarry;
        if (budget <= 0) {
            return 0;
        }
        moteCarry -= budget;
        return budget;
    }

    /**
     * 蓄能段"向内吸入粒子流"的发射预算。
     *
     * <p>与常驻绿星<b>各用各的存量</b>（{@code inflow*} 字段），互不干扰——
     * 共用一个累加器会让两者的开关互相污染：关掉绿星时吸入流也会跟着停。
     */
    public int takeInflowBudget(int perSec, double now) {
        if (perSec <= 0) {
            return 0;
        }
        double elapsed = Double.isNaN(inflowLastTime) ? 0.0D : Mth.clamp(now - inflowLastTime, 0.0D, 0.25D);
        inflowLastTime = now;
        inflowCarry += perSec * elapsed / 20.0D;
        int budget = (int) inflowCarry;
        if (budget <= 0) {
            return 0;
        }
        inflowCarry -= budget;
        return budget;
    }

    public boolean isClosing() {
        return closingTicks > 0;
    }

    /** 开启进度 0..1（客户端插值用；服务端同样可读）。 */
    public float openProgress(int durationTicks) {
        int d = Math.max(1, durationTicks);
        return Math.min(1F, (float) openTicks / (float) d);
    }

    /** 蓄能段长度（tick）：爆炸发生在锚点后的这一 tick。 */
    public int fxChargeEnd() {
        return Math.max(1, fxChargeEnd);
    }

    /** 演出锚点（绝对 gameTime）；&lt; 0 = 从未播放过演出。 */
    public int fxStartGameTime() {
        return fxStartGameTime;
    }

    /** 烟环窗口长度（tick）：爆炸之后的散射持续多久。 */
    public int fxBurstTicks() {
        return Math.max(1, GensokyouConfig.FX_SHATTER_BURST_TICKS.get());
    }

    /** 演出总长度（tick）= 蓄能 + 烟环；派生值，MUST NOT 硬编码。 */
    public int fxTotalTicks() {
        return fxChargeEnd() + fxBurstTicks();
    }

    /**
     * 演出已进行的 tick（服务端与客户端同口径）。
     *
     * <p>两侧都用 {@code level.getGameTime()}：客户端的 {@code ClientLevel#getGameTime}
     * 是服务端下发并推进的本地副本，与服务端相差至多一个 tick——对几十 tick 长的
     * 烟窗而言不可见。
     *
     * <p>从未播放过演出（{@code fxStartGameTime < 0}）时恒返回 0。
     */
    public int fxElapsed() {
        if (fxStartGameTime < 0 || level == null) {
            return 0;
        }
        return Math.max(0, (int) level.getGameTime() - fxStartGameTime);
    }

    /** 是否正处于「蓄能」段（爆炸之前）。 */
    public boolean fxCharging() {
        int t = fxElapsed();
        return fxStartGameTime >= 0 && t < fxChargeEnd();
    }

    /** 是否正处于「爆炸 + 烟环」段。 */
    public boolean fxBursting() {
        int t = fxElapsed();
        return fxStartGameTime >= 0 && t >= fxChargeEnd() && t < fxTotalTicks();
    }

    /** 蓄能进度 0..1。 */
    public float fxChargeProgress() {
        return Mth.clamp((float) fxElapsed() / fxChargeEnd(), 0F, 1F);
    }

    // ---------------------------------------------------------------- 状态迁移

    /**
     * 请求开启：写入尺寸，落下演出锚点，从 0 起算开合动画（若已在关门则撤销关门）。
     *
     * @param delayTicks 蓄能段 tick 数；期间眼形保持闭合，只播演出
     */
    public void requestOpen(float newScale, int delayTicks) {
        this.scale = newScale <= 0.0F ? 1.0F : newScale;
        this.openTicks = 0;
        this.closingTicks = 0;
        this.fxChargeEnd = Math.max(1, delayTicks);
        this.fxStartGameTime = level != null ? (int) level.getGameTime() : 0;
        setChanged();
        // 推一次：锚点与尺寸标量随之到达客户端，其后全靠本地时钟推进。
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }
    }

    /**
     * 请求关闭：进入关门流程，由本实体倒数完毕后自删。
     *
     * <p>时长由调用方按配置传入——<b>不能</b>用「首次 tick 再定」的哨兵值：那会让客户端在
     * 收到第一帧时读到占位值，闭眼进度算出来是负数、眼睛一帧瞬闭（没有过渡动画）。
     *
     * @param durationTicks 收拢全程时长
     */
    public void requestClose(int durationTicks) {
        int d = Math.max(1, durationTicks);
        if (closingTicks > 0 && closingTicks <= d) {
            return; // 已在关门且不延长
        }
        this.closingTicks = d;
        setChanged();
    }

    // ---------------------------------------------------------------- 演出编排（服务端）

    /**
     * 服务端每 tick 推进开合进度、在爆炸当刻触发音效与击退，并在关门流程结束时自删方块。
     *
     * <p>演出进度<b>不</b>由本方法推进：它由 {@link #fxElapsed()} 从锚点自算，
     * 因此这里不需要（也不该）把它放进 {@link #syncIfChanged()} 的差分里。
     *
     * @param durationTicks 开合全程时长（配置）
     * @return true 表示本 tick 发生了需要广播的状态推进
     */
    public boolean serverTick(int durationTicks) {
        boolean changed = false;
        // ---- 旧档迁移：补一个"演出早已结束"的锚点 ----
        // 旧存档里已闩锁的门没有 FxStart。若不补，fxElapsed() 恒为 0 而蓄能长度可能是 1，
        // 于是 (a) 递进音效的四个相对节点全塌成 tick 0 → 每 tick 叠 4 声永久噪音，
        //     (b) openTicks 永不增长 → 那扇门永远闭着。
        // 这里把锚点定在"整段演出早已过去"，效果是：眼立刻张开、演出不重播。
        if (level != null && !level.isClientSide && fxStartGameTime < 0) {
            this.fxChargeEnd = Math.max(1, GensokyouConfig.SUKIMA_PORTAL_BURST_TICKS.get());
            this.fxStartGameTime = (int) level.getGameTime() - this.fxChargeEnd - fxBurstTicks();
            changed = true;
        }
        if (closingTicks > 0) {
            closingTicks--;
            changed = true;
            if (closingTicks <= 0) {
                closingTicks = 0;
                if (level != null && !level.isClientSide) {
                    level.removeBlock(getBlockPos(), false);
                }
                return true;
            }
        } else {
            // 蓄能段内眼形保持闭合，玩家先看完崩解才看到眼睁开。
            // ⚠️ 条件方向别搞反：蓄能<b>期间</b> openTicks 必须停在 0，
            // 蓄能<b>耗尽后</b>才开始爬升。
            if (fxElapsed() >= fxChargeEnd() && openTicks < durationTicks) {
                openTicks++;
                changed = true;
            }
            // 音效与击退每 tick 自行按 elapsed 门控（只在少数几个 tick 上真正触发）。
            playShatterCues();
        }
        if (changed) {
            setChanged();
        }
        return changed;
    }

    /**
     * 服务端只负责「何时到哪一拍」的音效与击退；粒子编排（蓝白光球 + 径向光柱 + 烟环）
     * <b>在客户端产生</b>，见 {@code SukimaPortalRenderer#emitShatterFx}。
     *
     * <p>击退是<b>玩法</b>不是表现，MUST 由服务端权威执行；它落在爆炸那一 tick，
     * 而不是仪式开启之初。半径与强度沿用既有取值，半径不随烟环的 15 格而放大
     * （那会把玩家从祭坛上掀飞，且与既有调好的手感冲突）。
     */
    private void playShatterCues() {
        if (level == null || !(level instanceof ServerLevel server)) {
            return;
        }
        int t = fxElapsed();
        // 注意：<b>没有</b> "t == 0" 的起手音——门被放置的那一 tick 由
        // BarrierBreakBehavior 播 END_GATEWAY_SPAWN 承担，而本实体的 serverTick 最早在
        // 下一 tick 才跑，届时 elapsed 已经 ≥ 1，那个分支永远不命中。
        // 递进提示音改为若干<b>固定相对节点</b>：⚠️ MUST NOT 写成"每 tick 触发"，
        // 那会在 60fps 渲染 20tick 时钟的 BER 里退化成按帧闪烁
        // （见 docs/barrier-shatter-fx-postmortem.md §10.3）。
        for (int node : chargeCueNodes(fxChargeEnd())) {
            if (t == node) {
                server.playSound(null, getBlockPos(), SoundEvents.AMETHYST_BLOCK_RESONATE,
                        SoundSource.BLOCKS, 0.7F, 1.0F + node / (float) Math.max(1, fxChargeEnd()));
            }
        }
        if (t == fxChargeEnd()) {
            // 爆炸当刻：低频轰鸣 + 震荡 + 碎裂，三层叠出体量。
            server.playSound(null, getBlockPos(), SoundEvents.GENERIC_EXPLODE.value(),
                    SoundSource.BLOCKS, 1.6F, 0.7F);
            server.playSound(null, getBlockPos(), SoundEvents.WARDEN_SONIC_BOOM,
                    SoundSource.BLOCKS, 1.0F, 0.85F);
            server.playSound(null, getBlockPos(), SoundEvents.AMETHYST_CLUSTER_BREAK,
                    SoundSource.BLOCKS, 1.0F, 0.70F);
            BarrierBreakBehavior.knockback(server, getBlockPos().above(2),
                    BarrierBreakBehavior.burstKnockbackRadius(scale()),
                    BarrierBreakBehavior.BURST_KNOCKBACK_STRENGTH);
        }
    }

    /**
     * 递进提示音的触发 tick（升序、去重）。
     *
     * <p><b>节点 MUST NOT 塌到 0。</b>蓄能长度极小（旧档遗留、配置写坏）时，
     * {@code (int)(chargeEnd * fraction)} 会四舍五入成 0；而锚点缺失时
     * {@code fxElapsed()} 恒为 0——两者相等就变成<b>每 tick 叠 N 声</b>的永久噪音。
     * {@code max(1, ·)} + 去重把这条路彻底封死。
     */
    static int[] chargeCueNodes(int chargeEnd) {
        int end = Math.max(1, chargeEnd);
        int[] raw = new int[CHARGE_CUE_FRACTIONS.length];
        for (int i = 0; i < CHARGE_CUE_FRACTIONS.length; i++) {
            raw[i] = Math.max(1, Math.min(end, (int) (end * CHARGE_CUE_FRACTIONS[i])));
        }
        java.util.Arrays.sort(raw);
        // 去重：蓄能段很短时多个比例会落到同一 tick，重复节点 = 同一 tick 叠多声
        int unique = 0;
        for (int node : raw) {
            if (unique == 0 || node != raw[unique - 1]) {
                raw[unique++] = node;
            }
        }
        return java.util.Arrays.copyOf(raw, unique);
    }

    // ---------------------------------------------------------------- 同步

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putFloat(TAG_SCALE, scale);
        tag.putInt(TAG_OPEN_TICKS, openTicks);
        tag.putInt(TAG_CLOSING_TICKS, closingTicks);
        tag.putInt(TAG_FX_START, fxStartGameTime);
        tag.putInt(TAG_FX_CHARGE_END, fxChargeEnd);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.handleUpdateTag(tag, registries);
        float s = tag.getFloat(TAG_SCALE);
        if (s > 0.0F) {
            scale = s;
        }
        openTicks = tag.getInt(TAG_OPEN_TICKS);
        closingTicks = tag.getInt(TAG_CLOSING_TICKS);
        readFxTag(tag);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /**
     * 演出锚点与分拍边界的读取。
     *
     * <p><b>抽出成方法，因为两条同步路径都必须读到它们</b>：客户端的方块实体是由
     * {@code loadAdditional}（区块 NBT）创建的，而不是一上来就拿到 update tag。
     * 两条路径只读一条 → 客户端值永远停在默认值（历史故障：只读一条导致
     * {@code chargeEnd} 被抵到 1，整个蓄能段被跳过）。
     */
    private void readFxTag(CompoundTag tag) {
        fxStartGameTime = tag.contains(TAG_FX_START) ? tag.getInt(TAG_FX_START) : -1;
        int end = tag.contains(TAG_FX_CHARGE_END) ? tag.getInt(TAG_FX_CHARGE_END)
                : tag.getInt(TAG_LEGACY_FX_CHARGE_END);
        fxChargeEnd = Math.max(1, end);
    }

    /** 仅在状态实际变化时广播，避免每 tick 发包。 */
    public void syncIfChanged() {
        if (level == null || level.isClientSide) {
            return;
        }
        // 演出进度不在差分里：客户端用锚点自算。否则每次开门要发上百个包。
        // 锚点本身要进差分：旧档迁移会在服务端补上它，不推一次两侧就长期不一致。
        if (scale == lastSentScale && openTicks == lastSentOpenTicks
                && closingTicks == lastSentClosingTicks
                && fxStartGameTime == lastSentFxStart) {
            return;
        }
        lastSentScale = scale;
        lastSentOpenTicks = openTicks;
        lastSentClosingTicks = closingTicks;
        lastSentFxStart = fxStartGameTime;
        level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
    }

    // ---------------------------------------------------------------- 持久化

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putFloat(TAG_SCALE, scale);
        tag.putInt(TAG_OPEN_TICKS, openTicks);
        tag.putInt(TAG_CLOSING_TICKS, closingTicks);
        tag.putInt(TAG_FX_START, fxStartGameTime);
        tag.putInt(TAG_FX_CHARGE_END, fxChargeEnd);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        float s = tag.getFloat(TAG_SCALE);
        scale = s > 0.0F ? s : 1.0F;
        openTicks = Math.max(0, tag.getInt(TAG_OPEN_TICKS));
        closingTicks = Math.max(0, tag.getInt(TAG_CLOSING_TICKS));
        readFxTag(tag);
    }
}
