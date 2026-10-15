package com.bitsson.gensokyou.danmaku.track;

import com.bitsson.gensokyou.config.GensokyouConfig;
import com.bitsson.gensokyou.danmaku.DanmakuBudget;
import com.bitsson.gensokyou.danmaku.DanmakuEmitter;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * 轨道运行时：把一副符卡表跑起来。
 *
 * <p>职责边界：本类只做「谁在第几 tick 该发什么」的调度与多目标分发，
 * <b>不</b>管移动、不管阶段切换、不管血量——那些是 {@code AbstractTouhouBoss} 的事。
 *
 * <p>多目标分发遵循 {@link TargetMode}：{@code AIMED} 对每名被锁定目标各发一份，
 * 其余模式只发一份（否则单个玩家会吃到 N 份环形压力，那是加难不是公平）。
 *
 * <p>弹幕总量达上限时**停止生成新弹**，不删除既有弹（视觉上读作「这一轮到此为止」，
 * 而非「BOSS 放空了」）。
 */
public final class TrackRunner {

    /**
     * BOSS 符卡弹幕的默认存活时长（tick）——20 秒。
     *
     * <p>节拍未显式声明寿命时用它。与武器弹幕的默认
     * （{@code AbstractDanmakuProjectile.MAX_LIFETIME_TICKS} = 60 秒）刻意分开：
     * 「boss 弹幕 20 秒」是一条内容决策，不该顺带改武器。
     */
    public static final int DEFAULT_BOSS_DANMAKU_LIFETIME = 400;

    private final List<SpellCard> cards;
    private final SignaturePalette palette;
    private int tick;
    private SpellCard current;
    /**
     * {@link #current} 在 {@link #cards} 中的下标；未选卡时为 -1。
     *
     * <p>单独存而不是用 {@code cards.indexOf(current)}：{@code SpellCard} 是 record，
     * {@code indexOf} 走结构化 {@code equals}，两张 name/hpFraction/tracks 全同的卡
     * 会被判为同一条，下标就指错了。这里跟着 {@link #selectCard} 的赋值走，恒定正确。
     */
    private int currentIndex = -1;
    /**
     * 挂起中的目标符卡。
     *
     * <p>血量已跨过下一张卡的门槛、但当前卡的循环尚未走完时，它在此登记而<b>不生效</b>。
     * 真正切换那一刻按当时的血量重算落点，故本字段只用于读状态（调试 / 血条提示），
     * MUST NOT 成为切换判据本身。
     */
    private SpellCard pending;
    private boolean currentResolved;

    // ------------------------------------------------------------------
    // 锁轴快照（extend-danmaku-orchestration）
    //
    // 一个「锁轴组」= 若干拍共享一次世界快照（原点 O、基 F/R/U、各目标胸口 T）。
    // 键 = (轨道下标, 组标识)；在本轮该组最早的那一拍捕获，循环边界/切卡/停止时清除。
    // 快照是发射期的服务端中间量：不跨端、不入 NBT，故无同步与生命周期问题。
    // ------------------------------------------------------------------

    private final java.util.Map<Long, LockSnapshot> lockSnapshots = new java.util.HashMap<>();
    /** 上一 tick 的循环内步号，用于检测循环边界以清除快照。 */
    private int prevCycleStep = -1;

    /** 一组锁轴拍共享的世界快照。 */
    private record LockSnapshot(Vec3 origin, Vec3 forward, Vec3 right, Vec3 up,
                                List<Vec3> targetChests, List<Integer> targetIds) {

        Vec3 chestFor(int copy, int size) {
            if (targetChests.isEmpty()) {
                return null;
            }
            return targetChests.get(Math.min(Math.max(copy, 0), targetChests.size() - 1));
        }
    }

    private static long snapshotKey(int trackIndex, int group) {
        return ((long) trackIndex << 32) | (group & 0xFFFFFFFFL);
    }

