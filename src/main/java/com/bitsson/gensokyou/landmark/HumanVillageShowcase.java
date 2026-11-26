package com.bitsson.gensokyou.landmark;

import com.bitsson.gensokyou.Gensokyou;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gallery showcase in gensokyou:landmark_sample_v1 so every Human Village landmark,
 * decor piece, and parametric machiya variant can be inspected in-game at full color.
 *
 * Commands:
 *   /gs_village showcase             - Install all exhibits & teleport to gallery entrance
 *   /gs_village showcase <exhibit>   - Teleport directly in front of a named exhibit
 */
@EventBusSubscriber(modid = "gensokyou")
public final class HumanVillageShowcase {
    private static final int BASE_Y = 63; // Flush with landmark_sample_v1 ground (top solid block y=63, feet y=64)
    private static final Map<ResourceLocation, LocalLandmarkColors> LOCAL_COLOR_CACHE = new ConcurrentHashMap<>();

    public record Exhibit(String shortName, ResourceLocation id, int minX, int minY, int minZ,
                          int sizeX, int sizeY, int sizeZ, int themeId,
                          double viewDx, double viewDy, double viewDz, float yaw) {
        public boolean contains(int x, int y, int z) {
            return x >= minX && x < minX + sizeX
                    && y >= minY && y < minY + sizeY
                    && z >= minZ && z < minZ + sizeZ;
        }
    }

    // Row 1 (Z = 270..334): 4 Grand Signature NPC Landmarks + Dragon Plaza (spacious spacing, flush y=63!)
    // Row 2 (Z = 360): 10 Parametric Machiya Variants (v01..v10 with varied runtime palettes)
    // Row 3 (Z = 395): Non-functional Decor Landmarks
    public static final List<Exhibit> EXHIBITS = List.of(
            new Exhibit("kourindou", Gensokyou.id("hv_kourindou"), 200, BASE_Y, 270, 44, 25, 36, -1, 19.5, 2, 41, 180f),
            new Exhibit("geidontei", Gensokyou.id("hv_geidontei"), 258, BASE_Y, 270, 42, 29, 36, -1, -7, 2, 18, -90f),
            new Exhibit("suzunaan", Gensokyou.id("hv_suzunaan"), 314, BASE_Y, 270, 38, 26, 34, -1, 44, 2, 17, 90f),
            new Exhibit("hieda", Gensokyou.id("hv_hieda_residence"), 368, BASE_Y, 260, 76, 28, 64, -1, 38, 3, 70, 180f),
            new Exhibit("dragon_plaza", Gensokyou.id("hv_dragon_plaza"), 458, BASE_Y, 276, 29, 16, 29, -1, 14.5, 3, 35, 180f),

            new Exhibit("v01", Gensokyou.id("hv_machiya_v01"), 200, BASE_Y, 360, 9, 12, 10, 0, 4.5, 2, 14, 180f),
            new Exhibit("v02", Gensokyou.id("hv_machiya_v02"), 214, BASE_Y, 360, 11, 12, 10, 2, 5.5, 2, 14, 180f),
            new Exhibit("v03", Gensokyou.id("hv_machiya_v03"), 230, BASE_Y, 360, 9, 14, 11, 1, 4.5, 2, 15, 180f),
            new Exhibit("v04", Gensokyou.id("hv_machiya_v04"), 244, BASE_Y, 360, 11, 14, 11, 5, 5.5, 2, 15, 180f),
            new Exhibit("v05", Gensokyou.id("hv_machiya_v05"), 260, BASE_Y, 360, 11, 16, 12, 4, 5.5, 2, 16, 180f),
            new Exhibit("v06", Gensokyou.id("hv_machiya_v06"), 276, BASE_Y, 360, 13, 16, 12, 6, 6.5, 2, 16, 180f),
            new Exhibit("v07", Gensokyou.id("hv_machiya_v07"), 294, BASE_Y, 360, 9, 15, 11, 6, 4.5, 2, 15, 180f),
            new Exhibit("v08", Gensokyou.id("hv_machiya_v08"), 308, BASE_Y, 360, 11, 13, 12, 3, 5.5, 2, 16, 180f),
            new Exhibit("v09", Gensokyou.id("hv_machiya_v09"), 324, BASE_Y, 360, 13, 15, 13, 0, 6.5, 2, 17, 180f),
            new Exhibit("v10", Gensokyou.id("hv_machiya_v10"), 342, BASE_Y, 360, 11, 13, 13, 7, 5.5, 2, 17, 180f),

            new Exhibit("gate_east", Gensokyou.id("hv_gate_east"), 200, BASE_Y, 395, 14, 12, 19, -1, 7, 2, 23, 180f),
            new Exhibit("watermill", Gensokyou.id("hv_watermill"), 222, BASE_Y, 397, 12, 13, 12, -1, 6, 2, 17, 180f),
            new Exhibit("stall_dango", Gensokyou.id("hv_stall_dango"), 240, BASE_Y, 401, 5, 5, 4, -1, 2.5, 2, 8, 180f),
            new Exhibit("stall_fish", Gensokyou.id("hv_stall_fish"), 248, BASE_Y, 401, 5, 5, 4, -1, 2.5, 2, 8, 180f),
            new Exhibit("stall_umbrella", Gensokyou.id("hv_stall_umbrella"), 256, BASE_Y, 401, 5, 5, 4, -1, 2.5, 2, 8, 180f),
            new Exhibit("stall_books", Gensokyou.id("hv_stall_books"), 264, BASE_Y, 401, 5, 5, 4, -1, 2.5, 2, 8, 180f),
            new Exhibit("well", Gensokyou.id("hv_well"), 274, BASE_Y, 401, 5, 6, 5, -1, 2.5, 2, 9, 180f),
            new Exhibit("jizo", Gensokyou.id("hv_jizo"), 282, BASE_Y, 402, 3, 5, 3, -1, 1.5, 2, 7, 180f),
            new Exhibit("rest_tree", Gensokyou.id("hv_rest_tree"), 290, BASE_Y, 399, 7, 9, 7, -1, 3.5, 2, 11, 180f)
    );

