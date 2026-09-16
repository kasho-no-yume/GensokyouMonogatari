package com.bitsson.gensokyou.ritual.command;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.spirit.ModAttachments;
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
                .then(Commands.literal("grace")
                        .then(Commands.argument("tier", IntegerArgumentType.integer(0, 5))
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    int tier = IntegerArgumentType.getInteger(context, "tier");
                                    setGraceTier(player, tier);
                                    var data = ModAttachments.get(player);
                                    feedback(player, "debug_grace_set", tier,
                                            String.format("%.1f", data.max()));
                                    return 1;
                                })))
                .then(Commands.literal("cooldown")
                        .then(Commands.literal("clear").executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            var state = ModAttachments.skills(player);
                            ModAttachments.setSkills(player, state.cleared());
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
                                })))
                .then(Commands.literal("nichirin")
                        .then(Commands.literal("at")
                                .then(Commands.argument("daytime", IntegerArgumentType.integer(0, 23999))
                                        .then(Commands.argument("core",
                                                        net.minecraft.commands.arguments.coordinates.BlockPosArgument.blockPos())
                                                .executes(context -> probeDaycycle(context.getSource(),
                                                        net.minecraft.commands.arguments.coordinates.BlockPosArgument
                                                                .getLoadedBlockPos(context, "core"),
                                                        IntegerArgumentType.getInteger(context, "daytime"), true)))))
                        .then(Commands.argument("core",
                                        net.minecraft.commands.arguments.coordinates.BlockPosArgument.blockPos())
                                .executes(context -> probeDaycycle(context.getSource(),
                                        net.minecraft.commands.arguments.coordinates.BlockPosArgument
                                                .getLoadedBlockPos(context, "core"), -1L, true))))
                .then(Commands.literal("tsukikage")
                        .then(Commands.literal("at")
                                .then(Commands.argument("daytime", IntegerArgumentType.integer(0, 23999))
                                        .then(Commands.argument("core",
                                                        net.minecraft.commands.arguments.coordinates.BlockPosArgument.blockPos())
                                                .executes(context -> probeDaycycle(context.getSource(),
                                                        net.minecraft.commands.arguments.coordinates.BlockPosArgument
                                                                .getLoadedBlockPos(context, "core"),
                                                        IntegerArgumentType.getInteger(context, "daytime"), false)))))
                        .then(Commands.argument("core",
                                        net.minecraft.commands.arguments.coordinates.BlockPosArgument.blockPos())
                                .executes(context -> probeDaycycle(context.getSource(),
                                        net.minecraft.commands.arguments.coordinates.BlockPosArgument
                                                .getLoadedBlockPos(context, "core"), -1L, false))))
                .then(Commands.literal("yumewatari")
                        .then(Commands.literal("beds")
                                .then(Commands.argument("core",
                                                net.minecraft.commands.arguments.coordinates.BlockPosArgument.blockPos())
                                        .executes(context -> probeYumewatari(context.getSource(),
                                                net.minecraft.commands.arguments.coordinates.BlockPosArgument
                                                        .getLoadedBlockPos(context, "core"), -1))))
                        .then(Commands.literal("settle")
                                .then(Commands.argument("core",
                                                net.minecraft.commands.arguments.coordinates.BlockPosArgument.blockPos())
                                        .executes(context -> probeYumewatari(context.getSource(),
                                                net.minecraft.commands.arguments.coordinates.BlockPosArgument
                                                        .getLoadedBlockPos(context, "core"), -2))
                                        .then(Commands.argument("sleepers", IntegerArgumentType.integer(0, 1000000))
                                                .executes(context -> probeYumewatari(context.getSource(),
                                                        net.minecraft.commands.arguments.coordinates.BlockPosArgument
                                                                .getLoadedBlockPos(context, "core"),
                                                        IntegerArgumentType.getInteger(context, "sleepers")))))))
                .then(Commands.literal("sacrifice")
                        .then(Commands.argument("core",
                                        net.minecraft.commands.arguments.coordinates.BlockPosArgument.blockPos())
                                .executes(context -> probeSacrifice(context.getSource(),
                                        net.minecraft.commands.arguments.coordinates.BlockPosArgument
                                                .getLoadedBlockPos(context, "core"))))));
    }

    /** 梦渡之座探针：包围盒/合规床清单/占用者。forcedSleepers：-1 只查看；-2 真实快照结算；≥0 注入数结算
     *  （分流路径与跳夜事件同源，仅调试）。 */
    private static int probeYumewatari(net.minecraft.commands.CommandSourceStack source,
                                       net.minecraft.core.BlockPos pos, int forcedSleepers) {
        if (!(source.getLevel() instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
            return 0;
        }
        if (!(serverLevel.getBlockEntity(pos)
                instanceof com.bitsson.gensokyou.block.entity.RitualCoreBlockEntity core)
                || core.activeMatch() == null
                || !core.activeMatch().patternId()
                        .equals(com.bitsson.gensokyou.ritual.RitualBehaviors.YUMEWATARI)) {
            source.sendSystemMessage(Component.literal(
                    "[yume] no formed yumewatari core at " + pos.toShortString()));
            Gensokyou.LOGGER.info("[yume] no formed yumewatari core at {}", pos.toShortString());
            return 0;
        }
        var am = core.activeMatch();
        var rect = com.bitsson.gensokyou.ritual.behavior.YumewatariBehavior.scanRect(am, pos);
        long bat = core.batteryStack().getItem()
                instanceof com.bitsson.gensokyou.spirit.SpiritCoreItem
                ? com.bitsson.gensokyou.spirit.SpiritCoreItem.getStored(core.batteryStack())
                : -1L;
        String probe = "[yume] L" + am.level()
                + " rect x[" + rect.minX() + ".." + rect.maxX() + "] z[" + rect.minZ() + ".." + rect.maxZ()
                + "] y=" + rect.y() + " sp=" + core.getStored() + "/" + core.getCapacity()
                + " bat=" + bat
                + " unit=" + com.bitsson.gensokyou.ritual.behavior.YumewatariBehavior
                        .unitPerSleeper(am.level());
        Gensokyou.LOGGER.info(probe);
        source.sendSystemMessage(Component.literal(probe));
        var beds = com.bitsson.gensokyou.ritual.behavior.YumewatariBehavior
                .scanBeds(serverLevel, am, pos);
        if (beds.isEmpty()) {
            source.sendSystemMessage(Component.literal("[yume] no qualifying beds"));
        }
        for (var bed : beds) {
            source.sendSystemMessage(Component.literal("[yume] bed head=" + bed.head().toShortString()
                    + " foot=" + bed.foot().toShortString() + " sleeper="
                    + (bed.sleeper() == null ? "none"
                    : bed.sleeper() instanceof net.minecraft.server.level.ServerPlayer sp
                            ? "player:" + sp.getName().getString()
                            : bed.sleeper().getType().getDescriptionId())));
        }
        if (forcedSleepers >= 0) {
            var r = com.bitsson.gensokyou.ritual.behavior.YumewatariBehavior
                    .settleWith(serverLevel, pos, am, core, forcedSleepers);
            logSettle(source, pos, r);
        } else if (forcedSleepers == -2) {
            logSettle(source, pos, com.bitsson.gensokyou.ritual.behavior.YumewatariBehavior
                    .settle(serverLevel, pos, am, core));
        }
        return 1;
    }

    private static void logSettle(net.minecraft.commands.CommandSourceStack source,
                                  net.minecraft.core.BlockPos pos,
                                  com.bitsson.gensokyou.ritual.behavior.YumewatariBehavior.Settlement r) {
        String msg = "[yume] settle at " + pos.toShortString() + " sleepers=" + r.sleepers()
                + " produced=" + r.produced() + " cache=" + r.toCache()
                + " spiritCore=" + r.toCore() + " discarded=" + r.discarded();
        Gensokyou.LOGGER.info(msg); // 函数上下文吞 sendSystemMessage，全量分流必须可见
        source.sendSystemMessage(Component.literal(msg));
    }

    /** 昼夜发电机探针（日轮/月影共用）：时刻/比例/实际产灵/峰值/缓存/槽核。forcedDayTime <0 = 用真实时刻。 */
    private static int probeDaycycle(net.minecraft.commands.CommandSourceStack source,
                                     net.minecraft.core.BlockPos pos, long forcedDayTime, boolean solar) {
        if (!(source.getLevel() instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
            return 0;
        }
        if (!(serverLevel.getBlockEntity(pos)
                instanceof com.bitsson.gensokyou.block.entity.RitualCoreBlockEntity core)
                || core.activeMatch() == null
                || !core.activeMatch().patternId().equals(solar
                        ? com.bitsson.gensokyou.ritual.RitualBehaviors.NICHIRIN
                        : com.bitsson.gensokyou.ritual.RitualBehaviors.TSUKIKAGE)) {
            source.sendSystemMessage(Component.literal("[daygen] no formed "
                    + (solar ? "nichirin" : "tsukikage") + " core at " + pos.toShortString()));
            Gensokyou.LOGGER.info("[daygen] no formed {} core at {}",
                    solar ? "nichirin" : "tsukikage", pos.toShortString());
            return 0;
        }
        var am = core.activeMatch();
        long dayTime = forcedDayTime >= 0L
                ? forcedDayTime
                : com.bitsson.gensokyou.ritual.behavior.DayCycleGeneratorBehavior.dayTimeOf(serverLevel);
        double base = solar
                ? com.bitsson.gensokyou.config.GensokyouConfig.NICHIRIN_BASE_RATE_PER_SECOND.get()
                : com.bitsson.gensokyou.config.GensokyouConfig.TSUKIKAGE_BASE_RATE_PER_SECOND.get();
        double frac = com.bitsson.gensokyou.ritual.behavior.DayCycleGeneratorBehavior
                .fraction(dayTime, solar);
        long produced = com.bitsson.gensokyou.ritual.behavior.DayCycleGeneratorBehavior
                .producedPerSecond(dayTime, am.level(), base, solar);
        long bat = core.batteryStack().getItem()
                instanceof com.bitsson.gensokyou.spirit.SpiritCoreItem
                ? com.bitsson.gensokyou.spirit.SpiritCoreItem.getStored(core.batteryStack())
                : -1L;
        String msg = "[GS-AUTO] DAYGEN " + (solar ? "NICHIRIN" : "TSUKIKAGE")
                + " L" + am.level() + " dayTime=" + Math.floorMod(dayTime, 24000L)
                + " frac=" + String.format(java.util.Locale.ROOT, "%.4f", frac)
                + " produced=" + produced
                + " sp=" + core.getStored() + "/" + core.getCapacity()
                + " bat=" + bat
                + " enabled=" + core.isEnabled();
        Gensokyou.LOGGER.info(msg);
        source.sendSystemMessage(Component.literal(msg));
        return 1;
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

    /** 献祭仪式权重表探针：单行 [GS-AUTO] SACRIFICE，含材质/条件/成本/总数/池（权重+约%）。 */
    private static int probeSacrifice(net.minecraft.commands.CommandSourceStack source,
                                      net.minecraft.core.BlockPos pos) {
        if (!(source.getLevel() instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
            return 0;
        }
        String msg;
        if (serverLevel.getBlockEntity(pos)
                instanceof com.bitsson.gensokyou.block.entity.RitualCoreBlockEntity core
                && core.activeMatch() != null) {
            var am = core.activeMatch();
            var tableOpt = com.bitsson.gensokyou.ritual.RitualLootLoader.byPattern(am.patternId());
            if (tableOpt.isEmpty()) {
                msg = "[GS-AUTO] SACRIFICE NO-DATA pattern=" + am.patternId();
            } else {
                var table = tableOpt.get();
                int skulls = com.bitsson.gensokyou.ritual.behavior.ToolSacrificeBehavior
                        .countSkulls(serverLevel, am);
                int heads = com.bitsson.gensokyou.ritual.behavior.ToolSacrificeBehavior
                        .countDragonHeads(serverLevel, am);
                boolean nether = skulls >= table.skullsRequired();
                boolean end = heads >= table.dragonHeadsRequired();
                String tier = com.bitsson.gensokyou.ritual.behavior.ToolSacrificeBehavior
                        .debugTier(serverLevel, am, table);
                if (tier == null) {
                    tier = "none";
                }
                var pool = com.bitsson.gensokyou.ritual.behavior.ToolSacrificeBehavior
                        .debugPool(table, tier, nether, end);
                double total = 0D;
                for (double w : pool.values()) {
                    total += w;
                }
                StringBuilder sb = new StringBuilder();
                for (var e : pool.entrySet()) {
                    double pct = total > 0D ? e.getValue() / total * 100D : 0D;
                    sb.append(e.getKey()).append('=')
                            .append(String.format(java.util.Locale.ROOT, "%.2f", e.getValue()))
                            .append("~")
                            .append(String.format(java.util.Locale.ROOT, "%.2f", pct))
                            .append("% ");
                }
                msg = "[GS-AUTO] SACRIFICE pattern=" + am.patternId() + " level=" + am.level()
                        + " ped=" + com.bitsson.gensokyou.ritual.RitualPedestals.positions(am).size()
                        + " tools=" + com.bitsson.gensokyou.ritual.behavior.ToolSacrificeBehavior
                                .scanTools(serverLevel, am, table).size()
                        + " tier=" + tier + " skulls=" + skulls + " heads=" + heads
                        + " nether=" + nether + " end=" + end
                        + " cost=" + com.bitsson.gensokyou.ritual.behavior.ToolSacrificeBehavior
                                .spiritCost(am.level())
                        + " count=" + com.bitsson.gensokyou.ritual.behavior.ToolSacrificeBehavior
                                .producedCount(am.level())
                        + " pool={" + sb.toString().trim() + "}";
            }
        } else {
            msg = "[GS-AUTO] SACRIFICE NO-MATCH";
        }
        Gensokyou.LOGGER.info(msg);
        source.sendSystemMessage(Component.literal(msg));
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

    /** 调试直设阶级：清空全部神恩贡献与台账后按新表逐阶重 roll（0=凡人重置；满池便于测试）。 */
    private static void setGraceTier(ServerPlayer player, int tier) {
        for (int n = 1; n <= SpiritPowerData.MAX_TIER; n++) {
            for (com.bitsson.gensokyou.spirit.attr.AttributeKey key
                    : com.bitsson.gensokyou.spirit.attr.AttributeKey.values()) {
                com.bitsson.gensokyou.spirit.attr.PlayerAttributes
                        .setPermanent(player, key, com.bitsson.gensokyou.spirit.grace.GraceService.sourceId(n), 0F);
            }
        }
        var base = ModAttachments.get(player);
        ModAttachments.set(player, new SpiritPowerData(0F, 0F, 0, 0F, 0F,
                java.util.List.of(), 0F, base.flightInertia()));
        for (int n = 1; n <= tier; n++) {
            com.bitsson.gensokyou.spirit.grace.GraceService.advance(player, n, player.getRandom());
        }
        var data = ModAttachments.get(player);
        ModAttachments.set(player, data.withCurrent(data.max()));
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
        ModAttachments.set(player, new SpiritPowerData(newCurrent, newMax, data.temperLevel(),
                data.regenBuffer(), data.spiritDamage(), data.graceLedger(), data.flightBuffer(),
                data.flightInertia()));
        feedback(player, "debug_spirit_set", String.format("%.1f", newCurrent), String.format("%.1f", newMax));
        return 1;
    }

    private static void feedback(ServerPlayer player, String key, Object... args) {
        player.displayClientMessage(Component.translatable(key, args), false);
    }
}
