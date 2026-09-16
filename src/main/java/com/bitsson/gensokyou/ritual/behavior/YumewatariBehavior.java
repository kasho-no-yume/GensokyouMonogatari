package com.bitsson.gensokyou.ritual.behavior;

import com.bitsson.gensokyou.block.entity.RitualCoreBlockEntity;
import com.bitsson.gensokyou.config.GensokyouConfig;
import com.bitsson.gensokyou.network.InfoLine;
import com.bitsson.gensokyou.network.ModNetworking;
import com.bitsson.gensokyou.ritual.RitualBehavior;
import com.bitsson.gensokyou.ritual.RitualMatch;
import com.bitsson.gensokyou.spirit.SpiritCoreItem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/**
 * 梦渡之座：被动型跳夜结算仪式（成型即生效，无启动态、无配方）。
 *
 * 维度内因玩家睡眠达标而跳过时间（夜晚或雷雨白天，{@code SleepFinishedTimeEvent}）的当 tick，
 * 扫描结构 XZ 包围盒 × 核心等高平面内的全部合规床（{@code #minecraft:beds}，头脚双格均在
 * 盒内、以床头格去重），床上正在睡觉的玩家/村民每只一次性产出
 * {@code PRODUCTION_PER_SLEEPER × 4^等级} 灵力：先经普通 receive 填缓存
 * （上限 {@code BASE_CAPACITY × 4^等级}，路由抽取走 extractRouted 账本、与本路径无关），
 * 溢出部分若槽内装有灵力核心则不限速直注（仿 refundCached），余量作废。
 * 供灵速率 {@code OUT_RATE_PER_SECOND} 固定不随阶级。床数不设硬上限（可用空间即天然上限）。
 */
public class YumewatariBehavior implements RitualBehavior {

    /** 一张合规床及其当前占用者（结算 tick 快照，空床 sleeper=null）。 */
    public record Bed(BlockPos head, BlockPos foot, Entity sleeper) {
    }

    /** 结算结果（调试命令回显；事件侧忽略）。 */
    public record Settlement(int sleepers, long unit, long produced,
                             long toCache, long toCore, long discarded) {
    }

    /** 扫描范围：结构全量格位的 XZ 包围盒 + 核心 Y 平面（y 为绝对层）。 */
    public record Rect(int minX, int maxX, int minZ, int maxZ, int y) {
        boolean contains(int x, int z) {
            return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
        }
    }

    @Override
    public long spiritOutRatePerSecond(ServerLevel level, BlockPos corePos,
                                       RitualMatch match, RitualCoreBlockEntity core) {
        return GensokyouConfig.YUMEWATARI_OUT_RATE_PER_SECOND.get().longValue();
    }

    /** 产能不能动：成型即每秒把缓存灵力按核心注灵速率自发搬进槽内灵力核心（无核心时零副作用）。 */
    @Override
    public void serverPassiveTick(ServerLevel level, BlockPos corePos, RitualMatch match,
                                  RitualCoreBlockEntity core) {
        if (core.ageTicks() % 20 == 0L) {
            core.tickBatteryAutoFill();
        }
    }

    /** 可见信息仅一行合规床；数值不占栏（调试命令有全量），下方固定放由来诗。键显式列举——拼接前缀会被 lang_audit 当字面量误报。 */
    private static final String[] LORE_KEYS = {
            "gui.gensokyou.ritual.yumewatari.lore_1",
            "gui.gensokyou.ritual.yumewatari.lore_2",
            "gui.gensokyou.ritual.yumewatari.lore_3",
            "gui.gensokyou.ritual.yumewatari.lore_4",
            "gui.gensokyou.ritual.yumewatari.lore_5",
    };

    @Override
    public List<InfoLine> uiInfo(ServerLevel level, BlockPos corePos,
                                 RitualMatch match, RitualCoreBlockEntity core) {
        List<InfoLine> lines = new ArrayList<>();
        List<Bed> beds = scanBeds(level, match, corePos);
        int sleepers = countSleepers(beds);
        String[] args = {String.valueOf(beds.size()), String.valueOf(sleepers)};
        if (beds.isEmpty()) {
            lines.add(new InfoLine("gui.gensokyou.ritual.yumewatari.beds", args,
                    "", 0xFF4FC3F7, -1F, null));
        } else {
            List<String> detail = new ArrayList<>();
            for (Bed b : beds) {
                detail.add((b.sleeper() != null ? "* " : "- ")
                        + b.head().getX() + ", " + b.head().getY() + ", " + b.head().getZ());
            }
            lines.add(InfoLine.tipped("gui.gensokyou.ritual.yumewatari.beds", args, 0xFF4FC3F7,
                    "gui.gensokyou.ritual.yumewatari.beds.tip",
                    new String[]{String.join("\n", detail)}));
        }
        for (String loreKey : LORE_KEYS) {
            lines.add(new InfoLine(loreKey, new String[0], "", 0xFFB39DDB, -1F, null));
        }
        return lines;
    }

