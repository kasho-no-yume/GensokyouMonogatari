package com.bitsson.gensokyou.client;

import com.bitsson.gensokyou.client.screen.DialogScreen;
import com.bitsson.gensokyou.network.DialogSyncPayload;
import net.minecraft.client.Minecraft;

/**
 * 对话界面客户端侧调度。独立类使 Minecraft/DialogScreen 引用只在客户端运行时加载
 * （服务端仅经 ClientPayloadHandler 的惰性 lambda 指向此处，永不触发类加载）。
 */
public final class ClientDialog {

    private ClientDialog() {
    }

    /** nodeId 空串 = 关闭哨兵；已在对话界面内则原地刷新，否则开新界面。 */
    public static void handle(DialogSyncPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        if (payload.nodeId().isEmpty()) {
            if (mc.screen instanceof DialogScreen) {
                mc.setScreen(null);
            }
            return;
        }
        if (mc.screen instanceof DialogScreen screen) {
            screen.setContent(payload);
        } else {
            mc.setScreen(new DialogScreen(payload));
        }
    }
}