    /**
     * 确保某组的快照已捕获（只在本轮第一次遇到该组时捕获一次）。
     *
     * <p>以「首个到期拍」为捕获时刻——拍按 tick 升序到期，故第一个遇到的即该组最早拍。
     */
    private void ensureSnapshot(int trackIndex, int group, LivingEntity boss, List<Player> targets) {
        long key = snapshotKey(trackIndex, group);
        if (lockSnapshots.containsKey(key)) {
            return;
        }
        Vec3 origin = boss.getEyePosition();
        Vec3 worldUp = new Vec3(0, 1, 0);
        List<Vec3> chests = new ArrayList<>(targets.size());
        List<Integer> ids = new ArrayList<>(targets.size());
        for (Player player : targets) {
            chests.add(new Vec3(player.getX(), player.getY() + 0.9D, player.getZ()));
            ids.add(player.getId());
        }
        Vec3 forward;
        if (chests.isEmpty()) {
            forward = boss.getLookAngle();
            if (forward.lengthSqr() < 1.0E-9D) {
                forward = new Vec3(0, 0, 1);
            }
            forward = forward.normalize();
        } else {
            Vec3 toChest = chests.get(0).subtract(origin);
            forward = toChest.lengthSqr() < 1.0E-9D ? new Vec3(0, 0, 1) : toChest.normalize();
        }
        Vec3 right = forward.cross(worldUp);
        if (right.lengthSqr() < 1.0E-9D) {
            right = new Vec3(1, 0, 0);
        }
        right = right.normalize();
        Vec3 up = right.cross(forward).normalize();
        lockSnapshots.put(key, new LockSnapshot(origin, forward, right, up, chests, ids));
        com.bitsson.gensokyou.danmaku.DanmakuOrchestrationStats.recordLockGroup();
    }

    private void clearSnapshots() {
        lockSnapshots.clear();
        prevCycleStep = -1;
    }
    /**
     * 本阶段的编队装置，按轨道下标索引；无编队的轨道为 {@code null}。
     *
     * <p>「按轨道下标」而非按 rig id 列表：归属关系是静态的（轨 k 的弹挂 rig k），
     * 存一张按下标对齐的表就够了，不必为每条轨再维护一个列表。
     */

    public TrackRunner(List<SpellCard> cards, SignaturePalette palette) {
        this.cards = List.copyOf(cards);
        this.palette = palette;
    }

    public List<SpellCard> cards() {
        return cards;
    }

    public SignaturePalette palette() {
        return palette;
    }

    public SpellCard current() {
        return current;
    }

    /**
     * 当前符卡在表中的下标；未选卡时为 -1。
     *
     * <p>HUD 显示符卡名走这里而不是 {@link #current()}：网络包只传下标，客户端据此
     * 在本地表里反查名字，符卡名字符串因此永不上线（见 {@code SpellCardNamePayload}）。
     */
    public int currentIndex() {
        return currentIndex;
    }

    /**
     * 切到给定生命占比对应的符卡。返回是否发生了切换。
     *
     * <p><b>阈值语义</b>：{@code SpellCard.hpFraction} 是该符卡的<b>起始</b>生命占比，
     * 符卡表按占比从高到低排列。选中规则 = <b>「起始门槛已被达到的最后一张」</b>。
     * 这样边界无歧义：血量恰好等于某张卡的门槛时，算<b>已经进入</b>那张。
     *
     * <p><b>挂起语义</b>：若目标卡与当前卡不同、且<b>当前卡声明了循环长度</b>，
     * 本方法 SHALL NOT 立即切换，只把目标记为挂起；待当前卡的循环走完
     * （{@link #tick} 到达该长度）才真正切换。这保证符卡之间不叠加半程。
     * 未声明循环长度的符卡保持既有的即时切换。
     *
     * <p>（早先误写成「取第一个 {@code f <= threshold} 的卡」——而首卡阈值最高，
     * 于是它永远第一个命中，<b>符卡永不切换</b>，玩家从头到尾只见过一种弹幕。）
     */
    public boolean selectCard(double hpFraction) {
        if (cards.isEmpty()) {
            return false;
        }
        SpellCard next = cardFor(hpFraction);
        if (next == current) {
            // 血量回到当前卡区间内：撤销挂起。
            pending = null;
            return false;
        }
        if (current != null && current.cycleTicks() > 0 && !cycleFinished()) {
            // 当前卡还在循环中：挂起，不切换。
            pending = next;
            return false;
        }
        current = next;
        currentIndex = cards.indexOf(next);
        pending = null;
        currentResolved = false;
        tick = 0;
        clearSnapshots();
        // 换阶段先收装置：上一阶段的 rig 若留着，它的新弹会挂到「上一阶段的编队」上，
        // 表现为切卡瞬间队形突变。残留的旧弹则自行脱钩自由飞行（见 AbstractDanmakuProjectile）。
        return true;
    }

