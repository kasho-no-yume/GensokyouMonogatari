package com.bitsson.gensokyou.netprof;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.danmaku.DanmakuBudget;
import com.bitsson.gensokyou.entity.AbstractTouhouBoss;
import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.fml.loading.FMLPaths;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * BOSS-fight network profiler (server side, dev facility).
 *
 * <p>Writes a JSONL run ledger next to the game directory ({@code net_profile/}). Records:
 * <ul>
 *   <li>{@code session} lines on start/stop;</li>
 *   <li>{@code summon}/{@code death}/{@code card} markers for bracketing a fight;</li>
 *   <li>{@code mark} lines for manual annotation;</li>
 *   <li>{@code pkt} lines: per player, per tick, per packet class -- packet count and
 *       <b>pre-compression</b> byte count.</li>
 * </ul>
 *
 * <p>This is the semantic half of the profile; the wire-byte half (post-compression, exact)
 * comes from {@code tools/net_jitter_proxy.py --profile}. The two are deliberately kept as
 * separate sources with separate units and reconciled offline, never mixed.
 *
 * <p>Enabled only via {@code /gs_net start}. When disabled every entry point is a no-op, so
 * the event hooks may call in unconditionally.
 */
public final class NetProfile {

    private static final String CLASS_HANDLER = "gs_net_class";
    private static final String SIZE_HANDLER = "gs_net_size";
    /** Emit a per-type concurrency snapshot every this many ticks (10 = twice a second). */
    private static final int LIVE_INTERVAL_TICKS = 10;

    private static volatile boolean enabled;
    private static BufferedWriter writer;
    private static Path file;
    private static int liveCountdown;
    private static final Map<UUID, NetChannelCounters.Counters> COUNTERS = new ConcurrentHashMap<>();

    /**
     * 上一 tick 末的弹幕 tick 成本累计读数，仅用于差分（见 {@link #emitTickCost}）。
     *
     * <p>{@code DanmakuBudget} 只保留一个累计量；{@code /gs_boss danmaku} 的
     * {@code tickTime} 因此只能给平均值。逐 tick 分布需要每 tick 一个样本，
     * 差分必须落在具体的 tick 边界上，所以读数留在这里而不是丢进统计类。
     */
    private static long lastCostNanos;
    private static long lastCostBullets;

    private NetProfile() {
    }

    public static boolean enabled() {
        return enabled;
    }
    /** Program host CPU includes advance, collisions, projection/publication and host callbacks. */
    public static void programTick(long tick,long nanos,int members) {
        if (!enabled) return;
        writeLine(raw("program_tickcost",System.currentTimeMillis())+",\"tick\":"+tick+",\"nanos\":"+nanos+",\"members\":"+members+"}");
    }

    public static Path file() {
        return file;
    }

    /** Starts a profile session; returns the file path, or null on IO failure. */
    public static synchronized String start(String label) {
        if (enabled) {
            // Two windows opened on top of each other is a silent data-loss bug: the second
            // start truncates the ledger, and (before the inject() fix) left the semantic
            // half empty. Say so instead of quietly restarting.
            Gensokyou.LOGGER.warn("[gs_net] start() while a session is already open"
                    + " -- restarting, the previous session's data is discarded");
        }
        stop();
        String stem = (label == null || label.isBlank())
                ? "server_" + System.currentTimeMillis()
                : "server_" + sanitize(label);
        Path dir = FMLPaths.GAMEDIR.get().resolve("net_profile");
        try {
            Files.createDirectories(dir);
            Path target = dir.resolve(stem + ".jsonl");
            writer = Files.newBufferedWriter(target, StandardCharsets.UTF_8);
            file = target;
        } catch (IOException exc) {
            Gensokyou.LOGGER.error("[gs_net] cannot open profile file", exc);
            writer = null;
            file = null;
            return null;
        }
        enabled = true;
        // 重新对齐差分基线：DanmakuBudget 的累计量在两次会话之间照常增长，
        // 不重设的话开表后第一行 tickcost 会把整段间隔算成「上一 tick」。
        lastCostNanos = DanmakuBudget.tickNanosTotal();
        lastCostBullets = DanmakuBudget.tickedBulletsTotal();
        writeLine(raw("session", System.currentTimeMillis())
                + ",\"event\":\"start\",\"label\":" + quote(label) + "}");
        return file.toString();
    }

