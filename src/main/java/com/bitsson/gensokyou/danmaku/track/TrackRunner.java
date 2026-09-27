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

    private final List<SpellCard> cards;
    private final SignaturePalette palette;
    private int tick;
    private SpellCard current;
    private boolean currentResolved;

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
     * 切到给定生命占比对应的符卡。返回是否发生了切换。
     *
     * <p><b>阈值语义</b>：{@code SpellCard.hpFraction} 是该符卡的<b>起始</b>生命占比，
     * 符卡表按占比从高到低排列。选中规则 = <b>「起始门槛已被达到的最后一张」</b>。
     * 这样边界无歧义：血量恰好等于某张卡的门槛时，算<b>已经进入</b>那张。
     *
     * <p>（早先误写成「取第一个 {@code f <= threshold} 的卡」——而首卡阈值最高，
     * 于是它永远第一个命中，<b>符卡永不切换</b>，玩家从头到尾只见过一种弹幕。）
     */
    public boolean selectCard(double hpFraction) {
        if (cards.isEmpty()) {
            return false;
        }
        SpellCard next = cards.get(0);
        for (SpellCard card : cards) {
            if (hpFraction <= card.hpFraction() + 1.0E-6D) {
                next = card;
            } else {
                break;
            }
        }
        if (next == current) {
            return false;
        }
        current = next;
        currentResolved = false;
        tick = 0;
        return true;
    }

    /**
     * 推进一拍。
     *
     * @param boss 发射者
     * @param targets 已被锁定的玩家（≤5，主目标为第一个）
     * @param baseDamage 单发弹伤
     */
    public void tick(LivingEntity boss, List<Player> targets, float baseDamage) {
        if (current == null || boss.level().isClientSide || !(boss.level() instanceof ServerLevel)) {
            tick++;
            return;
        }
        if (!currentResolved && tick >= currentCardDuration()) {
            currentResolved = true;
        }
        for (int i = 0; i < current.tracks().size(); i++) {
            Track track = current.tracks().get(i);
            if (!isDue(track, tick)) {
                continue;
            }
            emitTrack(boss, targets, track, tick, palette.at(i),
                    (float) (baseDamage * track.damageScale()));
        }
        tick++;
    }

    /** 本轨在当前 tick 是否该发射。 */
    private static boolean isDue(Track track, int tick) {
        if (track.repeatEvery() > 0) {
            return tick % track.repeatEvery() == 0;
        }
        return track.firesAt(tick);
    }

    /** 结束：清空当前符卡（换阶段或死亡时调用）。 */
    public void stop() {
        current = null;
        currentResolved = false;
        tick = 0;
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

    private void emitTrack(LivingEntity boss, List<Player> targets, Track track, int tickIndex,
                           int color, float baseDamage) {
        List<Player> aimTargets = targets;
        boolean perTarget = track.beats().stream().anyMatch(b -> b.targetMode().copiesPerTarget());
        int copies = perTarget ? Math.max(1, aimTargets.size()) : 1;
        for (int c = 0; c < copies; c++) {
            if (!DanmakuBudget.canEmit((ServerLevel) boss.level())) {
                return;
            }
            Vec3 anchor = aimTargets.isEmpty() ? boss.position()
                    : aimTargets.get(Math.min(c, aimTargets.size() - 1)).position();
            Vec3 toAnchor = anchor.subtract(boss.getEyePosition());
            if (toAnchor.lengthSqr() < 1.0E-6D) {
                continue;
            }
            Vec3 forward = toAnchor.normalize();
            Vec3 up = boss.getLookAngle();
            Vec3 worldUp = new Vec3(0, 1, 0);
            if (Math.abs(forward.dot(worldUp)) > 0.98D) {
                up = new Vec3(1, 0, 0);
            }
            // 缺口对齐玩家方位：缺口中心 = 玩家方位在 BOSS 平面内的投影角。
            double gapPhase = gapPhaseTowards(forward, up, worldUp, aimTargets);
            double phase = track.phaseAt(tickIndex);
            for (Track.Beat beat : track.beats()) {
                if (!isDue(beat, track, tickIndex)) {
                    continue;
                }
                List<Geometry.Shot> shots = Geometry.build(
                        beat.shape(), boss.getEyePosition(), forward, worldUp,
                        beat.params(), gapPhase, phase);
                for (Geometry.Shot shot : shots) {
                    DanmakuEmitter.emit(boss, shot, color, baseDamage, beat.shape());
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
        out.add("card=" + (current == null ? "-" : current.name()));
        out.add("tick=" + tick);
        out.add("tracks=" + (current == null ? 0 : current.tracks().size()));
        out.add("palette=" + palette.size());
        out.add("budget=" + GensokyouConfig.DANMAKU_ENTITY_CAP.get());
        return out;
    }
}
