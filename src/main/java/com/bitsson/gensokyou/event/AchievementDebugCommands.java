package com.bitsson.gensokyou.event;

import com.bitsson.gensokyou.Gensokyou;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = Gensokyou.MODID)
public final class AchievementDebugCommands {

    private AchievementDebugCommands() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("gs_ach")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("give")
                        .then(Commands.argument("id", ResourceLocationArgument.id())
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    ResourceLocation id = ResourceLocationArgument.getId(context, "id");
                                    String path = id.getPath();
                                    if (path.startsWith("achievement/")) {
                                        path = path.substring("achievement/".length());
                                    }
                                    AchievementAwards.award(player, path);
                                    player.sendSystemMessage(Component.literal("Awarded " + id));
                                    return 1;
                                })))
                .then(Commands.literal("has")
                        .then(Commands.argument("id", ResourceLocationArgument.id())
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    ResourceLocation id = ResourceLocationArgument.getId(context, "id");
                                    String path = id.getPath();
                                    if (path.startsWith("achievement/")) {
                                        path = path.substring("achievement/".length());
                                    }
                                    player.sendSystemMessage(Component.literal(id + " = "
                                            + AchievementAwards.has(player, path)));
                                    return 1;
                                }))));
    }
}
