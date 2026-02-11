package com.bankrag.controller;

import com.bankrag.generator.SyntheticDataGenerator;
import com.bankrag.generator.SyntheticDataGenerator.GenerationStats;
import com.bankrag.repository.CustomerRepository;
import com.bankrag.repository.AccountRepository;
import com.bankrag.repository.TransactionRepository;
import com.bankrag.repository.VectorStoreRepository;
import com.bankrag.service.DocumentIndexer;
import com.bankrag.service.DocumentIndexer.IndexStats;
import com.bankrag.service.EmbeddingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Admin endpoints for data management and system status.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private static final Logger log = LoggerFactory.getLogger(AdminController.class);

    private final SyntheticDataGenerator dataGenerator;
    private final DocumentIndexer documentIndexer;
    private final CustomerRepository customerRepository;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final VectorStoreRepository vectorStoreRepository;
    private final EmbeddingService embeddingService;
    private final JdbcTemplate jdbcTemplate;

    // Track async reset progress
    private final AtomicReference<String> resetStatus = new AtomicReference<>("idle");

    public AdminController(SyntheticDataGenerator dataGenerator,
                           DocumentIndexer documentIndexer,
                           CustomerRepository customerRepository,
                           AccountRepository accountRepository,
                           TransactionRepository transactionRepository,
                           VectorStoreRepository vectorStoreRepository,
                           EmbeddingService embeddingService,
                           JdbcTemplate jdbcTemplate) {
        this.dataGenerator = dataGenerator;
        this.documentIndexer = documentIndexer;
        this.customerRepository = customerRepository;
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.vectorStoreRepository = vectorStoreRepository;
        this.embeddingService = embeddingService;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * GET /api/admin/status - System status and data counts.
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok(Map.of(
            "customers", customerRepository.count(),
            "accounts", accountRepository.count(),
            "transactions", transactionRepository.count(),
            "embeddings", vectorStoreRepository.countEmbeddings(),
            "status", "UP"
        ));
    }

    /**
     * POST /api/admin/generate - Generate synthetic data.
     */
    @PostMapping("/generate")
    public ResponseEntity<GenerationStats> generateData() {
        GenerationStats stats = dataGenerator.generate();
        return ResponseEntity.ok(stats);
    }

    /**
     * POST /api/admin/index - Index all data into vector store.
     */
    @PostMapping("/index")
    public ResponseEntity<IndexStats> indexData() {
        IndexStats stats = documentIndexer.indexAll();
        return ResponseEntity.ok(stats);
    }

    /**
     * POST /api/admin/reindex - Re-index all data (deletes existing embeddings).
     */
    @PostMapping("/reindex")
    public ResponseEntity<IndexStats> reindexData() {
        IndexStats stats = documentIndexer.reindexAll();
        return ResponseEntity.ok(stats);
    }

    /**
     * POST /api/admin/setup - Full setup: generate data + index embeddings.
     */
    @PostMapping("/setup")
    public ResponseEntity<Map<String, Object>> fullSetup() {
        GenerationStats genStats = dataGenerator.generate();
        IndexStats indexStats = documentIndexer.indexAll();
        return ResponseEntity.ok(Map.of(
            "generation", genStats,
            "indexing", indexStats
        ));
    }

    /**
     * POST /api/admin/reset - Wipe ALL data and re-generate + re-index.
     * Uses TRUNCATE CASCADE for speed. Runs generation async — poll /status to track progress.
     */
    @PostMapping("/reset")
    public ResponseEntity<Map<String, Object>> resetAll() {
        if ("running".equals(resetStatus.get())) {
            return ResponseEntity.ok(Map.of("message", "Reset already in progress", "status", resetStatus.get()));
        }

        // Fast wipe using TRUNCATE CASCADE
        log.info("Truncating all tables...");
        jdbcTemplate.execute("TRUNCATE TABLE chat_history, document_embeddings, transactions, accounts, customers CASCADE");
        log.info("Tables truncated.");

        resetStatus.set("running");

        // Run generation + indexing in background thread
        CompletableFuture.runAsync(() -> {
            try {
                resetStatus.set("generating data...");
                GenerationStats genStats = dataGenerator.generate();
                log.info("Generation done: {} customers, {} accounts, {} transactions",
                        genStats.customers(), genStats.accounts(), genStats.transactions());

                resetStatus.set("indexing embeddings...");
                IndexStats indexStats = documentIndexer.indexAll();
                log.info("Indexing done: {} total embeddings", indexStats.total());

                resetStatus.set("complete");
            } catch (Exception e) {
                log.error("Reset failed", e);
                resetStatus.set("failed: " + e.getMessage());
            }
        });

        return ResponseEntity.ok(Map.of(
            "message", "Reset started — tables truncated, generation running in background",
            "status", "running",
            "hint", "Poll GET /api/admin/status and GET /api/admin/reset/status to track progress"
        ));
    }

    /**
     * GET /api/admin/reset/status - Check async reset progress.
     */
    @GetMapping("/reset/status")
    public ResponseEntity<Map<String, Object>> resetProgress() {
        return ResponseEntity.ok(Map.of("resetStatus", resetStatus.get()));
    }

    /**
     * GET /api/admin/debug - Diagnostic endpoint to verify embeddings exist and search works.
     */
    @GetMapping("/debug")
    public ResponseEntity<Map<String, Object>> debug() {
        Map<String, Object> info = new HashMap<>();
        info.put("customers", customerRepository.count());
        info.put("accounts", accountRepository.count());
        info.put("transactions", transactionRepository.count());
        info.put("embeddings", vectorStoreRepository.countEmbeddings());
        info.put("embeddingProvider", embeddingService.getProvider());
        info.put("embeddingDimension", embeddingService.getDimension());

        // Test: embed a sample query and search
        try {
            float[] testVec = embeddingService.embed("overdraft policy");
            int nonZero = 0;
            for (float v : testVec) if (v != 0f) nonZero++;
            info.put("testEmbedding_nonZeroDims", nonZero);
            info.put("testEmbedding_totalDims", testVec.length);

            var results = vectorStoreRepository.searchSimilar(testVec, 3, 0.0);
            info.put("testSearch_resultsAt0Threshold", results.size());
            if (!results.isEmpty()) {
                info.put("testSearch_topSimilarity", results.get(0).similarity());
                info.put("testSearch_topSourceType", results.get(0).sourceType());
                info.put("testSearch_topSnippet", results.get(0).content().substring(0, Math.min(100, results.get(0).content().length())));
            }

            // Also check if any embeddings have null vectors
            Long nullVectors = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM document_embeddings WHERE embedding IS NULL", Long.class);
            info.put("nullEmbeddings", nullVectors);

        } catch (Exception e) {
            info.put("testError", e.getClass().getSimpleName() + ": " + e.getMessage());
        }

        return ResponseEntity.ok(info);
    }
}
