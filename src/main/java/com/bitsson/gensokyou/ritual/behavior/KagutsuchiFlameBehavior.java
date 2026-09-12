package com.bitsson.gensokyou.ritual.behavior;

import com.bitsson.gensokyou.block.entity.RitualCoreBlockEntity;
import com.bitsson.gensokyou.block.entity.RitualPedestalBlockEntity;
import com.bitsson.gensokyou.config.GensokyouConfig;
import com.bitsson.gensokyou.network.ModNetworking;
import com.bitsson.gensokyou.ritual.RitualBehavior;
import com.bitsson.gensokyou.ritual.RitualMatch;
import com.bitsson.gensokyou.spirit.SpiritCoreItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 加具土命之焰：吞食祭品台上的可燃物产出灵力。
 *
 * 点火即吞——选取成功的燃料当场从台面销毁（容器残留落回原台），
 * 批次只记于核心 BE 计时字段，不引用台位；烧完的当 tick 立即衔接下一批，
 * 无料/缓存满则停等，缓存回落即续火。产出进核心缓存（上限随等级
 * {@code BASE_CAPACITY × 10^L}），并按所插灵力核心的注灵速率节流转入电池。
 */
public class KagutsuchiFlameBehavior implements RitualBehavior {

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
        if (core.ageTicks() % 20 == 0) {
            settlePerSecond(match, core);
            // 缓存显示/sp 文本 1Hz 收敛（打开界面即持续收帧，无需事件级全状态推送）
            ModNetworking.sendRitualInfoToViewers(level, corePos);
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

    /** 每秒一次的产灵/注灵结算（速率 ×1000 定点进位，避免整除截断）。 */
    private static void settlePerSecond(RitualMatch match, RitualCoreBlockEntity core) {
        // 产灵：20 × 4^等级 每秒；receive 天然截到上限，超出部分作废（空烧语义）
        double ratePerSecond = GensokyouConfig.KAGUTSUICHI_BASE_RATE_PER_SECOND.get()
                * pow4(match.level());
        long rateCarry = core.rateCarry() + (long) Math.floor(ratePerSecond * 1000D);
        long produced = rateCarry / 1000L;
        core.setRateCarry(rateCarry % 1000L);
        if (produced > 0L) {
            core.receive(produced);
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

    private static void emitFlameParticles(ServerLevel level, BlockPos corePos,
                                           RitualMatch match, RitualCoreBlockEntity core) {
        if (!core.isBurning()) {
            return;
        }
        int interval = Math.max(2, 6 - match.level());
        if (core.ageTicks() % interval != 0) {
            return;
        }
        int count = 2 + match.level();
        var random = level.getRandom();
        level.sendParticles(ParticleTypes.FLAME,
                corePos.getX() + 0.5D + (random.nextDouble() - 0.5D) * 0.7D,
                corePos.getY() + 0.9D + random.nextDouble() * 0.5D,
                corePos.getZ() + 0.5D + (random.nextDouble() - 0.5D) * 0.7D,
                count, 0.03D, 0.08D, 0.03D, 0.005D);
    }
}
