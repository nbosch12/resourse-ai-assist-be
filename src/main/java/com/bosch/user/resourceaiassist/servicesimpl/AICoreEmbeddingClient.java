package com.bosch.user.resourceaiassist.servicesimpl;

import com.bosch.user.resourceaiassist.config.EmbedProps;
import com.bosch.user.resourceaiassist.services.EmbeddingClient;
import lombok.RequiredArgsConstructor;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Component
@RequiredArgsConstructor
public class AICoreEmbeddingClient implements EmbeddingClient {

    private final EmbedProps cfg;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    // --- token cache (avoid hitting XSUAA on every call)
    private volatile String cachedToken;
    private volatile Instant cachedTokenExp = Instant.EPOCH;

    /** SAP XSUAA: Basic auth + grant_type=client_credentials (exactly like your curl) */
    private String token() {
        try {
            Instant now = Instant.now();
            if (cachedToken != null && now.isBefore(cachedTokenExp.minusSeconds(60))) {
                return cachedToken;
            }

            String form = "grant_type=client_credentials"; // add &scope=... if you ever need scopes

            HttpRequest req = HttpRequest.newBuilder(URI.create(cfg.getTokenUrl()))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("Authorization", basic(cfg.getClientId(), cfg.getClientSecret()))
                    .POST(HttpRequest.BodyPublishers.ofString(form))
                    .timeout(Duration.ofSeconds(15))
                    .build();

            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) {
                throw new RuntimeException("AI Core token error: HTTP " + res.statusCode() + " -> " + res.body());
            }

            JSONObject body = new JSONObject(res.body());
            cachedToken = body.getString("access_token");
            int expiresIn = body.optInt("expires_in", 600); // seconds
            cachedTokenExp = now.plusSeconds(expiresIn);
            return cachedToken;

        } catch (HttpTimeoutException te) {
            throw new RuntimeException("AI Core token timeout", te);
        } catch (Exception e) {
            throw new RuntimeException("AI Core token error", e);
        }
    }

    private static String basic(String id, String secret) {
        String raw = id + ":" + secret;
        return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public float[] embed(String text) {
        return embedBatch(List.of(text)).get(0);
    }

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        try {
            String t = token();

            // Match your working request:
            // POST /v2/inference/deployments/{dep}/embeddings?api-version=2023-05-15
            String url = cfg.getApiBase()
                    + "/v2/inference/deployments/"
                    + cfg.getDeploymentId()
                    + "/embeddings?api-version=2023-05-15";

            JSONObject payload = new JSONObject().put("input", new JSONArray(texts));

            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .header("Authorization", "Bearer " + t)
                    .header("AI-Resource-Group", cfg.getResourceGroup())
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofMillis(cfg.getTimeoutMs()))
                    .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                    .build();

            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != HttpStatus.OK.value()) {
                throw new RuntimeException("Embeddings HTTP " + res.statusCode() + ": " + res.body());
            }

            JSONObject body = new JSONObject(res.body());
            List<float[]> out = new ArrayList<>();

            // OpenAI-style
            if (body.has("data")) {
                JSONArray data = body.getJSONArray("data");
                for (int i = 0; i < data.length(); i++) {
                    JSONArray e = data.getJSONObject(i).getJSONArray("embedding");
                    out.add(toFloatArray(e));
                }
            }
            // Native-style (some tenants)
            else if (body.has("embeddings")) {
                JSONArray arr = body.getJSONArray("embeddings");
                for (int i = 0; i < arr.length(); i++) {
                    out.add(toFloatArray(arr.getJSONArray(i)));
                }
            } else {
                throw new IllegalStateException("Unexpected embeddings response: " + body.keySet());
            }

            // dim check (3072)
            for (float[] v : out) {
                if (v.length != cfg.getDim()) {
                    throw new IllegalStateException("Embedding dim mismatch: got " + v.length + " expected " + cfg.getDim());
                }
            }
            return out;

        } catch (HttpTimeoutException te) {
            throw new RuntimeException("Embeddings timeout after " + cfg.getTimeoutMs() + "ms", te);
        } catch (Exception e) {
            throw new RuntimeException("AI Core embeddings error", e);
        }
    }

    private static float[] toFloatArray(JSONArray emb) {
        float[] v = new float[emb.length()];
        for (int j = 0; j < emb.length(); j++) v[j] = (float) emb.getDouble(j);
        return v;
    }
}