    /** Stops the session, flushing everything. Safe to call when already stopped. */
    public static synchronized void stop() {
        if (enabled) {
            writeLine(raw("session", System.currentTimeMillis()) + ",\"event\":\"stop\"}");
        }
        enabled = false;
        if (writer != null) {
            try {
                writer.flush();
                writer.close();
            } catch (IOException ignored) {
                // best effort
            }
            writer = null;
        }
        // COUNTERS MUST NOT be cleared here. The pipeline handlers installed by inject()
        // capture a Counters reference at install time and keep feeding it for the life of
        // the connection. Clearing the map orphaned those objects: the next start()'s
        // inject() hit the "handler already installed" path, onTick then drained a fresh
        // empty map, and the ledger ended up with ZERO pkt/live lines while tickcost (which
        // is global) looked perfectly healthy. That is exactly the 2026-10-16 21:25
        // flower_r1 run. Keeping the objects also keeps liveByType accurate across sessions,
        // because the handlers maintain it continuously rather than per session.
        // Only the stale byte counts are dropped, so a new session starts from zero.
        for (NetChannelCounters.Counters counters : COUNTERS.values()) {
            counters.drain();
        }
        liveCountdown = 0;
    }

    public static void mark(String label) {
        if (!enabled) {
            return;
        }
        writeLine(raw("mark", System.currentTimeMillis()) + ",\"label\":" + quote(label) + "}");
    }

    public static void onSummon(Entity boss) {
        if (!enabled) {
            return;
        }
        writeLine(raw("summon", System.currentTimeMillis()) + bossTick(boss)
                + ",\"boss\":" + quote(boss.getType().toString()) + "}");
    }

    public static void onDeath(Entity boss) {
        if (!enabled) {
            return;
        }
        writeLine(raw("death", System.currentTimeMillis()) + bossTick(boss)
                + ",\"boss\":" + quote(boss.getType().toString()) + "}");
    }

    public static void onCardChanged(AbstractTouhouBoss boss, int index) {
        if (!enabled) {
            return;
        }
        writeLine(raw("card", System.currentTimeMillis()) + bossTick(boss)
                + ",\"boss\":" + quote(boss.getType().toString())
                + ",\"card\":" + index + "}");
    }

    /** Injects the pipeline counter for a player and (if enabled) logs the join. */
    public static void onLoggedIn(ServerPlayer player) {
        inject(player);
        if (!enabled) {
            return;
        }
        writeLine(raw("join", System.currentTimeMillis())
                + ",\"tick\":" + player.level().getGameTime()
                + ",\"player\":" + quote(player.getUUID().toString()) + "}");
    }

    public static void inject(ServerPlayer player) {
        if (!enabled) {
            return;
        }
        // The per-player Counters entry is what onTick drains, so it MUST exist even when
        // the pipeline handlers are already in place from an earlier session.
        // Returning before this point made the SECOND `gs_net start` on the same connection
        // produce a ledger with zero `pkt`/`live` lines -- the handlers were installed on the
        // first start, COUNTERS was then cleared by stop(), and inject() bailed out on the
        // "already installed" check. Observed on the 2026-10-16 21:25 flower_r1 run.
        NetChannelCounters.Counters counters =
                COUNTERS.computeIfAbsent(player.getUUID(), k -> new NetChannelCounters.Counters());
        Channel channel = player.connection.getConnection().channel();
        ChannelPipeline pipeline = channel.pipeline();
        if (pipeline.get(CLASS_HANDLER) != null) {
            return;
        }
        try {
            pipeline.addAfter("encoder", CLASS_HANDLER,
                    new NetChannelCounters.ClassHandler(counters));
            pipeline.addBefore("encoder", SIZE_HANDLER,
                    new NetChannelCounters.SizeHandler(counters));
        } catch (RuntimeException exc) {
            Gensokyou.LOGGER.error("[gs_net] pipeline inject failed (no 'encoder'?)", exc);
        }
    }

