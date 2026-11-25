package com.bitsson.gensokyou.client.resident;

import org.jetbrains.annotations.Nullable;

/** Local motion state. An obstructed step never consumes route time. */
public final class ResidentWalker {
    private final ResidentScene.Slot slot;
    private final ResidentAppearance appearance;
    private double time;
    private Frame previous;
    private Frame current;
    private boolean visible;

    public ResidentWalker(ResidentScene.Slot slot) {
        this.slot = slot;
        appearance = ResidentAppearance.of(slot.identity());
        time = appearance.phaseTicks();
    }

    public ResidentScene.Slot slot() { return slot; }
    public ResidentAppearance appearance() { return appearance; }
    public boolean visible() { return visible; }

    public void tick(Surface surface) {
        previous = current;
        ResidentRoute.Sample next = slot.route().sample(time + (current == null ? 0 : 1),
                appearance.speed(), appearance.pauseTicks(), slot.facing());
        ResidentRoute.Point ground = surface.resolve(next.position(), appearance.scale(), slot.protectedGround());
        if (ground != null && (current == null || (Math.abs(ground.y() - current.position.y()) <= 0.6
                && surface.clear(current.position, ground, appearance.scale())))) {
            boolean initialized = current != null;
            current = new Frame(ground, next.yaw(), next.walked(), next.walking());
            if (initialized) time++;
            else previous = current;
            visible = true;
            return;
        }
        if (current != null) {
            ResidentRoute.Point support = surface.resolve(current.position, appearance.scale(), slot.protectedGround());
            visible = support != null && Math.abs(support.y() - current.position.y()) < 0.01;
            current = new Frame(current.position, current.yaw, current.walked, false);
        } else {
            visible = false;
        }
    }

    @Nullable
    public Frame frame(float partialTick) {
        if (!visible || current == null) return null;
        double t = Math.max(0, Math.min(1, partialTick));
        float turn = wrapDegrees(current.yaw - previous.yaw);
        return new Frame(previous.position.lerp(current.position, t), previous.yaw + turn * (float) t,
                previous.walked + (current.walked - previous.walked) * t, current.walking);
    }

    public static float wrapDegrees(float degrees) {
        return degrees - (float) Math.floor((degrees + 180) / 360) * 360;
    }

    public interface Surface {
        @Nullable ResidentRoute.Point resolve(ResidentRoute.Point expected, float scale, boolean protectedGround);
        boolean clear(ResidentRoute.Point from, ResidentRoute.Point to, float scale);
    }

    public record Frame(ResidentRoute.Point position, float yaw, double walked, boolean walking) {}
}
