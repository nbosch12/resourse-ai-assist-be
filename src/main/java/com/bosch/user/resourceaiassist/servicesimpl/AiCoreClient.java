package com.bosch.user.resourceaiassist.servicesimpl;

import com.bosch.user.resourceaiassist.config.EmbedProps;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
class AiCoreClient {
    private final RestClient rest;
    private final EmbedProps props;
    private final AiCoreTokenProvider tokenProvider;

    // ---- Embeddings ----
    record EmbeddingData(int index, float[] embedding) {}
    record EmbeddingResp(List<EmbeddingData> data) {}

    float[] embed(String text) {
        String url = props.getApiBase() + "/v2/inference/deployments/" + props.getEmbeddingsDeploymentId()
                + "/embeddings?api-version=" + props.getApiVersion();

        Map<String,Object> body = Map.of("input", text,
                "model", "text-embedding-3-large"); // harmless if ignored

        EmbeddingResp resp = rest.post()
                .uri(url)
                .headers(h -> {
                    h.set("Authorization", tokenProvider.getBearer());
                    h.set("AI-Resource-Group", props.getResourceGroup());
                    h.setContentType(MediaType.APPLICATION_JSON);
                })
                .body(body)
                .retrieve()
                .body(EmbeddingResp.class);

        return resp.data().get(0).embedding();
    }

    // ---- Chat (Claude 3 Haiku) ----
    // Accepts OpenAI/Azure-like chat body; AI Core bridges to the Anthropic backend.
    record ChatMessage(String role, String content) {}

    @SuppressWarnings("unchecked")
    String chat(List<ChatMessage> messages, Integer maxTokens, Double temperature) {
        // Use /invoke + api-version (same as your working PowerShell)
        String url = props.getApiBase()
                + "/v2/inference/deployments/" + props.getChatDeploymentId()
                + "/invoke?api-version=" + props.getApiVersion();

        // Build Anthropic-style messages (role: user/assistant, content: [{type:"text",text:"..."}])
        var anthroMsgs = new java.util.ArrayList<Map<String,Object>>();
        var sysBuf = new StringBuilder();

        for (ChatMessage m : messages) {
            String role = (m.role() == null) ? "user" : m.role().toLowerCase();
            String content = (m.content() == null) ? "" : m.content();

            if ("system".equals(role)) {
                if (sysBuf.length() > 0) sysBuf.append('\n');
                sysBuf.append(content);
                continue; // put system into top-level "system"
            }
            if (!"user".equals(role) && !"assistant".equals(role)) role = "user";

            anthroMsgs.add(Map.of(
                    "role", role,
                    "content", java.util.List.of(Map.of("type","text","text", content))
            ));
        }

        var body = new java.util.LinkedHashMap<String,Object>();
        body.put("anthropic_version", "bedrock-2023-05-31");              // <- key that made PS call work
        body.put("messages", anthroMsgs);
        if (sysBuf.length() > 0) body.put("system", sysBuf.toString());  // optional
        body.put("max_tokens",   maxTokens != null ? maxTokens : 1024);  // adapter accepts max_tokens
        if (temperature != null) body.put("temperature", temperature);

        // NOTE: don't send "model" here; deployment already pins it

        Map<String,Object> resp = rest.post()
                .uri(url)
                .headers(h -> {
                    h.set("Authorization",     tokenProvider.getBearer()); // "Bearer <token>"
                    h.set("AI-Resource-Group", props.getResourceGroup());
                    h.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
                    h.setContentType(MediaType.APPLICATION_JSON);
                })
                .body(body)
                .retrieve()
                .body(Map.class);

        // Try common shapes returned by the adapter
        if (resp != null) {
            // 1) output_text
            Object out = resp.get("output_text");
            if (out instanceof String s && !s.isBlank()) return s;

            // 2) content: [{type:"text", text:"..."}]
            Object content = resp.get("content");
            if (content instanceof java.util.List<?> blocks && !blocks.isEmpty()) {
                var sb = new StringBuilder();
                for (Object b : blocks) {
                    if (b instanceof Map<?,?> m) {
                        Object t = m.get("text");
                        if (t instanceof String s) sb.append(s);
                    }
                }
                if (sb.length() > 0) return sb.toString();
            }

            // 3) OpenAI-ish: choices[0].message.content
            Object choices = resp.get("choices");
            if (choices instanceof java.util.List<?> ch && !ch.isEmpty()) {
                Object first = ch.get(0);
                if (first instanceof Map<?,?> cc) {
                    Object msg = cc.get("message");
                    if (msg instanceof Map<?,?> mm) {
                        Object txt = mm.get("content");
                        if (txt instanceof String s) return s;
                    }
                }
            }
        }
        return "(no content)";
    }
}