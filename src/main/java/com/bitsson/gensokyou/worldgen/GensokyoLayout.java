package com.bitsson.gensokyou.worldgen;

/** Shared fixed geography used by the biome source, terrain, and developer map. */
public final class GensokyoLayout {
    /** Bounds the coastline in every direction, including its longest headlands. */
    public static final int ISLAND_RADIUS = 4288;
    public static final int WRAP_PERIOD = 12_288;
    public static final int SEA_LEVEL = 63;
    public static final int COURTYARD_X = 3072;
    public static final int COURTYARD_Z = 128;
    public static final int COURTYARD_Y = 96;
    public static final int TWIN_GATE_X = 3088;
    public static final int TWIN_GATE_Z = 128;

    public enum Region {
        HAKUREI("hakurei_shrine", 3072, 128, 3072, 128, 96, 86),
        MORIYA("moriya_mountain", 0, -3000, 0, -3000, 190, 132),
        MAGIC_FOREST("magic_forest", -2500, -900, -2500, -900, 92, 84),
        BAMBOO("misty_bamboo_grove", 1650, 2300, 1650, 2300, 80, 75),
        HAKUGYOKUROU("hakugyokurou", -1600, -2600, -1600, -2600, 160, 118),
        CHIREIDEN("chireiden", -1600, 2050, -1320, 2220, 92, 82),
        SCARLET_MANSION("scarlet_devil_mansion", 1700, -1450, 2200, -1100, 96, 77),
        HUMAN_VILLAGE("human_village", -200, 100, -200, 100, 72, 70);

        private final String biomePath;
        private final int anchorX;
        private final int anchorZ;
        private final int landmarkX;
        private final int landmarkZ;
        private final int landmarkY;
        private final double baseHeight;

        Region(String biomePath, int anchorX, int anchorZ, int landmarkX, int landmarkZ,
               int landmarkY, double baseHeight) {
            this.biomePath = biomePath;
            this.anchorX = anchorX;
            this.anchorZ = anchorZ;
            this.landmarkX = landmarkX;
            this.landmarkZ = landmarkZ;
            this.landmarkY = landmarkY;
            this.baseHeight = baseHeight;
        }

        public String biomePath() {
            return biomePath;
        }

        public int anchorX() {
            return anchorX;
        }

        public int anchorZ() {
            return anchorZ;
        }

        public int landmarkX() {
            return landmarkX;
        }

        public int landmarkZ() {
            return landmarkZ;
        }

        public int landmarkY() {
            return landmarkY;
        }

        public double baseHeight() {
            return baseHeight;
        }
    }

    private GensokyoLayout() {
    }

    public static boolean isIsland(int x, int z) {
        return coastDistance(x, z) >= 0.0;
    }

    /** Positive inland, negative offshore. A single radial coast keeps the main island joined. */
    public static double coastDistance(int x, int z) {
        double angle = Math.atan2(z, x);
        double radius = 3700.0
                + 250.0 * Math.cos(angle * 2.0 + 0.3)
                + 155.0 * Math.sin(angle * 3.0 - 0.4)
                + 95.0 * Math.cos(angle * 5.0 + 0.8)
                + 55.0 * Math.sin(angle * 9.0 + 0.2)
                + 25.0 * Math.cos(angle * 15.0 - 1.3);
        return radius - Math.hypot(x, z);
    }

    public static Region regionAt(int x, int z) {
        if (!isIsland(x, z)) {
            return null;
        }
        return nearestRegion(x, z);
    }

    public static boolean isMistyLake(int x, int z) {
        long dx = (long) x - 1500;
        long dz = (long) z + 1750;
        return dx * dx + dz * dz <= 350L * 350L;
    }

    public static boolean isChireidenRiftVoid(int x, int y, int z) {
        long dx = (long) x + 1600;
        long dz = (long) z - 2050;
        boolean shaft = dx * dx + dz * dz <= 54L * 54L && y >= 70 && y <= 180;
        boolean cavern = dx * dx / (230L * 230L) + dz * dz / (190L * 190L) <= 1
                && y >= 68 && y <= 96;
        return shaft || cavern;
    }

