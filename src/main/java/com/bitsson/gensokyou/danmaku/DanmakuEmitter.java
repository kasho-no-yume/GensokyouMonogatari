package com.bitsson.gensokyou.danmaku;

import com.bitsson.gensokyou.danmaku.track.Behaviour;
import com.bitsson.gensokyou.danmaku.track.Geometry;
import com.bitsson.gensokyou.danmaku.track.Projectile;
import com.bitsson.gensokyou.danmaku.track.Track;
import com.bitsson.gensokyou.entity.AbstractDanmakuProjectile;
import com.bitsson.gensokyou.entity.AbstractTouhouBoss;
import com.bitsson.gensokyou.entity.DanmakuWhitelists;
import com.bitsson.gensokyou.entity.LaserDanmaku;
import com.bitsson.gensokyou.entity.SphereDanmaku;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Set;


/**
 * 发射指令 → 弹幕实体的翻译层。
 *
 * <p><b>几何与行为在这里分开施加</b>：{@code Geometry} 产出的是「纯几何」，
 * 本类 MUST NOT 依据<b>形状名</b>改写弹的运动；运动/分裂/显隐一律由
 * {@link Behaviour} 决定，按<b>行为种类</b>分派。改前这里是
 * {@code switch (shape)}，于是「让某几何带某行为」必须新造一个几何。
 *
 * <p>分派按行为种类而非形状名，是「几何与行为正交」这条约束的落点：若哪天
 * 出现了「按 {@code shape} 分派」的分支，lint 侧的新增判据会立刻失去意义。
 *
 * <p>行为到实体的映射（全部走既有弹幕实体的行为开关，MUST NOT 为任一行为新增
 * 独立弹幕类型）：
 * <pre>
 *   Motion.NONE        → 无额外处理（匀速直线）
 *   Motion.CURVE       → configureCurve(axis, rate)
 *   Motion.HOVER       → configureHover(hoverTick)      定住后仍判伤
 *   Motion.MINE        → configureMine(radius)          埋设期不接触判伤，近身才结算
 *   Motion.GROUND_HUG  → 抹掉竖直分量、保留水平分量
 *   Split              → configureSplit(tick, count)
 *   Visibility         → configurePhaseHide(period, duty, offset)
 * </pre>
 *
 * <p>新弹种是一个独立且可后置的旋钮：凡是改变「能命中什么 / 如何判定命中」的能力
 * 才走新弹种（如激光的射线判伤），运动与显隐一律留在行为层。
 */
public final class DanmakuEmitter {

    /** BOSS 弹幕不伤同类：白名单留空，靠 owner 判定即可。 */
    private static final Set<net.minecraft.world.entity.EntityType<?>> NO_WHITELIST = Set.of();

    private DanmakuEmitter() {
    }

    public static void emit(LivingEntity boss, Geometry.Shot shot, Behaviour behaviour,
                            int color, float damage) {
        emit(boss, shot, behaviour, color, damage, null, null, Projectile.SPHERE);
    }

    /**
     * 发射一枚弹，可选地挂上编队帧。
     *
     * @param formation 本轨的编队声明；未启用时传 {@code null}
     * @param center    编队参考点的世界坐标快照。声明未启用时忽略
     */
    public static void emit(LivingEntity boss, Geometry.Shot shot, Behaviour behaviour,
                            int color, float damage, Behaviour.Formation formation, Vec3 center) {
        emit(boss, shot, behaviour, color, damage, formation, center, Projectile.SPHERE);
    }

    /**
     * 发射一枚弹：几何 + 行为 + <b>弹种</b>，可选地挂上编队帧。
     *
     * <p><b>弹种 MUST 由入参决定，MUST NOT 写死球弹。</b>早先这里硬编
     * {@code new SphereDanmaku}，于是「用激光发一个环」写不出来——只能去改翻译层，
     * 而改翻译层会一次性动到所有形状的路径。
     */
    public static void emit(LivingEntity boss, Geometry.Shot shot, Behaviour behaviour,
                            int color, float damage, Behaviour.Formation formation, Vec3 center,
                            Projectile projectile) {
        emit(boss, shot, behaviour, color, damage, formation, center, projectile,
                Track.Beat.SpawnAnchor.NONE, 0, 0);
    }

