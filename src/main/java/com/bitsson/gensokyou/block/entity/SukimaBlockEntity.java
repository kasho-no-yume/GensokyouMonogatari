package com.bitsson.gensokyou.block.entity;

import com.bitsson.gensokyou.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;

/**
 * 隙间方块实体：承载传送门的**实例级**呈现状态——尺寸标量与开合动画进度。
 *
 * <p>三个字段的分工：
 * <ul>
 *   <li>{@code scale}——眼形几何的乘数，默认 1.0 即保持既有尺寸。逐实例而非全局常量，
 *       以便同一批 {@code sukima} 方块被不同仪式按不同尺寸复用。</li>
 *   <li>{@code openTicks}——自张眼开始起经过的 tick，客户端据此插值出 0..1 的开合进度
 *       并<b>同时</b>驱动紫色爆发粒子。爆发因此不需要任何独立网络包。</li>
 *   <li>{@code closingTicks}——&gt; 0 表示已进入关门流程，倒数归零后由本实体自删方块。
 *       关门是「请求 → 播动画 → 自删」而非调用方立即 removeBlock，好让核心被破坏 /
 *       变成别的仪式时能优雅地闭眼。</li>
 * </ul>
 *
 * <p>同步走<b>自有</b> update tag，不复用 {@code RitualCoreBlockEntity} 的渲染态通道
 * （后者只写 {@code Rendu} 一个键）。
 */
public class SukimaBlockEntity extends BlockEntity {

    private static final String TAG_SCALE = "Scale";
    private static final String TAG_OPEN_TICKS = "OpenTicks";
    private static final String TAG_CLOSING_TICKS = "ClosingTicks";
    private static final String TAG_FX_TICKS = "FxTicks";
    private static final String TAG_OPEN_DELAY = "OpenDelay";
    private static final String TAG_FX_SEQ = "FxSeq";

    /** 尺寸标量；1.0 = 默认眼形（半宽 0.5 / 上盖 0.85 / 下弧 1.15 / 总高 2 格）。 */
    private float scale = 1.0F;

    /** 自张眼开始起经过的 tick（已饱和于 openDuration）。 */
    private int openTicks;

    /** &gt; 0 = 关门流程剩余 tick；归零时自删方块。 */
    private int closingTicks;

    /** 上一次广播给客户端的状态，用于只在变化时发包。 */
    private float lastSentScale = Float.NaN;
    private int lastSentOpenTicks = -1;
    private int lastSentClosingTicks = -1;
    private int lastSentFxTicks = -1;

    // ---- 仅服务端的演出时序（不持久化、不广播） ----

    /**
     * 张眼前置延迟：&gt; 0 时眼形保持闭合，只播「结界崩解」演出。
     *
     * <p>存在理由是<b>观感</b>而非功能：演出与张眼若从同一 tick 起算，玩家只看到一团光里
     * 隐约裂开一条缝，两个演出互相盖住，谁也看不清。拆成「先崩解后张眼」后各占一段独立时间。
     *
     * <p>不参与同步：延迟期间 {@code openTicks} 恒为 0，客户端据此自然停在闭眼态，
     * 无需额外字段。服务端重启导致本值丢失时，表现为「直接张开」，可接受。
     */
    private int openDelay;

    /**
     * 蓄能段的<b>不可变</b>结束 tick（即请求时的延迟值）。
     *
     * <p>{@link #openDelay} 是每 tick 递减的倒计时，<b>不能当作分拍边界用</b>：
     * 客户端拿到的是不同 tick 的不同剩余值，分拍边界会随之漂移；
     * 声音卡点拿递减值去比较则永远对不上。
     */
    private int fxChargeEnd;

    /**
     * 演出已播 tick；与 {@link #openTicks} 分离以错开两个演出。
     *
     * <p><b>会同步到客户端</b>：整套「结界崩解」编排（白光球膨胀 + 四周白光柱）
     * 改由渲染器在客户端本地产生。若放在服务端，每个粒子都需一个
     * 包、弧度上数千粒就是数千个包/tick；放客户端则零往返。
     */
    private int fxTicks;

