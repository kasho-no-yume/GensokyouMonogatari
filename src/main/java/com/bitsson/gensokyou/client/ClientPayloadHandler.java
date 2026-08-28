package com.bitsson.gensokyou.client;

import com.bitsson.gensokyou.network.RitualInfoPayload;
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
}