    private HumanVillageShowcase() {}

    public static LocalLandmarkColors loadLocalColors(ResourceLocation templateId) {
        return LOCAL_COLOR_CACHE.computeIfAbsent(templateId, id -> {
            String path = "/assets/" + id.getNamespace() + "/landmark/" + id.getPath() + ".local.bin";
            try (var stream = HumanVillageShowcase.class.getResourceAsStream(path)) {
                if (stream == null) {
                    return null;
                }
                return LocalLandmarkColors.read(stream);
            } catch (IOException e) {
                LogUtils.getLogger().error("Failed to load GLC3 local colors {}", path, e);
                return null;
            }
        });
    }

    /** Client color lookup for the gallery in gensokyou:landmark_sample_v1. */
    public static int colorAt(int x, int y, int z, ResourceLocation actualBlock) {
        if (x < 195 || x > 500 || z < 255 || z > 425) {
            return LandmarkColors.NO_TINT;
        }
        for (Exhibit ex : EXHIBITS) {
            if (!ex.contains(x, y, z)) {
                continue;
            }
            LocalLandmarkColors colors = loadLocalColors(ex.id());
            if (colors != null) {
                int c = colors.colorAtWorld(x, y, z, ex.minX(), ex.minY(), ex.minZ(), Rotation.NONE, ex.themeId(), actualBlock);
                if (c != LandmarkColors.NO_TINT) {
                    return c;
                }
            }
        }
        return LandmarkColors.NO_TINT;
    }

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("gs_village")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("showcase")
                        .executes(ctx -> runShowcase(ctx.getSource(), "kourindou"))
                        .then(Commands.argument("exhibit", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    for (Exhibit ex : EXHIBITS) {
                                        builder.suggest(ex.shortName());
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> runShowcase(ctx.getSource(), StringArgumentType.getString(ctx, "exhibit"))))));
    }

    private static int runShowcase(CommandSourceStack source, String targetName) throws CommandSyntaxException {
        ServerLevel level = source.getServer().getLevel(LandmarkSample.DIMENSION);
        if (level == null) {
            source.sendFailure(Component.literal("Missing preview dimension gensokyou:landmark_sample_v1"));
            return 0;
        }
        int placedCount = 0;
        Exhibit target = null;
        for (Exhibit ex : EXHIBITS) {
            if (ex.shortName().equalsIgnoreCase(targetName)) {
                target = ex;
            }
            StructureTemplate template = level.getStructureManager().get(ex.id()).orElse(null);
            if (template == null) {
                source.sendFailure(Component.literal("Missing structure template: " + ex.id()));
                continue;
            }
            BlockPos origin = new BlockPos(ex.minX(), ex.minY(), ex.minZ());
            if (template.placeInWorld(level, origin, origin, new StructurePlaceSettings(), level.getRandom(), Block.UPDATE_ALL)) {
                placedCount++;
            }
        }
        if (target == null) {
            target = EXHIBITS.getFirst();
        }
        ServerPlayer player = source.getPlayerOrException();
        player.teleportTo(level,
                target.minX() + target.viewDx(),
                target.minY() + target.viewDy(),
                target.minZ() + target.viewDz(),
                target.yaw(), 5.0F);
        final int finalPlaced = placedCount;
        final String name = target.shortName();
        source.sendSuccess(() -> Component.literal(
                "Installed " + finalPlaced + " Human Village exhibits in landmark_sample_v1. Viewing: " + name), false);
        return 1;
    }
}
