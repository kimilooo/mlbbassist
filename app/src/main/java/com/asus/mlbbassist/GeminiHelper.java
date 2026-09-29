package com.asus.mlbbassist;

import android.graphics.Bitmap;
import android.util.Log;

import com.google.ai.client.generativeai.GenerativeModel;
import com.google.ai.client.generativeai.java.GenerativeModelFutures;
import com.google.ai.client.generativeai.type.Content;
import com.google.ai.client.generativeai.type.GenerateContentResponse;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class GeminiHelper {
    private final KeyManager keyManager;
    private final Executor executor = Executors.newSingleThreadExecutor();

    public interface Callback {
        void onSuccess(String result, long duration);

        void onError(String error, boolean isRateLimit);
    }

    public GeminiHelper(KeyManager keyManager) {
        this.keyManager = keyManager;
    }

    public void analyzeImage(Bitmap bitmap, Callback callback) {
        long startTime = System.currentTimeMillis();
        performAnalysis(bitmap, callback, 0, startTime);
    }

    public void validateKey(Callback callback) {
        String key = keyManager.getActiveKey();
        String modelName = keyManager.getModel();

        if (key.isEmpty()) {
            callback.onError("No API key set.", false);
            return;
        }

        GenerativeModel gm = new GenerativeModel(modelName, key);
        GenerativeModelFutures model = GenerativeModelFutures.from(gm);

        Content content = new Content.Builder()
                .addText("Reply with OK")
                .build();

        ListenableFuture<GenerateContentResponse> response = model.generateContent(content);

        Futures.addCallback(response, new FutureCallback<GenerateContentResponse>() {
            @Override
            public void onSuccess(GenerateContentResponse result) {
                callback.onSuccess("Connection Successful", 0);
            }

            @Override
            public void onFailure(Throwable t) {
                callback.onError(t.getMessage(), false);
            }
        }, executor);
    }

    private void performAnalysis(Bitmap bitmap, Callback callback, int retryCount, long startTime) {
        String key = keyManager.getActiveKey();
        String modelName = keyManager.getModel();

        // Skip empty keys and rotate if needed.
        if (key.isEmpty()) {
            keyManager.rotateKey();
            if (retryCount < 3) {
                performAnalysis(bitmap, callback, retryCount + 1, startTime);
            } else {
                callback.onError("No valid API keys found.", false);
            }
            return;
        }

        GenerativeModel gm = new GenerativeModel(modelName, key);
        GenerativeModelFutures model = GenerativeModelFutures.from(gm);

        String username = keyManager.getUsername();
        String playerHint = username.isEmpty()
                ? "Identify the player's row from the gold highlight, YOU indicator, or other obvious local-player marker."
                : "The player's username is '" + username + "'. Use that exact row when visible.";

        String prompt = "You are an expert Mobile Legends: Bang Bang (MLBB) live match coach.\n"
                + "Analyze ONLY information visible in this scoreboard screenshot.\n\n"
                + "VALIDATION:\n"
                + "- If this is not an MLBB in-match scoreboard showing player rows/items/KDA, output exactly: INVALID_IMAGE\n"
                + "- Do not wrap the answer in markdown or code fences.\n\n"
                + "PLAYER IDENTIFICATION:\n"
                + "- " + playerHint + "\n"
                + "- Identify the player's hero, KDA, and currently visible items.\n"
                + "- Never recommend an item that is already clearly present in the player's current build.\n\n"
                + "ENEMY ANALYSIS:\n"
                + "- Read enemy heroes, visible enemy items, KDA/economy indicators when readable, and team composition.\n"
                + "- Rank the TOP TWO threats specifically against the player's hero and current match state.\n"
                + "- For each top threat, include its main threat type: Physical Burst, Magic Burst, DPS, Crit, Sustain, Heal, Shield, CC, Tank, or Mixed.\n"
                + "- Include the important enemy items you can actually recognize from the screenshot.\n"
                + "- If an item icon/name is not readable with confidence, write 'unclear' instead of guessing.\n\n"
                + "COUNTER BUILD:\n"
                + "- Recommend exactly TWO next counter-items that are appropriate for the player's hero/role and legal in MLBB.\n"
                + "- counter_items[0] is BUY FIRST. counter_items[1] is THEN.\n"
                + "- Prioritize the biggest current threat, not a generic full build.\n"
                + "- Consider anti-heal, anti-shield, magic defense, physical defense, anti-crit, penetration, cleanse/CC resistance, mobility, or sustain only when justified by what is visible.\n"
                + "- damage_items should be ONE optional damage item that still fits the player's hero, or 'None' if defense/utility is more important now.\n"
                + "- reasoning must be short and explain what enemy hero/item each recommendation counters.\n"
                + "- teamfight_strategy must be practical and at most 2 short sentences.\n\n"
                + "OUTPUT STRICTLY AS ONE VALID JSON OBJECT USING THIS EXACT SHAPE:\n"
                + "{\n"
                + "  \"player_status\": {\"hero_name\": \"string\", \"kda\": \"string\"},\n"
                + "  \"top_threats\": [\n"
                + "    \"Hero 1 | Threat: type | Items: item, item\",\n"
                + "    \"Hero 2 | Threat: type | Items: item, item\"\n"
                + "  ],\n"
                + "  \"recommended_build\": {\n"
                + "    \"counter_items\": [\"BUY FIRST item\", \"THEN item\"],\n"
                + "    \"damage_items\": \"Optional damage item or None\",\n"
                + "    \"reasoning\": \"short match-specific reason\"\n"
                + "  },\n"
                + "  \"teamfight_strategy\": \"short practical strategy\"\n"
                + "}";

        Content content = new Content.Builder()
                .addText(prompt)
                .addImage(bitmap)
                .build();

        ListenableFuture<GenerateContentResponse> response = model.generateContent(content);

        Futures.addCallback(response, new FutureCallback<GenerateContentResponse>() {
            @Override
            public void onSuccess(GenerateContentResponse result) {
                String responseText = result.getText();
                long duration = System.currentTimeMillis() - startTime;
                Log.i("GeminiProfile", "Analysis took " + duration + "ms");
                Log.d("GeminiHelper", "API Response: " + responseText);

                if (responseText == null || responseText.trim().isEmpty()) {
                    callback.onError("AI returned an empty response.", false);
                    return;
                }

                callback.onSuccess(responseText, duration);
            }

            @Override
            public void onFailure(Throwable t) {
                String error = t.getMessage();
                boolean isRateLimit = error != null && error.contains("429");

                if (isRateLimit) {
                    keyManager.rotateKey();
                    if (retryCount < 3) {
                        performAnalysis(bitmap, callback, retryCount + 1, startTime);
                    } else {
                        callback.onError("All keys exhausted or rate limited.", true);
                    }
                } else {
                    callback.onError(error != null ? error : "Unknown Gemini API error", false);
                }
            }
        }, executor);
    }
}
