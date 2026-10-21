package com.bitsson.gensokyou.testkit.scenario;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 活动场景表 + tick 驱动。
 *
 * <p>场景由服务端 tick 推进（{@link ServerTickEvent.Post}），
 * 故等待期间世界照常运行，且时序判定用的是服务端自己的时钟。
 *
 * <p>同名场景 MUST NOT 并发运行——报告是唯一结论出口，同名并发会让「报告写哪个」不确定。
 */
@EventBusSubscriber(modid = com.bitsson.gensokyou.Gensokyou.MODID)
public final class ScenarioManager {

    private static final Map<String, ScenarioRunner> ACTIVE = new LinkedHashMap<>();

    private ScenarioManager() {
    }

    /** @return false 表示同名场景已在运行 */
    public static boolean start(ScenarioRunner runner) {
        synchronized (ACTIVE) {
            if (ACTIVE.containsKey(runner.name())) {
                return false;
            }
            ACTIVE.put(runner.name(), runner);
            return true;
        }
    }

    public static boolean abort(String name) {
        synchronized (ACTIVE) {
            return ACTIVE.remove(name) != null;
        }
    }

    public static Collection<String> active() {
        synchronized (ACTIVE) {
            return List.copyOf(ACTIVE.keySet());
        }
    }

    public static Optional<ScenarioRunner> get(String name) {
        synchronized (ACTIVE) {
            return Optional.ofNullable(ACTIVE.get(name));
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        List<ScenarioRunner> runners;
        synchronized (ACTIVE) {
            if (ACTIVE.isEmpty()) {
                return;
            }
            runners = List.copyOf(ACTIVE.values());
        }
        for (ScenarioRunner runner : runners) {
            runner.tick();
            if (runner.isFinished()) {
                synchronized (ACTIVE) {
                    ACTIVE.remove(runner.name(), runner);
                }
            }
        }
    }
}
