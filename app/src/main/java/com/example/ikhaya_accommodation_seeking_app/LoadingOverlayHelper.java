package com.example.ikhaya_accommodation_seeking_app;

import android.os.Handler;
import android.os.Looper;
import android.view.View;

import java.util.concurrent.atomic.AtomicInteger;

public class LoadingOverlayHelper {

    private static final long SHOW_DELAY_MS = 150L;

    private volatile View overlay;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final AtomicInteger pendingTasks = new AtomicInteger(0);
    private long defaultTimeoutMs;
    private Runnable showRunnable;
    private Runnable timeoutRunnable;

    public LoadingOverlayHelper(View overlay, long defaultTimeoutMs) {
        this.overlay = overlay;
        this.defaultTimeoutMs = defaultTimeoutMs;
    }

    public void beginTask() {
        beginTask(defaultTimeoutMs);
    }

    public void beginTask(long timeoutMs) {
        if (overlay == null) return;
        if (pendingTasks.incrementAndGet() == 1) {
            cancelCallbacks();

            showRunnable = () -> {
                if (overlay != null && pendingTasks.get() > 0) {
                    overlay.setVisibility(View.VISIBLE);
                }
            };

            timeoutRunnable = () -> {
                pendingTasks.set(0);
                hideOverlay();
            };

            mainHandler.postDelayed(showRunnable, SHOW_DELAY_MS);
        }
        if (timeoutRunnable != null) {
            mainHandler.removeCallbacks(timeoutRunnable);
            mainHandler.postDelayed(timeoutRunnable, timeoutMs);
        }
    }

    public void endTask() {
        if (pendingTasks.decrementAndGet() <= 0) {
            pendingTasks.set(0);
            cancelCallbacks();
            hideOverlay();
        }
    }

    private void hideOverlay() {
        mainHandler.post(() -> {
            if (overlay != null) {
                overlay.setVisibility(View.GONE);
            }
        });
    }

    private void cancelCallbacks() {
        if (showRunnable != null) {
            mainHandler.removeCallbacks(showRunnable);
        }
        if (timeoutRunnable != null) {
            mainHandler.removeCallbacks(timeoutRunnable);
        }
    }

    public void cleanup() {
        cancelCallbacks();
        pendingTasks.set(0);
        overlay = null;
    }
}