    // ---- 共享静态核心：事件结算与调试命令同一代码路径 ----

    /** 单生物产灵 = 基值 × 4^等级。 */
    public static long unitPerSleeper(int level) {
        long unit = GensokyouConfig.YUMEWATARI_PRODUCTION_PER_SLEEPER.get().longValue();
        for (int i = 0; i < level; i++) {
            unit *= 4L;
        }
        return unit;
    }

    /** 结构全量格位 → XZ 包围盒；y = 核心所在层（床头脚同层，天然满足等高）。 */
    public static Rect scanRect(RitualMatch match, BlockPos corePos) {
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (List<BlockPos> positions : match.keyedPositions().values()) {
            for (BlockPos p : positions) {
                if (p.getX() < minX) minX = p.getX();
                if (p.getX() > maxX) maxX = p.getX();
                if (p.getZ() < minZ) minZ = p.getZ();
                if (p.getZ() > maxZ) maxZ = p.getZ();
            }
        }
        return new Rect(minX, maxX, minZ, maxZ, corePos.getY());
    }

    /** 合规床扫描：矩形等高层逐格找床，只认床头格、床尾须在盒内且同为床；快照床上占用者。 */
    public static List<Bed> scanBeds(ServerLevel level, RitualMatch match, BlockPos corePos) {
        Rect rect = scanRect(match, corePos);
        List<Bed> beds = new ArrayList<>();
        for (int x = rect.minX(); x <= rect.maxX(); x++) {
            for (int z = rect.minZ(); z <= rect.maxZ(); z++) {
                BlockPos head = new BlockPos(x, rect.y(), z);
                BlockState state = level.getBlockState(head);
                if (!state.is(BlockTags.BEDS) || state.getValue(BedBlock.PART) != BedPart.HEAD) {
                    continue;
                }
                BlockPos foot = head.relative(state.getValue(BedBlock.FACING).getOpposite());
                if (!rect.contains(foot.getX(), foot.getZ())
                        || !level.getBlockState(foot).is(BlockTags.BEDS)) {
                    continue;
                }
                beds.add(new Bed(head, foot, findSleeper(level, head, foot)));
            }
        }
        return beds;
    }

    /** 床上占用者：睡眠中且 sleepingPos 落在头/脚任一格的玩家或村民（原版排斥保证至多一只）。 */
    private static Entity findSleeper(ServerLevel level, BlockPos head, BlockPos foot) {
        for (ServerPlayer player : level.players()) {
            if (sleepingInBed(player, head, foot)) {
                return player;
            }
        }
        AABB box = new AABB(head).minmax(new AABB(foot)).inflate(0.5D);
        List<Villager> villagers = level.getEntitiesOfClass(Villager.class, box,
                v -> sleepingInBed(v, head, foot));
        return villagers.isEmpty() ? null : villagers.get(0);
    }

    private static boolean sleepingInBed(LivingEntity entity, BlockPos head, BlockPos foot) {
        if (!entity.isSleeping()) {
            return false;
        }
        return entity.getSleepingPos()
                .filter(p -> p.equals(head) || p.equals(foot))
                .isPresent();
    }

    private static int countSleepers(List<Bed> beds) {
        int count = 0;
        for (Bed b : beds) {
            if (b.sleeper() != null) {
                count++;
            }
        }
        return count;
    }

    /**
     * 跳夜结算（事件与调试共用）：快照 → 产出 → 缓存截断 → 溢出直注灵力核心 → 余量作废。
     * 零睡眠者不产生任何写入与推送。
     */
    public static Settlement settle(ServerLevel level, BlockPos corePos,
                                    RitualMatch match, RitualCoreBlockEntity core) {
        return settleWith(level, corePos, match, core, countSleepers(scanBeds(level, match, corePos)));
    }

    /** 结算内核；{@code sleepers} 由调试命令注入以在无真实睡眠者时验证分流路径（仅调试侧使用）。 */
    public static Settlement settleWith(ServerLevel level, BlockPos corePos, RitualMatch match,
                                        RitualCoreBlockEntity core, int sleepers) {
        long unit = unitPerSleeper(match.level());
        if (sleepers <= 0) {
            return new Settlement(0, unit, 0L, 0L, 0L, 0L);
        }
        long amount = unit * sleepers;
        long toCache = core.receive(amount);
        long rest = amount - toCache;
        long toCore = 0L;
        if (rest > 0L) {
            ItemStack battery = core.batteryStack();
            if (battery.getItem() instanceof SpiritCoreItem) {
                toCore = SpiritCoreItem.receive(battery, rest);
                if (toCore > 0L) {
                    core.setBatteryStack(battery);
                }
            }
        }
        ModNetworking.sendRitualInfoToViewers(level, corePos);
        return new Settlement(sleepers, unit, amount, toCache, toCore, rest - toCore);
    }
}
