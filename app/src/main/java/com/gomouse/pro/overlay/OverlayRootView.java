package com.gomouse.pro.overlay;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.Region;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;

import com.gomouse.pro.model.ActionType;
import com.gomouse.pro.model.InputMapping;
import com.gomouse.pro.model.InputSourceType;
import com.gomouse.pro.model.Profile;
import com.gomouse.pro.service.GomouseAccessibilityService;
import com.gomouse.pro.util.InputCodeUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Hosts every {@link OverlayButtonView} for the active profile inside the
 * floating overlay window, and owns the two things that make the overlay
 * usable at the same time as the game underneath:
 *
 * 1. A touchable-region restriction (official
 *    {@link ViewTreeObserver.OnComputeInternalInsetsListener} API) so a
 *    finger touch in empty space is never intercepted — only touches that
 *    land on a rendered control are.
 * 2. An optional "Mouse Active" mode, toggled from the small handle this
 *    view always keeps on screen, which temporarily takes window focus and
 *    official Pointer Capture so mouse movement/buttons can drive a
 *    mouse-look joystick or MOUSE_BUTTON-bound controls from anywhere on
 *    screen — see {@link WindowController}. Outside that mode, a mouse
 *    click still works exactly like a touch wherever the cursor is over a
 *    control, with no special handling needed.
 */
public class OverlayRootView extends FrameLayout {

    public interface WindowController {
        void setFocusableForMouseCapture(boolean focusable);

        void onMouseActiveChanged(boolean active);
    }

    private final Point screenSize;
    private final List<OverlayButtonView> buttonViews = new ArrayList<>();
    private WindowController windowController;
    private boolean mouseActive = false;
    private int lastMouseButtonState = 0;

    private final View toggleHandle;
    private final Paint handlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;

    public OverlayRootView(Context context, Point screenSize) {
        super(context);
        this.screenSize = screenSize;
        this.density = context.getResources().getDisplayMetrics().density;
        setWillNotDraw(false);

        handlePaint.setColor(Color.parseColor("#CC2979FF"));

        toggleHandle = new View(context) {
            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                float r = Math.min(getWidth(), getHeight()) / 2f;
                canvas.drawCircle(getWidth() / 2f, getHeight() / 2f, r, handlePaint);
            }
        };
        int handleSizePx = Math.round(28 * density);
        LayoutParams handleParams = new LayoutParams(handleSizePx, handleSizePx);
        handleParams.gravity = Gravity.TOP | Gravity.START;
        handleParams.leftMargin = Math.round(4 * density);
        handleParams.topMargin = Math.round(80 * density);
        toggleHandle.setOnClickListener(v -> setMouseActive(!mouseActive));
        addView(toggleHandle, handleParams);

        setOnComputeInternalInsetsListener(insetsInfo -> {
            if (mouseActive) {
                insetsInfo.setTouchableInsets(ViewTreeObserver.InternalInsetsInfo.TOUCHABLE_INSETS_FRAME);
                return;
            }
            insetsInfo.setTouchableInsets(ViewTreeObserver.InternalInsetsInfo.TOUCHABLE_INSETS_REGION);
            Region region = new Region();
            for (OverlayButtonView bv : buttonViews) {
                region.union(viewBoundsOnScreen(bv));
            }
            region.union(viewBoundsOnScreen(toggleHandle));
            insetsInfo.touchableRegion.set(region);
        });
    }

    private Rect viewBoundsOnScreen(View v) {
        return new Rect(v.getLeft(), v.getTop(), v.getRight(), v.getBottom());
    }

    public void setWindowController(WindowController controller) {
        this.windowController = controller;
    }

    public void bindProfile(Profile profile, OverlayButtonView.Listener listener) {
        for (OverlayButtonView bv : buttonViews) {
            removeView(bv);
        }
        buttonViews.clear();
        if (profile == null) {
            return;
        }
        for (InputMapping mapping : profile.getMappings()) {
            if (!mapping.isVisible()) {
                continue;
            }
            OverlayButtonView bv = new OverlayButtonView(getContext(), mapping);
            bv.setListener(listener);
            bv.setAlpha(profile.getOpacity());
            int w = Math.round(mapping.getWidth() * screenSize.x);
            int h = Math.round(mapping.getHeight() * screenSize.y);
            int left = Math.round(mapping.getX() * screenSize.x - w / 2f);
            int top = Math.round(mapping.getY() * screenSize.y - h / 2f);
            LayoutParams lp = new LayoutParams(w, h);
            lp.leftMargin = left;
            lp.topMargin = top;
            lp.gravity = Gravity.TOP | Gravity.START;
            addView(bv, lp);
            buttonViews.add(bv);
        }
        // requestLayout() causes the framework to re-run its layout pass,
        // which is also what re-invokes setOnComputeInternalInsetsListener's
        // callback — so the touchable region picks up each button's real
        // bounds automatically once they're placed, with no need to
        // manually trigger insets computation ourselves.
        requestLayout();
    }

    public void setMouseActive(boolean active) {
        if (mouseActive == active) {
            return;
        }
        mouseActive = active;
        toggleHandle.setSelected(active);
        handlePaint.setColor(active ? Color.parseColor("#CC00E676") : Color.parseColor("#CC2979FF"));
        toggleHandle.invalidate();
        if (windowController != null) {
            windowController.setFocusableForMouseCapture(active);
            windowController.onMouseActiveChanged(active);
        }
        if (active) {
            requestFocus();
            requestPointerCapture();
        } else {
            releasePointerCapture();
        }
        // Re-triggers setOnComputeInternalInsetsListener's callback so the
        // touchable region switches between "just the controls" and "the
        // whole window" immediately when mouse mode toggles.
        requestLayout();
    }

    public boolean isMouseActive() {
        return mouseActive;
    }

    /**
     * Fires with RELATIVE motion once this view holds official Pointer Capture.
     * This overrides {@link View#onCapturedPointerEvent(MotionEvent)} directly
     * (OverlayRootView IS the captured view, via requestPointerCapture() in
     * {@link #setMouseActive}) — no separate OnCapturedPointerListener/
     * setOnCapturedPointerListener wiring is needed for that case.
     */
    @Override
    public boolean onCapturedPointerEvent(MotionEvent event) {
        GomouseAccessibilityService service = GomouseAccessibilityService.getInstance();
        Profile profile = service != null ? service.getActiveProfile() : null;
        if (service == null || profile == null) {
            return false;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
            float dx = event.getX();
            float dy = event.getY();
            for (InputMapping mapping : profile.getMappings()) {
                if (mapping.getActionType() == ActionType.JOYSTICK && mapping.isMouseLookEnabled()
                        && mapping.isVisible()) {
                    service.onMouseLookDelta(mapping, dx, dy);
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

    public void updateOpacity(float opacity) {
        for (OverlayButtonView bv : buttonViews) {
            bv.setAlpha(opacity);
        }
    }
}
