package com.bitsson.gensokyou.ritual.behavior;

import com.bitsson.gensokyou.config.GensokyouConfig;
import com.bitsson.gensokyou.ritual.RitualBehavior;
import com.bitsson.gensokyou.ritual.RitualMatch;
import com.bitsson.gensokyou.spirit.ModAttachments;
import com.bitsson.gensokyou.spirit.SpiritPowerHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

public class TemperingBehavior implements RitualBehavior {

    public static final int ACTION_TEMPER = 0;

    @Override
    public List<UiAction> uiActions(ServerLevel level, BlockPos corePos, RitualMatch match,
                                    com.bitsson.gensokyou.block.entity.RitualCoreBlockEntity core) {
        return List.of(new UiAction(ACTION_TEMPER, "gui.gensokyou.ritual.temper"));
    }

    @Override
    public InteractionResult onUiAction(ServerLevel level, BlockPos corePos,
                                        com.bitsson.gensokyou.ritual.RitualMatch match,
                                        com.bitsson.gensokyou.block.entity.RitualCoreBlockEntity core,
                                        ServerPlayer player, int actionId) {
        if (actionId == ACTION_TEMPER) {
            attempt(level, player, corePos);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    @Override
    public InteractionResult onUseItem(ServerLevel level, BlockPos corePos,
                                       com.bitsson.gensokyou.ritual.RitualMatch match,
                                       com.bitsson.gensokyou.block.entity.RitualCoreBlockEntity core,
                                       ServerPlayer player, ItemStack stack) {
        attempt(level, player, corePos);
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult onUseEmptyHand(ServerLevel level, BlockPos corePos,
                                            com.bitsson.gensokyou.ritual.RitualMatch match,
                                            com.bitsson.gensokyou.block.entity.RitualCoreBlockEntity core,
                                            ServerPlayer player) {
        attempt(level, player, corePos);
        return InteractionResult.SUCCESS;
    }

    private void attempt(ServerLevel level, ServerPlayer player, BlockPos corePos) {
        var data = ModAttachments.get(player);
        int nextLevel = data.temperLevel() + 1;
        double cost = Math.floor(GensokyouConfig.TEMPER_SP_COST_BASE.get()
                * Math.pow(GensokyouConfig.TEMPER_SP_COST_GROWTH.get(), nextLevel - 1));

        boolean hasDiamond = player.getMainHandItem().is(Items.DIAMOND)
                || player.getOffhandItem().is(Items.DIAMOND);
        if (!hasDiamond) {
            player.displayClientMessage(Component.translatable("msg.gensokyou.temper_need_diamond"), true);
            return;
        }
        float paidFromPlayer = Math.min(data.current(), (float) cost);
        float remaining = (float) cost - paidFromPlayer;
        if (remaining > 0F && !SpiritPowerHelper.hasCapacitorAround(level, player.blockPosition(), 3)) {
            player.displayClientMessage(Component.translatable("msg.gensokyou.temper_no_power", (int) cost), true);
            return;
        }
        ModAttachments.set(player, data.withCurrent(data.current() - paidFromPlayer));
        if (remaining > 0F) {
            float drained = SpiritPowerHelper.drainCapacitorsAround(level, player.blockPosition(), 3, remaining);
            if (drained < remaining - 0.01F) {
                ModAttachments.set(player, ModAttachments.get(player).withAddedCurrent(remaining - drained));
                player.displayClientMessage(Component.translatable("msg.gensokyou.temper_no_power", (int) cost), true);
                return;
            }
        }
        if (player.getMainHandItem().is(Items.DIAMOND)) {
            player.getMainHandItem().shrink(1);
        } else {
            player.getOffhandItem().shrink(1);
        }
        ModAttachments.set(player, ModAttachments.get(player).withTemperUp(
                GensokyouConfig.MAX_SP_GAIN_PER_TEMPER.get().floatValue(), 0F));
        level.playSound(null, player.blockPosition(),
                SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1F, 1.2F);
        player.displayClientMessage(Component.translatable("msg.gensokyou.temper_success",
                nextLevel, (int) GensokyouConfig.MAX_SP_GAIN_PER_TEMPER.get().doubleValue()), false);
    }
}
