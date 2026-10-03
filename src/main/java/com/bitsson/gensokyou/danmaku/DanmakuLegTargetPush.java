package com.bitsson.gensokyou.danmaku;

import com.bitsson.gensokyou.danmaku.motion.DanmakuLegMotion;
import com.bitsson.gensokyou.danmaku.render.DanmakuSyncServer;
import com.bitsson.gensokyou.danmaku.render.DanmakuSyncStats;
import com.bitsson.gensokyou.danmaku.render.DanmakuTrackingIndex;
import com.bitsson.gensokyou.entity.AbstractDanmakuProjectile;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * `TARGET` 段的权威方向推送 —— 档三（运行期下发）的唯一用武之地。
 *
 * <p><b>为什么只有它需要推送</b>：段方向若是 {@code FIXED}（写在段表里）或
 * {@code SEED}（{@code f(种子, 段号)}），都是年龄的纯函数，两端各自算即可。
 * 而 {@code TARGET} 段指向一个实体的<b>实时</b>位置 ——
 * 那依赖发射之后才发生的事实，客户端无从得知。
 *
 * <p><b>为什么是一次快照而不是事件流</b>：见
 * {@code danmaku-pipeline-capacity} 的三档优先级。档三的形态是<b>一次权威状态快照</b>，
 * MUST NOT 为单次决策新建包类型 —— 故这里复用既有的 {@code DanmakuSnapshotPayload}
 * （位置 + 速度 + 指纹），它已经带足了「让客户端从权威状态重新锚定」所需的一切。
 *
 * <p><b>为什么每段只推一次</b>：方向在整段内恒定（{@code isSegmentStart} 守门），
 * 否则一条 48 拍的环会退化成每拍一包 —— 那是 20 倍带宽。
 *
 * <p><b>降级路径是常态</b>：玩家在悬停期间退出是常事。目标不可解析时
 * 服务端沿用当前方向、客户端按自己的段表走完剩下的段，两条路径在换向后的
 * 第一 tick 位置相同（快照给的就是那个位置）。
 */
public final class DanmakuLegTargetPush {

    /** 每 tick 的推送预算，防呆用。真正的守门是 {@code isSegmentStart}。 */
    private static final int MAX_PUSHES_PER_TICK = 64;

    private DanmakuLegTargetPush() {
    }

    /**
     * 若该年龄正好是 {@code TARGET} 段的起点且方向仍缺失，推一次权威快照。
     *
     * <p>由 {@code tickDanmaku} 在服务端每 tick 调用。
     */
    public static void request(AbstractDanmakuProjectile bullet, DanmakuLegMotion motion) {
        if (!(bullet.level() instanceof ServerLevel level)) {
            return;
        }
        int age = bullet.age();
        if (!motion.isSegmentStart(age)) {
            // 非段起点 ⇒ 该段方向已定（或正在沿用上一段），不推。
            return;
        }
        int segment = motion.segmentAt(age);
        Vec3 direction = resolveAuthoritativeDirection(bullet, segment);
        if (direction == null) {
            // 目标不可解析 ⇒ 降级：保持当前方向，两端由此得出相同结果。
            DanmakuSyncStats.recordLegTargetUnresolved();
            return;
        }
        motion.applyAuthoritativeDirection(segment, direction);
        pushToTracking(bullet, level, DanmakuSyncStats::recordLegTurnPushed);
    }

    /**
     * 解出该段的服务端权威方向。
     *
     * <p>取「弹 → 目标」的连线。目标由 {@code Behaviour.TARGET_AUTO} 那类既有语义决定，
     * 这里刻意<b>不</b>新造目标选择：档三的代价应花在「下发」上，不是「发明语义」上。
     */
    @Nullable
    private static Vec3 resolveAuthoritativeDirection(AbstractDanmakuProjectile bullet, int segment) {
        Entity target = bullet.targetEntity();
        if (target == null || !target.isAlive()) {
            return null;
        }        Vec3 to = target.getEyePosition().subtract(bullet.position());
        if (to.lengthSqr() < 1.0E-8D) {
            return null;
        }
        return to.normalize();
    }

    private static void pushToTracking(AbstractDanmakuProjectile bullet, ServerLevel level,
                                       Runnable onPushed) {
        int pushed = 0;
        for (ServerPlayer player : level.players()) {
            if (!DanmakuTrackingIndex.isTracking(player.getUUID(), bullet.getId())) {
                continue;
            }
            if (pushed >= MAX_PUSHES_PER_TICK) {
                break;
            }
            DanmakuSyncServer.sendTurnSnapshot(player, bullet);
            pushed++;
        }
        if (pushed > 0) {
            onPushed.run();
        }
    }
}