package com.bankrag.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for generating text embeddings.
 * Supports two providers:
 *  - "local": A deterministic hash-based embedding (no external API needed, great for dev/testing)
 *  - "openai": Uses OpenAI's text-embedding API for production-quality embeddings
 */
@Service
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);

    @Value("${rag.embedding.provider:local}")
    private String provider;

    @Value("${rag.embedding.dimension:384}")
    private int dimension;

    @Value("${rag.embedding.openai-api-key:}")
    private String openaiApiKey;

    @Value("${rag.embedding.openai-model:text-embedding-3-small}")
    private String openaiModel;

    private final OkHttpClient httpClient = new OkHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Generate an embedding vector for the given text.
     */
    public float[] embed(String text) {
        return switch (provider) {
            case "openai" -> embedWithOpenAI(text);
            default -> embedLocal(text);
        };
    }

    /**
     * Batch embed multiple texts.
     */
    public List<float[]> embedBatch(List<String> texts) {
        if ("openai".equals(provider)) {
            return embedBatchWithOpenAI(texts);
        }
        List<float[]> results = new ArrayList<>(texts.size());
        for (String text : texts) {
            results.add(embedLocal(text));
        }
        return results;
    }

    /**
     * Local deterministic embedding using seeded random projection.
     *
     * For each token (unigrams, bigrams, and key domain terms), we use its
     * hash as a seed for a deterministic pseudo-random generator that produces
     * a DENSE vector across all dimensions. This ensures:
     *  - Texts sharing tokens have high cosine similarity
     *  - Every dimension is populated, producing meaningful similarity scores
     *  - Results are fully deterministic (same text = same embedding)
     */
    private float[] embedLocal(String text) {
        float[] vector = new float[dimension];
        String normalized = text.toLowerCase().trim()
                .replaceAll("[^a-z0-9\\s.$%,]", " ")  // keep letters, digits, $, %, .
                .replaceAll("\\s+", " ");

        String[] words = normalized.split(" ");
        if (words.length == 0) return vector;

        List<String> tokens = new ArrayList<>();
        List<Float> weights = new ArrayList<>();

        // Unigrams with weight boosting for important terms
        for (String w : words) {
            if (!w.isEmpty()) {
                tokens.add(w);
                // Boost location/city/state names and other key terms
                weights.add(isImportantTerm(w) ? 2.0f : 1.0f);
            }
        }

        // Bigrams (important for "new york", "fort worth", etc.)
        for (int i = 0; i < words.length - 1; i++) {
            if (!words[i].isEmpty() && !words[i + 1].isEmpty()) {
                String bigram = words[i] + "_" + words[i + 1];
                tokens.add(bigram);
                // Boost bigrams that look like location names
                weights.add(isImportantBigram(words[i], words[i + 1]) ? 3.0f : 1.0f);
            }
        }

        // For each token, generate a dense pseudo-random vector seeded by token hash
        for (int t = 0; t < tokens.size(); t++) {
            String token = tokens.get(t);
            float weight = weights.get(t);
            long seed = tokenToSeed(token);
            // Simple LCG (linear congruential generator) for speed
            long state = seed;
            for (int d = 0; d < dimension; d++) {
                state = state * 6364136223846793005L + 1442695040888963407L;
                // Map to float in [-1, 1]
                float val = ((int) (state >>> 33) - 1073741823) / 1073741823.0f;
                vector[d] += val * weight;
            }
        }

        // L2 normalize
        float norm = 0;
        for (float v : vector) norm += v * v;
        norm = (float) Math.sqrt(norm);
        if (norm > 0) {
            for (int i = 0; i < dimension; i++) vector[i] /= norm;
        }

        return vector;
    }

    /**
     * Check if a term is important and should be weighted higher.
     */
    private boolean isImportantTerm(String word) {
        // Common US states (abbreviations and full names)
        return switch (word) {
            case "ny", "ca", "tx", "fl", "il", "pa", "ohio", "ga", "nc", "mi", "nj", "va", "wa", "az", "ma", "tn", "in", "mo", "md", "wi",
                 "york", "california", "texas", "florida", "illinois", "pennsylvania", "georgia", "carolina", "michigan", "jersey",
                 "virginia", "washington", "arizona", "massachusetts", "tennessee", "indiana", "missouri", "maryland", "wisconsin",
                 // Common cities
                 "chicago", "houston", "phoenix", "philadelphia", "antonio", "diego", "dallas", "jose", "austin", "jacksonville",
                 "francisco", "columbus", "charlotte", "indianapolis", "seattle", "denver", "boston", "detroit", "nashville",
                 "memphis", "portland", "vegas", "louisville", "baltimore", "milwaukee", "albuquerque", "tucson", "fresno", "mesa",
                 "sacramento", "atlanta", "kansas", "miami", "oakland", "minneapolis", "tulsa", "cleveland", "wichita", "arlington",
                 "worth" -> true;
            default -> false;
        };
    }

    /**
     * Check if a bigram represents an important multi-word location.
     */
    private boolean isImportantBigram(String w1, String w2) {
        String combined = w1 + " " + w2;
        return switch (combined) {
            case "new york", "new jersey", "new mexico", "new hampshire", "new orleans",
                 "los angeles", "san francisco", "san diego", "san antonio", "san jose",
                 "fort worth", "fort wayne", "salt lake", "el paso", "north carolina",
                 "south carolina", "south dakota", "north dakota", "west virginia",
                 "las vegas", "santa barbara", "santa cruz", "kansas city", "oklahoma city",
                 "jersey city", "virginia beach" -> true;
            default -> false;
        };
    }

    /**
     * Convert a token string to a deterministic 64-bit seed.
     */
    private long tokenToSeed(String token) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(token.getBytes(StandardCharsets.UTF_8));
            return ((long)(hash[0] & 0xFF) << 56) | ((long)(hash[1] & 0xFF) << 48) |
                   ((long)(hash[2] & 0xFF) << 40) | ((long)(hash[3] & 0xFF) << 32) |
                   ((long)(hash[4] & 0xFF) << 24) | ((long)(hash[5] & 0xFF) << 16) |
                   ((long)(hash[6] & 0xFF) << 8)  | ((long)(hash[7] & 0xFF));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Generate embedding using OpenAI API.
     */
    private float[] embedWithOpenAI(String text) {
        try {
            String json = objectMapper.writeValueAsString(new java.util.HashMap<>() {{
                put("input", text);
                put("model", openaiModel);
            }});

            Request request = new Request.Builder()
                    .url("https://api.openai.com/v1/embeddings")
                    .header("Authorization", "Bearer " + openaiApiKey)
                    .header("Content-Type", "application/json")
                    .post(RequestBody.create(json, MediaType.parse("application/json")))
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new IOException("OpenAI API error: " + response.code() + " " + response.body().string());
                }
                JsonNode root = objectMapper.readTree(response.body().string());
                JsonNode embeddingArray = root.get("data").get(0).get("embedding");
                float[] result = new float[embeddingArray.size()];
                for (int i = 0; i < embeddingArray.size(); i++) {
                    result[i] = (float) embeddingArray.get(i).asDouble();
                }
                return result;
            }
        } catch (IOException e) {
            log.error("Failed to get OpenAI embedding", e);
            throw new RuntimeException("Embedding generation failed", e);
        }
    }

    private List<float[]> embedBatchWithOpenAI(List<String> texts) {
        // OpenAI supports batch embedding
        try {
            String json = objectMapper.writeValueAsString(new java.util.HashMap<>() {{
                put("input", texts);
                put("model", openaiModel);
            }});

            Request request = new Request.Builder()
                    .url("https://api.openai.com/v1/embeddings")
                    .header("Authorization", "Bearer " + openaiApiKey)
                    .header("Content-Type", "application/json")
                    .post(RequestBody.create(json, MediaType.parse("application/json")))
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new IOException("OpenAI API error: " + response.code());
                }
                JsonNode root = objectMapper.readTree(response.body().string());
                JsonNode dataArray = root.get("data");
                List<float[]> results = new ArrayList<>();
                for (JsonNode item : dataArray) {
                    JsonNode embeddingArray = item.get("embedding");
                    float[] vec = new float[embeddingArray.size()];
                    for (int i = 0; i < embeddingArray.size(); i++) {
                        vec[i] = (float) embeddingArray.get(i).asDouble();
                    }
                    results.add(vec);
                }
                return results;
            }
        } catch (IOException e) {
            log.error("Failed to get OpenAI batch embeddings", e);
            throw new RuntimeException("Batch embedding generation failed", e);
        }
    }

    public int getDimension() {
        return dimension;
    }

    public String getProvider() {
        return provider;
    }
}
