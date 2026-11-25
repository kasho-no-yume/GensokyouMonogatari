package com.bitsson.gensokyou.client.resident;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ResidentModelTest {
    private static final ResidentWalker.Frame FRAME = new ResidentWalker.Frame(new ResidentRoute.Point(0, 0, 0), 0, 1.25, true);

    @Test
    void modelEmitsFiniteGeometryWithIndependentSkinAndClothAndNormalLight() {
        ResidentModel model = new ResidentModel();
        ResidentAppearance look = ResidentAppearance.of("geometry-test");
        Capture out = new Capture();
        model.render(new PoseStack(), out, 0x00800060, look, FRAME, 100, true);
        assertTrue(out.vertices.size() >= 250);
        assertTrue(out.colors.contains(look.skin()));
        assertTrue(out.colors.contains(look.cloth()));
        assertEquals(Set.of("96,128"), out.lights);
        assertTrue(out.maxUv <= 1);
        assertTrue(out.minUv >= 0);
    }

    @Test
    void reusingModelNeverLeaksHatVisibilityOrPoseBetweenResidents() {
        ResidentModel shared = new ResidentModel();
        Set<String> combinations = new HashSet<>();
        for (int i = 0; i < 300; i++) {
            ResidentAppearance look = ResidentAppearance.of("look/" + i);
            combinations.add(look.outfit() + "/" + look.headStyle() + "/" + look.accessory());
            Capture actual = new Capture();
            shared.render(new PoseStack(), actual, 0x00A00070, look, FRAME, 77, true);
            Capture fresh = new Capture();
            new ResidentModel().render(new PoseStack(), fresh, 0x00A00070, look, FRAME, 77, true);
            assertEquals(fresh.vertices, actual.vertices, "shared state leaked for " + look);
            assertEquals(fresh.colors, actual.colors);
        }
        assertTrue(combinations.size() >= 28);
    }

    @Test
    void distanceDetailReductionActuallyRemovesGeometryAndAtlasMatchesTheModel() throws Exception {
        ResidentModel model = new ResidentModel();
        Capture detailed = new Capture(), distant = new Capture();
        var look = java.util.stream.IntStream.range(0, 100).mapToObj(i -> ResidentAppearance.of("details/" + i))
                .filter(a -> a.accessory() != ResidentAppearance.Accessory.NONE).findFirst().orElseThrow();
        model.render(new PoseStack(), detailed, 0, look, FRAME, 0, true);
        model.render(new PoseStack(), distant, 0, look, FRAME, 0, false);
        assertTrue(distant.vertices.size() < detailed.vertices.size());
        var image = ImageIO.read(Path.of("src/main/resources/assets/gensokyou/textures/entity/village_resident.png").toFile());
        assertEquals(128, image.getWidth()); assertEquals(128, image.getHeight());
    }

    private static final class Capture implements VertexConsumer {
        final List<List<Float>> vertices = new ArrayList<>();
        final Set<Integer> colors = new HashSet<>();
        final Set<String> lights = new HashSet<>();
        float minUv = Float.POSITIVE_INFINITY, maxUv = Float.NEGATIVE_INFINITY;

        @Override public VertexConsumer addVertex(float x, float y, float z) {
            assertTrue(Float.isFinite(x) && Float.isFinite(y) && Float.isFinite(z));
            vertices.add(List.of(x, y, z)); return this;
        }
        @Override public VertexConsumer setColor(int r, int g, int b, int a) {
            colors.add(a << 24 | r << 16 | g << 8 | b); return this;
        }
        @Override public VertexConsumer setUv(float u, float v) {
            minUv = Math.min(minUv, Math.min(u, v)); maxUv = Math.max(maxUv, Math.max(u, v)); return this;
        }
        @Override public VertexConsumer setUv1(int u, int v) { return this; }
        @Override public VertexConsumer setUv2(int u, int v) { lights.add(u + "," + v); return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) { return this; }
    }
}
