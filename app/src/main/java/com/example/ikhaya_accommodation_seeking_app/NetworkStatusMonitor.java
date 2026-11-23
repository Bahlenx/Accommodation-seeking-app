package com.example.ikhaya_accommodation_seeking_app;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;

import java.net.HttpURLConnection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NetworkStatusMonitor {

    private final Activity activity;
    private final View statusDot;
    private final TextView statusLabel;
    private final ConnectivityManager connectivityManager;
    private final ExecutorService probeExecutor = Executors.newSingleThreadExecutor();
    private ConnectivityManager.NetworkCallback networkCallback;

    public NetworkStatusMonitor(Activity activity, View statusDot, TextView statusLabel) {
        this.activity = activity;
        this.statusDot = statusDot;
        this.statusLabel = statusLabel;
        this.connectivityManager = (ConnectivityManager) activity.getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    public void start() {
        if (connectivityManager == null) return;
        evaluateCurrentNetwork();

        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                evaluateCurrentNetwork();
            }

            @Override
            public void onCapabilitiesChanged(@NonNull Network network, @NonNull NetworkCapabilities caps) {
                evaluateCapabilities(caps);
            }

            @Override
            public void onLost(@NonNull Network network) {
                applyUiState("OFFLINE");
            }
        };

        try {
            connectivityManager.registerDefaultNetworkCallback(networkCallback);
        } catch (Exception e) {
            applyUiState("UNSTABLE");
        }
    }

    public void stop() {
        if (connectivityManager != null && networkCallback != null) {
            try {
                connectivityManager.unregisterNetworkCallback(networkCallback);
            } catch (Exception ignored) {}
            networkCallback = null;
        }
    }

    public void cleanup() {
        stop();
        probeExecutor.shutdownNow();
    }

    private void evaluateCurrentNetwork() {
        if (connectivityManager == null) return;
        Network active = connectivityManager.getActiveNetwork();
        if (active == null) {
            applyUiState("OFFLINE");
            return;
        }
        evaluateCapabilities(connectivityManager.getNetworkCapabilities(active));
    }

    private void evaluateCapabilities(NetworkCapabilities caps) {
        if (caps == null || !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            applyUiState("OFFLINE");
            return;
        }
        boolean validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        int downKbps = caps.getLinkDownstreamBandwidthKbps();
        if (!validated || (downKbps > 0 && downKbps < 1500)) {
            applyUiState("UNSTABLE");
            return;
        }

        if (probeExecutor.isShutdown()) return;
        probeExecutor.execute(() -> {
            long start = System.currentTimeMillis();
            HttpURLConnection conn = null;
            try {
                conn = SupabaseHelper.createConnection(
                        "/rest/v1/listings?select=id&limit=1", "HEAD", SupabaseHelper.getAccessToken(activity));
                conn.setConnectTimeout(3000);
                conn.setReadTimeout(3000);
                int code = conn.getResponseCode();
                long elapsed = System.currentTimeMillis() - start;
                if (code >= 200 && code < 500) {
                    applyUiState(elapsed > 1500 ? "UNSTABLE" : "STABLE");
                } else {
                    applyUiState("UNSTABLE");
                }
            } catch (Exception e) {
                applyUiState("UNSTABLE");
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    private void applyUiState(String state) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        activity.runOnUiThread(() -> {
            if (activity.isFinishing() || activity.isDestroyed()) return;
            int color;
            String text;
            if ("STABLE".equals(state)) {
                color = Color.parseColor("#15803D");
                text = "Online";
            } else if ("UNSTABLE".equals(state)) {
                color = Color.parseColor("#CA8A04");
                text = "Weak Signal";
            } else {
                color = Color.parseColor("#DC2626");
                text = "Offline";
            }
            if (statusLabel != null) {
                statusLabel.setText(text);
                statusLabel.setTextColor(color);
            }
            if (statusDot != null) {
                statusDot.setBackgroundTintList(ColorStateList.valueOf(color));
            }
        });
    }
}
