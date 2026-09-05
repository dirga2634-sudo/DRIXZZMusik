package com.gomouse.pro.editor;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import com.google.gson.Gson;
import com.gomouse.pro.model.ActionType;
import com.gomouse.pro.model.InputMapping;
import com.gomouse.pro.util.UndoRedoManager;

import java.util.ArrayList;
import java.util.List;

/**
 * The full-screen editor canvas: renders every mapping in the current
 * profile at its real on-screen position/size and lets the user add, select,
 * drag, and resize them. All coordinates the user manipulates here are
 * pixel coordinates converted to/from the model's normalized [0,1] storage
 * format, so a layout built on one screen renders correctly on another.
 */
public class EditorCanvasView extends View {

    public interface SelectionListener {
        void onSelectionChanged(InputMapping selected);

        void onMappingsChanged();
    }

    private static final float HANDLE_RADIUS_DP = 12f;
    private static final float MIN_SIZE_NORMALIZED = 0.045f;

    private final float density;
    private final Gson gson = new Gson();
    private final UndoRedoManager undoRedoManager = new UndoRedoManager();

    private List<InputMapping> mappings = new ArrayList<>();
    private InputMapping selected;
    private SelectionListener listener;

    private boolean gridSnapEnabled = true;
    private float gridSize = 0.02f;

    private enum DragMode {NONE, MOVE, RESIZE}

    private DragMode dragMode = DragMode.NONE;
    private int activeResizeHandle = -1; // 0=TL,1=TR,2=BL,3=BR
    private float dragStartTouchX, dragStartTouchY;
    private float dragStartMappingX, dragStartMappingY, dragStartMappingW, dragStartMappingH;

    private final Paint gridPaint = new Paint();
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selectedStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint handlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint lockedOverlayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public EditorCanvasView(Context context) {
        super(context);
        density = context.getResources().getDisplayMetrics().density;

        gridPaint.setColor(Color.parseColor("#222A3A55"));
        gridPaint.setStrokeWidth(1f);

        fillPaint.setColor(Color.parseColor("#401A2A3A"));
        fillPaint.setStyle(Paint.Style.FILL);

        strokePaint.setColor(Color.parseColor("#802979FF"));
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(2f * density);

        selectedStrokePaint.setColor(Color.parseColor("#FF6D00"));
        selectedStrokePaint.setStyle(Paint.Style.STROKE);
        selectedStrokePaint.setStrokeWidth(3f * density);

        handlePaint.setColor(Color.parseColor("#FF6D00"));
        handlePaint.setStyle(Paint.Style.FILL);

        textPaint.setColor(Color.parseColor("#E8ECFF"));
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(12f * density);

        lockedOverlayPaint.setColor(Color.parseColor("#33000000"));

        setFocusableInTouchMode(true);
    }

    public void setSelectionListener(SelectionListener listener) {
        this.listener = listener;
    }

    public void setGridSnap(boolean enabled, float size) {
        this.gridSnapEnabled = enabled;
        this.gridSize = size;
        invalidate();
    }

    public void setMappings(List<InputMapping> newMappings) {
        this.mappings = newMappings != null ? newMappings : new ArrayList<>();
        this.selected = null;
        undoRedoManager.clear();
        invalidate();
    }

    public List<InputMapping> getMappings() {
        return mappings;
    }

    public InputMapping getSelected() {
        return selected;
    }

    // --- Editing operations (each pushes undo state first) ---

    public void addMapping(ActionType type) {
        pushUndo();
        float cx = 0.5f + (mappings.size() % 5) * 0.01f;
        float cy = 0.45f + (mappings.size() % 5) * 0.03f;
        InputMapping mapping = InputMapping.createDefault(type, cx, cy);
        mappings.add(mapping);
        select(mapping);
        notifyChanged();
    }

    public void deleteSelected() {
        if (selected == null) {
            return;
        }
        pushUndo();
        mappings.remove(selected);
        select(null);
        notifyChanged();
    }

    public void duplicateSelected() {
        if (selected == null) {
            return;
        }
        pushUndo();
        InputMapping copy = selected.copy();
        mappings.add(copy);
        select(copy);
        notifyChanged();
    }

    public void resetLayout() {
        pushUndo();
        mappings.clear();
        select(null);
        notifyChanged();
    }

