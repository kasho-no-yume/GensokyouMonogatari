package com.bitsson.gensokyou.entity;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.dialogue.DialogueAction;
import com.bitsson.gensokyou.dialogue.DialogueGraph;
import com.bitsson.gensokyou.dialogue.DialogueNode;
import com.bitsson.gensokyou.dialogue.DialogueOption;
import com.bitsson.gensokyou.registry.ModItems;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 占位角色：森近霖之助（香霖堂店主）。示例交易表 + 示例对话图，用于端到端验证。 */
public class RinnosukeEntity extends TouhouNpcEntity {

    public RinnosukeEntity(EntityType<? extends RinnosukeEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected void updateTrades() {
        if (level().isClientSide) {
            return;
        }
        getOffers().add(new MerchantOffer(
                new ItemCost(Items.EMERALD, 1), Optional.empty(),
                new ItemStack(ModItems.YEN.get(), 8), 12, 0, 1.0F));
        getOffers().add(new MerchantOffer(
                new ItemCost(ModItems.YEN.get(), 16), Optional.empty(),
                new ItemStack(Items.GOLD_INGOT, 1), 12, 0, 1.0F));
        getOffers().add(new MerchantOffer(
                new ItemCost(Items.EMERALD, 2),
                Optional.of(new ItemCost(ModItems.PPOINT.get(), 4)),
                new ItemStack(ModItems.SPELLCARD_STAR.get(), 1), 6, 0, 1.0F));
    }

    @Nullable
    @Override
    public DialogueGraph dialogueGraph() {
        return Dialogue.RINNOSUKE;
    }

    /** 示例对话图（代码定义，形状对齐未来 datapack JSON）。 */
    static final class Dialogue {
        static final DialogueGraph RINNOSUKE = build();

        private static DialogueGraph build() {
            Map<String, DialogueNode> nodes = new LinkedHashMap<>();
            nodes.put("greet", new DialogueNode(
                    Component.translatable("dialogue.gensokyou.rinnosuke.greet"),
                    List.of(
                            new DialogueOption(
                                    Component.translatable("dialogue.gensokyou.rinnosuke.opt.wares"),
                                    Optional.of("wares"), Optional.empty()),
                            new DialogueOption(
                                    Component.translatable("dialogue.gensokyou.rinnosuke.opt.trade"),
                                    Optional.empty(), Optional.of(DialogueAction.OPEN_TRADE)),
                            new DialogueOption(
                                    Component.translatable("dialogue.gensokyou.rinnosuke.opt.farewell"),
                                    Optional.empty(), Optional.empty()))));
            nodes.put("wares", new DialogueNode(
                    Component.translatable("dialogue.gensokyou.rinnosuke.wares"),
                    List.of(
                            new DialogueOption(
                                    Component.translatable("dialogue.gensokyou.rinnosuke.opt.trade"),
                                    Optional.empty(), Optional.of(DialogueAction.OPEN_TRADE)),
                            new DialogueOption(
                                    Component.translatable("dialogue.gensokyou.rinnosuke.opt.back"),
                                    Optional.of("greet"), Optional.empty()))));
            ResourceLocation id = Gensokyou.id("rinnosuke");
            return new DialogueGraph(id, Map.copyOf(nodes), "greet");
        }
    }
}
