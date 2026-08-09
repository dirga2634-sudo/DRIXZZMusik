package com.webtools.optimizer.view;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * View kustom yang gambar percikan petir PROSEDURAL (jalur zigzag acak yang nyebar dari
 * tengah) -- murni Canvas drawing, gak pakai gambar/video eksternal, jadi hasilnya beda
 * dikit tiap kali dipanggil (lebih hidup). Dipicu sekali lewat strike() pas boost 100%.
 */
public class LightningView extends View {

    private final List<Path> bolts = new ArrayList<>();
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint corePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float progress = 0f;
    private final Random random = new Random();
    private ValueAnimator animator;

    public LightningView(Context context, AttributeSet attrs) {
        super(context, attrs);
        glowPaint.setStyle(Paint.Style.STROKE);
        glowPaint.setStrokeWidth(10f);
        glowPaint.setColor(Color.parseColor("#804FC3F7"));
        glowPaint.setStrokeCap(Paint.Cap.ROUND);
        glowPaint.setStrokeJoin(Paint.Join.ROUND);

        corePaint.setStyle(Paint.Style.STROKE);
        corePaint.setStrokeWidth(3.5f);
        corePaint.setColor(Color.WHITE);
        corePaint.setStrokeCap(Paint.Cap.ROUND);
        corePaint.setStrokeJoin(Paint.Join.ROUND);
    }

    /** Picu satu ledakan petir dari tengah view. Aman dipanggil berkali-kali. */
    public void strike() {
        if (getWidth() == 0 || getHeight() == 0) return;

        bolts.clear();
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float maxLen = Math.min(getWidth(), getHeight()) * 0.42f;
        int count = 7 + random.nextInt(3);
        for (int i = 0; i < count; i++) {
            double angle = (Math.PI * 2 / count) * i + (random.nextDouble() - 0.5) * 0.6;
            float length = maxLen * (0.55f + random.nextFloat() * 0.45f);
            bolts.add(generateBoltPath(cx, cy, (float) angle, length));
        }

        if (animator != null) animator.cancel();
        animator = ValueAnimator.ofFloat(0f, 1f, 1f, 0f);
        animator.setDuration(420);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(a -> {
            progress = (float) a.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    private Path generateBoltPath(float cx, float cy, float angle, float length) {
        Path path = new Path();
        path.moveTo(cx, cy);
        int segments = 5 + random.nextInt(3);
        double dx = Math.cos(angle);
        double dy = Math.sin(angle);
        double perpAngle = angle + Math.PI / 2;
        for (int i = 1; i <= segments; i++) {
            float t = i / (float) segments;
            float targetX = (float) (cx + dx * length * t);
            float targetY = (float) (cy + dy * length * t);
            float jitter = (random.nextFloat() - 0.5f) * length * 0.16f * (1 - t * 0.5f);
            targetX += (float) (Math.cos(perpAngle) * jitter);
            targetY += (float) (Math.sin(perpAngle) * jitter);
            path.lineTo(targetX, targetY);
        }
        return path;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (progress <= 0f || bolts.isEmpty()) return;
        int alpha = (int) (255 * progress);
        glowPaint.setAlpha(Math.min(180, alpha));
        corePaint.setAlpha(alpha);
        for (Path bolt : bolts) {
            canvas.drawPath(bolt, glowPaint);
            canvas.drawPath(bolt, corePaint);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (animator != null) animator.cancel();
    }
}
