package com.example.ikhaya_accommodation_seeking_app;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

public class IkhayaApplication extends Application {
    private static final String PREF_NAME = "iKhayaCrashQueue";

    @Override
    public void onCreate() {
        super.onCreate();

        final Thread.UncaughtExceptionHandler defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                try {
                    String trace = Log.getStackTraceString(e);
                    String sanitizedTrace = DiagnosticLogger.sanitizeDiagnosticText(trace);
                    String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();

                    SharedPreferences prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
                    prefs.edit()
                        .putString("PENDING_CRASH_MSG", msg)
                        .putString("PENDING_CRASH_TRACE", sanitizedTrace)
                        .putString("PENDING_CRASH_DEVICE", "API " + Build.VERSION.SDK_INT + ", " + Build.MANUFACTURER + " " + Build.MODEL)
                        .putInt("CRASH_RETRY_COUNT", 0)
                        .commit();
                } catch (Throwable ignored) { }

                if (defaultHandler != null) {
                    defaultHandler.uncaughtException(t, e);
                }
            }
        });
    }
}
