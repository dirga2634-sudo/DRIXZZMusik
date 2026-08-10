package com.webtools.optimizer.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

/**
 * Crosshair yang digambar dinamis lewat Canvas -- warna/bentuk/ukuran bisa diubah lewat
 * setStyle(), dipakai bareng di layar preview (CrosshairCustomizeActivity) dan overlay
 * beneran (CrosshairService), jadi keduanya selalu identik.
 */
public class CrosshairView extends View {

    public static final int SHAPE_CROSS_CIRCLE = 0;
    public static final int SHAPE_CROSS = 1;
    public static final int SHAPE_DOT = 2;
    public static final int SHAPE_CIRCLE = 3;

    private int color = Color.WHITE;
    private int shape = SHAPE_CROSS_CIRCLE;
    private float sizeDp = 44f;

    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public CrosshairView(Context context, AttributeSet attrs) {
        super(context, attrs);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        dotPaint.setStyle(Paint.Style.FILL);
        applyStyle();
    }

    public void setStyle(int newColor, int newShape, float newSizeDp) {
        this.color = newColor;
        this.shape = newShape;
        this.sizeDp = newSizeDp;
        applyStyle();
        requestLayout();
        invalidate();
    }

    private void applyStyle() {
        linePaint.setColor(color);
        dotPaint.setColor(color);
        float density = getResources().getDisplayMetrics().density;
        linePaint.setStrokeWidth(Math.max(2f, sizeDp * density * 0.045f));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        float density = getResources().getDisplayMetrics().density;
        int size = (int) (sizeDp * density);
        setMeasuredDimension(size, size);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float r = Math.min(getWidth(), getHeight()) / 2f;
        if (r <= 0) return;
        float gap = r * 0.28f;

        switch (shape) {
            case SHAPE_DOT:
                canvas.drawCircle(cx, cy, Math.max(2f, r * 0.18f), dotPaint);
                break;
            case SHAPE_CIRCLE:
                canvas.drawCircle(cx, cy, r * 0.82f, linePaint);
                break;
            case SHAPE_CROSS:
                canvas.drawLine(cx, cy - r, cx, cy - gap, linePaint);
                canvas.drawLine(cx, cy + gap, cx, cy + r, linePaint);
                canvas.drawLine(cx - r, cy, cx - gap, cy, linePaint);
                canvas.drawLine(cx + gap, cy, cx + r, cy, linePaint);
                break;
            case SHAPE_CROSS_CIRCLE:
            default:
                float ringR = r * 0.75f;
                float innerStart = ringR + gap * 0.35f;
                canvas.drawCircle(cx, cy, ringR, linePaint);
                canvas.drawLine(cx, cy - r, cx, cy - innerStart, linePaint);
                canvas.drawLine(cx, cy + innerStart, cx, cy + r, linePaint);
                canvas.drawLine(cx - r, cy, cx - innerStart, cy, linePaint);
                canvas.drawLine(cx + innerStart, cy, cx + r, cy, linePaint);
                canvas.drawCircle(cx, cy, Math.max(1.5f, r * 0.07f), dotPaint);
                break;
        }
    }
}
