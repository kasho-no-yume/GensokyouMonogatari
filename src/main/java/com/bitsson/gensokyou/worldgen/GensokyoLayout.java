package com.bitsson.gensokyou.worldgen;

/** Shared fixed geography used by the biome source, terrain, and developer map. */
public final class GensokyoLayout {
    public static final int ISLAND_RADIUS = 4096;
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
        double nx = (double) x / ISLAND_RADIUS;
        double nz = (double) z / ISLAND_RADIUS;
        return nx * nx + nz * nz <= 1.0;
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
        Region nearest = Region.HUMAN_VILLAGE;
        long nearestDistance = Long.MAX_VALUE;
        for (Region candidate : Region.values()) {
            long distance = squaredDistance(x, z, candidate.anchorX, candidate.anchorZ);
            if (distance < nearestDistance) {
                nearest = candidate;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    private static long squaredDistance(int x, int z, int anchorX, int anchorZ) {
        long dx = (long) x - anchorX;
        long dz = (long) z - anchorZ;
        return dx * dx + dz * dz;
    }

    /** Seeded noise is supplied by the density-function codec; the same geometry bounds all seeds. */
    public static double surfaceHeight(int x, int z, double terrainNoise) {
        double nx = (double) x / ISLAND_RADIUS;
        double nz = (double) z / ISLAND_RADIUS;
        double radial = Math.sqrt(nx * nx + nz * nz);
        if (radial >= 0.90) {
            double coast = smoothstep((radial - 0.92) / 0.16);
            return lerp(SEA_LEVEL + 2.0, 36.0, coast) + terrainNoise * 2.0;
        }

        Region region = nearestRegion(x, z);
        double edgeBlend = smoothstep((1.0 - radial) / 0.10);
        double height = lerp(SEA_LEVEL + 2.0, region.baseHeight, edgeBlend);
        height += terrainNoise * 9.0;

        height += gaussian(x, z, 0, -3000, 1250.0, 70.0);       // 妖怪之山主脊
        height += gaussian(x, z, -1600, -2600, 850.0, 42.0);    // 白玉楼阶状台地
        height -= gaussian(x, z, 1500, -1750, 430.0, 62.0);     // 雾之湖盆地
        height += gaussian(x, z, -1600, 2050, 520.0, 58.0);     // 地灵殿裂谷高地
        height -= gaussian(x, z, -1600, 2050, 650.0, 13.0);     // 裂谷入口洼地

        double landmarkDistance = Math.hypot(x - region.landmarkX, z - region.landmarkZ);
        if (landmarkDistance < 176.0) {
            double padBlend = smoothstep((landmarkDistance - 72.0) / 104.0);
            height = lerp(region.landmarkY, height, padBlend);
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
