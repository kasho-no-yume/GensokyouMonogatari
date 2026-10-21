package com.bitsson.gensokyou.testkit.command;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.testkit.probe.BuiltinProbes;
import com.bitsson.gensokyou.testkit.probe.Probe;
import com.bitsson.gensokyou.testkit.probe.ProbeArgs;
import com.bitsson.gensokyou.testkit.probe.ProbeContext;
import com.bitsson.gensokyou.testkit.probe.ProbeRegistry;
import com.bitsson.gensokyou.testkit.scenario.ScenarioManager;
import com.bitsson.gensokyou.testkit.scenario.ScenarioRunner;
import com.bitsson.gensokyou.testkit.scenario.ScenarioSpec;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * 游戏内验收测试台命令面（dev-only；本类不进 mod jar）。
 *
 * <ul>
 *   <li>{@code /gs_probe <id> [args...]} —— 只读探针，输出单行 JSON。</li>
 *   <li>{@code /gs_scenario run|list|status|abort|report <name>} —— 场景执行器。</li>
 * </ul>
 *
 * <p>全部输出为 {@code [GS-*]} 字面量，**不产生任何玩家可见文本**（故无需 lang 条目）。
 */
@EventBusSubscriber(modid = Gensokyou.MODID)
public final class TestKitCommands {

    private TestKitCommands() {
    }

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent event) {
        BuiltinProbes.registerAll();
        event.getDispatcher().register(probeCommand());
        event.getDispatcher().register(scenarioCommand());
    }

    // --------------------------------------------------------------- /gs_probe

    private static LiteralArgumentBuilder<CommandSourceStack> probeCommand() {
        return Commands.literal("gs_probe")
                .requires(s -> s.hasPermission(2))
                .then(Commands.argument("id", StringArgumentType.word())
                        .executes(ctx -> runProbe(ctx.getSource(),
                                StringArgumentType.getString(ctx, "id"), Map.of()))
                        .then(Commands.argument("args", StringArgumentType.greedyString())
                                .executes(ctx -> runProbe(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "id"),
                                        ProbeArgs.parse(StringArgumentType.getString(ctx, "args"))))));
    }

    private static int runProbe(CommandSourceStack source, String id, Map<String, String> args) {
        Optional<Probe> probe = ProbeRegistry.find(id);
        if (probe.isEmpty()) {
            source.sendFailure(Component.literal("[GS-PROBE] unknown probe: " + id
                    + " (known: " + String.join(", ", ProbeRegistry.ids()) + ")"));
            return 0;
        }
        ServerLevel level = source.getLevel();
        if (level == null) {
            source.sendFailure(Component.literal("[GS-PROBE] no level bound to this source"));
            return 0;
        }
        try {
            JsonObject result = probe.get().run(new ProbeContext(source, level, args));
            String json = result.toString();
            source.sendSuccess(() -> Component.literal(json), false);
            Gensokyou.LOGGER.info("[GS-PROBE] {} {}", id, json);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("[GS-PROBE] " + id + " failed: " + e));
            Gensokyou.LOGGER.error("[GS-PROBE] {} failed", id, e);
            return 0;
        }
    }

    // ------------------------------------------------------------ /gs_scenario

    private static LiteralArgumentBuilder<CommandSourceStack> scenarioCommand() {
        return Commands.literal("gs_scenario")
                .requires(s -> s.hasPermission(2))
                .then(Commands.literal("list")
                        .executes(ctx -> listScenarios(ctx.getSource())))
                .then(Commands.literal("status")
                        .executes(ctx -> status(ctx.getSource())))
                .then(Commands.literal("run")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(ctx -> runScenario(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "name")))))
                .then(Commands.literal("abort")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(ctx -> abortScenario(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "name")))))
                .then(Commands.literal("report")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(ctx -> report(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "name")))));
    }

    private static int listScenarios(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        if (level == null) {
            source.sendFailure(Component.literal("[GS-SCENARIO] no level bound to this source"));
            return 0;
        }
        Path dir = scenarioDir(level);
        if (!Files.isDirectory(dir)) {
            source.sendSuccess(() -> Component.literal("[GS-SCENARIO] no scenario dir at " + dir), false);
            return 1;
        }
        List<String> names;
        try (Stream<Path> files = Files.list(dir)) {
            names = files.filter(p -> p.getFileName().toString().endsWith(".json"))
                    .map(p -> p.getFileName().toString())
                    .sorted()
                    .toList();
        } catch (Exception e) {
            source.sendFailure(Component.literal("[GS-SCENARIO] cannot list " + dir + ": " + e));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("[GS-SCENARIO] " + names.size()
                + " scenario(s): " + String.join(", ", names)), false);
        return 1;
    }

    private static int status(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("[GS-SCENARIO] active: "
                + String.join(", ", ScenarioManager.active())), false);
        return 1;
    }

    private static int runScenario(CommandSourceStack source, String name) {
        ServerLevel level = source.getLevel();
        if (level == null) {
            source.sendFailure(Component.literal("[GS-SCENARIO] no level bound to this source"));
            return 0;
        }
        Path file = scenarioDir(level).resolve(name + ".json");
        if (!Files.isRegularFile(file)) {
            source.sendFailure(Component.literal("[GS-SCENARIO] no such scenario file: " + file));
            return 0;
        }
        JsonObject root;
        try {
            root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            source.sendFailure(Component.literal("[GS-SCENARIO] cannot parse " + file + ": " + e));
            return 0;
        }
        ScenarioSpec spec = ScenarioSpec.parse(root, name);
        ScenarioRunner runner = new ScenarioRunner(spec, source, level);
        if (!ScenarioManager.start(runner)) {
            source.sendFailure(Component.literal("[GS-SCENARIO] " + name + " is already running"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("[GS-SCENARIO] started " + name
                + " (timeout " + spec.timeoutTicks() + " ticks, " + spec.steps().size() + " steps)"), false);
        Gensokyou.LOGGER.info("[GS-SCENARIO] started {} ({} steps)", name, spec.steps().size());
        return 1;
    }

    private static int abortScenario(CommandSourceStack source, String name) {
        if (ScenarioManager.abort(name)) {
            source.sendSuccess(() -> Component.literal("[GS-SCENARIO] aborted " + name), false);
            return 1;
        }
        source.sendFailure(Component.literal("[GS-SCENARIO] no running scenario named " + name));
        return 0;
    }

    private static int report(CommandSourceStack source, String name) {
        ServerLevel level = source.getLevel();
        if (level == null) {
            source.sendFailure(Component.literal("[GS-SCENARIO] no level bound to this source"));
            return 0;
        }
        Path file = reportDir(level).resolve(name + ".json");
        if (!Files.isRegularFile(file)) {
            source.sendFailure(Component.literal("[GS-SCENARIO] no report at " + file));
            return 0;
        }
        try {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            source.sendSuccess(() -> Component.literal(text), false);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("[GS-SCENARIO] cannot read " + file + ": " + e));
            return 0;
        }
    }

    // ------------------------------------------------------------------ paths

    /** 场景定义目录：游戏目录下的 {@code gs_scenarios/}。 */
    public static Path scenarioDir(ServerLevel level) {
        return level.getServer().getServerDirectory().resolve("gs_scenarios");
    }

    /** 结论报告目录：游戏目录下的 {@code gs_report/}。 */
    public static Path reportDir(ServerLevel level) {
        return level.getServer().getServerDirectory().resolve("gs_report");
    }
}
