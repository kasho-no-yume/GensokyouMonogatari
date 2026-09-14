package com.bitsson.gensokyou.ritual.behavior;

import com.bitsson.gensokyou.block.entity.RitualCoreBlockEntity;
import com.bitsson.gensokyou.block.entity.RitualPedestalBlockEntity;
import com.bitsson.gensokyou.config.GensokyouConfig;
import com.bitsson.gensokyou.network.InfoLine;
import com.bitsson.gensokyou.network.ModNetworking;
import com.bitsson.gensokyou.ritual.RitualBehavior;
import com.bitsson.gensokyou.ritual.RitualMatch;
import com.bitsson.gensokyou.spirit.SpiritCoreItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 加具土命之焰：吞食祭品台上的可燃物产出灵力。
 *
 * 缓存对路由（万象共鸣）的可抽取上限由独立的供灵基项声明（默认与产灵等值，语义分道）。
 *
 * 点火即吞——选取成功的燃料当场从台面销毁（容器残留落回原台），
 * 批次只记于核心 BE 计时字段，不引用台位；烧完的当 tick 立即衔接下一批，
 * 无料/缓存满则停等，缓存回落即续火。产出进核心缓存（上限随等级
 * {@code BASE_CAPACITY × 10^L}），并按所插灵力核心的注灵速率节流转入电池。
 */
public class KagutsuchiFlameBehavior implements RitualBehavior {

    @Override
    public boolean usesCoreSocket() {
        return true;
    }

    @Override
    public List<InfoLine> uiInfo(ServerLevel level, BlockPos corePos, RitualMatch match,
                                 RitualCoreBlockEntity core) {
        List<InfoLine> lines = new ArrayList<>();
        int remaining = core.burnRemainingTicks();
        int total = core.burnTotalTicks();
        if (core.isBurning() && remaining > 0 && total > 0) {
            // 燃烧批次：燃料图标 + 进度条 + 剩余秒
            ItemStack fuel = core.burnFuelIcon();
            String iconId = fuel.isEmpty() ? ""
                    : BuiltInRegistries.ITEM.getKey(fuel.getItem()).toString();
            lines.add(new InfoLine("gui.gensokyou.ritual.kagutsuchi.burning", new String[0],
                    iconId, 0xFF2E8B57, (float) (total - remaining) / total, null));
            lines.add(new InfoLine("gui.gensokyou.ritual.kagutsuchi.remaining",
                    new String[]{String.valueOf((remaining + 19) / 20)},
                    "", 0, -1F, null));
        } else if (!core.isEnabled()) {
            lines.add(new InfoLine("gui.gensokyou.ritual.kagutsuchi.not_started",
                    new String[0], "", 0, -1F, null));
        } else if (core.getStored() >= core.getCapacity()) {
            lines.add(new InfoLine("gui.gensokyou.ritual.kagutsuchi.stalled",
                    new String[0], "", 0xFFB22222, -1F, null));
        } else {
            lines.add(new InfoLine("gui.gensokyou.ritual.kagutsuchi.idle",
                    new String[0], "", 0, -1F, null));
        }
        lines.addAll(RitualBehavior.defaultUiInfo(level, corePos, match, core));
        return lines;
    }

    @Override
    public void serverTick(ServerLevel level, BlockPos corePos, RitualMatch match,
                           RitualCoreBlockEntity core) {
        // 1) 推进当前批；烧尽在同 tick 尝试点燃下一批（规则：无断档帧）
        if (core.isBurning()) {
            if (core.advanceBurnTick()) {
                core.clearBurnBatch();
            }
        }
        if (!core.isBurning()) {
            tryIgnite(level, corePos, match, core);
        }
        // 2) 每秒结算：产灵入账（缓存满则本秒产出作废=空烧）+ 注灵进电池
        //    GUI 快照刷新已收编至核心 BE 的统一 1Hz 心跳（含停机态），此处不再自推
        if (core.ageTicks() % 20 == 0) {
            settlePerSecond(match, core);
        }
        // 3) 燃烧中（含空烧期）飘火焰粒子；停等/待机零粒子
        emitFlameParticles(level, corePos, match, core);
    }

