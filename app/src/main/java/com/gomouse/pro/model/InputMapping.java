package com.gomouse.pro.model;

import java.io.Serializable;
import java.util.UUID;

/**
 * A single mapped control placed on the editor canvas / overlay: a button,
 * joystick, or D-pad, with its screen position/size and the physical input
 * (keyboard key, gamepad button, or mouse button) that triggers it.
 *
 * Positions and sizes are stored normalized to [0, 1] relative to the
 * screen's usable width/height, so a profile created on one device's screen
 * resolution still lays out correctly on another.
 */
public class InputMapping implements Serializable {

    private String id;
    private String label;
    private ActionType actionType;

    // Normalized center position and size, both in [0, 1].
    private float x;
    private float y;
    private float width;
    private float height;

    // --- Simple binding, used when actionType is TAP / HOLD / DOUBLE_TAP / SWIPE ---
    private InputSourceType sourceType;
    private int inputCode;

    // --- SWIPE-specific: direction + distance, normalized to screen size, and duration ---
    private float swipeDx;
    private float swipeDy;
    private long swipeDurationMs;

    // --- JOYSTICK / DPAD-specific: independent bindings per direction so any
    // subset of directions can be bound to physical input; unbound
    // directions still work by direct touch/drag. ---
    private DirectionBinding up;
    private DirectionBinding down;
    private DirectionBinding left;
    private DirectionBinding right;

    /** JOYSTICK only: also steer this stick from relative mouse movement while active. */
    private boolean mouseLookEnabled;

    /** DPAD only: 8-direction (diagonals) vs. 4-direction. */
    private boolean eightDirectional;

    private boolean locked;
    private boolean visible;

    /** No-arg constructor required by Gson. */
    public InputMapping() {
        this.id = UUID.randomUUID().toString();
        this.label = "";
        this.actionType = ActionType.TAP;
        this.width = 0.09f;
        this.height = 0.09f;
        this.sourceType = null;
        this.inputCode = 0;
        this.swipeDx = 0.1f;
        this.swipeDy = 0f;
        this.swipeDurationMs = 120L;
        this.up = new DirectionBinding();
        this.down = new DirectionBinding();
        this.left = new DirectionBinding();
        this.right = new DirectionBinding();
        this.mouseLookEnabled = false;
        this.eightDirectional = false;
        this.locked = false;
        this.visible = true;
    }

    public static InputMapping createDefault(ActionType type, float x, float y) {
        InputMapping mapping = new InputMapping();
        mapping.actionType = type;
        mapping.x = x;
        mapping.y = y;
        switch (type) {
            case JOYSTICK:
                mapping.width = 0.22f;
                mapping.height = 0.22f;
                mapping.label = "Joystick";
                break;
            case DPAD:
                mapping.width = 0.24f;
                mapping.height = 0.24f;
                mapping.label = "D-Pad";
                break;
            case SWIPE:
                mapping.label = "Swipe";
                break;
            case HOLD:
                mapping.label = "Hold";
                break;
            case DOUBLE_TAP:
                mapping.label = "Double Tap";
                break;
            case TAP:
            default:
                mapping.label = "Button";
                break;
        }
        return mapping;
    }

    public InputMapping copy() {
        InputMapping copy = new InputMapping();
        copy.id = UUID.randomUUID().toString();
        copy.label = this.label;
        copy.actionType = this.actionType;
        copy.x = this.x + 0.03f;
        copy.y = this.y + 0.03f;
        copy.width = this.width;
        copy.height = this.height;
        copy.sourceType = this.sourceType;
        copy.inputCode = this.inputCode;
        copy.swipeDx = this.swipeDx;
        copy.swipeDy = this.swipeDy;
        copy.swipeDurationMs = this.swipeDurationMs;
        copy.up = new DirectionBinding(this.up.getSourceType(), this.up.getInputCode());
        copy.down = new DirectionBinding(this.down.getSourceType(), this.down.getInputCode());
        copy.left = new DirectionBinding(this.left.getSourceType(), this.left.getInputCode());
        copy.right = new DirectionBinding(this.right.getSourceType(), this.right.getInputCode());
        copy.mouseLookEnabled = this.mouseLookEnabled;
        copy.eightDirectional = this.eightDirectional;
        copy.locked = false;
        copy.visible = this.visible;
        return copy;
    }

    // --- Getters / setters ---

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public ActionType getActionType() {
        return actionType;
    }

    public void setActionType(ActionType actionType) {
        this.actionType = actionType;
    }

    public float getX() {
        return x;
    }

    public void setX(float x) {
        this.x = x;
    }

    public float getY() {
        return y;
    }

    public void setY(float y) {
        this.y = y;
    }

    public float getWidth() {
        return width;
    }

    public void setWidth(float width) {
        this.width = width;
    }

    public float getHeight() {
        return height;
    }

    public void setHeight(float height) {
        this.height = height;
    }

    public InputSourceType getSourceType() {
        return sourceType;
    }

    public void setSourceType(InputSourceType sourceType) {
        this.sourceType = sourceType;
    }

    public int getInputCode() {
        return inputCode;
    }

    public void setInputCode(int inputCode) {
        this.inputCode = inputCode;
    }

    public float getSwipeDx() {
        return swipeDx;
    }

    public void setSwipeDx(float swipeDx) {
        this.swipeDx = swipeDx;
    }

    public float getSwipeDy() {
        return swipeDy;
    }

    public void setSwipeDy(float swipeDy) {
        this.swipeDy = swipeDy;
    }

    public long getSwipeDurationMs() {
        return swipeDurationMs;
    }

    public void setSwipeDurationMs(long swipeDurationMs) {
        this.swipeDurationMs = swipeDurationMs;
    }

    public DirectionBinding getUp() {
        return up;
    }

    public DirectionBinding getDown() {
        return down;
    }

    public DirectionBinding getLeft() {
        return left;
    }

    public DirectionBinding getRight() {
        return right;
    }

    public boolean isMouseLookEnabled() {
        return mouseLookEnabled;
    }

    public void setMouseLookEnabled(boolean mouseLookEnabled) {
        this.mouseLookEnabled = mouseLookEnabled;
    }

    public boolean isEightDirectional() {
        return eightDirectional;
    }

    public void setEightDirectional(boolean eightDirectional) {
        this.eightDirectional = eightDirectional;
    }

    public boolean isLocked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    /** True for JOYSTICK/DPAD, which use per-direction bindings instead of a single binding. */
    public boolean usesDirectionBindings() {
        return actionType == ActionType.JOYSTICK || actionType == ActionType.DPAD;
    }
}
