package com.gomouse.pro.model;

import java.io.Serializable;

/**
 * A single physical-input binding for one direction of a JOYSTICK or DPAD
 * mapping (e.g. the "up" direction bound to the W key). {@link #sourceType}
 * is {@code null} when that direction has no physical binding — the
 * direction still works, it just requires a direct touch/drag rather than a
 * key press.
 */
public class DirectionBinding implements Serializable {

    private InputSourceType sourceType;
    private int inputCode;

    public DirectionBinding() {
        this.sourceType = null;
        this.inputCode = 0;
    }

    public DirectionBinding(InputSourceType sourceType, int inputCode) {
        this.sourceType = sourceType;
        this.inputCode = inputCode;
    }

    public boolean isBound() {
        return sourceType != null;
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

    public void clear() {
        this.sourceType = null;
        this.inputCode = 0;
    }
}
