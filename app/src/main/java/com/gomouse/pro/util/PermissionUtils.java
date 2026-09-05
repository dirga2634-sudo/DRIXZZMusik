package com.gomouse.pro.util;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityManager;

import com.gomouse.pro.service.GomouseAccessibilityService;

import java.util.List;

/**
 * All permission checks Gomouse Pro needs go through official Android APIs
 * only: {@link Settings#canDrawOverlays} for the overlay permission and the
 * {@link AccessibilityManager} for accessibility-service status. There is no
 * root-only or hidden-API path — on devices/OEM skins that block or kill the
 * service in the background, the only remedy is the one Android exposes:
 * asking the user to allow it (see SettingsActivity's battery-optimization
 * shortcut).
 */
public final class PermissionUtils {

    private PermissionUtils() {
    }

    public static boolean canDrawOverlays(Context context) {
        return Settings.canDrawOverlays(context);
    }

    public static Intent overlayPermissionIntent(Context context) {
        return new Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + context.getPackageName()));
    }

    public static boolean isAccessibilityServiceEnabled(Context context) {
        AccessibilityManager am =
                (AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (am == null) {
            return false;
        }
        List<AccessibilityServiceInfo> enabledServices =
                am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC);
        String targetId = context.getPackageName() + "/" + GomouseAccessibilityService.class.getCanonicalName();
        for (AccessibilityServiceInfo info : enabledServices) {
            String id = info.getId();
            if (!TextUtils.isEmpty(id) && id.equals(targetId)) {
                return true;
            }
        }
        return false;
    }

    public static Intent accessibilitySettingsIntent() {
        return new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
    }

    public static Intent appNotificationSettingsIntent(Context context) {
        Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
        intent.putExtra(Settings.EXTRA_APP_PACKAGE, context.getPackageName());
        return intent;
    }

    public static Intent ignoreBatteryOptimizationsSettingsIntent() {
        // Takes the user to the general "ignore battery optimizations" list
        // rather than requesting REQUEST_IGNORE_BATTERY_OPTIMIZATIONS directly,
        // since that permission is heavily restricted by Play Policy — the
        // user can grant it themselves from this screen instead.
        return new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
    }
}
