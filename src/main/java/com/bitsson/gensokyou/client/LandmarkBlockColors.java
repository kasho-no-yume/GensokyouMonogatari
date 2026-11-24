package com.bitsson.gensokyou.client;

import com.bitsson.gensokyou.landmark.LandmarkColors;
import com.bitsson.gensokyou.landmark.LandmarkSample;
import com.bitsson.gensokyou.mixin.client.RenderChunkRegionAccessor;
import com.bitsson.gensokyou.registry.LandmarkBlocks;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

import java.io.IOException;

@EventBusSubscriber(modid = "gensokyou", value = Dist.CLIENT)
public final class LandmarkBlockColors {
    private LandmarkBlockColors() {}

    @SubscribeEvent
    public static void register(RegisterColorHandlersEvent.Block event) {
        LandmarkColors colors = load();
        if (colors == null) {
            return;
        }
        event.register((state, view, pos, tint) -> {
            if (tint != 0 || pos == null) {
                return LandmarkColors.NO_TINT;
            }
            Level level = view instanceof RenderChunkRegionAccessor region ? region.gensokyou$level()
                    : view instanceof Level world ? world : null;
            return level != null && level.dimension().equals(LandmarkSample.DIMENSION)
                    ? colors.colorAt(pos.getX(), pos.getY(), pos.getZ(), BuiltInRegistries.BLOCK.getKey(state.getBlock()))
                    : LandmarkColors.NO_TINT;
        }, LandmarkBlocks.tintedBlocks());
    }

    private static LandmarkColors load() {
        String path = "/assets/gensokyou/landmark/" + LandmarkSample.ID.getPath() + ".bin";
        try (var resource = LandmarkBlockColors.class.getResourceAsStream(path)) {
            if (resource == null) {
                throw new IOException("Missing resource " + path);
            }
            return LandmarkColors.read(resource);
        } catch (IOException exception) {
            LogUtils.getLogger().error("Could not load landmark sample colors", exception);
            return null;
        }
    }
}
