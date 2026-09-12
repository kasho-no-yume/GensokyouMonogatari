package com.bitsson.gensokyou.ritual.editor;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.item.BuilderSelection;
import com.bitsson.gensokyou.item.RitualWandItem;
import com.bitsson.gensokyou.network.ModNetworking;
import com.bitsson.gensokyou.registry.ModBlocks;
import com.bitsson.gensokyou.registry.ModDataComponents;
import com.bitsson.gensokyou.spirit.ModAttachments;
import com.bitsson.gensokyou.ritual.RitualPattern;
import com.bitsson.gensokyou.ritual.RitualPatternLoader;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 编辑杖全部服务端动作（权威闸口）：创造硬闸 + 锚点惰性校验 + 力建两段式 + 捕获存阶 + 仪式保存双写。
 * 物品 useOn 与所有 C2S payload 入口共用本类——UI 隐藏不算防御（spec）。
 */
public final class RitualEditorActions {

    private RitualEditorActions() {
    }

    // ---------------------------------------------------------------- 会话读写

    public static EditorState stateOf(ItemStack stack) {
        EditorState st = stack.get(ModDataComponents.RITUAL_EDITOR_STATE.get());
        return st == null ? EditorState.EMPTY : st;
    }

    public static void storeState(ServerPlayer player, InteractionHand hand, EditorState state) {
        player.getItemInHand(hand).set(ModDataComponents.RITUAL_EDITOR_STATE.get(), state);
    }

