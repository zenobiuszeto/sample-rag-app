package com.bankrag.controller;

import com.bankrag.rag.RagEngine;
import com.bankrag.rag.RagEngine.RagResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST API for the RAG banking assistant.
 */
@RestController
@RequestMapping("/api/rag")
public class RagController {

    private final RagEngine ragEngine;

    public RagController(RagEngine ragEngine) {
        this.ragEngine = ragEngine;
    }

    /**
     * Query the RAG system.
     *
     * POST /api/rag/query
     * {
     *   "query": "What is the overdraft policy?",
     *   "sessionId": "optional-session-id",
     *   "sourceType": "optional-filter" // CUSTOMER_PROFILE, ACCOUNT_SUMMARY, TRANSACTION_PATTERN, POLICY
     * }
     */
    @PostMapping("/query")
    public ResponseEntity<RagResponse> query(@RequestBody QueryRequest request) {
        if (request.query() == null || request.query().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        RagResponse response = ragEngine.query(request.query(), request.sessionId(), request.sourceType());
        return ResponseEntity.ok(response);
    }

    /**
     * Simple GET endpoint for quick testing.
     */
    @GetMapping("/ask")
    public ResponseEntity<RagResponse> ask(
            @RequestParam String q,
            @RequestParam(required = false) String sessionId,
            @RequestParam(required = false) String sourceType) {
        if (q == null || q.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        RagResponse response = ragEngine.query(q, sessionId, sourceType);
        return ResponseEntity.ok(response);
    }

    // Request DTO
    public record QueryRequest(String query, String sessionId, String sourceType) {}
}
