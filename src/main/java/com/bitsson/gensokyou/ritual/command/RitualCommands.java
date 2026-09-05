package com.bitsson.gensokyou.ritual.command;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.ritual.Orientation;
import com.bitsson.gensokyou.ritual.RitualCapture;
import com.bitsson.gensokyou.ritual.RitualPattern;
import com.bitsson.gensokyou.ritual.RitualPatternLoader;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.ArrayList;
import java.util.List;

@EventBusSubscriber(modid = Gensokyou.MODID)
public final class RitualCommands {

    private RitualCommands() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("gs_ritual_capture")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("name", StringArgumentType.word())
                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, 16))
                                .then(Commands.argument("height", IntegerArgumentType.integer(1, 16))
                                        .executes(context -> capture(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "name"),
                                                IntegerArgumentType.getInteger(context, "radius"),
                                                IntegerArgumentType.getInteger(context, "height")))))));
        event.getDispatcher().register(Commands.literal("gs_ritual_selftest")
                .requires(source -> source.hasPermission(2))
                .executes(context -> {
                    List<String> failures = OrientationSelfTest.run();
                    if (failures.isEmpty()) {
                        context.getSource().sendSuccess(
                                () -> Component.literal("orientation selftest: all passed"), false);
                        return 1;
                    }
                    for (String failure : failures) {
                        Gensokyou.LOGGER.warn("orientation selftest: {}", failure);
                        context.getSource().sendFailure(Component.literal(failure));
                    }
                    return 0;
                }));
    }

    /** 朝向常量表与展开变换的表驱动自检（schema v4 回归锚点，无世界依赖）。 */
    public static final class OrientationSelfTest {

        private final List<String> failures = new ArrayList<>();

        public static List<String> run() {
            return new OrientationSelfTest().execute();
        }

        private List<String> execute() {
            tables();
            expansion();
            blockStates();
            return failures;
        }

        private void check(boolean ok, String what) {
            if (!ok) {
                failures.add(what);
            }
        }

        /** 两张本原置换表 + 复合：全 26 常量封闭性与已知值。 */
        private void tables() {
            for (int id = Orientation.NORTH; id <= Orientation.MAX_ID; id++) {
                check(Orientation.valid(Orientation.rot90(id)), "rot90 escapes table: " + id);
                check(Orientation.valid(Orientation.mirrorX(id)), "mirrorX escapes table: " + id);
                check(Orientation.rot90(Orientation.rot90(Orientation.rot90(
                        Orientation.rot90(id)))) == id, "rot90^4 != id: " + id);
                check(Orientation.mirrorX(Orientation.mirrorX(id)) == id, "mirrorX^2 != id: " + id);
                check(Orientation.mirrorZ(id) == Orientation.rot180(Orientation.mirrorX(id)),
                        "mirrorZ composite mismatch: " + id);
            }
            check(Orientation.rot90(Orientation.NORTH) == Orientation.EAST, "rot90 N!=E");
            check(Orientation.rot90(Orientation.WEST_TOP) == Orientation.NORTH_TOP, "rot90 WT!=NT");
            check(Orientation.rot90(Orientation.UP) == Orientation.UP, "rot90 UP!=UP");
            check(Orientation.mirrorX(Orientation.EAST) == Orientation.WEST, "mirrorX E!=W");
            check(Orientation.mirrorX(Orientation.NORTH) == Orientation.NORTH, "mirrorX N!=N");
            // R 系（罗盘：北0 东4 南8 西12，恒等映射）
            check(Orientation.rot90(Orientation.R0) == Orientation.R0 + 4, "rot90 R0!=R4");
            check(Orientation.mirrorX(Orientation.R0 + 4) == Orientation.R0 + 12, "mirrorX R4!=R12");
            check(Orientation.mirrorX(Orientation.R0) == Orientation.R0, "mirrorX R0!=R0");
            check(Orientation.mirrorX(Orientation.R0 + 2) == Orientation.R0 + 14, "mirrorX R2!=R14");
        }

        /** 四分之一展开的朝向复合：朝中心楼梯环性质 + 对角镜像公式。 */
        private void expansion() {
            check(Orientation.diagSwap(Orientation.NORTH) == Orientation.WEST, "diagSwap N!=W");
            check(Orientation.diagAnti(Orientation.NORTH) == Orientation.EAST, "diagAnti N!=E");
            List<RitualPattern.BlockEntry> ring = new ArrayList<>();
            RitualPatternLoader.expandInto('T', 0, 0, 2, Orientation.NORTH, ring);
            check(ring.size() == 4, "axis expansion size != 4: " + ring.size());
            for (RitualPattern.BlockEntry entry : ring) {
                int expected = switch (entry.x() * 10 + entry.z()) {
                    case 2 -> Orientation.NORTH;   // (0,2) 南位朝北
                    case -2 -> Orientation.SOUTH;  // (0,-2) 北位朝南
                    case 20 -> Orientation.WEST;   // (2,0) 东位朝西
                    default -> Orientation.EAST;   // (-2,0) 西位朝东
                };
                check(expected == entry.orientation(), "ring orientation mismatch at ("
                        + entry.x() + "," + entry.z() + "): " + entry.orientation() + " != " + expected);
            }
        }

        /** 真实方块状态的 apply/matches/detect 往返（注册表已就绪时执行）。 */
        private void blockStates() {
            var stairs = Blocks.STONE_STAIRS.defaultBlockState();
            check(Orientation.supports(stairs, Orientation.NORTH_TOP), "stairs support N_TOP");
            check(!Orientation.supports(stairs, Orientation.UP), "stairs must not support UP");
            var top = Orientation.apply(stairs, Orientation.NORTH_TOP);
            check(Orientation.detect(top) == Orientation.NORTH_TOP, "stairs detect roundtrip");
            check(Orientation.matches(top, Orientation.NORTH_TOP), "stairs matches N_TOP");
            check(!Orientation.matches(top, Orientation.NORTH), "stairs HALF strictness");
            var bottom = Orientation.apply(stairs, Orientation.EAST);
            check(Orientation.matches(bottom, Orientation.EAST)
                    && Orientation.detect(bottom) == Orientation.EAST, "stairs bottom roundtrip");
            var log = Orientation.apply(Blocks.OAK_LOG.defaultBlockState(), Orientation.SOUTH);
            check(Orientation.matches(log, Orientation.NORTH), "log axis bidirectional");
            check(Orientation.detect(log) == Orientation.NORTH, "log detect canonical Z->NORTH");
            var banner = Orientation.apply(Blocks.WHITE_BANNER.defaultBlockState(), Orientation.R0 + 4);
            check((int) banner.getValue(
                    net.minecraft.world.level.block.state.properties.BlockStateProperties.ROTATION_16)
                    == 4, "banner R4 identity segment");
            check(Orientation.detect(banner) == Orientation.R0 + 4, "banner detect roundtrip");
            check(!Orientation.supports(Blocks.COBBLESTONE_WALL.defaultBlockState(), Orientation.NORTH),
                    "wall must not support any constant");
            // 匹配器公式一致性：verifyTier 用 matches(state, rotateBy(id, r))，
            // 则对任意 r，把期望常量旋转后 apply 出的状态必须恰好被 matches 接受。
            for (int id = Orientation.NORTH; id <= Orientation.MAX_ID; id++) {
                for (int r = 0; r < 4; r++) {
                    int rotated = Orientation.rotateBy(id, r);
                    var base = Blocks.STONE_STAIRS.defaultBlockState();
                    var bannerBase = Blocks.WHITE_BANNER.defaultBlockState();
                    var logBase = Blocks.OAK_LOG.defaultBlockState();
                    check(Orientation.matches(Orientation.apply(
                                    id >= Orientation.R0 ? bannerBase : (id >= Orientation.UP ? logBase : base),
                                    rotated),
                            rotated), "rotateBy matches self-consistency id=" + id + " r=" + r);
                }
            }
        }
    }

    /** 以玩家脚下为锚点中心，捕获半径 × 高度的区域为仪式结构骨架（纵向自脚下 1 格起，站核心上也能扫到核心层）。 */
    private static int capture(CommandSourceStack source, String name, int radius, int height) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception exception) {
            source.sendFailure(Component.literal("players only"));
            return 0;
        }
        BlockPos center = player.blockPosition();
        BlockPos min = new BlockPos(center.getX() - radius, center.getY() - 1, center.getZ() - radius);
        BlockPos max = new BlockPos(center.getX() + radius, center.getY() + height - 1,
                center.getZ() + radius);
        RitualCapture.Result result = RitualCapture.capture(source.getLevel(), name, min, max);
        Gensokyou.LOGGER.info("[ritual capture: {}]\n{}", name, result.json());
        java.nio.file.Path file = null;
        try {
            java.nio.file.Path dir = net.neoforged.fml.loading.FMLPaths.GAMEDIR.get()
                    .resolve("ritual_captures");
            java.nio.file.Files.createDirectories(dir);
            file = java.nio.file.Files.writeString(dir.resolve(name + ".json"), result.json());
        } catch (Exception exception) {
            Gensokyou.LOGGER.warn("Failed to save ritual capture {}: {}", name, exception.getMessage());
        }
        String saved = file != null ? ", saved to " + file : "";
        source.sendSuccess(() -> Component.literal(
                "Ritual skeleton '" + name + "' written to logs/latest.log (" + result.blocksCaptured()
                        + " blocks" + saved + ")"), false);
        for (String violation : result.violations()) {
            source.sendFailure(Component.literal(violation));
        }
        return result.violations().isEmpty() ? 1 : 0;
    }
}
