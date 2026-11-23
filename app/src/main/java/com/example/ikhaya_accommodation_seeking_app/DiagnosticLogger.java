package com.example.ikhaya_accommodation_seeking_app;

import android.app.ApplicationExitInfo;
import android.app.ActivityManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.os.Build;
import android.util.Log;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DiagnosticLogger {
    private static final String TAG = "DiagnosticLogger";
    private static final String PREF_NAME = "iKhayaCrashQueue";
    private static final ExecutorService warningExecutor = Executors.newSingleThreadExecutor();

    public static boolean isNetworkAvailable(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            android.net.Network network = cm.getActiveNetwork();
            if (network == null) return false;
            android.net.NetworkCapabilities caps = cm.getNetworkCapabilities(network);
            return caps != null && (caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) || caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET));
        } else {
            android.net.NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            return activeNetwork != null && activeNetwork.isConnected();
        }
    }

    public static String sanitizeDiagnosticText(String raw) {
        if (raw == null) return null;

        String sanitized = raw;
        // Redact Bearer tokens
        sanitized = sanitized.replaceAll("(?i)Bearer\\s+[a-zA-Z0-9\\-_\\.]+", "Bearer [REDACTED]");
        // Redact raw JWTs
        sanitized = sanitized.replaceAll("eyJ[a-zA-Z0-9\\-_]+\\.[a-zA-Z0-9\\-_]+\\.[a-zA-Z0-9\\-_]+", "[REDACTED_JWT]");
        // Redact SA IDs
        sanitized = sanitized.replaceAll("\\b\\d{13}\\b", "[REDACTED_SA_ID]");

        if (sanitized.length() > 1500) {
            sanitized = sanitized.substring(0, 1500) + "...(truncated)";
        }
        return sanitized;
    }

    public static void captureHistoricalAnrOrNativeCrash(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) {
                try {
                    List<ApplicationExitInfo> exitReasons = am.getHistoricalProcessExitReasons(context.getPackageName(), 0, 5);
                    SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
                    long lastReportedTs = prefs.getLong("LAST_REPORTED_EXIT_TS", 0);

                    for (ApplicationExitInfo info : exitReasons) {
                        if (info.getReason() == ApplicationExitInfo.REASON_ANR || info.getReason() == ApplicationExitInfo.REASON_CRASH_NATIVE) {
                            if (info.getTimestamp() > lastReportedTs) {
                                // We found a new ANR or native crash
                                String reasonStr = (info.getReason() == ApplicationExitInfo.REASON_ANR) ? "ANR" : "Native Crash";
                                String trace = info.getDescription();
                                try (InputStream traceStream = info.getTraceInputStream()) {
                                    if (traceStream != null) {
                                        byte[] bytes = new byte[1024];
                                        int read = traceStream.read(bytes);
                                        if (read > 0) {
                                            trace = new String(bytes, 0, read, StandardCharsets.UTF_8);
                                        }
                                    }
                                } catch (Exception ignored) { }

                                String sanitizedTrace = sanitizeDiagnosticText(trace);
                                prefs.edit()
                                    .putString("PENDING_CRASH_MSG", reasonStr)
                                    .putString("PENDING_CRASH_TRACE", sanitizedTrace)
                                    .putString("PENDING_CRASH_DEVICE", "API " + Build.VERSION.SDK_INT + ", " + Build.MANUFACTURER + " " + Build.MODEL)
                                    .putInt("CRASH_RETRY_COUNT", 0)
                                    .putLong("LAST_REPORTED_EXIT_TS", info.getTimestamp())
                                    .commit();
                                break;
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error checking historical crashes", e);
                }
            }
        }
    }

    public static void flushPendingCrashReports(Context context) {
        try {
            captureHistoricalAnrOrNativeCrash(context);

            if (!isNetworkAvailable(context)) {
                return; // Offline, don't increment retry count
            }

            SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            String msg = prefs.getString("PENDING_CRASH_MSG", null);
            String trace = prefs.getString("PENDING_CRASH_TRACE", null);

            if (msg != null && trace != null) {
                int retryCount = prefs.getInt("CRASH_RETRY_COUNT", 0);
                if (retryCount >= 3) {
                    prefs.edit()
                        .remove("PENDING_CRASH_MSG")
                        .remove("PENDING_CRASH_TRACE")
                        .remove("PENDING_CRASH_DEVICE")
                        .remove("CRASH_RETRY_COUNT")
                        .commit();
                    return;
                }

                prefs.edit().putInt("CRASH_RETRY_COUNT", retryCount + 1).commit();

                String device = prefs.getString("PENDING_CRASH_DEVICE", "Unknown");
                String description = "Device: " + device + "\nTrace:\n" + trace;

                boolean success = SupabaseHelper.postCrashReportSync(context, msg, description);
                if (success) {
                    prefs.edit()
                        .remove("PENDING_CRASH_MSG")
                        .remove("PENDING_CRASH_TRACE")
                        .remove("PENDING_CRASH_DEVICE")
                        .remove("CRASH_RETRY_COUNT")
                        .commit();
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "Failed to flush pending crash reports", t);
        }
    }

    public static void logWarning(Context context, String screenName, String detail) {
        warningExecutor.execute(() -> {
            try {
                if (!isNetworkAvailable(context)) return;
                SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
                long lastTs = prefs.getLong("WARN_TS_" + screenName, 0);
                long now = System.currentTimeMillis();
                if (now - lastTs < 5 * 60 * 1000) return; // 5-minute cooldown
                prefs.edit().putLong("WARN_TS_" + screenName, now).apply();
                String warningText = sanitizeDiagnosticText(detail);
                SupabaseHelper.postDiagnosticReportSync(context, "APP_WARNING", "WARNING: " + screenName, warningText);
            } catch (Exception e) {
                Log.e(TAG, "Failed to log warning", e);
            }
        });
    }
}
