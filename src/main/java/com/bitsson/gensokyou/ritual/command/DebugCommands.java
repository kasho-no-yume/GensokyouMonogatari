package com.bitsson.gensokyou.ritual.command;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.config.GensokyouConfig;
import com.bitsson.gensokyou.spirit.ModAttachments;
import com.bitsson.gensokyou.spirit.SkillStateData;
import com.bitsson.gensokyou.spirit.SpiritPowerData;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * 仅作弊模式的调试指令，正式玩法不依赖。
 */
@EventBusSubscriber(modid = Gensokyou.MODID)
public final class DebugCommands {

    private DebugCommands() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("gs_debug")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("spirit")
                        .then(Commands.literal("get").executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            var data = ModAttachments.get(player);
                            feedback(player, "debug_spirit_get",
                                    String.format("%.1f", data.current()),
                                    String.format("%.1f", data.max()),
                                    data.temperLevel());
                            return 1;
                        }))
                        .then(Commands.literal("current")
                                .then(Commands.literal("set")
                                        .then(Commands.argument("value", DoubleArgumentType.doubleArg(0))
                                                .executes(context -> modifySpirit(context.getSource().getPlayerOrException(),
                                                        false, false, DoubleArgumentType.getDouble(context, "value")))))
                                .then(Commands.literal("add")
                                        .then(Commands.argument("value", DoubleArgumentType.doubleArg(-1000000))
                                                .executes(context -> modifySpirit(context.getSource().getPlayerOrException(),
                                                        false, true, DoubleArgumentType.getDouble(context, "value"))))))
                        .then(Commands.literal("max")
                                .then(Commands.literal("set")
                                        .then(Commands.argument("value", DoubleArgumentType.doubleArg(1))
                                                .executes(context -> modifySpirit(context.getSource().getPlayerOrException(),
                                                        true, false, DoubleArgumentType.getDouble(context, "value")))))
                                .then(Commands.literal("add")
                                        .then(Commands.argument("value", DoubleArgumentType.doubleArg(-1000000))
                                                .executes(context -> modifySpirit(context.getSource().getPlayerOrException(),
                                                        true, true, DoubleArgumentType.getDouble(context, "value")))))))
                .then(Commands.literal("temper")
                        .then(Commands.argument("level", IntegerArgumentType.integer(0))
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    int level = IntegerArgumentType.getInteger(context, "level");
                                    var data = ModAttachments.get(player);
                                    float baseMax = GensokyouConfig.BASE_MAX_SP.get().floatValue();
                                    float gain = GensokyouConfig.MAX_SP_GAIN_PER_TEMPER.get().floatValue();
                                    float newMax = baseMax + gain * level;
                                    float baseDamage = GensokyouConfig.BASE_SPIRIT_DAMAGE.get().floatValue();
                                    float damageGain = GensokyouConfig.SPIRIT_DAMAGE_PER_TEMPER.get().floatValue();
                                    float newDamage = baseDamage + damageGain * level;
                                    ModAttachments.set(player, new SpiritPowerData(
                                            Math.min(data.current(), newMax), newMax, level, data.regenBuffer(),
                                            newDamage));
                                    feedback(player, "debug_temper_set", level,
                                            String.format("%.1f", newMax));
                                    return 1;
                                })))
                .then(Commands.literal("cooldown")
                        .then(Commands.literal("clear").executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            var state = ModAttachments.skills(player);
                            ModAttachments.setSkills(player,
                                    new SkillStateData(state.learned(), 0L, 0L, 0L));
                            feedback(player, "debug_cd_cleared");
                            return 1;
                        }))));
    }

    /** target=true 改上限，否则改当前值；add=true 在原值基础上累加。 */
    private static int modifySpirit(ServerPlayer player, boolean targetMax, boolean add, double value) {
        var data = ModAttachments.get(player);
        float newCurrent = data.current();
        float newMax = data.max();
        if (targetMax) {
            newMax = (float) Math.max(1D, add ? newMax + value : value);
            newCurrent = Math.min(newCurrent, newMax);
        } else {
            newCurrent = (float) Math.max(0D, add ? newCurrent + value : value);
        }
        newCurrent = Math.min(newCurrent, newMax);
        ModAttachments.set(player, new SpiritPowerData(newCurrent, newMax, data.temperLevel(), data.regenBuffer(),
                data.spiritDamage()));
        feedback(player, "debug_spirit_set", String.format("%.1f", newCurrent), String.format("%.1f", newMax));
        return 1;
    }

    private static void feedback(ServerPlayer player, String key, Object... args) {
        player.displayClientMessage(Component.translatable(key, args), false);
    }
}