    public void toggleSelectedLock() {
        if (selected == null) {
            return;
        }
        pushUndo();
        selected.setLocked(!selected.isLocked());
        notifyChanged();
    }

    public void toggleSelectedVisibility() {
        if (selected == null) {
            return;
        }
        pushUndo();
        selected.setVisible(!selected.isVisible());
        notifyChanged();
    }

    /** Call once BEFORE editing the selected mapping's properties through a dialog. */
    public void pushUndo() {
        undoRedoManager.push(UndoRedoManager.deepCopyList(gson, mappings));
    }

    /** Call AFTER a dialog finishes editing the selected mapping's properties in place. */
    public void commitPropertyChange() {
        notifyChanged();
    }

    public boolean canUndo() {
        return undoRedoManager.canUndo();
    }

    public boolean canRedo() {
        return undoRedoManager.canRedo();
    }

    public void undo() {
        List<InputMapping> restored = undoRedoManager.undo(UndoRedoManager.deepCopyList(gson, mappings));
        if (restored != null) {
            mappings = restored;
            select(null);
            notifyChanged();
        }
    }

    public void redo() {
        List<InputMapping> restored = undoRedoManager.redo(UndoRedoManager.deepCopyList(gson, mappings));
        if (restored != null) {
            mappings = restored;
            select(null);
            notifyChanged();
        }
    }

    private void select(InputMapping mapping) {
        selected = mapping;
        if (listener != null) {
            listener.onSelectionChanged(selected);
        }
        invalidate();
    }

    private void notifyChanged() {
        if (listener != null) {
            listener.onMappingsChanged();
        }
        invalidate();
    }