    /**
     * 「起始门槛已达成的最后一张」。
     *
     * <p>循环边界处若有挂起的目标，<b>以挂起者为准</b>：挂起期间血量可能又跌过一张卡，
     * 而挂起者记的是「第一次跨阈值时应该去的那张」。这里在真正切换那一刻按<b>当时</b>的血量
     * 重算一次，故最终落点与「若未挂起会落在哪」一致。
     */
    private SpellCard cardFor(double hpFraction) {
        SpellCard next = cards.get(0);
        for (SpellCard card : cards) {
            if (hpFraction <= card.hpFraction() + 1.0E-6D) {
                next = card;
            } else {
                break;
            }
        }
        return next;
    }

    /** 挂起中的目标符卡（无挂起时为 null）。供调试读。 */
    public SpellCard pending() {
        return pending;
    }

    /**
     * 当前符卡的循环是否已走完。
     *
     * <p>「走完」的判据是 <b>整除</b>而非 {@code >=}：符卡表按周期枚举时长度取整，
     * 而一个 10 秒周期（200 tick）的循环不该在第 201 tick 也算「走完」——
     * 那会让同一拍在边界上被发两次。
     */
    private boolean cycleFinished() {
        if (current == null) {
            return true;
        }
        int cycle = current.cycleTicks();
        return cycle <= 0 || tick > 0 && tick % cycle == 0;
    }

    /**
     * 推进一拍。
     *
     * @param boss 发射者
     * @param targets 已被锁定的玩家（≤5，主目标为第一个）
     * @param baseDamage 单发弹伤
     */
    public void tick(LivingEntity boss, List<Player> targets, float baseDamage) {
        if (current == null || boss == null || boss.level().isClientSide
                || !(boss.level() instanceof ServerLevel)) {
            // 无发射者时只推进时钟：调度本身不依赖世界，故离线可测「循环边界」这件事。
            tick++;
            return;
        }
        if (!currentResolved && tick >= currentCardDuration()) {
            currentResolved = true;
        }
        // 发射判据 MUST 用<b>循环内的步号</b>，不是自入卡以来的绝对 tick。
        //
        // <p>「显式枚举拍」的时间线是按周期写的（0..47 放一圈），而符卡一旦不切走，
        // 绝对 tick 就一直涨过 47 —— 那些拍再也不会命中，于是「每 240 tick 放一次环」
        // 退化成「只放一次」。未声明循环长度的符卡 {@code cycleTick()} 就等于绝对 tick，
        // 行为不变。
        int step = cycleTick();
        // 循环边界：清除上一轮的锁轴快照，使组在下一轮以新状态重新捕获。
        if (current.cycleTicks() > 0 && step == 0 && prevCycleStep > 0) {
            clearSnapshots();
        }
        prevCycleStep = step;
        // 符印调度：在本轮到期的拍之前 lead tick，按锁轴规则生成符印（与真弹共用同一快照）。
        if (GensokyouConfig.DANMAKU_TELEGRAPH_ENABLED.get()) {
            scheduleTelegraphs(boss, targets, step);
        }
        for (int i = 0; i < current.tracks().size(); i++) {
            Track track = current.tracks().get(i);
            if (!isDue(track, step)) {
                continue;
            }
            emitTrack(boss, targets, track, step,
                    (float) (baseDamage * track.damageScale()), formationOf(track), i);
        }
        tick++;
    }

    /**
     * 取出本轨的编队声明（可能未启用）。
     *
     * <p><b>一轨一份</b>：同一轨的多次重复发射共享同一份声明，故 20 次重复不会得到
     * 20 个互不相干的编队——队形在两次发射之间就断了。
     *
     * <p>本方法<b>不创建任何实体</b>。编队在发射那一刻被烘进每颗弹自己的同步数据，
     * 之后不存在「需要被维持的编队」，也就不存在换阶段时的销毁时序与孤儿弹处置。
     */
    private static Behaviour.Formation formationOf(Track track) {
        return track.formation();
    }

    /** 本轨在当前 tick 是否该发射。 */
    private static boolean isDue(Track track, int tick) {        if (track.repeatEvery() > 0) {
            return tick % track.repeatEvery() == 0;
        }
        return track.firesAt(tick);
    }

