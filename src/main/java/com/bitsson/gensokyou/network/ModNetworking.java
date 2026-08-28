package com.bitsson.gensokyou.network;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.block.entity.RitualCoreBlockEntity;
import com.bitsson.gensokyou.client.ClientPayloadHandler;
import com.bitsson.gensokyou.spirit.ModAttachments;
import com.bitsson.gensokyou.spirit.SkillStateData;
import com.bitsson.gensokyou.spirit.SpellCardEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = Gensokyou.MODID)
public final class ModNetworking {

    private ModNetworking() {
    }

    @SubscribeEvent
    public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(SpiritPowerSyncPayload.TYPE, SpiritPowerSyncPayload.STREAM_CODEC,
                ClientPayloadHandler::handleSpiritPowerSync);
        registrar.playToClient(SkillSyncPayload.TYPE, SkillSyncPayload.STREAM_CODEC,
                ClientPayloadHandler::handleSkillSync);
        registrar.playToClient(RitualInfoPayload.TYPE, RitualInfoPayload.STREAM_CODEC,
                ClientPayloadHandler::handleRitualInfo);
        registrar.playToServer(CastSkillPayload.TYPE, CastSkillPayload.STREAM_CODEC,
                ModNetworking::handleCastSkill);
        registrar.playToServer(RitualTogglePayload.TYPE, RitualTogglePayload.STREAM_CODEC,
                ModNetworking::handleRitualToggle);
    }

    /** 服务端权威启停处理：距离/存在性校验后执行，并回推最新界面信息。 */
    private static void handleRitualToggle(RitualTogglePayload payload,
                                           IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.level() instanceof ServerLevel serverLevel)) {
                return;
            }
            BlockPos pos = payload.pos();
            if (!serverLevel.isLoaded(pos)
                    || player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 64.0
                    || !(serverLevel.getBlockEntity(pos) instanceof RitualCoreBlockEntity core)) {
                return;
            }
            if (payload.start()) {
                boolean started = core.start(player);
                if (!started) {
                    player.displayClientMessage(
                            Component.translatable("msg.gensokyou.ritual_start_failed"), true);
                }
                sendRitualInfo(player, serverLevel, pos, core,
                        started ? "msg.gensokyou.ritual_started" : "");
            } else {
                core.stop();
                sendRitualInfo(player, serverLevel, pos, core, "msg.gensokyou.ritual_stopped");
            }
        });
    }

    /** 组装并下发仪式界面全量信息。 */
    public static void sendRitualInfo(ServerPlayer player, ServerLevel level, BlockPos pos,
                                      RitualCoreBlockEntity core, String statusKey) {
        PacketDistributor.sendToPlayer(player,
                RitualInfoPayload.snapshot(level, pos, core, statusKey));
    }

    private static void handleCastSkill(CastSkillPayload payload,
                                        net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            int slot = payload.slot();
            if (slot < 0 || slot >= SpellCardEffects.SLOT_ORDER.length) {
                return;
            }
            String cardId = SpellCardEffects.SLOT_ORDER[slot];
            var entry = SpellCardEffects.get(cardId);
            if (entry == null) {
                return;
            }
            SkillStateData state = ModAttachments.skills(player);
            if (!state.hasLearned(cardId)) {
                player.displayClientMessage(
                        Component.translatable("msg.gensokyou.skill_not_learned"), true);
                return;
            }
            long now = player.level().getGameTime();
            if (now < state.cooldownUntil(slot)) {
                long seconds = (state.cooldownUntil(slot) - now + 19) / 20;
                player.displayClientMessage(
                        Component.translatable("msg.gensokyou.skill_on_cooldown", seconds), true);
                return;
            }
            var powerData = ModAttachments.get(player);
            int spCost = entry.spCost().get();
            if (powerData.current() < spCost) {
                player.displayClientMessage(
                        Component.translatable("msg.gensokyou.skill_no_sp", spCost), true);
                return;
            }
            ModAttachments.set(player, powerData.withCurrent(powerData.current() - spCost));
            SpellCardEffects.perform(cardId, player.level(), player);
            ModAttachments.setSkills(player,
                    state.withCooldown(slot, now + entry.cooldownTicks().get()));
        });
    }
}
