package com.bitsson.gensokyou.item;

import com.bitsson.gensokyou.menu.RitualBuilderMenu;
import com.bitsson.gensokyou.network.ModNetworking;
import com.bitsson.gensokyou.registry.ModBlocks;
import com.bitsson.gensokyou.registry.ModDataComponents;
import com.bitsson.gensokyou.registry.ModMenus;
import com.bitsson.gensokyou.registry.TierPalette;
import com.bitsson.gensokyou.ritual.RitualBuilderPlacement;
import com.bitsson.gensokyou.ritual.RitualMatch;
import com.bitsson.gensokyou.ritual.RitualMatcher;
import com.bitsson.gensokyou.ritual.RitualPattern;
import com.bitsson.gensokyou.ritual.RitualPatternLoader;
import com.bitsson.gensokyou.ritual.RitualPreviewState;
import com.bitsson.gensokyou.spirit.ModAttachments;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Optional;

/**
 * 仪式构建器：潜行右键（对空或对方块）打开选择菜单；右键仪式核心一键搭建所选多方块结构。
 * 走物品 {@link #useOn} 路径——核心未成型时 {@code useItemOn} 返回 PASS 让位（与召唤催化剂同链路），
 * 故不改动 RitualCoreBlock/RitualMatcher。
 */
public class RitualBuilderItem extends Item {

