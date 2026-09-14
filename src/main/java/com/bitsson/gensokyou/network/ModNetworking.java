package com.bitsson.gensokyou.network;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.block.entity.RitualCoreBlockEntity;
import com.bitsson.gensokyou.client.ClientPayloadHandler;
import com.bitsson.gensokyou.dialogue.DialogueManager;
import com.bitsson.gensokyou.item.BuilderSelection;
import com.bitsson.gensokyou.item.RitualBuilderItem;
import com.bitsson.gensokyou.registry.ModDataComponents;
import com.bitsson.gensokyou.ritual.RitualMatch;
import com.bitsson.gensokyou.ritual.RitualPatternLoader;
import com.bitsson.gensokyou.ritual.RitualPreviewState;
import com.bitsson.gensokyou.spirit.ModAttachments;
import com.bitsson.gensokyou.spirit.SkillStateData;
import com.bitsson.gensokyou.spirit.SpellCardEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
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
        registrar.playToClient(RitualCraftFxPayload.TYPE, RitualCraftFxPayload.STREAM_CODEC,
                ClientPayloadHandler::handleRitualCraftFx);
        registrar.playToServer(CastSkillPayload.TYPE, CastSkillPayload.STREAM_CODEC,
                ModNetworking::handleCastSkill);
        registrar.playToServer(RitualTogglePayload.TYPE, RitualTogglePayload.STREAM_CODEC,
                ModNetworking::handleRitualToggle);
        registrar.playToServer(RitualSelectPayload.TYPE, RitualSelectPayload.STREAM_CODEC,
                ModNetworking::handleRitualSelect);
        registrar.playToClient(RitualConflictPayload.TYPE, RitualConflictPayload.STREAM_CODEC,
                ClientPayloadHandler::handleRitualConflict);
        registrar.playToClient(RitualPreviewPayload.TYPE, RitualPreviewPayload.STREAM_CODEC,
                ClientPayloadHandler::handleRitualPreview);
        registrar.playToClient(EditorPreviewPayload.TYPE, EditorPreviewPayload.STREAM_CODEC,
                ClientPayloadHandler::handleEditorPreview);
        registrar.playToServer(EditorCommandPayload.TYPE, EditorCommandPayload.STREAM_CODEC,
                ModNetworking::handleEditorCommand);
        registrar.playToClient(DialogSyncPayload.TYPE, DialogSyncPayload.STREAM_CODEC,
                ClientPayloadHandler::handleDialogSync);
        registrar.playToServer(DialogActionPayload.TYPE, DialogActionPayload.STREAM_CODEC,
                ModNetworking::handleDialogAction);
        registrar.playToServer(DialogClosePayload.TYPE, DialogClosePayload.STREAM_CODEC,
                ModNetworking::handleDialogClose);
    }

    /** C2S 对话选项：服务端权威校验后推进。 */
    private static void handleDialogAction(DialogActionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                DialogueManager.handleAction(player, payload.entityId(), payload.choiceIndex());
            }
        });
    }

    /** C2S 对话关闭：校验 npcId 匹配后销会话。 */
    private static void handleDialogClose(DialogClosePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                DialogueManager.handleClose(player, payload.entityId());
            }
        });
    }

    /** 下发冲突坐标供客户端红框渲染。 */
    public static void sendRitualConflicts(ServerPlayer player, java.util.List<BlockPos> positions) {
        PacketDistributor.sendToPlayer(player, new RitualConflictPayload(positions));
    }

    /** 下发构建器预览态（有值置入/替换，空清除）。 */
    public static void sendRitualPreview(ServerPlayer player,
                                         java.util.Optional<RitualPreviewState> preview) {
        PacketDistributor.sendToPlayer(player, new RitualPreviewPayload(preview));
    }

    /** 下发编辑杖力建预览（null = 清除；带草稿时附合成 pattern JSON 供客户端每帧本地重算三色）。 */
    public static void sendEditorPreview(ServerPlayer player,
                                         @javax.annotation.Nullable com.bitsson.gensokyou.ritual.editor.EditorPreviewState state) {
        String json = null;
        if (state != null && player.level() instanceof ServerLevel level
                && com.bitsson.gensokyou.ritual.editor.RitualEditorActions.hasDrafts(level, state.patternId())) {
            json = com.bitsson.gensokyou.ritual.editor.RitualEditorActions.composedRaw(level, state.patternId())
                    .map(com.bitsson.gensokyou.ritual.editor.RitualPatternSerializer::serialize).orElse(null);
        }
        PacketDistributor.sendToPlayer(player, state == null
                ? EditorPreviewPayload.CLEAR : EditorPreviewPayload.of(state, json));
    }

    /** C2S 编辑杖命令：创造硬闸后分发动作（UI 隐藏不算防御，spec 要求逐入口重复校验）。 */
    private static void handleEditorCommand(EditorCommandPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || com.bitsson.gensokyou.ritual.editor.RitualEditorActions.gated(player) == null) {
                return;
            }
            net.minecraft.world.InteractionHand hand =
                    com.bitsson.gensokyou.ritual.editor.RitualEditorActions.handWithWand(player);
            if (hand == null) {
                return;
            }
            var stack = player.getItemInHand(hand);
            var state = com.bitsson.gensokyou.ritual.editor.RitualEditorActions.stateOf(stack);
            switch (payload.action()) {
                case EditorCommandPayload.ACTION_SELECT -> {
                    if (payload.patternId() == null) {
                        return;
                    }
                    var pattern = com.bitsson.gensokyou.ritual.RitualPatternLoader
                            .byId(payload.patternId()).orElse(null);
                    if (pattern == null || !com.bitsson.gensokyou.ritual.RitualBuilderPlacement
                            .hasLevel(pattern, payload.level())) {
                        player.displayClientMessage(Component.translatable(
                                "msg.gensokyou.editor_pattern_gone"), true);
                        return;
                    }
                    com.bitsson.gensokyou.ritual.editor.RitualEditorActions.storeState(player, hand,
                            state.withSelection(new com.bitsson.gensokyou.item.BuilderSelection(
                                    payload.patternId(), payload.level())));
                    com.bitsson.gensokyou.ritual.editor.RitualEditorActions.invalidatePreview(player);
                }
                case EditorCommandPayload.ACTION_SET_WORKSPACE -> {
                    if (payload.workspace() == null) {
                        return;
                    }
                    com.bitsson.gensokyou.ritual.editor.RitualEditorActions.storeState(player, hand,
                            state.withWorkspace(payload.level(), payload.workspace()
                                    .clamped(com.bitsson.gensokyou.item.RitualWandItem.maxDimension())));
                    com.bitsson.gensokyou.ritual.editor.RitualEditorActions.invalidatePreview(player);
                }
                case EditorCommandPayload.ACTION_CAPTURE_DRAFT ->
                        com.bitsson.gensokyou.ritual.editor.RitualEditorActions
                                .captureDraft(player, hand);
                case EditorCommandPayload.ACTION_SAVE_RITUAL ->
                        com.bitsson.gensokyou.ritual.editor.RitualEditorActions
                                .saveRitual(player, hand);
                case EditorCommandPayload.ACTION_CLEAR_ANCHOR -> {
                    com.bitsson.gensokyou.ritual.editor.RitualEditorActions.storeState(player, hand,
                            state.withAnchor(null, null));
                    com.bitsson.gensokyou.ritual.editor.RitualEditorActions.invalidatePreview(player);
                }
                default -> {
                }
            }
        });
    }

    /** C2S 选择：校验图案存在 + 品阶合法后写回手上构建器组件。 */
    private static void handleRitualSelect(RitualSelectPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            var patternOpt = RitualPatternLoader.byId(payload.patternId());
            if (patternOpt.isEmpty() || !patternOpt.get().tiers().contains(payload.tier())) {
                player.displayClientMessage(
                        Component.translatable("msg.gensokyou.builder_pattern_gone"), true);
                return;
            }
            for (InteractionHand hand : InteractionHand.values()) {
                ItemStack stack = player.getItemInHand(hand);
                if (stack.getItem() instanceof RitualBuilderItem) {
                    stack.set(ModDataComponents.RITUAL_BUILDER_SELECTION.get(),
                            new BuilderSelection(payload.patternId(), payload.tier()));
                    return;
                }
            }
        });
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

    /** 造化合成演出指令：FLIGHT 起点单发给追踪该区块的玩家（客户端程序化升空粒子）。 */
    public static void sendRitualCraftFx(ServerLevel level, BlockPos corePos,
                                         RitualCoreBlockEntity core, int durationTicks) {
        net.minecraft.core.BlockPos min = corePos.immutable();
        net.minecraft.core.BlockPos max = corePos.immutable();
        RitualMatch match = core.activeMatch();
        if (match != null) {
            int minX = corePos.getX();
            int minY = corePos.getY();
            int minZ = corePos.getZ();
            int maxX = minX;
            int maxY = minY;
            int maxZ = minZ;
            for (java.util.List<BlockPos> positions : match.keyedPositions().values()) {
                for (BlockPos p : positions) {
                    minX = Math.min(minX, p.getX());
                    minY = Math.min(minY, p.getY());
                    minZ = Math.min(minZ, p.getZ());
                    maxX = Math.max(maxX, p.getX());
                    maxY = Math.max(maxY, p.getY());
                    maxZ = Math.max(maxZ, p.getZ());
                }
            }
            min = new BlockPos(minX, minY, minZ);
            max = new BlockPos(maxX, maxY, maxZ);
        }
        PacketDistributor.sendToPlayersTrackingChunk(level,
                new net.minecraft.world.level.ChunkPos(corePos),
                new RitualCraftFxPayload(corePos, min, max, durationTicks));
    }

    /** 向正打开该核心界面的玩家重推快照：核心 BE tick 的 1Hz 心跳（含停机态）与状态跃迁（点火/换批等）共用。 */
    public static void sendRitualInfoToViewers(ServerLevel level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof RitualCoreBlockEntity core)) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            if (player.containerMenu instanceof com.bitsson.gensokyou.menu.RitualCoreMenu menu
                    && menu.pos().equals(pos)) {
                sendRitualInfo(player, level, pos, core, "");
            }
        }
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
