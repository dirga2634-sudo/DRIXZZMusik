package com.gomouse.pro.overlay;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import com.gomouse.pro.model.ActionType;
import com.gomouse.pro.model.InputMapping;
import com.gomouse.pro.service.GomouseAccessibilityService;

/**
 * Renders a single {@link InputMapping} inside the floating overlay and
 * forwards direct touches on it to {@link GomouseAccessibilityService},
 * which is what actually turns the touch into a synthetic gesture on the
 * app underneath (this view's own window is separate from the target app's
 * window, so a touch landing here does nothing on its own).
 */
public class OverlayButtonView extends View {

    private final InputMapping mapping;
    private final float density;

    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint activeFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint knobPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private static final int COLOR_FILL = Color.parseColor("#331A2A3A");
    private static final int COLOR_STROKE = Color.parseColor("#2979FF");
    private static final int COLOR_ACTIVE = Color.parseColor("#662979FF");
    private static final int COLOR_KNOB = Color.parseColor("#2979FF");
    private static final int COLOR_TEXT = Color.parseColor("#E8ECFF");

    private boolean pressed = false;
    private float knobOffsetX = 0f; // -1..1 fraction of radius, JOYSTICK only
    private float knobOffsetY = 0f;
    private int activeDpadMask = 0; // bit 0=up,1=down,2=left,3=right

    private Listener listener;

    public interface Listener {
        void onTriggered(InputMapping mapping);

        void onHoldStart(InputMapping mapping);

        void onHoldEnd(InputMapping mapping);

        void onJoystickDrag(InputMapping mapping, float dx, float dy);

        void onJoystickRelease(InputMapping mapping);

        void onDpadDirectionDown(InputMapping mapping, String direction);

        void onDpadDirectionUp(InputMapping mapping, String direction);
    }