    /** 结束：清空当前符卡（换阶段或死亡时调用）。 */
    public void stop() {
        current = null;
        currentIndex = -1;
        currentResolved = false;
        tick = 0;
        clearSnapshots();
    }

    /** 当前符卡的名义时长（tick）：由最慢的一拍决定，供 HUD/调试用。 */
    public int currentCardDuration() {
        if (current == null) {
            return 0;
        }
        int max = 0;
        for (Track track : current.tracks()) {
            int last = 0;
            for (Track.Beat beat : track.beats()) {
                last = Math.max(last, beat.tick());
            }
            max = Math.max(max, track.repeatEvery() > 0 ? track.repeatEvery() : last);
        }
        return max;
    }

    /**
     * 当前符卡循环内的进度（tick）。
     *
     * <p>符卡内部按「循环内的第几拍」编排时需要它——典型是「前 120 tick 悬停放环、
     * 后 120 tick 游走」，这条分界既不进弹幕也不进移动策略，只进演出。
     *
     * <p>未声明循环长度时返回自入卡以来的总 tick。
     */
    public int cycleTick() {
        if (current == null) {
            return 0;
        }
        int cycle = current.cycleTicks();
        return cycle > 0 ? tick % cycle : tick;
    }

    /** 当前符卡声明的循环长度（tick）；未声明时为 0。 */
    public int cycleTicks() {
        return current == null ? 0 : current.cycleTicks();
    }

