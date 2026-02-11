package com.bankrag.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Service for LLM-based text generation.
 * Supports four providers:
 *  - "mock": Returns a formatted response using just the context (no LLM needed)
 *  - "openai": Uses OpenAI Chat Completions API
 *  - "gemini": Uses Google Gemini API
 *  - "ollama": Uses local Ollama instance
 */
@Service
public class LlmService {

    private static final Logger log = LoggerFactory.getLogger(LlmService.class);

    @Value("${rag.llm.provider:mock}")
    private String provider;

    @Value("${rag.llm.openai-api-key:}")
    private String openaiApiKey;

    @Value("${rag.llm.openai-model:gpt-4o-mini}")
    private String openaiModel;

    @Value("${rag.llm.gemini-api-key:}")
    private String geminiApiKey;

    @Value("${rag.llm.gemini-model:gemini-1.5-flash}")
    private String geminiModel;

    @Value("${rag.llm.ollama-url:http://localhost:11434}")
    private String ollamaUrl;

    @Value("${rag.llm.ollama-model:llama3}")
    private String ollamaModel;

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Generate a response given a system prompt, user query, and retrieved context.
     */
    public String generate(String systemPrompt, String userQuery, String context) {
        return switch (provider) {
            case "openai" -> generateWithOpenAI(systemPrompt, userQuery, context);
            case "gemini" -> generateWithGemini(systemPrompt, userQuery, context);
            case "ollama" -> generateWithOllama(systemPrompt, userQuery, context);
            default -> generateMock(systemPrompt, userQuery, context);
        };
    }

    /**
     * Mock generator — formats the retrieved context as a structured answer.
     * Useful for development/testing without needing an LLM API.
     */
    private String generateMock(String systemPrompt, String userQuery, String context) {
        StringBuilder response = new StringBuilder();
        response.append("=== RAG Response (Mock LLM) ===\n\n");
        response.append("**Query:** ").append(userQuery).append("\n\n");
        response.append("**Based on retrieved banking data:**\n\n");

        if (context == null || context.isBlank()) {
            response.append("No relevant information found in the banking knowledge base.\n");
        } else {
            // Parse and present each context chunk
            String[] chunks = context.split("---\n");
            for (int i = 0; i < chunks.length; i++) {
                String chunk = chunks[i].trim();
                if (!chunk.isEmpty()) {
                    response.append("**Finding ").append(i + 1).append(":** ");
                    // Summarize: take first 200 chars
                    if (chunk.length() > 200) {
                        response.append(chunk.substring(0, 200)).append("...\n\n");
                    } else {
                        response.append(chunk).append("\n\n");
                    }
                }
            }
        }

        response.append("\n_Note: This is a mock response. Configure an LLM provider (OpenAI/Ollama) for natural language answers._");
        return response.toString();
    }

    private String generateWithOpenAI(String systemPrompt, String userQuery, String context) {
        try {
            String fullPrompt = systemPrompt + "\n\nContext:\n" + context + "\n\nUser Question: " + userQuery;

            String json = objectMapper.writeValueAsString(Map.of(
                "model", openaiModel,
                "messages", List.of(
                    Map.of("role", "system", "content", systemPrompt),
                    Map.of("role", "user", "content", "Context:\n" + context + "\n\nQuestion: " + userQuery)
                ),
                "temperature", 0.3,
                "max_tokens", 1000
            ));

            Request request = new Request.Builder()
                    .url("https://api.openai.com/v1/chat/completions")
                    .header("Authorization", "Bearer " + openaiApiKey)
                    .header("Content-Type", "application/json")
                    .post(RequestBody.create(json, MediaType.parse("application/json")))
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String body = response.body() != null ? response.body().string() : "";
                    throw new IOException("OpenAI API error: " + response.code() + " " + body);
                }
                JsonNode root = objectMapper.readTree(response.body().string());
                return root.get("choices").get(0).get("message").get("content").asText();
            }
        } catch (IOException e) {
            log.error("OpenAI generation failed", e);
            return "Error generating response: " + e.getMessage();
        }
    }

    private String generateWithGemini(String systemPrompt, String userQuery, String context) {
        try {
            // Gemini uses a combined prompt structure
            String combinedPrompt = systemPrompt + "\n\nContext:\n" + context + "\n\nUser Question: " + userQuery;

            String json = objectMapper.writeValueAsString(Map.of(
                "contents", List.of(
                    Map.of(
                        "parts", List.of(
                            Map.of("text", combinedPrompt)
                        )
                    )
                ),
                "generationConfig", Map.of(
                    "temperature", 0.3,
                    "maxOutputTokens", 1000
                )
            ));

            // Gemini API endpoint format: https://generativelanguage.googleapis.com/v1/models/{model}:generateContent?key={apiKey}
            String url = String.format(
                "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s",
                geminiModel, geminiApiKey
            );

            Request request = new Request.Builder()
                    .url(url)
                    .header("Content-Type", "application/json")
                    .post(RequestBody.create(json, MediaType.parse("application/json")))
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String body = response.body() != null ? response.body().string() : "";
                    throw new IOException("Gemini API error: " + response.code() + " " + body);
                }
                JsonNode root = objectMapper.readTree(response.body().string());

                // Gemini response format: candidates[0].content.parts[0].text
                if (root.has("candidates") && root.get("candidates").size() > 0) {
                    JsonNode candidate = root.get("candidates").get(0);
                    if (candidate.has("content")) {
                        JsonNode content = candidate.get("content");
                        if (content.has("parts") && content.get("parts").size() > 0) {
                            return content.get("parts").get(0).get("text").asText();
                        }
                    }
                }
                throw new IOException("Unexpected Gemini response format");
            }
        } catch (IOException e) {
            log.error("Gemini generation failed", e);
            return "Error generating response with Gemini: " + e.getMessage();
        }
    }

    private String generateWithOllama(String systemPrompt, String userQuery, String context) {
        try {
            String prompt = systemPrompt + "\n\nContext:\n" + context + "\n\nUser Question: " + userQuery;

            String json = objectMapper.writeValueAsString(Map.of(
                "model", ollamaModel,
                "prompt", prompt,
                "stream", false,
                "options", Map.of("temperature", 0.3)
            ));

            Request request = new Request.Builder()
                    .url(ollamaUrl + "/api/generate")
                    .header("Content-Type", "application/json")
                    .post(RequestBody.create(json, MediaType.parse("application/json")))
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new IOException("Ollama API error: " + response.code());
                }
                JsonNode root = objectMapper.readTree(response.body().string());
                return root.get("response").asText();
            }
        } catch (IOException e) {
            log.error("Ollama generation failed", e);
            return "Error generating response: " + e.getMessage();
        }
    }

    public String getProvider() {
        return provider;
    }
}
