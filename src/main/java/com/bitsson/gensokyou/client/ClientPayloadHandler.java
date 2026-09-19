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
        context.enqueueWork(() -> SpiritPowerClientState.update(
                payload.current(), payload.max(), payload.temper(), payload.flightInertia()));
    }

    public static void handleSkillSync(SkillSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientSkillState.update(
                payload.learned(), payload.remainingTicks(), payload.equipped()));
    }

    public static void handleRitualInfo(RitualInfoPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientRitualState.update(payload));
    }

    /** 造化合成演出：登记包围盒粒子程序，客户端本地生成至到期。 */
    public static void handleRitualCraftFx(com.bitsson.gensokyou.network.RitualCraftFxPayload payload,
                                           IPayloadContext context) {
        context.enqueueWork(() -> ClientCraftFxState.add(payload,
                net.minecraft.client.Minecraft.getInstance().level));
    }

    public static void handleRitualConflict(RitualConflictPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientRitualConflictState.update(payload.positions()));
    }

    public static void handleRitualPreview(RitualPreviewPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientRitualPreviewState.update(payload.preview()));
    }

    public static void handleEditorPreview(com.bitsson.gensokyou.network.EditorPreviewPayload payload,
                                           IPayloadContext context) {
        context.enqueueWork(() -> ClientRitualEditorState.update(payload));
    }

    /** 对话同步：委托客户端专属类处理（服务端不加载 DialogScreen/Minecraft）。 */
    public static void handleDialogSync(DialogSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientDialog.handle(payload));
    }

    /** 无尽藏晶可见页快照：暂存供界面渲染。 */
    public static void handleCrystalPage(
            com.bitsson.gensokyou.network.CrystalStoragePagePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientCrystalStorageState.update(payload));
    }
}
