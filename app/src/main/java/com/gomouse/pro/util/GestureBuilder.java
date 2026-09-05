package com.gomouse.pro.util;

import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.os.Build;

/**
 * Builds {@link GestureDescription} objects for the official
 * {@code AccessibilityService#dispatchGesture} API. Every touch Gomouse Pro
 * ever sends to another app goes through one of these — a tap, a swipe, or
 * (for HOLD / JOYSTICK / DPAD) a chain of continued strokes that keeps a
 * pointer down for as long as the bound input stays active.
 */
public final class GestureBuilder {

    private GestureBuilder() {
    }

    /** A short, single-point tap. */
    public static GestureDescription buildTap(float x, float y, long durationMs) {
        Path path = new Path();
        path.moveTo(x, y);
        GestureDescription.StrokeDescription stroke =
                new GestureDescription.StrokeDescription(path, 0, Math.max(durationMs, 1));
        return new GestureDescription.Builder().addStroke(stroke).build();
    }

    /** Two quick taps at the same point, as one gesture. */
    public static GestureDescription buildDoubleTap(float x, float y, long tapDurationMs, long gapMs) {
        Path path1 = new Path();
        path1.moveTo(x, y);
        Path path2 = new Path();
        path2.moveTo(x, y);
        GestureDescription.StrokeDescription first =
                new GestureDescription.StrokeDescription(path1, 0, Math.max(tapDurationMs, 1));
        GestureDescription.StrokeDescription second =
                new GestureDescription.StrokeDescription(path2, tapDurationMs + gapMs, Math.max(tapDurationMs, 1));
        return new GestureDescription.Builder().addStroke(first).addStroke(second).build();
    }

    /** A straight-line swipe from (x, y) to (x + dx, y + dy). */
    public static GestureDescription buildSwipe(float x, float y, float dx, float dy, long durationMs) {
        Path path = new Path();
        path.moveTo(x, y);
        path.lineTo(x + dx, y + dy);
        GestureDescription.StrokeDescription stroke =
                new GestureDescription.StrokeDescription(path, 0, Math.max(durationMs, 1));
        return new GestureDescription.Builder().addStroke(stroke).build();
    }

    /** The first segment of a stroke that will be extended later via {@link #continueSegment}. */
    public static GestureDescription.StrokeDescription startContinuedStroke(float x, float y, long durationMs) {
        Path path = new Path();
        path.moveTo(x, y);
        return new GestureDescription.StrokeDescription(path, 0, Math.max(durationMs, 1), true);
    }

    /**
     * Extends a previous continued stroke to a new point. Pass
     * {@code willContinue = false} on the final call to lift the touch.
     */
    public static GestureDescription.StrokeDescription continueSegment(
            GestureDescription.StrokeDescription previous, float fromX, float fromY,
            float toX, float toY, long durationMs, boolean willContinue) {
        Path path = new Path();
        path.moveTo(fromX, fromY);
        if (toX != fromX || toY != fromY) {
            path.lineTo(toX, toY);
        }
        return previous.continueStroke(path, 0, Math.max(durationMs, 1), willContinue);
    }

    public static GestureDescription single(GestureDescription.StrokeDescription stroke) {
        return new GestureDescription.Builder().addStroke(stroke).build();
    }

    public static boolean gesturesSupported() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N; // dispatchGesture requires API 24+
    }
}
