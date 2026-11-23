package com.bitsson.gensokyou.command;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.item.GensokyoDebugMapItem;
import com.bitsson.gensokyou.worldgen.GensokyoLayout;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.Set;

public final class DimensionDebugTeleport {
    private DimensionDebugTeleport() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> command() {
        var gensokyo = Commands.literal("gensokyo").executes(context ->
                visitBiome(context.getSource(), GensokyoLayout.Region.HAKUREI));
        for (var region : GensokyoLayout.Region.values()) {
            gensokyo.then(Commands.literal(region.biomePath()).executes(context -> visitBiome(context.getSource(), region)));
        }
        gensokyo.then(Commands.literal("misty_lake").executes(context -> visitGensokyo(context.getSource(),
                "misty_lake", GensokyoLayout.MISTY_LAKE_X, GensokyoLayout.MISTY_LAKE_Z, true)));
        gensokyo.then(Commands.literal("sanzu_river").executes(context -> visitGensokyo(context.getSource(),
                "sanzu_river", GensokyoLayout.ISLAND_RADIUS + 256, 0, true)));
        return Commands.literal("tp").then(gensokyo).then(Commands.literal("overworld").executes(context -> {
            var source = context.getSource();
            ServerLevel overworld = source.getServer().overworld();
            BlockPos spawn = overworld.getSharedSpawnPos();
            return teleport(source, overworld, spawn.getX(), spawn.getZ(), null, false,
                    Component.translatable("msg.gensokyou.debug_tp_overworld"));
        }));
    }

    private static int visitBiome(CommandSourceStack source, GensokyoLayout.Region region) throws CommandSyntaxException {
        return visitGensokyo(source, region.biomePath(), region.landmarkX(), region.landmarkZ(), false);
    }

    private static int visitGensokyo(CommandSourceStack source, String biome, int x, int z, boolean waterAllowed)
            throws CommandSyntaxException {
        ServerLevel target = source.getServer().getLevel(GensokyoDebugMapItem.DIMENSION);
        if (target == null) {
            source.sendFailure(Component.translatable("msg.gensokyou.debug_tp_missing_dimension"));
            return 0;
        }
        return teleport(source, target, x, z, biome, waterAllowed, Component.translatable("biome.gensokyou." + biome));
    }

    private static int teleport(CommandSourceStack source, ServerLevel level, int x, int z,
                                @Nullable String biome, boolean waterAllowed, Component destination) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        BlockPos landing = findLanding(level, x, z, biome, waterAllowed);
        if (landing == null || !player.teleportTo(level, landing.getX() + 0.5, landing.getY(),
                landing.getZ() + 0.5, Set.of(), player.getYRot(), player.getXRot())) {
            source.sendFailure(Component.translatable("msg.gensokyou.debug_tp_failed", destination));
            return 0;
        }
        player.resetFallDistance();
        source.sendSuccess(() -> Component.translatable("msg.gensokyou.debug_tp_success", destination,
                landing.getX(), landing.getY(), landing.getZ()), false);
        return 1;
    }

    @Nullable
    public static BlockPos findLanding(ServerLevel level, int x, int z, @Nullable String biome, boolean waterAllowed) {
        var biomeId = biome == null ? null : Gensokyou.id(biome);
        for (int radius = 0; radius <= 24; radius += 4) {
            for (int dx = -radius; dx <= radius; dx += 4) {
                for (int dz = -radius; dz <= radius; dz += 4) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                        continue;
                    }
                    int candidateX = x + dx;
                    int candidateZ = z + dz;
                    // Level#getHeight returns the minimum build height for unloaded chunks.
                    var chunk = level.getChunk(candidateX >> 4, candidateZ >> 4);
                    int top = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, candidateX & 15, candidateZ & 15);
                    BlockPos feet = new BlockPos(candidateX, top + 1, candidateZ);
                    if (!level.isInWorldBounds(feet.above()) || !level.getWorldBorder().isWithinBounds(feet)
                            || (biomeId != null && !level.getBiome(feet).is(biomeId))) {
                        continue;
                    }
                    BlockPos floor = feet.below();
                    var fluid = level.getFluidState(floor);
                    boolean supported = fluid.isEmpty() && level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP);
                    if ((supported || waterAllowed && fluid.is(FluidTags.WATER))
                            && level.getFluidState(feet).isEmpty() && level.getFluidState(feet.above()).isEmpty()
                            && level.noCollision(new AABB(feet.getX() + 0.2, feet.getY(), feet.getZ() + 0.2,
                            feet.getX() + 0.8, feet.getY() + 1.8, feet.getZ() + 0.8))) {
                        return feet;
                    }
                }
            }
        }
        return null;
    }
}
