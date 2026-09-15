package com.bitsson.gensokyou.spirit.grace;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.block.entity.RitualCoreBlockEntity;
import com.bitsson.gensokyou.menu.RitualCoreMenu;
import com.bitsson.gensokyou.ritual.RitualBehaviors;
import com.bitsson.gensokyou.ritual.behavior.YaoyorozuGraceService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * 神恩演出与预览的横切事件：
 * 演出期外部伤害全免疫（脚本 grace_perform 伤害放行）；noGravity 泄漏兜底
 * （核心区块卸载等导致演出 tick 断供时，解除残留的失重）；
 * initiator 关闭核心界面 = 当场制洗练预览作废。
 */
@EventBusSubscriber(modid = Gensokyou.MODID)
public final class GraceEvents {

    private GraceEvents() {
    }

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || com.bitsson.gensokyou.registry.ModDamageTypes.isGracePerform(event.getSource())) {
            return;
        }
        if (YaoyorozuGraceService.isPerforming(player)) {
            event.setCanceled(true); // 演出免疫：脚本掉血/回血曲线不受外力干扰
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player
                && !player.isCreative() && !player.isSpectator()
                && player.isNoGravity()
                && !YaoyorozuGraceService.isPerforming(player)) {
            player.setNoGravity(false);
        }
    }

    @SubscribeEvent
    public static void onContainerClosed(PlayerContainerEvent.Close event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(player.level() instanceof ServerLevel level)
                || !(event.getContainer() instanceof RitualCoreMenu menu)
                || !(level.getBlockEntity(menu.pos()) instanceof RitualCoreBlockEntity core)
                || !core.isPattern(RitualBehaviors.KAMI_NO_MEGUMI)) {
            return;
        }
        YaoyorozuGraceService.onViewerClosed(level, menu.pos(), player);
    }
}
