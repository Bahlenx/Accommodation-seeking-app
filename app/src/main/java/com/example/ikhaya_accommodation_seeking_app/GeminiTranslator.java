package com.example.ikhaya_accommodation_seeking_app;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class GeminiTranslator {

    private static final String API_KEY = BuildConfig.GEMINI_API_KEY;

    // Direct, pinned model endpoint to avoid deprecated alias 404s
    // Use the current active 3.5 production model
    private static final String API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent";

    // Reusable client with socket timeouts to prevent timeout drops
    private static final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(25, TimeUnit.SECONDS)
            .readTimeout(35, TimeUnit.SECONDS)
            .writeTimeout(25, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build();

    public interface TranslationCallback {
        void onSuccess(String translatedText);
        void onError(String errorMessage);
    }

    public static void translate(String text, String targetLang, TranslationCallback callback) {
        translateWithRetry(text, targetLang, callback, 2);
    }

    private static void translateWithRetry(String text, String targetLang, TranslationCallback callback, int retriesLeft) {
        String languageName;
        switch (targetLang) {
            case "zu":
                languageName = "isiZulu";
                break;
            case "xh":
                languageName = "isiXhosa";
                break;
            case "st":
                languageName = "Southern Sotho (Sesotho)";
                break;
            default:
                languageName = "English";
                break;
        }

        String prompt = "Translate this South African rental accommodation description into natural, everyday " + languageName + ". "
                + "Ensure terms like rent, water, electricity, and transport sound natural and grammatically correct. "
                + "Do not include quotes, markdown formatting, or preamble. Return ONLY the translated text:\n\n"
                + text;

        Handler mainHandler = new Handler(Looper.getMainLooper());

        try {
            JSONObject textPart = new JSONObject();
            textPart.put("text", prompt);

            JSONArray partsArray = new JSONArray();
            partsArray.put(textPart);

            JSONObject contentObj = new JSONObject();
            contentObj.put("parts", partsArray);

            JSONArray contentsArray = new JSONArray();
            contentsArray.put(contentObj);

            JSONObject payload = new JSONObject();
            payload.put("contents", contentsArray);

            RequestBody body = RequestBody.create(
                    payload.toString(),
                    MediaType.get("application/json; charset=utf-8")
            );

            Request request = new Request.Builder()
                    .url(API_URL)
                    .addHeader("X-goog-api-key", API_KEY)
                    .addHeader("Content-Type", "application/json")
                    .post(body)
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    mainHandler.post(() -> callback.onError("Network error: " + e.getMessage()));
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String responseBody = response.body() != null ? response.body().string() : "";

                    if (response.isSuccessful()) {
                        try {
                            JSONObject json = new JSONObject(responseBody);
                            JSONArray candidates = json.getJSONArray("candidates");
                            JSONObject firstCandidate = candidates.getJSONObject(0);
                            JSONObject content = firstCandidate.getJSONObject("content");
                            JSONArray parts = content.getJSONArray("parts");
                            String translatedResult = parts.getJSONObject(0).getString("text").trim();

                            mainHandler.post(() -> callback.onSuccess(translatedResult));
                        } catch (Exception e) {
                            mainHandler.post(() -> callback.onError("Parsing error: " + e.getMessage()));
                        }
                    } else if (response.code() == 503 && retriesLeft > 0) {
                        mainHandler.postDelayed(() -> translateWithRetry(text, targetLang, callback, retriesLeft - 1), 2000);
                    } else {
                        try {
                            JSONObject errJson = new JSONObject(responseBody);
                            String message = errJson.getJSONObject("error").getString("message");
                            mainHandler.post(() -> callback.onError("(" + response.code() + ") " + message));
                        } catch (Exception e) {
                            mainHandler.post(() -> callback.onError("(" + response.code() + ") " + responseBody));
                        }
                    }
                }
            });

        } catch (Exception e) {
            callback.onError("Request error: " + e.getMessage());
        }
    }
}