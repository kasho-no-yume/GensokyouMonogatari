package com.bitsson.gensokyou.event;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.balance.BalanceNumbers;
import com.bitsson.gensokyou.registry.ModItems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/**
 * 原版怪物死亡时按固定概率额外掉落 P 点（1 × ppoint）。
 *
 * <p>只放行原版（{@code minecraft} 命名空间）且属 {@link MobCategory#MONSTER} 的实体，
 * 因此妖精与模组 BOSS 不会重复产出 P 点——它们走各自的掉落链路。
 */
@EventBusSubscriber(modid = Gensokyou.MODID)
public final class VanillaPPointDropEvents {

    private VanillaPPointDropEvents() {
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Mob mob)) {
            return;
        }
        if (!(mob.level() instanceof ServerLevel)) {
            return;
        }
        if (mob.getType().getCategory() != MobCategory.MONSTER) {
            return;
        }
        if (!BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).getNamespace().equals("minecraft")) {
            return;
        }
        if (mob.getRandom().nextDouble() >= BalanceNumbers.VANILLA_MOB_PPOINT_CHANCE) {
            return;
        }
        mob.spawnAtLocation(new ItemStack(ModItems.PPOINT.get()));
    }
}
