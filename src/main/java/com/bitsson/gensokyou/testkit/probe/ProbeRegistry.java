package com.bitsson.gensokyou.testkit.probe;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 探针注册表：探针 id → 探针实现。
 *
 * <p>注册表是**扩展点**：确有必要时可新增专用探针（走同一条注册路径），
 * 但 MUST NOT 把「为每个功能写一个专用探针」当作常态做法——
 * 通用探针（区域方块计数 / 区域实体计数 + 包围盒）能表达大多数验收项。
 */
public final class ProbeRegistry {

    private static final Map<String, Probe> PROBES = new LinkedHashMap<>();

    private ProbeRegistry() {
    }

    public static void register(Probe probe) {
        PROBES.put(probe.id(), probe);
    }

    public static Optional<Probe> find(String id) {
        return Optional.ofNullable(PROBES.get(id));
    }

    public static Collection<String> ids() {
        return PROBES.keySet();
    }
}
