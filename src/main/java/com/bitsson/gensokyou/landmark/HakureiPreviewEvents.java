package com.bitsson.gensokyou.landmark;

import com.bitsson.gensokyou.worldgen.GensokyoLayout;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;

@EventBusSubscriber(modid = "gensokyou")
public final class HakureiPreviewEvents {
    private HakureiPreviewEvents() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("gs_hakurei_preview")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("install").executes(ctx -> install(ctx.getSource())))
                .then(Commands.literal("visit")
                        .executes(ctx -> teleportPreview(ctx.getSource(), -4.5, 1.0, 35.5, -90.0F, -10.0F))
                        .then(Commands.literal("courtyard")
                                .executes(ctx -> teleportPreview(ctx.getSource(), 50.5, 13.0, 35.5, -90.0F, -5.0F)))
                        .then(Commands.literal("engawa")
                                .executes(ctx -> teleportPreview(ctx.getSource(), 60.5, 15.0, 55.5, 90.0F, 5.0F))))
                .then(Commands.literal("visit_gensokyo")
                        .executes(ctx -> visitGensokyo(ctx.getSource(), -12.0, 1.0, 35.5, -90.0F, -8.0F))
                        .then(Commands.literal("courtyard")
                                .executes(ctx -> visitGensokyo(ctx.getSource(), 50.5, 13.0, 35.5, 90.0F, 0.0F)))
                        .then(Commands.literal("front")
                                .executes(ctx -> visitGensokyo(ctx.getSource(), -26.0, 2.0, 35.5, 90.0F, 0.0F)))
                        .then(Commands.literal("north")
                                .executes(ctx -> visitGensokyo(ctx.getSource(), 34.0, 15.0, -16.5, 180.0F, 0.0F)))
                        .then(Commands.literal("east")
                                .executes(ctx -> visitGensokyo(ctx.getSource(), 108.0, 19.0, 35.5, -90.0F, 0.0F)))));
    }

    private static int install(CommandSourceStack source) {
        ServerLevel level = source.getServer().getLevel(LandmarkSample.DIMENSION);
        if (level == null) {
            source.sendFailure(Component.literal("Missing preview dimension gensokyou:landmark_sample_v1"));
            return 0;
        }
        StructureTemplate template = level.getStructureManager().get(HakureiShrinePreview.ID).orElse(null);
        BlockPos origin = HakureiShrinePreview.ORIGIN;
        if (template == null || !template.getSize().equals(HakureiShrinePreview.SIZE)) {
            source.sendFailure(Component.literal("Failed to load hakurei_shrine_preview structure template"));
            return 0;
        }
        boolean placed = template.placeInWorld(level, origin, origin, new StructurePlaceSettings(), level.getRandom(), Block.UPDATE_ALL);
        if (!placed) {
            source.sendFailure(Component.literal("Failed to place hakurei_shrine_preview"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Installed Hakurei Shrine preview at " + origin.toShortString()), false);
        return 1;
    }

    private static int teleportPreview(CommandSourceStack source, double dx, double dy, double dz, float yaw, float pitch)
            throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = source.getServer().getLevel(LandmarkSample.DIMENSION);
        if (level == null) {
            source.sendFailure(Component.literal("Missing preview dimension gensokyou:landmark_sample_v1"));
            return 0;
        }
        BlockPos origin = HakureiShrinePreview.ORIGIN;
        player.teleportTo(level, origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz, yaw, pitch);
        return 1;
    }

    private static int visitGensokyo(CommandSourceStack source, double dx, double dy, double dz, float yaw, float pitch)
            throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel gensokyo = source.getServer().getLevel(HakureiShrineLandmark.GENSOKYO_DIMENSION);
        if (gensokyo == null) {
            source.sendFailure(Component.literal("Missing dimension gensokyou:gensokyo"));
            return 0;
        }
        HakureiShrineLandmark.ensurePlaced(gensokyo);
        HakureiOuterGrounds.ensurePlaced(gensokyo);
        BlockPos origin = HakureiShrineLandmark.ORIGIN;
        player.teleportTo(gensokyo, origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz, yaw, pitch);
        return 1;
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (!level.dimension().equals(HakureiShrineLandmark.GENSOKYO_DIMENSION)) {
            return;
        }
        var chunkPos = event.getChunk().getPos();
        if (HakureiShrineLandmark.intersectsChunk(chunkPos.x, chunkPos.z)
                || HakureiOuterGrounds.intersectsChunk(chunkPos.x, chunkPos.z)) {
            level.getServer().execute(() -> {
                HakureiShrineLandmark.ensurePlaced(level);
                HakureiOuterGrounds.ensurePlaced(level);
            });
        }
    }

    @SubscribeEvent
    public static void preventPlacement(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel() instanceof ServerLevel) {
            if (event instanceof BlockEvent.EntityMultiPlaceEvent multi) {
                if (multi.getReplacedBlockSnapshots().stream().anyMatch(
                        s -> isProtected(event.getLevel(), s.getPos()))) {
                    event.setCanceled(true);
                }
            } else if (isProtected(event.getLevel(), event.getPos())) {
                event.setCanceled(true);
            }
        }
    }

    private static boolean isProtected(net.minecraft.world.level.LevelAccessor level, BlockPos pos) {
        return HakureiShrinePreview.contains(level, pos)
                || HakureiShrineLandmark.contains(level, pos)
                || HakureiOuterGrounds.contains(level, pos);
    }
}
