package com.bitsson.gensokyou.item;

import com.bitsson.gensokyou.spirit.ModAttachments;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import vazkii.patchouli.api.PatchouliAPI;

/**
 * 《幻想乡物语》书本体（add-guide-book）。
 * 打开逻辑全部在服务端：通过 Patchouli 服务端 API 发包驱动客户端打开 GUI。
 * 首次使用翻到序言条目（玩家持久化已读标记），此后落在章节列表落地页。
 */
public class GuideBookItem extends Item {

    private static final ResourceLocation BOOK_ID =
            ResourceLocation.fromNamespaceAndPath("gensokyou", "gensokyou_book");
    private static final ResourceLocation PREFACE_ENTRY =
            ResourceLocation.fromNamespaceAndPath("gensokyou", "preface");
    private static final int LINE_COUNT = 6;

    public GuideBookItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.sidedSuccess(stack, true);
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.pass(stack);
        }
        if (PatchouliAPI.get().isStub()) {
            // Defensive branch: Patchouli is a required external dependency, so this is
            // unreachable in a normal install. Keep the old placeholder chat fallback.
            for (int i = 1; i <= LINE_COUNT; i++) {
                serverPlayer.displayClientMessage(
                        Component.translatable("book.gensokyou.line" + i), false);
            }
            level.playSound(null, player.blockPosition(),
                    SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 1F, 1F);
            return InteractionResultHolder.success(stack);
        }
        if (ModAttachments.hasReadGuidebook(serverPlayer)) {
            PatchouliAPI.get().openBookGUI(serverPlayer, BOOK_ID);
        } else {
            ModAttachments.markGuidebookRead(serverPlayer);
            PatchouliAPI.get().openBookEntry(serverPlayer, BOOK_ID, PREFACE_ENTRY, 0);
        }
        return InteractionResultHolder.success(stack);
    }
}
