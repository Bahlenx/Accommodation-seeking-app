package com.example.ikhaya_accommodation_seeking_app;

import android.app.ApplicationExitInfo;
import android.app.ActivityManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.util.Log;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class DiagnosticLogger {
    private static final String TAG = "DiagnosticLogger";
    private static final String PREF_NAME = "iKhayaCrashQueue";

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
                                try {
                                    InputStream traceStream = info.getTraceInputStream();
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

            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) {
                NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
                if (activeNetwork == null || !activeNetwork.isConnected()) {
                    return; // Offline, don't increment retry count
                }
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
        // Rate-limited background warning reporter
        new Thread(() -> {
            try {
                String warningText = sanitizeDiagnosticText(detail);
                SupabaseHelper.postCrashReportSync(context, "WARNING: " + screenName, warningText);
            } catch (Exception e) {
                Log.e(TAG, "Failed to log warning", e);
            }
        }).start();
    }
}
