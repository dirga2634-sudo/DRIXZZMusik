package com.gomouse.pro.service;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.os.Build;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;

import com.gomouse.pro.GomouseApplication;
import com.gomouse.pro.R;
import com.gomouse.pro.model.ActionType;
import com.gomouse.pro.model.InputMapping;
import com.gomouse.pro.model.InputSourceType;
import com.gomouse.pro.model.Profile;
import com.gomouse.pro.overlay.OverlayButtonView;
import com.gomouse.pro.storage.ProfileRepository;
import com.gomouse.pro.ui.MainActivity;
import com.gomouse.pro.util.InputCodeUtils;
import com.gomouse.pro.util.PermissionUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * Owns every floating overlay window Gomouse Pro draws on top of other apps.
 *
 * Each mapped control gets its OWN small {@code TYPE_APPLICATION_OVERLAY}
 * window, sized to exactly that control's bounds, instead of one big
 * full-screen window with a "touchable region" carved out of it. That one
 * choice is deliberate: making empty space transparent to touch by punching
 * holes in a single window's touchable region requires
 * {@code ViewTreeObserver.InternalInsetsInfo}, which is a hidden/internal
 * API not present in the public Android SDK — using it would have quietly
 * broken the "no hidden APIs" requirement this project is built on. With
 * one small window per control, empty space is never covered by any window
 * at all, so it reaches the app underneath with zero extra code — every
 * flag and API used here (TYPE_APPLICATION_OVERLAY, FLAG_NOT_TOUCH_MODAL,
 * View#requestPointerCapture, View#onCapturedPointerEvent) is public and
 * documented.
 *
 * This service never touches the target app's process, memory, or files —
 * it only draws its own windows and asks
 * {@link GomouseAccessibilityService} to dispatch official synthetic touch
 * gestures, exactly as if the user had touched the screen.
 */
public class OverlayService extends Service implements OverlayButtonView.Listener {

    public static final String ACTION_START = "com.gomouse.pro.action.START_OVERLAY";
    public static final String ACTION_STOP = "com.gomouse.pro.action.STOP_OVERLAY";
    public static final String ACTION_RELOAD_PROFILE = "com.gomouse.pro.action.RELOAD_PROFILE";
    public static final String ACTION_TOGGLE_MOUSE = "com.gomouse.pro.action.TOGGLE_MOUSE";

    private static final int NOTIFICATION_ID = 4201;

    private WindowManager windowManager;
    private ProfileRepository profileRepository;
    private boolean showing = false;

    private final Map<String, View> mappingWindows = new HashMap<>();
    private View toggleHandleView;
    private Paint toggleHandlePaint;
    private CaptureAnchorView captureAnchorView;
    private boolean mouseActive = false;
    private int lastMouseButtonState = 0;

    private static volatile boolean running = false;

    public static boolean isRunning() {
        return running;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        profileRepository = ProfileRepository.getInstance(this);
        windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : ACTION_START;
        if (action == null) {
            action = ACTION_START;
        }
        switch (action) {
            case ACTION_STOP:
                stopOverlay();
                return START_NOT_STICKY;
            case ACTION_RELOAD_PROFILE:
                reloadAndRebind();
                return START_STICKY;
            case ACTION_TOGGLE_MOUSE:
                setMouseActive(!mouseActive);
                return START_STICKY;
            case ACTION_START:
            default:
                startOverlay();
                return START_STICKY;
        }
    }

    private void startOverlay() {
        if (!PermissionUtils.canDrawOverlays(this)) {
            Toast.makeText(this, R.string.error_overlay_permission_required, Toast.LENGTH_LONG).show();
            stopSelf();
            return;
        }
        if (!PermissionUtils.isAccessibilityServiceEnabled(this)) {
            Toast.makeText(this, R.string.error_accessibility_required, Toast.LENGTH_LONG).show();
            stopSelf();
            return;
        }
        String profileId = profileRepository.getActiveProfileId();
        if (profileId == null) {
            Toast.makeText(this, R.string.error_no_active_profile, Toast.LENGTH_LONG).show();
            stopSelf();
            return;
        }

        startForeground(NOTIFICATION_ID, buildNotification());
        running = true;
        showing = true;

        addToggleHandle();
        reloadAndRebind();
    }

    private void reloadAndRebind() {
        GomouseAccessibilityService a11y = GomouseAccessibilityService.getInstance();
        if (a11y != null) {
            a11y.reloadActiveProfile();
        }
        if (!showing) {
            return;
        }
        String profileId = profileRepository.getActiveProfileId();
        Profile profile = profileId != null ? profileRepository.load(profileId) : null;
        bindProfile(profile);
    }

    /** Removes every existing per-control window and re-adds one for each visible mapping. */
    private void bindProfile(Profile profile) {
        for (View v : mappingWindows.values()) {
            safeRemoveView(v);
        }
        mappingWindows.clear();
        if (profile == null) {
            return;
        }
        Point screen = getScreenSize();
        for (InputMapping mapping : profile.getMappings()) {
            if (!mapping.isVisible()) {
                continue;
            }
            OverlayButtonView view = new OverlayButtonView(this, mapping);
            view.setListener(this);
            view.setAlpha(profile.getOpacity());

            int w = Math.max(1, Math.round(mapping.getWidth() * screen.x));
            int h = Math.max(1, Math.round(mapping.getHeight() * screen.y));
            int left = Math.round(mapping.getX() * screen.x - w / 2f);
            int top = Math.round(mapping.getY() * screen.y - h / 2f);

            WindowManager.LayoutParams params = smallWindowParams(w, h, left, top, false);
            try {
                windowManager.addView(view, params);
                mappingWindows.put(mapping.getId(), view);
            } catch (Exception ignored) {
                // Skip a control if its window can't be added rather than crashing the service.
            }
        }
    }

    private WindowManager.LayoutParams smallWindowParams(int w, int h, int x, int y, boolean focusable) {
        int flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
        if (!focusable) {
            flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        }
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                w, h,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                flags,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = x;
        params.y = y;
        return params;
    }

    // --- Small always-on toggle handle for Mouse Mode ---

    private void addToggleHandle() {
        if (toggleHandleView != null) {
            return;
        }
        toggleHandlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        toggleHandlePaint.setColor(Color.parseColor("#CC2979FF"));

        toggleHandleView = new View(this) {
            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                float r = Math.min(getWidth(), getHeight()) / 2f;
                canvas.drawCircle(getWidth() / 2f, getHeight() / 2f, r, toggleHandlePaint);
            }
        };
        toggleHandleView.setOnClickListener(v -> setMouseActive(!mouseActive));

        float density = getResources().getDisplayMetrics().density;
        int sizePx = Math.round(28 * density);
        WindowManager.LayoutParams params = smallWindowParams(
                sizePx, sizePx, Math.round(4 * density), Math.round(80 * density), false);
        try {
            windowManager.addView(toggleHandleView, params);
        } catch (Exception ignored) {
            toggleHandleView = null;
        }
    }

    private void setMouseActive(boolean active) {
        if (mouseActive == active || toggleHandleView == null) {
            return;
        }
        mouseActive = active;
        toggleHandlePaint.setColor(Color.parseColor(active ? "#CC00E676" : "#CC2979FF"));
        toggleHandleView.invalidate();
        if (active) {
            addCaptureAnchor();
        } else {
            removeCaptureAnchor();
        }
    }

    /**
     * A minimal focusable window whose only job is to hold official Pointer
     * Capture (View#requestPointerCapture) while Mouse Mode is on, so mouse
     * movement/buttons can drive a look-joystick or MOUSE_BUTTON mappings
     * from anywhere on screen. All the other control windows stay
     * non-focusable throughout, so ordinary touch on them is never affected
     * by this toggle.
     */
    private void addCaptureAnchor() {
        if (captureAnchorView != null) {
            return;
        }
        captureAnchorView = new CaptureAnchorView(this);
        float density = getResources().getDisplayMetrics().density;
        int sizePx = Math.round(10 * density);
        WindowManager.LayoutParams params = smallWindowParams(sizePx, sizePx, 0, 0, true);
        try {
            windowManager.addView(captureAnchorView, params);
            captureAnchorView.post(() -> {
                captureAnchorView.requestFocus();
                captureAnchorView.requestPointerCapture();
            });
        } catch (Exception ignored) {
            captureAnchorView = null;
        }
    }

    private void removeCaptureAnchor() {
        if (captureAnchorView == null) {
            return;
        }
        try {
            captureAnchorView.releasePointerCapture();
        } catch (Exception ignored) {
            // no-op
        }
        safeRemoveView(captureAnchorView);
        captureAnchorView = null;
    }

    private final class CaptureAnchorView extends View {
        CaptureAnchorView(Context context) {
            super(context);
            setFocusable(true);
            setFocusableInTouchMode(true);
        }

        @Override
        public boolean onCapturedPointerEvent(MotionEvent event) {
            GomouseAccessibilityService service = GomouseAccessibilityService.getInstance();
            Profile profile = service != null ? service.getActiveProfile() : null;
            if (service == null || profile == null) {
                return false;
            }
            if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                for (InputMapping mapping : profile.getMappings()) {
                    if (mapping.getActionType() == ActionType.JOYSTICK && mapping.isMouseLookEnabled()
                            && mapping.isVisible()) {
                        service.onMouseLookDelta(mapping, event.getX(), event.getY());
                    }
                }
            }
            int buttonState = event.getButtonState();
            if (buttonState != lastMouseButtonState) {
                handleMouseButtonChange(service, profile, lastMouseButtonState, buttonState);
                lastMouseButtonState = buttonState;
            }
            return true;
        }
    }

    private void handleMouseButtonChange(GomouseAccessibilityService service, Profile profile,
                                          int oldState, int newState) {
        int[] buttons = {
                InputCodeUtils.MOUSE_BUTTON_PRIMARY, InputCodeUtils.MOUSE_BUTTON_SECONDARY,
                InputCodeUtils.MOUSE_BUTTON_TERTIARY, InputCodeUtils.MOUSE_BUTTON_BACK,
                InputCodeUtils.MOUSE_BUTTON_FORWARD
        };
        for (int button : buttons) {
            boolean wasDown = (oldState & button) != 0;
            boolean isDown = (newState & button) != 0;
            if (wasDown == isDown) {
                continue;
            }
            int action = isDown ? android.view.KeyEvent.ACTION_DOWN : android.view.KeyEvent.ACTION_UP;
            for (InputMapping mapping : profile.getMappings()) {
                if (!mapping.isVisible() || mapping.usesDirectionBindings()) {
                    continue;
                }
                if (mapping.getSourceType() == InputSourceType.MOUSE_BUTTON && mapping.getInputCode() == button) {
                    service.onMouseButtonPressed(mapping, action);
                }
            }
        }
    }

    private void stopOverlay() {
        running = false;
        for (View v : mappingWindows.values()) {
            safeRemoveView(v);
        }
        mappingWindows.clear();
        if (toggleHandleView != null) {
            safeRemoveView(toggleHandleView);
            toggleHandleView = null;
        }
        removeCaptureAnchor();
        showing = false;
        stopForeground(true);
        stopSelf();
    }

    private void safeRemoveView(View view) {
        if (view == null || windowManager == null) {
            return;
        }
        try {
            windowManager.removeView(view);
        } catch (IllegalArgumentException ignored) {
            // Window was already detached (e.g. by the system tearing down the service).
        }
    }

    private Point getScreenSize() {
        Point point = new Point(1080, 1920);
        if (windowManager == null) {
            return point;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            point.set(windowManager.getCurrentWindowMetrics().getBounds().width(),
                    windowManager.getCurrentWindowMetrics().getBounds().height());
        } else {
            DisplayMetrics dm = new DisplayMetrics();
            windowManager.getDefaultDisplay().getRealMetrics(dm);
            point.set(dm.widthPixels, dm.heightPixels);
        }
        return point;
    }

    private Notification buildNotification() {
        Intent openIntent = new Intent(this, MainActivity.class);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, openIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Intent stopIntent = new Intent(this, OverlayService.class).setAction(ACTION_STOP);
        PendingIntent stopPendingIntent = PendingIntent.getService(this, 0, stopIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Intent mouseIntent = new Intent(this, OverlayService.class).setAction(ACTION_TOGGLE_MOUSE);
        PendingIntent mousePendingIntent = PendingIntent.getService(this, 1, mouseIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        return new NotificationCompat.Builder(this, GomouseApplication.OVERLAY_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(getString(R.string.notification_overlay_title))
                .setContentText(getString(R.string.notification_overlay_text))
                .setContentIntent(contentIntent)
                .addAction(0, getString(R.string.notification_action_toggle_mouse), mousePendingIntent)
                .addAction(0, getString(R.string.notification_action_stop), stopPendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    // --- OverlayButtonView.Listener: direct-touch path into the gesture engine ---

    @Override
    public void onTriggered(InputMapping mapping) {
        withService(service -> {
            switch (mapping.getActionType()) {
                case DOUBLE_TAP:
                    service.onDirectDoubleTap(mapping);
                    break;
                case SWIPE:
                    service.onDirectSwipe(mapping);
                    break;
                case TAP:
                default:
                    service.onDirectTap(mapping);
                    break;
            }
        });
    }

    @Override
    public void onHoldStart(InputMapping mapping) {
        withService(service -> service.onDirectHoldDown(mapping));
    }

    @Override
    public void onHoldEnd(InputMapping mapping) {
        withService(service -> service.onDirectHoldUp(mapping));
    }

    @Override
    public void onJoystickDrag(InputMapping mapping, float dx, float dy) {
        withService(service -> service.onDirectJoystickDrag(mapping, dx, dy));
    }

    @Override
    public void onJoystickRelease(InputMapping mapping) {
        withService(service -> service.onDirectJoystickRelease(mapping));
    }

    @Override
    public void onDpadDirectionDown(InputMapping mapping, String direction) {
        withService(service -> service.onDirectDpadDown(mapping, direction));
    }

    @Override
    public void onDpadDirectionUp(InputMapping mapping, String direction) {
        withService(service -> service.onDirectDpadUp(mapping, direction));
    }

    private interface ServiceAction {
        void run(GomouseAccessibilityService service);
    }

    private void withService(ServiceAction action) {
        GomouseAccessibilityService service = GomouseAccessibilityService.getInstance();
        if (service != null) {
            action.run(service);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        running = false;
        for (View v : mappingWindows.values()) {
            safeRemoveView(v);
        }
        mappingWindows.clear();
        if (toggleHandleView != null) {
            safeRemoveView(toggleHandleView);
            toggleHandleView = null;
        }
        removeCaptureAnchor();
        showing = false;
        super.onDestroy();
    }
}
