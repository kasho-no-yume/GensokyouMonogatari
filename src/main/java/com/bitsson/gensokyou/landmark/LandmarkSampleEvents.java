package com.bitsson.gensokyou.landmark;

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

@EventBusSubscriber(modid = "gensokyou")
public final class LandmarkSampleEvents {
    private LandmarkSampleEvents() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("gs_landmark_sample")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("install").executes(context -> install(context.getSource())))
                .then(Commands.literal("visit").executes(context -> visit(context.getSource()))));
    }

    private static int install(CommandSourceStack source) {
        ServerLevel level = source.getServer().getLevel(LandmarkSample.DIMENSION);
        if (level == null) {
            source.sendFailure(Component.translatable("commands.gensokyou.landmark_sample.missing_dimension"));
            return 0;
        }
        if (!placeSample(level)) {
            source.sendFailure(Component.translatable("commands.gensokyou.landmark_sample.invalid_template"));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("commands.gensokyou.landmark_sample.installed"), false);
        return 1;
    }

    static boolean placeSample(ServerLevel level) {
        StructureTemplate template = level.getStructureManager().get(LandmarkSample.ID).orElse(null);
        BlockPos origin = LandmarkSample.ORIGIN;
        return template != null && template.getSize().equals(LandmarkSample.END.subtract(origin).offset(1, 1, 1))
                && template.placeInWorld(level, origin, origin, new StructurePlaceSettings(), level.getRandom(), Block.UPDATE_ALL);
    }

    private static int visit(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = source.getServer().getLevel(LandmarkSample.DIMENSION);
        if (level == null) {
            source.sendFailure(Component.translatable("commands.gensokyou.landmark_sample.missing_dimension"));
            return 0;
        }
        BlockPos origin = LandmarkSample.ORIGIN;
        player.teleportTo(level, origin.getX() + 5.5, origin.getY(), origin.getZ() - 3.5, 0, 0);
        return 1;
    }

    @SubscribeEvent
    public static void preventPlacement(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel() instanceof ServerLevel) {
            if (event instanceof BlockEvent.EntityMultiPlaceEvent multi) {
                if (multi.getReplacedBlockSnapshots().stream().anyMatch(snapshot -> LandmarkSample.contains(event.getLevel(), snapshot.getPos()))) {
                    event.setCanceled(true);
                }
            } else if (LandmarkSample.contains(event.getLevel(), event.getPos())) {
                event.setCanceled(true);
            }
        }
    }
}
