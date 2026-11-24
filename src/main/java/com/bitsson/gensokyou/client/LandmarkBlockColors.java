package com.bitsson.gensokyou.client;

import com.bitsson.gensokyou.landmark.HakureiOuterGrounds;
import com.bitsson.gensokyou.landmark.HakureiShrineLandmark;
import com.bitsson.gensokyou.landmark.HakureiShrinePreview;
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
        LandmarkColors sampleColors = load(LandmarkSample.ID.getPath());
        LandmarkColors shrinePreviewColors = load(HakureiShrinePreview.ID.getPath());
        LandmarkColors gensokyoShrineColors = load(HakureiShrineLandmark.COLOR_ID.getPath());
        LandmarkColors[] gensokyoColors = {
                gensokyoShrineColors,
                load(HakureiOuterGrounds.FRONT.colorId().getPath()),
                load(HakureiOuterGrounds.NORTH.colorId().getPath()),
                load(HakureiOuterGrounds.EAST.colorId().getPath())
        };
        if (sampleColors == null && shrinePreviewColors == null && !hasAny(gensokyoColors)) {
            return;
        }
        event.register((state, view, pos, tint) -> {
            if (tint != 0 || pos == null) {
                return LandmarkColors.NO_TINT;
            }
            Level level = view instanceof RenderChunkRegionAccessor region ? region.gensokyou$level()
                    : view instanceof Level world ? world : null;
            if (level == null) {
                return LandmarkColors.NO_TINT;
            }
            var blockKey = BuiltInRegistries.BLOCK.getKey(state.getBlock());
            if (level.dimension().equals(HakureiShrineLandmark.GENSOKYO_DIMENSION)) {
                for (LandmarkColors colors : gensokyoColors) {
                    if (colors == null) {
                        continue;
                    }
                    int color = colors.colorAt(pos.getX(), pos.getY(), pos.getZ(), blockKey);
                    if (color != LandmarkColors.NO_TINT) {
                        return color;
                    }
                }
                return LandmarkColors.NO_TINT;
            }
            if (!level.dimension().equals(LandmarkSample.DIMENSION)) {
                return LandmarkColors.NO_TINT;
            }
            if (sampleColors != null) {
                int color = sampleColors.colorAt(pos.getX(), pos.getY(), pos.getZ(), blockKey);
                if (color != LandmarkColors.NO_TINT) {
                    return color;
                }
            }
            return shrinePreviewColors != null
                    ? shrinePreviewColors.colorAt(pos.getX(), pos.getY(), pos.getZ(), blockKey)
                    : LandmarkColors.NO_TINT;
        }, LandmarkBlocks.tintedBlocks());
    }

    private static boolean hasAny(LandmarkColors[] colors) {
        for (LandmarkColors value : colors) {
            if (value != null) {
                return true;
            }
        }
        return false;
    }

    private static LandmarkColors load(String idPath) {
        String path = "/assets/gensokyou/landmark/" + idPath + ".bin";
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
