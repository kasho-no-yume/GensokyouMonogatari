package com.bitsson.gensokyou.testkit.probe;

import com.bitsson.gensokyou.block.entity.RitualCoreBlockEntity;
import com.bitsson.gensokyou.ritual.RitualMatch;
import com.bitsson.gensokyou.spirit.ModAttachments;
import com.bitsson.gensokyou.spirit.SpiritPowerData;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 内置**通用**探针。
 *
 * <p>设计取向：通用优先。绝大多数验收项（「球半径 N 格」「祭品是否当场消失」
 * 「光柱是否被 48 格截断」）本质都是**区域内的方块/实体分布与包围盒**问题，
 * 用 {@code blocks} 与 {@code entities} 两个通用读数即可表达，零新增 Java。
 * 确有必要时再走 {@link ProbeRegistry} 加专用探针。
 *
 * <p>全部探针 MUST 只读。
 */
public final class BuiltinProbes {

    /** 单次 blocks 采样允许扫描的最大格数（超出即截断并在输出里标注）。 */
    private static final int MAX_BLOCK_SCAN = 500_000;
    /** entities 探针列出的位置明细上限（超出即截断并在输出里标注）。 */
    private static final int MAX_ENTITY_POSITIONS = 64;

    private BuiltinProbes() {
    }

    public static void registerAll() {
        ProbeRegistry.register(world());
        ProbeRegistry.register(spirit());
        ProbeRegistry.register(entities());
        ProbeRegistry.register(blocks());
        ProbeRegistry.register(ritual());
    }

    // ---------------------------------------------------------------- world

