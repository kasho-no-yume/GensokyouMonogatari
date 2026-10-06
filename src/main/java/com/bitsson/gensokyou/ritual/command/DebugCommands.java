package com.bitsson.gensokyou.ritual.command;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.spirit.ModAttachments;
import com.bitsson.gensokyou.spirit.SpiritPowerData;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
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
                .then(Commands.literal("progress").executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    feedback(player, "msg.gensokyou.debug_world_tier",
                            com.bitsson.gensokyou.event.GuideTierProgress.worldTier(player));
                    return 1;
                }))
                .then(Commands.literal("wujinzang")
                        .then(Commands.argument("core",
                                        net.minecraft.commands.arguments.coordinates.BlockPosArgument.blockPos())
                                .executes(context -> probeWujinzang(context.getSource(),
                                        net.minecraft.commands.arguments.coordinates.BlockPosArgument
                                                .getLoadedBlockPos(context, "core")))))
                .then(Commands.literal("kanayamahiko")
                        .then(Commands.argument("core",
                                        net.minecraft.commands.arguments.coordinates.BlockPosArgument.blockPos())
                                .executes(context -> probeKanayamahiko(context.getSource(),
                                        net.minecraft.commands.arguments.coordinates.BlockPosArgument
                                                .getLoadedBlockPos(context, "core")))))
                .then(Commands.literal("sair")
                        .then(Commands.argument("core",
                                        net.minecraft.commands.arguments.coordinates.BlockPosArgument.blockPos())
                                .executes(context -> probeSair(context.getSource(),
                                        net.minecraft.commands.arguments.coordinates.BlockPosArgument
                                                .getLoadedBlockPos(context, "core")))))
                .then(Commands.literal("bafang")
                        .then(Commands.argument("core",
                                        net.minecraft.commands.arguments.coordinates.BlockPosArgument.blockPos())
                                .executes(context -> {
                                    net.minecraft.core.BlockPos pos =
                                            net.minecraft.commands.arguments.coordinates.BlockPosArgument
                                                    .getLoadedBlockPos(context, "core");
                                    return probeBafang(context.getSource(), pos);
                                })))
                .then(Commands.literal("reiyoku")
                        .then(Commands.argument("core",
                                        net.minecraft.commands.arguments.coordinates.BlockPosArgument.blockPos())
                                .executes(context -> {
                                    net.minecraft.core.BlockPos pos =
                                            net.minecraft.commands.arguments.coordinates.BlockPosArgument
                                                    .getLoadedBlockPos(context, "core");
                                    return probeReiyoku(context.getSource(), pos);
                                })))
                .then(Commands.literal("bousen")
                        .then(Commands.literal("selftest")
                                .executes(context -> probeBousenSelftest(context.getSource())))
                        .then(Commands.argument("core",
                                        net.minecraft.commands.arguments.coordinates.BlockPosArgument.blockPos())
                                .executes(context -> {
                                    net.minecraft.core.BlockPos pos =
                                            net.minecraft.commands.arguments.coordinates.BlockPosArgument
                                                    .getLoadedBlockPos(context, "core");
                                    return probeBousen(context.getSource(), pos);
                                })))
                .then(Commands.literal("seii")
                        .then(Commands.argument("core",
                                        net.minecraft.commands.arguments.coordinates.BlockPosArgument.blockPos())
                                .executes(context -> {
                                    net.minecraft.core.BlockPos pos =
                                            net.minecraft.commands.arguments.coordinates.BlockPosArgument
                                                    .getLoadedBlockPos(context, "core");
                                    return probeSeii(context.getSource(), pos);
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
                .then(Commands.literal("barrier")
                        .then(Commands.literal("replay")
                                .then(Commands.argument("core",
                                                net.minecraft.commands.arguments.coordinates.BlockPosArgument.blockPos())
                                        .executes(context -> replayBarrier(context.getSource(),
                                                net.minecraft.commands.arguments.coordinates.BlockPosArgument
                                                        .getLoadedBlockPos(context, "core")))))
                        .then(Commands.argument("core",
                                        net.minecraft.commands.arguments.coordinates.BlockPosArgument.blockPos())
                                .executes(context -> probeBarrier(context.getSource(),
                                        net.minecraft.commands.arguments.coordinates.BlockPosArgument
                                                .getLoadedBlockPos(context, "core")))))
                .then(summonCommand())
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
                                                .getLoadedBlockPos(context, "core")))))
                .then(Commands.literal("watatsumi")
                        .then(Commands.argument("core",
                                        net.minecraft.commands.arguments.coordinates.BlockPosArgument.blockPos())
                                .executes(context -> probeWatatsumi(context.getSource(),
                                        net.minecraft.commands.arguments.coordinates.BlockPosArgument
                                                .getLoadedBlockPos(context, "core")))))
                .then(Commands.literal("sunako")
                        .then(Commands.literal("brew")
                                .then(Commands.argument("core",
                                                net.minecraft.commands.arguments.coordinates.BlockPosArgument.blockPos())
                                        .executes(context -> probeSunako(context.getSource(),
                                                net.minecraft.commands.arguments.coordinates
                                                        .BlockPosArgument.getLoadedBlockPos(context, "core"),
                                                false))))
                        .then(Commands.literal("force")
                                .then(Commands.argument("core",
                                                net.minecraft.commands.arguments.coordinates.BlockPosArgument.blockPos())
                                        .executes(context -> probeSunako(context.getSource(),
                                                net.minecraft.commands.arguments.coordinates
                                                        .BlockPosArgument.getLoadedBlockPos(context, "core"),
                                                true)))))
                .then(Commands.literal("omoikane")
                        .then(Commands.literal("scan")
                                .then(Commands.argument("core",
                                                net.minecraft.commands.arguments.coordinates.BlockPosArgument.blockPos())
                                        .executes(context -> probeOmoikane(context.getSource(),
                                                net.minecraft.commands.arguments.coordinates
                                                        .BlockPosArgument.getLoadedBlockPos(context, "core"),
                                                false, false))))
                        .then(Commands.literal("forge")
                                .then(Commands.argument("core",
                                                net.minecraft.commands.arguments.coordinates.BlockPosArgument.blockPos())
                                        .executes(context -> probeOmoikane(context.getSource(),
                                                net.minecraft.commands.arguments.coordinates
                                                        .BlockPosArgument.getLoadedBlockPos(context, "core"),
                                                true, false))))
                        .then(Commands.literal("force")
                                .then(Commands.argument("core",
                                                net.minecraft.commands.arguments.coordinates.BlockPosArgument.blockPos())
                                        .executes(context -> probeOmoikane(context.getSource(),
                                                net.minecraft.commands.arguments.coordinates
                                                        .BlockPosArgument.getLoadedBlockPos(context, "core"),
                                                true, true)))))
                .then(Commands.literal("shujou")
                        .then(Commands.argument("core",
                                        net.minecraft.commands.arguments.coordinates.BlockPosArgument.blockPos())
                                .executes(context -> probeShujou(context.getSource(),
                                        net.minecraft.commands.arguments.coordinates.BlockPosArgument
                                                .getLoadedBlockPos(context, "core")))))
                .then(Commands.literal("houjouno")
                        .then(Commands.argument("core",
                                        net.minecraft.commands.arguments.coordinates.BlockPosArgument.blockPos())
                                .executes(context -> probeHoujouno(context.getSource(),
                                        net.minecraft.commands.arguments.coordinates.BlockPosArgument
                                                .getLoadedBlockPos(context, "core")))))
                .then(Commands.literal("crystal_mode")
                        .then(Commands.argument("pos",
                                        net.minecraft.commands.arguments.coordinates.BlockPosArgument.blockPos())
                                .then(Commands.argument("mode", StringArgumentType.word())
                                        .executes(context -> setCrystalMode(context.getSource(),
                                                net.minecraft.commands.arguments.coordinates.BlockPosArgument
                                                        .getLoadedBlockPos(context, "pos"),
                                                StringArgumentType.getString(context, "mode"))))))
                .then(Commands.literal("shujou_roll")
                        .then(Commands.argument("species", StringArgumentType.string())
                                .then(Commands.argument("level", IntegerArgumentType.integer(0, 5))
                                        .executes(context -> probeShujouRoll(context.getSource(),
                                                StringArgumentType.getString(context, "species"),
                                                IntegerArgumentType.getInteger(context, "level")))))));
    }

    /**
     * 测试期临时能力：切换无尽藏晶存储模式并清空内容（正式版删除）。
     * 用法：/gs_debug crystal_mode &lt;pos&gt; &lt;typed|total&gt;。仅调试命令可达，无 GUI/物品入口。
     */
    private static int setCrystalMode(net.minecraft.commands.CommandSourceStack source,
                                      net.minecraft.core.BlockPos pos, String modeName) {
        if (!(source.getLevel().getBlockEntity(pos)
                instanceof com.bitsson.gensokyou.block.entity.CrystalBlockEntity crystal)) {
            source.sendFailure(Component.literal("[crystal] no crystal at " + pos.toShortString()));
            return 0;
        }
        com.bitsson.gensokyou.block.entity.CrystalBlockEntity.Mode mode =
                com.bitsson.gensokyou.block.entity.CrystalBlockEntity.Mode.byName(modeName, null);
        if (mode == null) {
            source.sendFailure(Component.literal("[crystal] unknown mode '" + modeName
                    + "' (expected typed|total)"));
            return 0;
        }
        crystal.setMode(mode, true);
        source.sendSystemMessage(Component.literal("[crystal] mode=" + mode.name()
                + " cleared at " + pos.toShortString()));
        return 1;
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
    /**
     * 少名探针：把批次结算的<b>每一道关卡</b>逐项打成一行机读输出。
     *
     * <p>存在的理由：批次链有 4 个静默失败点（无试剂 / 无有效台 / 缓存不足一瓶 / 写回复验全灭），
     * 且它们<b>全都不报错、不扣费</b>——实机只表现为"点了没反应"。光看代码无法判断卡在哪一关，
     * 故把中间量全部导出。
     *
     * @param force true = 先给核心灌满缓存再跑，用于把"灵力不足"从变量列表里摘出去
     */
    private static int probeSunako(net.minecraft.commands.CommandSourceStack source,
                                   net.minecraft.core.BlockPos pos, boolean force) {
        if (!(source.getLevel() instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
            return 0;
        }
        if (!(serverLevel.getBlockEntity(pos)
                instanceof com.bitsson.gensokyou.block.entity.RitualCoreBlockEntity core)) {
            source.sendFailure(net.minecraft.network.chat.Component.literal(
                    "[GS-AUTO] SUNAKO NO-CORE at " + pos));
            return 0;
        }
        var am = core.activeMatch();
        if (am == null || !am.patternId().equals(com.bitsson.gensokyou.ritual.RitualBehaviors.SUNAKO)) {
            source.sendFailure(net.minecraft.network.chat.Component.literal(
                    "[GS-AUTO] SUNAKO NO-MATCH active="
                            + (am == null ? "null" : am.patternId().toString())));
            return 0;
        }
        if (force) {
            core.receive(core.getCapacity());
        }
        net.minecraft.world.item.ItemStack reagent = core.extraSlot(
                com.bitsson.gensokyou.ritual.behavior.SunakoBrewing.REAGENT_SLOT);
        var resolution = com.bitsson.gensokyou.ritual.behavior.SunakoBrewing
                .reagent(serverLevel, core);
        var scan = com.bitsson.gensokyou.ritual.behavior.SunakoBrewing
                .scanPedestals(serverLevel, am);
        int valid = com.bitsson.gensokyou.ritual.behavior.SunakoBrewing
                .validPedestals(serverLevel, am).size();
        long stored = core.getStored();
        int level = am.level();
        long unit = com.bitsson.gensokyou.ritual.behavior.SunakoScaling.unitCostOf(level);
        int affordable = com.bitsson.gensokyou.ritual.behavior.SunakoScaling
                .affordableBottles(valid, stored, level);
        var outcome = com.bitsson.gensokyou.ritual.behavior.SunakoBrewing
                .brew(serverLevel, pos, am, core);
        String msg = "[GS-AUTO] SUNAKO level=" + level
                + " capacity=" + core.getCapacity()
                + " reagent=" + (reagent.isEmpty() ? "-"
                        : net.minecraft.core.registries.BuiltInRegistries.ITEM
                                .getKey(reagent.getItem()).toString())
                + " resolved=" + resolution.isPresent()
                + (resolution.isPresent()
                        ? "(" + resolution.get().base().unwrapKey().orElseThrow().location()
                                + ")" : "")
                + " pedestals=" + scan.pedestals()
                + " water=" + scan.water()
                + " other=" + scan.other()
                + " valid=" + valid
                + " stored=" + stored
                + " unit=" + unit
                + " affordable=" + affordable
                + " brewed=" + outcome.brewed()
                + " spent=" + outcome.spent()
                + " storedAfter=" + outcome.storedAfter()
                + " battery=" + (core.batteryStack().isEmpty() ? "-"
                        : net.minecraft.core.registries.BuiltInRegistries.ITEM
                                .getKey(core.batteryStack().getItem()).toString())
                + " held=[" + pedestalContents(serverLevel, am) + "]";
        source.sendSuccess(() -> net.minecraft.network.chat.Component.literal(msg), false);
        return 1;
    }

    /**
     * 思兼神封探针：把批次结算的<b>每一道关卡</b>逐项打成一行机读输出。
     *
     * <p>存在的理由同少名：批次链有 5 个静默失败点（无槽物 / 无有效祭品 / 装备模式无有效
     * 词条 / 灵力不足 / 随机池为空），且它们<b>全都不报错、不扣费</b>——实机只表现为
     * "点了没反应"。
     *
     * @param execute true = 真的跑一个批次（会消耗祭品与灵力）
     * @param force   true = 先给核心灌满缓存，把"灵力不足"从变量列表里摘出去
     */
    private static int probeOmoikane(net.minecraft.commands.CommandSourceStack source,
                                     net.minecraft.core.BlockPos pos, boolean execute,
                                     boolean force) {
        if (!(source.getLevel() instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
            return 0;
        }
        if (!(serverLevel.getBlockEntity(pos)
                instanceof com.bitsson.gensokyou.block.entity.RitualCoreBlockEntity core)) {
            source.sendFailure(net.minecraft.network.chat.Component.literal(
                    "[GS-AUTO] OMOIKANE NO-CORE at " + pos));
            return 0;
        }
        var am = core.activeMatch();
        if (am == null || !am.patternId().equals(com.bitsson.gensokyou.ritual.RitualBehaviors.OMOIKANE)) {
            source.sendFailure(net.minecraft.network.chat.Component.literal(
                    "[GS-AUTO] OMOIKANE NO-MATCH active="
                            + (am == null ? "null" : am.patternId().toString())));
            return 0;
        }
        if (force) {
            core.receive(core.getCapacity());
        }
        int level = am.level();
        var mode = com.bitsson.gensokyou.ritual.behavior.OmoikaneForging.modeOf(core);
        net.minecraft.world.item.ItemStack slot = core.extraSlot(
                com.bitsson.gensokyou.ritual.behavior.OmoikaneForging.GEAR_SLOT);
        var scan = com.bitsson.gensokyou.ritual.behavior.OmoikaneForging
                .scanPedestals(serverLevel, am);
        long stored = core.getStored();
        long unit = com.bitsson.gensokyou.ritual.behavior.OmoikaneScaling.unitCostOf(level);
        int entries = 0;
        String entryList = "-";
        if (mode == com.bitsson.gensokyou.ritual.behavior.OmoikaneForging.Mode.GEAR) {
            java.util.List<net.minecraft.world.item.ItemStack> books = new java.util.ArrayList<>();
            for (net.minecraft.core.BlockPos p : com.bitsson.gensokyou.ritual.RitualPedestals
                    .positions(am)) {
                if (serverLevel.getBlockEntity(p)
                        instanceof com.bitsson.gensokyou.block.entity.RitualPedestalBlockEntity ped
                        && ped.getHeld().is(net.minecraft.world.item.Items.ENCHANTED_BOOK)) {
                    books.add(ped.getHeld().copy());
                }
            }
            var plan = com.bitsson.gensokyou.ritual.behavior.OmoikaneForging
                    .planGear(core, books, level);
            entries = plan.entryCount();
            StringBuilder sb = new StringBuilder();
            for (var e : plan.levels().entrySet()) {
                if (sb.length() > 0) {
                    sb.append(';');
                }
                sb.append(e.getKey().unwrapKey().map(k -> k.location().toString()).orElse("?"))
                        .append('=').append(e.getValue());
            }
            entryList = sb.length() > 0 ? sb.toString() : "-";
        } else if (mode == com.bitsson.gensokyou.ritual.behavior.OmoikaneForging.Mode.BOOK) {
            entries = scan.lapis();
        }
        long cost = com.bitsson.gensokyou.ritual.behavior.OmoikaneScaling.batchCost(entries, level);
        boolean affordable = com.bitsson.gensokyou.ritual.behavior.OmoikaneScaling
                .canAfford(stored, entries, level);
        String msg = "[GS-AUTO] OMOIKANE level=" + level
                + " capacity=" + core.getCapacity()
                + " mode=" + mode
                + " slot=" + (slot.isEmpty() ? "-"
                        : net.minecraft.core.registries.BuiltInRegistries.ITEM
                                .getKey(slot.getItem()).toString())
                + " pedestals=" + scan.pedestals()
                + " books=" + scan.books()
                + " lapis=" + scan.lapis()
                + " other=" + scan.other()
                + " entries=" + entries
                + " entryList=[" + entryList + "]"
                + " unit=" + unit
                + " cost=" + cost
                + " stored=" + stored
                + " affordable=" + affordable;
        if (execute) {
            var outcome = com.bitsson.gensokyou.ritual.behavior.OmoikaneForging
                    .forge(serverLevel, pos, am, core);
            msg += " outcome={mode=" + outcome.mode()
                    + " entries=" + outcome.entries()
                    + " spent=" + outcome.spent()
                    + " storedAfter=" + outcome.storedAfter()
                    + " fail=" + outcome.failReason() + "}";
        } else {
            var last = com.bitsson.gensokyou.ritual.behavior.OmoikaneForging.lastOutcome();
            msg += " lastOutcome={mode=" + last.mode() + " entries=" + last.entries()
                    + " spent=" + last.spent() + " fail=" + last.failReason() + "}";
        }
        msg += " held=[" + pedestalContents(serverLevel, am) + "]";
        final String out = msg;
        source.sendSuccess(() -> net.minecraft.network.chat.Component.literal(out), false);
        return 1;
    }

    /**
     * 逐台列出祭品台台面物品 id（空台记为 {@code -}）。
     *
     * <p>{@code water=0} 这类聚合数字只告诉你"没有三途川水"，不告诉你"摆了些什么"。
     * 本实机反馈正是如此：玩家把原料放进了 GUI 的试剂槽，而三途川水该放<b>祭品台</b>。
     */
    private static String pedestalContents(net.minecraft.server.level.ServerLevel level,
                                           com.bitsson.gensokyou.ritual.RitualMatch match) {
        StringBuilder sb = new StringBuilder();
        for (net.minecraft.core.BlockPos p
                : com.bitsson.gensokyou.ritual.RitualPedestals.positions(match)) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            if (level.getBlockEntity(p)
                    instanceof com.bitsson.gensokyou.block.entity.RitualPedestalBlockEntity ped) {
                net.minecraft.world.item.ItemStack held = ped.getHeld();
                sb.append(held.isEmpty() ? "-"
                        : net.minecraft.core.registries.BuiltInRegistries.ITEM
                                .getKey(held.getItem()).toString());
            } else {
                sb.append("?");
            }
        }
        return sb.toString();
    }

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
                boolean gkLow = com.bitsson.gensokyou.ritual.behavior.ToolSacrificeBehavior
                        .countGuideBooks(serverLevel, am) >= 1;
                boolean gkHigh = com.bitsson.gensokyou.ritual.behavior.ToolSacrificeBehavior
                        .countSukimaFragments(serverLevel, am) >= 1;
                String tier = com.bitsson.gensokyou.ritual.behavior.ToolSacrificeBehavior
                        .debugTier(serverLevel, am, table);
                if (tier == null) {
                    tier = "none";
                }
                var pool = com.bitsson.gensokyou.ritual.behavior.ToolSacrificeBehavior
                        .debugPool(table, tier, nether, end, gkLow, gkHigh);
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

    /** 绵津见神之藏探针：单行 [GS-AUTO] WATATSUMI，含等级/竿数/两池掷数/解锁/成本/特产池（权重+约%）。 */
    private static int probeWatatsumi(net.minecraft.commands.CommandSourceStack source,
                                      net.minecraft.core.BlockPos pos) {
        if (!(source.getLevel() instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
            return 0;
        }
        String msg;
        if (serverLevel.getBlockEntity(pos)
                instanceof com.bitsson.gensokyou.block.entity.RitualCoreBlockEntity core
                && core.activeMatch() != null
                && com.bitsson.gensokyou.ritual.RitualBehaviors.WATATSUMI
                        .equals(core.activeMatch().patternId())) {
            msg = "[GS-AUTO] WATATSUMI "
                    + com.bitsson.gensokyou.ritual.behavior.WatatsumiBehavior
                            .debugSummary(serverLevel, core.activeMatch())
                    + " enabled=" + core.isEnabled()
                    + " be=" + core.getStored() + "/" + core.getCapacity();
        } else {
            msg = "[GS-AUTO] WATATSUMI NO-MATCH";
        }
        Gensokyou.LOGGER.info(msg);
        source.sendSystemMessage(Component.literal(msg));
        return 1;
    }

    private static int probeHoujouno(net.minecraft.commands.CommandSourceStack source,
                                     net.minecraft.core.BlockPos pos) {
        if (!(source.getLevel() instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
            return 0;
        }
        String msg;
        if (serverLevel.getBlockEntity(pos)
                instanceof com.bitsson.gensokyou.block.entity.RitualCoreBlockEntity core
                && core.activeMatch() != null
                && com.bitsson.gensokyou.ritual.RitualBehaviors.HOUJOUNO_TEIHOU
                        .equals(core.activeMatch().patternId())) {
            msg = "[GS-AUTO] HOUJOUNO "
                    + com.bitsson.gensokyou.ritual.behavior.HoujounoTeihouBehavior
                            .debugSummary(serverLevel, pos, core.activeMatch(), core);
        } else {
            msg = "[GS-AUTO] HOUJOUNO NO-MATCH";
        }
        Gensokyou.LOGGER.info(msg);
        source.sendSystemMessage(Component.literal(msg));
        return 1;
    }

    /** 众生余录探针：单行 [GS-AUTO] SHUJOU，含等级/祭品台/有效典籍/去重 species/成本/容量/L2/试掷。 */
    private static int probeShujou(net.minecraft.commands.CommandSourceStack source,
                                   net.minecraft.core.BlockPos pos) {
        if (!(source.getLevel() instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
            return 0;
        }
        String msg;
        if (serverLevel.getBlockEntity(pos)
                instanceof com.bitsson.gensokyou.block.entity.RitualCoreBlockEntity core
                && core.activeMatch() != null
                && com.bitsson.gensokyou.ritual.RitualBehaviors.SHUJOU
                        .equals(core.activeMatch().patternId())) {
            msg = "[GS-AUTO] SHUJOU "
                    + com.bitsson.gensokyou.ritual.behavior.ShujouYorokuBehavior
                            .debugSummary(serverLevel, core.activeMatch())
                    + " enabled=" + core.isEnabled()
                    + " be=" + core.getStored() + "/" + core.getCapacity()
                    + " " + com.bitsson.gensokyou.ritual.behavior.ShujouYorokuBehavior
                            .debugRoll(serverLevel, pos, core.activeMatch());
        } else {
            msg = "[GS-AUTO] SHUJOU NO-MATCH";
        }
        Gensokyou.LOGGER.info(msg);
        source.sendSystemMessage(Component.literal(msg));
        return 1;
    }

    /** 无结构依赖掷骰探针：以假玩家凭证掷指定 species 的死亡表，并按结算口径套用 L2 倍率。 */
    private static int probeShujouRoll(net.minecraft.commands.CommandSourceStack source,
                                       String rawId, int level) {
        if (!(source.getLevel() instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
            return 0;
        }
        net.minecraft.resources.ResourceLocation id =
                net.minecraft.resources.ResourceLocation.tryParse(rawId.trim());
        String msg;
        if (id == null) {
            msg = "[GS-AUTO] SHUJOU-ROLL bad-id=" + rawId;
        } else {
            net.minecraft.core.BlockPos pos =
                    net.minecraft.core.BlockPos.containing(source.getPosition());
            int mult = com.bitsson.gensokyou.ritual.behavior.ShujouYorokuBehavior.l2Multiplier(level);
            java.util.List<net.minecraft.world.item.ItemStack> out =
                    com.bitsson.gensokyou.ritual.behavior.ShujouYorokuBehavior
                            .rollSpecies(serverLevel, pos, id, level);
            java.util.Map<String, Integer> agg = new java.util.LinkedHashMap<>();
            for (net.minecraft.world.item.ItemStack stack : out) {
                if (!stack.isEmpty()) {
                    agg.merge(net.minecraft.core.registries.BuiltInRegistries.ITEM
                            .getKey(stack.getItem()).toString(), stack.getCount(), Integer::sum);
                }
            }
            // 与结算口径一致：2 阶在抢夺结果上再乘倍率
            if (mult > 1) {
                agg.replaceAll((k, v) -> (int) Math.min(Integer.MAX_VALUE, (long) v * mult));
            }
            StringBuilder sb = new StringBuilder();
            agg.forEach((k, v) -> sb.append(sb.length() == 0 ? "" : " ")
                    .append(k).append('x').append(v));
            msg = "[GS-AUTO] SHUJOU-ROLL species=" + id + " level=" + level
                    + " looting=" + (level >= 1
                            ? com.bitsson.gensokyou.config.GensokyouConfig.SHUJOU_LOOTING_LEVEL.get() : 0)
                    + " mult=" + mult
                    + " -> " + (sb.length() == 0 ? "<empty>" : sb.toString());
        }
        Gensokyou.LOGGER.info(msg);
        source.sendSystemMessage(Component.literal(msg));
        return 1;
    }

    private static int probeKanayamahiko(net.minecraft.commands.CommandSourceStack source,
                                          net.minecraft.core.BlockPos pos) {
        if (!(source.getLevel() instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
            return 0;
        }
        String msg;
        if (serverLevel.getBlockEntity(pos)
                instanceof com.bitsson.gensokyou.block.entity.RitualCoreBlockEntity core
                && core.activeMatch() != null
                && com.bitsson.gensokyou.ritual.RitualBehaviors.KANAYAMAHIKO
                        .equals(core.activeMatch().patternId())) {
            msg = "[GS-AUTO] KANAYAMAHIKO "
                    + com.bitsson.gensokyou.ritual.behavior.KanayamahikoBehavior
                            .debugSummary(serverLevel, pos, core.activeMatch(), core)
                    + " enabled=" + core.isEnabled();
        } else {
            msg = "[GS-AUTO] KANAYAMAHIKO NO-MATCH";
        }
        Gensokyou.LOGGER.info(msg);
        source.sendSystemMessage(Component.literal(msg));
        return 1;
    }

    /** 八方归元储灵池探针：聚合态单行 [GS-AUTO]，日志+指令源双输出（服务器函数上下文可跑）。 */
    /** 无尽藏探针：单行 [GS-AUTO] WUJINZANG，含分区/容量/耗电/被占。 */
    private static int probeWujinzang(net.minecraft.commands.CommandSourceStack source,
                                      net.minecraft.core.BlockPos pos) {
        if (!(source.getLevel() instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
            return 0;
        }
        String msg;
        if (serverLevel.getBlockEntity(pos)
                instanceof com.bitsson.gensokyou.block.entity.RitualCoreBlockEntity core
                && core.activeMatch() != null
                && com.bitsson.gensokyou.ritual.RitualBehaviors.WUJINZANG
                        .equals(core.activeMatch().patternId())) {
            msg = "[GS-AUTO] WUJINZANG "
                    + com.bitsson.gensokyou.ritual.behavior.WujinzangBehavior
                            .debugSummary(serverLevel, pos, core.activeMatch())
                    + " enabled=" + core.isEnabled();
        } else {
            msg = "[GS-AUTO] WUJINZANG NO-MATCH";
        }
        Gensokyou.LOGGER.info(msg);
        source.sendSystemMessage(Component.literal(msg));
        return 1;
    }

    /** 星移之仪探针：机读单行（phase / 核阶 / 花费 / 缓存 / 暂存条数 / 洗练度）。 */
    private static int probeSeii(net.minecraft.commands.CommandSourceStack source,
                                 net.minecraft.core.BlockPos pos) {
        if (!(source.getLevel() instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
            return 0;
        }
        String msg;
        if (serverLevel.getBlockEntity(pos)
                instanceof com.bitsson.gensokyou.block.entity.RitualCoreBlockEntity core
                && core.activeMatch() != null) {
            var am = core.activeMatch();
            msg = "[GS-AUTO] SEII "
                    + com.bitsson.gensokyou.ritual.behavior.SeiiService.debugSummary(core)
                    + " pattern=" + am.patternId() + " enabled=" + core.isEnabled()
                    + " ladder cap=" + com.bitsson.gensokyou.item.weapon.SeiiNumbers.capacity(am.level())
                    + " inRate=" + com.bitsson.gensokyou.item.weapon.SeiiNumbers.inRate(am.level())
                    + " maxCoreTier=" + com.bitsson.gensokyou.item.weapon.SeiiNumbers.maxCoreTier(am.level());
        } else {
            msg = "[GS-AUTO] SEII NO-MATCH";
        }
        source.sendSuccess(() -> net.minecraft.network.chat.Component.literal(msg), false);
        return 1;
    }

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

    /**
     * 结界破坏探针：单行 [GS-AUTO] BARRIER，含五态、缓存/上限、流失、供灵诊断与双门在位情况。
     * 供灵不足导致进度条倒退时，本行是唯一的机读判据（state=INSUFFICIENT + net&lt;0）。
     */
    private static int probeBarrier(net.minecraft.commands.CommandSourceStack source,
                                    net.minecraft.core.BlockPos pos) {
        if (!(source.getLevel() instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
            return 0;
        }
        if (!(serverLevel.getBlockEntity(pos)
                instanceof com.bitsson.gensokyou.block.entity.RitualCoreBlockEntity core)
                || core.activeMatch() == null
                || !core.activeMatch().patternId()
                        .equals(com.bitsson.gensokyou.ritual.RitualBehaviors.BARRIER_BREAK)) {
            emitGs(source, "[GS-AUTO] BARRIER NO-MATCH");
            return 1;
        }
        emitGs(source, "[GS-AUTO] BARRIER "
                + com.bitsson.gensokyou.ritual.behavior.BarrierBreakBehavior
                        .debugSummary(serverLevel, pos, core.activeMatch(), core)
                + " level=" + core.activeMatch().level());
        for (String row : com.bitsson.gensokyou.ritual.behavior.BarrierBreakBehavior
                .debugOfferings(serverLevel, core.activeMatch(), core)) {
            emitGs(source, "[GS-AUTO] BARRIER " + row);
        }
        return 1;
    }

    private static void emitGs(net.minecraft.commands.CommandSourceStack source, String msg) {
        Gensokyou.LOGGER.info(msg);
        source.sendSystemMessage(Component.literal(msg));
    }

    /**
     * 资源源探测：重播 [GS-AUTO] BARRIER REPLAY —— 让已开启的隙间门再放一遍「结界崩解」演出。
     *
     * <p>仪式是<b>永久闩锁</b>的且开启瞬间祭品已被消耗，闩锁态下没有任何别的办法让演出
     * 再跑一遍；没有这个入口，表现就只能盲写（历史上正是"观测不到"被当成了"不存在"）。
     * 刻意不触碰闩锁、不重跑需求判定、不消耗祭品。
     */
    /**
     * {@code /gs_debug summon <core>}：自检单行。
     * {@code /gs_debug summon phase <core> <charging|burst|pillar>}：强制跳到指定演出相位。
     *
     * <p>单独抽成方法：内联写需要 5 层嵌套，闭括号数到眼花——抽出来后调用点只剩
     * {@code .then(summonCommand())}，与相邻的 {@code crystal_mode} 同款深度。
     */
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<net.minecraft.commands.CommandSourceStack> summonCommand() {
        return Commands.literal("summon")
                .then(Commands.argument("core",
                                net.minecraft.commands.arguments.coordinates.BlockPosArgument.blockPos())
                        .executes(context -> probeSummon(context.getSource(),
                                net.minecraft.commands.arguments.coordinates.BlockPosArgument
                                        .getLoadedBlockPos(context, "core"))))
                .then(Commands.literal("phase")
                        .then(Commands.argument("core",
                                        net.minecraft.commands.arguments.coordinates.BlockPosArgument.blockPos())
                                .then(Commands.argument("phase", StringArgumentType.word())
                                        .executes(context -> setSummonPhase(context.getSource(),
                                                net.minecraft.commands.arguments.coordinates.BlockPosArgument
                                                        .getLoadedBlockPos(context, "core"),
                                                StringArgumentType.getString(context, "phase"))))));
    }

    /**
     * 百鬼夜行自检单行：相位 / 存量 / 容量 / 两条供灵 / 演出锚点与已演时长 / 阶 / 配方 effect。
     *
     * <p>机读单行（外层已有 {@code [GS-AUTO]} 前缀），供外部 harness 解析。
     */
    private static int probeSummon(net.minecraft.commands.CommandSourceStack source,
                                   net.minecraft.core.BlockPos pos) {
        if (!(source.getLevel() instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
            return 0;
        }
        if (!(serverLevel.getBlockEntity(pos)
                instanceof com.bitsson.gensokyou.block.entity.RitualCoreBlockEntity core)
                || core.activeMatch() == null
                || !core.activeMatch().patternId()
                        .equals(com.bitsson.gensokyou.ritual.RitualBehaviors.HYAKKI_YAGYO)) {
            emitGs(source, "[GS-AUTO] SUMMON NO-MATCH");
            return 1;
        }
        emitGs(source, "[GS-AUTO] SUMMON "
                + com.bitsson.gensokyou.ritual.behavior.HyakkiYagyoBehavior
                        .debugSummary(serverLevel, pos, core.activeMatch(), core));
        return 1;
    }

    /**
     * 强制跳到指定相位（{@code charging} / {@code burst} / {@code pillar}），供实机验收演出。
     *
     * <p>没有这个入口后两段表现就只能盲写：充能最快也要十秒（要真的把供灵网络搭起来才看得到
     * 球与闪电），而爆散与光柱各只有 1~2 秒的窗口。
     *
     * <p><b>按新语义落锚点</b>：演出锚点是"爆散那一刻"，不是"启动那一刻"。故
     * {@code charging} 把锚点清成 {@code -1}（演出段未开始），{@code burst} / {@code pillar}
     * 把锚点回拨到 {@code now − 想看的相位内偏移}。若沿用旧的"锚点=启动时刻"语义，
     * 零供灵下整段演出会在一秒内播完，正是本命令要帮玩家验的那三段被压扁的表现。
     */
    private static int setSummonPhase(net.minecraft.commands.CommandSourceStack source,
                                      net.minecraft.core.BlockPos pos, String phase) {
        if (!(source.getLevel() instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
            return 0;
        }
        if (!(serverLevel.getBlockEntity(pos)
                instanceof com.bitsson.gensokyou.block.entity.RitualCoreBlockEntity core)
                || core.activeMatch() == null
                || !core.activeMatch().patternId()
                        .equals(com.bitsson.gensokyou.ritual.RitualBehaviors.HYAKKI_YAGYO)) {
            emitGs(source, "[GS-AUTO] SUMMON NO-MATCH");
            return 1;
        }
        if (!core.summonActive()) {
            emitGs(source, "[GS-AUTO] SUMMON IDLE (start a session first)");
            return 1;
        }
        int burst = com.bitsson.gensokyou.config.GensokyouConfig.FX_SUMMON_BURST_TICKS.get();
        int hold = com.bitsson.gensokyou.config.GensokyouConfig.FX_SUMMON_PILLAR_HOLD_TICKS.get();
        String want = phase.toLowerCase(java.util.Locale.ROOT);
        int back; // 从"爆散那一刻"回拨的 tick 数
        if ("charging".equals(want)) {
            back = -1;
        } else if ("burst".equals(want)) {
            back = 1;
        } else if ("pillar".equals(want)) {
            back = burst + 1;
        } else {
            emitGs(source, "[GS-AUTO] SUMMON BAD-PHASE " + phase);
            return 1;
        }
        core.setEnabled(true);
        if (back < 0) {
            core.setSummonPhase(
                    com.bitsson.gensokyou.block.entity.RitualCoreBlockEntity.SummonPhase.CHARGING);
            core.clearSummonFxStart();
        } else {
            core.markSummonBurst((int) (serverLevel.getGameTime() - back));
            if (back > burst) {
                core.setSummonPhase(
                        com.bitsson.gensokyou.block.entity.RitualCoreBlockEntity.SummonPhase.PILLAR);
            }
        }
        emitGs(source, "[GS-AUTO] SUMMON PHASE " + want
                + " phase=" + core.summonPhase()
                + " elapsed=" + core.summonElapsed()
                + " (burst=" + burst + " hold=" + hold + ")");
        return 1;
    }

    private static int replayBarrier(net.minecraft.commands.CommandSourceStack source,
                                     net.minecraft.core.BlockPos pos) {
        if (!(source.getLevel() instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
            return 0;
        }
        if (!(serverLevel.getBlockEntity(pos)
                instanceof com.bitsson.gensokyou.block.entity.RitualCoreBlockEntity core)) {
            emitGs(source, "[GS-AUTO] BARRIER REPLAY NO-CORE");
            return 1;
        }
        int replayed = com.bitsson.gensokyou.ritual.behavior.BarrierBreakBehavior
                .replayShatter(serverLevel, pos, core);
        emitGs(source, "[GS-AUTO] BARRIER REPLAY doors=" + replayed
                + " latched=" + core.isBarrierLatched()
                + " burstTicks=" + com.bitsson.gensokyou.config.GensokyouConfig
                        .SUKIMA_PORTAL_BURST_TICKS.get());
        return replayed > 0 ? 1 : 0;
    }

    /** 赛尔能源探针：单行 [GS-AUTO] SAIR，含缓存/上限/供灵速率与命中态。 */
    private static int probeSair(net.minecraft.commands.CommandSourceStack source,
                                 net.minecraft.core.BlockPos pos) {
        if (!(source.getLevel() instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
            return 0;
        }
        String msg;
        if (serverLevel.getBlockEntity(pos)
                instanceof com.bitsson.gensokyou.block.entity.RitualCoreBlockEntity core
                && core.activeMatch() != null
                && com.bitsson.gensokyou.ritual.RitualBehaviors.SAIR_ENERGY
                        .equals(core.activeMatch().patternId())) {
            msg = "[GS-AUTO] SAIR stored=" + core.getStored()
                    + " capacity=" + core.getCapacity()
                    + " outRate=" + com.bitsson.gensokyou.config.GensokyouConfig
                            .SAIR_ENERGY_OUT_RATE_PER_SECOND.get()
                    + " hit=true";
        } else {
            msg = "[GS-AUTO] SAIR NO-MATCH";
        }
        Gensokyou.LOGGER.info(msg);
        source.sendSystemMessage(Component.literal(msg));
        return 1;
    }

    /** 灵浴探针：机读单行（hit/level/stored/capacity/inRate/chargePerSecond/cachePerSecond）。 */
    private static int probeReiyoku(net.minecraft.commands.CommandSourceStack source,
                                    net.minecraft.core.BlockPos pos) {
        if (!(source.getLevel() instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
            return 0;
        }
        String msg;
        if (serverLevel.getBlockEntity(pos)
                instanceof com.bitsson.gensokyou.block.entity.RitualCoreBlockEntity core
                && core.activeMatch() != null
                && com.bitsson.gensokyou.ritual.RitualBehaviors.REIYOKU
                        .equals(core.activeMatch().patternId())) {
            msg = "[GS-AUTO] " + com.bitsson.gensokyou.ritual.behavior.ReiyokuBehavior
                    .debugSummary(serverLevel, pos, core.activeMatch(), core);
        } else {
            msg = "[GS-AUTO] REIYOKU NO-MATCH";
        }
        Gensokyou.LOGGER.info(msg);
        source.sendSystemMessage(Component.literal(msg));
        return 1;
    }

    /**
     * 忘川灯坛探针：机读单行（供外部 harness 解析）。
     *
     * <p>额外打 {@code selftest=} —— 由行为侧的 {@code selftest} 现场跑一遍世界无关内核断言
     * （满掩码 64 特判、二项采样均值、抽样下标合法性），并把 1/2/3 阶的蜡烛数一并报出。
     */
    private static int probeBousen(net.minecraft.commands.CommandSourceStack source,
                                   net.minecraft.core.BlockPos pos) {
        String msg;
        if (source.getLevel().getBlockEntity(pos)
                instanceof com.bitsson.gensokyou.block.entity.RitualCoreBlockEntity core
                && core.activeMatch() != null
                && com.bitsson.gensokyou.ritual.RitualBehaviors.BOUSEN
                        .equals(core.activeMatch().patternId())) {
            msg = "[GS-AUTO] " + com.bitsson.gensokyou.ritual.behavior.BousenBehavior
                    .debugSummary(source.getLevel(), pos, core.activeMatch(), core);
        } else {
            msg = "[GS-AUTO] BOUSEN NO-MATCH";
        }
        msg = msg + " selftest=" + bousenSelftest(selftestRandom(source.getLevel()))
                + " candleCounts=" + bousenCandleCounts();
        Gensokyou.LOGGER.info(msg);
        source.sendSystemMessage(Component.literal(msg));
        return 1;
    }

    /** 世界无关内核自检（无世界依赖，可与探针同批跑）。 */
    private static int probeBousenSelftest(net.minecraft.commands.CommandSourceStack source) {
        String result = bousenSelftest(selftestRandom(source.getLevel()));
        String msg = "[GS-AUTO] BOUSEN SELFTEST " + result + " candleCounts=" + bousenCandleCounts();
        Gensokyou.LOGGER.info(msg);
        source.sendSystemMessage(Component.literal(msg));
        return result.startsWith("PASS") ? 1 : 0;
    }

    private static String bousenSelftest(net.minecraft.util.RandomSource rng) {
        var failures = com.bitsson.gensokyou.ritual.behavior.BousenBehavior.selftest(rng);
        return failures.isEmpty() ? "PASS"
                : "FAIL(" + String.join("; ", failures) + ")";
    }

    /**
     * 自检专用 RNG：<b>MUST NOT</b> 用 {@code Level#getRandom()}（固定种子、被共享的遗留流）。
     * selftest 第 0 项就是查 RNG 退化，用被查对象去查自己等于没查。
     */
    private static net.minecraft.util.RandomSource selftestRandom(
            net.minecraft.server.level.ServerLevel level) {
        return net.minecraft.util.RandomSource.create(
                com.bitsson.gensokyou.ritual.behavior.BousenBehavior.class.hashCode()
                        ^ level.getGameTime() ^ System.nanoTime());
    }

    /**
     * 各阶蜡烛数（从已加载的 pattern 切片本地枚举，零世界访问）。
     * 用于确认四重展开与 16/32/64 的预期一致——位掩码宽度依赖它。
     */
    private static String bousenCandleCounts() {
        var pattern = com.bitsson.gensokyou.ritual.RitualPatternLoader
                .byId(com.bitsson.gensokyou.ritual.RitualBehaviors.BOUSEN);
        if (pattern.isEmpty()) {
            return "pattern-missing";
        }
        var p = pattern.get();
        StringBuilder sb = new StringBuilder();
        for (var slice : p.levels()) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(slice.level()).append('=')
                    .append(com.bitsson.gensokyou.ritual.BousenLanterns.offsets(p, slice.level()).size());
        }
        return sb.toString();
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
