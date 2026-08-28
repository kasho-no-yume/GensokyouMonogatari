package com.bitsson.gensokyou.ritual.command;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.spirit.ModAttachments;
import com.bitsson.gensokyou.spirit.SpellCardEffects;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * 过渡用学习命令：正式学卡途径为幻想乡委托任务，接入后移除本命令。
 */
@EventBusSubscriber(modid = Gensokyou.MODID)
public final class LearnCommand {

    private LearnCommand() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("gs_learn")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("card", StringArgumentType.word())
                        .executes(context -> learn(
                                context.getSource(),
                                StringArgumentType.getString(context, "card")))));
    }

    private static int learn(CommandSourceStack source, String cardId) {
        if (SpellCardEffects.get(cardId) == null) {
            source.sendFailure(Component.literal(
                    "unknown card: " + cardId + " (valid: musou_fuuin, icicle_fall, light_reflect)"));
            return 0;
        }
        if (source.getEntity() instanceof ServerPlayer player) {
            var state = ModAttachments.skills(player);
            ModAttachments.setSkills(player, state.withLearned(cardId));
            source.sendSuccess(() -> Component.literal("learned: " + cardId), false);
            return 1;
        }
        return 0;
    }
}
