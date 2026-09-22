package com.bitsson.gensokyou.command;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.balance.BalanceTestService;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * 数值测试台命令（add-balance-test-harness，权限 2）：
 * /gs_test player &lt;tier&gt; [infinite] | boss &lt;tier&gt; &lt;min|max&gt; | clear | reset
 */
@EventBusSubscriber(modid = Gensokyou.MODID)
public final class BalanceTestCommands {

    private BalanceTestCommands() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("gs_test")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("player")
                        .then(Commands.argument("tier", IntegerArgumentType.integer(1, 5))
                                .executes(ctx -> configurePlayer(ctx, false))
                                .then(Commands.literal("infinite")
                                        .executes(ctx -> configurePlayer(ctx, true)))))
                .then(Commands.literal("boss")
                        .then(Commands.argument("tier", IntegerArgumentType.integer(1, 5))
                                .then(Commands.literal("min").executes(ctx -> spawnBoss(ctx, false)))
                                .then(Commands.literal("max").executes(ctx -> spawnBoss(ctx, true)))))
                .then(Commands.literal("clear").executes(ctx -> {
                    BalanceTestService.clearBosses(ctx.getSource().getPlayerOrException());
                    return 1;
                }))
                .then(Commands.literal("reset").executes(ctx -> {
                    BalanceTestService.resetPlayer(ctx.getSource().getPlayerOrException());
                    return 1;
                })));
    }

    private static int configurePlayer(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> ctx,
                                       boolean infinite) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        BalanceTestService.configurePlayer(player, IntegerArgumentType.getInteger(ctx, "tier"), infinite);
        return 1;
    }

    private static int spawnBoss(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> ctx,
                                 boolean maxVariant) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        BalanceTestService.spawnBoss(player, IntegerArgumentType.getInteger(ctx, "tier"), maxVariant);
        return 1;
    }
}
