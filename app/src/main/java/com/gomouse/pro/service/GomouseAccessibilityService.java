package com.gomouse.pro.service;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.GestureDescription;
import android.graphics.Point;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;

import com.gomouse.pro.model.ActionType;
import com.gomouse.pro.model.DirectionBinding;
import com.gomouse.pro.model.InputMapping;
import com.gomouse.pro.model.InputSourceType;
import com.gomouse.pro.model.Profile;
import com.gomouse.pro.storage.ProfileRepository;
import com.gomouse.pro.util.GestureBuilder;
import com.gomouse.pro.util.InputCodeUtils;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Gomouse Pro's keymapping engine.
 *
 * Every touch this app ever injects into another app goes through the
 * official {@link AccessibilityService#dispatchGesture} API — there is no
 * root, exploit, or hidden-API path. Two things feed into it:
 *
 * 1. Physical keyboard/gamepad input, delivered here via {@link #onKeyEvent}
 *    because the service requests {@code FLAG_REQUEST_FILTER_KEY_EVENTS}.
 * 2. Direct touches on the floating overlay's own buttons, forwarded in via
 *    the {@code onDirect*} methods by {@code service.OverlayService}.
 *
 * Both paths end up at the same small set of dispatch helpers below.
 *
 * Known Android platform limits (see README "Keterbatasan"): this service
 * receives KeyEvents (keyboard keys + gamepad buttons) system-wide, but
 * Android does not deliver generic mouse MotionEvents to an
 * AccessibilityService — only to whichever window currently has them. Mouse
 * handling therefore lives in OverlayService instead, which
 * calls back into the public methods here to actually trigger a gesture.
 */
public class GomouseAccessibilityService extends AccessibilityService {

    private static final String TAG = "GomouseA11yService";

    private static final long HOLD_SEGMENT_MS = 3000L;
    private static final long HOLD_REFRESH_MS = 1000L;
    private static final long TAP_DURATION_MS = 55L;
    private static final long DOUBLE_TAP_GAP_MS = 90L;
    private static final long RELEASE_DURATION_MS = 40L;

    private static volatile GomouseAccessibilityService instance;

    private ProfileRepository profileRepository;
    private volatile Profile activeProfile;
    private final Handler handler = new Handler(Looper.getMainLooper());

    /** Active continued-stroke sessions, keyed by mappingId (HOLD/JOYSTICK) or "mappingId:direction" (DPAD). */
    private final ConcurrentHashMap<String, StrokeSession> sessions = new ConcurrentHashMap<>();

    /** Which of [up, down, left, right] are currently physically held, per JOYSTICK mapping id. */
    private final ConcurrentHashMap<String, boolean[]> heldDirections = new ConcurrentHashMap<>();

    public static GomouseAccessibilityService getInstance() {
        return instance;
    }

    public static boolean isRunning() {
        return instance != null;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        profileRepository = ProfileRepository.getInstance(this);
        reloadActiveProfile();

        AccessibilityServiceInfo info = getServiceInfo();
        if (info != null) {
            info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
            setServiceInfo(info);
        }
        Log.i(TAG, "Gomouse Pro accessibility service connected");
    }

    /** Call after the active profile changes (new profile picked, or edited and saved). */
    public void reloadActiveProfile() {
        String id = profileRepository.getActiveProfileId();
        activeProfile = (id != null) ? profileRepository.load(id) : null;
        cancelAllSessions();
    }

    public Profile getActiveProfile() {
        return activeProfile;
    }

    // -------------------------------------------------------------------
    // Physical keyboard / gamepad input
    // -------------------------------------------------------------------

    @Override
    public boolean onKeyEvent(KeyEvent event) {
        Profile profile = activeProfile;
        if (profile == null) {
            return false;
        }
        int keyCode = event.getKeyCode();
        if (!InputCodeUtils.isRemappable(keyCode)) {
            return false;
        }
        int action = event.getAction();
        InputSourceType source = InputCodeUtils.isFromGamepad(event)
                ? InputSourceType.GAMEPAD_BUTTON
                : InputSourceType.KEYBOARD_KEY;

        boolean isMapped = isKeyBound(profile, source, keyCode);
        if (!isMapped) {
            return false;
        }
        // We manage HOLD/JOYSTICK/DPAD duration ourselves; ignore the OS's
        // own key-repeat down events for keys we already own so we don't
        // restart/re-tap on every repeat tick.
        if (action == KeyEvent.ACTION_DOWN && event.getRepeatCount() > 0) {
            return true;
        }
        if (action != KeyEvent.ACTION_DOWN && action != KeyEvent.ACTION_UP) {
            return true;
        }

        for (InputMapping mapping : profile.getMappings()) {
            if (!mapping.isVisible()) {
                continue;
            }
            if (mapping.usesDirectionBindings()) {
                handleDirectionalKey(mapping, source, keyCode, action);
            } else if (mapping.getSourceType() == source && mapping.getInputCode() == keyCode) {
                handleSimpleMapping(mapping, action);
            }
        }
        return true;
    }

    private boolean isKeyBound(Profile profile, InputSourceType source, int keyCode) {
        for (InputMapping mapping : profile.getMappings()) {
            if (!mapping.isVisible()) {
                continue;
            }
            if (mapping.usesDirectionBindings()) {
                DirectionBinding[] dirs = {mapping.getUp(), mapping.getDown(), mapping.getLeft(), mapping.getRight()};
                for (DirectionBinding d : dirs) {
                    if (d.isBound() && d.getSourceType() == source && d.getInputCode() == keyCode) {
                        return true;
                    }
                }
            } else if (mapping.getSourceType() == source && mapping.getInputCode() == keyCode) {
                return true;
            }
        }
        return false;
    }

    private void handleSimpleMapping(InputMapping mapping, int action) {
        float[] c = centerPx(mapping);
        switch (mapping.getActionType()) {
            case TAP:
                if (action == KeyEvent.ACTION_DOWN) {
                    dispatch(GestureBuilder.buildTap(c[0], c[1], TAP_DURATION_MS));
                }
                break;
            case DOUBLE_TAP:
                if (action == KeyEvent.ACTION_DOWN) {
                    dispatch(GestureBuilder.buildDoubleTap(c[0], c[1], TAP_DURATION_MS, DOUBLE_TAP_GAP_MS));
                }
                break;
            case SWIPE:
                if (action == KeyEvent.ACTION_DOWN) {
                    Point screen = getScreenSize();
                    float dx = mapping.getSwipeDx() * screen.x;
                    float dy = mapping.getSwipeDy() * screen.y;
                    dispatch(GestureBuilder.buildSwipe(c[0], c[1], dx, dy, mapping.getSwipeDurationMs()));
                }
                break;
            case HOLD:
                if (action == KeyEvent.ACTION_DOWN) {
                    startOrUpdateHold(mapping.getId(), c[0], c[1]);
                } else {
                    endSession(mapping.getId());
                }
                break;
            default:
                // JOYSTICK / DPAD are handled through handleDirectionalKey instead.
                break;
        }
    }

    private void handleDirectionalKey(InputMapping mapping, InputSourceType source, int keyCode, int action) {
        String[] names = {"up", "down", "left", "right"};
        DirectionBinding[] dirs = {mapping.getUp(), mapping.getDown(), mapping.getLeft(), mapping.getRight()};
        for (int i = 0; i < 4; i++) {
            DirectionBinding d = dirs[i];
            if (d.isBound() && d.getSourceType() == source && d.getInputCode() == keyCode) {
                boolean pressed = (action == KeyEvent.ACTION_DOWN);
                if (mapping.getActionType() == ActionType.DPAD) {
                    setDpadDirection(mapping, names[i], pressed);
                } else if (mapping.getActionType() == ActionType.JOYSTICK) {
                    setJoystickDirection(mapping, i, pressed);
                }
            }
        }
    }

    // -------------------------------------------------------------------
    // Public API used by OverlayService / OverlayButtonView for direct
    // touches on the overlay itself, and for mouse-button / mouse-look input
    // (Android does not route generic mouse MotionEvents to an
    // AccessibilityService, so the overlay's own window captures those and
    // calls back in here).
    // -------------------------------------------------------------------

    public void onDirectTap(InputMapping mapping) {
        float[] c = centerPx(mapping);
        dispatch(GestureBuilder.buildTap(c[0], c[1], TAP_DURATION_MS));
    }

    public void onDirectDoubleTap(InputMapping mapping) {
        float[] c = centerPx(mapping);
        dispatch(GestureBuilder.buildDoubleTap(c[0], c[1], TAP_DURATION_MS, DOUBLE_TAP_GAP_MS));
    }

    public void onDirectSwipe(InputMapping mapping) {
        float[] c = centerPx(mapping);
        Point screen = getScreenSize();
        float dx = mapping.getSwipeDx() * screen.x;
        float dy = mapping.getSwipeDy() * screen.y;
        dispatch(GestureBuilder.buildSwipe(c[0], c[1], dx, dy, mapping.getSwipeDurationMs()));
    }

    public void onDirectHoldDown(InputMapping mapping) {
        float[] c = centerPx(mapping);
        startOrUpdateHold(mapping.getId(), c[0], c[1]);
    }

    public void onDirectHoldUp(InputMapping mapping) {
        endSession(mapping.getId());
    }

    /** knobDx/knobDy: -1..1, the joystick knob's current offset from center as a fraction of its radius. */
    public void onDirectJoystickDrag(InputMapping mapping, float knobDx, float knobDy) {
        float[] c = centerPx(mapping);
        float radius = radiusPx(mapping);
        Profile profile = activeProfile;
        float sensX = profile != null ? profile.getSensitivityX() : 1f;
        float sensY = profile != null ? profile.getSensitivityY() : 1f;
        float targetX = c[0] + clamp(knobDx * sensX, -1f, 1f) * radius;
        float targetY = c[1] + clamp(knobDy * sensY, -1f, 1f) * radius;
        startOrUpdateHold(mapping.getId(), targetX, targetY);
    }

    public void onDirectJoystickRelease(InputMapping mapping) {
        endSession(mapping.getId());
    }

    public void onDirectDpadDown(InputMapping mapping, String direction) {
        setDpadDirection(mapping, direction, true);
    }

    public void onDirectDpadUp(InputMapping mapping, String direction) {
        setDpadDirection(mapping, direction, false);
    }

    /** Used by mouse-look: nudges a JOYSTICK mapping's knob by a relative delta rather than an absolute one. */
    public void onMouseLookDelta(InputMapping mapping, float dxPx, float dyPx) {
        StrokeSession session = sessions.get(mapping.getId());
        float[] c = centerPx(mapping);
        float radius = radiusPx(mapping);
        float baseX = session != null ? session.lastX : c[0];
        float baseY = session != null ? session.lastY : c[1];
        float targetX = clampToCircle(baseX + dxPx, c[0], radius, true, baseY, c[1]);
        float targetY = clampToCircle(baseY + dyPx, c[1], radius, false, baseX, c[0]);
        startOrUpdateHold(mapping.getId(), targetX, targetY);
    }

    public void onMouseButtonPressed(InputMapping mapping, int action) {
        handleSimpleMapping(mapping, action);
    }

    // -------------------------------------------------------------------
    // JOYSTICK (keyboard/gamepad WASD-style) and DPAD direction bookkeeping
    // -------------------------------------------------------------------

    private void setJoystickDirection(InputMapping mapping, int directionIndex, boolean pressed) {
        boolean[] held = heldDirections.computeIfAbsent(mapping.getId(), k -> new boolean[4]);
        held[directionIndex] = pressed;
        float dx = (held[3] ? 1 : 0) - (held[2] ? 1 : 0); // right - left
        float dy = (held[1] ? 1 : 0) - (held[0] ? 1 : 0); // down - up
        if (dx == 0 && dy == 0) {
            endSession(mapping.getId());
            return;
        }
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        dx /= len;
        dy /= len;
        float[] c = centerPx(mapping);
        float radius = radiusPx(mapping);
        Profile profile = activeProfile;
        float sensX = profile != null ? profile.getSensitivityX() : 1f;
        float sensY = profile != null ? profile.getSensitivityY() : 1f;
        float targetX = c[0] + dx * radius * clamp(sensX, 0.2f, 3f);
        float targetY = c[1] + dy * radius * clamp(sensY, 0.2f, 3f);
        startOrUpdateHold(mapping.getId(), targetX, targetY);
    }

    private void setDpadDirection(InputMapping mapping, String direction, boolean pressed) {
        String sessionKey = mapping.getId() + ":" + direction;
        if (pressed) {
            float[] pos = dpadDirectionPx(mapping, direction);
            startOrUpdateHold(sessionKey, pos[0], pos[1]);
        } else {
            endSession(sessionKey);
        }
    }

    // -------------------------------------------------------------------
    // Continued-stroke session management (backs HOLD, JOYSTICK, DPAD)
    // -------------------------------------------------------------------

    private static final class StrokeSession {
        GestureDescription.StrokeDescription lastStroke;
        float lastX;
        float lastY;
        float targetX;
        float targetY;
        final AtomicBoolean ending = new AtomicBoolean(false);
        final Runnable refreshRunnable;

        StrokeSession(Runnable refreshRunnable) {
            this.refreshRunnable = refreshRunnable;
        }
    }

    private void startOrUpdateHold(String sessionKey, float x, float y) {
        StrokeSession existing = sessions.get(sessionKey);
        if (existing != null) {
            existing.targetX = x;
            existing.targetY = y;
            return;
        }
        StrokeSession session = new StrokeSession(null);
        session.lastX = x;
        session.lastY = y;
        session.targetX = x;
        session.targetY = y;
        session.lastStroke = GestureBuilder.startContinuedStroke(x, y, HOLD_SEGMENT_MS);
        sessions.put(sessionKey, session);
        dispatch(GestureBuilder.single(session.lastStroke));
        scheduleRefresh(sessionKey);
    }

    private void scheduleRefresh(String sessionKey) {
        Runnable runnable = () -> refreshSession(sessionKey);
        StrokeSession session = sessions.get(sessionKey);
        if (session == null) {
            return;
        }
        handler.postDelayed(runnable, HOLD_REFRESH_MS);
    }

    private void refreshSession(String sessionKey) {
        StrokeSession session = sessions.get(sessionKey);
        if (session == null || session.ending.get()) {
            return;
        }
        GestureDescription.StrokeDescription next = GestureBuilder.continueSegment(
                session.lastStroke, session.lastX, session.lastY, session.targetX, session.targetY,
                HOLD_SEGMENT_MS, true);
        session.lastStroke = next;
        session.lastX = session.targetX;
        session.lastY = session.targetY;
        dispatch(GestureBuilder.single(next));
        scheduleRefresh(sessionKey);
    }

    private void endSession(String sessionKey) {
        StrokeSession session = sessions.remove(sessionKey);
        if (session == null || !session.ending.compareAndSet(false, true)) {
            return;
        }
        GestureDescription.StrokeDescription end = GestureBuilder.continueSegment(
                session.lastStroke, session.lastX, session.lastY, session.lastX, session.lastY,
                RELEASE_DURATION_MS, false);
        dispatch(GestureBuilder.single(end));
    }

    private void cancelAllSessions() {
        for (String key : sessions.keySet()) {
            endSession(key);
        }
        heldDirections.clear();
    }

    private void dispatch(GestureDescription description) {
        if (!GestureBuilder.gesturesSupported()) {
            return;
        }
        try {
            dispatchGesture(description, null, null);
        } catch (Exception e) {
            Log.w(TAG, "dispatchGesture failed: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------
    // Screen geometry helpers — normalized [0,1] mapping coords -> pixels
    // -------------------------------------------------------------------

    private float[] centerPx(InputMapping mapping) {
        Point size = getScreenSize();
        return new float[]{mapping.getX() * size.x, mapping.getY() * size.y};
    }

    private float radiusPx(InputMapping mapping) {
        Point size = getScreenSize();
        float w = mapping.getWidth() * size.x;
        float h = mapping.getHeight() * size.y;
        return Math.min(w, h) / 2f;
    }

    private float[] dpadDirectionPx(InputMapping mapping, String direction) {
        float[] c = centerPx(mapping);
        float r = radiusPx(mapping) * 0.62f;
        switch (direction) {
            case "up":
                return new float[]{c[0], c[1] - r};
            case "down":
                return new float[]{c[0], c[1] + r};
            case "left":
                return new float[]{c[0] - r, c[1]};
            case "right":
                return new float[]{c[0] + r, c[1]};
            default:
                return c;
        }
    }

    private Point getScreenSize() {
        Point size = new Point(1080, 1920);
        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (wm == null) {
            return size;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            android.graphics.Rect bounds = wm.getCurrentWindowMetrics().getBounds();
            size.set(bounds.width(), bounds.height());
        } else {
            DisplayMetrics dm = new DisplayMetrics();
            wm.getDefaultDisplay().getRealMetrics(dm);
            size.set(dm.widthPixels, dm.heightPixels);
        }
        return size;
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    private static float clampToCircle(float newVal, float base, float radius, boolean isX,
                                        float otherVal, float otherBase) {
        float dx = isX ? newVal - base : otherVal - otherBase;
        float dy = isX ? otherVal - otherBase : newVal - base;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len <= radius) {
            return newVal;
        }
        float scale = radius / len;
        return isX ? base + dx * scale : base + dy * scale;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Gomouse Pro does not react to accessibility tree events — only to
        // key events and to direct calls from the overlay — but AccessibilityService
        // requires this override.
    }

    @Override
    public void onInterrupt() {
        cancelAllSessions();
    }

    @Override
    public boolean onUnbind(android.content.Intent intent) {
        cancelAllSessions();
        instance = null;
        return super.onUnbind(intent);
    }
}