    /**
     * 演出序列号。每次 {@code requestOpen} 自增一，客户端看到新值就开始用本地时钟跑一遍。
     *
     * <p>演出是<b>纯视觉</b>的，不该用逐 tick 同步驱动：开门一次就是 200 个方块更新包，
     * 且客户端必须逐个收到 0..200 才会播完——丢一个包就永久卡死
     * （实测：服务端每 tick 发到 200，客户端却卡在 fxTicks=0）。
     */
    private int fxSeq;

    /** 客户端双次记录：最后见过的序列号与本地起点时间。 */
    private transient int lastSeenFxSeq = -1;
    private transient int fxLocalStart = -1;

    /** 演出时钟（客户端编排用）。 */
    public int fxTicks() {
        if (level == null || !level.isClientSide) {
            return fxTicks;
        }
        // 客户端：看到新序列号就用自己的游戏时钟开始跑，不依赖逐 tick 同步。
        if (lastSeenFxSeq != fxSeq) {
            lastSeenFxSeq = fxSeq;
            fxLocalStart = (int) level.getGameTime();
        }
        if (fxLocalStart < 0) {
            return 0;
        }
        return Math.max(0, (int) level.getGameTime() - fxLocalStart);
    }

    /** 张眼前置延迟，即「蓄能段」的结束 tick（客户端编排用）。 */
    public int openDelayTicks() {
        return Math.max(1, fxChargeEnd);
    }

    /** 客户端已发送到哪一个演出 tick。 */
    public int lastEmittedFxTick() {
        return lastEmittedFx;
    }

    public void setLastEmittedFxTick(int t) {
        this.lastEmittedFx = t;
    }

    public int fxReplayTick() {
        return fxReplayTick;
    }

    public void setFxReplayTick(int tick) {
        this.fxReplayTick = tick;
    }

    public boolean isFxPlayed() {
        return fxPlayed;
    }

    public void markFxPlayed() {
        this.fxPlayed = true;
    }

    /**
     * 客户端已发送到的演出 tick。
     *
     * <p>BER 的 {@code render()} <b>每帧</b>调用，而演出密度必须是「每 tick」。
     * 若不区分，144fps 下会发出 2.4 倍粒子。故记住已发 tick，只在它变化时
     * 按差值补发。不持久化：重进区域时重放一遍演出无害。
     */
    private transient int lastEmittedFx;

    /**
     * 本客户端是否已经播过这段演出。
     *
     * <p>演出总长有限，而方块实体是<b>持久化</b>的。强设是长期封闭的
     * （门一旦开吧就不再重触发）——因此客户端可能<b>整段都跳过</b>：
     * 进服时接收到的 {@code fxTicks} 已经饱和。若不补播，这些玩家就永远看不到崩解演出。
     */
    private transient boolean fxPlayed;

    /**
     * 补播进度。客户端补播演出时每帧自行 +1，到 {@code SHATTER_TOTAL} 后停止并置 {@code fxPlayed}。
     * 为 -1 表示尚未开始补播。
     *
     * <p>以前的实现在单帧内循环画完整段，结果只是一帧闪光就结束了——
     * 渲染本身没问题，是“把整段压进一帧”这个实现错了。
     */
    private transient int fxReplayTick = -1;

    // ---- 仅客户端的渲染态（不持久化、不广播） ----

    /**
     * 环境微粒的「已发序号」。发射节奏以「单调目标数 − 本值」推进，故与帧率无关；
     * 只在客户端有意义，服务端恒为 0。
     */
    private transient int moteIndex;

    /** 渲染用随机源，按门位派生，保证同一扇门每帧的抖动序列稳定。 */
    private transient RandomSource renderRandom;

    /** 爆发是否已播完（仅客户端渲染态；开合结束后停止逐帧判定爆发区间）。 */
    private transient boolean burstDone;

    public int moteIndex() {
        return moteIndex;
    }

    public void setMoteIndex(int index) {
        this.moteIndex = Math.max(0, index);
    }

    public RandomSource renderRandom() {
        if (renderRandom == null) {
            renderRandom = RandomSource.create((long) getBlockPos().asLong() * 341873128712L + 1L);
        }
        return renderRandom;
    }

    /** 爆发是否已播完；用于避免开合结束后仍逐帧判定爆发区间。 */
    public boolean isBurstDone() {
        return burstDone;
    }

