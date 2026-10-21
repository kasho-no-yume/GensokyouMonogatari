package com.bitsson.gensokyou.testkit.probe;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collections;
import java.util.Map;

/**
 * 探针执行上下文：来源命令栈、所在维度、解析后的参数。
 *
 * <p>参数键一律小写。坐标写法 {@code "x,y,z"}（逗号或空格分隔皆可）。
 */
public final class ProbeContext {

    private final CommandSourceStack source;
    private final ServerLevel level;
    private final Map<String, String> args;

    public ProbeContext(CommandSourceStack source, ServerLevel level, Map<String, String> args) {
        this.source = source;
        this.level = level;
        this.args = args == null ? Map.of() : args;
    }

    public CommandSourceStack source() {
        return source;
    }

    public ServerLevel level() {
        return level;
    }

    public Map<String, String> args() {
        return Collections.unmodifiableMap(args);
    }

    public boolean has(String key) {
        return args.containsKey(key);
    }

    public String arg(String key, String fallback) {
        String v = args.get(key);
        return v == null ? fallback : v;
    }

    public String arg(String key) {
        return arg(key, null);
    }

    /** 解析浮点参数；缺失或非法时回落默认值（不抛异常——探针不该因为参数写错就崩掉整个场景）。 */
    public double argDouble(String key, double fallback) {
        String v = args.get(key);
        if (v == null) {
            return fallback;
        }
        try {
            return Double.parseDouble(v.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public int argInt(String key, int fallback) {
        String v = args.get(key);
        if (v == null) {
            return fallback;
        }
        try {
            return (int) Math.floor(Double.parseDouble(v.trim()));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public boolean argBool(String key, boolean fallback) {
        String v = args.get(key);
        if (v == null) {
            return fallback;
        }
        String t = v.trim().toLowerCase(java.util.Locale.ROOT);
        if (t.equals("true") || t.equals("1") || t.equals("yes")) {
            return true;
        }
        if (t.equals("false") || t.equals("0") || t.equals("no")) {
            return false;
        }
        return fallback;
    }

    /** 解析 {@code "x,y,z"} 或 {@code "x y z"} 坐标；缺失或非法时回落默认值。 */
    public BlockPos argPos(String key, BlockPos fallback) {
        String v = args.get(key);
        if (v == null) {
            return fallback;
        }
        String[] parts = v.split("[,\\s]+");
        if (parts.length < 3) {
            return fallback;
        }
        try {
            return new BlockPos(
                    (int) Math.floor(Double.parseDouble(parts[0].trim())),
                    (int) Math.floor(Double.parseDouble(parts[1].trim())),
                    (int) Math.floor(Double.parseDouble(parts[2].trim())));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /**
     * 解析目标玩家：显式 {@code player} 参数 → 发起者本人（玩家执行时）→ 在线玩家中的第一个。
     *
     * @return 无玩家在线时返回 null（探针据此写 {@code error} 字段）
     */
    public ServerPlayer player() {
        String name = args.get("player");
        if (name != null && !name.isBlank()) {
            return level.getServer().getPlayerList().getPlayerByName(name);
        }
        if (source.getEntity() instanceof ServerPlayer sp) {
            return sp;
        }
        var list = level.getServer().getPlayerList().getPlayers();
        return list.isEmpty() ? null : list.getFirst();
    }
}
