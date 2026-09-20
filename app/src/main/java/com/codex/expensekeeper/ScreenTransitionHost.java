package com.codex.expensekeeper;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.provider.Settings;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;

/** Keeps an opaque screen beneath the incoming page until its transition finishes. */
final class ScreenTransitionHost extends FrameLayout {
    private static final long DURATION_MS = 180;
    private View current, outgoing;
    private ValueAnimator animator;
    private ViewTreeObserver.OnPreDrawListener pendingStart;
    private ViewTreeObserver pendingObserver;
    private boolean transitioning;

    ScreenTransitionHost(Context context) {
        super(context);
        setClipChildren(true);
        setClipToPadding(true);
    }

    void show(View next, boolean animate, boolean backwards, boolean rtl, int background) {
        // Rapid Back presses or a refresh settle the previous destination first.
        finishTransition();
        setBackgroundColor(background);
        View previous = current;
        current = next;
        boolean motionEnabled = Settings.Global.getFloat(getContext().getContentResolver(),
                Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f;
        if (previous == null || !animate || !motionEnabled || !isLaidOut() || !isShown()) {
            removeAllViews();
            addView(next, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
            return;
        }

        outgoing = previous;
        outgoing.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        outgoing.clearFocus();
        transitioning = true;
        float direction = (backwards ? -1f : 1f) * (rtl ? -1f : 1f);
        float distance = 18f * getResources().getDisplayMetrics().density * direction;
        next.setAlpha(0f);
        next.setTranslationX(distance);
        addView(next, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        // Wait for measurement/layout, keeping the old page fully visible meanwhile.
        pendingObserver = getViewTreeObserver();
        pendingStart = () -> {
            clearPendingStart();
            ValueAnimator transition = ValueAnimator.ofFloat(0f, 1f);
            animator = transition;
            transition.setDuration(DURATION_MS);
            transition.setInterpolator(new DecelerateInterpolator(1.5f));
            transition.addUpdateListener(value -> {
                float progress = (float) value.getAnimatedValue();
                next.setAlpha(progress);
                next.setTranslationX(distance * (1f - progress));
                // The outgoing page stays opaque: no fade-through to the window.
                previous.setTranslationX(-distance * .35f * progress);
            });
            transition.addListener(new AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(Animator animation) {
                    if (animator == animation) finishTransition();
                }
            });
            transition.start();
            return true;
        };
        pendingObserver.addOnPreDrawListener(pendingStart);
    }

    void finishTransition() {
        clearPendingStart();
        if (animator != null) {
            ValueAnimator running = animator;
            animator = null;
            running.cancel();
        }
        if (current != null) {
            current.setAlpha(1f);
            current.setTranslationX(0f);
        }
        if (outgoing != null) {
            outgoing.setTranslationX(0f);
            removeView(outgoing);
            outgoing = null;
        }
        transitioning = false;
    }

    private void clearPendingStart() {
        if (pendingStart != null && pendingObserver != null && pendingObserver.isAlive()) {
            pendingObserver.removeOnPreDrawListener(pendingStart);
        }
        pendingStart = null;
        pendingObserver = null;
    }

    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        // Avoid duplicate taps and clicks on the outgoing page during the short overlap.
        return transitioning || super.dispatchTouchEvent(event);
    }

    @Override protected void onDetachedFromWindow() {
        finishTransition();
        super.onDetachedFromWindow();
    }
}
