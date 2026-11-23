package com.bitsson.gensokyou.client;

import com.bitsson.gensokyou.item.GensokyoDebugMapItem;
import com.bitsson.gensokyou.worldgen.GensokyoMapImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import javax.annotation.Nullable;

public final class GensokyoDebugMapClient {
    private static final String VIEWER_MARKER = "gensokyou:debug_map_viewer";

    private GensokyoDebugMapClient() {
    }

    @Nullable
    public static MapItemSavedData forViewer(MapItemSavedData data) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !player.hasInfiniteMaterials()) {
            return null;
        }
        if (player.level().dimension() != GensokyoDebugMapItem.DIMENSION) {
            data.removeDecoration(VIEWER_MARKER);
        } else {
            GensokyoMapImage.updateViewerMarker(data, VIEWER_MARKER, player.getX(), player.getZ(), player.getYRot());
        }
        return data;
    }
}
