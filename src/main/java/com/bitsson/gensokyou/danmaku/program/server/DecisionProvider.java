package com.bitsson.gensokyou.danmaku.program.server;

import com.bitsson.gensokyou.danmaku.program.math.*;
import com.bitsson.gensokyou.danmaku.program.ops.*;
import java.util.*;

/** The only boundary through which the authority runtime reads world facts. */
public interface DecisionProvider {
    record Player(UUID uuid, Vec3 feet, Vec3 eyes, double height, boolean alive,
                  boolean creative, boolean spectator, double halfWidth) {
        public Player {
            Objects.requireNonNull(uuid); Objects.requireNonNull(feet); Objects.requireNonNull(eyes);
            Deterministic.nonnegative(height); Deterministic.nonnegative(halfWidth);
        }
        public Vec3 point(AuthorityDescriptions.TargetPoint point) {
            return switch (point) {
                case EYES -> eyes;
                case CHEST -> feet.add(Vec3.Y.multiply(height * .65));
                case FEET -> feet;
            };
        }
        /** Distance to the actual player AABB, before deducting the member radius. */
        public double distance(Vec3 p) {
            double x=StrictMath.max(0,StrictMath.abs(p.x()-feet.x())-halfWidth);
            double z=StrictMath.max(0,StrictMath.abs(p.z()-feet.z())-halfWidth);
            double y=StrictMath.max(0,StrictMath.max(feet.y()-p.y(),p.y()-feet.y()-height));
            return new Vec3(x,y,z).length();
        }
    }
    record Block(boolean loaded, boolean air, boolean solid, double top) {
        public Block { Deterministic.finite(top); }
    }
    List<Player> players();
    Optional<Actions.Pose> entity(UUID uuid);
    boolean loaded(Vec3 point);
    Block block(int x, int y, int z);

    default List<UUID> captureTargets(Vec3 origin, AuthorityDescriptions.CaptureTargets policy) {
        return players().stream().filter(p -> p.alive() && (!policy.excludeCreative() || !p.creative())
                        && (!policy.excludeSpectator() || !p.spectator()) && p.feet().subtract(origin).length()<=policy.radius())
                .sorted(Comparator.comparingDouble((Player p)->p.feet().subtract(origin).length()).thenComparing(Player::uuid))
                .limit(policy.maximum()).map(Player::uuid).toList();
    }
    default Optional<Player> target(UUID id) {
        return players().stream().filter(p->p.uuid().equals(id) && p.alive() && !p.creative() && !p.spectator()).findFirst();
    }
    static Frame captureFrame(Vec3 origin, Vec3 target, Vec3 up, Vec3 previousRight, Vec3 fallbackRight) {
        Vec3 forward=target.subtract(origin).unit();
        Vec3 right=up.cross(forward);
        if (right.length()<1e-6) {
            right=project(previousRight==null?fallbackRight:previousRight,forward);
            if (right.length()<1e-6) right=project(fallbackRight,forward);
            if (right.length()<1e-6) right=project(StrictMath.abs(forward.dot(Vec3.X))<.9?Vec3.X:Vec3.Z,forward);
        }
        right=right.unit();
        return new Frame(origin,right,forward.cross(right).unit(),forward);
    }
    private static Vec3 project(Vec3 right, Vec3 forward) { return right.subtract(forward.multiply(right.dot(forward))); }

    record Surface(List<Optional<Vec3>> points, int queries) {
        public Surface { points=List.copyOf(points); }
    }
    default Surface resolveSurface(List<Vec3> points, AuthorityDescriptions.SurfaceMode mode, int maxSteps, int maxQueries) {
        if (points.size()>512 || maxSteps<1 || maxSteps>4096 || maxQueries<1 || maxQueries>512)
            throw new IllegalArgumentException("bounded surface query required");
        List<Optional<Vec3>> result=new ArrayList<>(); int queries=0;
        for (Vec3 point:points) {
            Optional<Vec3> found=Optional.empty();
            int x=(int)StrictMath.floor(point.x()), y=(int)StrictMath.floor(point.y()), z=(int)StrictMath.floor(point.z());
            for (int step=0;step<maxSteps && queries<maxQueries;step++) {
                int sampleY=mode==AuthorityDescriptions.SurfaceMode.DOWNWARD_SOLID_TOP?y-step:y+step;
                Block block=block(x,sampleY,z); queries++;
                if (!block.loaded()) break;
                if (mode==AuthorityDescriptions.SurfaceMode.DOWNWARD_SOLID_TOP && block.solid()) {
                    found=Optional.of(new Vec3(point.x(),sampleY+block.top(),point.z())); break;
                }
                if (mode==AuthorityDescriptions.SurfaceMode.FIRST_AIR_ABOVE && block.air()) {
                    found=Optional.of(new Vec3(point.x(),sampleY,point.z())); break;
                }
            }
            result.add(found);
        }
        return new Surface(result,queries);
    }
    /** Finite voxel corridor, including endpoints; exhaustion and unloaded blocks fail closed. */
    default boolean corridor(Vec3 from, Vec3 to, double halfWidth, int maxQueries) {
        Deterministic.nonnegative(halfWidth);
        if (maxQueries<1 || maxQueries>4096) throw new IllegalArgumentException("bounded corridor required");
        Vec3 delta=to.subtract(from); int steps=(int)StrictMath.ceil(delta.length()*2); int queries=0;
        Set<List<Integer>> visited=new HashSet<>();
        if (steps>maxQueries) return false;
        for (int i=0;i<=steps;i++) {
            Vec3 p=from.add(delta.multiply(steps==0?0:(double)i/steps));
            for (int x=(int)StrictMath.floor(p.x()-halfWidth);x<=(int)StrictMath.floor(p.x()+halfWidth);x++)
                for (int y=(int)StrictMath.floor(p.y()-halfWidth);y<=(int)StrictMath.floor(p.y()+halfWidth);y++)
                    for (int z=(int)StrictMath.floor(p.z()-halfWidth);z<=(int)StrictMath.floor(p.z()+halfWidth);z++) {
                        if (!visited.add(List.of(x,y,z))) continue;
                        if (++queries>maxQueries) return false;
                        Block b=block(x,y,z); if (!b.loaded() || b.solid()) return false;
                    }
        }
        return true;
    }
}
