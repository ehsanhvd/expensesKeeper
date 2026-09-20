package com.codex.expensekeeper;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.provider.Settings;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/** Decorative, scalable instrument. Its reading is also presented as accessible text. */
final class PaceGaugeView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float target;
    private final boolean learning;
    private float position;
    private ValueAnimator animator;

    PaceGaugeView(Context context, SpendingPace pace) {
        super(context);
        target = pace.needle;
        learning = pace.state == SpendingPace.LEARNING;
        position = target;
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    void animateIn() {
        stopAnimation();
        if (Settings.Global.getFloat(getContext().getContentResolver(),
                Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f || learning) {
            position = target;
            invalidate();
            return;
        }
        animator = ValueAnimator.ofFloat(0f, target);
        animator.setDuration(1200);
        animator.setInterpolator(new DecelerateInterpolator(2f));
        animator.addUpdateListener(value -> {
            position = (float) value.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    void stopAnimation() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
    }

    @Override protected void onDetachedFromWindow() {
        stopAnimation();
        super.onDetachedFromWindow();
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec);
        setMeasuredDimension(width, resolveSize(Math.round(width * .64f), heightSpec));
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.save();
        canvas.scale(getWidth() / 360f, getHeight() / 230f);
        paint.setStyle(Paint.Style.FILL);
        // Quiet star field and dotted orbital track, with no continuous animation.
        for (int row = 0; row < 7; row++) {
            for (int col = 0; col < 12; col++) {
                paint.setColor(Color.argb(22, 205, 222, 255));
                canvas.drawCircle(15 + col * 30, 12 + row * 30, 1.3f, paint);
            }
        }
        paint.setColor(0x887FE9C4);
        star(canvas, 29, 41, 5);
        star(canvas, 329, 72, 4);
        paint.setColor(0x88D4C8FF);
        star(canvas, 298, 23, 6);
        for (int i = 0; i <= 40; i++) {
            double angle = Math.toRadians(180 + i * 4.5);
            paint.setColor(0x446C87BD);
            canvas.drawCircle(180 + (float) Math.cos(angle) * 164,
                    193 + (float) Math.sin(angle) * 164, 1.5f, paint);
        }
        RectF arc = new RectF(37, 50, 323, 336);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(19);
        int[] colors = {0xFF7FE9C4, 0xFFFFD481, 0xFFFF9BAD};
        for (int i = 0; i < 3; i++) {
            paint.setColor(learning ? 0xFF9DAED1 : colors[i]);
            canvas.drawArc(arc, 180 + i * 61.5f, 57, false, paint);
        }
        for (int i = 0; i <= 24; i++) {
            double angle = Math.toRadians(180 + i * 7.5);
            paint.setStrokeWidth(i % 4 == 0 ? 2.5f : 1.3f);
            paint.setColor(i % 4 == 0 ? 0xCCDDE6FF : 0x557B94BB);
            float inner = i % 4 == 0 ? 112 : 118;
            canvas.drawLine(180 + (float) Math.cos(angle) * inner, 193 + (float) Math.sin(angle) * inner,
                    180 + (float) Math.cos(angle) * 126, 193 + (float) Math.sin(angle) * 126, paint);
        }
        double angle = Math.toRadians(180 + position * 180);
        float tipX = 180 + (float) Math.cos(angle) * 102;
        float tipY = 193 + (float) Math.sin(angle) * 102;
        paint.setStrokeWidth(15);
        paint.setColor(0x224FFFFF);
        canvas.drawLine(180, 193, tipX, tipY, paint);
        paint.setStrokeWidth(5);
        paint.setColor(Color.WHITE);
        canvas.drawLine(180, 193, tipX, tipY, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF314568);
        canvas.drawCircle(180, 193, 20, paint);
        paint.setColor(Color.WHITE);
        canvas.drawCircle(180, 193, 11, paint);
        paint.setColor(learning ? 0xFF9DAED1 : 0xFF7FE9C4);
        canvas.drawCircle(180, 193, 4, paint);
        canvas.restore();
    }

    private void star(Canvas canvas, float x, float y, float radius) {
        paint.setStrokeWidth(1.5f);
        canvas.drawLine(x - radius, y, x + radius, y, paint);
        canvas.drawLine(x, y - radius, x, y + radius, paint);
    }
}
