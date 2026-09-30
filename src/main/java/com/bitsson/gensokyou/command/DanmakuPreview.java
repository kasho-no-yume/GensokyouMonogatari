package com.bitsson.gensokyou.command;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.danmaku.track.BossCards;
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
import java.util.Locale;
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
    }

    /**
     * 取一个容得下所有轨道数的色盘。
     *
     * <p>{@code TrackRunner} 用 {@code palette.at(轨道序)} 取每轨颜色，容量不足会越界。
     * 各 BOSS 的正式色盘是其签名身份的一部分，预览里改成按序递减的白阶——
     * 预览要能分辨轨，而不是复现配色。
     */
    /** 停止预览并清掉装置。 */
    public static void stop(ServerPlayer player) {
        Session session = SESSIONS.remove(player.getUUID());
        if (session != null && !session.rig.isRemoved()) {
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
            if (viewer == null || viewer.isRemoved() || viewer.level() != level
                    || now >= session.endTick || session.rig.isRemoved()) {
                if (!session.rig.isRemoved()) {
                    session.rig.discard();
                }
                it.remove();
                continue;
            }

            // 每 ticksPerCard tick 换一张符卡
            int slot = Math.min((now - session.startTick) / session.ticksPerCard,
                    session.cards.size() - 1);
            if (slot != session.currentCard) {
                session.currentCard = slot;
                session.runner.stop();
                session.runner.selectCard(session.cards.get(slot).hpFraction());
            }
            session.runner.tick(session.rig, List.<Player>of(viewer), session.damage);
        }
    }

    /** 全部可预览的符卡，供命令列出。 */
    public static String describeAll() {
        StringBuilder sb = new StringBuilder();
        for (String id : new String[]{"big_fairy", "kuzumono", "kitsune_bi", "nomen_mask"}) {
            List<SpellCard> cards = setOf(id).cards();
            sb.append(id).append("  ");
            for (int i = 0; i < cards.size(); i++) {
                sb.append(i).append('=').append(cards.get(i).name().getString()).append("  ");
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    /** 按 id 取符卡表 + 其真实签名色盘；未知 id 返回 null。 */
    public static CardSet setOf(String id) {
        return switch (id.toLowerCase(Locale.ROOT)) {
            case "big_fairy", "大妖精" ->
                    new CardSet(BossCards.bigFairy(), BossCards.BIG_FAIRY_PALETTE);
            case "kuzumono", "鬼蛛" ->
                    new CardSet(BossCards.kuzumono(), BossCards.KUZUMONO_PALETTE);
            case "kitsune_bi", "kitsunebi", "狐火" ->
                    new CardSet(BossCards.kitsuneBi(), BossCards.KITSUNEBI_PALETTE);
            case "nomen_mask", "傩神楽面" ->
                    new CardSet(BossCards.nomenMask(), BossCards.NOMEN_PALETTE);
            default -> null;
        };
    }
}
