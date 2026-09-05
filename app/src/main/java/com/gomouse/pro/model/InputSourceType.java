package com.gomouse.pro.model;

/**
 * The physical input class a mapping (or a single direction of a joystick /
 * D-pad) is bound to.
 *
 * KEYBOARD_KEY and GAMEPAD_BUTTON both arrive at runtime as a standard
 * Android {@link android.view.KeyEvent} — Android does not distinguish a
 * physical keyboard key from a gamepad button at the KeyEvent level, they
 * are simply different keyCodes on devices with different
 * {@link android.view.InputDevice} sources. Gomouse Pro keeps them as
 * separate enum values purely so the editor UI can present "Keyboard" and
 * "Controller" as distinct, clearly-labelled categories to the user.
 */
public enum InputSourceType {
    KEYBOARD_KEY,
    GAMEPAD_BUTTON,
    MOUSE_BUTTON,
    MOUSE_MOVEMENT
}
