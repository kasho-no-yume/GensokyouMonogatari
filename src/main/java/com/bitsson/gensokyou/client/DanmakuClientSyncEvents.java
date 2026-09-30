package com.bitsson.gensokyou.client;

import com.bitsson.gensokyou.danmaku.render.DanmakuClientClock;
import com.bitsson.gensokyou.danmaku.render.DanmakuResyncQueue;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * 客户端每 tick 的弹幕同步泵。
 *
 * <p>只做一件客户端专属的事：驱动 {@link DanmakuResyncQueue} 发出合并后的恢复请求。
 * 队列本身是 common 类（它只依赖 {@code PacketDistributor}），因为弹幕实体的每 tick
 * 路径要直接调用它，而那个类在专用服务端上同样会加载——让 common 类静态引用 client
 * 类，等于把类加载失败的风险留在服务端。
 *
 * <p><b>切世界 / 退出世界时清空队列</b>：上一局的实体 id 在新世界里可能指向完全无关
 * 的实体，而恢复请求只带 id —— 不清空就是把请求发给了错误的对象。
 */
@EventBusSubscriber(modid = com.bitsson.gensokyou.Gensokyou.MODID, value = Dist.CLIENT)
public final class DanmakuClientSyncEvents {

    private DanmakuClientSyncEvents() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        var level = Minecraft.getInstance().level;
        if (level == null) {
            DanmakuResyncQueue.clear();
            // 退出世界 MUST 一并清时钟：上一段连接的速率套到新一段上，
            // 而新连接的快慢可能完全不同。
            DanmakuClientClock.reset();
            return;
        }
        DanmakuResyncQueue.onClientTick(level.getGameTime());
        // 陈旧即降级：收不到服务器时间观测时停止外推，而不是给出一个
        // 越来越离谱的答案。放在这里而不是包处理器里，是因为「多久没收到」
        // 只有按 tick 问才准得出来。
        DanmakuClientClock.noteTick(level.getGameTime());
    }
}
