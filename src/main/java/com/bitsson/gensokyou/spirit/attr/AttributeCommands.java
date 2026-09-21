package com.bitsson.gensokyou.spirit.attr;

import com.bitsson.gensokyou.Gensokyou;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Locale;

/**
 * 属性套件调试命令：dump 全键最终值与来源分解（占位期代替属性面板 GUI），
 * 并供测试注入贡献 / 模拟变身临时层。
 */
@EventBusSubscriber(modid = Gensokyou.MODID)
public final class AttributeCommands {

    private AttributeCommands() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("gs_attributes")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("show").executes(ctx -> show(ctx.getSource().getPlayerOrException())))
                .then(Commands.literal("add")
                        .then(Commands.argument("key", StringArgumentType.word())
                                .then(Commands.argument("value", FloatArgumentType.floatArg(-1000F, 1000F))
                                        .executes(ctx -> add(ctx.getSource().getPlayerOrException(),
                                                StringArgumentType.getString(ctx, "key"),
                                                FloatArgumentType.getFloat(ctx, "value"))))))
                .then(Commands.literal("temp")
                        .then(Commands.argument("key", StringArgumentType.word())
                                .then(Commands.argument("value", FloatArgumentType.floatArg(-1000F, 1000F))
                                        .executes(ctx -> temp(ctx.getSource().getPlayerOrException(),
                                                StringArgumentType.getString(ctx, "key"),
                                                FloatArgumentType.getFloat(ctx, "value"))))))
                .then(Commands.literal("cleartemp")
                        .executes(ctx -> cleartemp(ctx.getSource().getPlayerOrException()))));
    }

    private static int show(ServerPlayer player) {
        for (AttributeKey key : AttributeKey.values()) {
            PlayerAttributes.Breakdown b = PlayerAttributes.breakdown(player, key);
            String capPart = key.cap() >= 0D
                    ? String.format(Locale.ROOT, " cap=%.2f%s", key.cap(), b.capped() ? "(!)" : "")
                    : "";
            String extra = key == AttributeKey.DANMAKU_REDUCE
                    ? String.format(Locale.ROOT, "  [灵力护壁 x%.0f]",
                            AttributeMath.wardDivisor(b.effective()))
                    : "";
            player.displayClientMessage(Component.literal(String.format(Locale.ROOT,
                            "%-18s base=%.2f perm=%+.2f temp=%+.2f%s => %.2f%s",
                            key.id(), b.base(), b.permanent(), b.temp(), capPart, b.effective(), extra)),
                    false);
        }
        return AttributeKey.values().length;
    }

    private static int add(ServerPlayer player, String keyId, float value) {
        AttributeKey key = AttributeKey.byId(keyId);
        if (key == null) {
            player.displayClientMessage(Component.translatable("msg.gensokyou.attr_unknown_key", keyId), false);
            return 0;
        }
        PlayerAttributes.setPermanent(player, key, "command", value);
        player.displayClientMessage(Component.translatable("msg.gensokyou.attr_set",
                key.langKey(), value), false);
        return 1;
    }

    private static int temp(ServerPlayer player, String keyId, float value) {
        AttributeKey key = AttributeKey.byId(keyId);
        if (key == null) {
            player.displayClientMessage(Component.translatable("msg.gensokyou.attr_unknown_key", keyId), false);
            return 0;
        }
        if (!PlayerAttributes.setTemp(player, key, "transformation-test", value)) {
            player.displayClientMessage(Component.translatable("msg.gensokyou.attr_temp_denied", keyId), false);
            return 0;
        }
        player.displayClientMessage(Component.translatable("msg.gensokyou.attr_set",
                key.langKey(), value), false);
        return 1;
    }

    private static int cleartemp(ServerPlayer player) {
        PlayerAttributes.clearTemp(player);
        player.displayClientMessage(Component.translatable("msg.gensokyou.attr_temp_cleared"), false);
        return 1;
    }
}
