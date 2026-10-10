package com.bitsson.gensokyou.client;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.network.CastSkillPayload;
import com.bitsson.gensokyou.spirit.SkillStateData;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * 符卡槽触发（skill-slots-hud）：按住修饰键（默认 G）+ 数字键 1..N 触发对应槽。
 *
 * <p>走 {@link ClientTickEvent.Pre}：`Minecraft.tick()` 在 `handleKeybinds()` 之前先 fire 本事件，
 * 故此刻清空热键栏数字键排队的点击即可屏蔽原版切槽。数字键用原始 GLFW 键态轮询 + 上升沿去抖，
 * 避免长按每 tick 连发。
 */
@EventBusSubscriber(modid = Gensokyou.MODID, value = Dist.CLIENT)
public final class ClientSkillTickHandler {

    /** 数字键（1..9）上升沿检测，索引 i = 数字键 i+1。 */
    private static final boolean[] DIGIT_DOWN = new boolean[9];

    private ClientSkillTickHandler() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        boolean active = mc.player != null && mc.screen == null && !mc.options.hideGui;
        boolean modDown = active && ClientKeyBindings.SKILL_MODIFIER.isDown();

        if (modDown) {
            // 屏蔽原版快捷栏数字键：清掉本 tick 已排队的切换点击
            for (KeyMapping hotbar : mc.options.keyHotbarSlots) {
                while (hotbar.consumeClick()) {
                }
            }
            int slotCount = SkillStateData.slotCountForTier(SpiritPowerClientState.temper());
            long window = mc.getWindow().getWindow();
            for (int i = 0; i < DIGIT_DOWN.length; i++) {
                boolean down = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_1 + i);
                if (down && !DIGIT_DOWN[i] && i < slotCount) {
                    PacketDistributor.sendToServer(new CastSkillPayload(i));
                }
                DIGIT_DOWN[i] = down;
            }
        } else {
            java.util.Arrays.fill(DIGIT_DOWN, false);
        }
        ClientSkillState.tickDown();
    }
}