    /**
     * 发射一枚弹的完整入口。
     *
     * @param anchorArg 生成点的世界锚定（几何层不接触世界，故由本层求值）
     * @param lifetimeTicks 本批弹的寿命（tick）；{@code 0} = 沿用弹体默认寿命
     * @param turnTargetId 定时换向的目标实体 id（{@code 0} = 无目标）。
     *        它由 {@code TrackRunner} 在<b>按人复制</b>的那一刻解析——同一拍发 5 份时
     *        每份该朝不同的人，故不能由符卡表写死。
     */
public static void emit(LivingEntity boss, Geometry.Shot shot, Behaviour behaviour,
                             int color, float damage, Behaviour.Formation formation, Vec3 center,
                             Projectile projectile, Track.Beat.SpawnAnchor anchorArg,
                             int lifetimeTicks, int turnTargetId) {
        emit(boss, shot, behaviour, color, damage, formation, center, projectile,
                anchorArg, lifetimeTicks, turnTargetId, null);
    }

    /**
     * 发射一枚弹的完整入口，<b>可挂段式运动</b>。
     *
     * <p>段式运动在<b>此处</b>构造（而不是在 {@code TrackRunner} 里预先构造好传进来），
     * 因为它需要以这枚弹的<b>发射方向</b>为极轴 —— 那个方向只有翻译层在这一刻知道。
     * 构造即缓存：{@link com.bitsson.gensokyou.danmaku.motion.DanmakuLegMotion}
     * 的纪律是「方向在构造期定死，之后每 tick 只读缓存」。
     *
     * @param legSpec 本拍的段式运动声明；{@code null} = 不使用段式运动
     */
    public static void emit(LivingEntity boss, Geometry.Shot shot, Behaviour behaviour,
                             int color, float damage, Behaviour.Formation formation, Vec3 center,
                             Projectile projectile, Track.Beat.SpawnAnchor anchorArg,
                             int lifetimeTicks, int turnTargetId,
                             @Nullable com.bitsson.gensokyou.danmaku.motion.DanmakuLegSpec legSpec) {

        if (!(boss.level() instanceof ServerLevel server)) {
            return;
        }
        Track.Beat.SpawnAnchor anchor = anchorArg == null
                ? Track.Beat.SpawnAnchor.NONE : anchorArg;
        // 逐发旋钮先于实体构造施加：尺寸进构造器（走 setSize → refreshDimensions），
        // 寿命与生成点锚定都要在入世界前落定。
        double size = Math.max(0.05D, shot.size());
        double speed = shot.params().speed();
        float shotDamage = (float) (damage * shot.damageScale());
        AbstractDanmakuProjectile bullet =
                create(server, boss, shot, color, shotDamage, size, projectile);
        if (bullet == null) {
            return;
        }
        if (lifetimeTicks > 0) {
            bullet.setLifetimeTicks(lifetimeTicks);
        }
        applySpawnAnchor(bullet, boss, shot, anchor);
        applyMotion(bullet, shot, behaviour.motion(), speed, turnTargetId);
        if (legSpec != null) {
            // 段式运动：以这枚弹自己的发射方向为极轴构造，构造即定死方向。
            // MUST 在 applyMotion 之后 —— 它会覆盖 applyMotion 设的速度。
            bullet.setLegMotion(legSpec.toMotion(shot.direction()), legSpec.randomState());
        }
        applySplit(bullet, behaviour.split());
        applyVisibility(bullet, behaviour.visibility());
        // MUST 在 setDirection 之后：编队帧会接管位置，几何给的初速随即失效。
        // 形状（「花瓣长短」）编码在 shot.origin 与 center 的差里，故两者都传进去。
        if (formation != null && formation.active() && center != null) {
            bullet.bindToFrame(formation.frameFor(center, shot.origin()));
        }
        server.addFreshEntity(bullet);
        DanmakuBudget.recordEmit();
    }

