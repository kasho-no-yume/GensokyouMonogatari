package com.bitsson.gensokyou.command;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.danmaku.track.SignaturePalette;
import com.bitsson.gensokyou.danmaku.track.SpellCard;
import com.bitsson.gensokyou.danmaku.track.TrackRunner;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 符卡图案预览：<b>只跑弹幕，不跑 BOSS</b>。
 *
 * <p>存在的理由：图案的手感与观感 SHOULD 能独立于 BOSS 的 AI、转向与避障来审。
 * 实机对着 BOSS 看，会把「弹走得不顺」与「BOSS 站错了位置」混在一起分不清。
 *
 * <p><b>用什么当发射者</b>——{@code TrackRunner} 与 {@code DanmakuEmitter} 需要一个
 * {@code LivingEntity} 作为 owner：弹幕的归属、伤害归属与位置原点都挂在它身上。
 * 这里用一枚不可见、无重力、无 AI 的 {@link ArmorStand}：
 * <ul>
 *   <li>不可见 ⇒ 画面里只有弹幕</li>
 *   <li>无 AI、无重力 ⇒ 绝对静止，不会像 BOSS 那样被避障逻辑推着走</li>
 *   <li>它是弹幕的 owner，故 {@code isWhitelisted} 对它为真 ⇒ 弹幕不会打中它</li>
 * </ul>
 * 代价是它占 0.5×1.975 的碰撞箱，但弹幕打不到它，玩家也不会去撞一个隐形的架。
 */
@EventBusSubscriber(modid = Gensokyou.MODID)
public final class DanmakuPreview {

    /** 一场预览。刻意用可变类而非 record——卡片推进需要改状态。 */
    private static final class Session {
        final ArmorStand rig;
        final TrackRunner runner;
        final List<SpellCard> cards;
        final float damage;
        final int startTick;
        final int ticksPerCard;
        final int endTick;
        int currentCard;
        final int[] traceCounts = new int[4];
        /** 已经向 ledger 报过换卡事件的槽位；session 中途开表时也要补报一次。 */
        int markedSlot = Integer.MIN_VALUE;
        boolean cardMarked;

        Session(ArmorStand rig, TrackRunner runner, List<SpellCard> cards, float damage,
                int startTick, int ticksPerCard, int endTick) {
            this.rig = rig;
            this.runner = runner;
            this.cards = cards;
            this.damage = damage;
            this.startTick = startTick;
            this.ticksPerCard = ticksPerCard;
            this.endTick = endTick;
            this.currentCard = -1;
        }
    }

    /**
     * 一只 BOSS 的符卡表 + 它的<b>签名色盘</b>。
     *
     * <p>色盘 MUST 用 BOSS 自己的那份，理由有二：
     * <ul>
     *   <li>色相是 {@code VisualIdentity} 四轴之一——预览若换成白阶，就等于把要验的
     *       那一轴废掉了（同符卡内两轨 MUST 靠色相等至少一轴区分）</li>
     *   <li>配色本身就是符卡设计的一部分，「像不像」要能看出来</li>
     * </ul>
     */
    public record CardSet(List<SpellCard> cards, SignaturePalette palette) {
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private DanmakuPreview() {
    }

    /**
     * 开始预览。每名玩家至多一场，新开则替换旧的。
     *
     * @param cards     要依次预览的符卡
     * @param seconds   总时长（秒）
     * @param damage    单发伤害。默认 0——审图案时不该被弹幕打死；但伤害为 0 仍会走完
     *                  命中判定（弹照样撞上你并消失），故命中行为照样可验
     */
    public static void start(ServerPlayer player, CardSet set, List<SpellCard> cards,
                             int seconds, float damage) {
        stop(player);
        ServerLevel level = player.serverLevel();

        // 置于玩家正前方。弹幕的瞄准方向由 TrackRunner 从「owner 眼位 → 目标」算出，
        // 放在正前方能让图案正对镜头。
        // 下移一个眼高，使装置的<b>眼位</b>（不是脚底）落在瞄准点上——否则弹幕会
        // 从略高于视线的位置射出，画面轻微俯冲。
        Vec3 anchor = player.getEyePosition().add(player.getLookAngle().scale(8.0D));
        ArmorStand rig = new ArmorStand(EntityType.ARMOR_STAND, level);
        rig.setInvisible(true);
        rig.setNoGravity(true);
        rig.setSilent(true);
        rig.setInvulnerable(true);
        rig.setPos(anchor.x, anchor.y - rig.getEyeHeight(), anchor.z);
        level.addFreshEntity(rig);

        long now = level.getGameTime();
        int ticksPerCard = Math.max(40, seconds * 20 / Math.max(1, cards.size()));
        SESSIONS.put(player.getUUID(), new Session(
                rig, new TrackRunner(cards, set.palette()), cards, damage,
                (int) now, ticksPerCard, (int) (now + seconds * 20L)));
        Gensokyou.LOGGER.info("[danmaku-preview] START viewer={} rig={} cards={} seconds={} damage={} origin={}",
                player.getUUID(), rig.getUUID(), cards.stream().map(c -> c.name().getString()).toList(),
                seconds, damage, anchor);
    }

