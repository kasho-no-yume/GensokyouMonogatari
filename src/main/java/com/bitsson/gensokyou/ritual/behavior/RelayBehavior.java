package com.bitsson.gensokyou.ritual.behavior;

import com.bitsson.gensokyou.block.entity.RitualCoreBlockEntity;
import com.bitsson.gensokyou.config.GensokyouConfig;
import com.bitsson.gensokyou.ritual.RitualBehavior;
import com.bitsson.gensokyou.ritual.RitualBehaviors;
import com.bitsson.gensokyou.ritual.RitualMatch;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public class RelayBehavior implements RitualBehavior {

    private static final double LOOK_RANGE = 8D;
    private static final int CYCLE_TICKS = 20;

    @Override
    public InteractionResult onUseEmptyHand(ServerLevel level, BlockPos corePos, RitualMatch match,
                                            RitualCoreBlockEntity core, ServerPlayer player) {
        if (!player.isShiftKeyDown()) {
            return InteractionResult.SUCCESS;
        }
        BlockPos looked = lookedCapacitorCore(level, player);
        if (looked == null) {
            player.displayClientMessage(
                    Component.translatable("msg.gensokyou.relay_look_capacitor"), true);
            return InteractionResult.SUCCESS;
        }
        if (core.linked()) {
            core.unbindLinks();
            player.displayClientMessage(
                    Component.translatable("msg.gensokyou.relay_unbound"), true);
        } else if (core.pendingLink() != null) {
            if (core.completeLink(looked)) {
                player.displayClientMessage(
                        Component.translatable("msg.gensokyou.relay_linked"), true);
            } else {
                player.displayClientMessage(
                        Component.translatable("msg.gensokyou.relay_same"), true);
            }
        } else if (core.beginLink(looked)) {
            player.displayClientMessage(
                    Component.translatable("msg.gensokyou.relay_step1"), true);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void serverTick(ServerLevel level, BlockPos corePos, RitualMatch match,
                           RitualCoreBlockEntity core) {
        if (!core.linked() || core.ageTicks() % CYCLE_TICKS != 0) {
            return;
        }
        RitualCoreBlockEntity a = capacitorCoreAt(level, core.linkA());
        RitualCoreBlockEntity b = capacitorCoreAt(level, core.linkB());
        if (a == null || b == null) {
            core.unbindLinks();
            return;
        }
        int rate = GensokyouConfig.RELAY_TRANSFER_RATE.get();
        RitualCoreBlockEntity fuller = a.getStored() >= b.getStored() ? a : b;
        RitualCoreBlockEntity emptier = fuller == a ? b : a;
        emptier.receive(fuller.extract(Math.min(rate, fuller.getStored() - emptier.getStored())));
    }

    /** 视线落点是否为一台处于电容仪式状态的仪式核心。 */
    private BlockPos lookedCapacitorCore(ServerLevel level, ServerPlayer player) {
        HitResult hit = player.pick(LOOK_RANGE, 1F, false);
        if (!(hit instanceof BlockHitResult blockHit)
                || hit.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        BlockPos pos = blockHit.getBlockPos();
        if (!(level.getBlockEntity(pos) instanceof RitualCoreBlockEntity target)
                || !target.isPattern(RitualBehaviors.CAPACITOR)) {
            return null;
        }
        return pos.immutable();
    }

    private RitualCoreBlockEntity capacitorCoreAt(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return null;
        }
        if (!(level.getBlockEntity(pos) instanceof RitualCoreBlockEntity core)
                || !core.isPattern(RitualBehaviors.CAPACITOR)) {
            return null;
        }
        return core;
    }
}