    /** 找到玩家任一手上的编辑杖；无则 null。 */
    @Nullable
    public static ItemStack heldWand(ServerPlayer player) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.getItem() instanceof RitualWandItem) {
                return stack;
            }
        }
        return null;
    }

    /** C2S 统一入口闸：创造 + 持杖。 */
    @Nullable
    public static ServerPlayer gated(ServerPlayer player) {
        if (!player.hasInfiniteMaterials()) {
            player.displayClientMessage(
                    Component.translatable("msg.gensokyou.editor_creative_only"), true);
            return null;
        }
        return player;
    }

    /** 锚点惰性校验（任务 3.4）：仍是核心 + 同维度 + 已加载区块。 */
    public static boolean anchorValid(ServerPlayer player, EditorState state) {
        if (!state.anchored() || state.dimension() == null) {
            return false;
        }
        ResourceKey<net.minecraft.world.level.Level> here = player.level().dimension();
        if (!ResourceKey.create(Registries.DIMENSION, state.dimension()).equals(here)) {
            return false;
        }
        if (!(player.level() instanceof ServerLevel level) || !level.isLoaded(state.anchor())) {
            return false;
        }
        return level.getBlockState(state.anchor()).is(ModBlocks.RITUAL_CORE.get());
    }

    private static boolean requireAnchor(ServerPlayer player, EditorState state) {
        if (anchorValid(player, state)) {
            return true;
        }
        player.displayClientMessage(
                Component.translatable("msg.gensokyou.editor_anchor_stale"), true);
        return false;
    }

    // ---------------------------------------------------------------- 合成视图（原 pattern ⊕ 草稿）

    /** 原 pattern 套全部阶级草稿 → 合成 raw JSON；无 base 返回 empty。 */
    public static Optional<JsonObject> composedRaw(ServerLevel level, ResourceLocation patternId) {
        Optional<JsonObject> baseOpt = RitualPatternLoader.rawOf(patternId);
        if (baseOpt.isEmpty()) {
            return Optional.empty();
        }
        JsonObject composed = baseOpt.get();
        for (Map.Entry<Integer, RitualDraft> e : RitualDraftStorage.draftsOf(level, patternId).entrySet()) {
            composed = RitualPatternSerializer.mergeDraft(composed, e.getKey(), e.getValue());
        }
        return Optional.of(composed);
    }

    /** 原 pattern 套全部阶级草稿 → 可解析的合成 pattern；失败返回 null（草稿损坏）。 */
    @Nullable
    public static RitualPattern composed(ServerLevel level, ResourceLocation patternId) {
        Optional<JsonObject> composed = composedRaw(level, patternId);
        if (composed.isEmpty()) {
            return null;
        }
        try {
            return RitualPatternLoader.parseForEdit(patternId, composed.get());
        } catch (RuntimeException exception) {
            return null; // 草稿自相矛盾（阶级保存不校验的代价，捕获端已尽力拦截）
        }
    }

    /** 该 pattern 是否存在任一阶级草稿。 */
    public static boolean hasDrafts(ServerLevel level, ResourceLocation patternId) {
        return !RitualDraftStorage.draftsOf(level, patternId).isEmpty();
    }

    /** 持杖的手（任一手）；无杖返回 null。 */
    @Nullable
    public static InteractionHand handWithWand(ServerPlayer player) {
        for (InteractionHand hand : InteractionHand.values()) {
            if (player.getItemInHand(hand).getItem() instanceof RitualWandItem) {
                return hand;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- 力建两段式（group 4）

    /** 目标阶级工作区：存储值（钳制）或切片默认。 */
    public static Workspace workspaceFor(ServerPlayer player, EditorState state,
                                         RitualPattern pattern, int level) {
        Workspace fallback = RitualEditorPlacement.defaultWorkspace(pattern, level);
        return state.workspaceFor(level, fallback).clamped(RitualWandItem.maxDimension());
    }

    /**
     * 右键锚点核心（非潜行、已锚定同核心）= 力建两段式。
     * 预览态全等 → 执行（三色清单先清后建）；否则置入/替换预览并 S2C。
     */
    public static void forceBuild(ServerPlayer player, InteractionHand hand, BlockPos corePos) {
        if (gated(player) == null) {
            return;
        }
        ItemStack stack = player.getItemInHand(hand);
        EditorState state = stateOf(stack);
        if (!requireAnchor(player, state)) {
            return;
        }
        BuilderSelection selection = state.selection();
        if (selection == null) {
            player.displayClientMessage(
                    Component.translatable("msg.gensokyou.editor_select_first"), true);
            return;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        RitualPattern pattern = composed(level, selection.patternId());
        if (pattern == null) {
            player.displayClientMessage(
                    Component.translatable("msg.gensokyou.editor_pattern_gone"), true);
            return;
        }
        if (com.bitsson.gensokyou.ritual.RitualBuilderPlacement.sliceFor(pattern, selection.tier()) == null) {
            player.displayClientMessage(
                    Component.translatable("msg.gensokyou.editor_level_missing"), true);
            return;
        }
        Workspace workspace = workspaceFor(player, state, pattern, selection.tier());
        EditorPreviewState preview = player.getData(ModAttachments.RITUAL_EDITOR_PREVIEW.get());
        if (preview != null && preview.matches(corePos, selection.patternId(), selection.tier(),
                workspace, level.dimension())) {
            player.removeData(ModAttachments.RITUAL_EDITOR_PREVIEW.get());
            ModNetworking.sendEditorPreview(player, null);
            RitualEditorPlacement.Plan plan = RitualEditorPlacement.plan(
                    level, corePos, pattern, selection.tier(), workspace);
            RitualEditorPlacement.ExecResult result =
                    RitualEditorPlacement.apply(level, plan, corePos);
            player.displayClientMessage(Component.translatable("msg.gensokyou.editor_built",
                    result.placed(), result.overwritten(), result.swept()), false);
        } else {
            EditorPreviewState next = new EditorPreviewState(
                    selection.patternId(), selection.tier(), corePos, workspace, level.dimension());
            player.setData(ModAttachments.RITUAL_EDITOR_PREVIEW.get(), next);
            ModNetworking.sendEditorPreview(player, next);
            player.displayClientMessage(
                    Component.translatable("msg.gensokyou.editor_preview_confirm"), true);
        }
    }

    /** 选择/锚点变更后清预览（防陈旧三色残留）。 */
    public static void invalidatePreview(ServerPlayer player) {
        if (player.getData(ModAttachments.RITUAL_EDITOR_PREVIEW.get()) != null) {
            player.removeData(ModAttachments.RITUAL_EDITOR_PREVIEW.get());
            ModNetworking.sendEditorPreview(player, null);
        }
    }

    // ---------------------------------------------------------------- 捕获 → 阶级保存（5.2）

    public static void captureDraft(ServerPlayer player, InteractionHand hand) {
        if (gated(player) == null) {
            return;
        }
        ItemStack stack = player.getItemInHand(hand);
        EditorState state = stateOf(stack);
        BuilderSelection selection = state.selection();
        if (selection == null) {
            player.displayClientMessage(
                    Component.translatable("msg.gensokyou.editor_select_first"), true);
            return;
        }
        if (!requireAnchor(player, state) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        RitualPattern pattern = composed(level, selection.patternId());
        if (pattern == null) {
            player.displayClientMessage(
                    Component.translatable("msg.gensokyou.editor_pattern_gone"), true);
            return;
        }
        Workspace workspace = workspaceFor(player, state, pattern, selection.tier());
        Map<BlockPos3, RitualDiffCapture.Cell> cells =
                RitualEditorPlacement.scanWorkspace(level, state.anchor(), workspace);
        RitualDiffCapture.Patch patch = RitualDiffCapture.capture(cells, selection.tier(),
                RitualEditorViews.groundOf(pattern, selection.tier()),
                RitualEditorViews.viewOf(pattern), RegistryBlockTagIndex.INSTANCE);
        boolean internalConflict = patch.violations().stream().anyMatch(v -> v.startsWith("内部冲突"));
        if (!internalConflict) {
            RitualDraftStorage.saveDraft(level, selection.patternId(), selection.tier(),
                    new RitualDraft(patch.adds(), patch.paletteAdditions()));
        } // 自检拦截坏补丁：拒绝落盘，旧草稿保持不动
        invalidatePreview(player);
        if (!internalConflict) {
            player.displayClientMessage(Component.translatable("msg.gensokyou.editor_draft_saved",
                    selection.tier(), patch.adds().size(), patch.paletteAdditions().size(),
                    patch.keptGroundCells()), false);
        }
        for (String violation : patch.violations()) {
            player.displayClientMessage(Component.literal("§c" + violation), false);
        }
    }

    // ---------------------------------------------------------------- 仪式保存（5.3/5.4）

    public static void saveRitual(ServerPlayer player, InteractionHand hand) {
        if (gated(player) == null) {
            return;
        }
        ItemStack stack = player.getItemInHand(hand);
        EditorState state = stateOf(stack);
        BuilderSelection selection = state.selection();
        if (selection == null) {
            player.displayClientMessage(
                    Component.translatable("msg.gensokyou.editor_select_first"), true);
            return;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        Optional<JsonObject> baseOpt = RitualPatternLoader.rawOf(selection.patternId());
        if (baseOpt.isEmpty()) {
            player.displayClientMessage(
                    Component.translatable("msg.gensokyou.editor_pattern_gone"), true);
            return;
        }
        JsonObject composed = baseOpt.get();
        Map<Integer, RitualDraft> drafts = RitualDraftStorage.draftsOf(level, selection.patternId());
        if (drafts.isEmpty()) {
            player.displayClientMessage(
                    Component.translatable("msg.gensokyou.editor_no_drafts"), true);
            return;
        }
        for (Map.Entry<Integer, RitualDraft> e : drafts.entrySet()) {
            composed = RitualPatternSerializer.mergeDraft(composed, e.getKey(), e.getValue());
        }
        // 全量校验：目标合成体 + 其余已加载 pattern 原文（与 python 对表同一规则集）
        List<JsonObject> corpus = new java.util.ArrayList<>();
        for (RitualPattern other : RitualPatternLoader.all()) {
            if (other.id().equals(selection.patternId())) {
                corpus.add(composed);
            } else {
                RitualPatternLoader.rawOf(other.id()).ifPresent(corpus::add);
            }
        }
        if (!corpus.contains(composed)) {
            corpus.add(composed); // pattern 从未加载成功却在草稿有内容：仅校验自身
        }
        List<RitualPatternValidator.Issue> issues = RitualPatternValidator.relevantTo(
                RitualPatternValidator.validateAll(corpus, RegistryBlockTagIndex.INSTANCE),
                selection.patternId().toString());
        List<RitualPatternValidator.Issue> errors = issues.stream().filter(i -> !i.warn()).toList();
        for (RitualPatternValidator.Issue issue : issues) {
            player.displayClientMessage(Component.literal(
                    (issue.warn() ? "§e" : "§c") + issue.line()), false);
        }
        if (!errors.isEmpty()) {
            player.displayClientMessage(Component.translatable("msg.gensokyou.editor_save_blocked",
                    errors.size()), false);
            return;
        }
        String json = RitualPatternSerializer.serialize(composed);
        RitualEditorFileOps.Outcome outcome = RitualEditorFileOps.save(
                level.getServer().getWorldPath(
                        net.minecraft.world.level.storage.LevelResource.DATAPACK_DIR),
                RitualEditorFileOps.detectDevSourceRituals(
                        net.neoforged.fml.loading.FMLPaths.GAMEDIR.get(), selection.patternId().getNamespace()),
                selection.patternId(), json);
        if (!outcome.datapackWritten()) {
            player.displayClientMessage(
                    Component.translatable("msg.gensokyou.editor_save_failed"), false);
            return;
        }
        RitualDraftStorage.clearDrafts(level, selection.patternId());
        player.displayClientMessage(Component.translatable("msg.gensokyou.editor_saved",
                selection.patternId().toString()), false);
        player.displayClientMessage(Component.translatable("msg.gensokyou.editor_reload_hint"), true);
        if (!outcome.sourceEchoed()) {
            player.displayClientMessage(
                    Component.translatable("msg.gensokyou.editor_source_skipped"), true);
        }
    }
}
