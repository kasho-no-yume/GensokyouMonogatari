package com.bitsson.gensokyou.client.resident;

import java.util.function.Predicate;
import java.util.function.BooleanSupplier;
import java.util.Set;

/** Conservative entry-only visibility test, independent of client rendering classes. */
public final class ResidentSpawnVisibility {
    public static final double EDGE_MARGIN=1.0;
    private ResidentSpawnVisibility() {}

    public record Bounds(double minX,double minY,double minZ,double maxX,double maxY,double maxZ) {}

    public static boolean mayAppear(ResidentRoute.Point feet,float scale,Predicate<Bounds> inView) {
        // Before a fresh camera view is available, defer ordinary population admission.
        if(inView==null) return false;
        double radius=.55+EDGE_MARGIN;
        return !inView.test(new Bounds(feet.x()-radius,feet.y()-EDGE_MARGIN,feet.z()-radius,
                feet.x()+radius,feet.y()+2.4*scale+EDGE_MARGIN,feet.z()+radius));
    }

    /** Re-check the first actual render frame in case the camera turned after the client tick. */
    public static boolean firstFrameAllowed(Set<ResidentWalker> established,ResidentWalker resident,BooleanSupplier inView) {
        if(resident.slot().identity().startsWith("gensokyou:preview/") || established.contains(resident)) return true;
        if(inView.getAsBoolean()) return false;
        established.add(resident);
        return true;
    }
}