    /** Four samples per category/card, plus two children per sampled trail head. */
    public static void traceEmission(net.minecraft.world.entity.LivingEntity owner,
                                     com.bitsson.gensokyou.entity.AbstractDanmakuProjectile bullet) {
        if (!(owner instanceof ArmorStand)) {
            return;
        }
        for (Session session : SESSIONS.values()) {
            if (session.rig != owner) {
                continue;
            }
            int category = bullet.isTrailing() ? 0 : bullet.isBursting()
                    ? (bullet.getBbWidth() > 0.8F ? 1 : 2) : 3;
            if (session.traceCounts[category]++ < 4) {
                bullet.enablePreviewTrace(0);
            }
            return;
        }
    }

    /** 收掉一场预览（丢弃装置并写日志）。调用方负责从 {@link #SESSIONS} 移除。 */
    private static void stopSession(Session session, int now) {
        Gensokyou.LOGGER.info("[danmaku-preview] STOP rig={} gameTime={} endTick={}",
                session.rig.getUUID(), now, session.endTick);
        if (!session.rig.isRemoved()) {
            session.rig.discard();
        }
    }

    /** 停止预览并清掉装置。 */
    public static void stop(ServerPlayer player) {
        Session session = SESSIONS.remove(player.getUUID());
        if (session != null && !session.rig.isRemoved()) {
            Gensokyou.LOGGER.info("[danmaku-preview] STOP rig={} reason=replaced-or-command", session.rig.getUUID());
            session.rig.discard();
        }
    }

    public static boolean isPreviewing(ServerPlayer player) {
        return SESSIONS.containsKey(player.getUUID());
    }

    /**
     * 每世界 tick 推进所有预览。
     *
     * <p><b>必须做类型守卫。</b>{@code LevelTickEvent} 在<b>客户端也会触发</b>（见
     * {@code EventHooks#fireLevelTickPost} 由 {@code Minecraft#tick} 调起），而本类
     * 注册为双端。原先直接强转 {@code ServerLevel} 会让客户端线程抛
     * {@code ClassCastException} 而崩端——单人游戏尤其必然：集成服务端与客户端
     * <b>同 JVM</b>，静态 {@code SESSIONS} 是共用的，客户端线程必然走进本方法
     * 并拿到 {@code ClientLevel}。
     */
    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || SESSIONS.isEmpty()) {
            return;
        }
        int now = (int) level.getGameTime();

        Iterator<Map.Entry<UUID, Session>> it = SESSIONS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Session> entry = it.next();
            Session session = entry.getValue();
            ServerPlayer viewer = level.getServer().getPlayerList().getPlayer(entry.getKey());
            // 观看者不在了 / 已离开游戏：整场收掉。
            if (viewer == null || viewer.isRemoved()) {
                stopSession(session, now);
                it.remove();
                continue;
            }
            // <p><b>本维度守卫是必需的。</b>{@code LevelTickEvent.Post} 对<b>每个</b>
            // {@code ServerLevel} 各触发一次（主世界 / 下界 / 末地 / {@code gensokyou:gensokyo}…），
            // 而 {@code SESSIONS} 是跨维度共用的。若在这里直接判「观看者不在本维度 ⇒ 收掉」，
            // 那么只要<b>任意一个别的维度</b>在同一 tick 里先 tick 到，本场就会被立即收掉——
            // 现象是「预览只放了一拍就停」（lock 只见 1 颗、telegraph 只剩符印、safety 不再重发）。
            // 故本维度没有该观看者时 MUST {@code continue}（跳过），交给它自己那一维处理。
            if (viewer.level() != level) {
                continue;
            }
            if (now >= session.endTick || session.rig.isRemoved()) {
                stopSession(session, now);
                it.remove();
                continue;
            }

            // 每 ticksPerCard tick 换一张符卡
            int slot = Math.min((now - session.startTick) / session.ticksPerCard,
                    session.cards.size() - 1);
            if (slot != session.currentCard) {
                session.currentCard = slot;
                java.util.Arrays.fill(session.traceCounts, 0);
                Gensokyou.LOGGER.info("[danmaku-preview] CARD rig={} name={} gameTime={}",
                        session.rig.getUUID(), session.cards.get(slot).name().getString(), now);
                session.runner.stop();
                session.runner.selectCard(session.cards.get(slot).hpFraction());
            }

            // 换卡事件 MUST 在这里发，而不是只在上面那个 `slot != currentCard` 分支里发：
            // 预览往往在 /gs_net start 之前就开了，那时 profiling 还没启用，
            // 单卡预览的 slot 0 切换早已过去，整段窗口就会没有 card 事件
            // （现象：分析器把它归成 card None）。故按「本槽位是否已报过」判定，
            // 每次 tick 检查，开表后下一 tick 就补上。
            if (slot != session.markedSlot) {
                session.markedSlot = slot;
                session.cardMarked = false;
            }
            if (!session.cardMarked && com.bitsson.gensokyou.netprof.NetProfile.enabled()) {
                session.cardMarked = true;
                com.bitsson.gensokyou.netprof.NetProfile.onPreviewCard(
                        viewer, slot, session.cards.get(slot).name().getString());
            }
            session.runner.tick(session.rig, List.<Player>of(viewer), session.damage);
        }
    }

}