    public OverlayButtonView(Context context, InputMapping mapping) {
        super(context);
        this.mapping = mapping;
        this.density = context.getResources().getDisplayMetrics().density;

        fillPaint.setColor(COLOR_FILL);
        fillPaint.setStyle(Paint.Style.FILL);

        activeFillPaint.setColor(COLOR_ACTIVE);
        activeFillPaint.setStyle(Paint.Style.FILL);

        strokePaint.setColor(COLOR_STROKE);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(2f * density);

        knobPaint.setColor(COLOR_KNOB);
        knobPaint.setStyle(Paint.Style.FILL);

        dimPaint.setColor(Color.parseColor("#1AFFFFFF"));
        dimPaint.setStyle(Paint.Style.FILL);

        textPaint.setColor(COLOR_TEXT);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(11f * density);

        setAlpha(1f);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public InputMapping getMapping() {
        return mapping;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        float cx = w / 2f;
        float cy = h / 2f;

        switch (mapping.getActionType()) {
            case JOYSTICK:
                drawJoystick(canvas, cx, cy, Math.min(w, h) / 2f);
                break;
            case DPAD:
                drawDpad(canvas, cx, cy, Math.min(w, h) / 2f);
                break;
            default:
                drawButton(canvas, w, h);
                break;
        }
    }

    private void drawButton(Canvas canvas, float w, float h) {
        RectF rect = new RectF(strokePaint.getStrokeWidth(), strokePaint.getStrokeWidth(),
                w - strokePaint.getStrokeWidth(), h - strokePaint.getStrokeWidth());
        float radius = 10f * density;
        canvas.drawRoundRect(rect, radius, radius, pressed ? activeFillPaint : fillPaint);
        canvas.drawRoundRect(rect, radius, radius, strokePaint);
        if (mapping.getLabel() != null && !mapping.getLabel().isEmpty()) {
            float textY = (h / 2f) - ((textPaint.descent() + textPaint.ascent()) / 2f);
            canvas.drawText(mapping.getLabel(), w / 2f, textY, textPaint);
        }
    }

    private void drawJoystick(Canvas canvas, float cx, float cy, float radius) {
        float outerR = radius - strokePaint.getStrokeWidth();
        canvas.drawCircle(cx, cy, outerR, fillPaint);
        canvas.drawCircle(cx, cy, outerR, strokePaint);
        float knobR = outerR * 0.42f;
        float knobCx = cx + knobOffsetX * (outerR - knobR);
        float knobCy = cy + knobOffsetY * (outerR - knobR);
        canvas.drawCircle(knobCx, knobCy, knobR, pressed ? activeFillPaint : dimPaint);
        canvas.drawCircle(knobCx, knobCy, knobR, strokePaint);
    }

    private void drawDpad(Canvas canvas, float cx, float cy, float radius) {
        float outerR = radius - strokePaint.getStrokeWidth();
        canvas.drawCircle(cx, cy, outerR, fillPaint);
        canvas.drawCircle(cx, cy, outerR, strokePaint);
        float armLen = outerR * 0.72f;
        float armW = outerR * 0.34f;
        drawDpadArm(canvas, cx, cy, 0, -armLen, armW, (activeDpadMask & 1) != 0);   // up
        drawDpadArm(canvas, cx, cy, 0, armLen, armW, (activeDpadMask & 2) != 0);    // down
        drawDpadArm(canvas, cx, cy, -armLen, 0, armW, (activeDpadMask & 4) != 0);   // left
        drawDpadArm(canvas, cx, cy, armLen, 0, armW, (activeDpadMask & 8) != 0);    // right
    }

    private void drawDpadArm(Canvas canvas, float cx, float cy, float dx, float dy, float armW, boolean active) {
        RectF rect;
        if (dx != 0) {
            float left = dx > 0 ? cx : cx + dx;
            float right = dx > 0 ? cx + dx : cx;
            rect = new RectF(left, cy - armW / 2f, right, cy + armW / 2f);
        } else {
            float top = dy > 0 ? cy : cy + dy;
            float bottom = dy > 0 ? cy + dy : cy;
            rect = new RectF(cx - armW / 2f, top, cx + armW / 2f, bottom);
        }
        canvas.drawRoundRect(rect, 6f * density, 6f * density, active ? activeFillPaint : dimPaint);
        canvas.drawRoundRect(rect, 6f * density, 6f * density, strokePaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (listener == null) {
            return false;
        }
        switch (mapping.getActionType()) {
            case JOYSTICK:
                return handleJoystickTouch(event);
            case DPAD:
                return handleDpadTouch(event);
            default:
                return handleButtonTouch(event);
        }
    }

    private boolean handleButtonTouch(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                pressed = true;
                invalidate();
                if (mapping.getActionType() == ActionType.HOLD) {
                    listener.onHoldStart(mapping);
                } else {
                    listener.onTriggered(mapping);
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                pressed = false;
                invalidate();
                if (mapping.getActionType() == ActionType.HOLD) {
                    listener.onHoldEnd(mapping);
                }
                return true;
            default:
                return true;
        }
    }

    private boolean handleJoystickTouch(MotionEvent event) {
        float radius = Math.min(getWidth(), getHeight()) / 2f;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                pressed = true;
                float rawDx = (event.getX() - getWidth() / 2f) / radius;
                float rawDy = (event.getY() - getHeight() / 2f) / radius;
                float len = (float) Math.sqrt(rawDx * rawDx + rawDy * rawDy);
                if (len > 1f) {
                    rawDx /= len;
                    rawDy /= len;
                }
                knobOffsetX = rawDx;
                knobOffsetY = rawDy;
                invalidate();
                listener.onJoystickDrag(mapping, rawDx, rawDy);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                pressed = false;
                knobOffsetX = 0f;
                knobOffsetY = 0f;
                invalidate();
                listener.onJoystickRelease(mapping);
                return true;
            default:
                return true;
        }
    }

    private boolean handleDpadTouch(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE: {
                int newMask = dpadMaskForPoint(event.getX(), event.getY());
                updateDpadMask(newMask);
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                updateDpadMask(0);
                return true;
            default:
                return true;
        }
    }

    private int dpadMaskForPoint(float x, float y) {
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float dx = x - cx;
        float dy = y - cy;
        float deadZone = Math.min(getWidth(), getHeight()) * 0.12f;
        if (Math.abs(dx) < deadZone && Math.abs(dy) < deadZone) {
            return 0;
        }
        int mask = 0;
        if (mapping.isEightDirectional()) {
            double angle = Math.toDegrees(Math.atan2(dy, dx)); // -180..180, 0 = right
            if (angle > -22.5 && angle <= 22.5) mask = 8;                 // right
            else if (angle > 22.5 && angle <= 67.5) mask = 8 | 2;         // down-right
            else if (angle > 67.5 && angle <= 112.5) mask = 2;            // down
            else if (angle > 112.5 && angle <= 157.5) mask = 2 | 4;       // down-left
            else if (angle > 157.5 || angle <= -157.5) mask = 4;          // left
            else if (angle > -157.5 && angle <= -112.5) mask = 4 | 1;     // up-left
            else if (angle > -112.5 && angle <= -67.5) mask = 1;          // up
            else mask = 1 | 8;                                           // up-right
        } else {
            if (Math.abs(dx) > Math.abs(dy)) {
                mask = dx > 0 ? 8 : 4;
            } else {
                mask = dy > 0 ? 2 : 1;
            }
        }
        return mask;
    }

    private void updateDpadMask(int newMask) {
        if (newMask == activeDpadMask) {
            return;
        }
        String[] names = {"up", "down", "left", "right"};
        for (int i = 0; i < 4; i++) {
            int bit = 1 << i;
            boolean wasOn = (activeDpadMask & bit) != 0;
            boolean isOn = (newMask & bit) != 0;
            if (isOn && !wasOn) {
                listener.onDpadDirectionDown(mapping, names[i]);
            } else if (wasOn && !isOn) {
                listener.onDpadDirectionUp(mapping, names[i]);
            }
        }
        activeDpadMask = newMask;
        pressed = newMask != 0;
        invalidate();
    }
}
