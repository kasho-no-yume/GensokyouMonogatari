package com.bitsson.gensokyou.client.resident;

import java.util.List;

/** A short authored polyline, walked out and back with a pause at each waypoint. */
public final class ResidentRoute {
    public static final int MAX_POINTS = 16;
    private final List<Point> points;
    private final double length;
    private final double minX, minY, minZ, maxX, maxY, maxZ;

    public ResidentRoute(List<Point> points) {
        if (points.isEmpty() || points.size() > MAX_POINTS) {
            throw new IllegalArgumentException("route needs 1.." + MAX_POINTS + " points");
        }
        this.points = List.copyOf(points);
        double total = 0;
        for (int i = 1; i < points.size(); i++) {
            double distance = points.get(i).distance(points.get(i - 1));
            if (distance < 0.05 || distance > 64) {
                throw new IllegalArgumentException("adjacent route points must be 0.05..64 blocks apart");
            }
            total += distance;
        }
        length = total;
        minX = points.stream().mapToDouble(Point::x).min().orElseThrow();
        minY = points.stream().mapToDouble(Point::y).min().orElseThrow();
        minZ = points.stream().mapToDouble(Point::z).min().orElseThrow();
        maxX = points.stream().mapToDouble(Point::x).max().orElseThrow();
        maxY = points.stream().mapToDouble(Point::y).max().orElseThrow();
        maxZ = points.stream().mapToDouble(Point::z).max().orElseThrow();
    }

    public List<Point> points() { return points; }

    public Sample sample(double ticks, double speed, double pauseTicks, float facing) {
        if (!Double.isFinite(ticks) || ticks < 0 || !Double.isFinite(speed) || speed <= 0
                || !Double.isFinite(pauseTicks) || pauseTicks < 0) {
            throw new IllegalArgumentException("invalid route time or speed");
        }
        if (points.size() == 1) return new Sample(points.getFirst(), facing, 0, false);
        int legs = (points.size() - 1) * 2;
        double period = length * 2 / speed + legs * pauseTicks;
        double cycles = Math.floor(ticks / period);
        double within = ticks % period;
        double walked = cycles * length * 2;
        for (int i = 0; i < legs; i++) {
            int a = i < points.size() - 1 ? i : legs - i;
            int b = i < points.size() - 1 ? a + 1 : a - 1;
            Point from = points.get(a);
            Point to = points.get(b);
            double distance = from.distance(to);
            double duration = distance / speed;
            float yaw = (float) Math.toDegrees(-Math.atan2(to.x - from.x, to.z - from.z));
            if (within < pauseTicks) return new Sample(from, yaw, walked, false);
            within -= pauseTicks;
            if (within < duration) {
                return new Sample(from.lerp(to, within / duration), yaw, walked + within * speed, true);
            }
            within -= duration;
            walked += distance;
        }
        // Floating-point round-off at the period boundary.
        return new Sample(points.getFirst(), facing, (cycles + 1) * length * 2, false);
    }

    public double distanceSquared(Point viewer) {
        double dx = Math.max(0, Math.max(minX - viewer.x, viewer.x - maxX));
        double dy = Math.max(0, Math.max(minY - viewer.y, viewer.y - maxY));
        double dz = Math.max(0, Math.max(minZ - viewer.z, viewer.z - maxZ));
        return dx * dx + dy * dy + dz * dz;
    }

    public record Point(double x, double y, double z) {
        public Point {
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                    || Math.abs(x) > 29_999_900 || Math.abs(z) > 29_999_900 || Math.abs(y) > 4096) {
                throw new IllegalArgumentException("invalid resident coordinate");
            }
        }

        public double distance(Point other) {
            double dx = x - other.x, dy = y - other.y, dz = z - other.z;
            return Math.sqrt(dx * dx + dy * dy + dz * dz);
        }

        public Point lerp(Point to, double fraction) {
            return new Point(x + (to.x - x) * fraction, y + (to.y - y) * fraction,
                    z + (to.z - z) * fraction);
        }
    }

    public record Sample(Point position, float yaw, double walked, boolean walking) {}
}