    private static Probe world() {
        return new Probe() {
            @Override
            public String id() {
                return "world";
            }

            @Override
            public JsonObject run(ProbeContext ctx) {
                ServerLevel level = ctx.level();
                JsonObject o = base("world");
                o.addProperty("game_time", level.getGameTime());
                o.addProperty("day_time", level.getDayTime());
                o.addProperty("dimension", level.dimension().location().toString());
                // 出生点：场景需要一个「区块一定已加载」的稳定坐标，
                // 而出生区块正是服务端唯一保证常驻加载的区块。
                net.minecraft.core.BlockPos spawn = level.getSharedSpawnPos();
                o.add("spawn", pos3(spawn.getX(), spawn.getY(), spawn.getZ()));
                JsonArray players = new JsonArray();
                for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
                    players.add(p.getGameProfile().getName());
                }
                o.add("players", players);
                o.addProperty("player_count", players.size());
                return o;
            }
        };
    }

    // --------------------------------------------------------------- spirit

    private static Probe spirit() {
        return new Probe() {
            @Override
            public String id() {
                return "spirit";
            }

            @Override
            public JsonObject run(ProbeContext ctx) {
                JsonObject o = base("spirit");
                ServerPlayer p = ctx.player();
                if (p == null) {
                    o.addProperty("error", "no player online");
                    return o;
                }
                SpiritPowerData d = ModAttachments.get(p);
                o.addProperty("player", p.getGameProfile().getName());
                o.addProperty("current", d.current());
                o.addProperty("max", d.max());
                o.addProperty("temper_level", d.temperLevel());
                o.addProperty("spirit_damage", d.spiritDamage());
                o.addProperty("infinite", ModAttachments.isTestInfiniteSpirit(p));
                o.add("position", pos3(p.getX(), p.getY(), p.getZ()));
                return o;
            }
        };
    }

    // ------------------------------------------------------------- entities

    private static Probe entities() {
        return new Probe() {
            @Override
            public String id() {
                return "entities";
            }

            @Override
            public JsonObject run(ProbeContext ctx) {
                ServerLevel level = ctx.level();
                JsonObject o = base("entities");
                ServerPlayer self = ctx.player();
                BlockPos center = ctx.argPos("center", self == null ? BlockPos.ZERO : self.blockPosition());
                double radius = ctx.argDouble("radius", 16.0);

                ResourceLocation filter = null;
                if (ctx.has("type")) {
                    filter = ResourceLocation.tryParse(ctx.arg("type"));
                    if (filter == null) {
                        o.addProperty("error", "bad type id: " + ctx.arg("type"));
                        return o;
                    }
                }

                o.add("center", pos3(center.getX(), center.getY(), center.getZ()));
                o.addProperty("radius", radius);
                o.addProperty("type_filter", filter == null ? "null" : filter.toString());

                double r2 = radius * radius;
                Map<String, Integer> types = new LinkedHashMap<>();
                List<Entity> hits = new ArrayList<>();
                for (Entity e : level.getEntities().getAll()) {
                    if (e instanceof ServerPlayer sp && sp.isSpectator()) {
                        continue;
                    }
                    double dx = e.getX() - center.getX();
                    double dy = e.getY() - center.getY();
                    double dz = e.getZ() - center.getZ();
                    if (dx * dx + dy * dy + dz * dz > r2) {
                        continue;
                    }
                    ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType());
                    if (filter != null && !filter.equals(key)) {
                        continue;
                    }
                    hits.add(e);
                    types.merge(key.toString(), 1, Integer::sum);
                }

                o.addProperty("count", hits.size());
                JsonObject typeObj = new JsonObject();
                types.forEach(typeObj::addProperty);
                o.add("types", typeObj);

                if (hits.isEmpty()) {
                    o.add("bbox", new JsonObject());
                } else {
                    double minX = hits.getFirst().getX();
                    double minY = hits.getFirst().getY();
                    double minZ = hits.getFirst().getZ();
                    double maxX = minX;
                    double maxY = minY;
                    double maxZ = minZ;
                    for (Entity e : hits) {
                        minX = Math.min(minX, e.getX());
                        minY = Math.min(minY, e.getY());
                        minZ = Math.min(minZ, e.getZ());
                        maxX = Math.max(maxX, e.getX());
                        maxY = Math.max(maxY, e.getY());
                        maxZ = Math.max(maxZ, e.getZ());
                    }
                    JsonObject bbox = new JsonObject();
                    bbox.add("min", pos3(minX, minY, minZ));
                    bbox.add("max", pos3(maxX, maxY, maxZ));
                    bbox.addProperty("size_x", round(maxX - minX));
                    bbox.addProperty("size_y", round(maxY - minY));
                    bbox.addProperty("size_z", round(maxZ - minZ));
                    o.add("bbox", bbox);
                }

                JsonArray positions = new JsonArray();
                int listed = Math.min(hits.size(), MAX_ENTITY_POSITIONS);
                for (int i = 0; i < listed; i++) {
                    Entity e = hits.get(i);
                    positions.add(pos3(e.getX(), e.getY(), e.getZ()));
                }
                o.add("positions", positions);
                o.addProperty("positions_truncated", hits.size() > listed);
                return o;
            }
        };
    }

    // --------------------------------------------------------------- blocks

    private static Probe blocks() {
        return new Probe() {
            @Override
            public String id() {
                return "blocks";
            }

            @Override
            public JsonObject run(ProbeContext ctx) {
                ServerLevel level = ctx.level();
                JsonObject o = base("blocks");
                ServerPlayer self = ctx.player();
                BlockPos center = ctx.argPos("center", self == null ? BlockPos.ZERO : self.blockPosition());

                BlockPos lo;
                BlockPos hi;
                if (ctx.has("from") && ctx.has("to")) {
                    BlockPos a = ctx.argPos("from", center);
                    BlockPos b = ctx.argPos("to", center);
                    lo = new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()),
                            Math.min(a.getZ(), b.getZ()));
                    hi = new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()),
                            Math.max(a.getZ(), b.getZ()));
                } else {
                    int radius = Math.max(0, ctx.argInt("radius", 8));
                    lo = center.offset(-radius, -radius, -radius);
                    hi = center.offset(radius, radius, radius);
                }

                long volume = ((long) hi.getX() - lo.getX() + 1)
                        * ((long) hi.getY() - lo.getY() + 1)
                        * ((long) hi.getZ() - lo.getZ() + 1);

                o.add("from", pos3(lo.getX(), lo.getY(), lo.getZ()));
                o.add("to", pos3(hi.getX(), hi.getY(), hi.getZ()));
                o.addProperty("volume", volume);

                Map<String, Integer> counts = new LinkedHashMap<>();
                int nonAir = 0;
                long scanned = 0;
                int skippedChunks = 0;
                boolean truncated = false;

                int minCX = lo.getX() >> 4;
                int maxCX = hi.getX() >> 4;
                int minCZ = lo.getZ() >> 4;
                int maxCZ = hi.getZ() >> 4;

                outer:
                for (int cx = minCX; cx <= maxCX; cx++) {
                    for (int cz = minCZ; cz <= maxCZ; cz++) {
                        if (!level.isLoaded(new BlockPos(cx << 4, lo.getY(), cz << 4))) {
                            skippedChunks++;
                            continue;
                        }
                        int x0 = Math.max(lo.getX(), cx << 4);
                        int x1 = Math.min(hi.getX(), (cx << 4) + 15);
                        int z0 = Math.max(lo.getZ(), cz << 4);
                        int z1 = Math.min(hi.getZ(), (cz << 4) + 15);
                        for (int x = x0; x <= x1; x++) {
                            for (int z = z0; z <= z1; z++) {
                                for (int y = lo.getY(); y <= hi.getY(); y++) {
                                    if (scanned >= MAX_BLOCK_SCAN) {
                                        truncated = true;
                                        break outer;
                                    }
                                    scanned++;
                                    BlockState st = level.getBlockState(new BlockPos(x, y, z));
                                    String id = BuiltInRegistries.BLOCK.getKey(st.getBlock()).toString();
                                    counts.merge(id, 1, Integer::sum);
                                    if (!st.isAir()) {
                                        nonAir++;
                                    }
                                }
                            }
                        }
                    }
                }

                JsonObject countObj = new JsonObject();
                counts.forEach(countObj::addProperty);
                o.add("counts", countObj);
                o.addProperty("non_air", nonAir);
                o.addProperty("scanned", scanned);
                o.addProperty("skipped_chunks", skippedChunks);
                o.addProperty("truncated", truncated);
                return o;
            }
        };
    }

    // --------------------------------------------------------------- ritual

    private static Probe ritual() {
        return new Probe() {
            @Override
            public String id() {
                return "ritual";
            }

            @Override
            public JsonObject run(ProbeContext ctx) {
                ServerLevel level = ctx.level();
                JsonObject o = base("ritual");
                ServerPlayer self = ctx.player();
                BlockPos pos = ctx.argPos("pos", self == null ? BlockPos.ZERO : self.blockPosition());
                o.add("pos", pos3(pos.getX(), pos.getY(), pos.getZ()));

                if (!(level.getBlockEntity(pos) instanceof RitualCoreBlockEntity core)) {
                    o.addProperty("error", "not a ritual core");
                    o.addProperty("is_core", false);
                    return o;
                }
                o.addProperty("is_core", true);
                RitualMatch m = core.activeMatch();
                o.addProperty("pattern", m == null ? null : m.patternId().toString());
                o.addProperty("level", m == null ? -1 : m.level());
                o.addProperty("tier", m == null ? -1 : m.ritualTier());
                o.addProperty("stored", core.getStored());
                o.addProperty("capacity", core.getCapacity());
                o.addProperty("enabled", core.isEnabled());
                o.addProperty("age_ticks", core.ageTicks());
                o.addProperty("summon_phase", core.summonPhase().name());
                o.addProperty("summon_elapsed", core.summonElapsed());
                o.addProperty("summon_active", core.summonActive());
                return o;
            }
        };
    }

    // ---------------------------------------------------------------- utils

    private static JsonObject base(String id) {
        JsonObject o = new JsonObject();
        o.addProperty("probe", id);
        return o;
    }

    private static JsonArray pos3(double x, double y, double z) {
        JsonArray a = new JsonArray();
        a.add(round(x));
        a.add(round(y));
        a.add(round(z));
        return a;
    }

    /** 坐标保留 3 位小数（避免浮点噪声让断言抖动）。 */
    private static double round(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }
}
