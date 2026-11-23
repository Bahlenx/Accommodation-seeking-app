package com.example.ikhaya_accommodation_seeking_app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;
import android.util.LruCache;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.InputStream;
import java.util.concurrent.ConcurrentHashMap;

public final class SessionCacheManager {
    private static final String TAG = "SessionCacheManager";
    private static final String PREF_CACHE = "iKhayaCache";
    private static final int MAX_IMAGE_BYTES = 8 * 1024 * 1024; // 8 MB OOM safety cap

    private static final LruCache<String, Bitmap> bitmapCache;
    private static final ConcurrentHashMap<String, CachedJson> jsonCache = new ConcurrentHashMap<>();

    static {
        int maxMemKb = (int) (Runtime.getRuntime().maxMemory() / 1024L);
        bitmapCache = new LruCache<String, Bitmap>(Math.max(4 * 1024, maxMemKb / 8)) {
            @Override
            protected int sizeOf(String key, Bitmap bmp) {
                return bmp.getByteCount() / 1024;
            }
        };
    }

    private static class CachedJson {
        final String json;
        final long expiresAt;

        CachedJson(String json, long ttlMs) {
            this.json = json;
            this.expiresAt = System.currentTimeMillis() + ttlMs;
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }

    public static Bitmap getBitmap(String key) {
        if (key == null) return null;
        return bitmapCache.get(key);
    }

    public static void putBitmap(String key, Bitmap bmp) {
        if (key != null && bmp != null) {
            bitmapCache.put(key, bmp);
        }
    }

    public static Bitmap decodeSampledBitmap(InputStream in, int reqWidth, int reqHeight) {
        try {
            java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
            int nRead;
            byte[] data = new byte[4096];
            int totalBytes = 0;
            while ((nRead = in.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, nRead);
                totalBytes += nRead;
                if (totalBytes > MAX_IMAGE_BYTES) {
                    Log.w(TAG, "Image exceeds MAX_IMAGE_BYTES cap, aborting decode.");
                    return null; // Stop reading to prevent OOM
                }
            }
            buffer.flush();
            byte[] imageBytes = buffer.toByteArray();

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length, options);

            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);

            options.inJustDecodeBounds = false;
            options.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length, options);
        } catch (Exception e) {
            Log.e(TAG, "Failed to decode sampled bitmap", e);
            return null;
        }
    }

    private static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }

    public static void putJson(String key, String json, long ttlMs) {
        if (key != null && json != null) {
            jsonCache.put(key, new CachedJson(json, ttlMs));
        }
    }

    public static String getJson(String key) {
        if (key == null) return null;
        CachedJson cached = jsonCache.get(key);
        if (cached != null) {
            if (!cached.isExpired()) {
                return cached.json;
            } else {
                jsonCache.remove(key);
            }
        }
        return null;
    }

    public static JSONArray getJsonArraySafe(String key) {
        String raw = getJson(key);
        if (raw == null) return null;
        try {
            return new JSONArray(raw);
        } catch (Exception corrupt) {
            Log.w(TAG, "Evicting corrupted cache entry: " + key, corrupt);
            invalidateJson(key);
            return null;
        }
    }

    public static JSONObject getJsonObjectSafe(String key) {
        String raw = getJson(key);
        if (raw == null) return null;
        try {
            return new JSONObject(raw);
        } catch (Exception corrupt) {
            Log.w(TAG, "Evicting corrupted cache entry: " + key, corrupt);
            invalidateJson(key);
            return null;
        }
    }

    public static void invalidateJson(String key) {
        if (key != null) jsonCache.remove(key);
    }

    public static void invalidateAllDataCaches() {
        jsonCache.clear();
    }

    public static void trimBitmapsToHalf() {
        bitmapCache.trimToSize(Math.max(1024, bitmapCache.maxSize() / 2));
    }

    public static void clearMemoryCaches() {
        bitmapCache.evictAll();
        jsonCache.clear();
    }

    public static void purgeDiskCachesBackground(Context context, boolean isOnline) {
        clearMemoryCaches();
        if (context == null) return;
        Context app = context.getApplicationContext();

        // Only wipe cached listings when online; NEVER touch iKhayaSession, iKhayaCrashQueue, or iKhayaUserPrefs
        if (isOnline) {
            try {
                app.getSharedPreferences(PREF_CACHE, Context.MODE_PRIVATE).edit().clear().apply();
            } catch (Exception ignored) {}
        }
        deleteChildrenSafely(app.getCacheDir());
        deleteChildrenSafely(app.getExternalCacheDir());
        try {
            File osmCache = new File(app.getFilesDir().getParent(), "osmdroid"); // fallback path check, normally accessible via org.osmdroid
            if (osmCache.exists()) deleteChildrenSafely(osmCache);
        } catch (Throwable ignored) {}
    }

    private static void deleteChildrenSafely(File dir) {
        if (dir != null && dir.isDirectory()) {
            File[] children = dir.listFiles();
            if (children != null) {
                for (File child : children) {
                    if (child.isDirectory()) {
                        deleteChildrenSafely(child);
                    }
                    child.delete();
                }
            }
        }
    }
}