    /**
     * 生成点的世界锚定。
     *
     * <p>「往下找地面 / 找第一个空气」需要访问世界，而<b>几何层刻意不接触世界</b>
     * （它是无世界即可离线 lint 与断言的纯数学）。故锚定在此完成：
     * 发射器持有发射者，因而持有世界。
     *
     * <p>锚定只改出生点，<b>不改方向</b>——雨往下、柱往上是几何决定的语义，
     * 锚定只回答「从哪儿开始」。
     */
    private static void applySpawnAnchor(AbstractDanmakuProjectile bullet, LivingEntity boss,
                                         Geometry.Shot shot, Track.Beat.SpawnAnchor anchor) {
        if (anchor == Track.Beat.SpawnAnchor.NONE) {
            return;
        }
        Vec3 at = switch (anchor) {
            case NONE -> shot.origin();
            case FIRST_AIR_BELOW -> firstAirBelow(boss.level(), shot.origin());
            case GROUND_BELOW -> groundBelow(boss.level(), shot.origin());
            case PLAYER_GROUND -> groundBelow(boss.level(), randomPlayerColumn(boss, shot.origin()));
        };
        bullet.setPos(at.x, at.y, at.z);
    }

    /**
     * 从给定点向下找<b>第一个空气块</b>。
     *
     * <p>返回该空气块的下沿（即贴着上方实体块的顶面）。找不到（下方 64 格全是空气）时
     * 退回给定点本身——宁可让雨从原高度落下，也不要凭空把它塞进地底。
     */
    private static Vec3 firstAirBelow(Level level, Vec3 from) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int startY = (int) Math.floor(from.y);
        for (int y = startY; y >= startY - 64; y--) {
            cursor.set((int) Math.floor(from.x), y, (int) Math.floor(from.z));
            if (level.getBlockState(cursor).isAir()) {
                return new Vec3(from.x, y, from.z);
            }
        }
        return from;
    }

    /**
     * 从给定点向下找<b>地面</b>，返回地面之上那一格。
     *
     * <p>找不到实体方块时退回给定点：地柱打不中人也比打在地底下强。
     */
    private static Vec3 groundBelow(Level level, Vec3 from) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int startY = (int) Math.floor(from.y);
        for (int y = startY; y >= startY - 64; y--) {
            cursor.set((int) Math.floor(from.x), y, (int) Math.floor(from.z));
            if (!level.getBlockState(cursor).isAir()) {
                return new Vec3(from.x, y + 1.0D, from.z);
            }
        }
        return from;
    }

    /**
     * 取一名被锁定玩家的 xz 柱，返回其正上方的给定点高度。
     *
     * <p>「每 5 次至少一次压在玩家头上」这条规则由<b>拍</b>声明：
     * 编排表把每第 5 拍标成 {@code PLAYER_GROUND}，其余拍用随机点。
     * 规则因此是数据而不是代码里的计数器。
     */
    private static Vec3 randomPlayerColumn(LivingEntity boss, Vec3 fallback) {
        if (boss instanceof AbstractTouhouBoss touhou) {
            List<Player> locked = touhou.lockedTargets();
            if (!locked.isEmpty()) {
                Player pick = locked.get(boss.getRandom().nextInt(locked.size()));
                return new Vec3(pick.getX(), fallback.y, pick.getZ());
            }
        }
        if (boss instanceof net.minecraft.world.entity.Mob mob && mob.getTarget() != null) {
            return new Vec3(mob.getTarget().getX(), fallback.y, mob.getTarget().getZ());
        }
        return fallback;
    }


    /**
     * 按弹种造出实体。
     *
     * <p>两种弹种都是 {@link AbstractDanmakuProjectile} 的子类，故「弹种」与
     * 「行为」能正交组合——激光同样可以挂曲射与相位隐藏。
     *
     * <p><b>行为在此一律不施加</b>：激光的延迟与持续时间是它自己的生命周期
     * （{@code Phase{DELAY, ACTIVE, DONE}}），与 {@code Behaviour} 的显隐是两套东西，
     * 在这里混起来会让「延迟期间算不算隐藏态」这种问题没有答案。
     */
    private static AbstractDanmakuProjectile create(ServerLevel server, LivingEntity boss,
                                                     Geometry.Shot shot, int color, float damage,
                                                     double size, Projectile projectile) {
        if (projectile != null && projectile.isLaser()) {
            LaserDanmaku laser = new LaserDanmaku(server, shot.origin(), shot.direction(),
                    damage, color,
                    projectile.laserLength(), projectile.laserRadius(),
                    projectile.laserDelaySeconds(), projectile.laserDurationSeconds(),
                    boss, NO_WHITELIST, projectile.laserPiercesBlocks());
            return laser;
        }
        // 第 5 个形参是 <b>size</b>（球体直径），不是速度。此处此前传的是 speed，
        // 于是所有 BOSS 弹幕的实际直径 = 弹速：符卡表声明的 size 全程未被读取，
        // 「全大慢弹」实际发成比默认球（0.4）还小的弹，碰撞箱也随之变小。
        SphereDanmaku sphere = new SphereDanmaku(
                server, boss, damage, color, (float) size, NO_WHITELIST);
        sphere.moveTo(shot.origin().x, shot.origin().y, shot.origin().z, 0F, 0F);
        return sphere;
    }


    /**
     * 几何给出的初速，再按行为运动改写。
     *
     * <p>顺序有意义：行为在几何<b>之后</b>施加，故「贴地」这类运动能把几何给的
     * 竖直分量抹掉，而几何不必知道自己被改写了。
     */
    private static void applyMotion(AbstractDanmakuProjectile bullet, Geometry.Shot shot,
                                    Behaviour.Motion motion, double speed, int turnTargetId) {

        Behaviour.Motion.Kind kind = motion.kind();
        if (kind == Behaviour.Motion.Kind.GROUND_HUG) {
            Vec3 flat = new Vec3(shot.direction().x, 0.0D, shot.direction().z);
            if (flat.lengthSqr() > 1.0E-9D) {
                bullet.setDirection(flat.normalize(), speed);
            } else {
                bullet.setDeltaMovement(Vec3.ZERO);
            }
            return;
        }
        if (shot.direction().lengthSqr() > 1.0E-9D) {
            bullet.setDirection(shot.direction(), speed);
        } else {
            // 几何给了零方向（静止待发类）：由行为决定其语义。
            bullet.setDeltaMovement(Vec3.ZERO);
        }
        switch (kind) {
            case CURVE -> bullet.configureCurve(
                    Geometry.axisFrom(motion.curveYawDeg(), motion.curvePitchDeg()),
                    motion.curveRateDegPerSec());
            // 速率曲线 MUST 在 setDirection 之后挂：它要趁速度非零时记下方向轴
            // （曲线会把速率降到 0，零速时方向不可恢复）。
            case SPEED_PROFILE -> bullet.configureSpeedProfile(motion.speedProfile(),
                    motion.diesAtOrigin());
            case HOVER -> bullet.configureHover(motion.hoverTick());
            case MINE -> bullet.configureMine(motion.mineRadius());
            case BURST, RECLAIM -> {
                // 定时换向 = 换向前的一条速率曲线（「飞 1 秒停 2 秒」/「原地停 3 秒」）
                // + 那一刻的换向。两者是同一条时间轴的两半，必须一起挂：
                // 只挂曲线则到点不变向（花一直悬着、环一直不动），
                // 只挂换向则时序丢失（花立刻炸开、环一出生就扑）。
                //
                // <p><b>换向目标解析不出实体时，整条运动都不挂。</b>只挂曲线的话，
                // 弹会走到「停住等待」那一段再无下文——零速悬在原地直到寿命结束
                // （默认 60 秒），既打不到人也退不掉。宁可让它退回普通匀速弹直飞出去：
                // 打偏是一瞬间的事，悬住是一分钟的事，而且完全静默。
                if (turnTargetId > 0 || motion.burstTargetId() > 0) {
                    bullet.configureSpeedProfile(motion.speedProfile(), false);
                    bullet.configureBurst(motion.burstAtTick(), motion.burstRadialSpeed(),
                            motion.burstAimSpeed(),
                            turnTargetId > 0 ? turnTargetId : motion.burstTargetId());
                }
            }
            case NONE, GROUND_HUG -> {
                // 上面已处理
            }
        }
    }


    private static void applySplit(AbstractDanmakuProjectile bullet, Behaviour.Split split) {
        if (split.active()) {
            bullet.configureSplit(split.tick(), split.count());
        }
    }

    private static void applyVisibility(AbstractDanmakuProjectile bullet, Behaviour.Visibility visibility) {
        if (visibility.active()) {
            bullet.configurePhaseHide(
                    visibility.periodTicks(), visibility.duty(), visibility.phaseOffset());
        }
    }

    /** BOSS 自用弹幕的白名单工厂（留作差异化旋钮）。 */
    public static Set<net.minecraft.world.entity.EntityType<?>> bossWhitelist() {
        return DanmakuWhitelists.FAIRY;
    }
}
