package com.bitsson.gensokyou.command;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.danmaku.program.gameplay.ProgramBenchmarkHost;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid=Gensokyou.MODID)
public final class ProgramBenchmarkCommands {
    private ProgramBenchmarkCommands() {}
    @SubscribeEvent public static void register(RegisterCommandsEvent event) {
        // Player is explicit so the same command works from RCON.
        var playerRoot=Commands.literal("gs_program_benchmark").requires(s->s.hasPermission(2));
        for (String mode:new String[]{"legacy","program"}) playerRoot.then(Commands.literal("start").then(Commands.literal(mode)
                .then(Commands.argument("count",IntegerArgumentType.integer(1,2000)).then(Commands.argument("seconds",IntegerArgumentType.integer(210,600))
                        .then(Commands.argument("player",StringArgumentType.word()).executes(c->{
                            var player=c.getSource().getServer().getPlayerList().getPlayerByName(StringArgumentType.getString(c,"player"));
                            if (player==null) { c.getSource().sendFailure(Component.literal("player must be online")); return 0; }
                            try { String result=ProgramBenchmarkHost.start(player,mode,IntegerArgumentType.getInteger(c,"count"),IntegerArgumentType.getInteger(c,"seconds")); c.getSource().sendSuccess(()->Component.literal(result),false); return 1; }
                            catch (IllegalArgumentException | IllegalStateException ex) { c.getSource().sendFailure(Component.literal(ex.getMessage())); return 0; }
                        }))))));
        playerRoot.then(Commands.literal("stop").executes(c->{ c.getSource().sendSuccess(()->Component.literal(ProgramBenchmarkHost.stop(c.getSource().getLevel())),false); return 1; }));
        playerRoot.then(Commands.literal("status").executes(c->{ c.getSource().sendSuccess(()->Component.literal(ProgramBenchmarkHost.status(c.getSource().getLevel())),false); return 1; }));
        event.getDispatcher().register(playerRoot);
    }
}
