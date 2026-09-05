package com.bitsson.gensokyou.client;

import com.bitsson.gensokyou.network.DialogSyncPayload;
import com.bitsson.gensokyou.network.RitualConflictPayload;
import com.bitsson.gensokyou.network.RitualInfoPayload;
import com.bitsson.gensokyou.network.RitualPreviewPayload;
import com.bitsson.gensokyou.network.SkillSyncPayload;
import com.bitsson.gensokyou.network.SpiritPowerSyncPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class ClientPayloadHandler {

    private ClientPayloadHandler() {
    }

    public static void handleSpiritPowerSync(SpiritPowerSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> SpiritPowerClientState.update(payload.current(), payload.max()));
    }

    public static void handleSkillSync(SkillSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() ->
                ClientSkillState.update(payload.learned(), payload.remainingTicks()));
    }

    public static void handleRitualInfo(RitualInfoPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientRitualState.update(payload));
    }

    public static void handleRitualConflict(RitualConflictPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientRitualConflictState.update(payload.positions()));
    }

    public static void handleRitualPreview(RitualPreviewPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientRitualPreviewState.update(payload.preview()));
    }

    /** 对话同步：委托客户端专属类处理（服务端不加载 DialogScreen/Minecraft）。 */
    public static void handleDialogSync(DialogSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientDialog.handle(payload));
    }
}