    public void markBurstDone() {
        this.burstDone = true;
    }

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

    public boolean isClosing() {
        return closingTicks > 0;
    }

    /** 开启进度 0..1（客户端插值用；服务端同样可读）。 */
    public float openProgress(int durationTicks) {
        int d = Math.max(1, durationTicks);
        return Math.min(1F, (float) openTicks / (float) d);
    }

    // ---------------------------------------------------------------- 状态迁移

    /**
     * 请求开启：写入尺寸，从 0 起算开合动画（若已在关门则撤销关门）。
     *
     * @param delayTicks 张眼前置演出 tick 数；期间眼形保持闭合，只播演出
     */
    public void requestOpen(float newScale, int delayTicks) {
        this.scale = newScale <= 0.0F ? 1.0F : newScale;
        this.openTicks = 0;
        this.closingTicks = 0;
        this.openDelay = Math.max(0, delayTicks);
        this.fxChargeEnd = Math.max(1, delayTicks);
        this.fxTicks = 0;
        this.fxSeq++;
        this.burstDone = false;
        this.moteIndex = 0;
        setChanged();
        // 只推一次：客户端拿到新序列号就自己走时钟。
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

    // ---------------------------------------------------------------- 演出编排

    /** 「结界崩解」演出总时长（tick）。 */
    private static final int SHATTER_FX_TICKS = 200;

    /** 崩解段（第二拍）长度。 */
    private static final int SHATTER_SHOCK_TICKS = 55;

    /** 崩解段内每隔多少 tick 放一道水平冲击环。 */
    private static final int SHATTER_RING_STEP = 3;

    /** 崩解段内每隔多少 tick 放一组侧向光束。 */
    private static final int SHATTER_BEAM_STEP = 5;

    /** 余波段长度。 */
    private static final int SHATTER_EMBER_TICKS = 60;

    /**
     * 崩解壳层最大半径（按门尺寸缩放）。壳层是<b>压扁</b>的（纵向 0.62），
     * 因为门是竖高而非正方，正球的观感会浪费掉大半画面。
     */
    private static final double SHATTER_RADIUS_PER_SCALE = 2.6D;
    private static final double SHATTER_FLATTEN = 0.62D;

    /** 蓄能段每隔多少 tick 在核心闪一次。 */
    private static final int SHATTER_CORE_STEP = 8;

    /** 斐波那契球取点数；相邻取点角距均匀，够密即可。 */
    private static final int FIB_COUNT = 256;

    /** 黄金角（度）——用它在球面上取点。 */
    private static final double GOLDEN_ANGLE = 137.50776405003785D;

    /**
     * 调色板。全部走 {@code DUST}（可指定颜色与尺寸），因此观感与本模组的紫调一致，
     * <b>不借用任何 vanilla 演出资源</b>。
     */
    private static final DustParticleOptions FX_CORE =
            new DustParticleOptions(new Vector3f(0.95F, 0.92F, 1.00F), 3.0F);
    private static final DustParticleOptions FX_CHARGE =
            new DustParticleOptions(new Vector3f(0.66F, 0.34F, 0.98F), 1.5F);
    private static final DustParticleOptions FX_SHELL =
            new DustParticleOptions(new Vector3f(0.58F, 0.26F, 0.95F), 2.4F);
    private static final DustParticleOptions FX_BEAM =
            new DustParticleOptions(new Vector3f(0.80F, 0.62F, 1.00F), 1.1F);
    private static final DustParticleOptions FX_EMBER =
            new DustParticleOptions(new Vector3f(0.40F, 0.15F, 0.68F), 1.0F);

    /**
     * 服务端每 tick 推进动画状态，播「结界崩解」演出，并在关门流程结束时自删方块。
     *
     * @param durationTicks 开合全程时长（配置）
     * @return true 表示本 tick 发生了需要广播的状态推进
     */
    public boolean serverTick(int durationTicks) {
        boolean changed = false;
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
            // 演出与张眼分属两条时钟：演出从开门请求那刻就走完整个三拍，
            // 而 openTicks 要等 openDelay 耗尽才开始——玩家先看完崩解才看到眼睁开。
            if (fxTicks < SHATTER_FX_TICKS) {
                playShatterCues(fxChargeEnd);
                fxTicks++;
                changed = true;
            }
            if (openDelay > 0) {
                openDelay--;
            } else if (openTicks < durationTicks) {
                openTicks++;
                changed = true;
            }
        }
        if (changed) {
            setChanged();
        }
        return changed;
    }

