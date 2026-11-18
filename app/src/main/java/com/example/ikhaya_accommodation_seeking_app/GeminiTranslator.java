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

    // Active 2026 Gemini model serving pools (strictly no retired 1.5 models)
    private static final String[] CANDIDATE_MODELS = {
            "gemini-2.5-flash-lite",
            "gemini-3-flash-preview",
            "gemini-flash-latest"
    };

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
        translateWithModel(text, targetLang, callback, 0, 1);
    }

    private static void translateWithModel(String text, String targetLang, TranslationCallback callback, int modelIndex, int retriesLeft) {
        if (modelIndex >= CANDIDATE_MODELS.length) {
            callback.onError("Translation failed across all available Gemini models.");
            return;
        }

        String modelName = CANDIDATE_MODELS[modelIndex];
        String apiUrl = "https://generativelanguage.googleapis.com/v1beta/models/" + modelName + ":generateContent";

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
            case "af":
                languageName = "Afrikaans";
                break;
            case "nso":
                languageName = "Sepedi (Northern Sotho)";
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
                    .url(apiUrl)
                    .addHeader("X-goog-api-key", API_KEY)
                    .addHeader("Content-Type", "application/json")
                    .post(body)
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    if (retriesLeft > 0) {
                        mainHandler.postDelayed(() -> translateWithModel(text, targetLang, callback, modelIndex, retriesLeft - 1), 1000);
                    } else if (modelIndex + 1 < CANDIDATE_MODELS.length) {
                        mainHandler.post(() -> translateWithModel(text, targetLang, callback, modelIndex + 1, 1));
                    } else {
                        mainHandler.post(() -> callback.onError("Network error: " + e.getMessage()));
                    }
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String responseBody = response.body() != null ? response.body().string() : "";
                    int code = response.code();

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
                    } else if ((code == 503 || code == 429 || code == 404 || code == 400) && modelIndex + 1 < CANDIDATE_MODELS.length) {
                        // Pause 1200ms on 503/429 rate limit or traffic spike so it clears before trying the next model
                        long delayMs = (code == 503 || code == 429) ? 1200 : 0;
                        mainHandler.postDelayed(() -> translateWithModel(text, targetLang, callback, modelIndex + 1, 1), delayMs);
                    } else {
                        try {
                            JSONObject errJson = new JSONObject(responseBody);
                            String message = errJson.getJSONObject("error").getString("message");
                            mainHandler.post(() -> callback.onError("(" + code + ") " + message));
                        } catch (Exception e) {
                            mainHandler.post(() -> callback.onError("(" + code + ") " + responseBody));
                        }
                    }
                }
            });

        } catch (Exception e) {
            callback.onError("Request error: " + e.getMessage());
        }
    }
}