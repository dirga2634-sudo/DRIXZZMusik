package com.gomouse.pro.model;

/**
 * What a mapped control does on the screen when its bound physical input
 * fires (or when the user touches it directly — every control also always
 * works by direct touch, regardless of any physical binding).
 */
public enum ActionType {
    /** A single quick tap at the mapping's position. */
    TAP,
    /** Touch stays down for exactly as long as the physical input is held. */
    HOLD,
    /** Two quick taps in succession. */
    DOUBLE_TAP,
    /** A single directional swipe gesture from the mapping's position. */
    SWIPE,
    /** An analog virtual joystick / thumbstick. */
    JOYSTICK,
    /** A 4- or 8-direction D-pad. */
    DPAD
}
