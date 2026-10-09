package com.bitsson.gensokyou.network;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.block.entity.RitualCoreBlockEntity;
import com.bitsson.gensokyou.client.ClientPayloadHandler;
import com.bitsson.gensokyou.dialogue.DialogueManager;
import com.bitsson.gensokyou.entity.AbstractDanmakuProjectile;
import com.bitsson.gensokyou.entity.AbstractTouhouBoss;
import com.bitsson.gensokyou.entity.TouhouBoss;
import com.bitsson.gensokyou.item.BuilderSelection;
import com.bitsson.gensokyou.item.RitualBuilderItem;
import com.bitsson.gensokyou.network.SpiritBombConfigPayload;
import com.bitsson.gensokyou.network.SpiritBombStatePayload;
import com.bitsson.gensokyou.registry.ModDataComponents;
import com.bitsson.gensokyou.ritual.RitualMatch;
import com.bitsson.gensokyou.ritual.RitualPatternLoader;
import com.bitsson.gensokyou.ritual.RitualPreviewState;
import com.bitsson.gensokyou.spirit.ModAttachments;
import com.bitsson.gensokyou.spirit.SkillStateData;
import com.bitsson.gensokyou.spirit.SpellCardEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
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
        registrar.playToClient(BoundSupplyCountsPayload.TYPE, BoundSupplyCountsPayload.STREAM_CODEC,
                ClientPayloadHandler::handleBoundSupplyCounts);
        registrar.playToClient(SpiritBombStatePayload.TYPE, SpiritBombStatePayload.STREAM_CODEC,
                ClientPayloadHandler::handleSpiritBombState);
        registrar.playToServer(SpiritBombConfigPayload.TYPE, SpiritBombConfigPayload.STREAM_CODEC,
                SpiritBombConfigPayload::handle);
        registrar.playToClient(EditorPreviewPayload.TYPE, EditorPreviewPayload.STREAM_CODEC,
                ClientPayloadHandler::handleEditorPreview);
        registrar.playToClient(RitualDataSyncPayload.TYPE, RitualDataSyncPayload.STREAM_CODEC,
                ClientPayloadHandler::handleRitualDataSync);
        registrar.playToServer(EditorCommandPayload.TYPE, EditorCommandPayload.STREAM_CODEC,
                ModNetworking::handleEditorCommand);
        registrar.playToClient(DialogSyncPayload.TYPE, DialogSyncPayload.STREAM_CODEC,
                ClientPayloadHandler::handleDialogSync);
        registrar.playToServer(DialogActionPayload.TYPE, DialogActionPayload.STREAM_CODEC,
                ModNetworking::handleDialogAction);
        registrar.playToServer(DialogClosePayload.TYPE, DialogClosePayload.STREAM_CODEC,
                ModNetworking::handleDialogClose);
        registrar.playToServer(com.bitsson.gensokyou.network.CrystalStorageNavPayload.TYPE,
                CrystalStorageNavPayload.STREAM_CODEC,
                ModNetworking::handleCrystalNav);
        registrar.playToServer(com.bitsson.gensokyou.network.CrystalStorageClickPayload.TYPE,
                CrystalStorageClickPayload.STREAM_CODEC,
                ModNetworking::handleCrystalClick);
        registrar.playToClient(com.bitsson.gensokyou.network.CrystalStoragePagePayload.TYPE,
                CrystalStoragePagePayload.STREAM_CODEC,
                ClientPayloadHandler::handleCrystalPage);
        registrar.playToServer(WujinzangRecipeFillPayload.TYPE,
                WujinzangRecipeFillPayload.STREAM_CODEC,
                ModNetworking::handleRecipeFill);
        registrar.playToClient(DanmakuAgePayload.TYPE, DanmakuAgePayload.STREAM_CODEC,
                ClientPayloadHandler::handleDanmakuAge);
        registrar.playToClient(DanmakuSnapshotPayload.TYPE, DanmakuSnapshotPayload.STREAM_CODEC,
                ClientPayloadHandler::handleDanmakuSnapshot);
        registrar.playToClient(DanmakuCalibrationPayload.TYPE,
                DanmakuCalibrationPayload.STREAM_CODEC,
                ClientPayloadHandler::handleDanmakuCalibration);
        registrar.playToServer(DanmakuResyncRequestPayload.TYPE,
                DanmakuResyncRequestPayload.STREAM_CODEC,
                com.bitsson.gensokyou.danmaku.render.DanmakuSyncServer::handleResyncRequest);
        registrar.playToClient(SpellCardNamePayload.TYPE, SpellCardNamePayload.STREAM_CODEC,
                ClientPayloadHandler::handleSpellCardName);
    }

    /**
     * 符卡名下标：向所有正在跟踪该 BOSS 的玩家单发一次。
     *
     * <p>用 {@code sendToPlayersTrackingEntity} 而非遍历血条受众：两者<b>本来就是同一批人</b>
     * ——血条靠 {@code StartTracking}/{@code StopTracking} 增删受众，跟踪范围也由同一套
     * {@code ServerEntity} 逻辑决定。用跟踪集不必自己维护「谁看得见这只 BOSS」。
     */
    public static void broadcastSpellCardName(Entity boss, int cardIndex) {
        PacketDistributor.sendToPlayersTrackingEntity(boss,
                new SpellCardNamePayload(boss.getId(), cardIndex));
    }

    /**
     * 符卡名补发：玩家开始跟踪某东方 BOSS 时下发其<b>当前</b>符卡下标。
     *
     * <p>⚠️ 这一步 MUST NOT 省。切卡才发的话，晚进场的玩家从没见过任何包，血条下方的
     * 符卡位会一直空白到下一次切卡——而「空白」与「这只 BOSS 没有符卡」在画面上
     * <b>完全无法区分</b>，是本能力里最难自查的一类 bug。
     *
     * <p>无符卡表的血族（{@code FlandreEntity}）走 {@code activeCardIndex()} 的缺省空值，
     * 此时不发包，客户端因而无从显示——与「本来就没有符卡」一致。
     */
    @SubscribeEvent
    public static void onStartTrackingBoss(net.neoforged.neoforge.event.entity.player.PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof TouhouBoss boss
                && event.getTarget() instanceof AbstractTouhouBoss tracked
                && event.getEntity() instanceof ServerPlayer player) {
            tracked.activeCardIndex().ifPresent(idx ->
                    PacketDistributor.sendToPlayer(player,
                            new SpellCardNamePayload(tracked.getId(), idx)));
        }
    }

    /**
     * 弹体年龄种子：客户端开始跟踪某枚弹幕时下发其当前年龄。
     *
     * <p>NeoForge 在 {@code ServerEntity.addPairing} 内、生成包发出<b>之后</b>才触发本事件，
     * 故本包必定排在生成包之后到达，客户端实体的首次 tick 时基准已就位。
     * 每个客户端各收一份——两名玩家在不同时刻开始跟踪同一枚弹，年龄本就不同。
     *
     * <p><b>与完整快照的关系</b>：本包是 5 字节的轻量兜底，只带年龄；同一次事件里
     * {@code DanmakuSyncServer} 还会发一份带时间锚点与运动状态的完整快照。两者到达
     * 顺序不保证，但<b>不构成冲突</b>：快照写入的是独立的锚点字段，而
     * {@code age()} 在锚点存在时只读锚点。因此「谁先到」不影响最终求值。
     */
    @SubscribeEvent
    public static void onStartTracking(net.neoforged.neoforge.event.entity.player.PlayerEvent.StartTracking event) {
        // getTarget() = 刚开始跟踪的实体；getEntity() 继承自 PlayerEvent，指的是玩家自己
        // （声明类型是 Player，发起端在服务端，故窄化成 ServerPlayer）。
        if (event.getTarget() instanceof AbstractDanmakuProjectile bullet
                && event.getEntity() instanceof ServerPlayer player) {
            PacketDistributor.sendToPlayer(player,
                    new DanmakuAgePayload(bullet.getId(), bullet.age()));
            bullet.noteTracking();
        }
    }

    /** C2S 无尽藏终端配方填充：按 containerId 定位菜单并服务端权威取料。 */
    private static void handleRecipeFill(WujinzangRecipeFillPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player
                    && player.containerMenu
                    instanceof com.bitsson.gensokyou.menu.WujinzangTerminalMenu menu
                    && menu.containerId() == payload.containerId()) {
                menu.handleRecipeFill(player, payload.ingredients());
            }
        });
    }

    /** C2S 无尽藏晶导航：校验容器 id 后应用搜索/滚动/排序并重推可见页。 */
    private static void handleCrystalNav(CrystalStorageNavPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player
                    && player.containerMenu instanceof com.bitsson.gensokyou.menu.CrystalGridHost menu
                    && menu.containerId() == payload.containerId()) {
                menu.applyNav(payload.query(), payload.scrollDelta(), payload.sortMode());
            }
        });
    }

    /** C2S 无尽藏晶网格手势：定位条目并执行取放（服务端权威）。 */
    private static void handleCrystalClick(CrystalStorageClickPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player
                    && player.containerMenu instanceof com.bitsson.gensokyou.menu.CrystalGridHost menu
                    && menu.containerId() == payload.containerId()) {
                menu.handleClick(player, payload.action(), payload.key());
            }
        });
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
            if (payload.tier() > com.bitsson.gensokyou.event.GuideTierProgress.worldTier(player)) {
                player.displayClientMessage(Component.translatable(
                        "msg.gensokyou.builder_tier_locked",
                        com.bitsson.gensokyou.event.GuideTierProgress.worldTier(player)), true);
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

    /** 组装并下发仪式界面全量信息（按接收者作为查看者组装）。 */
    public static void sendRitualInfo(ServerPlayer player, ServerLevel level, BlockPos pos,
                                      RitualCoreBlockEntity core, String statusKey) {
        PacketDistributor.sendToPlayer(player,
                RitualInfoPayload.snapshot(level, pos, core, statusKey, player));
    }

    /** 造化合成演出指令：FLIGHT 起点单发给追踪该区块的玩家（客户端程序化升空粒子）。 */
    public static void sendRitualCraftFx(ServerLevel level, BlockPos corePos,
                                         com.bitsson.gensokyou.ritual.SpiritPowerAccess core, int durationTicks) {
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
            } else if (player.containerMenu
                    instanceof com.bitsson.gensokyou.menu.WujinzangTerminalMenu terminal
                    && terminal.pos().equals(pos)) {
                sendRitualInfo(player, level, pos, core, "");
            }
        }
    }

    // ------------------------------------------------------------------ 发包快捷方式

    /** 客户端 → 服务端。 */
    public static void sendToServer(CustomPacketPayload payload) {
        PacketDistributor.sendToServer(payload);
    }

    /** 服务端 → 指定玩家。 */
    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    private static void handleCastSkill(CastSkillPayload payload,
                                        net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            int slot = payload.slot();
            if (slot < 0 || slot >= SkillStateData.MAX_SLOTS) {
                return;
            }
            // 阶级门（superhuman-temper）：凡人拦"需进阶"，未解锁槽位单独提示
            int tier = com.bitsson.gensokyou.spirit.grace.GraceService.tierOf(player);
            if (tier <= 0) {
                player.displayClientMessage(
                        Component.translatable("msg.gensokyou.grace_required"), true);
                return;
            }
            if (slot >= tier) {
                player.displayClientMessage(
                        Component.translatable("msg.gensokyou.skill_slot_locked"), true);
                return;
            }
            SkillStateData state = ModAttachments.skills(player);
            String cardId = state.equippedCard(slot);
            var entry = cardId == null ? null : SpellCardEffects.get(cardId);
            if (entry == null) {
                player.displayClientMessage(
                        Component.translatable("msg.gensokyou.skill_slot_empty"), true);
                return;
            }
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
            // 冷却施放瞬间经套件 CDR 折减（中途属性变化不回溯）
            ModAttachments.setSkills(player,
                    state.withCooldown(slot, now + com.bitsson.gensokyou.spirit.attr.PlayerAttributes
                            .effectiveSkillCooldown(player, entry.cooldownTicks().get())));
        });
    }

    /** 登录 / datapack 重载时下发全量仪式数据快照（客户端本地缓存兜底）。 */
    @SubscribeEvent
    public static void onDatapackSync(net.neoforged.neoforge.event.OnDatapackSyncEvent event) {
        RitualDataSyncPayload payload = RitualDataSyncPayload.snapshot();
        ServerPlayer single = event.getPlayer();
        if (single != null) {
            PacketDistributor.sendToPlayer(single, payload);
            return;
        }
        for (ServerPlayer player : event.getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }
}