    // --- Rendering ---

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (gridSnapEnabled) {
            drawGrid(canvas);
        }
        for (InputMapping m : mappings) {
            drawMapping(canvas, m, m == selected);
        }
    }

    private void drawGrid(Canvas canvas) {
        float stepX = gridSize * getWidth();
        float stepY = gridSize * getHeight();
        if (stepX < 4 || stepY < 4) {
            return;
        }
        for (float x = 0; x < getWidth(); x += stepX) {
            canvas.drawLine(x, 0, x, getHeight(), gridPaint);
        }
        for (float y = 0; y < getHeight(); y += stepY) {
            canvas.drawLine(0, y, getWidth(), y, gridPaint);
        }
    }

    private RectF boundsPx(InputMapping m) {
        float w = m.getWidth() * getWidth();
        float h = m.getHeight() * getHeight();
        float cx = m.getX() * getWidth();
        float cy = m.getY() * getHeight();
        return new RectF(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f);
    }

    private void drawMapping(Canvas canvas, InputMapping m, boolean isSelected) {
        RectF bounds = boundsPx(m);
        boolean isRound = m.getActionType() == ActionType.JOYSTICK || m.getActionType() == ActionType.DPAD;

        if (isRound) {
            float r = Math.min(bounds.width(), bounds.height()) / 2f;
            canvas.drawCircle(bounds.centerX(), bounds.centerY(), r, fillPaint);
            canvas.drawCircle(bounds.centerX(), bounds.centerY(), r, isSelected ? selectedStrokePaint : strokePaint);
        } else {
            canvas.drawRoundRect(bounds, 10f * density, 10f * density, fillPaint);
            canvas.drawRoundRect(bounds, 10f * density, 10f * density, isSelected ? selectedStrokePaint : strokePaint);
        }

        String label = m.getLabel() == null || m.getLabel().isEmpty()
                ? m.getActionType().name() : m.getLabel();
        float textY = bounds.centerY() - ((textPaint.descent() + textPaint.ascent()) / 2f);
        canvas.drawText(label, bounds.centerX(), textY, textPaint);

        if (!m.isVisible() || m.isLocked()) {
            canvas.drawRoundRect(bounds, 10f * density, 10f * density, lockedOverlayPaint);
        }

        if (isSelected && !m.isLocked()) {
            drawHandle(canvas, bounds.left, bounds.top);
            drawHandle(canvas, bounds.right, bounds.top);
            drawHandle(canvas, bounds.left, bounds.bottom);
            drawHandle(canvas, bounds.right, bounds.bottom);
        }
    }

    private void drawHandle(Canvas canvas, float x, float y) {
        canvas.drawCircle(x, y, HANDLE_RADIUS_DP * density / 2f, handlePaint);
    }

    // --- Touch handling: select / drag / resize ---

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float touchX = event.getX();
        float touchY = event.getY();

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                return handleDown(touchX, touchY);
            case MotionEvent.ACTION_MOVE:
                return handleMove(touchX, touchY);
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                dragMode = DragMode.NONE;
                activeResizeHandle = -1;
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    private boolean handleDown(float touchX, float touchY) {
        if (selected != null && !selected.isLocked()) {
            int handle = hitTestHandle(selected, touchX, touchY);
            if (handle >= 0) {
                pushUndo();
                dragMode = DragMode.RESIZE;
                activeResizeHandle = handle;
                beginDragTracking(selected, touchX, touchY);
                return true;
            }
        }
        InputMapping hit = hitTestMapping(touchX, touchY);
        select(hit);
        if (hit != null && !hit.isLocked()) {
            pushUndo();
            dragMode = DragMode.MOVE;
            beginDragTracking(hit, touchX, touchY);
        } else {
            dragMode = DragMode.NONE;
        }
        return true;
    }

    private void beginDragTracking(InputMapping mapping, float touchX, float touchY) {
        dragStartTouchX = touchX;
        dragStartTouchY = touchY;
        dragStartMappingX = mapping.getX();
        dragStartMappingY = mapping.getY();
        dragStartMappingW = mapping.getWidth();
        dragStartMappingH = mapping.getHeight();
    }

    private boolean handleMove(float touchX, float touchY) {
        if (selected == null || dragMode == DragMode.NONE) {
            return true;
        }
        float dxNorm = (touchX - dragStartTouchX) / getWidth();
        float dyNorm = (touchY - dragStartTouchY) / getHeight();

        if (dragMode == DragMode.MOVE) {
            float newX = clamp01(dragStartMappingX + dxNorm);
            float newY = clamp01(dragStartMappingY + dyNorm);
            if (gridSnapEnabled) {
                newX = snap(newX);
                newY = snap(newY);
            }
            selected.setX(newX);
            selected.setY(newY);
        } else if (dragMode == DragMode.RESIZE) {
            applyResize(dxNorm, dyNorm);
        }
        invalidate();
        return true;
    }

    private void applyResize(float dxNorm, float dyNorm) {
        // Handles: 0=TL,1=TR,2=BL,3=BR — dragging a corner changes size symmetrically
        // about the opposite corner and keeps the mapping's center consistent with it.
        float signX = (activeResizeHandle == 1 || activeResizeHandle == 3) ? 1f : -1f;
        float signY = (activeResizeHandle == 2 || activeResizeHandle == 3) ? 1f : -1f;

        float newW = Math.max(MIN_SIZE_NORMALIZED, dragStartMappingW + signX * dxNorm * 2f);
        float newH = Math.max(MIN_SIZE_NORMALIZED, dragStartMappingH + signY * dyNorm * 2f);

        if (gridSnapEnabled) {
            newW = snap(newW);
            newH = snap(newH);
        }
        selected.setWidth(newW);
        selected.setHeight(newH);
        // Center stays put; only size changes. (Keeps resize predictable/simple for touch.)
    }

    private float snap(float value) {
        return Math.round(value / gridSize) * gridSize;
    }

    private float clamp01(float v) {
        return Math.max(0.02f, Math.min(0.98f, v));
    }

    private int hitTestHandle(InputMapping m, float x, float y) {
        RectF b = boundsPx(m);
        float r = HANDLE_RADIUS_DP * density;
        if (within(x, y, b.left, b.top, r)) return 0;
        if (within(x, y, b.right, b.top, r)) return 1;
        if (within(x, y, b.left, b.bottom, r)) return 2;
        if (within(x, y, b.right, b.bottom, r)) return 3;
        return -1;
    }

    private boolean within(float x, float y, float cx, float cy, float r) {
        float dx = x - cx;
        float dy = y - cy;
        return dx * dx + dy * dy <= r * r;
    }

    private InputMapping hitTestMapping(float x, float y) {
        // Iterate topmost-drawn (last in list) first, so overlapping controls
        // select the one visually on top.
        for (int i = mappings.size() - 1; i >= 0; i--) {
            InputMapping m = mappings.get(i);
            RectF b = boundsPx(m);
            if (b.contains(x, y)) {
                return m;
            }
        }
        return null;
    }
}