    public static void onTick(MinecraftServer server) {
        if (!enabled) {
            return;
        }
        // 逐 tick 成本是全局的（不属于某个玩家），故在 COUNTERS 判空之前先落笔：
        // 采样窗口里必然有玩家在线，但把两件事解耦后，「没有玩家时成本是多少」也读得出来。
        emitTickCost(server);
        if (COUNTERS.isEmpty()) {
            return;
        }
        long tick = server.getTickCount();
        long wall = System.currentTimeMillis();
        boolean emitLive = ++liveCountdown >= LIVE_INTERVAL_TICKS;
        if (emitLive) {
            liveCountdown = 0;
        }
        for (Map.Entry<UUID, NetChannelCounters.Counters> entry : COUNTERS.entrySet()) {
            Map<String, long[]> drained = entry.getValue().drain();
            for (Map.Entry<String, long[]> bucket : drained.entrySet()) {
                long[] value = bucket.getValue();
                writeLine(raw("pkt", wall)
                        + ",\"tick\":" + tick
                        + ",\"player\":" + quote(entry.getKey().toString())
                        + ",\"class\":" + quote(bucket.getKey())
                        + ",\"bytes\":" + value[0]
                        + ",\"packets\":" + value[1] + "}");
            }
            if (emitLive) {
                Map<String, Long> live = entry.getValue().snapshotLive();
                if (!live.isEmpty()) {
                    StringBuilder json = new StringBuilder(raw("live", wall))
                            .append(",\"tick\":").append(tick)
                            .append(",\"player\":").append(quote(entry.getKey().toString()))
                            .append(",\"live\":{");
                    boolean first = true;
                    for (Map.Entry<String, Long> type : live.entrySet()) {
                        if (!first) {
                            json.append(',');
                        }
                        first = false;
                        json.append(quote(type.getKey())).append(':').append(type.getValue());
                    }
                    writeLine(json.append("}}").toString());
                }
            }
        }
    }

    /**
     * 一行 {@code tickcost}：把「本 tick 的弹幕成本」单独落一笔。
     *
     * <p>这是 {@code /gs_boss danmaku} 的 {@code tickTime} 永远给不出的东西——
     * 后者是累计均值，合计成总量也推不出 p95。此处每 tick 一条，离线即可算
     * 中位数 / p95。{@code bullets} 是同一 tick 里参与计时的弹幕数，
     * 与 {@code nanos} 同源，故两者的比值仍可读。
     */
    private static void emitTickCost(MinecraftServer server) {
        long nanos = DanmakuBudget.tickNanosTotal();
        long bullets = DanmakuBudget.tickedBulletsTotal();
        long deltaNanos = nanos - lastCostNanos;
        long deltaBullets = bullets - lastCostBullets;
        // /gs_boss danmaku reset 会在会话中途把累计量清零。清零后差分是负的，
        // 记成 0 并打上标记，让分析端知道这一 tick 只是断了，不是「变快了」。
        boolean reset = deltaNanos < 0L || deltaBullets < 0L;
        if (reset) {
            deltaNanos = 0L;
            deltaBullets = 0L;
        }
        lastCostNanos = nanos;
        lastCostBullets = bullets;
        writeLine(raw("tickcost", System.currentTimeMillis())
                + ",\"tick\":" + server.getTickCount()
                + ",\"nanos\":" + deltaNanos
                + ",\"bullets\":" + deltaBullets
                + ",\"reset\":" + (reset ? 1 : 0) + "}");
    }

    /**
     * 预览源的换卡标记。预览装置是 {@code ArmorStand}，没有
     * {@link AbstractTouhouBoss}，故 {@link #onCardChanged} 永远叫不到；
     * 这里补一发同键名的 {@code card} 行，离线分析器就能照常按卡分段
     * （否则整段窗口会被归成 {@code card None}）。
     */
    public static void onPreviewCard(ServerPlayer player, int index, String name) {
        if (!enabled) {
            return;
        }
        writeLine(raw("card", System.currentTimeMillis())
                + ",\"tick\":" + player.level().getGameTime()
                + ",\"boss\":" + quote("gensokyou:preview")
                + ",\"card\":" + index
                + ",\"name\":" + quote(name) + "}");
    }

    private static String raw(String type, long wall) {
        return "{\"wall\":" + wall + ",\"type\":" + quote(type);
    }

    private static String bossTick(Entity boss) {
        return ",\"tick\":" + boss.level().getGameTime();
    }

    private static String quote(String value) {
        if (value == null) {
            return "null";
        }
        StringBuilder out = new StringBuilder(value.length() + 2);
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }

    private static String sanitize(String label) {
        StringBuilder out = new StringBuilder(label.length());
        for (int i = 0; i < label.length(); i++) {
            char c = label.charAt(i);
            out.append(Character.isLetterOrDigit(c) || c == '-' || c == '_' ? c : '_');
        }
        return out.toString();
    }

    private static synchronized void writeLine(String json) {
        if (writer == null) {
            return;
        }
        try {
            writer.write(json);
            writer.newLine();
            writer.flush();
        } catch (IOException exc) {
            Gensokyou.LOGGER.error("[gs_net] write failed", exc);
            enabled = false;
        }
    }
}
