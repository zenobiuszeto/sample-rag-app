package com.bankrag.config;

import com.bankrag.generator.SyntheticDataGenerator;
import com.bankrag.service.DocumentIndexer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Auto-runs data generation and indexing on startup.
 * Disable by running with --spring.profiles.active=no-auto-setup
 */
@Configuration
public class AppStartupRunner {

    private static final Logger log = LoggerFactory.getLogger(AppStartupRunner.class);

    @Bean
    @Profile("!no-auto-setup")
    public CommandLineRunner startupRunner(SyntheticDataGenerator generator, DocumentIndexer indexer) {
        return args -> {
            log.info("========================================");
            log.info("Banking RAG System - Starting");
            log.info("  API will be available at http://localhost:8080");
            log.info("  Data generation running in background...");
            log.info("  Poll /api/admin/status to track progress.");
            log.info("========================================");

            // Run in background so the server starts immediately
            new Thread(() -> {
                try {
                    log.info("Step 1: Generating synthetic banking data...");
                    var genStats = generator.generate();
                    if (genStats.skipped()) {
                        log.info("  Data already exists, skipped generation.");
                    } else {
                        log.info("  Generated: {} customers, {} accounts, {} transactions",
                                genStats.customers(), genStats.accounts(), genStats.transactions());
                    }

                    log.info("Step 2: Indexing data into vector store...");
                    var indexStats = indexer.indexAll();
                    if (indexStats.skipped()) {
                        log.info("  Embeddings already exist, skipped indexing.");
                    } else {
                        log.info("  Indexed: {} profiles, {} accounts, {} patterns, {} policies (total: {})",
                                indexStats.profiles(), indexStats.accounts(),
                                indexStats.patterns(), indexStats.policies(), indexStats.total());
                    }

                    log.info("========================================");
                    log.info("Banking RAG System - Ready!");
                    log.info("========================================");
                } catch (Exception e) {
                    log.error("Background setup FAILED: {}", e.getMessage(), e);
                }
            }, "data-setup").start();
        };
    }
}
