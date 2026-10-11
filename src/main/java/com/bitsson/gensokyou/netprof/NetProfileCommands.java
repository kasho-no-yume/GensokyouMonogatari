package com.bitsson.gensokyou.netprof;

import com.bitsson.gensokyou.Gensokyou;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * {@code /gs_net} -- controls the BOSS-fight network profiler (dev facility, permission 2).
 *
 * <pre>
 *   /gs_net start [label]   begin a session (writes net_profile/server_&lt;label&gt;.jsonl)
 *   /gs_net stop            end the session
 *   /gs_net mark &lt;label&gt;    write a manual marker
 *   /gs_net status          print current state
 * </pre>
 */
@EventBusSubscriber(modid = Gensokyou.MODID)
public final class NetProfileCommands {

    private NetProfileCommands() {
    }

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("gs_net")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("start")
                        .executes(ctx -> start(ctx.getSource(), null))
                        .then(Commands.argument("label", StringArgumentType.word())
                                .executes(ctx -> start(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "label")))))
                .then(Commands.literal("stop").executes(ctx -> stop(ctx.getSource())))
                .then(Commands.literal("mark")
                        .then(Commands.argument("label", StringArgumentType.greedyString())
                                .executes(ctx -> mark(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "label")))))
                .then(Commands.literal("status").executes(ctx -> status(ctx.getSource()))));
    }

    private static int start(CommandSourceStack source, String label) {
        String path = NetProfile.start(label);
        if (path == null) {
            source.sendFailure(Component.literal("[GS-NET] 无法打开 profile 文件"));
            return 0;
        }
        for (ServerPlayer player : source.getServer().getPlayerList().getPlayers()) {
            NetProfile.inject(player);
        }
        source.sendSuccess(() -> Component.literal("[GS-NET] profiler started -> " + path), true);
        return 1;
    }

    private static int stop(CommandSourceStack source) {
        NetProfile.stop();
        source.sendSuccess(() -> Component.literal("[GS-NET] profiler stopped"), true);
        return 1;
    }

    private static int mark(CommandSourceStack source, String label) {
        NetProfile.mark(label);
        source.sendSuccess(() -> Component.literal("[GS-NET] mark: " + label), false);
        return 1;
    }

    private static int status(CommandSourceStack source) {
        String state = NetProfile.enabled() ? ("on -> " + NetProfile.file()) : "off";
        source.sendSuccess(() -> Component.literal("[GS-NET] " + state), false);
        return 1;
    }
}
