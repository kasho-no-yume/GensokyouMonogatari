package com.bitsson.gensokyou.event;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.block.entity.RitualCoreBlockEntity;
import com.bitsson.gensokyou.ritual.RitualBehaviors;
import com.bitsson.gensokyou.ritual.RitualCoreRegistry;
import com.bitsson.gensokyou.ritual.RitualMatch;
import com.bitsson.gensokyou.ritual.behavior.YumewatariBehavior;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.SleepFinishedTimeEvent;

/**
 * 梦渡之座跳夜结算钩子：原版在玩家睡眠达标跳时的 setDayTime 处派发
 * {@link SleepFinishedTimeEvent}（wakeUpAllPlayers 之前），此刻全部睡眠者仍在床——
 * 快照即真相。/time 命令与该事件路径无关，天然不触发。
 * 事件先于起床广播派发这一时序已由 sources jar 实测核验（MinecraftServer/ServerLevel.tickTime）。
 */
@EventBusSubscriber(modid = Gensokyou.MODID)
public final class YumewatariSleepHandler {

    private YumewatariSleepHandler() {
    }

    @SubscribeEvent
    public static void onSleepFinished(SleepFinishedTimeEvent event) {
        if (!(event.getLevel() instanceof net.minecraft.server.level.ServerLevel level)) {
            return;
        }
        for (RitualCoreBlockEntity core
                : RitualCoreRegistry.formedOfPattern(level, RitualBehaviors.YUMEWATARI)) {
            RitualMatch match = core.activeMatch();
            if (match != null) {
                YumewatariBehavior.settle(level, core.getBlockPos(), match, core);
            }
        }
    }
}