    /**
     * 服务端只负责「何时到哪一拍」的音效。
     *
     * <p>粒子编排（白光球膨胀 + 四周白光柱）<b>在客户端产生</b>，见
     * {@code SukimaPortalRenderer#emitShatterFx}。分工的原因：数千粒若走服务端就是数千包/tick，
     * 而客户端本地产生是零往返。演出总长、分拍边界均由 {@code fxTicks} 决定，
     * 而 {@code fxTicks} 会同步到客户端——两边分开不会脱节。
     */
    private void playShatterCues(int chargeEnd) {
        if (level == null || !(level instanceof ServerLevel server)) {
            return;
        }
        int t = fxTicks;
        if (t == 0) {
            // 蓄能底噪：紫水晶共振，深而长，给后面的爆发做铺垫。
            server.playSound(null, getBlockPos(), SoundEvents.AMETHYST_BLOCK_RESONATE,
                    SoundSource.BLOCKS, 1.0F, 0.55F);
        } else if (t == chargeEnd) {
            // 崩解瞬间：破障的震荡与碎裂声，均非 vanilla 演出资源。
            server.playSound(null, getBlockPos(), SoundEvents.WARDEN_SONIC_BOOM,
                    SoundSource.BLOCKS, 1.0F, 0.85F);
            server.playSound(null, getBlockPos(), SoundEvents.AMETHYST_CLUSTER_BREAK,
                    SoundSource.BLOCKS, 1.0F, 0.70F);
        }
    }

    // ---------------------------------------------------------------- 同步

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putFloat(TAG_SCALE, scale);
        tag.putInt(TAG_OPEN_TICKS, openTicks);
        tag.putInt(TAG_CLOSING_TICKS, closingTicks);
        tag.putInt(TAG_FX_TICKS, fxTicks);
        tag.putInt(TAG_FX_SEQ, fxSeq);
        tag.putInt(TAG_OPEN_DELAY, fxChargeEnd);
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
        fxTicks = Math.max(0, tag.getInt(TAG_FX_TICKS));
        fxSeq = tag.getInt(TAG_FX_SEQ);
        // 同步的是不可变边界，不是倒计时。
        fxChargeEnd = Math.max(1, tag.getInt(TAG_OPEN_DELAY));
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** 仅在状态实际变化时广播，避免每 tick 发包。 */
    public void syncIfChanged() {
        if (level == null || level.isClientSide) {
            return;
        }
        // 演出进度不在差分里：客户端用本地时钟推。否则每次开门要发 200 个包。
        if (scale == lastSentScale && openTicks == lastSentOpenTicks
                && closingTicks == lastSentClosingTicks) {
            return;
        }
        lastSentScale = scale;
        lastSentOpenTicks = openTicks;
        lastSentClosingTicks = closingTicks;
        level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
    }

    // ---------------------------------------------------------------- 持久化

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putFloat(TAG_SCALE, scale);
        tag.putInt(TAG_OPEN_TICKS, openTicks);
        tag.putInt(TAG_CLOSING_TICKS, closingTicks);
        tag.putInt(TAG_OPEN_DELAY, fxChargeEnd);
        tag.putInt(TAG_FX_SEQ, fxSeq);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        float s = tag.getFloat(TAG_SCALE);
        scale = s > 0.0F ? s : 1.0F;
        openTicks = Math.max(0, tag.getInt(TAG_OPEN_TICKS));
        closingTicks = Math.max(0, tag.getInt(TAG_CLOSING_TICKS));
        // 分拍边界也要在这条路径读：客户端的方块实体是由区域 NBT
        // 创建的（loadAdditional），而不是一上就拿到 update tag。
        // 两条路径只读一条的话，客户端 chargeEnd 会停在 0 被抵到 1，
        // 整个「蓄能」拍被跳过。
        fxChargeEnd = Math.max(1, tag.getInt(TAG_OPEN_DELAY));
        fxSeq = tag.getInt(TAG_FX_SEQ);
    }
}
