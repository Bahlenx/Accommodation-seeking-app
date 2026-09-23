package com.example.ikhaya;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class VulavulaTranslator {

    private static final String API_URL = "https://api.lelapa.ai/v1/translate/process";
    private static final String API_KEY = "eyJhbGciOiJFUzI1NiIsImtpZCI6ImVzMjU2LXByb2QtMjAyNjA4IiwidHlwIjoiSldUIn0.eyJ1c2VyX2lkIjoiNmZmYTRjNTAtNWViMS00MjQ1LTkzOGQtZjJhMGQxMzQ1OGEwIiwia2V5Y2hhaW5faWQiOiJlZGFkMmE2ZC1hMWY2LTRlZmItOTg0NS04ZjEwYzliOWYzZmYiLCJwcm9qZWN0X2lkIjoiNTY1ZDRmMmUtZWFiNC00ZTJjLWE2OGUtNGRkYzI3NDdkZmQ3IiwiY3JlYXRlZF9hdCI6IjIwMjYtMDktMjNUMTU6MjY6MDQuMjY2OTM2In0.wWOLW4Nf_l5qYd_MtOeueuULkkwOdalT1J0gSunqLzg6BGsKArwMNGMw1WeDg68QnNjozdGkApoXalbOCJIx7Q";

    public interface TranslationCallback {
        void onSuccess(String translatedText);
        void onError(String errorMessage);
    }

    public static void translate(String text, String targetLang, TranslationCallback callback) {
        String targetFlores;
        switch (targetLang) {
            case "zu":
                targetFlores = "zul_Latn";
                break;
            case "xh":
                targetFlores = "xho_Latn";
                break;
            case "st":
                targetFlores = "sot_Latn";
                break;
            default:
                targetFlores = "eng_Latn";
                break;
        }

        OkHttpClient client = new OkHttpClient();
        Handler mainHandler = new Handler(Looper.getMainLooper());

        try {
            JSONObject json = new JSONObject();
            json.put("input_text", text);
            json.put("source_lang", "eng_Latn");
            json.put("target_lang", targetFlores);

            RequestBody body = RequestBody.create(
                    json.toString(),
                    MediaType.get("application/json; charset=utf-8")
            );

            Request request = new Request.Builder()
                    .url(API_URL)
                    .addHeader("X-CLIENT-TOKEN", API_KEY)
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
                            String result = "";

                            try {
                                JSONObject resObj = new JSONObject(responseBody);
                                if (resObj.has("translated_text")) {
                                    Object val = resObj.get("translated_text");
                                    if (val instanceof JSONArray) {
                                        result = ((JSONArray) val).getString(0);
                                    } else {
                                        result = val.toString();
                                    }
                                } else if (resObj.has("translation")) {
                                    Object val = resObj.get("translation");
                                    if (val instanceof JSONArray) {
                                        result = ((JSONArray) val).getString(0);
                                    } else {
                                        result = val.toString();
                                    }
                                }
                            } catch (Exception ignored) {
                            }

                            if (result.isEmpty()) {
                                java.util.regex.Matcher matcher = java.util.regex.Pattern
                                        .compile("['\"](?:translated_text|translation)['\"]\\s*:\\s*['\"](.*?)['\"]\\s*\\}?", java.util.regex.Pattern.DOTALL)
                                        .matcher(responseBody);

                                if (matcher.find()) {
                                    result = matcher.group(1);
                                } else {
                                    result = responseBody.replaceAll("[\\{\\}'\"\\[\\]]", "").trim();
                                }
                            }

                            result = cleanRepeatedPatterns(result);
                            String finalResult = result;
                            mainHandler.post(() -> callback.onSuccess(finalResult));
                        } catch (Exception e) {
                            mainHandler.post(() -> callback.onError("Parsing error: " + e.getMessage()));
                        }
                    } else {
                        mainHandler.post(() -> callback.onError("(" + response.code() + ") " + responseBody));
                    }
                }
            });

        } catch (Exception e) {
            callback.onError("Request error: " + e.getMessage());
        }
    }

    private static String cleanRepeatedPatterns(String text) {
        if (text == null) return "";
        return text.replaceAll("(?i)(\\b\\w+\\s+\\w+\\b)(?:\\s+\\1){2,}", "$1")
                .replaceAll("(?i)(\\b\\w+\\b)(?:\\s+\\1){3,}", "$1");
    }
}