    private static Region nearestRegion(int x, int z) {
        // Warp the coordinates continuously before comparing anchors. This bends the shared
        // borders without scattering small patches of one biome into another.
        double warpedX = x + regionWarp(z, x);
        double warpedZ = z + regionWarp(x, -z);
        Region nearest = Region.HUMAN_VILLAGE;
        double nearestDistance = Double.POSITIVE_INFINITY;
        for (Region candidate : Region.values()) {
            double distance = squaredDistance(warpedX, warpedZ, candidate);
            if (distance < nearestDistance) {
                nearest = candidate;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    private static double regionWarp(double across, double along) {
        return 335.0 * Math.sin(across / 690.0 + along / 2400.0)
                + 120.0 * Math.sin(across / 300.0 - along / 1600.0);
    }

    private static double squaredDistance(double x, double z, Region region) {
        double dx = x - region.anchorX;
        double dz = z - region.anchorZ;
        return dx * dx + dz * dz;
    }

    /** Seeded noise is supplied by the density-function codec; the same geometry bounds all seeds. */
    public static double surfaceHeight(int x, int z, double terrainNoise) {
        double inland = coastDistance(x, z);
        if (inland <= 0.0) {
            double depth = smoothstep(-inland / 580.0);
            return lerp(SEA_LEVEL - 1.0, 36.0 + terrainNoise * 2.0, depth);
        }

        double warpedX = x + regionWarp(z, x);
        double warpedZ = z + regionWarp(x, -z);
        double weightedHeight = 0.0;
        double totalWeight = 0.0;
        for (Region region : Region.values()) {
            double weight = 1.0 / (squaredDistance(warpedX, warpedZ, region) + 160_000.0);
            weight *= weight * weight;
            weightedHeight += region.baseHeight * weight;
            totalWeight += weight;
        }
        double height = weightedHeight / totalWeight + terrainNoise * 9.0;

        height += gaussian(x, z, 0, -3000, 1250.0, 70.0);       // 妖怪之山主脊
        height += gaussian(x, z, -1600, -2600, 850.0, 42.0);    // 白玉楼阶状台地
        height -= gaussian(x, z, 1500, -1750, 430.0, 62.0);     // 雾之湖盆地
        height += gaussian(x, z, -1600, 2050, 520.0, 58.0);     // 地灵殿裂谷高地
        height -= gaussian(x, z, -1600, 2050, 650.0, 13.0);     // 裂谷入口洼地

        // Keep the coast and the land mask aligned while easing into the inland relief.
        if (!isMistyLake(x, z)) {
            height = Math.max(SEA_LEVEL + 2.0, height);
        }
        double beach = SEA_LEVEL - 1.0 + Math.min(12.0, inland / 14.0);
        height = lerp(beach, height, smoothstep(inland / 380.0));

        // Pads are tied to their world positions, including their grading around the edges.
        for (Region region : Region.values()) {
            double dx = (double) x - region.landmarkX;
            double dz = (double) z - region.landmarkZ;
            double distanceSquared = dx * dx + dz * dz;
            if (distanceSquared < 176.0 * 176.0) {
                double padBlend = smoothstep((Math.sqrt(distanceSquared) - 72.0) / 104.0);
                height = lerp(region.landmarkY, height, padBlend);
                break;
            }
        }

        double courtyardDistance = Math.hypot(x - COURTYARD_X, z - COURTYARD_Z);
        if (courtyardDistance < 144.0) {
            double padBlend = smoothstep((courtyardDistance - 40.0) / 104.0);
            height = lerp(COURTYARD_Y, height, padBlend);
        }
        return height;
    }

    private static double gaussian(int x, int z, int centerX, int centerZ, double radius, double amplitude) {
        double dx = (double) x - centerX;
        double dz = (double) z - centerZ;
        double normalized = (dx * dx + dz * dz) / (radius * radius);
        return amplitude * Math.exp(-0.5 * normalized);
    }

    private static double smoothstep(double value) {
        double t = Math.max(0.0, Math.min(1.0, value));
        return t * t * (3.0 - 2.0 * t);
    }

    private static double lerp(double from, double to, double amount) {
        return from + (to - from) * amount;
    }
}
