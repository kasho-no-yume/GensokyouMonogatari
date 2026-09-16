package com.bitsson.gensokyou.ritual;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.block.entity.RitualCoreBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;

/**
 * 已成型仪式核心的 per-ServerLevel 运行时索引（纯运行时，不持久化）。
 * 注册/注销挂在 RitualCoreBlockEntity 的 20t 重扫与移除路径上；
 * 消费方为大范围候选发现（如万象共鸣之仪），替代 betweenClosed 体积扫描。
 */
@EventBusSubscriber(modid = Gensokyou.MODID)
public final class RitualCoreRegistry {

    /** 索引条目：坐标为 key，此处记注册时的图案与阶级（查询时以现场解析为准做一致性校验）。 */
    public record Entry(ResourceLocation patternId, int level) {
    }

    private static final Map<ServerLevel, RitualCoreRegistry> INSTANCES = new ConcurrentHashMap<>();

    private final Map<BlockPos, Entry> entries = new HashMap<>();

    RitualCoreRegistry() {
    }

    /** 取（必要时创建）所在维度的注册表；仅在核心存活期（serverTick）调用。 */
    public static RitualCoreRegistry forLevel(ServerLevel level) {
        return INSTANCES.computeIfAbsent(level, k -> new RitualCoreRegistry());
    }

    /** 只查询不创建：注销与外部读取用，防止关停期"注销反而复活实例"。 */
    @Nullable
    public static RitualCoreRegistry peek(ServerLevel level) {
        return INSTANCES.get(level);
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            INSTANCES.remove(level);
        }
    }

    /** 幂等登记（每 20t 重扫刷新条目值，含同坐标仪式/阶级变化的自然覆盖）。 */
    public void register(BlockPos pos, ResourceLocation patternId, int level) {
        entries.put(pos.immutable(), new Entry(patternId, level));
    }

    /** 注销，带归属守卫：仅当条目仍是 expectedPatternId 登记的这条才删除。 */
    public void unregister(BlockPos pos, ResourceLocation expectedPatternId) {
        entries.computeIfPresent(pos, (k, v) -> v.patternId().equals(expectedPatternId) ? null : v);
    }

    /**
     * 维度内范围查询：XZ 切比雪夫方形（|dx|≤r 且 |dz|≤r），Y 不限；
     * excludePatternId 非空时该图案的条目整体跳过（不参与校验，也不剔除）。
     * liveAndConsistent 现场校验（坐标处仍是成型核心且图案与条目一致），
     * 失败的条目即时剔除（惰性收敛，索引发散不可累积）。
     */
    public List<BlockPos> matchedPositions(BlockPos center, int xzRadius,
                                           @Nullable ResourceLocation excludePatternId,
                                           BiPredicate<BlockPos, Entry> liveAndConsistent) {
        List<BlockPos> out = new ArrayList<>();
        List<BlockPos> stale = null;
        for (Map.Entry<BlockPos, Entry> e : entries.entrySet()) {
            BlockPos pos = e.getKey();
            if (Math.abs(pos.getX() - center.getX()) > xzRadius
                    || Math.abs(pos.getZ() - center.getZ()) > xzRadius) {
                continue;
            }
            if (excludePatternId != null && e.getValue().patternId().equals(excludePatternId)) {
                continue;
            }
            if (liveAndConsistent.test(pos, e.getValue())) {
                out.add(pos);
            } else {
                if (stale == null) {
                    stale = new ArrayList<>();
                }
                stale.add(pos);
            }
        }
        if (stale != null) {
            stale.forEach(entries::remove);
        }
        return out;
    }

    /** 面向消费方的便捷查询：返回校验通过的成型核心实例（同维度天然由 ServerLevel 界定）。 */
    public static List<RitualCoreBlockEntity> formedWithin(ServerLevel level, BlockPos center,
                                                           int xzRadius,
                                                           @Nullable ResourceLocation excludePatternId) {
        RitualCoreRegistry registry = peek(level);
        if (registry == null) {
            return List.of();
        }
        List<RitualCoreBlockEntity> out = new ArrayList<>();
        registry.matchedPositions(center, xzRadius, excludePatternId,
                (pos, entry) -> level.getBlockEntity(pos) instanceof RitualCoreBlockEntity core
                        && core.activeMatch() != null
                        && core.activeMatch().patternId().equals(entry.patternId()))
                .forEach(pos -> {
                    if (level.getBlockEntity(pos) instanceof RitualCoreBlockEntity core) {
                        out.add(core);
                    }
                });
        return out;
    }

    /**
     * 按图案遍历该维度全部登记条目（无中心/半径）：live 现场校验与陈旧剔除语义同范围查询。
     * 消费方为跳夜结算——事件只给出维度，需枚举全部成型梦渡核心。
     */
    public static List<RitualCoreBlockEntity> formedOfPattern(ServerLevel level, ResourceLocation patternId) {
        RitualCoreRegistry registry = peek(level);
        if (registry == null) {
            return List.of();
        }
        List<RitualCoreBlockEntity> out = new ArrayList<>();
        List<BlockPos> stale = null;
        for (Map.Entry<BlockPos, Entry> e : registry.entries.entrySet()) {
            if (!e.getValue().patternId().equals(patternId)) {
                continue;
            }
            if (level.getBlockEntity(e.getKey()) instanceof RitualCoreBlockEntity core
                    && core.activeMatch() != null
                    && core.activeMatch().patternId().equals(e.getValue().patternId())) {
                out.add(core);
            } else {
                if (stale == null) {
                    stale = new ArrayList<>();
                }
                stale.add(e.getKey());
            }
        }
        if (stale != null) {
            stale.forEach(registry.entries::remove);
        }
        return out;
    }
}
