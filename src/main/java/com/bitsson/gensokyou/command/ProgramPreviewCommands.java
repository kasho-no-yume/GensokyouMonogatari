package com.bitsson.gensokyou.command;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.danmaku.program.gameplay.ProgramPreviewHost;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid=Gensokyou.MODID)
public final class ProgramPreviewCommands {
    private ProgramPreviewCommands() {}
    @SubscribeEvent public static void register(RegisterCommandsEvent event) {
        for (String formal:java.util.List.of("formal_big_fairy","formal_yamame","formal_yakumo_ran"))
            event.getDispatcher().register(Commands.literal("gs_program_preview").requires(source->source.hasPermission(2))
                    .then(Commands.literal("start").then(Commands.literal(formal).executes(context->{
                        var source=context.getSource();
                        if (!ProgramPreviewHost.start(source.getPlayerOrException(),formal,"sphere")) {
                            source.sendFailure(Component.translatable("commands.gensokyou.program_preview.busy")); return 0;
                        }
                        source.sendSuccess(()->Component.translatable("commands.gensokyou.program_preview.started"),false); return 1;
                    }))));
        for (String template:java.util.List.of("author_volley","author_flower","author_carriers","author_hidden"))
            event.getDispatcher().register(Commands.literal("gs_program_preview").requires(source->source.hasPermission(2))
                    .then(Commands.literal("start").then(Commands.literal(template).executes(context->{
                        var source=context.getSource();
                        if (!ProgramPreviewHost.start(source.getPlayerOrException(),template,"sphere")) {
                            source.sendFailure(Component.translatable("commands.gensokyou.program_preview.busy")); return 0;
                        }
                        source.sendSuccess(()->Component.translatable("commands.gensokyou.program_preview.started"),false); return 1;
                    }))));
        event.getDispatcher().register(Commands.literal("gs_program_preview").requires(source->source.hasPermission(2))
                .then(Commands.literal("start").then(Commands.literal("roaming_tower").executes(context->{
                    var source=context.getSource();
                    if (!ProgramPreviewHost.start(source.getPlayerOrException(),"roaming_tower","sphere")) {
                        source.sendFailure(Component.translatable("commands.gensokyou.program_preview.busy")); return 0;
                    }
                    source.sendSuccess(()->Component.translatable("commands.gensokyou.program_preview.started"),false); return 1;
                })))
                .then(Commands.literal("start").then(Commands.literal("petal_release").executes(context->{
                    var source=context.getSource();
                    if (!ProgramPreviewHost.start(source.getPlayerOrException(),"petal_release","sphere")) {
                        source.sendFailure(Component.translatable("commands.gensokyou.program_preview.busy")); return 0;
                    }
                    source.sendSuccess(()->Component.translatable("commands.gensokyou.program_preview.started"),false); return 1;
                })))
                .then(Commands.literal("start").then(Commands.literal("moving_carriers").executes(context->{
                    var source=context.getSource();
                    if (!ProgramPreviewHost.start(source.getPlayerOrException(),"moving_carriers","sphere")) {
                        source.sendFailure(Component.translatable("commands.gensokyou.program_preview.busy")); return 0;
                    }
                    source.sendSuccess(()->Component.translatable("commands.gensokyou.program_preview.started"),false); return 1;
                })))
                .then(Commands.literal("start").then(Commands.literal("sphere_split").executes(context->{
                    var source=context.getSource();
                    if (!ProgramPreviewHost.start(source.getPlayerOrException(),"sphere_split","sphere")) {
                        source.sendFailure(Component.translatable("commands.gensokyou.program_preview.busy")); return 0;
                    }
                    source.sendSuccess(()->Component.translatable("commands.gensokyou.program_preview.started"),false); return 1;
                })))
                .then(Commands.literal("start").then(Commands.literal("high_frequency_circle").executes(context->{
                    var source=context.getSource();
                    if (!ProgramPreviewHost.start(source.getPlayerOrException(),"high_frequency_circle","sphere")) {
                        source.sendFailure(Component.translatable("commands.gensokyou.program_preview.busy")); return 0;
                    }
                    source.sendSuccess(()->Component.translatable("commands.gensokyou.program_preview.started"),false); return 1;
                })))
                .then(Commands.literal("start").then(Commands.literal("dynamic_warning").executes(context->{
                    var source=context.getSource();
                    if (!ProgramPreviewHost.start(source.getPlayerOrException(),"dynamic_warning","sphere")) {
                        source.sendFailure(Component.translatable("commands.gensokyou.program_preview.busy")); return 0;
                    }
                    source.sendSuccess(()->Component.translatable("commands.gensokyou.program_preview.started"),false); return 1;
                })))
                .then(Commands.literal("start").then(Commands.literal("rotating_laser").executes(context->{
                    var source=context.getSource();
                    if (!ProgramPreviewHost.start(source.getPlayerOrException(),"rotating_laser","sphere")) {
                        source.sendFailure(Component.translatable("commands.gensokyou.program_preview.busy")); return 0;
                    }
                    source.sendSuccess(()->Component.translatable("commands.gensokyou.program_preview.started"),false); return 1;
                })))
                .then(Commands.literal("start").then(Commands.literal("moving_tornado").executes(context->{
                    var source=context.getSource();
                    if (!ProgramPreviewHost.start(source.getPlayerOrException(),"moving_tornado","sphere")) {
                        source.sendFailure(Component.translatable("commands.gensokyou.program_preview.busy")); return 0;
                    }
                    source.sendSuccess(()->Component.translatable("commands.gensokyou.program_preview.started"),false); return 1;
                })))
                .then(Commands.literal("start").then(Commands.literal("motion_lab").executes(context->{
                    var source=context.getSource();
                    if (!ProgramPreviewHost.start(source.getPlayerOrException(),"motion_lab","sphere")) {
                        source.sendFailure(Component.translatable("commands.gensokyou.program_preview.busy")); return 0;
                    }
                    source.sendSuccess(()->Component.translatable("commands.gensokyou.program_preview.started"),false); return 1;
                })))
                .then(Commands.literal("start").then(Commands.literal("partial_release").executes(context->{
                    var source=context.getSource();
                    if (!ProgramPreviewHost.start(source.getPlayerOrException(),"partial_release","sphere")) {
                        source.sendFailure(Component.translatable("commands.gensokyou.program_preview.busy")); return 0;
                    }
                    source.sendSuccess(()->Component.translatable("commands.gensokyou.program_preview.started"),false); return 1;
                })))
                .then(Commands.literal("start").then(Commands.literal("composite_motion").executes(context->{
                    var source=context.getSource();
                    if (!ProgramPreviewHost.start(source.getPlayerOrException(),"composite_motion","sphere")) {
                        source.sendFailure(Component.translatable("commands.gensokyou.program_preview.busy")); return 0;
                    }
                    source.sendSuccess(()->Component.translatable("commands.gensokyou.program_preview.started"),false); return 1;
                })))
                .then(Commands.literal("start").then(Commands.literal("split_emit").executes(context->{
                    var source=context.getSource();
                    if (!ProgramPreviewHost.start(source.getPlayerOrException(),"split_emit","sphere")) {
                        source.sendFailure(Component.translatable("commands.gensokyou.program_preview.busy")); return 0;
                    }
                    source.sendSuccess(()->Component.translatable("commands.gensokyou.program_preview.started"),false); return 1;
                })))
                .then(Commands.literal("start").then(Commands.literal("split_replace").executes(context->{
                    var source=context.getSource();
                    if (!ProgramPreviewHost.start(source.getPlayerOrException(),"split_replace","sphere")) {
                        source.sendFailure(Component.translatable("commands.gensokyou.program_preview.busy")); return 0;
                    }
                    source.sendSuccess(()->Component.translatable("commands.gensokyou.program_preview.started"),false); return 1;
                })))
                .then(Commands.literal("start").then(Commands.literal("flower_sea").executes(context->{
                    var source=context.getSource();
                    if (!ProgramPreviewHost.start(source.getPlayerOrException(),"flower_sea","sphere")) {
                        source.sendFailure(Component.translatable("commands.gensokyou.program_preview.busy")); return 0;
                    }
                    source.sendSuccess(()->Component.translatable("commands.gensokyou.program_preview.started"),false); return 1;
                })))
                .then(Commands.literal("start").then(Commands.literal("tail_pearls").executes(context->{
                    var source=context.getSource();
                    if (!ProgramPreviewHost.start(source.getPlayerOrException(),"tail_pearls","sphere")) {
                        source.sendFailure(Component.translatable("commands.gensokyou.program_preview.busy")); return 0;
                    }
                    source.sendSuccess(()->Component.translatable("commands.gensokyou.program_preview.started"),false); return 1;
                })))
                .then(Commands.literal("start").then(Commands.literal("blue4").executes(context->{
                    var source=context.getSource();
                    if (!ProgramPreviewHost.start(source.getPlayerOrException(),"blue4","sphere")) {
                        source.sendFailure(Component.translatable("commands.gensokyou.program_preview.busy")); return 0;
                    }
                    source.sendSuccess(()->Component.translatable("commands.gensokyou.program_preview.started"),false); return 1;
                })))
                .then(Commands.literal("start").then(Commands.literal("blue6").executes(context->{
                    var source=context.getSource();
                    if (!ProgramPreviewHost.start(source.getPlayerOrException(),"blue6","sphere")) {
                        source.sendFailure(Component.translatable("commands.gensokyou.program_preview.busy")); return 0;
                    }
                    source.sendSuccess(()->Component.translatable("commands.gensokyou.program_preview.started"),false); return 1;
                })))
                .then(Commands.literal("start").then(Commands.literal("blue1").executes(context->{
                    var source=context.getSource();
                    if (!ProgramPreviewHost.start(source.getPlayerOrException(),"blue1","sphere")) {
                        source.sendFailure(Component.translatable("commands.gensokyou.program_preview.busy")); return 0;
                    }
                    source.sendSuccess(()->Component.translatable("commands.gensokyou.program_preview.started"),false); return 1;
                }).then(Commands.literal("star").executes(context->{
                    var source=context.getSource();
                    if (!ProgramPreviewHost.start(source.getPlayerOrException(),"blue1","star_prism")) {
                        source.sendFailure(Component.translatable("commands.gensokyou.program_preview.busy")); return 0;
                    }
                    source.sendSuccess(()->Component.translatable("commands.gensokyou.program_preview.started"),false); return 1;
                }))))
                .then(Commands.literal("start").then(Commands.literal("blue1_slice").executes(context->{
                    var source=context.getSource();
                    if (!ProgramPreviewHost.start(source.getPlayerOrException())) {
                        source.sendFailure(Component.translatable("commands.gensokyou.program_preview.busy")); return 0;
                    }
                    source.sendSuccess(()->Component.translatable("commands.gensokyou.program_preview.started"),false); return 1;
                }).then(Commands.literal("star").executes(context->{
                    var source=context.getSource();
                    if (!ProgramPreviewHost.start(source.getPlayerOrException(),"star_prism")) {
                        source.sendFailure(Component.translatable("commands.gensokyou.program_preview.busy")); return 0;
                    }
                    source.sendSuccess(()->Component.translatable("commands.gensokyou.program_preview.started"),false); return 1;
                }))))
                .then(Commands.literal("stop").executes(context->{
                    boolean stopped=ProgramPreviewHost.stop(context.getSource().getPlayerOrException().serverLevel(),"preview-command");
                    context.getSource().sendSuccess(()->Component.translatable("commands.gensokyou.program_preview.stopped"),false); return stopped?1:0;
                }))
                .then(Commands.literal("stats").executes(context->{
                    String stats=ProgramPreviewHost.stats(context.getSource().getPlayerOrException().serverLevel());
                    context.getSource().sendSuccess(()->Component.literal(stats),false); return 1;
                })));
    }
}
