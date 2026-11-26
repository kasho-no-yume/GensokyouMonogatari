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
    /** Twin Sukima gate sits on the north boundary stone dais inside Hakurei Shrine (16 blocks north of spawn). */
    public static final int TWIN_GATE_X = 3072;
    public static final int TWIN_GATE_Z = 112;
    /** Long pilgrimage approach road from Hakurei First Torii (X=3022) down to Human Village east edge (X=40). */
    public static final int HAKUREI_ROAD_START_X = 3022;
    public static final int HAKUREI_ROAD_END_X = 40;
    public static final int VILLAGE_CENTER_X = -200, VILLAGE_CENTER_Z = 100;
    public static final int VILLAGE_BASE_Y = 72, VILLAGE_STREAM_WATER_Y = 70, VILLAGE_HIEDA_Y = 75;

    // Outer-grounds footprints mirror HakureiOuterGrounds. Fixed pads keep the satellite
    // structures level without flattening the whole Hakurei region.
    public static final int HAKUREI_FRONT_X = 2976;
    public static final int HAKUREI_FRONT_Z = 96;
    public static final int HAKUREI_FRONT_WIDTH = 46;
    public static final int HAKUREI_FRONT_DEPTH = 60;
    public static final int HAKUREI_NORTH_X = 3040;
    public static final int HAKUREI_NORTH_Z = 62;
    public static final int HAKUREI_NORTH_WIDTH = 72;
    public static final int HAKUREI_NORTH_DEPTH = 31;
    public static final int HAKUREI_EAST_X = 3118;
    public static final int HAKUREI_EAST_Z = 100;
    public static final int HAKUREI_EAST_WIDTH = 48;
    public static final int HAKUREI_EAST_DEPTH = 92;

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
            if (region == Region.HAKUREI || region == Region.HUMAN_VILLAGE) {
                continue; // Hakurei Shrine uses a sculpted stepped hillside shoulder below.
            }
            double dx = (double) x - region.landmarkX;
            double dz = (double) z - region.landmarkZ;
            double distanceSquared = dx * dx + dz * dz;
            if (distanceSquared < 176.0 * 176.0) {
                double padBlend = smoothstep((Math.sqrt(distanceSquared) - 72.0) / 104.0);
                height = lerp(region.landmarkY, height, padBlend);
                break;
            }
        }

        height = sculptHumanVillage(height, x, z);

        // Smooth roadbed grading along the long pilgrimage road from Hakurei foot (Y=84) to Human Village (Y=72).
        double roadDist = hakureiRoadDistance(x, z);
        if (roadDist < 18.0) {
            double rt = Math.max(0.0, Math.min(1.0, (HAKUREI_ROAD_START_X - (double) x)
                    / (HAKUREI_ROAD_START_X - HAKUREI_ROAD_END_X)));
            double targetRoadY = lerp(84.0, 72.0, smoothstep(rt));
            double nearFoot = 1.0 - smoothstep(Math.max(0.0, HAKUREI_ROAD_START_X - x) / 48.0);
            double approach = 1.0 - smoothstep((x - 40.0) / 48.0);
            double graded = lerp(Math.max(SEA_LEVEL + 3.0, lerp(height, targetRoadY, 0.72)), 84.0, nearFoot);
            graded = lerp(graded, targetRoadY, approach);
            double roadBlend = 1.0 - smoothstep((roadDist - 5.0) / 13.0);
            height = lerp(height, graded, roadBlend);
        }

        // Sculpted Hakurei Shrine hillside: west approach ramp (Y=84->96), flat shrine plateau (Y=96), east back ridge.
        double hdx = Math.max(0.0, Math.max(3022.0 - x, x - 3120.0));
        double hdz = Math.max(0.0, Math.max(93.0 - z, z - 163.0));
        double shrineEdgeDist = Math.hypot(hdx, hdz);
        if (shrineEdgeDist < 48.0) {
            double shrineTarget;
            if (x < 3056) {
                double st = Math.max(0.0, Math.min(1.0, (x - 3022.0) / 34.0));
                shrineTarget = lerp(84.0, COURTYARD_Y, smoothstep(st));
            } else if (x <= 3110) {
                shrineTarget = COURTYARD_Y;
            } else {
                double bt = Math.max(0.0, Math.min(1.0, (x - 3110.0) / 28.0));
                shrineTarget = lerp(COURTYARD_Y, COURTYARD_Y + 8.0, smoothstep(bt));
            }
            double blend = 1.0 - smoothstep(shrineEdgeDist / 48.0);
            height = lerp(height, shrineTarget, blend);
        }
        height = sculptHakureiOuterGrounds(height, x, z);
        return height;
    }

    private double sculptHumanVillage(double height, int x, int z) {
        double radius = Math.hypot(x - VILLAGE_CENTER_X, z - VILLAGE_CENTER_Z);
        if (radius < 250) {
            double micro = 0.8 * Math.sin(x / 55.0) * Math.cos(z / 55.0);
            double target = VILLAGE_BASE_Y + micro * smoothstep((radius - 120) / 80.0);
            height = lerp(height, target, 1 - smoothstep((radius - 145) / 105.0));
        }
        double estateDistance = rectangleDistance(x, z, -242, -48, -159, 20);
        if (estateDistance < 14) height = lerp(height, VILLAGE_HIEDA_Y, 1 - smoothstep(estateDistance / 14));
        if (x >= -360 && x <= 20) {
            double distance = Math.abs(z - villageStreamCenterZ(x));
            if (distance < 7) {
                double bed = 68.2 + 0.5 * Math.min(distance / 2.6, 1.0);
                double valley = lerp(bed, height, smoothstep((distance - 2.6) / 4.4));
                double end = smoothstep((x + 360.0) / 40) * (1 - smoothstep((x + 20.0) / 40));
                height = lerp(height, valley, end);
            }
        }
        // The last 40 blocks of village road meet the existing pilgrimage road at exactly 72.
        if (x >= 0 && x <= 40 && Math.abs(z - 100) < 18) {
            height = lerp(height, VILLAGE_BASE_Y, 1 - smoothstep((Math.abs(z - 100) - 3.0) / 15));
        }
        return height;
    }

    /** Fixed across seeds. The level canal reach fits the approved, north-facing tavern deck. */
    public static double villageStreamCenterZ(int x) {
        double t = (x + 200.0) / 180;
        double original = 156 - 16 * Math.sin(t * 1.35) + 8 * Math.cos(t * 2.2);
        double canal = smoothstep((x + 180.0) / 20) * (1 - smoothstep((x + 100.0) / 40));
        return lerp(original, 158.0, canal);
    }

    private double sculptHakureiOuterGrounds(double height, int x, int z) {
        // Never let a satellite pad's blended edge raise or lower the established main shrine.
        if (x >= 3022 && x <= 3116 && z >= 93 && z <= 163) {
            return height;
        }
        double frontDistance = rectangleDistance(x, z,
                HAKUREI_FRONT_X, HAKUREI_FRONT_Z,
                HAKUREI_FRONT_X + HAKUREI_FRONT_WIDTH - 1,
                HAKUREI_FRONT_Z + HAKUREI_FRONT_DEPTH - 1);
        // The pilgrimage road keeps its own graded surface through the front gateway.
        if (frontDistance < 10.0 && hakureiRoadDistance(x, z) > 6.5) {
            double target = z < COURTYARD_Z ? 83.0 : 82.0;
            height = lerp(height, target, 1.0 - smoothstep(frontDistance / 10.0));
        }

        double northDistance = rectangleDistance(x, z,
                HAKUREI_NORTH_X, HAKUREI_NORTH_Z,
                HAKUREI_NORTH_X + HAKUREI_NORTH_WIDTH - 1,
                HAKUREI_NORTH_Z + HAKUREI_NORTH_DEPTH - 1);
        if (northDistance < 12.0) {
            height = lerp(height, 96.0, 1.0 - smoothstep(northDistance / 12.0));
        }

        double eastDistance = rectangleDistance(x, z,
                HAKUREI_EAST_X, HAKUREI_EAST_Z,
                HAKUREI_EAST_X + HAKUREI_EAST_WIDTH - 1,
                HAKUREI_EAST_Z + HAKUREI_EAST_DEPTH - 1);
        if (eastDistance < 14.0) {
            double target = z < 156 ? 100.0 : 98.0;
            height = lerp(height, target, 1.0 - smoothstep(eastDistance / 14.0));
        }
        return height;
    }

    private static double rectangleDistance(int x, int z, int minX, int minZ, int maxX, int maxZ) {
        double dx = Math.max(0.0, Math.max(minX - x, x - maxX));
        double dz = Math.max(0.0, Math.max(minZ - z, z - maxZ));
        return Math.hypot(dx, dz);
    }

    /** Gentle winding Z centerline of the Hakurei-to-Human-Village pilgrimage road. */
    public double hakureiRoadCenterZ(int x) {
        double t = Math.max(0.0, Math.min(1.0,
                (HAKUREI_ROAD_START_X - (double) x) / (HAKUREI_ROAD_START_X - HAKUREI_ROAD_END_X)));
        double baseZ = lerp(COURTYARD_Z, 100.0, smoothstep(t));
        double env = Math.sin(Math.PI * t);
        double meander = env * env * (34.0 * Math.sin(t * 7.0 + reliefPhase * 0.5)
                + 18.0 * Math.cos(t * 13.0 - reliefPhase * 0.3));
        return baseZ + meander;
    }

    /** Perpendicular Z distance to the pilgrimage road, or +Inf outside its X span. */
    public double hakureiRoadDistance(int x, int z) {
        if (x < HAKUREI_ROAD_END_X || x > HAKUREI_ROAD_START_X) {
            return Double.POSITIVE_INFINITY;
        }
        return Math.abs((double) z - hakureiRoadCenterZ(x));
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
            // Broad inhabited slopes, rather than short, noisy hummocks between neighboring houses.
            case HUMAN_VILLAGE -> 8 + 3 * Math.sin(x / 240.0 + reliefPhase) + 3 * Math.sin(z / 310.0) + 1.2 * noise;
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