    /**
     * 发射一条轨道的全部到期拍。
     *
     * <p><b>颜色取 {@code track.color()} 而非 {@code palette.at(轨序)}。</b>
     * 后者让「符卡表里声明的颜色」全程未被读取：单轨符卡（多张卡的常态）全部拿到
     * {@code at(0)}，于是三张卡同色，而作者写在 {@code of(name, PALETTE.at(k))} 里的
     * 那个颜色是死的。症状是「我明明声明了淡蓝，激光出来是白灰」——
     * 淡蓝声明在轨 2，运行时拿到的是 {@code at(1)}。
     *
     * <p>轨序只用来做<b>卡内轨与色盘的对应</b>（即每条轨从色盘取一个身份色），
     * 取哪个色号是符卡作者的决定，不该由位置决定。
     */
    private void emitTrack(LivingEntity boss, List<Player> targets, Track track, int step,
                           float baseDamage, Behaviour.Formation formation, int trackIndex) {
        List<Player> aimTargets = targets;
        boolean perTarget = track.beats().stream().anyMatch(b -> b.targetMode().copiesPerTarget());
        if (perTarget && aimTargets.isEmpty()) {
            // 瞄准型轨道在<b>无人可瞄</b>时整轨不发射。
            //
            // <p>瞄准型的语义是「有一发是给你的」；没有给的人就不该有这一发。
            // 早先这里用 {@code Math.max(1, aimTargets.size())} 兜底，于是「零目标」
            // 被静默当成「一个人」：锚点落回 BOSS 自己的脚，瞄准方向变成竖直向下，
            // 环挂到 BOSS 下方，而「重瞄各自的目标」解析不出实体 id ⇒ 那 48 颗弹
            // 永远不发射、零速悬在原地 60 秒（默认寿命）才消失。
            // 现象是「玩家一死，BOSS 就不放追踪环了」——它其实还在放，只是放歪了、
            // 而且放出来的是一批打不到人的僵尸弹。
            return;
        }
        // 锁轴组：在首个到期拍捕获快照（组内其余拍复用）。
        for (Track.Beat beat : track.beats()) {
            if (isDue(beat, track, step) && beat.orchestration().locks()) {
                ensureSnapshot(trackIndex, beat.orchestration().lockGroup(), boss, aimTargets);
            }
        }
        int copies = perTarget ? aimTargets.size() : 1;
        Vec3 worldUp = new Vec3(0, 1, 0);
        for (int c = 0; c < copies; c++) {
            if (!DanmakuBudget.canEmit((ServerLevel) boss.level())) {
                return;
            }
            Vec3 anchor = aimTargets.isEmpty() ? boss.position()
                    : aimTargets.get(Math.min(c, aimTargets.size() - 1)).position();
            // 「朝本份的目标」与「随机一名」都在复制这一刻才确定具体是谁：
            // 同一拍在 5 人场里发 5 份，每份该朝不同的人。
            int copyTargetId = aimTargets.isEmpty() ? 0
                    : aimTargets.get(Math.min(c, aimTargets.size() - 1)).getId();
            int randomPick = aimTargets.isEmpty() ? 0
                    : aimTargets.get(boss.level().getRandom().nextInt(aimTargets.size())).getId();
            Vec3 toAnchor = anchor.subtract(boss.getEyePosition());
            Vec3 liveForward = toAnchor.lengthSqr() < 1.0E-6D ? null : toAnchor.normalize();
            Vec3 liveUp = boss.getLookAngle();
            if (liveForward != null && Math.abs(liveForward.dot(worldUp)) > 0.98D) {
                liveUp = new Vec3(1, 0, 0);
            }
            double phase = track.phaseAt(step);
            for (Track.Beat beat : track.beats()) {
                if (!isDue(beat, track, step)) {
                    continue;
                }
                // 锁轴组：用锁定帧的原点/方向/目标；否则用当刻采样（既有行为）。
                LockSnapshot snap = beat.orchestration().locks()
                        ? lockSnapshots.get(snapshotKey(trackIndex, beat.orchestration().lockGroup()))
                        : null;
                Vec3 origin;
                Vec3 forward;
                Vec3 buildTarget;
                Vec3 up;
                if (snap != null) {
                    origin = snap.origin();
                    Vec3 chest = snap.chestFor(c, copies);
                    buildTarget = chest != null ? chest : origin.add(snap.forward());
                    Vec3 toChest = buildTarget.subtract(origin);
                    forward = toChest.lengthSqr() < 1.0E-9D ? snap.forward() : toChest.normalize();
                    up = snap.up();
                } else {
                    if (liveForward == null) {
                        continue;
                    }
                    origin = boss.getEyePosition();
                    forward = liveForward;
                    buildTarget = anchor;
                    up = liveUp;
                }
                double gapPhase = gapPhaseTowards(forward, up, worldUp, aimTargets);
                // 把<b>目标位置</b>一并传入：AROUND_TARGET / LATTICE 的发射点采样自目标周围，
                // 方向由每一发自己的「原点 → 目标」决定。它们是唯一需要它的形状。
                List<Geometry.Shot> shots = Geometry.build(beat, origin, forward,
                        buildTarget, worldUp, gapPhase, phase, boss.level().getRandom());
                // 组级安全取消：任一发出生点距任一存活战斗玩家过近 ⇒ 整组本轮不生成。
                if (!spawnGroupSafe(shots, targets)) {
                    com.bitsson.gensokyou.danmaku.DanmakuOrchestrationStats.recordSafetyCancel();
                    continue;
                }
                int turnTarget = Behaviour.resolveTurnTarget(
                        beat.behaviour().motion().burstTargetId(), copyTargetId, randomPick);
                Vec3 formationCenter = snap != null ? snap.origin() : boss.getEyePosition();
                for (Geometry.Shot shot : shots) {
                    // 编队参考点在<b>发射这一刻</b>对 BOSS 位置取快照，随后写进每颗弹。
                    // 刻意<b>不</b>存 BOSS 实体引用：编队不是反应式的，此后弹的世界里
                    // 再没有外部输入，双端也就不再有「任何需要收敛的量」。
                    //
                    // 取<b>眼位</b>而非脚位：编队中心必须与几何给出的形状中心同一点，
                    // 否则形状中心的那颗弹（花心）到参考点就有一段固定偏移，
                    // 「自身即参考点 → 改朝目标射」这条退化分支永不成立。
                    int collapseAge = beat.orchestration().collapses()
                            ? Math.max(1, beat.orchestration().collapseAge() - beat.tick()) : 0;
                    DanmakuEmitter.emit(boss, shot, beat.behaviour(), track.color(), baseDamage,
                            formation, formation.active() ? formationCenter : null,
                            beat.projectile(), beat.anchor(),
                            beat.lifetimeTicks() > 0 ? beat.lifetimeTicks()
                                    : DEFAULT_BOSS_DANMAKU_LIFETIME,
                            turnTarget,
                            beat.legSpec(),
                            beat.harmlessTicks(), collapseAge);
                }
            }
        }
    }