    public RitualBuilderItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.PASS;
        }
        if (player.isShiftKeyDown()) {
            openMenu(player, context.getHand());
            return InteractionResult.SUCCESS;
        }
        BlockState state = level.getBlockState(context.getClickedPos());
        if (!state.is(ModBlocks.RITUAL_CORE.get())) {
            player.displayClientMessage(
                    Component.translatable("msg.gensokyou.builder_core_only"), true);
            return InteractionResult.FAIL;
        }
        return handleBuild(player, context.getHand(), context.getClickedPos());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        if (level.isClientSide) {
            return InteractionResultHolder.pass(player.getItemInHand(usedHand));
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.pass(player.getItemInHand(usedHand));
        }
        if (serverPlayer.isShiftKeyDown()) {
            openMenu(serverPlayer, usedHand);
            return InteractionResultHolder.success(serverPlayer.getItemInHand(usedHand));
        }
        serverPlayer.displayClientMessage(
                Component.translatable("msg.gensokyou.builder_core_only"), true);
        return InteractionResultHolder.pass(serverPlayer.getItemInHand(usedHand));
    }

    private void openMenu(ServerPlayer player, InteractionHand hand) {
        int maxTier = com.bitsson.gensokyou.event.GuideTierProgress.worldTier(player);
        player.openMenu(new SimpleMenuProvider(
                (id, inventory, p) -> new RitualBuilderMenu(id, inventory, hand),
                Component.translatable("gui.gensokyou.builder.title")),
                buf -> {
                    buf.writeByte(hand.ordinal());
                    buf.writeVarInt(maxTier);
                });
    }

    /**
     * 两段式确认（服务端权威）：成型核心先过升级分发（他仪式/阶级不足提示让位不开 GUI），
     * 预览态与"此核心 + 手上选择 + 当前维度"全等 → 执行 {@link #doBuild} 并清态（成功/失败均清）；
     * 否则置入/替换预览态并 S2C 下发，不放置任何方块、不消耗材料。
     */
    private InteractionResult handleBuild(ServerPlayer player, InteractionHand hand, BlockPos corePos) {
        ItemStack stack = player.getItemInHand(hand);
        BuilderSelection selection = stack.get(ModDataComponents.RITUAL_BUILDER_SELECTION.get());
        if (selection == null) {
            player.displayClientMessage(
                    Component.translatable("msg.gensokyou.builder_select_first"), true);
            return InteractionResult.FAIL;
        }
        Optional<RitualPattern> patternOpt = RitualPatternLoader.byId(selection.patternId());
        if (patternOpt.isEmpty()) {
            player.displayClientMessage(
                    Component.translatable("msg.gensokyou.builder_pattern_gone"), true);
            return InteractionResult.FAIL;
        }
        if (!tierAllowed(player, selection)) {
            rejectTier(player);
            return InteractionResult.FAIL;
        }
        Optional<RitualMatch> matchOpt = RitualMatcher.matchAt(player.level(), corePos);
        if (matchOpt.isPresent()) {
            RitualMatch match = matchOpt.get();
            if (!match.patternId().equals(selection.patternId())) {
                player.displayClientMessage(
                        Component.translatable("msg.gensokyou.builder_core_other_ritual"), true);
                return InteractionResult.FAIL;
            }
            if (selection.tier() <= match.level()) {
                player.displayClientMessage(Component.translatable(
                        "msg.gensokyou.builder_level_insufficient", match.level()), true);
                return InteractionResult.FAIL;
            }
        }
        if (!RitualBuilderPlacement.hasLevel(patternOpt.get(), selection.tier())) {
            player.displayClientMessage(
                    Component.translatable("msg.gensokyou.builder_level_missing"), true);
            return InteractionResult.FAIL;
        }
        RitualPreviewState preview = player.getData(ModAttachments.RITUAL_PREVIEW.get());
        if (preview != null && preview.matches(corePos, selection, player.level().dimension())) {
            player.removeData(ModAttachments.RITUAL_PREVIEW.get());
            ModNetworking.sendRitualPreview(player, Optional.empty());
            return doBuild(player, hand, corePos);
        }
        RitualPreviewState next = new RitualPreviewState(
                selection.patternId(), selection.tier(), corePos, player.level().dimension());
        player.setData(ModAttachments.RITUAL_PREVIEW.get(), next);
        ModNetworking.sendRitualPreview(player, Optional.of(next));
        player.displayClientMessage(
                Component.translatable("msg.gensokyou.builder_preview_confirm"), true);
        return InteractionResult.SUCCESS;
    }

    private InteractionResult doBuild(ServerPlayer player, InteractionHand hand, BlockPos corePos) {
        ItemStack stack = player.getItemInHand(hand);
        BuilderSelection selection = stack.get(ModDataComponents.RITUAL_BUILDER_SELECTION.get());
        if (selection == null) {
            player.displayClientMessage(
                    Component.translatable("msg.gensokyou.builder_select_first"), true);
            return InteractionResult.FAIL;
        }
        if (RitualPatternLoader.byId(selection.patternId()).isEmpty()) {
            player.displayClientMessage(
                    Component.translatable("msg.gensokyou.builder_pattern_gone"), true);
            return InteractionResult.FAIL;
        }
        if (!tierAllowed(player, selection)) {
            rejectTier(player);
            return InteractionResult.FAIL;
        }
        RitualBuilderPlacement.Result result =
                RitualBuilderPlacement.build((ServerLevel) player.level(), corePos, player, selection);
        if (result.blocked()) {
            com.bitsson.gensokyou.network.ModNetworking.sendRitualConflicts(player, result.conflicts());
            player.displayClientMessage(
                    Component.translatable("msg.gensokyou.builder_blocked"), false);
            return InteractionResult.SUCCESS;
        }
        if (result.total() == 0) {
            player.displayClientMessage(
                    Component.translatable("msg.gensokyou.builder_nothing"), true);
        } else {
            player.displayClientMessage(Component.translatable(
                    "msg.gensokyou.builder_placed", result.placed(), result.total()), false);
            if (result.placed() < result.total()) {
                player.displayClientMessage(
                        Component.translatable("msg.gensokyou.builder_incomplete"), true);
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        BuilderSelection selection = stack.get(ModDataComponents.RITUAL_BUILDER_SELECTION.get());
        if (selection == null) {
            tooltip.add(Component.translatable("gui.gensokyou.builder.none")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }
        Optional<RitualPattern> patternOpt = RitualPatternLoader.byId(selection.patternId());
        if (patternOpt.isEmpty()) {
            tooltip.add(Component.translatable("msg.gensokyou.builder_pattern_gone")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        RitualPattern pattern = patternOpt.get();
        tooltip.add(patternName(pattern).copy().withStyle(ChatFormatting.YELLOW));
        if (pattern.hasTieredSlots()) {
            tooltip.add(Component.translatable("gui.gensokyou.builder.tier")
                    .append(Component.literal(" " + selection.tier()))
                    .withStyle(style -> style.withColor(TierPalette.textColor(selection.tier()))));
        }
        Player holder = null;
        Level level = context.level();
        if (level != null && level.isClientSide) {
            holder = ClientProbe.player();
        }
        for (RitualBuilderPlacement.Requirement req : RitualBuilderPlacement.requirements(pattern, selection.tier())) {
            boolean itemLess = req.itemLess();
            int have = (itemLess || holder == null)
                    ? 0 : holder.getInventory().countItem(req.block().asItem());
            boolean enough = itemLess || have >= req.count();
            Component line = itemLess
                    ? Component.translatable("gui.gensokyou.builder.material_line_itemless",
                            req.block().getName(), req.count())
                    : Component.translatable("gui.gensokyou.builder.material_line",
                            req.block().getName(), req.count(), have);
            tooltip.add(line.copy().withStyle(itemLess
                    ? ChatFormatting.GRAY
                    : (enough ? ChatFormatting.GREEN : ChatFormatting.RED)));
        }
    }

    /** 所选品阶是否在玩家世界进度上限内（创造由 {@code worldTier} 归入满阶）。 */
    private static boolean tierAllowed(ServerPlayer player, BuilderSelection selection) {
        return selection.tier() <= com.bitsson.gensokyou.event.GuideTierProgress.worldTier(player);
    }

    private static void rejectTier(ServerPlayer player) {
        player.displayClientMessage(Component.translatable(
                "msg.gensokyou.builder_tier_locked",
                com.bitsson.gensokyou.event.GuideTierProgress.worldTier(player)), true);
    }

    static Component patternName(RitualPattern pattern) {
        return Component.translatableWithFallback(
                "jei." + pattern.id().getNamespace() + ".ritual." + pattern.id().getPath(),
                pattern.id().getPath().replace('_', ' '));
    }

    /**
     * 客户端专属探针。独立嵌套类使 {@code Minecraft.player}（→ LocalPlayer）的引用
     * 只在真正调用时惰性解析；专用服务端 {@code isClientSide} 恒 false，永不加载本类。
     */
    private static final class ClientProbe {
        static Player player() {
            return net.minecraft.client.Minecraft.getInstance().player;
        }
    }
}
