package com.bitsson.gensokyou.netprof;

import com.bitsson.gensokyou.Gensokyou;
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

    private NetProfile() {
    }

    public static boolean enabled() {
        return enabled;
    }

    public static Path file() {
        return file;
    }

    /** Starts a profile session; returns the file path, or null on IO failure. */
    public static synchronized String start(String label) {
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
        COUNTERS.clear();
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
        Channel channel = player.connection.getConnection().channel();
        ChannelPipeline pipeline = channel.pipeline();
        if (pipeline.get(CLASS_HANDLER) != null) {
            return;
        }
        NetChannelCounters.Counters counters =
                COUNTERS.computeIfAbsent(player.getUUID(), k -> new NetChannelCounters.Counters());
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
        if (!enabled || COUNTERS.isEmpty()) {
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