    @Override
    public void onStructureLost(ServerLevel level, BlockPos corePos) {
        if (level.getBlockEntity(corePos) instanceof RitualCoreBlockEntity core) {
            core.clearBurnBatch();
        }
    }

    /** 规范序扫描祭品台，点燃首个合格可燃物；缓存满时不选（停等）。 */
    private static void tryIgnite(ServerLevel level, BlockPos corePos, RitualMatch match,
                                  RitualCoreBlockEntity core) {
        if (core.getStored() >= core.getCapacity()) {
            return;
        }
        for (BlockPos pos : match.positionsOf('P')) {
            if (!(level.getBlockEntity(pos) instanceof RitualPedestalBlockEntity pedestal)) {
                continue;
            }
            ItemStack held = pedestal.getHeld();
            if (held.isEmpty()) {
                continue;
            }
            int burnTicks = burnTicksOf(held);
            if (burnTicks <= 0 || isBlacklisted(held)) {
                continue;
            }
            ItemStack remainder = held.hasCraftingRemainingItem()
                    ? held.getCraftingRemainingItem() : ItemStack.EMPTY;
            pedestal.setHeld(remainder);
            core.beginBurnBatch(held, burnTicks);
            ModNetworking.sendRitualInfoToViewers(level, corePos);
            return;
        }
    }

    /** 燃料燃烧时长：熔炉口径（同原版 canPlaceItem 判定），tick 计。 */
    static int burnTicksOf(ItemStack stack) {
        return stack.getBurnTime(null);
    }

