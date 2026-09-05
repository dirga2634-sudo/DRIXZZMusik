package com.gomouse.pro.util;

import android.view.KeyEvent;

/**
 * Human-readable labels for keycodes and mouse-button constants, used by the
 * editor's "press a key to bind" dialog and by the mapping list.
 */
public final class InputCodeUtils {

    private InputCodeUtils() {
    }

    /** Mouse buttons as reported by {@link android.view.MotionEvent#getButtonState()}. */
    public static final int MOUSE_BUTTON_PRIMARY = 1;   // MotionEvent.BUTTON_PRIMARY
    public static final int MOUSE_BUTTON_SECONDARY = 2; // MotionEvent.BUTTON_SECONDARY
    public static final int MOUSE_BUTTON_TERTIARY = 4;  // MotionEvent.BUTTON_TERTIARY
    public static final int MOUSE_BUTTON_BACK = 8;       // MotionEvent.BUTTON_BACK
    public static final int MOUSE_BUTTON_FORWARD = 16;   // MotionEvent.BUTTON_FORWARD

    public static String describeKeyCode(int keyCode) {
        if (keyCode == 0) {
            return "—";
        }
        String label = KeyEvent.keyCodeToString(keyCode);
        // KeyEvent.keyCodeToString() returns e.g. "KEYCODE_W"; trim the prefix for a compact UI.
        if (label != null && label.startsWith("KEYCODE_")) {
            return label.substring("KEYCODE_".length()).replace('_', ' ');
        }
        return label != null ? label : ("Key " + keyCode);
    }

    public static String describeMouseButton(int buttonConstant) {
        if (buttonConstant == MOUSE_BUTTON_PRIMARY) {
            return "Left Click";
        } else if (buttonConstant == MOUSE_BUTTON_SECONDARY) {
            return "Right Click";
        } else if (buttonConstant == MOUSE_BUTTON_TERTIARY) {
            return "Middle Click";
        } else if (buttonConstant == MOUSE_BUTTON_BACK) {
            return "Mouse Back";
        } else if (buttonConstant == MOUSE_BUTTON_FORWARD) {
            return "Mouse Forward";
        }
        return "Mouse Button";
    }

    /**
     * True for keycodes that are safe to intercept/remap. Gomouse Pro never
     * consumes system-critical keys (Home, Recents, power, volume, etc.) so
     * the device always stays fully controllable regardless of what is mapped.
     */
    public static boolean isRemappable(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_HOME:
            case KeyEvent.KEYCODE_POWER:
            case KeyEvent.KEYCODE_APP_SWITCH:
            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_VOLUME_DOWN:
            case KeyEvent.KEYCODE_VOLUME_MUTE:
            case KeyEvent.KEYCODE_CAMERA:
            case KeyEvent.KEYCODE_CALL:
            case KeyEvent.KEYCODE_ENDCALL:
                return false;
            default:
                return true;
        }
    }

    public static boolean isFromGamepad(KeyEvent event) {
        int sources = event.getDevice() != null ? event.getDevice().getSources() : event.getSource();
        return (sources & android.view.InputDevice.SOURCE_GAMEPAD) == android.view.InputDevice.SOURCE_GAMEPAD
                || (sources & android.view.InputDevice.SOURCE_JOYSTICK) == android.view.InputDevice.SOURCE_JOYSTICK;
    }
}
