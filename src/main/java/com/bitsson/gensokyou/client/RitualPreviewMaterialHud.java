package com.bitsson.gensokyou.client;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.item.RitualBuilderItem;
import com.bitsson.gensokyou.registry.ModBlocks;
import com.bitsson.gensokyou.ritual.RitualBuilderPlacement;
import com.bitsson.gensokyou.ritual.RitualPattern;
import com.bitsson.gensokyou.ritual.RitualPatternLoader;
import com.bitsson.gensokyou.ritual.RitualPreviewState;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 预览材料缺口 HUD：预览态在场且手持构建杖时，屏幕右缘竖直居中显示
 * 缺口最大的至多 3 种方块（图标 + 名称 + ×缺口数）。
 * 需求口径 = "完成本次搭建"：白幽灵（pending）与红幽灵（conflicts）格均按
 * {@code resolveState} 解析目标方块计数，AIR 红框与不可解析格不计；
 * 每帧从共用 {@code classify} 自算（与投影渲染同一事实源），补/拆方块当帧联动，
 * 零网络同步。全齐 / 切手持 / 创造 / F1 时整块消失。
 */
@EventBusSubscriber(modid = Gensokyou.MODID, value = Dist.CLIENT)
public final class RitualPreviewMaterialHud {

    /** 行槽位边距与行距（18 容纳 16px 图标 + 1px 内边距，图标行间不重叠）。 */
    private static final int MARGIN = 4;
    private static final int SLOT = 18;
    private static final int MAX_ROWS = 3;
    private static final TextColor SHORTAGE_COLOR = TextColor.fromRgb(0xFF5555);

    /** 一种方块的缺口条目：需求格数与背包不足数。 */
    record Shortage(Block block, int need, int missing) {
    }

    private RitualPreviewMaterialHud() {
    }

    @SubscribeEvent
    public static void onRenderGuiLayer(RenderGuiLayerEvent.Post event) {
        if (event.getName() != VanillaGuiLayers.CROSSHAIR) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null
                || minecraft.options.hideGui) {
            return;
        }
        RitualPreviewState preview = ClientRitualPreviewState.active();
        if (preview == null || !minecraft.level.dimension().equals(preview.dimension())
                || !isHoldingBuilder(minecraft.player)
                || minecraft.player.hasInfiniteMaterials()
                || !minecraft.level.getBlockState(preview.corePos())
                        .is(ModBlocks.RITUAL_CORE.get())) {
            return;
        }
        Optional<RitualPattern> patternOpt = RitualPatternLoader.byId(preview.patternId());
        if (patternOpt.isEmpty()) {
            return; // 热重载宽限：与投影渲染一致，图案消失则不画
        }
        RitualPattern pattern = patternOpt.get();
        RitualBuilderPlacement.Classification classification = RitualBuilderPlacement
                .classify(minecraft.level, preview.corePos(), pattern, preview.tier());
        List<Shortage> shortages = shortages(classification, pattern, preview.tier(),
                minecraft.player.getInventory());
        if (shortages.isEmpty()) {
            return; // 材料全齐：整块消失，不留占位
        }
        draw(event.getGuiGraphics(), minecraft, shortages);
    }

    /**
     * 缺口聚合纯函数：pending + conflicts 经 {@code resolveState} 计需求
     * （null 解析与 AIR 谓词格不计，与 build 扣料口径一致），
     * 缺口 = max(0, 需求 − 背包持有)，仅留有缺口者，
     * 排序 = 缺口降序 → 需求降序 → 注册名，取前 {@value #MAX_ROWS}。
     */
    static List<Shortage> shortages(RitualBuilderPlacement.Classification classification,
                                    RitualPattern pattern, int tier, Inventory inventory) {
        Map<Block, Integer> need = new LinkedHashMap<>();
        for (RitualPattern.BlockEntry entry : classification.pending()) {
            accumulate(need, pattern, entry, tier);
        }
        for (RitualBuilderPlacement.Conflict conflict : classification.conflicts()) {
            accumulate(need, pattern, conflict.entry(), tier);
        }
        List<Shortage> out = new ArrayList<>();
        need.forEach((block, count) -> {
            if (RitualBuilderPlacement.itemLess(block)) {
                return; // 无物品形态的方块（盆栽等）不可作为物品获得，不计缺口、不点亮 HUD
            }
            int have = inventory.countItem(block.asItem());
            if (count > have) {
                out.add(new Shortage(block, count, count - have));
            }
        });
        out.sort(Comparator.comparingInt(Shortage::missing).reversed()
                .thenComparing(Comparator.comparingInt(Shortage::need).reversed())
                .thenComparing(s -> BuiltInRegistries.BLOCK.getKey(s.block()).toString()));
        return out.size() > MAX_ROWS ? List.copyOf(out.subList(0, MAX_ROWS)) : out;
    }

    private static void accumulate(Map<Block, Integer> need, RitualPattern pattern,
                                   RitualPattern.BlockEntry entry, int tier) {
        BlockState desired = RitualBuilderPlacement.resolveState(pattern, entry, tier);
        if (desired != null) {
            need.merge(desired.getBlock(), 1, Integer::sum);
        }
    }

    private static void draw(GuiGraphics graphics, Minecraft minecraft, List<Shortage> shortages) {
        Font font = minecraft.font;
        int right = minecraft.getWindow().getGuiScaledWidth() - MARGIN;
        int top = (minecraft.getWindow().getGuiScaledHeight() - shortages.size() * SLOT) / 2;
        for (int i = 0; i < shortages.size(); i++) {
            Shortage shortage = shortages.get(i);
            ItemStack probe = new ItemStack(shortage.block());
            Component line = probe.getHoverName().copy().append(" ")
                    .append(Component.translatable("gui.gensokyou.builder.hud_shortage",
                            shortage.missing()).withStyle(Style.EMPTY.withColor(SHORTAGE_COLOR)));
            int textX = right - font.width(line);
            int y = top + i * SLOT;
            graphics.renderItem(probe, textX - SLOT + 1, y + 1);
            graphics.drawString(font, line, textX, y + (SLOT - font.lineHeight) / 2 + 1,
                    ChatFormatting.WHITE.getColor(), true);
        }
    }

    private static boolean isHoldingBuilder(Player player) {
        return player.getMainHandItem().getItem() instanceof RitualBuilderItem
                || player.getOffhandItem().getItem() instanceof RitualBuilderItem;
    }
}
