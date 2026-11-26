package com.bitsson.gensokyou.quest;

/** Clip-space to GUI-space mapping, including a stable edge direction for targets behind the camera. */
public final class QuestProjection {
    public record Point(double x, double y, boolean edge, double angle) {}
    public static Point project(double clipX, double clipY, double clipW, int width, int height, int margin) {
        double cx = width * 0.5, cy = height * 0.5;
        double dx = clipX / Math.max(Math.abs(clipW), 1E-6) * cx;
        double dy = -clipY / Math.max(Math.abs(clipW), 1E-6) * cy;
        double limitX = Math.max(1, cx - margin), limitY = Math.max(1, cy - margin);
        boolean edge = clipW <= 0 || Math.abs(dx) > limitX || Math.abs(dy) > limitY;
        if (edge) {
            if (Math.abs(dx) + Math.abs(dy) < 1E-6) dy = 1;
            double scale = Math.min(limitX / Math.max(Math.abs(dx), 1E-12), limitY / Math.max(Math.abs(dy), 1E-12));
            dx *= scale; dy *= scale;
        }
        return new Point(cx + dx, cy + dy, edge, Math.atan2(dy, dx));
    }
    private QuestProjection() {}
}
