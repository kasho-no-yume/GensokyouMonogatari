package com.bitsson.gensokyou.client;

import com.bitsson.gensokyou.Gensokyou;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * 飞行惯性关闭态（superhuman-flight）：无移动键输入的 tick 水平速度立即归零（松键即停）。
 *
 * <p>客户端手感为权威（玩家运动本就客户端预测+位置包上行，服务端裸改速度必回弹）；
 * 服务端仅持久开关值。创造（instabuild）/观察者不受开关影响——与规范一致。
 */
@EventBusSubscriber(modid = Gensokyou.MODID, value = Dist.CLIENT)
public final class GraceFlightClient {

    private GraceFlightClient() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player != event.getEntity()) {
            return;
        }
        var abilities = mc.player.getAbilities();
        if (!abilities.flying || abilities.instabuild || mc.player.isSpectator()
                || SpiritPowerClientState.flightInertia()) {
            return;
        }
        var options = mc.options;
        if (options.keyUp.isDown() || options.keyDown.isDown()
                || options.keyLeft.isDown() || options.keyRight.isDown()) {
            return;
        }
        Vec3 motion = mc.player.getDeltaMovement();
        if (motion.x != 0D || motion.z != 0D) {
            mc.player.setDeltaMovement(0D, motion.y, 0D);
        }
    }
}
