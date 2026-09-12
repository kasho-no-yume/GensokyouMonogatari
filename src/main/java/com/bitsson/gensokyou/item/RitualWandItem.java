package com.bitsson.gensokyou.item;

import com.bitsson.gensokyou.config.GensokyouConfig;
import com.bitsson.gensokyou.registry.ModBlocks;
import com.bitsson.gensokyou.registry.ModDataComponents;
import com.bitsson.gensokyou.ritual.editor.EditorState;
import com.bitsson.gensokyou.ritual.editor.RitualEditorActions;
import com.bitsson.gensokyou.ritual.editor.Workspace;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * 仪式编辑杖（纯开发者工具，仅创造模式）：右键任意仪式核心 = 锚定编辑位置（成型与否皆可，无副作用）；
 * 潜行右键 = 打开编辑菜单（选仪式/阶级/工作区 + 力建/捕获存阶/仪式保存动作）。
 * 锚定、选择、按阶级工作区全存于杖 Data Component（{@link EditorState}），语义"这根杖即当前编辑会话"。
 *
 * <p>旧的 A/B 两点框选捕获已由工作区 diff 捕获（{@code ritual-capture-diff}）取代。
 */
public class RitualWandItem extends Item {

    public RitualWandItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        Player player = context.getPlayer();
        if (!(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }
        if (!serverPlayer.hasInfiniteMaterials()) {
            serverPlayer.displayClientMessage(
                    Component.translatable("msg.gensokyou.editor_creative_only"), true);
            return InteractionResult.FAIL;
        }
        if (serverPlayer.isShiftKeyDown()) {
            openMenu(serverPlayer, context.getHand());
            return InteractionResult.SUCCESS;
        }
        BlockState state = level.getBlockState(context.getClickedPos());
        if (!state.is(ModBlocks.RITUAL_CORE.get())) {
            serverPlayer.displayClientMessage(
                    Component.translatable("msg.gensokyou.editor_core_only"), true);
            return InteractionResult.FAIL;
        }
        return anchorOrBuild(serverPlayer, context.getHand(), context.getClickedPos());
    }

    /** 未锚定此核心 → 纯锚定；已锚定同核心 → 力建两段式（预览→确认）。 */
    private InteractionResult anchorOrBuild(net.minecraft.server.level.ServerPlayer player,
                                            InteractionHand hand, BlockPos corePos) {
        ItemStack stack = player.getItemInHand(hand);
        EditorState st = state(stack);
        if (st.anchored() && st.anchor().equals(corePos)) {
            if (st.selection() == null) {
                player.displayClientMessage(
                        Component.translatable("msg.gensokyou.editor_select_first"), true);
                return InteractionResult.SUCCESS;
            }
            RitualEditorActions.forceBuild(player, hand, corePos);
            return InteractionResult.SUCCESS;
        }
        stack.set(ModDataComponents.RITUAL_EDITOR_STATE.get(),
                st.withAnchor(corePos.immutable(), player.level().dimension().location()));
        RitualEditorActions.invalidatePreview(player);
        player.displayClientMessage(Component.translatable("msg.gensokyou.editor_anchored",
                corePos.toShortString()), true);
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        if (level.isClientSide) {
            return InteractionResultHolder.pass(player.getItemInHand(usedHand));
        }
        if (!(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) {
            return InteractionResultHolder.pass(player.getItemInHand(usedHand));
        }
        if (!serverPlayer.hasInfiniteMaterials()) {
            serverPlayer.displayClientMessage(
                    Component.translatable("msg.gensokyou.editor_creative_only"), true);
            return InteractionResultHolder.fail(player.getItemInHand(usedHand));
        }
        if (serverPlayer.isShiftKeyDown()) {
            openMenu(serverPlayer, usedHand);
            return InteractionResultHolder.success(player.getItemInHand(usedHand));
        }
        serverPlayer.displayClientMessage(
                Component.translatable("msg.gensokyou.editor_core_only"), true);
        return InteractionResultHolder.pass(player.getItemInHand(usedHand));
    }

    /** 潜行右键开菜单（事件闸与 useOn 共用；对空右键亦可）。 */
    public void openMenu(net.minecraft.server.level.ServerPlayer player, InteractionHand hand) {
        player.openMenu(new net.minecraft.world.SimpleMenuProvider(
                (id, inventory, p) -> new com.bitsson.gensokyou.menu.RitualEditorMenu(id, inventory, hand),
                Component.translatable("gui.gensokyou.editor.title")),
                buf -> buf.writeByte(hand.ordinal()));
    }

    public static EditorState state(ItemStack stack) {
        EditorState st = stack.get(ModDataComponents.RITUAL_EDITOR_STATE.get());
        return st == null ? EditorState.EMPTY : st;
    }

    /** 配置上限钳制（任务 3.5）。 */
    public static int maxDimension() {
        return GensokyouConfig.EDITOR_MAX_DIMENSION.get();
    }

    /** 阶级默认工作区（切片 AABB）越界时按配置钳制。 */
    public static Workspace clamp(Workspace w) {
        return w.clamped(maxDimension());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        EditorState st = state(stack);
        if (!st.anchored()) {
            tooltip.add(Component.translatable("gui.gensokyou.editor.no_anchor")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }
        tooltip.add(Component.translatable("gui.gensokyou.editor.anchor",
                st.anchor().toShortString(), st.dimension() == null ? "?" : st.dimension().getPath())
                .withStyle(ChatFormatting.AQUA));
        if (st.selection() != null) {
            tooltip.add(Component.translatable("gui.gensokyou.editor.selected",
                    st.selection().patternId().getPath(), st.selection().tier())
                    .withStyle(ChatFormatting.YELLOW));
            Workspace w = st.workspaces().get(st.selection().tier());
            if (w != null) {
                tooltip.add(Component.translatable("gui.gensokyou.editor.workspace",
                        w.sizeX(), w.sizeZ(), w.height(), w.yOffset())
                        .withStyle(ChatFormatting.GRAY));
            }
        }
    }
}
