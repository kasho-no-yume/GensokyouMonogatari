package com.bitsson.gensokyou.client.resident;

import com.bitsson.gensokyou.Gensokyou;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;

@EventBusSubscriber(modid = Gensokyou.MODID, value = Dist.CLIENT)
public final class ResidentClientEvents {
    private ResidentClientEvents() {}

    @SubscribeEvent
    public static void reload(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(new ResidentResources());
    }

    @SubscribeEvent
    public static void commands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("gs_residents")
                .then(Commands.literal("preview")
                        .executes(ctx -> preview(ctx.getSource(), 24))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, ResidentScene.ACTIVE_LIMIT))
                                .executes(ctx -> preview(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "count")))))
                .then(Commands.literal("clear").executes(ctx -> {
                    ClientResidents.clearPreview();
                    ctx.getSource().sendSuccess(() -> Component.translatable("msg.gensokyou.residents.cleared"), false);
                    return 1;
                }))
                .then(Commands.literal("status").executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> Component.translatable("msg.gensokyou.residents.status",
                            ClientResidents.activeCount(), ClientResidents.drawnCount(), ClientResidents.previewCount()), false);
                    return 1;
                })));
    }

    private static int preview(CommandSourceStack source, int count) {
        var player = Minecraft.getInstance().player;
        if (player == null || !player.isCreative()) {
            source.sendFailure(Component.translatable("msg.gensokyou.residents.creative_only"));
            return 0;
        }
        int created = ClientResidents.preview(count);
        source.sendSuccess(() -> Component.translatable("msg.gensokyou.residents.preview", created), false);
        return created;
    }
}
