package com.bitsson.gensokyou.command;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.danmaku.program.gameplay.ProgramMotionProbe;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid=Gensokyou.MODID)
public final class ProgramMotionProbeCommands {
    private ProgramMotionProbeCommands() {}

    @SubscribeEvent public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("gs_program_probe").requires(s->s.hasPermission(2))
                .then(Commands.literal("start").executes(c->{
                    ProgramMotionProbe.start();
                    c.getSource().sendSuccess(()->Component.literal("[GS-PROBE] started"),false);
                    return 1;
                }))
                .then(Commands.literal("status").executes(c->{
                    c.getSource().sendSuccess(()->Component.literal("[GS-PROBE] "+ProgramMotionProbe.summary()),false);
                    return 1;
                }))
                .then(Commands.literal("stop").executes(c->{
                    c.getSource().sendSuccess(()->Component.literal("[GS-PROBE] "+ProgramMotionProbe.stop()),false);
                    return 1;
                })));
    }
}