    private static boolean isBlacklisted(ItemStack stack) {
        for (String id : GensokyouConfig.KAGUTSUICHI_FUEL_BLACKLIST.get()) {
            ResourceLocation rl = ResourceLocation.tryParse(id);
            if (rl == null) {
                continue;
            }
            Item item = BuiltInRegistries.ITEM.get(rl);
            if (item != null && stack.is(item)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 产灵速率式（每秒，指定等级）：内部生成入账口径，仅 settlePerSecond 引用。
     * 与供灵输出上限（{@link #maxOutputRatePerSecond}）语义分道、配置基项独立。
     */
    public static double productionRatePerSecond(int level) {
        return GensokyouConfig.KAGUTSUICHI_BASE_RATE_PER_SECOND.get() * pow4(level);
    }

    /** 供灵输出上限式（每秒，指定等级）：路由（万象共鸣）可抽取的全局上限，独立配置基项，默认数值与产灵相同。 */
    public static double maxOutputRatePerSecond(int level) {
        return GensokyouConfig.KAGUTSUICHI_BASE_OUT_RATE_PER_SECOND.get() * pow4(level);
    }

    @Override
    public long spiritOutRatePerSecond(ServerLevel level, BlockPos corePos, RitualMatch match,
                                       RitualCoreBlockEntity core) {
        return (long) Math.floor(maxOutputRatePerSecond(match.level()));
    }

    /** 每秒一次的产灵/注灵结算（速率 ×1000 定点进位，避免整除截断）。 */
    private static void settlePerSecond(RitualMatch match, RitualCoreBlockEntity core) {
        // 产灵：20 × 4^等级 每秒，仅燃烧期入账（含空烧——receive 天然截到上限，超出作废）；
        // 无燃料待机/停等 MUST NOT 白产（bugfix：此前缺 isBurning 门控）
        if (core.isBurning()) {
            double ratePerSecond = productionRatePerSecond(match.level());
            long rateCarry = core.rateCarry() + (long) Math.floor(ratePerSecond * 1000D);
            long produced = rateCarry / 1000L;
            core.setRateCarry(rateCarry % 1000L);
            if (produced > 0L) {
                core.receive(produced);
            }
        }
        // 注灵：按核心自身速率从缓存转入电池（电池满/无缓存自然为 0）
        ItemStack battery = core.batteryStack();
        if (!battery.isEmpty() && battery.getItem() instanceof SpiritCoreItem spiritCore
                && core.getStored() > 0L) {
            long fillCarry = core.fillCarry() + (long) spiritCore.fillRatePerSecond() * 1000L;
            long want = Math.min(fillCarry / 1000L, core.getStored());
            core.setFillCarry(fillCarry % 1000L);
            if (want > 0L) {
                long pushed = SpiritCoreItem.receive(battery, want);
                core.extract(pushed);
            }
        }
    }

    private static long pow4(int exponent) {
        long value = 1L;
        for (int i = 0; i < exponent; i++) {
            value *= 4L;
        }
        return value;
    }

    /**
     * 多点火柱粒子（方案 B v2）：每柱 FLAME 主簇 + SMALL_FLAME 细簇双层。
     * 阶级 0 仅核心；≥1 每座祭品台一柱；≥2 环插值柱，半径铺到结构外扩边界的 40%~100%。
     * 间隔 {6,4,3,2}、单簇数量随阶级线性放大 → 观感密度约每阶 ×2。
     * 每 40t（L≥2 为 20t）补发 LARGE_SMOKE。非燃烧态（停等/待机）零粒子。
     */
    private static void emitFlameParticles(ServerLevel level, BlockPos corePos,
                                           RitualMatch match, RitualCoreBlockEntity core) {
        if (!core.isBurning()) {
            return;
        }
        int l = Math.min(3, Math.max(0, match.level()));
        int[] intervals = {6, 4, 3, 2};
        var random = level.getRandom();
        List<BlockPos> pedestals = match.positionsOf('P');
        double cx = corePos.getX() + 0.5D;
        double cz = corePos.getZ() + 0.5D;
        double baseY = corePos.getY() + 1.1D;
        if (core.ageTicks() % (l >= 2 ? 20 : 40) == 0) {
            BlockPos s = pedestals.isEmpty() ? corePos
                    : pedestals.get(random.nextInt(pedestals.size()));
            level.sendParticles(ParticleTypes.LARGE_SMOKE,
                    s.getX() + 0.5D, s.getY() + 1.6D, s.getZ() + 0.5D,
                    l >= 2 ? 2 : 1, 0.2D, 0.1D, 0.2D, 0.01D);
        }
        if (core.ageTicks() % intervals[l] != 0) {
            return;
        }
        emitFlameColumn(level, cx, baseY, cz, 2 + 2 * l, 0.35D, random);
        if (l < 1) {
            return;
        }
        double avgPed = 0D;
        for (BlockPos p : pedestals) {
            avgPed += Math.hypot(p.getX() + 0.5D - cx, p.getZ() + 0.5D - cz);
            emitFlameColumn(level, p.getX() + 0.5D, p.getY() + 1.1D, p.getZ() + 0.5D,
                    2 + 2 * l, 0.3D, random);
        }
        if (l < 2) {
            return;
        }
        double structR = 2D;
        for (List<BlockPos> ps : match.keyedPositions().values()) {
            for (BlockPos p : ps) {
                structR = Math.max(structR,
                        Math.hypot(p.getX() + 0.5D - cx, p.getZ() + 0.5D - cz));
            }
        }
        double ringBase = Math.max(structR, avgPed / Math.max(1, pedestals.size()) * 1.2D);
        for (int i = 0, points = 2 + 3 * l; i < points; i++) {
            double angle = random.nextDouble() * Math.PI * 2D;
            double radius = ringBase * (0.4D + random.nextDouble() * 0.6D);
            emitFlameColumn(level, cx + Math.cos(angle) * radius, baseY,
                    cz + Math.sin(angle) * radius, 3 + l, 0.25D, random);
        }
    }

    /** 单点火柱：FLAME 主簇 + 半数 SMALL_FLAME 细簇，中心随机上浮。 */
    private static void emitFlameColumn(ServerLevel level, double x, double y, double z,
                                        int count, double spread, RandomSource random) {
        level.sendParticles(ParticleTypes.FLAME,
                x, y + random.nextDouble() * 0.4D, z,
                count, spread, 0.1D, spread, 0.005D);
        level.sendParticles(ParticleTypes.SMALL_FLAME,
                x, y + random.nextDouble() * 0.4D, z,
                (count + 1) / 2, spread, 0.14D, spread, 0.004D);
    }
}
