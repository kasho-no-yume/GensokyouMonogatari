package com.bitsson.gensokyou.worldgen;

/** Seeded geography around fixed regional and landmark anchors. */
public final class GensokyoLayout {
    /** Bounds the coastline in every direction, including its longest headlands. */
    public static final int ISLAND_RADIUS = 4480;
    public static final int WRAP_PERIOD = 12_288;
    public static final int SEA_LEVEL = 63;
    public static final int COURTYARD_X = 3072;
    public static final int COURTYARD_Z = 128;
    public static final int COURTYARD_Y = 96;
    public static final int TWIN_GATE_X = 3088;
    public static final int TWIN_GATE_Z = 128;
    public static final int MISTY_LAKE_X = 1500;
    public static final int MISTY_LAKE_Z = -1750;

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

    private static final Region[] REGIONS = Region.values();
    private final double coastPhase;
    private final double xPhase;
    private final double zPhase;
    private final double reliefPhase;

    public GensokyoLayout(long seed) {
        var random = new java.util.SplittableRandom(seed);
        coastPhase = random.nextDouble(2.0 * Math.PI);
        xPhase = random.nextDouble(2.0 * Math.PI);
        zPhase = random.nextDouble(2.0 * Math.PI);
        reliefPhase = random.nextDouble(2.0 * Math.PI);
    }

    public boolean isIsland(int x, int z) {
        return coastDistance(x, z) >= 0.0;
    }

    /** Positive inland, negative offshore. A single radial coast keeps the main island joined. */
    public double coastDistance(int x, int z) {
        double angle = Math.atan2(z, x);
        double radius = 3700.0
                + 250.0 * Math.cos(angle * 2.0 + 0.3)
                + 155.0 * Math.sin(angle * 3.0 - 0.4)
                + 95.0 * Math.cos(angle * 5.0 + 0.8)
                + 55.0 * Math.sin(angle * 9.0 + 0.2)
                + 25.0 * Math.cos(angle * 15.0 - 1.3)
                + 120.0 * Math.cos(angle * 4.0 + coastPhase)
                + 65.0 * Math.sin(angle * 7.0 - coastPhase);
        // The closest landmark to the coast still needs room for its pad and approach.
        radius = Math.max(3310.0, radius);
        return radius - Math.hypot(x, z);
    }

    public Region regionAt(int x, int z) {
        if (!isIsland(x, z)) {
            return null;
        }
        return nearestRegion(x, z);
    }

    public boolean isMistyLake(int x, int z) {
        return lakeRadius(x, z) <= 1.0;
    }

    private double lakeRadius(int x, int z) {
        double u = ((double) x - MISTY_LAKE_X) / 355.0;
        double v = ((double) z - MISTY_LAKE_Z) / 300.0;
        return Math.hypot(u, v) + 0.12 * Math.sin(3.0 * u + reliefPhase) * Math.sin(3.0 * v);
    }

    public boolean isChireidenRiftVoid(int x, int y, int z) {
        if (y < 68) return false;
        double along = (double) z - 2050;
        double across = riftAcross(x, z);
        boolean shaft = Math.abs(along) < 185 && Math.abs(across) < 48 * (1 - Math.abs(along) / 185);
        double dx = (x + 1650.0) / 215.0;
        double dz = (z - 2050.0) / 170.0;
        boolean cavern = y <= 100 && dx * dx + dz * dz + Math.pow((y - 83.0) / 18.0, 2) < 1;
        return shaft || cavern;
    }

    private static double riftAcross(int x, int z) {
        return x + 1600.0 - 85.0 * Math.sin((z - 2050.0) / 165.0);
    }

