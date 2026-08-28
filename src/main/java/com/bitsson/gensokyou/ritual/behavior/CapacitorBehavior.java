package com.bitsson.gensokyou.ritual.behavior;

import com.bitsson.gensokyou.block.entity.RitualCoreBlockEntity;
import com.bitsson.gensokyou.config.GensokyouConfig;
import com.bitsson.gensokyou.ritual.RitualBehavior;
import com.bitsson.gensokyou.ritual.RitualMatch;
import com.bitsson.gensokyou.spirit.ModAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public class CapacitorBehavior implements RitualBehavior {

    public static final int ACTION_WITHDRAW = 0;
    public static final int ACTION_DEPOSIT = 1;

    @Override
    public List<UiAction> uiActions(ServerLevel level, BlockPos corePos, RitualMatch match,
                                    RitualCoreBlockEntity core) {
        return List.of(
                new UiAction(ACTION_WITHDRAW, "gui.gensokyou.ritual.cap_withdraw"),
                new UiAction(ACTION_DEPOSIT, "gui.gensokyou.ritual.cap_deposit"));
    }

    @Override
    public InteractionResult onUiAction(ServerLevel level, BlockPos corePos, RitualMatch match,
                                        RitualCoreBlockEntity core, ServerPlayer player, int actionId) {
        switch (actionId) {
            case ACTION_WITHDRAW -> withdraw(core, player);
            case ACTION_DEPOSIT -> deposit(core, player);
            default -> {
                return InteractionResult.PASS;
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult onUseItem(ServerLevel level, BlockPos corePos, RitualMatch match,
                                       RitualCoreBlockEntity core, ServerPlayer player, ItemStack stack) {
        if (player.isShiftKeyDown()) {
            deposit(core, player);
        } else {
            withdraw(core, player);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult onUseEmptyHand(ServerLevel level, BlockPos corePos, RitualMatch match,
                                            RitualCoreBlockEntity core, ServerPlayer player) {
        withdraw(core, player);
        return InteractionResult.SUCCESS;
    }

    private void withdraw(RitualCoreBlockEntity core, ServerPlayer player) {
        int rate = GensokyouConfig.CAPACITOR_TRANSFER_RATE.get();
        int received = core.extract(rate);
        var data = ModAttachments.get(player);
        if (received > 0) {
            ModAttachments.set(player, data.withAddedCurrent(received));
        }
        player.displayClientMessage(Component.translatable("msg.gensokyou.capacitor_withdraw",
                received, core.getStored(), core.getCapacity()), true);
    }

    private void deposit(RitualCoreBlockEntity core, ServerPlayer player) {
        var data = ModAttachments.get(player);
        int want = (int) Math.min(GensokyouConfig.CAPACITOR_TRANSFER_RATE.get(),
                Math.floor(data.current()));
        int deposited = core.receive(want);
        if (deposited > 0) {
            ModAttachments.set(player, data.withCurrent(data.current() - deposited));
        }
        player.displayClientMessage(Component.translatable("msg.gensokyou.capacitor_deposit",
                deposited, core.getStored(), core.getCapacity()), true);
    }
}
