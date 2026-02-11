package com.bankrag.rag;

import com.bankrag.model.ChatMessage;
import com.bankrag.repository.ChatMessageRepository;
import com.bankrag.repository.VectorStoreRepository;
import com.bankrag.repository.VectorStoreRepository.SimilarDocument;
import com.bankrag.service.EmbeddingService;
import com.bankrag.service.LlmService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Core RAG (Retrieval-Augmented Generation) Engine.
 *
 * Pipeline:
 * 1. Receive user query
 * 2. Embed the query into a vector
 * 3. Retrieve top-k similar documents from pgvector
 * 4. Build a context window from retrieved documents
 * 5. Send context + query to LLM for answer generation
 * 6. Store conversation in chat history
 */
@Service
public class RagEngine {

    private static final Logger log = LoggerFactory.getLogger(RagEngine.class);

    private static final String SYSTEM_PROMPT = """
        You are a knowledgeable banking assistant with access to customer accounts, \
        transaction data, and banking policies. Answer questions accurately based on \
        the provided context. If the context doesn't contain enough information to \
        answer fully, say so clearly. Always be helpful and professional.

        When discussing specific customers or accounts, reference them by their IDs. \
        When discussing policies, cite the specific policy. For numerical data, be precise. \
        If asked about trends, analyze the transaction patterns provided.

        Important: Never fabricate data. Only reference information present in the context below.
        """;

    private final EmbeddingService embeddingService;
    private final VectorStoreRepository vectorStoreRepository;
    private final LlmService llmService;
    private final ChatMessageRepository chatMessageRepository;

    @Value("${rag.retrieval.top-k:5}")
    private int topK;

    @Value("${rag.retrieval.similarity-threshold:0.3}")
    private double similarityThreshold;

    public RagEngine(EmbeddingService embeddingService,
                     VectorStoreRepository vectorStoreRepository,
                     LlmService llmService,
                     ChatMessageRepository chatMessageRepository) {
        this.embeddingService = embeddingService;
        this.vectorStoreRepository = vectorStoreRepository;
        this.llmService = llmService;
        this.chatMessageRepository = chatMessageRepository;
    }

    /**
     * Process a query through the full RAG pipeline.
     */
    public RagResponse query(String userQuery) {
        return query(userQuery, null, null);
    }

    /**
     * Process a query with optional session context and source type filter.
     */
    public RagResponse query(String userQuery, String sessionId, String sourceTypeFilter) {
        long startTime = System.currentTimeMillis();

        if (sessionId == null) {
            sessionId = UUID.randomUUID().toString();
        }

        log.info("RAG query [session={}]: {}", sessionId, userQuery);

        // Step 1: Embed the query
        float[] queryEmbedding = embeddingService.embed(userQuery);

        // Step 2: Retrieve similar documents
        List<SimilarDocument> retrievedDocs;
        if (sourceTypeFilter != null && !sourceTypeFilter.isBlank()) {
            retrievedDocs = vectorStoreRepository.searchSimilarByType(
                    queryEmbedding, sourceTypeFilter, topK, similarityThreshold);
        } else {
            retrievedDocs = vectorStoreRepository.searchSimilar(
                    queryEmbedding, topK, similarityThreshold);
        }

        log.info("Retrieved {} relevant documents (threshold: {})", retrievedDocs.size(), similarityThreshold);

        // Step 3: Build context from retrieved documents
        String context = buildContext(retrievedDocs);

        // Step 4: Include conversation history if available
        String conversationContext = getConversationContext(sessionId);
        String fullContext = conversationContext.isEmpty()
                ? context
                : "Previous conversation:\n" + conversationContext + "\n\nRelevant data:\n" + context;

        // Step 5: Generate response via LLM
        String answer = llmService.generate(SYSTEM_PROMPT, userQuery, fullContext);

        // Step 6: Store in chat history
        chatMessageRepository.save(new ChatMessage(sessionId, "USER", userQuery));
        chatMessageRepository.save(new ChatMessage(sessionId, "ASSISTANT", answer));

        long elapsed = System.currentTimeMillis() - startTime;
        log.info("RAG response generated in {}ms", elapsed);

        // Build source references
        List<SourceReference> sources = retrievedDocs.stream()
                .map(d -> new SourceReference(d.sourceType(), d.sourceId(), d.similarity(), snippetOf(d.content())))
                .collect(Collectors.toList());

        return new RagResponse(
                answer,
                sessionId,
                sources,
                retrievedDocs.size(),
                elapsed,
                embeddingService.getProvider(),
                llmService.getProvider()
        );
    }

    private String buildContext(List<SimilarDocument> docs) {
        if (docs.isEmpty()) {
            return "No relevant information found in the knowledge base.";
        }

        StringBuilder context = new StringBuilder();
        for (int i = 0; i < docs.size(); i++) {
            SimilarDocument doc = docs.get(i);
            context.append("[Source: ").append(doc.sourceType())
                   .append(" | ID: ").append(doc.sourceId())
                   .append(" | Relevance: ").append(String.format("%.2f", doc.similarity()))
                   .append("]\n");
            context.append(doc.content()).append("\n");
            if (i < docs.size() - 1) context.append("---\n");
        }
        return context.toString();
    }

    private String getConversationContext(String sessionId) {
        List<ChatMessage> history = chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
        if (history.isEmpty()) return "";

        // Include last 6 messages for context
        int start = Math.max(0, history.size() - 6);
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < history.size(); i++) {
            ChatMessage msg = history.get(i);
            sb.append(msg.getRole()).append(": ").append(msg.getContent()).append("\n");
        }
        return sb.toString();
    }

    private String snippetOf(String content) {
        if (content.length() <= 150) return content;
        return content.substring(0, 150) + "...";
    }

    // Response DTOs

    public record RagResponse(
            String answer,
            String sessionId,
            List<SourceReference> sources,
            int documentsRetrieved,
            long latencyMs,
            String embeddingProvider,
            String llmProvider
    ) {}

    public record SourceReference(
            String sourceType,
            String sourceId,
            double similarity,
            String snippet
    ) {}
}
