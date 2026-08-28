package com.bitsson.gensokyou.item;

import com.bitsson.gensokyou.ritual.RitualBehaviors;
import com.bitsson.gensokyou.ritual.RitualMatcher;
import com.bitsson.gensokyou.registry.ModEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.function.Supplier;

public class SummonCatalystItem extends Item {

    private final Supplier<net.minecraft.world.entity.EntityType<? extends Mob>> bossType;

    public SummonCatalystItem(Properties properties,
                              Supplier<net.minecraft.world.entity.EntityType<? extends Mob>> bossType) {
        super(properties);
        this.bossType = bossType;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();

        if (level instanceof ServerLevel serverLevel) {
            var match = RitualMatcher.matchAt(serverLevel, pos);
            if (match.isEmpty() || !match.get().patternId().equals(RitualBehaviors.SUMMON)) {
                if (player != null) {
                    player.displayClientMessage(
                            Component.translatable(match.isPresent()
                                    ? "msg.gensokyou.ritual_wrong_type"
                                    : "msg.gensokyou.ritual_invalid"), true);
                }
                return InteractionResult.FAIL;
            }
            Mob boss = bossType.get().create(serverLevel);
            if (boss == null) {
                return InteractionResult.FAIL;
            }
            boss.moveTo(pos.getX() + 0.5D, pos.getY() + 1D, pos.getZ() + 0.5D, 0F, 0F);
            serverLevel.addFreshEntity(boss);

            ItemStack stack = context.getItemInHand();
            if (player == null || !player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            level.playSound(null, pos, SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 0.6F, 1.4F);
            if (player != null) {
                player.displayClientMessage(
                        Component.translatable("msg.gensokyou.ritual_summoned"), false);
            }
        }
        return InteractionResult.SUCCESS;
    }
}
