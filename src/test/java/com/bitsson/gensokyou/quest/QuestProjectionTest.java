package com.bitsson.gensokyou.quest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class QuestProjectionTest {
    @Test void frontTargetsProjectAndRearTargetsStayOnTheCorrectEdge() {
        var center = QuestProjection.project(0, 0, 1, 480, 270, 18);
        assertEquals(240, center.x()); assertEquals(135, center.y()); assertFalse(center.edge());
        var right = QuestProjection.project(2, 0, 1, 480, 270, 18);
        assertEquals(462, right.x(), 1E-6); assertTrue(right.edge());
        var rearRight = QuestProjection.project(0.5, 0, -1, 480, 270, 18);
        assertEquals(462, rearRight.x(), 1E-6);
        var directlyBehind = QuestProjection.project(0, 0, -1, 480, 270, 18);
        assertEquals(252, directlyBehind.y(), 1E-6);
    }
    @Test void cornersAndCameraPlaneNeverProduceOffscreenOrNonfiniteCoordinates() {
        for (double x : new double[]{-1000, -0.1, 0, 0.1, 1000}) for (double y : new double[]{-1000, 0, 1000})
            for (double w : new double[]{-1, 0, 1E-12, 1}) {
                var point = QuestProjection.project(x, y, w, 320, 240, 18);
                assertTrue(Double.isFinite(point.x()) && Double.isFinite(point.y()));
                assertTrue(point.x() >= 18 - 1E-6 && point.x() <= 302 + 1E-6);
                assertTrue(point.y() >= 18 - 1E-6 && point.y() <= 222 + 1E-6);
            }
    }
}
