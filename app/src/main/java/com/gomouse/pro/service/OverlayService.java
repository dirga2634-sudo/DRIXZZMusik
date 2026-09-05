package com.gomouse.pro.service;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Point;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;

import com.gomouse.pro.GomouseApplication;
import com.gomouse.pro.R;
import com.gomouse.pro.model.InputMapping;
import com.gomouse.pro.model.Profile;
import com.gomouse.pro.overlay.OverlayButtonView;
import com.gomouse.pro.overlay.OverlayRootView;
import com.gomouse.pro.storage.ProfileRepository;
import com.gomouse.pro.ui.MainActivity;
import com.gomouse.pro.util.PermissionUtils;

/**
 * Owns the {@code TYPE_APPLICATION_OVERLAY} window that draws the active
 * profile's controls on top of other apps. This service never touches the
 * target app's process, memory, or files — it only draws its own window and
 * asks {@link GomouseAccessibilityService} to dispatch official synthetic
 * touch gestures, exactly as if the user had touched the screen.
 */
public class OverlayService extends Service implements OverlayButtonView.Listener,
        OverlayRootView.WindowController {

    public static final String ACTION_START = "com.gomouse.pro.action.START_OVERLAY";
    public static final String ACTION_STOP = "com.gomouse.pro.action.STOP_OVERLAY";
    public static final String ACTION_RELOAD_PROFILE = "com.gomouse.pro.action.RELOAD_PROFILE";
    public static final String ACTION_TOGGLE_MOUSE = "com.gomouse.pro.action.TOGGLE_MOUSE";

    private static final int NOTIFICATION_ID = 4201;

    private WindowManager windowManager;
    private OverlayRootView rootView;
    private WindowManager.LayoutParams layoutParams;
    private ProfileRepository profileRepository;
    private boolean showing = false;

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
                if (rootView != null) {
                    rootView.setMouseActive(!rootView.isMouseActive());
                }
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

        if (!showing) {
            addOverlayWindow();
        }
        reloadAndRebind();
    }

    private void addOverlayWindow() {
        Point screenSize = getScreenSize();
        rootView = new OverlayRootView(this, screenSize);
        rootView.setWindowController(this);

        int type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        layoutParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        layoutParams.gravity = Gravity.TOP | Gravity.START;

        windowManager.addView(rootView, layoutParams);
        showing = true;
    }

    private void reloadAndRebind() {
        GomouseAccessibilityService a11y = GomouseAccessibilityService.getInstance();
        if (a11y != null) {
            a11y.reloadActiveProfile();
        }
        if (rootView == null) {
            return;
        }
        String profileId = profileRepository.getActiveProfileId();
        Profile profile = profileId != null ? profileRepository.load(profileId) : null;
        rootView.bindProfile(profile, this);
    }

    private void stopOverlay() {
        running = false;
        if (showing && rootView != null && windowManager != null) {
            try {
                windowManager.removeView(rootView);
            } catch (IllegalArgumentException ignored) {
                // Window was already detached (e.g. by the system tearing down the service).
            }
        }
        showing = false;
        rootView = null;
        stopForeground(true);
        stopSelf();
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

    // --- OverlayRootView.WindowController ---

    @Override
    public void setFocusableForMouseCapture(boolean focusable) {
        if (windowManager == null || rootView == null || layoutParams == null) {
            return;
        }
        if (focusable) {
            layoutParams.flags &= ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        } else {
            layoutParams.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        }
        windowManager.updateViewLayout(rootView, layoutParams);
    }

    @Override
    public void onMouseActiveChanged(boolean active) {
        // Reserved for future notification/state updates when mouse capture toggles.
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
        if (showing && rootView != null && windowManager != null) {
            try {
                windowManager.removeView(rootView);
            } catch (IllegalArgumentException ignored) {
                // Already detached.
            }
        }
        showing = false;
        super.onDestroy();
    }
}
