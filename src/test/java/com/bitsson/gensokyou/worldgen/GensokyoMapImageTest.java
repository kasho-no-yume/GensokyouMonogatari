package com.bitsson.gensokyou.worldgen;

import com.bitsson.gensokyou.support.MinecraftTestBootstrap;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class GensokyoMapImageTest {
    @BeforeAll
    static void bootstrap() {
        MinecraftTestBootstrap.ensureStarted();
    }

    @Test
    void playerMarkerTracksPixelGridAndHeadingWithoutChangingTheImage() {
        MapItemSavedData map = MapItemSavedData.createForClient((byte) 4, true, Level.OVERWORLD);
        byte[] base = GensokyoMapImage.buildBaseColors(new GensokyoLayout(0));
        map.colors = base.clone();
        int scale = GensokyoMapImage.BLOCKS_PER_PIXEL;

        assertMarker(map, 12 * scale, -20 * scale, 0, 24, -39, 0);
        assertMarker(map, -12 * scale, 20 * scale, 90, -23, 40, 4);
        assertMarker(map, 0, 0, 180, 0, 0, 8);
        assertMarker(map, 0, 0, -90, 0, 0, 12);

        assertArrayEquals(base, map.colors);
    }

    @Test
    void distantPlayerStaysAtTheCorrectMapEdge() {
        MapItemSavedData map = MapItemSavedData.createForClient((byte) 4, true, Level.OVERWORLD);

        GensokyoMapImage.updateViewerMarker(map, "viewer", 30_000_000, -30_000_000, 0);

        MapDecoration marker = map.getDecorations().iterator().next();
        assertEquals(MapDecorationTypes.PLAYER_OFF_MAP, marker.type());
        assertEquals(127, marker.x());
        assertEquals(-128, marker.y());
    }

    private static void assertMarker(MapItemSavedData map, int x, int z, float yaw, int mapX, int mapZ, int rotation) {
        GensokyoMapImage.updateViewerMarker(map, "viewer", x, z, yaw);

        var decorations = map.getDecorations().iterator();
        MapDecoration marker = decorations.next();
        assertEquals(MapDecorationTypes.PLAYER, marker.type());
        assertEquals(mapX, marker.x());
        assertEquals(mapZ, marker.y());
        assertEquals(rotation, marker.rot());
        assertFalse(decorations.hasNext());
    }
}
