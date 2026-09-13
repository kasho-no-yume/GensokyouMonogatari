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
                        })))
                .then(Commands.literal("bafang")
                        .then(Commands.argument("core",
                                        net.minecraft.commands.arguments.coordinates.BlockPosArgument.blockPos())
                                .executes(context -> {
                                    net.minecraft.core.BlockPos pos =
                                            net.minecraft.commands.arguments.coordinates.BlockPosArgument
                                                    .getLoadedBlockPos(context, "core");
                                    return probeBafang(context.getSource(), pos);
                                })))
                .then(Commands.literal("kagutsuchi")
                        .then(Commands.argument("core",
                                        net.minecraft.commands.arguments.coordinates.BlockPosArgument.blockPos())
                                .executes(context -> {
                                    net.minecraft.core.BlockPos pos =
                                            net.minecraft.commands.arguments.coordinates.BlockPosArgument
                                                    .getLoadedBlockPos(context, "core");
                                    return probeKagutsuchi(context.getSource().getPlayerOrException(), pos);
                                }))));
    }

    /** 加具土命现场探针：匹配态/启用/批次/缓存 + 每台燃料识别值，逐项打到聊天栏。 */
    private static int probeKagutsuchi(ServerPlayer player, net.minecraft.core.BlockPos pos) {
        if (!(player.level() instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
            return 0;
        }
        var direct = com.bitsson.gensokyou.ritual.RitualMatcher.matchAt(serverLevel, pos);
        line(player, "[kagu] direct match: " + (direct.isEmpty() ? "NONE"
                : direct.get().patternId() + " L" + direct.get().level()));
        if (!(serverLevel.getBlockEntity(pos)
                instanceof com.bitsson.gensokyou.block.entity.RitualCoreBlockEntity core)) {
            line(player, "[kagu] core BE: MISSING");
            return 0;
        }
        var am = core.activeMatch();
        line(player, "[kagu] active=" + (am == null ? "null" : am.patternId() + " L" + am.level())
                + " enabled=" + core.isEnabled() + " burning=" + core.isBurning()
                + " rem=" + core.burnRemainingTicks() + "/" + core.burnTotalTicks()
                + " sp=" + core.getStored() + "/" + core.getCapacity());
        if (am == null) {
            return 1;
        }
        line(player, "[kagu] loader toggleable="
                + com.bitsson.gensokyou.ritual.RitualPatternLoader.byId(am.patternId())
                        .map(com.bitsson.gensokyou.ritual.RitualPattern::toggleable)
                        .map(String::valueOf).orElse("PATTERN-MISSING")
                + " requirements="
                + com.bitsson.gensokyou.ritual.RitualPatternLoader.byId(am.patternId())
                        .map(p -> p.requirements().size()).map(String::valueOf).orElse("?"));
        for (net.minecraft.core.BlockPos p : am.positionsOf('P')) {
            var be = serverLevel.getBlockEntity(p);
            if (be instanceof com.bitsson.gensokyou.block.entity.RitualPedestalBlockEntity pedestal) {
                net.minecraft.world.item.ItemStack held = pedestal.getHeld();
                line(player, "[kagu] P " + p.toShortString() + " held="
                        + (held.isEmpty() ? "empty"
                        : net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(held.getItem())
                                + " burn=" + held.getBurnTime(null)));
            } else {
                line(player, "[kagu] P " + p.toShortString() + " BE="
                        + (be == null ? "null" : be.getClass().getSimpleName()));
            }
        }
        return 1;
    }

    private static void line(ServerPlayer player, String text) {
        player.displayClientMessage(
                Component.literal(text).withStyle(net.minecraft.ChatFormatting.GRAY), false);
    }

    /** 八方归元储灵池探针：聚合态单行 [GS-AUTO]，日志+指令源双输出（服务器函数上下文可跑）。 */
    private static int probeBafang(net.minecraft.commands.CommandSourceStack source,
                                   net.minecraft.core.BlockPos pos) {
        if (!(source.getLevel() instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
            return 0;
        }
        String msg;
        if (serverLevel.getBlockEntity(pos)
                instanceof com.bitsson.gensokyou.block.entity.RitualCoreBlockEntity core
                && core.activeMatch() != null) {
            var am = core.activeMatch();
            msg = "[GS-AUTO] BAFANG "
                    + com.bitsson.gensokyou.ritual.behavior.BafangGuiyuanBehavior
                            .debugSummary(serverLevel, pos, am)
                    + " pattern=" + am.patternId() + " enabled=" + core.isEnabled()
                    + " be=" + core.getStored() + "/" + core.getCapacity();
        } else {
            msg = "[GS-AUTO] BAFANG NO-MATCH";
        }
        Gensokyou.LOGGER.info(msg);
        source.sendSystemMessage(Component.literal(msg));
        return 1;
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
