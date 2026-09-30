package com.bitsson.gensokyou.entity;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBossEventPacket;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * 东方 BOSS 的血条：<b>用实体自己的 UUID 当血条 id</b>的极简 {@link ServerBossEvent} 替身。
 *
 * <h2>为什么不能直接用 {@code ServerBossEvent}</h2>
 * 它的三参构造是
 * <pre>super(Mth.createInsecureUUID(), name, color, overlay)</pre>
 * ——血条带的是<b>血条自己随机生成的 UUID</b>，与实体 UUID 无关。而原版协议
 * {@code ClientboundBossEventPacket} 从服务端只送
 * {@code (barUUID, name, color, overlay, progress)}，<b>没有任何字段能把血条连回实体</b>。
 *
 * <p>于是客户端拿到的 {@code LerpingBossEvent.getId()} 与任何实体的 UUID 都不相等，
 * 血条就没法被识别成"某只特定实体的血条"。本项目的咒符条判别正是靠这个 UUID
 * （见 {@code TouhouBossBarRenderer#touhouBossOf}），所以<b>用原版 {@code ServerBossEvent}
 * 时咒符条从不生效</b>——它一直在画原版条。
 *
 * <h2>本类做了什么</h2>
 * 自己持有一个 {@link BossEvent}（构造时传入<b>实体 UUID</b>），并自己发那五种包。
 * 服务端能决定 barUUID 的唯一位置就是发包那一刻，{@code ServerBossEvent} 不给这个口子，
 * 所以只能自己发。行为照抄原版：进度/名字变化才发包、受众集合自己记、逐玩家单发。
 *
 * <p><b>不做什么</b>：不管理可见性开关、不播 BOSS 音乐、不加世界雾——原版那些
 * {@code darkenScreen / playBossMusic / createWorldFog} 对本项目全是不需要的行为。
 * 真要那些，得改成持有 {@link ServerBossEvent} 并只覆盖 id，那条路走不通（id 是 final 且由父类构造）。
 *
 * <h2>UUID MUST 懒解析——否则读档后血条就废了</h2>
 * {@code Entity.load()} 会用存档里的 {@code "UUID"} <b>覆盖</b> {@code this.uuid}
 * （原版 {@code Entity.java}：{@code if (compound.hasUUID("UUID")) this.uuid = ...}）。
 * 也就是说实体的 UUID 在构造之后<b>还会再变一次</b>：
 * <pre>
 *   新生成   构造器生成 A → 本类抓到 A → 实体 UUID = A    ✓
 *   读档后   构造器生成 B → 本类抓到 B → load() 改成 A    ✗ 血条 id=B ≠ 实体 A
 * </pre>
 * 后者会让客户端再也认不回这只实体，血条静默退回原版——而且<b>不报错</b>：
 * 血条照常出现，只是没人认领它。故 {@link #event()} 在<b>第一次真正要用</b>时才建，
 * 那时 {@code startSeenByPlayer} / {@code aiStep} 都已跑过，UUID 早已稳定。
 */
public final class TouhouBossBar {

    /** {@link BossEvent} 是抽象类，而它的 {@code (UUID, …)} 构造是 public 的——只需一个空具体子类。 */
    private static final class Event extends BossEvent {
        Event(UUID id, Component name, BossBarColor color, BossBarOverlay overlay) {
            super(id, name, color, overlay);
        }
    }

    private final Entity owner;
    private final Component initialName;
    private final BossEvent.BossBarColor color;
    private final BossEvent.BossBarOverlay overlay;
    /** 懒建：见类注释「UUID MUST 懒解析」。 */
    private Event event;
    /** LinkedHashSet：与原版一致，去重且保插入序（广播顺序可复现）。 */
    private final Set<ServerPlayer> players = new LinkedHashSet<>();

    public TouhouBossBar(Entity owner, Component name,
                         BossEvent.BossBarColor color, BossEvent.BossBarOverlay overlay) {
        this.owner = owner;
        this.initialName = name;
        this.color = color;
        this.overlay = overlay;
    }

    /**
     * 懒建底层 {@link BossEvent}，此时 {@code owner.getUUID()} 已是最终值。
     *
     * <p>{@link BossEvent#getId()} 是 final 且只在构造时定，故只能整建一次。
     */
    private Event event() {
        if (event == null) {
            event = new Event(owner.getUUID(), initialName, color, overlay);
        }
        return event;
    }

    /** 血条 id == 实体 UUID（懒解析）。客户端判别咒符条靠的就是这个值。 */
    public UUID id() {
        return owner.getUUID();
    }

    public Component name() {
        return event().getName();
    }

    /** 该实体当前在场的受众（= 正在追踪它的玩家）。 */
    public Set<ServerPlayer> players() {
        return players;
    }

    /** 开始向该玩家呈现这根血条。已在受众里则无操作（与原版 {@code addPlayer} 同语义）。 */
    public void addPlayer(ServerPlayer player) {
        if (players.add(player)) {
            player.connection.send(ClientboundBossEventPacket.createAddPacket(event()));
        }
    }

    /** 停止向该玩家呈现。 */
    public void removePlayer(ServerPlayer player) {
        if (players.remove(player)) {
            player.connection.send(ClientboundBossEventPacket.createRemovePacket(id()));
        }
    }

    public void removeAllPlayers() {
        for (ServerPlayer player : Set.copyOf(players)) {
            removePlayer(player);
        }
    }

    /** 血量占比。<b>值未变则不发包</b>——每 tick 都调，不去重会变成持续带宽。 */
    public void setProgress(float progress) {
        Event e = event();
        if (progress != e.getProgress()) {
            e.setProgress(progress);
            broadcast(ClientboundBossEventPacket::createUpdateProgressPacket);
        }
    }

    public void setName(Component name) {
        Event e = event();
        if (!name.equals(e.getName())) {
            e.setName(name);
            broadcast(ClientboundBossEventPacket::createUpdateNamePacket);
        }
    }

    private void broadcast(Function<BossEvent, ClientboundBossEventPacket> packet) {
        ClientboundBossEventPacket built = packet.apply(event());
        for (ServerPlayer player : players) {
            player.connection.send(built);
        }
    }
}