    private Region nearestRegion(int x, int z) {
        // Warp the coordinates continuously before comparing anchors. This bends the shared
        // borders without scattering small patches of one biome into another.
        double warpedX = x + regionWarp(z, x, xPhase);
        double warpedZ = z + regionWarp(x, -z, zPhase);
        Region nearest = Region.HUMAN_VILLAGE;
        double nearestDistance = Double.POSITIVE_INFINITY;
        for (Region candidate : REGIONS) {
            double distance = squaredDistance(warpedX, warpedZ, candidate);
            if (distance < nearestDistance) {
                nearest = candidate;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    private static double regionWarp(double across, double along, double phase) {
        return 290.0 * Math.sin(across / 720.0 + along / 2400.0)
                + 160.0 * Math.sin(across / 490.0 - along / 1900.0 + phase);
    }

    private static double squaredDistance(double x, double z, Region region) {
        double dx = x - region.anchorX;
        double dz = z - region.anchorZ;
        return dx * dx + dz * dz;
    }

    public double surfaceHeight(int x, int z, double terrainNoise) {
        double inland = coastDistance(x, z);
        if (inland <= 0.0) {
            double depth = smoothstep(-inland / 580.0);
            return lerp(SEA_LEVEL + Math.max(-8.0, inland / 6.0), 36.0 + terrainNoise * 2.0, depth);
        }

        double warpedX = x + regionWarp(z, x, xPhase);
        double warpedZ = z + regionWarp(x, -z, zPhase);
        double weightedHeight = 0.0;
        double totalWeight = 0.0;
        for (Region region : REGIONS) {
            double weight = 1.0 / (squaredDistance(warpedX, warpedZ, region) + 160_000.0);
            weight *= weight * weight;
            weightedHeight += relief(region, x, z, terrainNoise) * weight;
            totalWeight += weight;
        }
        double height = Math.max(SEA_LEVEL + 2.0, weightedHeight / totalWeight);
        double lake = lakeRadius(x, z);
        if (lake < 1.42) {
            if (lake <= 1.0) {
                // Keep the basin below the waterline until the shoreline itself.
                height = lerp(36.0, SEA_LEVEL, lake * lake);
            } else {
                double bank = SEA_LEVEL + (lake - 1.0) * 38.0;
                height = lerp(bank, height, smoothstep((lake - 1.0) / 0.42));
            }
        }
        double beach = SEA_LEVEL + Math.min(12.0, inland / 6.0);
        height = lerp(beach, height, smoothstep(inland / 380.0));

        // Pads are tied to their world positions, including their grading around the edges.
        for (Region region : REGIONS) {
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

    private double relief(Region region, int x, int z, double noise) {
        double folds = Math.sin(x / 170.0 + z / 280.0 + reliefPhase);
        return region.baseHeight + switch (region) {
            case HAKUREI -> 26 * Math.sin(x / 340.0 - z / 680.0) + 13 * folds + 15 * noise;
            case MORIYA -> {
                double spine = x - 430 * Math.sin((z + 2850.0) / 580.0);
                double ridge = smoothstep(1 - Math.abs(spine) / 1080.0);
                yield 25 + ridge * (103 + 34 * folds) + 29 * noise;
            }
            case MAGIC_FOREST -> 24 * folds + 18 * Math.sin(z / 220.0 - x / 300.0) + 20 * noise;
            case BAMBOO -> 20 - 25 * Math.pow(Math.abs(folds), 4) + 12 * noise;
            case HAKUGYOKUROU -> {
                double rise = Math.max(0, 5 - Math.hypot((x + 1650.0) / 290.0, (z + 2730.0) / 360.0) + 0.3 * folds);
                double step = Math.floor(rise);
                yield 19 * (step + smoothstep((rise - step - 0.72) / 0.28)) + 5 * noise;
            }
            case CHIREIDEN -> 53 + 23 * folds + 26 * noise
                    - 55 * smoothstep(1 - Math.abs(riftAcross(x, z)) / 260.0);
            case SCARLET_MANSION -> 20 + 16 * folds + 11 * noise;
            case HUMAN_VILLAGE -> 8 + 6 * folds + 7 * noise;
        };
    }

    private static double smoothstep(double value) {
        double t = Math.max(0.0, Math.min(1.0, value));
        return t * t * (3.0 - 2.0 * t);
    }

    private static double lerp(double from, double to, double amount) {
        return from + (to - from) * amount;
    }
}