    /**
     * 一拍在当前 tick 是否到期。
     *
     * <p>重复轨道里，{@code beat.tick()} 是<b>重复周期内的相位</b>，不是绝对 tick：
     * 一条 {@code repeatEvery(50)} 且拍在 {@code t=0} 的轨道，应当在 0/50/100… 各发一次。
     * 早先这里拿绝对 tick 去比 {@code beat.tick()}，导致所有重复轨道<b>一弹不发</b>。
     */
    private static boolean isDue(Track.Beat beat, Track track, int tick) {
        int period = track.repeatEvery();
        if (period > 0) {
            return Math.floorMod(beat.tick(), period) == Math.floorMod(tick, period);
        }
        return beat.tick() == tick;
    }

    /**
     * 整组出生点净距检查（{@code extend-danmaku-orchestration}）。
     *
     * <p>任一发出生点距任一存活战斗玩家的净距低于最小安全距离 ⇒ 整组本轮取消
     * （不平移、不积压）。净距扣掉弹体半径与玩家碰撞半径。
     *
     * <p>最小安全距离为 0 时整条关闭，行为与引入前一致。
     */
    private static boolean spawnGroupSafe(List<Geometry.Shot> shots, List<Player> targets) {
        double min = GensokyouConfig.DANMAKU_MIN_SPAWN_DISTANCE.get();
        if (min <= 0.0D || targets.isEmpty()) {
            return true;
        }
        for (Geometry.Shot shot : shots) {
            double radius = Math.max(0.05D, shot.size()) * 0.5D;
            for (Player player : targets) {
                Vec3 chest = player.position().add(0.0D, player.getBbHeight() * 0.5D, 0.0D);
                if (shot.origin().distanceTo(chest) - radius - 0.3D < min) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * 符印调度（{@code extend-danmaku-orchestration}）。
     *
     * <p>对每条轨的每个带领示的拍，在 {@code step == beat.tick − leadTicks} 生成一枚符印。
     * 若该拍属于锁轴组，MUST 先捕获快照，符印与随后的真弹共用同一原点。
     *
     * <p>符印只存活 {@code leadTicks}，故它恰好在真弹发射那一刻消失；
     * 若该组随后被安全取消，符印只是到点自然消失（不会保留、也不会转成真弹）。
     */
    private void scheduleTelegraphs(LivingEntity boss, List<Player> targets, int step) {
        if (current == null || boss == null || !(boss.level() instanceof ServerLevel server)) {
            return;
        }
        for (int i = 0; i < current.tracks().size(); i++) {
            Track track = current.tracks().get(i);
            for (Track.Beat beat : track.beats()) {
                Track.Beat.Orchestration orchestration = beat.orchestration();
                if (!orchestration.telegraphs()) {
                    continue;
                }
                if (beat.tick() - orchestration.leadTicks() != step) {
                    continue;
                }
                Vec3 origin = boss.getEyePosition();
                if (orchestration.locks()) {
                    ensureSnapshot(i, orchestration.lockGroup(), boss, targets);
                    LockSnapshot snap = lockSnapshots.get(snapshotKey(i, orchestration.lockGroup()));
                    if (snap != null) {
                        origin = snap.origin();
                    }
                }
                com.bitsson.gensokyou.entity.DanmakuTelegraph.spawn(
                        server, origin, orchestration.leadTicks());
            }
        }
    }

    /** 缺口相位：令环的缺口正对当前瞄准方向。 */
    private static double gapPhaseTowards(Vec3 forward, Vec3 up, Vec3 worldUp, List<Player> targets) {
        if (targets.isEmpty()) {
            return 0.0D;
        }
        Vec3 flat = new Vec3(forward.x, 0.0D, forward.z);
        if (flat.lengthSqr() < 1.0E-6D) {
            return 0.0D;
        }
        flat = flat.normalize();
        Vec3 right = flat.cross(worldUp).normalize();
        double deg = net.minecraft.util.Mth.wrapDegrees(
                Math.toDegrees(Math.atan2(flat.dot(right), flat.dot(forward))));
        return deg;
    }

    /** 供 HUD/调试：当前轨道的可读状态。 */
    public List<String> describe() {
        List<String> out = new ArrayList<>();
        out.add("card=" + (current == null ? "-" : current.name().getString()));
        out.add("tick=" + tick);
        out.add("tracks=" + (current == null ? 0 : current.tracks().size()));
        out.add("palette=" + palette.size());
        out.add("budget=" + GensokyouConfig.DANMAKU_ENTITY_CAP.get());
        return out;
    }
}
