package com.bitsson.gensokyou.ritual.command;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.ritual.RitualCapture;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = Gensokyou.MODID)
public final class RitualCommands {

    private RitualCommands() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("gs_ritual_capture")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("name", StringArgumentType.word())
                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, 16))
                                .then(Commands.argument("height", IntegerArgumentType.integer(1, 16))
                                        .executes(context -> capture(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "name"),
                                                IntegerArgumentType.getInteger(context, "radius"),
                                                IntegerArgumentType.getInteger(context, "height")))))));
    }

    /** 以玩家脚下为锚点中心，捕获半径 × 高度的区域为仪式结构骨架。 */
    private static int capture(CommandSourceStack source, String name, int radius, int height) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception exception) {
            source.sendFailure(Component.literal("players only"));
            return 0;
        }
        BlockPos center = player.blockPosition();
        BlockPos min = new BlockPos(center.getX() - radius, center.getY(), center.getZ() - radius);
        BlockPos max = new BlockPos(center.getX() + radius, center.getY() + height - 1,
                center.getZ() + radius);
        RitualCapture.Result result = RitualCapture.capture(source.getLevel(), name, min, max);
        Gensokyou.LOGGER.info("[ritual capture: {}]\n{}", name, result.json());
        java.nio.file.Path file = null;
        try {
            java.nio.file.Path dir = net.neoforged.fml.loading.FMLPaths.GAMEDIR.get()
                    .resolve("ritual_captures");
            java.nio.file.Files.createDirectories(dir);
            file = java.nio.file.Files.writeString(dir.resolve(name + ".json"), result.json());
        } catch (Exception exception) {
            Gensokyou.LOGGER.warn("Failed to save ritual capture {}: {}", name, exception.getMessage());
        }
        String saved = file != null ? ", saved to " + file : "";
        source.sendSuccess(() -> Component.literal(
                "Ritual skeleton '" + name + "' written to logs/latest.log (" + result.blocksCaptured()
                        + " blocks" + saved + ")"), false);
        for (String violation : result.violations()) {
            source.sendFailure(Component.literal(violation));
        }
        return result.violations().isEmpty() ? 1 : 0;
    }
}
